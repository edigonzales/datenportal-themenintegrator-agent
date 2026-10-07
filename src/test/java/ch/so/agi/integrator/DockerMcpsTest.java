package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DockerMcpsTest {
  @TempDir Path temp;
  static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64);

  Settings config(String extra) throws Exception {
    Path file = temp.resolve("local.toml");
    Json.write(file, "root=\".\"\ntopics_repo=\"repo\"\nstack_repo=\"stack\"\n" + extra);
    Files.createDirectories(temp.resolve("config"));
    Files.createDirectories(temp.resolve("validation"));
    return new Settings(file);
  }

  String dockerConfig() {
    return "[datasheet]\ntransport=\"stdio\"\nimage=\"sogis/datasheet:v1\"\n[interlis]\ntransport=\"stdio\"\nimage=\"sogis/interlis:v1\"\n";
  }

  class Docker extends ProcessRunner {
    final List<List<String>> calls = new ArrayList<>();
    String tag = A;
    boolean missingModel, failPull, imageMissing;
    String owner;

    @Override
    public Result run(List<String> args, Path cwd, int seconds, Path log) {
      calls.add(args);
      if (log != null) Json.write(log, "FIXTURE Docker operation\n");
      if (args.subList(1, Math.min(3, args.size())).equals(List.of("image", "inspect"))) {
        if (imageMissing) return new Result(1, "missing");
        String ref = args.getLast(), id = ref.startsWith("sha256:") ? ref : tag;
        return new Result(
            0,
            Json.text(
                Json.map(
                    "image_id",
                    id,
                    "digests",
                    List.of("sogis/datasheet@" + id),
                    "os",
                    "linux",
                    "architecture",
                    "arm64")));
      }
      if (args.get(1).equals("create")) {
        owner = args.get(args.indexOf("--label") + 1).split("=", 2)[1];
        return new Result(0, "fixture-container");
      }
      if (args.get(1).equals("cp")) {
        Path dest = Path.of(args.getLast());
        for (String n : DockerMcps.MODELS)
          if (!missingModel || !n.contains("Base"))
            Json.write(
                dest.resolve(n), Json.contents(Fixtures.FIXTURES.resolve("models").resolve(n)));
      }
      if (args.get(1).equals("pull")) return new Result(failPull ? 1 : 0, "fixture-pull");
      if (args.get(1).equals("container"))
        return new Result(owner == null ? 1 : 0, owner == null ? "" : owner);
      return new Result(0, "");
    }
  }

  @Test
  void retainsLegacyAndRejectsConflictingConfiguration() throws Exception {
    var legacy =
        config("datasheet_mcp_url=\"http://localhost:8000/mcp\"\n[interlis]\njar=\"old.jar\"\n");
    assertEquals("http", legacy.mcp("datasheet").get("transport"));
    assertEquals("old.jar", legacy.mcp("interlis").get("jar"));
    assertEquals(
        "invalid_configuration",
        assertThrows(
                Problem.class,
                () -> config("datasheet_mcp_url=\"http://localhost/mcp\"\n" + dockerConfig()))
            .code);
    assertEquals(
        "invalid_configuration",
        assertThrows(
                Problem.class,
                () -> config("[interlis]\nimage=\"sogis/mcp:v1\"\njar=\"old.jar\"\n"))
            .code);
    assertThrows(
        Problem.class, () -> config("[datasheet]\ntransport=\"stdio\"\nimage=\"--privileged\"\n"));
  }

  @Test
  void pinsImagesCopiesModelsAndOnlyUpdatesExplicitly() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    var docker = new DockerMcps(s, fake);
    docker.setup(false);
    String before = s.fingerprint();
    assertEquals(A, docker.selected("datasheet", false).get("image_id"));
    assertEquals(
        6, Json.obj(new Organizations(new Workflow(s)).officeRules().get("fields")).size());
    fake.tag = B;
    docker.setup(false);
    assertEquals(before, s.fingerprint());
    assertFalse(fake.calls.stream().anyMatch(c -> c.contains("pull")));
    docker.setup(true);
    assertEquals(B, docker.selected("datasheet", false).get("image_id"));
    assertNotEquals(before, s.fingerprint());
    assertTrue(fake.calls.stream().anyMatch(c -> c.contains("pull")));
    var command = docker.launch("owned", B);
    assertTrue(command.contains("-i"));
    assertTrue(command.contains("SPRING_PROFILES_ACTIVE=stdio"));
    assertTrue(command.contains("--pull=never"));
    assertFalse(command.contains("-t"));
    assertFalse(command.contains("-p"));
    assertFalse(command.contains("-v"));
  }

  @Test
  void tamperedModelsAndDuplicateModelDirectoriesFail() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    var docker = new DockerMcps(s, fake);
    docker.setup(false);
    Path models = Path.of(Json.required(docker.selected("datasheet", false), "model_directory"));
    Json.write(models.resolve(DockerMcps.MODELS.getFirst()), "tampered");
    assertEquals("mcp_models_changed", assertThrows(Problem.class, s::fingerprint).code);
    docker.setup(false);
    s.values.put("model_dirs", List.of(Fixtures.FIXTURES.resolve("models").toString()));
    assertEquals("invalid_configuration", assertThrows(Problem.class, s::modelDirectories).code);
  }

  @Test
  void missingModelsCleanUpWithoutPublishingRuntimeLock() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    fake.missingModel = true;
    var docker = new DockerMcps(s, fake);
    assertEquals("mcp_models_missing", assertThrows(Problem.class, () -> docker.setup(false)).code);
    assertFalse(Files.exists(docker.lockFile()));
    assertTrue(fake.calls.stream().anyMatch(c -> c.contains("rm")));
  }

  @Test
  void pullFailuresNeverInvokeBuild() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    fake.imageMissing = true;
    fake.failPull = true;
    assertEquals(
        "mcp_pull_failed",
        assertThrows(Problem.class, () -> new DockerMcps(s, fake).setup(false)).code);
    assertFalse(fake.calls.stream().anyMatch(c -> c.contains("build") || c.contains("bootJar")));
  }

  @Test
  void cleanupDoesNotRemoveForeignContainers() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    fake.owner = "foreign";
    var docker = new DockerMcps(s, fake);
    docker.remove("foreign-container");
    assertFalse(fake.calls.stream().anyMatch(c -> c.contains("rm")));
  }

  @Test
  void staleSelectionAndMissingSetupFailBeforeAnyContainerStart() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    var docker = new DockerMcps(s, fake);
    assertEquals(
        "mcp_setup_required",
        assertThrows(Problem.class, () -> docker.selected("datasheet", false)).code);
    docker.setup(false);
    s.mcp("datasheet").put("image", "sogis/datasheet:v2");
    assertEquals(
        "mcp_setup_required",
        assertThrows(Problem.class, () -> docker.selected("datasheet", false)).code);
    assertFalse(fake.calls.stream().anyMatch(c -> c.contains("run")));
  }

  @Test
  void orphanCleanupLeavesLiveOwnersAlone() throws Exception {
    var s = config(dockerConfig());
    var fake = new Docker();
    var docker = new DockerMcps(s, fake);
    fake.owner = docker.owner();
    docker.register("live");
    docker.reap();
    assertFalse(fake.calls.stream().anyMatch(c -> c.contains("rm")));
    Path dead = docker.home().resolve("containers/dead.json");
    Json.atomic(dead, Json.map("name", "dead", "owner", docker.owner(), "pid", Long.MAX_VALUE));
    docker.reap();
    assertTrue(fake.calls.stream().anyMatch(c -> c.contains("rm") && c.contains("dead")));
    assertFalse(Files.exists(dead));
    assertTrue(Files.exists(docker.home().resolve("containers/live.json")));
  }

  @Test
  void daemonFailureRetainsContainerRecoveryRecord() throws Exception {
    var s = config(dockerConfig());
    var unavailable =
        new ProcessRunner() {
          @Override
          public Result run(List<String> args, Path cwd, int seconds, Path log) {
            return new Result(1, "FIXTURE daemon unavailable");
          }
        };
    var docker = new DockerMcps(s, unavailable);
    docker.register("interrupted");
    docker.remove("interrupted");
    assertTrue(Files.exists(docker.home().resolve("containers/interrupted.json")));
  }
}
