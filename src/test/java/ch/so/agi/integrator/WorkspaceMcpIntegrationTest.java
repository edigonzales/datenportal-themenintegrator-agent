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
        "root=\"..\"\ntopics_repo=\"../repo\"\nstack_repo=\"../stack\"\n[datasheet]\ntransport=\"http\"\nurl=\"http://127.0.0.1:8000/mcp\"\n");
    var settings = new Settings(config);
    new Harness(settings).install();
    Path original = temp.resolve("data.csv");
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
          assertEquals(
              WorkspaceFiles.TOOLS.keySet(),
              new HashSet<>(
                  client.listTools().tools().stream().map(McpSchema.Tool::name).toList()));
          var imported =
              client.callTool(
                  new McpSchema.CallToolRequest(
                      "import_attachment",
                      Json.map("organization", "agi", "path", original.toString())));
          assertFalse(Boolean.TRUE.equals(imported.isError()));
          var response = Json.obj(imported.structuredContent());
          assertEquals(true, response.get("verified"));
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
          assertNotNull(Json.obj(created.structuredContent()).get("id"));
        }
      }
    }
    assertTrue(Files.exists(settings.runs));
  }
}
