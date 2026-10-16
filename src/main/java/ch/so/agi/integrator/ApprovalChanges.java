package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Hash-only explanation of review identities; never changes approval policy. */
final class ApprovalChanges {
  static Map<String, Object> snapshot(Workflow w, Map<String, Object> run, String gate) {
    var entries = Json.map();
    if (Offices.standalone(run)) {
      entries.put("office", new Offices(w).fingerprint(run));
    } else {
      w.fingerprintParts(run, gate, w.settings.fingerprint())
          .forEach(
              (key, value) -> {
                if (Set.of("context", "files", "changes").contains(key)) {
                  Json.obj(value)
                      .forEach(
                          (path, hash) ->
                              entries.put(
                                  key + "/" + path,
                                  hash instanceof String ? hash : Json.digest(hash)));
                } else entries.put(key, Json.digest(value));
              });
    }
    if (!gate.equals("data"))
      Json.obj(run.get("changes"))
          .forEach(
              (relative, value) -> {
                var change = Json.obj(value);
                Path target = Json.inside(w.settings.topics, relative);
                String actual = Files.isRegularFile(target) ? Json.sha(target) : null;
                boolean expected =
                    Objects.equals(actual, change.get("before_sha256"))
                        || Objects.equals(actual, change.get("sha256"));
                entries.put(
                    "repository_candidate/" + relative, expected ? change.get("sha256") : actual);
              });
    return Json.map("version", 1, "entries", entries);
  }

  static Map<String, Object> describe(Workflow w, Map<String, Object> run, String gate) {
    var approval = Json.obj(Json.obj(run.get("approvals")).get(gate));
    var result = Json.map("gate", gate);
    if (approval.isEmpty()) return Json.map("gate", gate, "state", "not_approved");
    var before = Json.obj(approval.get("fingerprint_components"));
    if (Json.number(before, "version", 0) != 1) {
      result.put("state", "baseline_unavailable");
      result.put(
          "message",
          "Änderungsursache nicht rekonstruierbar; historischer Vergleichssnapshot fehlt.");
      return result;
    }
    var old = Json.obj(before.get("entries"));
    var now = Json.obj(snapshot(w, run, gate).get("entries"));
    var keys = new TreeSet<>(old.keySet());
    keys.addAll(now.keySet());
    var changes = new ArrayList<Object>();
    boolean candidate = false;
    for (String key : keys) {
      if (Objects.equals(old.get(key), now.get(key))) continue;
      String kind = "component";
      if (key.startsWith("context/"))
        kind = key.startsWith("context/shared/") ? "shared_runtime" : "organization_context";
      if (key.startsWith("changes/") || key.startsWith("repository_candidate/")) {
        kind = "topic_candidate";
        candidate = true;
      }
      if (key.startsWith("files/")) {
        kind = "input_file";
        candidate = true;
      }
      changes.add(
          Json.map(
              "component",
              key,
              "kind",
              kind,
              "before_sha256",
              old.get(key),
              "sha256",
              now.get(key)));
    }
    result.put("state", changes.isEmpty() ? "unchanged" : "changed");
    result.put("changes", changes);
    if (!changes.isEmpty())
      result.put(
          "required_action",
          candidate
              ? "Geänderte Eingaben/Themenkandidaten im zugehörigen Reparaturpfad prüfen; manuelle Änderungen nicht überschreiben. Danach Prüfungen und Freigaben erneuern."
              : "Prüfkontext geändert; bestehende Themenbytes beibehalten. Prüfungen und Freigaben erneuern, keine Modellableitung allein wegen der Kontextänderung.");
    return result;
  }

  static Map<String, Object> all(Workflow w, Map<String, Object> run) {
    var result = Json.map();
    for (String gate : Json.obj(run.get("approvals")).keySet()) {
      try {
        result.put(gate, describe(w, run, gate));
      } catch (Problem e) {
        result.put(
            gate,
            Json.map(
                "gate",
                gate,
                "state",
                "comparison_blocked",
                "error",
                e.code,
                "path",
                e.details.get("path"),
                "required_action",
                "Konkrete Dateiabweichung prüfen; manuelle Änderungen nicht überschreiben.",
                "message",
                e.getMessage()));
      }
    }
    return result;
  }
}
