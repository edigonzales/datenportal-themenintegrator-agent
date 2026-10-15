package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Preserve an actual CSV approval across a proof-only component update, never create one. */
final class DataApprovalMigration {
  static Object update(Path workspace, Path before, Path after) {
    if (before == null || !sameCsvRules(before, after))
      return Json.map("status", "csv_rules_changed_or_unknown");
    var settings = new Settings(workspace.resolve("config/local.toml"));
    if (!Files.isDirectory(settings.runs)) return Json.map("status", "no_runs");
    McpClients.ToolClient forbidden =
        (name, args) -> {
          throw new Problem(
              "migration_external_call", "Keine externen Aufrufe bei Freigabemigration.");
        };
    var results = new ArrayList<Object>();
    try (var w = new Workflow(settings, new ProcessRunner(), forbidden, forbidden);
        var paths = Files.list(settings.runs)) {
      String oldPolicy = ReviewPolicy.fingerprint(settings, before),
          newPolicy = ReviewPolicy.fingerprint(settings, after);
      for (Path path : paths.sorted().toList()) {
        String id = path.getFileName().toString();
        if (!id.matches("[a-f0-9]{32}") || !Files.isRegularFile(path.resolve("state.json")))
          continue;
        try {
          results.add(w.store.edit(id, run -> migrate(w, run, oldPolicy, newPolicy)));
        } catch (Problem problem) {
          results.add(Json.map("run_id", id, "status", "not_migrated", "reason", problem.code));
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem("migration_unavailable", "Vorgänge nicht lesbar.");
    }
    return Json.map("status", "checked", "runs", results);
  }

  static Object migrate(Workflow w, Map<String, Object> run, String oldPolicy, String newPolicy) {
    var approval = Json.obj(Json.obj(run.get("approvals")).get("data"));
    var check = Json.obj(Json.obj(run.get("checks")).get("data"));
    String expected = w.fingerprint(run, "data", oldPolicy);
    boolean actualHumanEvent =
        humanEvidence(run, expected, approval.get("human_statement"), new HashSet<>());
    if (!actualHumanEvent
        || !Json.bool(check, "valid", false)
        || !expected.equals(check.get("fingerprint"))
        || !expected.equals(approval.get("fingerprint")))
      return Json.map("run_id", run.get("id"), "status", "no_current_data_approval");
    String next = w.fingerprint(run, "data", newPolicy);
    if (expected.equals(next)) return Json.map("run_id", run.get("id"), "status", "unchanged");
    var previous = Json.read(Json.text(approval));
    var archivedCheck = Json.read(Json.text(check));
    approval.put("fingerprint", next);
    check.put("fingerprint", next);
    check.put("manifest", CheckStates.manifest(w, run, "data"));
    check.put("state", "current");
    Json.list(
            Json.obj(run.computeIfAbsent("check_history", k -> Json.map()))
                .computeIfAbsent("data", k -> new ArrayList<>()))
        .add(archivedCheck);
    Store.event(
        run,
        "data_approval_preserved",
        Json.map(
            "previous_approval",
            previous,
            "fingerprint",
            next,
            "data_sha256",
            Json.sha(w.requiredFile(run, "data")),
            "reason",
            "Unveränderte CSV und identische CSV-Prüfregeln; tatsächliche menschliche Antwort bleibt erhalten."));
    return Json.map("run_id", run.get("id"), "status", "preserved");
  }

  private static boolean humanEvidence(
      Map<String, Object> run, String fingerprint, Object statement, Set<String> visited) {
    if (statement == null || statement.toString().isBlank() || !visited.add(fingerprint))
      return false;
    for (Object raw : Json.list(run.get("events"))) {
      var event = Json.obj(raw);
      if (!fingerprint.equals(event.get("fingerprint"))) continue;
      if ("approve".equals(event.get("action"))
          && "data".equals(event.get("gate"))
          && statement.equals(event.get("human_statement"))) return true;
      var previous = Json.obj(event.get("previous_approval"));
      if ("data_approval_preserved".equals(event.get("action"))
          && statement.equals(previous.get("human_statement"))
          && humanEvidence(run, Json.required(previous, "fingerprint"), statement, visited))
        return true;
    }
    return false;
  }

  private static boolean sameCsvRules(Path before, Path after) {
    try {
      if (!Json.sha(before.resolve("config/rules.json"))
          .equals(Json.sha(after.resolve("config/rules.json")))) return false;
      return csvClasses(before).equals(csvClasses(after));
    } catch (Exception e) {
      return false;
    }
  }

  private static Map<String, Object> csvClasses(Path home) throws java.io.IOException {
    var classes = Json.map();
    try (var jar = new ZipFile(home.resolve("lib/datenportal-integrator.jar").toFile())) {
      var entries = jar.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (!entry.getName().matches("ch/so/agi/integrator/Csv(\\$.*)?\\.class")) continue;
        try (var stream = jar.getInputStream(entry)) {
          classes.put(entry.getName(), Json.hash(stream.readAllBytes()));
        }
      }
    }
    if (classes.isEmpty()) throw new java.io.IOException("CSV implementation unavailable");
    return classes;
  }
}
