package ch.so.agi.integrator;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;

/** Standalone cold init acceptance, with a private configuration and Compose project. */
public class InitAcceptance {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]).toAbsolutePath();
    Settings source = Settings.load(root.resolve("config/local.toml").toString());
    Path fixture = root.resolve(".datenportal-integrator/init-acceptance/" + UUID.randomUUID());
    Files.createDirectories(fixture);
    for (String directory : List.of("validation", "templates", "runtime", "tests/fixtures", "bin"))
      Workspace.copy(root.resolve(directory), fixture.resolve(directory));
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
            System.getProperty("java.home") + "/bin/java",
            "-jar",
            fixture.resolve("build/libs/datenportal-integrator.jar").toString());
    Settings settings = null;
    try {
      var explicit = new ArrayList<>(command);
      explicit.addAll(List.of("--config", "config/missing.toml", "init"));
      var missing = runner.run(explicit, fixture, 30, null);
      if (missing.exitCode() == 0 || Files.exists(fixture.resolve("config/local.toml")))
        throw new AssertionError("Explicit missing configuration must not create defaults");
      var init = new ArrayList<>(command);
      init.add("init");
      String first = runner.checked(init, fixture, 1200);
      Json.write(fixture.resolve("first.log"), first);
      settings = new Settings(fixture.resolve("config/local.toml"));
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
              "explicit_missing_rejected",
              true,
              "repeat_reused",
              true,
              "configuration_unchanged",
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
