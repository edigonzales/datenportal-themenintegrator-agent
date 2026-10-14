package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** One-time, evidence-preserving conversion of the 0.2.20 review fingerprint format. */
final class ApprovalMigration {
  static Path previousHome(Path workspace) {
    if (!OperatorWorkspace.installed(workspace)) return null;
    var manifest = Json.read(workspace.resolve(OperatorWorkspace.MANIFEST));
    String home =
        Json.str(manifest, "legacy_package_home", Json.str(manifest, "package_home", null));
    if (home != null) return Path.of(home);
    Path launcher = workspace.resolve("bin/datenportal-agent");
    if (!Objects.equals(
        Json.obj(manifest.get("managed_files")).get("bin/datenportal-agent"), Json.sha(launcher)))
      return null;
    // Only decode our own generated launcher. Never execute or evaluate shell text.
    for (String line : Json.contents(launcher).lines().toList()) {
      if (line.startsWith("exec '") && line.endsWith("' \"$@\"")) {
        String executable = line.substring(6, line.length() - 6).replace("'\\''", "'");
        Path p = Path.of(executable);
        if (p.isAbsolute() && p.endsWith("bin/datenportal-agent")) return p.getParent().getParent();
      }
    }
    return null;
  }

  static Object update(Path workspace, Path oldHome, Path newHome) {
    Path config = workspace.resolve("config/local.toml");
    if (!Files.isRegularFile(config)) return Json.map("status", "not_available");
    var settings = new Settings(config);
    if (!Files.isDirectory(settings.runs)) return Json.map("status", "no_runs");
    boolean legacy = false;
    try (var paths = Files.list(settings.runs)) {
      for (Path p : paths.toList()) {
        if (!p.getFileName().toString().matches("[a-f0-9]{32}")
            || !Files.isRegularFile(p.resolve("state.json"))) continue;
        var state = Json.read(p.resolve("state.json"));
        legacy |=
            Objects.equals(state.get("schema_version"), Store.VERSION)
                && Json.number(state, "fingerprint_version", 1) < 2;
      }
    } catch (java.io.IOException e) {
      throw new Problem("migration_unavailable", "Vorgänge nicht lesbar.");
    }
    if (!legacy) return Json.map("status", "already_current");
    if (oldHome == null) return Json.map("status", "legacy_source_missing");
    Path distribution = oldHome.resolve("distribution.json");
    if (!Files.isRegularFile(distribution)
        || !"0.2.20".equals(Json.str(Json.read(distribution), "version", "")))
      return Json.map("status", "legacy_source_unsupported");
    var results = new ArrayList<Object>();
    // Migration reads state and files only; it cannot invoke MCPs, Jenkins or publication.
    McpClients.ToolClient forbidden =
        (name, args) -> {
          throw new Problem(
              "migration_external_call", "Keine externen Aufrufe bei Freigabemigration.");
        };
    try (var workflow = new Workflow(settings, new ProcessRunner(), forbidden, forbidden);
        var paths = Files.list(settings.runs)) {
      for (Path p : paths.sorted().toList()) {
        String id = p.getFileName().toString();
        if (!id.matches("[a-f0-9]{32}") || !Files.isRegularFile(p.resolve("state.json"))) continue;
        try {
          results.add(workflow.store.edit(id, r -> migrate(workflow, r, oldHome, newHome)));
        } catch (Problem e) {
          results.add(Json.map("run_id", id, "status", "not_migrated", "error", e.code));
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem("migration_unavailable", "Vorgänge nicht lesbar.");
    }
    return Json.map("status", "checked", "runs", results);
  }

  static Object migrate(Workflow w, Map<String, Object> r, Path oldHome, Path newHome) {
    if (Json.number(r, "fingerprint_version", 1) >= 2)
      return Json.map("run_id", r.get("id"), "status", "already_current");
    String oldPolicy = ReviewPolicy.fingerprint(w.settings, oldHome);
    String newPolicy = ReviewPolicy.fingerprint(w.settings, newHome);
    if (!oldPolicy.equals(newPolicy))
      return Json.map("run_id", r.get("id"), "status", "rules_changed");
    String legacy = w.settings.legacyFingerprint(oldHome);
    var gates = new TreeSet<>(Set.of("data", "metadata", "model", "organization"));
    gates.addAll(Json.obj(r.get("approvals")).keySet());
    Json.obj(r.get("publication_plans")).keySet().forEach(name -> gates.add("publish:" + name));
    var mapping = new LinkedHashMap<String, String>();
    for (String gate : gates)
      mapping.put(w.fingerprint(r, gate, legacy), w.fingerprint(r, gate, newPolicy));
    var originalApprovals = Json.read(Json.text(Json.obj(r.get("approvals"))));
    int replaced = 0;
    for (String key : List.of("approvals", "checks", "publication_plans", "deliveries", "seeds"))
      replaced += replace(r.get(key), mapping);
    r.put("fingerprint_version", 2);
    Store.event(
        r,
        "review_fingerprint_migrated",
        Json.map(
            "from",
            1,
            "to",
            2,
            "rule_content_fingerprint",
            newPolicy,
            "mapping",
            mapping,
            "previous_approvals",
            originalApprovals,
            "updated_records",
            replaced));
    return Json.map("run_id", r.get("id"), "status", "migrated", "updated_records", replaced);
  }

  private static int replace(Object value, Map<String, String> mapping) {
    int count = 0;
    if (value instanceof Map<?, ?>) {
      var map = Json.obj(value);
      Object before = map.get("fingerprint");
      if (before instanceof String && mapping.containsKey(before)) {
        map.put("fingerprint", mapping.get(before));
        count++;
      }
      for (Object child : map.values())
        if (child instanceof Map<?, ?> || child instanceof List<?>)
          count += replace(child, mapping);
    } else if (value instanceof List<?> list)
      for (Object child : list) count += replace(child, mapping);
    return count;
  }
}
