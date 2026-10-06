package ch.so.agi.integrator;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public final class Store {
  public static final int VERSION = 2;
  final Path root;

  public Store(Path root) {
    this.root = root;
  }

  public Path directory(String id) {
    if (!id.matches("[a-f0-9]{32}")) throw new Problem("invalid_run_id", "Ungültige Vorgangs-ID.");
    Path p = root.resolve(id);
    if (!Files.isRegularFile(p.resolve("state.json")))
      throw new Problem("run_not_found", "Vorgang nicht gefunden.");
    return p;
  }

  public Map<String, Object> create(
      String workflow, String organization, String identifier, String issue, String env) {
    identifier(organization);
    if (Set.of("shared", "build", ".git", ".gradle").contains(organization))
      throw new Problem(
          "invalid_identifier", "Reservierter Repository-Ordner ist keine Organisation.");
    if (!Set.of("topic", "model", "organization").contains(workflow))
      throw new Problem("invalid_workflow", "workflow muss topic, model oder organization sein.");
    if (!workflow.equals("organization")) identifier(identifier);
    else if (identifier != null)
      throw new Problem(
          "invalid_arguments", "Organisationsvorgänge haben keinen Themenidentifier.");
    String id = UUID.randomUUID().toString().replace("-", "");
    var data =
        Json.map(
            "schema_version",
            VERSION,
            "workflow",
            workflow,
            "id",
            id,
            "created",
            Json.now(),
            "organization",
            organization,
            "identifier",
            identifier,
            "issue",
            issue,
            "source_environment",
            env,
            "files",
            Json.map(),
            "changes",
            Json.map(),
            "approvals",
            Json.map(),
            "checks",
            Json.map(),
            "deliveries",
            Json.map(),
            "events",
            new ArrayList<>(),
            "provenance",
            new ArrayList<>(),
            "baseline",
            null);
    Json.atomic(root.resolve(id).resolve("state.json"), data);
    return data;
  }

  public static void identifier(String value) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]*") || value.contains(".."))
      throw new Problem("invalid_identifier", "Organisation/Identifier enthält ungültige Zeichen.");
  }

  public <T> T edit(String id, Function<Map<String, Object>, T> fn) {
    return edit(id, false, fn);
  }

  public <T> T edit(String id, boolean legacy, Function<Map<String, Object>, T> fn) {
    Path p = directory(id);
    try (var ch =
        FileChannel.open(p.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      FileLock lock;
      try {
        lock = ch.tryLock();
      } catch (OverlappingFileLockException e) {
        throw new Problem("run_locked", "Vorgang wird bereits bearbeitet.");
      }
      if (lock == null) throw new Problem("run_locked", "Vorgang wird bereits bearbeitet.");
      try (lock) {
        var data = Json.read(p.resolve("state.json"));
        if (!legacy && Json.number(data, "schema_version", 1) != VERSION)
          throw new Problem(
              "migration_required", "Vorgang muss mit migrate_run explizit übernommen werden.");
        try {
          return fn.apply(data);
        } finally {
          Json.atomic(p.resolve("state.json"), data);
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem("state_unavailable", "Vorgang nicht lesbar.");
    }
  }

  public Map<String, Object> attach(Map<String, Object> data, Path source, String role) {
    if (!Set.of(
            "original_data", "data", "metadata", "metadata_source", "accepted_metadata", "model")
        .contains(role)) throw new Problem("invalid_role", "Unbekannte Dateirolle.");
    if (!Files.isRegularFile(source))
      throw new Problem("file_missing", "Eingabedatei fehlt.", "path", source.toString());
    String sha = Json.sha(source), name = source.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String suffix = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    Path dest = directory(Json.required(data, "id")).resolve("files").resolve(sha + suffix);
    try {
      Files.createDirectories(dest.getParent());
      if (!Files.exists(dest)) Files.copy(source, dest);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Eingabedatei kann nicht gesichert werden.");
    }
    var record = Json.map("path", dest.toString(), "sha256", sha, "original_name", name);
    Json.obj(data.get("files")).put(role, record);
    event(data, "attach", Json.map("role", role, "sha256", sha));
    return record;
  }

  public Path file(Map<String, Object> data, String role) {
    var rec = Json.obj(Json.obj(data.get("files")).get(role));
    if (rec.isEmpty()) return null;
    Path p = Path.of(Json.required(rec, "path"));
    if (!p.toAbsolutePath()
            .normalize()
            .startsWith(directory(Json.required(data, "id")).toAbsolutePath().normalize())
        || !Files.isRegularFile(p)
        || !Json.sha(p).equals(rec.get("sha256")))
      throw new Problem(
          "artifact_changed",
          "Gespeichertes Artefakt wurde verändert; erneut aufnehmen.",
          "role",
          role);
    return p;
  }

  public static void event(Map<String, Object> data, String action, Map<String, Object> details) {
    var event = Json.map("at", Json.now(), "action", action);
    event.putAll(details);
    Json.list(data.get("events")).add(event);
  }

  public Map<String, Object> migrate(String id, boolean apply) {
    return edit(
        id,
        true,
        data -> {
          int version = Json.number(data, "schema_version", 1);
          if (version == VERSION) return Json.map("required", false, "id", id);
          if (version != 1)
            throw new Problem("unsupported_state_version", "Unbekannte Vorgangsversion.");
          var migration =
              Json.map(
                  "id",
                  id,
                  "from",
                  1,
                  "to",
                  VERSION,
                  "historical_approvals",
                  data.get("approvals"),
                  "deliveries",
                  data.get("deliveries"),
                  "pending_metadata",
                  data.get("pending_metadata"),
                  "python_converter_requires_migration",
                  data.containsKey("transform"),
                  "applied",
                  apply);
          if (apply) {
            Path backup =
                directory(id).resolve("state-v1-backup-" + System.currentTimeMillis() + ".json");
            Json.atomic(backup, data);
            data.put("schema_version", VERSION);
            data.put("workflow", "topic");
            data.put("historical_approvals", data.get("approvals"));
            data.put("historical_checks", data.get("checks"));
            data.put("approvals", Json.map());
            data.put("checks", Json.map());
            if (data.containsKey("transform")) data.put("converter_migration_required", true);
            Store.event(data, "migrate_run", Json.map("backup", backup.toString()));
            migration.put("backup", backup.toString());
          }
          return migration;
        });
  }
}
