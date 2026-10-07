package ch.so.agi.integrator;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Runs existing GRETL scripts in a pinned Jenkins image, without a Jenkins controller. */
public class GretlRuntime {
  public static final String DEFAULT_IMAGE = "sogis/datenportal-jenkins:0.1.0-3";
  final Settings settings;
  final ProcessRunner process;
  final DockerMcps docker;

  public GretlRuntime(Settings settings, ProcessRunner process) {
    this.settings = settings;
    this.process = process;
    docker = new DockerMcps(settings, process);
  }

  static Path file(Settings s) {
    return s.root.resolve(".datenportal-integrator/tools/gretl-runtime/runtime.json");
  }

  public static String reference(Settings s) {
    return Json.str(Json.obj(s.values.get("gretl")), "image", DEFAULT_IMAGE);
  }

  public static Map<String, Object> recorded(Settings s) {
    Path file = file(s);
    if (!Files.isRegularFile(file))
      throw new Problem("gretl_setup_required", "GRETL-Image mit setup-gretl vorbereiten.");
    var record = Json.read(file);
    if (Json.number(record, "schema_version", 0) != 2
        || !reference(s).equals(record.get("reference")))
      throw new Problem(
          "gretl_setup_required",
          "GRETL-Runtime hat einen alten oder abweichenden Stand; setup-gretl ausführen.");
    for (String field : List.of("image_id", "sha256"))
      if (!Json.str(record, field, "")
          .matches(field.equals("image_id") ? "sha256:[a-f0-9]{64}" : "[a-f0-9]{64}"))
        throw new Problem("gretl_setup_required", "GRETL-Runtime-Nachweis ist ungültig.");
    return record;
  }

  public Map<String, Object> selected(boolean inspect) {
    var record = recorded(settings);
    if (inspect) {
      Map<String, Object> actual;
      try {
        actual = docker.inspect(Json.required(record, "image_id"));
      } catch (Problem p) {
        throw translate(p);
      }
      if (!Objects.equals(record.get("os"), actual.get("os"))
          || !Objects.equals(record.get("architecture"), actual.get("architecture")))
        throw new Problem(
            "gretl_runtime_mismatch",
            "GRETL-Image-Plattform stimmt nicht mit dem Prüfstand überein.");
    }
    return record;
  }

  public Map<String, Object> setup(boolean update) {
    try {
      Files.createDirectories(file(settings).getParent());
      try (var channel =
              FileChannel.open(
                  file(settings).resolveSibling(".lock"),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.WRITE);
          var guard = channel.lock()) {
        var previous = Files.isRegularFile(file(settings)) ? Json.read(file(settings)) : Json.map();
        var old = Json.number(previous, "schema_version", 0) == 2 ? previous : Json.map();
        var pin = docker.pin(reference(settings), old, update);
        String name = start(Json.required(pin, "image_id"));
        try {
          pin.putAll(describe(name));
        } finally {
          docker.remove(name);
        }
        pin.put("schema_version", 2);
        if (!previous.isEmpty() && Json.number(previous, "schema_version", 0) != 2)
          Json.atomic(file(settings).resolveSibling("legacy-runtime.json"), previous);
        Json.atomic(file(settings), pin);
        return Json.map(
            "runtime_file",
            file(settings).toString(),
            "sha256",
            pin.get("sha256"),
            "runtime",
            pin,
            "deprecated_settings",
            deprecated(settings));
      }
    } catch (Problem p) {
      throw translate(p);
    } catch (Exception e) {
      throw new Problem(
          "gretl_setup_failed",
          "GRETL-Image konnte nicht vorbereitet werden.",
          "exception",
          e.getClass().getSimpleName());
    }
  }

  static List<String> deprecated(Settings s) {
    return List.of("gretl_java_home", "gretl_offline_jars").stream()
        .filter(s.values::containsKey)
        .toList();
  }

  String start(String image) {
    String name = docker.name("gretl");
    docker.reap();
    docker.register(name);
    try {
      process.checked(
          List.of(
              "docker",
              "run",
              "--rm",
              "-d",
              "--pull=never",
              "--name",
              name,
              "--label",
              "datenportal.integrator.owner=" + docker.owner(),
              "--entrypoint",
              "/bin/bash",
              image,
              "-c",
              "exec sleep infinity"),
          settings.root,
          settings.timeout);
      return name;
    } catch (Problem p) {
      docker.remove(name);
      throw p;
    }
  }

