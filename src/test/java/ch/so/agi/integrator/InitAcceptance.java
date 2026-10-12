package ch.so.agi.integrator;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;

/** Standalone cold init acceptance, with a private configuration and Compose project. */
public class InitAcceptance {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]).toAbsolutePath();
    Settings source =
        Settings.load(args.length > 1 ? args[1] : root.resolve("config/local.toml").toString());
    Path fixture = root.resolve(".datenportal-integrator/init-acceptance/" + UUID.randomUUID());
    Files.createDirectories(fixture);
    for (String directory :
        List.of("validation", "templates", "runtime", "tests/fixtures", "bin", "src", "gradle"))
      Workspace.copy(root.resolve(directory), fixture.resolve(directory));
    for (String file :
        List.of("build.gradle", "settings.gradle", "gradle.lockfile", "gradlew", ".dockerignore"))
      Files.copy(root.resolve(file), fixture.resolve(file), StandardCopyOption.COPY_ATTRIBUTES);
    Files.createDirectories(fixture.resolve("config"));
    try (var files = Files.list(root.resolve("config"))) {
      for (Path file : files.filter(Files::isRegularFile).toList())
        if (!file.getFileName().toString().equals("local.toml"))
          Files.copy(file, fixture.resolve("config").resolve(file.getFileName()));
    }
    Files.copy(root.resolve("compose.yaml"), fixture.resolve("compose.yaml"));
    Files.createDirectories(fixture.resolve("build/libs"));
    Files.copy(
        root.resolve("build/libs/datenportal-integrator.jar"),
        fixture.resolve("build/libs/datenportal-integrator.jar"));
    Path example = fixture.resolve("config/local.example.toml");
    String config =
        Json.contents(example)
            .replace(
                "topics_repo = \"../datenportal-themenrepo\"",
                "topics_repo = " + Json.text(source.topics.toString()))
            .replace(
                "stack_repo = \"../datenportal-dev-stack\"",
                "stack_repo = " + Json.text(source.stack.toString()))
            .replace("127.0.0.1:8000", "127.0.0.1:" + port())
            .replace("127.0.0.1:8080", "127.0.0.1:" + port())
            .replace(
                "project = \"datenportal-integrator\"",
                "project = \"init-test-" + UUID.randomUUID().toString().substring(0, 8) + "\"")
            .replace("enabled = true", "enabled = false");
    Json.write(example, config);
    ProcessRunner runner = new ProcessRunner();
    var command =
        List.of(
            fixture.resolve("bin/datenportal-agent").toString(),
            "--runtime",
            "docker-build",
            "--mount-rw",
            root.getParent().toString());
    Settings settings = null;
    try {
      var explicit = new ArrayList<>(command);
      explicit.addAll(List.of("--config", "config/missing.toml", "init"));
      var missing = runner.run(explicit, fixture, 300, null);
      if (missing.exitCode() == 0 || Files.exists(fixture.resolve("config/local.toml")))
        throw new AssertionError("Explicit missing configuration must not create defaults");
      var init = new ArrayList<>(command);
      init.add("init");
      String first = runner.checked(init, fixture, 1200);
      Json.write(fixture.resolve("first.log"), first);
      settings = new Settings(fixture.resolve("config/local.toml"));
      if (Files.mismatch(example, settings.file) != -1)
        throw new AssertionError("Cold init did not copy the configured defaults byte-for-byte");
      Settings defaults = new Settings(root.resolve("config/local.example.toml"));
      for (String tool : List.of("datasheet", "interlis", "gretl"))
        if (!Json.obj(defaults.values.get(tool))
            .get("image")
            .equals(Json.obj(settings.values.get(tool)).get("image")))
          throw new AssertionError("Cold init selected different image: " + tool);
      Path report = fixture.resolve(".datenportal-integrator/init.json");
      var initial = Json.read(report);
      if (!Json.bool(initial, "ready", false)) throw new AssertionError("Cold init failed");
      String configHash = Json.sha(settings.file);
      Object smoke = Json.obj(initial.get("steps")).get("smoke");
      Json.write(fixture.resolve("repeat.log"), runner.checked(init, fixture, 300));
      if (!smoke.equals(Json.obj(Json.read(report).get("steps")).get("smoke")))
        throw new AssertionError("Unchanged init did not reuse smoke");
      if (!configHash.equals(Json.sha(settings.file)))
        throw new AssertionError("Configuration changed");
      var doctorCommand = new ArrayList<>(command);
      doctorCommand.add("doctor");
      Path doctorOutput = fixture.resolve("doctor.json");
      var doctorProcess =
          new ProcessBuilder(doctorCommand)
              .directory(fixture.toFile())
              .redirectError(fixture.resolve("doctor.log").toFile())
              .redirectOutput(doctorOutput.toFile())
              .start();
      if (!doctorProcess.waitFor(300, java.util.concurrent.TimeUnit.SECONDS)) {
        doctorProcess.destroyForcibly();
        throw new AssertionError("Doctor timed out");
      }
      if (doctorProcess.exitValue() != 0) throw new AssertionError("Doctor failed");
      var doctor = Json.read(doctorOutput);
      Json.atomic(fixture.resolve("doctor.json"), doctor);
      if (!Json.bool(Json.obj(doctor.get("initialization")), "ready", false)
          || !Json.bool(Json.obj(doctor.get("datasheet_mcp")), "valid", false)
          || !Json.bool(Json.obj(doctor.get("interlis_mcp")), "valid", false))
        throw new AssertionError("Doctor did not confirm current readiness");
      for (boolean codex : List.of(true, false)) {
        try (var client = new DeliveryAcceptance(settings).client(settings, codex)) {
          if (client.listTools().tools().size() != Operations.ALL.size())
            throw new AssertionError("Generated MCP configuration did not expose current tools");
        }
      }
      init.add("--skip-smoke");
      Json.write(fixture.resolve("skip.log"), runner.checked(init, fixture, 300));
      if (Json.bool(Json.read(report), "ready", true))
        throw new AssertionError("Skipped smoke marked ready");
      Json.atomic(
          fixture.resolve("acceptance.json"),
          Json.map(
              "valid",
              true,
              "synthetic_fixture",
              true,
              "default_created",
              true,
              "default_images_preserved",
              true,
              "explicit_missing_rejected",
              true,
              "repeat_reused",
              true,
              "configuration_unchanged",
              true,
              "docker_launcher",
              true,
              "doctor_ready",
              true,
              "generated_clients_connected",
              true,
              "skip_not_ready",
              true));
      System.out.println(fixture.resolve("acceptance.json"));
    } finally {
      if (settings == null && Files.isRegularFile(fixture.resolve("config/local.toml")))
        settings = new Settings(fixture.resolve("config/local.toml"));
      if (settings != null)
        runner.run(new ComposeRuntime(settings, runner).command("down", "-v"), fixture, 120, null);
    }
  }

  static int port() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
