package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class McpCredentialsIntegrationTest {
  @TempDir Path temp;

  @Test
  void liveStdioProcessReadsNewStoreAndRotatedToken() throws Exception {
    Json.write(temp.resolve("SYNTHETIC_TEST_FIXTURE"), DeliveryAcceptance.STATEMENT);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    String[] expected = {"first-fixture-secret"};
    var observed = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
    server.createContext(
        "/jenkins/job/seed/1/api/json",
        exchange -> {
          boolean accepted =
              Objects.equals(
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  "Basic "
                      + Base64.getEncoder()
                          .encodeToString(("fixture-user:" + expected[0]).getBytes()));
          observed.add(accepted);
          byte[] body = "{\"building\":true}".getBytes();
          exchange.sendResponseHeaders(accepted ? 200 : 401, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      Json.write(
          temp.resolve("config/local.toml"),
          "root=\"..\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n[environments.local]\nkind=\"local\"\nenabled=true\nusername_env=\"SYNTHETIC_MCP_USER\"\ntoken_env=\"SYNTHETIC_MCP_TOKEN\"\njenkins_url="
              + Json.text(base + "/jenkins")
              + "\nportal_url="
              + Json.text(base)
              + "\nmanifest_url="
              + Json.text(base + "/manifest")
              + "\n");
      var s = new Settings(temp.resolve("config/local.toml"));
      var store = new Store(s.runs);
      String id =
          Json.required(
              store.create("topic", "fixture", DeliveryAcceptance.DATASET, null, "local"), "id");
      store.edit(
          id,
          r -> {
            Json.obj(r.get("deliveries"))
                .put(
                    "local",
                    Json.map(
                        "phase",
                        "seeding",
                        "seed",
                        Json.map("build_url", base + "/jenkins/job/seed/1/")));
            return null;
          });
      var parameters =
          ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin/java").toString())
              .args(
                  "-jar",
                  Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                  "--config",
                  s.file.toString(),
                  "serve")
              .build();
      var transport =
          new StdioClientTransport(
              parameters, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
      transport.setStdErrorHandler(line -> {});
      try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
        client.initialize();
        var blocked =
            client.callTool(new McpSchema.CallToolRequest("deliver", Json.map("run_id", id)));
        assertTrue(Boolean.TRUE.equals(blocked.isError()));
        assertEquals("credentials_missing", Json.obj(blocked.structuredContent()).get("error"));
        var credentials = new Credentials(s);
        credentials.save("local", "fixture-user", expected[0]);
        var first =
            client.callTool(new McpSchema.CallToolRequest("deliver", Json.map("run_id", id)));
        assertFalse(Boolean.TRUE.equals(first.isError()));
        expected[0] = "rotated-fixture-secret";
        var rejected =
            client.callTool(new McpSchema.CallToolRequest("deliver", Json.map("run_id", id)));
        assertTrue(Boolean.TRUE.equals(rejected.isError()));
        assertEquals(
            "jenkins_authentication_failed", Json.obj(rejected.structuredContent()).get("error"));
        credentials.save("local", "fixture-user", expected[0]);
        var rotated =
            client.callTool(new McpSchema.CallToolRequest("deliver", Json.map("run_id", id)));
        assertFalse(Boolean.TRUE.equals(rotated.isError()));
        assertEquals(List.of(true, false, true), observed);
      }
    } finally {
      server.stop(0);
    }
  }
}
