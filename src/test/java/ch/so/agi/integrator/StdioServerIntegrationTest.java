package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class StdioServerIntegrationTest {
  @TempDir Path temp;

  @Test
  @Timeout(60)
  void bothServersAnswerEveryConcurrentSuccessAndErrorAndRemainUsable() throws Exception {
    for (String command : List.of("serve-workspace", "serve")) {
      try (var peer = new Peer(command)) {
        peer.initialize();
        for (int batch = 0; batch < 3; batch++) {
          int base = batch * 400 + 1;
          var responses = peer.read(400);
          for (int n = 0; n < 400; n++) {
            boolean rejected = n % 2 == 0;
            var args =
                command.equals("serve-workspace")
                    ? Json.map(
                        "name",
                        "list_files",
                        "arguments",
                        Json.map("path", rejected ? "src/main/java" : "docs/agent"))
                    : Json.map(
                        "name",
                        "operation_schema",
                        "arguments",
                        Json.map("operation", rejected ? "not_an_operation" : "status"));
            peer.send(base + n, "tools/call", args);
          }
          var seen = new HashSet<Integer>();
          for (var response : responses.get(15, TimeUnit.SECONDS)) {
            int id = ((Number) response.get("id")).intValue();
            assertTrue(seen.add(id), "duplicate response");
            assertNull(response.get("error"));
            assertEquals((id - base) % 2 == 0, Json.obj(response.get("result")).get("isError"));
          }
          assertEquals(400, seen.size());
          assertTrue(peer.process.isAlive());
          // A new request after the queue is drained must not need a reconnect.
          var ping = peer.read(1);
          peer.send(10000 + batch, "ping", Json.map());
          assertNull(ping.get(5, TimeUnit.SECONDS).getFirst().get("error"));
        }
        peer.input.close();
        assertTrue(peer.process.waitFor(10, TimeUnit.SECONDS), "EOF must stop the server");
        assertEquals(0, peer.process.exitValue());
      }
    }
  }

  @Test
  @Timeout(30)
  void fatalProtocolErrorDoesNotLeaveAnUnresponsiveProcessWaitingForEof() throws Exception {
    try (var peer = new Peer("serve-workspace")) {
      peer.initialize();
      peer.input.write("invalid JSON\n");
      peer.input.flush();
      // Leave stdin open: the former EOF-only latch kept this broken process alive.
      assertTrue(
          peer.process.waitFor(10, TimeUnit.SECONDS), "transport failure must stop the server");
    }
  }

  private class Peer implements AutoCloseable {
    final Process process;
    final BufferedWriter input;
    final BufferedReader output;
    final ExecutorService reader = Executors.newSingleThreadExecutor();

    Peer(String command) throws IOException {
      Path config = temp.resolve(command + ".toml");
      Json.write(
          config,
          "root="
              + Json.text(Fixtures.ROOT.toString())
              + "\nstate_dir="
              + Json.text(temp.resolve("runs").toString())
              + "\ntopics_repo="
              + Json.text(temp.resolve("repo").toString())
              + "\n[datasheet]\ntransport=\"stdio\"\n");
      process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin/java").toString(),
                  "-jar",
                  Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                  "--config",
                  config.toString(),
                  command)
              .redirectError(temp.resolve(command + ".stderr").toFile())
              .start();
      input = process.outputWriter(StandardCharsets.UTF_8);
      output = process.inputReader(StandardCharsets.UTF_8);
    }

    void initialize() throws Exception {
      var result = read(1);
      send(
          0,
          "initialize",
          Json.map(
              "protocolVersion",
              "2025-03-26",
              "capabilities",
              Json.map(),
              "clientInfo",
              Json.map("name", "isolated-concurrency-test", "version", "1")));
      assertNull(result.get(10, TimeUnit.SECONDS).getFirst().get("error"));
      input.write(
          Json.text(Json.map("jsonrpc", "2.0", "method", "notifications/initialized")) + "\n");
      input.flush();
    }

    void send(int id, String method, Object args) throws IOException {
      input.write(
          Json.text(Json.map("jsonrpc", "2.0", "id", id, "method", method, "params", args)) + "\n");
      input.flush();
    }

    Future<List<Map<String, Object>>> read(int count) {
      return reader.submit(
          () -> {
            var results = new ArrayList<Map<String, Object>>();
            for (int n = 0; n < count; n++) {
              String line = output.readLine();
              assertNotNull(line, "MCP output closed before response");
              results.add(Json.read(line));
            }
            return results;
          });
    }

    @Override
    public void close() throws Exception {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      reader.shutdownNow();
      input.close();
      output.close();
    }
  }
}
