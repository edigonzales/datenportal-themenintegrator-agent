package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Uses only its own Compose project, private workspaces and synthetic test data. */
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComposeRuntimeIntegrationTest {
  @TempDir static Path temp;
  static Settings settings, source;
  static ComposeRuntime compose;
  static ProcessRunner process = new ProcessRunner();
  static Path snapshot, csv, init;
  static String configText;
  static Workflow workflow;
  static Map<String, Object> run;

  static int port() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  @BeforeAll
  static void prepare() throws Exception {
    source = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    String project = "integrator-test-" + UUID.randomUUID().toString().substring(0, 8);
    configText =
        "root=\".\"\ntopics_repo="
            + Json.text(source.topics.toString())
            + "\nstack_repo=\"missing-stack\"\ntimeout_seconds=300\nstack_timeout_seconds=90\n"
            + "[runtime]\nproject="
            + Json.text(project)
            + "\n[gretl]\nmode=\"compose\"\nimage="
            + Json.text(GretlRuntime.reference(source))
            + "\n[datasheet]\ntransport=\"http\"\nmanaged=true\nurl=\"http://127.0.0.1:"
            + port()
            + "/mcp\"\nimage="
            + Json.text(Json.required(source.mcp("datasheet"), "image"))
            + "\n[interlis]\ntransport=\"http\"\nmanaged=true\nurl=\"http://127.0.0.1:"
            + port()
            + "/mcp\"\nimage="
            + Json.text(Json.required(source.mcp("interlis"), "image"))
            + "\n";
    Json.write(temp.resolve("local.toml"), configText);
    Files.copy(Fixtures.ROOT.resolve("compose.yaml"), temp.resolve("compose.yaml"));
    settings = new Settings(temp.resolve("local.toml"));
    new DockerMcps(settings, process).setup(false);
    new GretlRuntime(settings, process).setup(false);
    ComposeRuntime.afterSetup(settings, process);
    compose = new ComposeRuntime(settings, process);
    workflow = new Workflow(settings);
    run = workflow.store.create("organization", "agi", null, null, "local");
    snapshot = temp.resolve("snapshot");
    Workspace.copy(source.topics, snapshot);
    Path topic = snapshot.resolve("agi/ch.so.integrator.fixture");
    Files.createDirectories(topic);
    Path model = topic.resolve("IntegratorComposeFixture.ili");
    Json.write(
        model,
        "INTERLIS 2.3;\nMODEL IntegratorComposeFixture (en) AT \"https://example.invalid\" VERSION \"2026-10-08\" =\nTOPIC Data =\nCLASS Row =\nJahr : MANDATORY 1900 .. 2100;\nEND Row;\nEND Data;\nEND IntegratorComposeFixture.\n");
    Json.write(
        snapshot.resolve("agi/build.gradle"),
        Models.task(
            "plugins { id 'ch.so.agi.gretl' }\n",
            "ch.so.integrator.fixture",
            "IntegratorComposeFixture",
            Json.required(new GretlRuntime(settings, process).selected(false), "sha256"),
            Json.sha(model)));
    csv = temp.resolve("valid.csv");
    Json.write(csv, "Jahr\n2025\n");
    init = temp.resolve("slow.gradle");
    Json.write(
        init,
        "gradle.projectsEvaluated { rootProject.tasks.register('integratorSlow') { doLast { println 'COMPOSE_SLOW_STARTED'; Thread.sleep(30000) } } }\n");
  }

  @AfterAll
  static void cleanup() {
    if (workflow != null) workflow.close();
    // All volumes in this explicitly isolated test project were created by this class.
    if (compose != null) process.run(compose.command("down", "-v"), settings.root, 60, null);
  }

  static Map<String, Object> validate(Settings s, Path data, String log) {
    return new GretlRuntime(s, process)
        .gradle(workflow, run, snapshot, "validateThemenCsv", List.of("-PdataFile=" + data), log);
  }

  static String pid(Map<String, Object> result) {
    var match =
        Pattern.compile("(?m)^\\s*(\\d+)\\s+IDLE\\b")
            .matcher(Json.required(result, "daemon_status"));
    assertTrue(match.find(), Json.pretty(result));
    return match.group(1);
  }

  @Test
  @Order(1)
  void coldStartUsesBothHttpMcpsAndCompatibleGretl() {
    process.checked(compose.command("config", "--quiet"), settings.root, 30);
    process.checked(compose.command("build"), settings.root, 30);
    try (var data = McpClients.datasheet(settings);
        var interlis = McpClients.interlis(settings)) {
      assertTrue(data.tools().containsAll(McpClients.expectedTools("datasheet")));
      assertTrue(interlis.tools().containsAll(McpClients.expectedTools("interlis")));
    }
    var doctor = new GretlRuntime(settings, process).doctor();
    assertTrue(Json.bool(doctor, "valid", false), Json.pretty(doctor));
    assertFalse(Json.required(doctor, "daemon_status").isBlank());
    assertFalse(Files.exists(settings.stack));
    for (String name : List.of("datasheet", "interlis", "gretl"))
      assertEquals("running", compose.inspect(name).get("state"));
    process.checked(List.of("docker", "compose", "config", "--quiet"), settings.root, 30);
    process.checked(List.of("docker", "compose", "up", "-d", "--pull", "never"), settings.root, 90);
    process.checked(compose.command("ps"), settings.root, 30);
  }

  @Test
  @Order(2)
  void repeatedValidationReusesDaemonAndMeasuresEphemeralBaseline() {
    var first = validate(settings, csv, "first");
    var second = validate(settings, csv, "second");
    assertTrue(Json.bool(first, "valid", false), Json.pretty(first));
    assertTrue(Json.bool(second, "valid", false), Json.pretty(second));
    assertEquals(pid(first), pid(second));
    assertNotEquals(first.get("container_workspace"), second.get("container_workspace"));
    Json.obj(settings.values.get("gretl")).put("mode", "ephemeral");
    Map<String, Object> baseline;
    try {
      baseline = validate(settings, csv, "ephemeral-baseline");
    } finally {
      Json.obj(settings.values.get("gretl")).put("mode", "compose");
    }
    assertTrue(Json.bool(baseline, "valid", false), Json.pretty(baseline));
    System.out.printf(
        "COMPOSE_TIMING first=%s ms repeated=%s ms ephemeral=%s ms daemon=%s%n",
        first.get("duration_ms"),
        second.get("duration_ms"),
        baseline.get("duration_ms"),
        pid(second));
  }

  @Test
  @Order(3)
  void parallelPositiveAndNegativeChecksKeepSeparateWorkspaces() throws Exception {
    Path invalid = temp.resolve("invalid.csv");
    Json.write(invalid, "Jahr\nnot-a-number\n");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var positive = executor.submit(() -> validate(settings, csv, "parallel-positive"));
      var negative = executor.submit(() -> validate(settings, invalid, "parallel-negative"));
      var p = positive.get(180, TimeUnit.SECONDS);
      var n = negative.get(180, TimeUnit.SECONDS);
      assertTrue(Json.bool(p, "valid", false), Json.pretty(p));
      assertFalse(Json.bool(n, "valid", true), Json.pretty(n));
      assertNotEquals(p.get("container_workspace"), n.get("container_workspace"));
      assertTrue(Json.bool(n, "reports_copied", false));
    }
    Path organization = temp.resolve("organization");
    Workspace.copy(source.topics, organization);
    var report =
        new GretlRuntime(settings, process)
            .gradle(workflow, run, organization, "tasks", List.of("--all"), "organization");
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
  }

  Settings shortTimeout() {
    Path config = temp.resolve("short.toml");
    Json.write(config, configText.replace("timeout_seconds=300", "timeout_seconds=3"));
    return new Settings(config);
  }

  @Test
  @Order(4)
  void timeoutConfirmsAbortKeepsLogsAndAllowsNextCheck() {
    var shortSettings = shortTimeout();
    var failure =
        assertThrows(
            Problem.class,
            () ->
                new GretlRuntime(shortSettings, process)
                    .gradle(
                        workflow,
                        run,
                        snapshot,
                        "integratorSlow",
                        List.of("-I", init.toString()),
                        "timeout"));
    assertEquals("command_timeout", failure.code, Json.pretty(failure.result()));
    assertTrue(
        Json.contents(workflow.directory(run).resolve("validation/timeout.log"))
            .contains("COMPOSE_SLOW_STARTED"));
    assertTrue(Json.bool(validate(settings, csv, "after-timeout"), "valid", false));
  }

  @Test
  @Order(5)
  void killedHostLeavesBoundedContainerTaskAndNextCheckRecovers() throws Exception {
    shortTimeout();
    String container = compose.ensure("gretl");
    String previous =
        process.checked(
            List.of("docker", "exec", container, "cat", "/var/lib/integrator/active"),
            settings.root,
            5);
    var child =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                AbandonedHost.class.getName(),
                temp.resolve("short.toml").toString(),
                snapshot.toString(),
                init.toString())
            .redirectErrorStream(true)
            .redirectOutput(temp.resolve("abandoned-host.log").toFile())
            .start();
    boolean started = false;
    try {
      for (int i = 0; i < 80; i++) {
        var active =
            process.run(
                List.of(
                    "docker",
                    "exec",
                    container,
                    "bash",
                    "-c",
                    "test -f /var/lib/integrator/active && test \"$(cat /var/lib/integrator/active)\" != \"$1\" && grep -q COMPOSE_SLOW_STARTED \"$(cat /var/lib/integrator/active)/task.log\"",
                    "abandoned-check",
                    previous),
                settings.root,
                5,
                null);
        if (active.exitCode() == 0) {
          started = true;
          break;
        }
        Thread.sleep(250);
      }
      assertTrue(started, Json.contents(temp.resolve("abandoned-host.log")));
    } finally {
      child.destroyForcibly();
      child.waitFor(10, TimeUnit.SECONDS);
    }
    var recovered = validate(settings, csv, "after-host-loss");
    assertTrue(Json.bool(recovered, "valid", false), Json.pretty(recovered));
    assertEquals(container, compose.ensure("gretl"));
  }

  public static class AbandonedHost {
    public static void main(String[] args) {
      var s = new Settings(Path.of(args[0]));
      try (var w = new Workflow(s)) {
        var state = w.store.create("organization", "agi", null, null, "local");
        new GretlRuntime(s, new ProcessRunner())
            .gradle(
                w, state, Path.of(args[1]), "integratorSlow", List.of("-I", args[2]), "abandoned");
      }
    }
  }

  @Test
  @Order(6)
  void containerRestartWarmsDaemonAndMissingIdleDaemonIsHealthy() {
    String container = compose.ensure("gretl");
    process.checked(List.of("docker", "restart", container), settings.root, 30);
    var runtime = new GretlRuntime(settings, process);
    assertTrue(Json.bool(runtime.doctor(), "valid", false));
    process.checked(
        List.of(
            "docker",
            "exec",
            "--user",
            "jenkins",
            "-w",
            "/var/lib/integrator/warmup",
            container,
            "bash",
            "shared/bin/gradlew-java17.sh",
            "--stop"),
        settings.root,
        30);
    assertTrue(Json.bool(runtime.doctor(), "valid", false));
    assertTrue(Json.bool(validate(settings, csv, "after-idle-stop"), "valid", false));
  }

  @Test
  @Order(7)
  void managedHttpDatasheetRestoresDraftAfterServiceRestart() throws Exception {
    Path fixtureRoot = temp.resolve("metadata-fixture");
    String id;
    try (var client = McpClients.datasheet(settings)) {
      var f = new Fixtures(fixtureRoot, process, client, (n, a) -> Json.map());
      f.settings.values.put("datasheet", settings.mcp("datasheet"));
      f.settings.values.put("model_dirs", List.of());
      id = f.metadataOnly();
      f.call("metadata", "run_id", id, "operation", "import_xtf", "arguments", Json.map());
      f.call("metadata", "run_id", id, "operation", "export_xtf", "arguments", Json.map());
      f.workflow.close();
    }
    String container = compose.ensure("datasheet");
    process.checked(List.of("docker", "restart", container), settings.root, 30);
    try (var client = McpClients.datasheet(settings)) {
      var f = new Fixtures(fixtureRoot, process, client, (n, a) -> Json.map());
      f.settings.values.put("datasheet", settings.mcp("datasheet"));
      f.settings.values.put("model_dirs", List.of());
      var restored =
          f.call("metadata", "run_id", id, "operation", "read_datasheet", "arguments", Json.map());
      assertFalse(Json.obj(restored.get("data")).isEmpty());
      f.workflow.close();
    }
    assertEquals(container, compose.ensure("datasheet"));
  }

  @Test
  @Order(8)
  void unclearCompletionRestartsOnlyOwnedGretlAndBundleMismatchStopsTask() {
    String container = compose.ensure("gretl");
    String started =
        process.checked(
            List.of("docker", "inspect", "--format", "{{.State.StartedAt}}", container),
            settings.root,
            10);
    String data = compose.ensure("datasheet");
    // Deliberately incomplete receipt in this isolated test volume, without a real build.
    process.checked(
        List.of(
            "docker",
            "exec",
            "--user",
            "jenkins",
            container,
            "bash",
            "-c",
            "mkdir -p /var/lib/integrator/jobs/incomplete-fixture; printf '%s\\n' /var/lib/integrator/jobs/incomplete-fixture > /var/lib/integrator/active"),
        settings.root,
        10);
    assertTrue(Json.bool(validate(settings, csv, "after-incomplete-receipt"), "valid", false));
    assertNotEquals(
        started,
        process.checked(
            List.of("docker", "inspect", "--format", "{{.State.StartedAt}}", container),
            settings.root,
            10));
    assertEquals(data, compose.ensure("datasheet"));
    var original = Json.read(GretlRuntime.file(settings));
    var wrong = new LinkedHashMap<>(original);
    wrong.put("sha256", "0".repeat(64));
    Json.atomic(GretlRuntime.file(settings), wrong);
    try {
      assertEquals(
          "gretl_runtime_mismatch",
          assertThrows(Problem.class, () -> validate(settings, csv, "wrong-bundle")).code);
    } finally {
      Json.atomic(GretlRuntime.file(settings), original);
    }
  }

  @Test
  @Order(9)
  void occupiedPortKeepsDiagnosticAndNeverUsesForeignServer() throws Exception {
    Path busyRoot = temp.resolve("busy-port");
    Files.createDirectories(busyRoot);
    try (var occupied = new ServerSocket()) {
      occupied.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
      Json.write(busyRoot.resolve("local.toml"), configText);
      var s = new Settings(busyRoot.resolve("local.toml"));
      Json.obj(s.values.get("runtime")).put("project", compose.project() + "-busy");
      Json.obj(s.values.get("gretl")).put("mode", "ephemeral");
      s.mcp("interlis").put("managed", false);
      s.mcp("datasheet").put("url", "http://127.0.0.1:" + occupied.getLocalPort() + "/mcp");
      new DockerMcps(s, process).setup(false);
      var runtime = new ComposeRuntime(s, process);
      try {
        var failure = assertThrows(Problem.class, () -> runtime.ensure("datasheet"));
        assertEquals("runtime_start_failed", failure.code, Json.pretty(failure.result()));
        assertTrue(Files.isRegularFile(Path.of(Json.required(failure.details, "log"))));
      } finally {
        process.run(runtime.command("down"), s.root, 30, null);
      }
    }
  }
}
