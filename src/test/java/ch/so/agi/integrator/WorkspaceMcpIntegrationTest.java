package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class WorkspaceMcpIntegrationTest {
  @TempDir Path temp;

  @Test
  void sourceFreeWorkspaceExposesBothServersAndEnforcesFileBoundary() throws Exception {
    Path root = temp.resolve("operator");
    OperatorWorkspace.install(Fixtures.ROOT.resolve("build/operator-workspace"), root, false);
    assertFalse(Files.exists(root.resolve("src")));
    Path config = root.resolve("config/local.toml");
    Json.write(
        config,
        "root=\"..\"\ntopics_repo=\"../repo\"\nstack_repo=\"../stack\"\n[datasheet]\ntransport=\"stdio\"\n");
    var settings = new Settings(config);
    new Harness(settings).install();
    Path original = temp.resolve("data-invalid.csv");
    Json.write(original, "Id\r\n0012\r\n");
    String importedPath = null;
    for (String command : List.of("serve-workspace", "serve")) {
      var params =
          ServerParameters.builder(Path.of(System.getProperty("java.home"), "bin/java").toString())
              .args(
                  "-jar",
                  Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                  "--config",
                  config.toString(),
                  command)
              .build();
      var transport =
          new StdioClientTransport(
              params, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
      try (var client =
          McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(30)).build()) {
        client.initialize();
        if (command.equals("serve-workspace")) {
          for (String base : List.of("skills", ".agents/skills")) {
            String path =
                base + "/datenportal-validierungsmodell/references/typableitung-und-reparatur.md";
            for (String reference : List.of(path, root.resolve(path).toString())) {
              var referenceResult =
                  client.callTool(
                      new McpSchema.CallToolRequest("read_file", Json.map("path", reference)));
              assertFalse(
                  Boolean.TRUE.equals(referenceResult.isError()), Json.text(referenceResult));
              assertFalse(
                  Json.str(Json.obj(referenceResult.structuredContent()), "content", "").isBlank());
            }
          }
          assertEquals(
              WorkspaceFiles.TOOLS.keySet(),
              new HashSet<>(
                  client.listTools().tools().stream().map(McpSchema.Tool::name).toList()));
          Path report = settings.runs.resolve("fixture/model-review.html");
          Json.write(report, "<html>" + "x".repeat(600000) + "</html>");
          var page =
              client.callTool(
                  new McpSchema.CallToolRequest("read_file", Json.map("path", report.toString())));
          assertFalse(Boolean.TRUE.equals(page.isError()));
          assertEquals(6000, Json.obj(page.structuredContent()).get("next_offset"));
          assertTrue(((McpSchema.TextContent) page.content().getFirst()).text().length() < 16000);
          var nextPage =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "read_file", Json.map("path", report.toString(), "offset", 6000)));
          assertEquals(12000, Json.obj(nextPage.structuredContent()).get("next_offset"));
          Files.delete(report);
          Files.delete(report.getParent());
          Files.delete(settings.runs);
          var imported =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "import_attachment",
                      Json.map(
                          "organization",
                          "agi",
                          "path",
                          original.toString(),
                          "target_name",
                          "data_valid.csv",
                          "human_statement",
                          "Synthetische isolierte Testzustimmung: Kopie als data_valid.csv erstellen.")));
          assertFalse(Boolean.TRUE.equals(imported.isError()));
          var response = Json.obj(imported.structuredContent());
          assertEquals(true, response.get("verified"));
          assertEquals(true, response.get("renamed"));
          assertEquals("data-invalid.csv", response.get("original_name"));
          assertEquals("data_valid.csv", response.get("target_name"));
          assertTrue(Files.exists(original));
          assertEquals(Json.sha(original), response.get("copy_sha256"));
          importedPath = Json.required(response, "path");
          var rejected =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "read_file",
                      Json.map(
                          "path",
                          Fixtures.ROOT
                              .resolve("src/main/java/ch/so/agi/integrator/Workflow.java")
                              .toString())));
          assertTrue(rejected.isError());
          assertEquals(
              "workspace_access_denied", Json.obj(rejected.structuredContent()).get("error"));
        } else {
          var followup =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "operation_schema", Json.map("operation", "prepare_followup_delivery")));
          assertFalse(Boolean.TRUE.equals(followup.isError()));
          var response =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "operation_schema", Json.map("operation", "attach")));
          assertFalse(Boolean.TRUE.equals(response.isError()));
          assertEquals("attach", Json.obj(response.structuredContent()).get("operation"));
          assertFalse(Files.exists(settings.runs));
          var start =
              Json.map(
                  "organization",
                  "agi",
                  "identifier",
                  "ch.so.test",
                  "data_path",
                  original.toString());
          var rejected = client.callTool(new McpSchema.CallToolRequest("start", start));
          assertTrue(rejected.isError());
          assertEquals(
              "workspace_access_denied", Json.obj(rejected.structuredContent()).get("error"));
          assertFalse(Files.exists(settings.runs));
          start.put("data_path", importedPath);
          var created = client.callTool(new McpSchema.CallToolRequest("start", start));
          assertFalse(Boolean.TRUE.equals(created.isError()));
          String runId = Json.required(Json.obj(created.structuredContent()), "id");
          var selected =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "set_issue",
                      Json.map(
                          "run_id",
                          runId,
                          "issue",
                          "2025",
                          "human_statement",
                          "ISOLATED TEST: Ausgabe 2025")));
          assertFalse(Boolean.TRUE.equals(selected.isError()));
          assertEquals("2025", Json.obj(selected.structuredContent()).get("issue"));
          var resumed =
              client.callTool(new McpSchema.CallToolRequest("status", Json.map("run_id", runId)));
          assertEquals(runId, Json.obj(resumed.structuredContent()).get("id"));
          assertEquals("2025", Json.obj(resumed.structuredContent()).get("issue"));
        }
      }
    }
    assertTrue(Files.exists(settings.runs));
  }
}
