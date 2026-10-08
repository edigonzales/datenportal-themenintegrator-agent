package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ComposeRuntimeTest {
  @TempDir Path temp;
  static final String IMAGE = "sha256:" + "a".repeat(64);

  Settings settings(String extra) {
    Path config = temp.resolve("local.toml");
    Json.write(config, "root=\".\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n" + extra);
    return new Settings(config);
  }

  class Docker extends ProcessRunner {
    final List<List<String>> calls = new ArrayList<>();
    boolean present, running, unavailable;
    String image = IMAGE,
        owner = temp.toString(),
        volume = "datenportal-integrator-gradle-" + "a".repeat(64);

    @Override
    public Result run(List<String> args, Path cwd, int seconds, Path log) {
      calls.add(args);
      if (unavailable) return new Result(1, "FIXTURE Docker unavailable");
      if (args.contains(GretlRuntime.DESCRIBE)) return new Result(0, GretlRuntimeTest.DESCRIPTION);
      if (args.get(1).equals("image"))
        return new Result(
            0, Json.text(Json.map("image_id", IMAGE, "os", "linux", "architecture", "arm64")));
      if (args.get(1).equals("ps")) return new Result(0, present ? "worker" : "");
      if (args.get(1).equals("container"))
        return new Result(
            0,
            Json.text(
                Json.map(
                    "id",
                    "worker",
                    "image_id",
                    image,
                    "state",
                    running ? "running" : "exited",
                    "health",
                    "healthy",
                    "root",
                    owner,
                    "mounts",
                    List.of(
                        Json.map(
                            "Destination", "/var/lib/integrator", "Name", volume, "RW", true)))));
      if (args.contains("up")) {
        present = true;
        running = true;
      }
      return new Result(0, "");
    }
  }

  ComposeRuntime runtime(Docker docker) {
    var s = settings("[gretl]\nmode=\"compose\"\nimage=\"sogis/jenkins:v1\"\n");
    Json.atomic(
        GretlRuntime.file(s),
        Json.map(
            "schema_version",
            2,
            "reference",
            "sogis/jenkins:v1",
            "image_id",
            IMAGE,
            "sha256",
            GretlRuntime.parseDescription(GretlRuntimeTest.DESCRIPTION).get("sha256"),
            "os",
            "linux",
            "architecture",
            "arm64"));
    return new ComposeRuntime(s, docker);
  }

  @Test
  void startsOnceReusesAndPinsWithoutTouchingPublicationStack() {
    var docker = new Docker();
    var runtime = runtime(docker);
    assertEquals("worker", runtime.ensure("gretl"));
    assertEquals("worker", runtime.ensure("gretl"));
    assertEquals(1, docker.calls.stream().filter(c -> c.contains("up")).count());
    assertFalse(
        docker.calls.stream()
            .anyMatch(c -> c.contains("pull") || c.contains("scripts/up.sh") || c.contains("rm")));
    var rendered = Json.read(runtime.home.resolve("compose.json"));
    assertEquals(IMAGE, Json.obj(Json.obj(rendered.get("services")).get("gretl")).get("image"));
    assertTrue(Files.exists(temp.resolve("compose.override.yaml")));
  }

  @Test
  void refusesForeignImageWorkspaceAndCacheBeforeStartOrRestart() {
    var docker = new Docker();
    var runtime = runtime(docker);
    docker.present = true;
    docker.image = "sha256:" + "c".repeat(64);
    assertEquals(
        "runtime_mismatch", assertThrows(Problem.class, () -> runtime.ensure("gretl")).code);
    docker.image = IMAGE;
    docker.owner = "/foreign";
    assertEquals(
        "runtime_mismatch", assertThrows(Problem.class, () -> runtime.ensure("gretl")).code);
    docker.owner = temp.toString();
    docker.volume = "jenkins-home";
    assertEquals(
        "runtime_mismatch", assertThrows(Problem.class, () -> runtime.ensure("gretl")).code);
    assertFalse(docker.calls.stream().anyMatch(c -> c.contains("up") || c.contains("restart")));
  }

  @Test
  void preservesUnmanagedComposeOverride() {
    var runtime = runtime(new Docker());
    Json.write(temp.resolve("compose.override.yaml"), "# user configuration\n");
    assertEquals(
        "runtime_configuration_conflict", assertThrows(Problem.class, runtime::prepare).code);
    assertEquals("# user configuration\n", Json.contents(temp.resolve("compose.override.yaml")));
  }

  @Test
  void validatesModesAndLocalManagedHttpWhileKeepingLegacyDefaults() {
    assertEquals("ephemeral", GretlRuntime.mode(settings("[gretl]\nimage=\"image:v1\"\n")));
    assertThrows(Problem.class, () -> settings("[gretl]\nmode=\"jenkins\"\n"));
    assertThrows(
        Problem.class,
        () ->
            settings(
                "[datasheet]\ntransport=\"http\"\nmanaged=true\nimage=\"image:v1\"\nurl=\"https://remote.example:8000/mcp\"\n"));
    assertThrows(
        Problem.class,
        () ->
            settings(
                "[datasheet]\ntransport=\"http\"\nmanaged=true\nimage=\"image:v1\"\nurl=\"http://127.0.0.1/mcp\"\n"));
    var external =
        settings("[datasheet]\ntransport=\"http\"\nurl=\"https://remote.example/mcp\"\n");
    assertFalse(ComposeRuntime.managed(external, "datasheet"));
  }

  @Test
  void dockerFailureCannotTriggerContainerMutation() {
    var docker = new Docker();
    var runtime = runtime(docker);
    docker.unavailable = true;
    assertThrows(Problem.class, () -> runtime.ensure("gretl"));
    assertFalse(docker.calls.stream().anyMatch(c -> c.contains("up") || c.contains("restart")));
  }

  @Test
  void guardSerializesThreadsAndExpires() {
    Path lock = temp.resolve("guard.lock");
    try (var first = RuntimeGuard.acquire(lock, 1)) {
      assertEquals(
          "runtime_locked", assertThrows(Problem.class, () -> RuntimeGuard.acquire(lock, 1)).code);
    }
    assertDoesNotThrow(
        () -> {
          try (var second = RuntimeGuard.acquire(lock, 1)) {}
        });
  }
}
