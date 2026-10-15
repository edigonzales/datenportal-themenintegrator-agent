package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Content identities and immutable snapshots of checks; paths are not version identities. */
final class CheckStates {
  static Path snapshot(Path directory, Path source) {
    if (source == null) return null;
    String hash = Json.sha(source);
    Path target = directory.resolve("check-inputs").resolve(hash + "-" + source.getFileName());
    try {
      Files.createDirectories(target.getParent());
      if (!Files.exists(target)) Files.copy(source, target);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Prüfdatei kann nicht gesichert werden.");
    }
    if (!hash.equals(Json.sha(target)) || !hash.equals(Json.sha(source)))
      throw new Problem("artifact_changed", "Prüfdatei während Sicherung verändert.");
    return target;
  }

  static Map<String, Object> manifest(Workflow w, Map<String, Object> run, String gate) {
    if (Offices.standalone(run)) return new Offices(w).manifest(run);
    var files = Json.map();
    for (String role : Json.obj(run.get("files")).keySet()) {
      if (gate.equals("data") && !role.equals("data")) continue;
      files.put(role, Json.sha(w.store.file(run, role)));
    }
    var repository = Json.map();
    if (!gate.equals("data")) {
      var paths = new TreeSet<String>(Json.obj(run.get("changes")).keySet());
      paths.add("shared/data/offices.xtf");
      for (String dir : List.of("shared", Json.required(run, "organization"))) {
        Path base = w.settings.topics.resolve(dir);
        if (Files.isDirectory(base))
          try (var walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                .filter(
                    p ->
                        !p.toString().contains("/.git/")
                            && !p.toString().contains("/.gradle/")
                            && !p.toString().contains("/build/"))
                .forEach(p -> paths.add(w.settings.topics.relativize(p).toString()));
          } catch (java.io.IOException e) {
            throw new Problem("repository_unavailable", "Prüfabhängigkeiten nicht lesbar.");
          }
      }
      if (run.get("identifier") != null) paths.add(w.topic(run) + "/dataset.gradle");
      for (String relative : paths) {
        Path p = w.repoFile(run, relative);
        repository.put(relative, Files.isRegularFile(p) ? Json.sha(p) : null);
        var change = Json.obj(Json.obj(run.get("changes")).get(relative));
        if (!change.isEmpty()) {
          Path target = w.settings.topics.resolve(relative);
          String actual = Files.isRegularFile(target) ? Json.sha(target) : null;
          if (!Objects.equals(actual, change.get("before_sha256"))
              && !Objects.equals(actual, change.get("sha256")))
            throw new Problem(
                "repository_conflict",
                "Repository-Datei seit Vorbereitung verändert.",
                "path",
                relative);
        }
      }
    }
    var components =
        Json.map(
            "integrator",
            component(AgentResources.jar(w.settings.root)),
            "java",
            Runtime.version().toString());
    if (!gate.equals("data")) {
      components.put("interlis", w.interlis.identity());
      components.put("datasheet", w.datasheets.identity());
      components.put(
          "ilivalidator",
          component(AgentResources.resolve(w.settings.root, "lib/ilivalidator.jar")));
    }
    var model = Json.obj(run.get("model"));
    return Json.map(
        "version",
        1,
        "files",
        files,
        "repository",
        repository,
        "components",
        components,
        "parameters",
        gate.equals("data")
            ? Json.map(
                "rules", component(AgentResources.resolve(w.settings.root, "config/rules.json")))
            : Json.map(
                "settings",
                w.settings.fingerprint(),
                "profile",
                model.get("profile"),
                "purpose",
                model.get("purpose"),
                "task",
                model.get("task_configuration")));
  }

  private static String component(Path path) {
    return Files.isRegularFile(path) ? Json.sha(path) : "unavailable";
  }

  static void record(
      Workflow w,
      Map<String, Object> run,
      String key,
      Map<String, Object> report,
      Map<String, Object> started) {
    String gate = key.equals("model_preparation") ? "model" : key;
    report.put("manifest", started);
    boolean current;
    try {
      current = Json.digest(started).equals(Json.digest(manifest(w, run, gate)));
    } catch (Problem changed) {
      current = false;
    }
    report.put("state", current ? "current" : "stale");
    if (!current) {
      report.put("valid", false);
      report.put("approvable", false);
      var errors = new ArrayList<>(Json.list(report.get("errors")));
      errors.add(
          Json.map(
              "code",
              "check_inputs_changed",
              "message",
              "Dateien oder Prüfkomponenten während der Prüfung verändert. Erneut prüfen."));
      report.put("errors", errors);
    }
    var checks = Json.obj(run.get("checks"));
    if (checks.containsKey(key)) {
      var history = Json.obj(run.computeIfAbsent("check_history", k -> Json.map()));
      Json.list(history.computeIfAbsent(key, k -> new ArrayList<>()))
          .add(Json.read(Json.text(checks.get(key))));
    }
    checks.put(key, Json.read(Json.text(report)));
    if (current && Json.bool(report, "valid", false))
      Json.obj(run.computeIfAbsent("last_successful_checks", k -> Json.map()))
          .put(key, Json.read(Json.text(report)));
  }

  static boolean current(
      Workflow w, Map<String, Object> run, String gate, Map<String, Object> report) {
    try {
      if (!report.containsKey("manifest"))
        return gate.equals("data")
            && Objects.equals(report.get("fingerprint"), w.fingerprint(run, "data"));
      return !"stale".equals(report.get("state"))
          && Json.digest(report.get("manifest")).equals(Json.digest(manifest(w, run, gate)));
    } catch (Problem changed) {
      return false;
    }
  }

  static Map<String, Object> overview(Workflow w, Map<String, Object> run) {
    var result = Json.map();
    Json.obj(run.get("checks"))
        .forEach(
            (key, raw) -> {
              var check = Json.obj(raw);
              String gate = key.equals("model_preparation") ? "model" : key;
              boolean same = current(w, run, gate, check);
              Map<String, Object> actual;
              try {
                actual = manifest(w, run, gate);
              } catch (Problem problem) {
                actual = problem.result();
              }
              var differences = new ArrayList<Object>();
              differences("", Json.obj(check.get("manifest")), actual, differences);
              result.put(
                  key,
                  Json.map(
                      "state",
                      same ? "current" : "stale",
                      "valid",
                      same && Json.bool(check, "valid", false),
                      "checked_manifest",
                      check.get("manifest"),
                      "current_manifest",
                      actual,
                      "differences",
                      differences,
                      "next",
                      same ? "Prüfergebnis beachten" : "Aktuellen Dateistand erneut prüfen",
                      "last_successful_manifest",
                      Json.obj(Json.obj(run.get("last_successful_checks")).get(key))
                          .get("manifest")));
            });
    return result;
  }

  private static void differences(
      String prefix, Map<String, Object> before, Map<String, Object> after, List<Object> result) {
    var keys = new TreeSet<String>(before.keySet());
    keys.addAll(after.keySet());
    for (String key : keys) {
      Object a = before.get(key), b = after.get(key);
      if (Objects.equals(a, b)) continue;
      String field = prefix.isEmpty() ? key : prefix + "." + key;
      if (a instanceof Map && b instanceof Map)
        differences(field, Json.obj(a), Json.obj(b), result);
      else result.add(Json.map("artifact", field, "checked", a, "current", b));
    }
  }
}
