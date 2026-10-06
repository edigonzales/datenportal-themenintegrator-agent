package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class DatasheetRestartIntegrationTest {
  @TempDir Path temp;

  Process launch(Path jar, String url, int port) throws Exception {
    return new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-jar",
            jar.toString(),
            "--server.port=" + port,
            "--server.address=127.0.0.1",
            "--datasheet.public-base-url=" + url)
        .directory(temp.toFile())
        .redirectErrorStream(true)
        .redirectOutput(temp.resolve("datasheet-runtime.log").toFile())
        .start();
  }

  void ready(Process p, String url) throws Exception {
    var http = HttpClient.newHttpClient();
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline && p.isAlive()) {
      try {
        if (http.send(
                    HttpRequest.newBuilder(URI.create(url + "/actuator/health"))
                        .timeout(Duration.ofSeconds(1))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding())
                .statusCode()
            == 200) return;
      } catch (Exception ignored) {
      }
      Thread.sleep(200);
    }
    fail(
        "Isolated datasheet MCP did not become ready: "
            + Json.contents(temp.resolve("datasheet-runtime.log")));
  }

  void stop(Process p) throws Exception {
    p.destroy();
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
      p.destroyForcibly();
      p.waitFor(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void actualRestartRestoresSnapshotAndTransferIdentity() throws Exception {
    Settings source = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    Path jar =
        source.root.resolve(
            ".datenportal-integrator/tools/datasheet-source/build/libs/datasheet-mcp.jar");
    assertTrue(Files.isRegularFile(jar));
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    String url = "http://127.0.0.1:" + port;
    source.values.put("datasheet_mcp_url", url + "/mcp");
    Process server = launch(jar, url, port);
    try {
      ready(server, url);
      var f =
          new Fixtures(
              temp, new ProcessRunner(), McpClients.datasheet(source), (n, a) -> Json.map());
      f.settings.values.put("validator_command", source.strings("validator_command", List.of()));
      String id = f.metadataOnly();
      f.call("metadata", "run_id", id, "operation", "import_xtf");
      f.call(
          "metadata",
          "run_id",
          id,
          "operation",
          "update_metadata",
          "arguments",
          Json.map("values", Json.map("title", "Isolierter tatsächlicher Neustarttest")));
      f.call("metadata", "run_id", id, "operation", "export_xtf");
      Path before = f.workflow.store.edit(id, r -> f.workflow.sheet(r));
      var fields = Xml.business(Xml.describe(before, null, null).get("fields"));
      String tid = Xml.datasets(Xml.parse(before)).getFirst().getAttributeNS(Xml.ILI, "tid");
      stop(server);
      server = launch(jar, url, port);
      ready(server, url);
      f.call("metadata", "run_id", id, "operation", "restore");
      f.call("metadata", "run_id", id, "operation", "export_xtf");
      Path after = f.workflow.store.edit(id, r -> f.workflow.sheet(r));
      assertEquals(fields, Xml.business(Xml.describe(after, null, null).get("fields")));
      assertEquals(tid, Xml.datasets(Xml.parse(after)).getFirst().getAttributeNS(Xml.ILI, "tid"));
      assertTrue(Json.bool(f.call("validate", "run_id", id), "valid", false));
    } finally {
      stop(server);
    }
  }
}