  static final String DESCRIBE =
      """
      set -euo pipefail
      export LC_ALL=C
      : "${GRADLE_JAVA_HOME_17:?}" "${DATENPORTAL_OFFLINE_JARS_DIR:?}" "${GRADLE_USER_HOME:?}"
      test -x "$GRADLE_JAVA_HOME_17/bin/java"
      test -d "$GRADLE_USER_HOME"
      printf '%s\\n' "$GRADLE_JAVA_HOME_17" "$DATENPORTAL_OFFLINE_JARS_DIR" "$GRADLE_USER_HOME"
      "$GRADLE_JAVA_HOME_17/bin/java" -version 2>&1 | awk -F'[".]' '/version/ {print $2; exit}'
      cd "$DATENPORTAL_OFFLINE_JARS_DIR"
      for jar in *.jar; do
        test -f "$jar"
        sha=$(sha256sum "$jar")
        printf '%s=%s\\n' "$jar" "${sha%% *}"
      done
      """;

  Map<String, Object> describe(String container) {
    String text =
        process.checked(
            List.of("docker", "exec", "--user", "jenkins", container, "bash", "-c", DESCRIBE),
            settings.root,
            settings.timeout);
    return parseDescription(text);
  }

  static Map<String, Object> parseDescription(String text) {
    var lines = text.lines().toList();
    if (lines.size() < 5 || !lines.get(3).equals("17"))
      throw new Problem(
          "gretl_runtime_invalid",
          "GRETL-Image enthält keine passende Java-17-Laufzeit mit Bundle.");
    for (int n = 0; n < 3; n++)
      if (!lines.get(n).startsWith("/") || lines.get(n).contains(".."))
        throw new Problem("gretl_runtime_invalid", "GRETL-Image meldet ungültige Runtime-Pfade.");
    var inventory = new ArrayList<>(lines.subList(4, lines.size()));
    if (inventory.stream().anyMatch(v -> !v.matches("[^/\\r\\n=]+\\.jar=[a-f0-9]{64}")))
      throw new Problem("gretl_runtime_invalid", "Ungültiger GRETL-Bundle-Nachweis.");
    inventory.sort(Comparator.comparing(v -> v.substring(0, v.indexOf('='))));
    return Json.map(
        "java_home",
        lines.get(0),
        "jars_directory",
        lines.get(1),
        "gradle_user_home",
        lines.get(2),
        "java_version",
        17,
        "sha256",
        Json.hash((String.join("\n", inventory) + "\n").getBytes(StandardCharsets.UTF_8)));
  }

  public Map<String, Object> matchJenkins() {
    var expected = selected(false);
    String ids =
        process.checked(
            List.of(
                "docker",
                "ps",
                "-q",
                "--filter",
                "label=com.docker.compose.project.working_dir=" + settings.stack,
                "--filter",
                "label=com.docker.compose.service=jenkins"),
            settings.root,
            60);
    if (ids.isBlank())
      throw new Problem(
          "gretl_runtime_missing", "Kein laufender lokaler Jenkins zum Runtime-Abgleich.");
    if (ids.lines().count() != 1)
      throw new Problem("ambiguous_stack", "Mehrere lokale Jenkins-Instanzen.");
    String image =
        process.checked(
            List.of("docker", "container", "inspect", "--format", "{{.Image}}", ids),
            settings.root,
            60);
    var actual = describe(ids);
    if (!expected.get("image_id").equals(image)
        || !expected.get("sha256").equals(actual.get("sha256")))
      throw new Problem(
          "gretl_runtime_mismatch",
          "Lokaler Jenkins und GRETL-Vorprüfung verwenden unterschiedliche Images oder Bundles.",
          "expected_image",
          expected.get("image_id"),
          "actual_image",
          image,
          "expected_bundle",
          expected.get("sha256"),
          "actual_bundle",
          actual.get("sha256"));
    return Json.map("valid", true, "image_id", image, "sha256", actual.get("sha256"));
  }

  public Map<String, Object> doctor() {
    var expected = selected(true);
    String name = start(Json.required(expected, "image_id"));
    try {
      var actual = describe(name);
      return Json.map(
          "valid", expected.get("sha256").equals(actual.get("sha256")), "runtime", expected);
    } finally {
      docker.remove(name);
    }
  }

  record Input(Path source, String target) {}

  static List<String> mapProperties(List<String> properties, List<Input> inputs) {
    var result = new ArrayList<String>();
    for (int n = 0; n < properties.size(); n++) {
      String value = properties.get(n);
      if (value.startsWith("-PdataFile=")) {
        Path file = Path.of(value.substring("-PdataFile=".length())).toAbsolutePath().normalize();
        String target = "/tmp/integrator-inputs/data.csv";
        inputs.add(new Input(file, target));
        result.add("-PdataFile=" + target);
      } else if (value.equals("-I") || value.equals("--init-script")) {
        if (++n >= properties.size())
          throw new Problem("invalid_arguments", "Zusätzliches Init-Script fehlt.");
        String target = "/tmp/integrator-inputs/init-" + n + ".gradle";
        inputs.add(new Input(Path.of(properties.get(n)).toAbsolutePath().normalize(), target));
        result.add(value);
        result.add(target);
      } else result.add(value);
    }
    return result;
  }

