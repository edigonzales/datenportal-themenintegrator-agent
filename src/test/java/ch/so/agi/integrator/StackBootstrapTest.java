package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StackBootstrapTest {
  @TempDir Path temp;
  HttpServer server;
  boolean published;
  final List<List<String>> calls = new ArrayList<>();
  int observedTimeout;
  Path observedLog;
  int exitCode;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  Map<String, Object> environment() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/manifest",
        exchange -> {
          byte[] body =
              (published
                      ? """
                        {"schemaVersion":1,"releaseId":"first","datasheets":"datasheets-first.xtf",
                         "catalog":null,"duckdb":"catalog-first.duckdb"}
                        """
                      : "missing")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(published ? 200 : 404, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return Json.map(
        "kind",
        "local",
        "manifest_url",
        "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest");
  }

  ProcessRunner process() {
    return new ProcessRunner() {
      @Override
      public String checked(List<String> args, Path cwd, int timeout) {
        calls.add(args);
        if (args.contains("--format"))
          return """
              [{"Service":"sodata","State":"running","Health":"healthy"},
               {"Service":"garage","State":"running","Health":"healthy"},
               {"Service":"downloads","State":"running","Health":"healthy"},
               {"Service":"jenkins","State":"running","Health":"healthy"}]
              """;
        return "";
      }

      @Override
      public Result run(List<String> args, Path cwd, int timeout, Path log) {
        calls.add(args);
        observedTimeout = timeout;
        observedLog = log;
        Json.write(log, "FIXTURE: retained stack diagnostics\n");
        if (exitCode == 0) published = true;
        return new Result(exitCode, "FIXTURE: stack ready");
      }
    };
  }

  @Test
  void delegatesFreshBootstrapWithComposeFilesAndSeparateTimeout() throws Exception {
    var env = environment();
    var process = process();
    var fixture = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    fixture.settings.values.put(
        "compose_files", List.of("compose.yaml", "compose.jenkins-local.yaml"));
    var result = Json.obj(new Stack(fixture.settings, process).bootstrap(env));
    assertEquals(true, result.get("initialized"));
    assertEquals("first", result.get("release_id"));
    assertEquals(1800, observedTimeout);
    assertTrue(Files.isRegularFile(observedLog));
    assertTrue(
        calls.contains(
            List.of(
                "bash",
                "scripts/bootstrap.sh",
                "-f",
                "compose.yaml",
                "-f",
                "compose.jenkins-local.yaml",
                "--timeout",
                "1800")));
    assertFalse(calls.stream().anyMatch(c -> c.contains("exec") || c.contains("quietDown")));
  }

  @Test
  void existingPublicationUsesReadOnlyCheck() throws Exception {
    var env = environment();
    published = true;
    var process = process();
    var fixture = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    var result = Json.obj(new Stack(fixture.settings, process).bootstrap(env));
    assertEquals(false, result.get("initialized"));
    assertTrue(calls.stream().anyMatch(c -> c.contains("--check-only")));
    assertFalse(calls.stream().anyMatch(c -> c.contains("up") || c.contains("scripts/up.sh")));
  }

  @Test
  void failedBootstrapRetainsDiagnosticsAndDoesNotClaimReadiness() throws Exception {
    var env = environment();
    exitCode = 17;
    var process = process();
    var fixture = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    var problem =
        assertThrows(Problem.class, () -> new Stack(fixture.settings, process).bootstrap(env));
    assertEquals("stack_command_failed", problem.code);
    assertEquals(17, problem.details.get("returncode"));
    assertEquals(observedLog.toString(), problem.details.get("log"));
    assertTrue(Files.readString(observedLog).contains("retained stack diagnostics"));
    assertFalse(published);
  }

  @Test
  void coldStartWaitsForRuntimeGateBeforePublication() throws Exception {
    var process = process();
    var fixture = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    new Stack(fixture.settings, process).ensure();
    assertTrue(calls.contains(List.of("bash", "scripts/up.sh", "--infrastructure-only")));
    assertFalse(calls.stream().anyMatch(c -> c.contains("scripts/bootstrap.sh")));
    assertEquals(1800, observedTimeout);
  }

  @Test
  void configurableStackTimeoutIsIndependentAndBounded() throws Exception {
    var process = process();
    new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    Path config = temp.resolve("local.toml");
    String original = Files.readString(config);
    Files.writeString(config, "stack_timeout_seconds=2400\n" + original);
    var settings = new Settings(config);
    assertEquals(300, settings.timeout);
    new Stack(settings, process).ensure();
    assertEquals(2400, observedTimeout);
    for (int invalid : List.of(0, 7201)) {
      Files.writeString(config, "stack_timeout_seconds=" + invalid + "\n" + original);
      assertEquals(
          "invalid_configuration", assertThrows(Problem.class, () -> new Settings(config)).code);
    }
  }

  @Test
  void remoteProfileNeverInvokesLocalBootstrap() throws Exception {
    var process = process();
    var fixture = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    assertEquals(
        "local_only",
        assertThrows(
                Problem.class,
                () -> new Stack(fixture.settings, process).bootstrap(Json.map("kind", "prod")))
            .code);
    assertTrue(calls.isEmpty());
  }
}
