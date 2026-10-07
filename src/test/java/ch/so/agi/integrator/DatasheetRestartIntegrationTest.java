package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class DatasheetRestartIntegrationTest {
  @TempDir Path temp;

  Settings source() {
    return Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
  }

  Fixtures fixture(Settings s, McpClients.ToolClient client) throws Exception {
    var f = new Fixtures(temp, new ProcessRunner(), client, (n, a) -> Json.map());
    f.settings.values.put("datasheet", s.mcp("datasheet"));
    f.settings.values.put("model_dirs", List.of());
    f.settings.values.put("validator_command", s.strings("validator_command", List.of()));
    return f;
  }

  Map<String, Object> op(Workflow w, String id, String name, Map<String, Object> args) {
    return Json.obj(
        w.call("metadata", Json.map("run_id", id, "operation", name, "arguments", args)));
  }

  @Test
  void restartRetainsExportApprovalAndHistoricalAttributeIds() throws Exception {
    var s = source();
    String id;
    String oldId;
    String fingerprint;
    String tid;
    String hash;
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      id = f.metadataOnly();
      op(f.workflow, id, "import_xtf", Json.map());
      var updated =
          op(
              f.workflow,
              id,
              "upsert_attribute",
              Json.map(
                  "values", Json.map("name", "EXTRA", "data_type", "Text", "mandatory", false)));
      oldId =
          Json.required(
              Json.obj(Json.list(Json.obj(updated.get("data")).get("attributes")).getLast()),
              "attribute_id");
      op(f.workflow, id, "export_xtf", Json.map());
      var report = f.call("validate", "run_id", id);
      assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
      f.approve(id, "metadata", report);
      // Simulate a compatible schema-2 run written before the content-hash fields existed.
      f.workflow.store.edit(
          id,
          r -> {
            r.remove("exported_content_sha256");
            r.remove("exported_artifact_sha256");
            return null;
          });
      fingerprint = Json.required(report, "fingerprint");
      Path sheet = f.workflow.store.edit(id, r -> f.workflow.sheet(r));
      hash = Json.sha(sheet);
      tid = Xml.datasets(Xml.parse(sheet)).getFirst().getAttributeNS(Xml.ILI, "tid");
    }
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      var restored = op(f.workflow, id, "read_datasheet", Json.map());
      assertEquals(
          fingerprint, f.workflow.store.edit(id, r -> f.workflow.fingerprint(r, "metadata")));
      assertTrue(
          Json.bool(
              Json.obj(f.call("status", "run_id", id).get("current_approvals")),
              "metadata",
              false));
      Path sheet = f.workflow.store.edit(id, r -> f.workflow.sheet(r));
      assertEquals(hash, Json.sha(sheet));
      assertEquals(tid, Xml.datasets(Xml.parse(sheet)).getFirst().getAttributeNS(Xml.ILI, "tid"));
      assertNotEquals(
          oldId,
          Json.obj(Json.list(Json.obj(restored.get("data")).get("attributes")).getLast())
              .get("attribute_id"));
    }
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      var updated =
          op(
              f.workflow,
              id,
              "upsert_attribute",
              Json.map(
                  "attribute_id",
                  oldId,
                  "values",
                  Json.map("description", "Changed after two restarts")));
      assertEquals(
          "Changed after two restarts",
          Json.obj(Json.list(Json.obj(updated.get("data")).get("attributes")).getLast())
              .get("description"));
      assertFalse(
          Json.bool(
              Json.obj(f.call("status", "run_id", id).get("current_approvals")), "metadata", true));
      op(f.workflow, id, "remove_attribute", Json.map("attribute_id", oldId));
      var exported = op(f.workflow, id, "export_xtf", Json.map());
      assertFalse(exported.containsKey("download_url"));
    }
  }

  @Test
  void incompleteDraftAndUnconfirmedMutationAreNotLostOrReplayed() throws Exception {
    var s = source();
    String id;
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      id = f.metadataOnly();
      op(
          f.workflow,
          id,
          "create_datasheet",
          Json.map("kind", "dataset", "values", Json.map("title", "Incomplete")));
      f.workflow.store.edit(
          id,
          r -> {
            r.put(
                "pending_metadata",
                Json.map(
                    "operation",
                    "update_metadata",
                    "arguments",
                    Json.map("values", Json.map("title", "UNCONFIRMED"))));
            return null;
          });
    }
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      f.error("metadata_uncertain", () -> op(f.workflow, id, "read_datasheet", Json.map()));
      var restored = op(f.workflow, id, "restore", Json.map());
      assertNotNull(restored.get("unconfirmed_operation"));
      assertEquals("Incomplete", Json.obj(restored.get("data")).get("title"));
      assertFalse(Json.bool(op(f.workflow, id, "validate_datasheet", Json.map()), "valid", true));
    }
  }

  void cli(String operation, Map<String, Object> args) {
    var result =
        new ProcessRunner()
            .run(
                List.of(
                    Path.of(System.getProperty("java.home"), "bin/java").toString(),
                    "-jar",
                    Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                    "--config",
                    temp.resolve("local.toml").toString(),
                    "call",
                    operation,
                    "--json",
                    Json.text(args)),
                Fixtures.ROOT,
                90,
                null);
    assertEquals(0, result.exitCode(), result.output());
  }

  @Test
  void separateCliProcessesAndIntegratorStdioUseOnlyPublishedImages() throws Exception {
    var s = source();
    var f = fixture(s, (n, a) -> Json.map());
    String id = f.metadataOnly();
    String config =
        Json.contents(temp.resolve("local.toml"))
            .replaceAll("(?m)^model_dirs = .*", "model_dirs = []");
    Json.write(
        temp.resolve("local.toml"),
        config
            + "\n[datasheet]\ntransport=\"stdio\"\nimage="
            + Json.text(Json.required(s.mcp("datasheet"), "image"))
            + "\n[interlis]\ntransport=\"stdio\"\nimage="
            + Json.text(Json.required(s.mcp("interlis"), "image"))
            + "\n");
    cli("metadata", Json.map("run_id", id, "operation", "import_xtf"));
    cli(
        "metadata",
        Json.map(
            "run_id",
            id,
            "operation",
            "update_metadata",
            "arguments",
            Json.map("values", Json.map("title", "Separate CLI processes"))));
    cli("metadata", Json.map("run_id", id, "operation", "export_xtf"));
    var params =
        ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin/java").toString())
            .args(
                "-jar",
                Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                "--config",
                temp.resolve("local.toml").toString(),
                "serve")
            .build();
    var transport =
        new StdioClientTransport(
            params, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    transport.setStdErrorHandler(line -> {});
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(90)).build()) {
      client.initialize();
      var result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "metadata", Json.map("run_id", id, "operation", "read_datasheet")));
      assertFalse(Boolean.TRUE.equals(result.isError()), Json.text(result));
      assertEquals(
          "Separate CLI processes",
          Json.obj(Json.obj(result.structuredContent()).get("data")).get("title"));
    }
  }

  @Test
  void httpStreamableSessionAndExportRegression() throws Exception {
    var s = source();
    var docker = new DockerMcps(s, new ProcessRunner());
    String name = docker.name("http-test");
    String image = Json.required(docker.selected("datasheet", true), "image_id");
    var process = new ProcessRunner();
    try {
      process.checked(
          List.of(
              "docker",
              "run",
              "--rm",
              "-d",
              "--name",
              name,
              "--label",
              "datenportal.integrator.owner=" + docker.owner(),
              "-p",
              "127.0.0.1::8000",
              "-e",
              "SPRING_PROFILES_ACTIVE=http",
              image),
          s.root,
          30);
      String port =
          process.checked(List.of("docker", "port", name, "8000/tcp"), s.root, 30).split(":")[1];
      String url = "http://127.0.0.1:" + port;
      var http = HttpClient.newHttpClient();
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      boolean ready = false;
      while (System.nanoTime() < deadline) {
        try {
          ready =
              http.send(
                          HttpRequest.newBuilder(URI.create(url + "/actuator/health"))
                              .timeout(Duration.ofSeconds(1))
                              .GET()
                              .build(),
                          HttpResponse.BodyHandlers.discarding())
                      .statusCode()
                  == 200;
        } catch (Exception ignored) {
        }
        if (ready) break;
        Thread.sleep(200);
      }
      assertTrue(ready);
      s.values.put(
          "datasheet",
          Json.map(
              "transport",
              "http",
              "url",
              url + "/mcp",
              "image",
              Json.required(s.mcp("datasheet"), "image")));
      try (var client = McpClients.datasheet(s)) {
        var draft =
            client.call(
                "import_xtf",
                Json.map("xml", Json.contents(Fixtures.FIXTURES.resolve("series.xtf"))));
        var exported =
            client.call(
                "export_xtf",
                Json.map(
                    "draft_id",
                    draft.get("draft_id"),
                    "expected_revision",
                    draft.get("revision"),
                    "include_xml",
                    true));
        assertTrue(exported.containsKey("download_url"));
        assertTrue(exported.containsKey("xml"));
      }
    } finally {
      docker.remove(name);
    }
  }

  @Test
  void publishedImageClosesOnEofAndHandlesLargeParallelResponses() throws Exception {
    var s = source();
    var docker = new DockerMcps(s, new ProcessRunner());
    String name = docker.name("eof-test");
    var transport =
        new EofStdioTransport(
            docker.launch(name, Json.required(docker.selected("datasheet", true), "image_id")),
            s.root,
            new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()),
            line -> {});
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(60)).build()) {
      client.initialize();
      assertEquals(12, client.listTools().tools().size());
      var created =
          client.callTool(
              new McpSchema.CallToolRequest("create_datasheet", Json.map("kind", "dataset")));
      var draft = Json.obj(created.structuredContent());
      if (draft.isEmpty())
        draft = Json.read(((McpSchema.TextContent) created.content().getFirst()).text());
      String draftId = Json.required(draft, "draft_id");
      var updated =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "upsert_attribute",
                  Json.map(
                      "draft_id",
                      draftId,
                      "expected_revision",
                      1,
                      "values",
                      Json.map("name", "LARGE", "description", "ä".repeat(90000)))));
      assertFalse(Boolean.TRUE.equals(updated.isError()));
      try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
        var requests = new ArrayList<java.util.concurrent.Callable<McpSchema.CallToolResult>>();
        for (int n = 0; n < 16; n++)
          requests.add(
              () ->
                  client.callTool(
                      new McpSchema.CallToolRequest(
                          "read_datasheet", Json.map("draft_id", draftId))));
        for (var response : pool.invokeAll(requests))
          assertFalse(Boolean.TRUE.equals(response.get().isError()));
      }
    } finally {
      docker.remove(name);
    }
    assertFalse(transport.process().isAlive());
    assertEquals(
        0, transport.process().exitValue(), "Normal shutdown must reach EOF, without TERM");
  }

  @Test
  void historicalSeriesAndAttributeIdsSurviveTwoRestarts() throws Exception {
    var s = source();
    String id, issueId, attributeId;
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      id =
          Json.required(
              f.call(
                  "start",
                  "organization",
                  "agi",
                  "identifier",
                  "ch.so.bevoelkerung.altersstruktur",
                  "issue",
                  "2025",
                  "metadata_path",
                  Fixtures.FIXTURES.resolve("series.xtf").toString()),
              "id");
      var draft = op(f.workflow, id, "import_xtf", Json.map());
      var issue = Json.obj(Json.list(Json.obj(draft.get("data")).get("issues")).getFirst());
      issueId = Json.required(issue, "issue_id");
      draft =
          op(
              f.workflow,
              id,
              "upsert_attribute",
              Json.map(
                  "issue_id",
                  issueId,
                  "values",
                  Json.map("name", "ISSUE_ATTRIBUTE", "data_type", "Text", "mandatory", false)));
      issue = Json.obj(Json.list(Json.obj(draft.get("data")).get("issues")).getFirst());
      attributeId =
          Json.required(Json.obj(Json.list(issue.get("attributes")).getFirst()), "attribute_id");
    }
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      op(f.workflow, id, "read_datasheet", Json.map());
    }
    try (var client = McpClients.datasheet(s)) {
      var f = fixture(s, client);
      var changed =
          op(
              f.workflow,
              id,
              "upsert_attribute",
              Json.map(
                  "issue_id",
                  issueId,
                  "attribute_id",
                  attributeId,
                  "values",
                  Json.map("description", "Series historical ID")));
      var issue = Json.obj(Json.list(Json.obj(changed.get("data")).get("issues")).getFirst());
      assertEquals(
          "Series historical ID",
          Json.obj(Json.list(issue.get("attributes")).getFirst()).get("description"));
      op(
          f.workflow,
          id,
          "remove_attribute",
          Json.map("issue_id", issueId, "attribute_id", attributeId));
      f.error(
          "not_found",
          () ->
              op(
                  f.workflow,
                  id,
                  "upsert_attribute",
                  Json.map(
                      "issue_id",
                      issueId,
                      "attribute_id",
                      attributeId,
                      "values",
                      Json.map("description", "Cannot resurrect deleted ID"))));
      // A confirmed atomic rejection must not be classified as an ambiguous mutation.
      assertFalse(f.call("status", "run_id", id).containsKey("pending_metadata"));
      op(f.workflow, id, "remove_issue", Json.map("issue_id", issueId));
    }
  }
}