  public Map<String, Object> gradle(
      Workflow w,
      Map<String, Object> run,
      Path snapshot,
      String task,
      List<String> properties,
      String logName) {
    var runtime = selected(true);
    Path log = w.directory(run).resolve("validation").resolve(logName + ".log");
    Path reports = w.directory(run).resolve("validation").resolve(logName + "-reports");
    String name = start(Json.required(runtime, "image_id"));
    Map<String, Object> report = null;
    Problem failure = null;
    try {
      var actual = describe(name);
      if (!runtime.get("sha256").equals(actual.get("sha256")))
        throw new Problem(
            "gretl_runtime_mismatch", "GRETL-Bundle weicht vom eingerichteten Image ab.");
      var inputs = new ArrayList<Input>();
      var mapped = mapProperties(properties, inputs);
      process.checked(
          List.of(
              "docker",
              "exec",
              "--user",
              "root",
              name,
              "mkdir",
              "-p",
              "/tmp/integrator-work",
              "/tmp/integrator-inputs"),
          settings.root,
          settings.timeout);
      process.checked(
          List.of("docker", "cp", snapshot + "/.", name + ":/tmp/integrator-work/"),
          settings.root,
          settings.timeout);
      for (var input : inputs) {
        if (!Files.isRegularFile(input.source()))
          throw new Problem(
              "file_missing", "GRETL-Prüfdatei fehlt.", "path", input.source().toString());
        process.checked(
            List.of("docker", "cp", input.source().toString(), name + ":" + input.target()),
            settings.root,
            settings.timeout);
      }
      process.checked(
          List.of(
              "docker",
              "exec",
              "--user",
              "root",
              name,
              "chown",
              "-R",
              "jenkins:jenkins",
              "/tmp/integrator-work",
              "/tmp/integrator-inputs"),
          settings.root,
          settings.timeout);
      String org = Json.required(run, "organization");
      var args =
          new ArrayList<>(
              List.of(
                  "docker",
                  "exec",
                  "--user",
                  "jenkins",
                  "-w",
                  "/tmp/integrator-work/" + org,
                  name,
                  "bash",
                  "/tmp/integrator-work/shared/bin/gradlew-java17.sh",
                  "--no-daemon",
                  "--console=plain",
                  "-I",
                  "/tmp/integrator-work/shared/gradle/init.gradle",
                  task));
      args.addAll(mapped);
      var result = process.run(args, settings.root, settings.timeout, log);
      report =
          Json.map(
              "valid",
              result.exitCode() == 0,
              "returncode",
              result.exitCode(),
              "diagnostics",
              result.exitCode() == 0
                  ? List.of()
                  : result
                      .output()
                      .lines()
                      .skip(Math.max(0, result.output().lines().count() - 60))
                      .toList(),
              "log",
              log.toString(),
              "log_sha256",
              Json.sha(log),
              "workspace",
              snapshot.toString(),
              "reports",
              reports.toString(),
              "runtime",
              runtime,
              "messages",
              result
                  .output()
                  .lines()
                  .filter(
                      l ->
                          l.contains("Error")
                              || l.contains("Warning")
                              || l.contains("FAILED")
                              || l.contains("Exception"))
                  .limit(100)
                  .toList());
      return report;
    } catch (Problem p) {
      failure = translate(p);
      failure.details.put("reports", reports.toString());
      throw failure;
    } finally {
      boolean copied = false;
      try {
        Files.createDirectories(reports);
        var capture =
            process.run(
                List.of(
                    "docker",
                    "cp",
                    name
                        + ":/tmp/integrator-work/"
                        + Json.required(run, "organization")
                        + "/build/.",
                    reports.toString()),
                settings.root,
                Math.min(settings.timeout, 30),
                null);
        copied = capture.exitCode() == 0;
      } catch (Exception ignored) {
      }
      if (report != null) report.put("reports_copied", copied);
      if (failure != null) failure.details.put("reports_copied", copied);
      docker.remove(name);
    }
  }

  static Problem translate(Problem p) {
    return p.code.startsWith("mcp_")
        ? new Problem(
            p.code.replace("mcp_", "gretl_"),
            "GRETL-Image nicht verfügbar; Docker und setup-gretl prüfen.",
            "details",
            p.details)
        : p;
  }
}
