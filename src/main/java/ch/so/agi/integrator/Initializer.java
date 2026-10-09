package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Repeatable technical setup. Never seeds, publishes, or approves real business data. */
final class Initializer {
  final Settings s;
  final Path report;

  Initializer(Settings s) {
    this.s = s;
    report = s.root.resolve(".datenportal-integrator/init.json");
  }

  static void prepareConfig(String explicit) {
    if (explicit != null || System.getenv("DATENPORTAL_INTEGRATOR_CONFIG") != null) return;
    Path target = Path.of("config/local.toml");
    if (Files.exists(target)) return;
    try {
      Files.copy(Path.of("config/local.example.toml"), target);
    } catch (FileAlreadyExistsException ignored) {
    } catch (java.io.IOException e) {
      throw new Problem(
          "configuration_missing", "Standardkonfiguration konnte nicht erstellt werden.");
    }
  }

  Map<String, Object> run(List<String> args) {
    if (args.stream().anyMatch(a -> !Set.of("--update-tools", "--skip-smoke").contains(a)))
      throw new Problem("invalid_arguments", "init [--update-tools] [--skip-smoke]");
    try (var guard = RuntimeGuard.acquire(report.resolveSibling("init.lock"), s.stackTimeout)) {
      var state = Files.isRegularFile(report) ? Json.read(report) : Json.map("steps", Json.map());
      state.put("ready", false);
      state.remove("problem");
      Json.atomic(report, state);
      try {
        step(state, "runtime", AgentRuntime.check(s));
        var setup = new Setup(s);
        step(state, "tools", setup.tools());
        var update = args.contains("--update-tools") ? List.of("--update") : List.<String>of();
        step(state, "mcps", setup.run("setup-mcps", update));
        step(state, "gretl", setup.run("setup-gretl", update));
        step(state, "services", setup.run("runtime-up", List.of()));
        step(state, "harness", new Harness(s).install());
        String fingerprint = fingerprint();
        var old = Json.obj(Json.obj(state.get("steps")).get("smoke"));
        if (args.contains("--skip-smoke")) {
          step(state, "smoke", Json.map("valid", false, "skipped", true));
        } else if (!smokeCurrent(old, fingerprint)) {
          var smoke = new Smoke(s).run();
          smoke.put("fingerprint", fingerprint);
          step(state, "smoke", smoke);
        }
        state.put(
            "ready",
            Json.bool(Json.obj(Json.obj(state.get("steps")).get("smoke")), "valid", false));
        state.put("updated_at", Json.now());
        Json.atomic(report, state);
        return Json.map(
            "ready", state.get("ready"), "report", report.toString(), "steps", state.get("steps"));
      } catch (Problem e) {
        state.put("problem", e.result());
        Json.atomic(report, state);
        throw new Problem(
            "init_incomplete",
            "Einrichtung nach Korrektur mit init fortsetzen.",
            "cause",
            e.result(),
            "report",
            report.toString());
      } catch (RuntimeException e) {
        state.put(
            "problem", Json.map("error", "init_failed", "exception", e.getClass().getSimpleName()));
        Json.atomic(report, state);
        throw new Problem(
            "init_incomplete",
            "Einrichtung fehlgeschlagen; Bericht prüfen und init fortsetzen.",
            "report",
            report.toString());
      }
    }
  }

  void step(Map<String, Object> state, String name, Object value) {
    Json.obj(state.computeIfAbsent("steps", k -> Json.map())).put(name, value);
    state.put("last_completed_step", name);
    Json.atomic(report, state);
  }

  String fingerprint() {
    var hashes = Json.map();
    for (String path :
        List.of(
            "build/libs/datenportal-integrator.jar",
            "runtime/Dockerfile",
            "tests/fixtures/dataset.xtf",
            "tests/fixtures/offices.xtf")) {
      hashes.put(path, Json.sha(s.root.resolve(path)));
    }
    for (Path directory : List.of(s.topics.resolve("shared"), s.topics.resolve("agi"))) {
      try (var paths = Files.walk(directory)) {
        for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
          String relative = directory.relativize(file).toString();
          if (relative.startsWith(".gradle/") || relative.startsWith("build/")) continue;
          if (file.toString().endsWith(".gradle")
              || file.toString().endsWith(".properties")
              || file.toString().endsWith(".sh")
              || file.getFileName().toString().equals("gradlew")
              || file.getFileName().toString().equals("gradle-wrapper.jar"))
            hashes.put(file.toString(), Json.sha(file));
        }
      } catch (java.io.IOException e) {
        throw new Problem(
            "smoke_source_missing", "GRETL-Prüfgrundlage fehlt.", "path", directory.toString());
      }
    }
    hashes.put("settings", s.fingerprint());
    hashes.put("offices", Json.sha(s.topics.resolve("shared/data/offices.xtf")));
    hashes.put("agent_image", System.getenv("DATENPORTAL_RUNTIME_IMAGE"));
    hashes.put("java", System.getProperty("java.runtime.version"));
    return Json.digest(hashes);
  }

  Map<String, Object> status() {
    if (!Files.isRegularFile(report)) return Json.map("ready", false, "reason", "init_required");
    var state = Json.read(report);
    var smoke = Json.obj(Json.obj(state.get("steps")).get("smoke"));
    try {
      boolean current = smokeCurrent(smoke, fingerprint());
      return Json.map(
          "ready",
          current && Json.bool(state, "ready", false),
          "smoke_current",
          current,
          "report",
          report.toString());
    } catch (Problem e) {
      return Json.map("ready", false, "problem", e.result(), "report", report.toString());
    }
  }

  static boolean smokeCurrent(Map<String, Object> smoke, String fingerprint) {
    if (!Json.bool(smoke, "valid", false) || !Objects.equals(smoke.get("fingerprint"), fingerprint))
      return false;
    String path = Json.str(smoke, "report", "");
    return !path.isBlank()
        && Files.isRegularFile(Path.of(path))
        && Objects.equals(smoke.get("report_sha256"), Json.sha(Path.of(path)));
  }
}
