package ch.so.agi.integrator;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Standalone real Docker acceptance, run after Gradle (never inside its build lock). */
public class LauncherAcceptance {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]).toAbsolutePath();
    Path evidence = root.resolve(".datenportal-integrator/launcher-acceptance");
    Files.createDirectories(evidence);
    String launcher = root.resolve("bin/datenportal-agent").toString();
    String config = root.resolve("config/local.toml").toString();
    var listAgents =
        List.of(
            "docker", "ps", "-q", "--filter", "label=datenportal.integrator.agent.root=" + root);
    var runner = new ProcessRunner();
    var before =
        new HashSet<>(
            runner.checked(listAgents, root, 20).lines().filter(line -> !line.isBlank()).toList());
    var results = Json.map();
    // Two actual launcher clients must serialize builds and return only protocol output.
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Map<String, Object>>>();
      for (int i = 0; i < 2; i++) {
        int n = i;
        futures.add(
            pool.submit(
                () -> {
                  var pb =
                      new ProcessBuilder(launcher, "schema", "start")
                          .directory(root.toFile())
                          .redirectError(evidence.resolve("cli-" + n + ".log").toFile());
                  pb.environment().put("JAVA_HOME", "/no-host-jdk");
                  var process = pb.start();
                  process.getOutputStream().close();
                  String output = new String(process.getInputStream().readAllBytes());
                  if (!process.waitFor(180, TimeUnit.SECONDS) || process.exitValue() != 0)
                    throw new AssertionError("CLI failed");
                  var value = Json.read(output);
                  if (!"start".equals(value.get("operation")))
                    throw new AssertionError("stdout contaminated");
                  return Json.map("valid", true, "operation", value.get("operation"));
                }));
      }
      results.put(
          "parallel_cli",
          futures.stream()
              .map(
                  f -> {
                    try {
                      return f.get(240, TimeUnit.SECONDS);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  })
              .toList());
    }
    var params = ServerParameters.builder(launcher).args("--config", config, "serve").build();
    var transport =
        new StdioClientTransport(
            params, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    transport.setStdErrorHandler(line -> {});
    try (var client =
        McpClient.sync(transport)
            .initializationTimeout(Duration.ofSeconds(180))
            .requestTimeout(Duration.ofSeconds(30))
            .build()) {
      client.initialize();
      int count = client.listTools().tools().size();
      if (count != Operations.ALL.size()) throw new AssertionError("MCP tools mismatch");
      results.put("mcp", Json.map("valid", true, "tools", count));
    }
    var eof =
        new ProcessBuilder(launcher, "--config", config, "serve")
            .directory(root.toFile())
            .redirectError(evidence.resolve("eof.log").toFile())
            .redirectOutput(evidence.resolve("eof.out").toFile())
            .start();
    eof.getOutputStream().close();
    if (!eof.waitFor(180, TimeUnit.SECONDS) || eof.exitValue() != 0) {
      eof.destroyForcibly();
      throw new AssertionError("EOF did not terminate launcher");
    }
    results.put("eof", Json.map("valid", true));
    var stopped =
        new ProcessBuilder(launcher, "--config", config, "serve")
            .directory(root.toFile())
            .redirectError(evidence.resolve("signal.log").toFile())
            .start();
    String initialize =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"acceptance\",\"version\":\"1\"}}}\n";
    stopped.getOutputStream().write(initialize.getBytes());
    stopped.getOutputStream().flush();
    try (var pool = Executors.newSingleThreadExecutor()) {
      String response =
          pool.submit(
                  () ->
                      new java.io.BufferedReader(
                              new java.io.InputStreamReader(stopped.getInputStream()))
                          .readLine())
              .get(180, TimeUnit.SECONDS);
      if (response == null || !Json.read(response).containsKey("result"))
        throw new AssertionError("signal test handshake failed");
      stopped.destroy();
      if (!stopped.waitFor(30, TimeUnit.SECONDS)) {
        stopped.destroyForcibly();
        throw new AssertionError("TERM not propagated");
      }
    }
    results.put("term", Json.map("valid", true, "exit_code", stopped.exitValue()));
    boolean reaped = false;
    for (int attempt = 0; attempt < 20; attempt++) {
      var after =
          new HashSet<>(
              runner
                  .checked(listAgents, root, 20)
                  .lines()
                  .filter(line -> !line.isBlank())
                  .toList());
      after.removeAll(before);
      if (after.isEmpty()) {
        reaped = true;
        break;
      }
      Thread.sleep(250);
    }
    if (!reaped) throw new AssertionError("Launcher left running containers after EOF/TERM");
    results.put("containers_reaped", true);
    Json.atomic(evidence.resolve("report.json"), results);
    System.out.println(Json.pretty(results));
  }
}
