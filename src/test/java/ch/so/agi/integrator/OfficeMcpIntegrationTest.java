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

/**
 * Real stdio MCP and INTERLIS validation; synthetic approval only in an isolated operator
 * workspace.
 */
@Tag("integration")
class OfficeMcpIntegrationTest {
  @TempDir Path temp;

  Map<String, Object> call(
      io.modelcontextprotocol.client.McpSyncClient client, String name, Object... args) {
    var result = client.callTool(new McpSchema.CallToolRequest(name, Json.map(args)));
    assertFalse(Boolean.TRUE.equals(result.isError()), Json.text(result.structuredContent()));
    return Json.obj(result.structuredContent());
  }

  @Test
  void sourceFreeBfsWorkflowWithRealValidator() throws Exception {
    var integration = Fixtures.integrationSettings();
    Path root = temp.resolve("operator");
    OperatorWorkspace.install(Fixtures.ROOT.resolve("build/operator-workspace"), root, false);
    assertFalse(Files.exists(root.resolve("src")));
    Path repo = temp.resolve("repo");
    Files.createDirectories(repo.resolve("shared/data"));
    Files.copy(Fixtures.FIXTURES.resolve("offices.xtf"), repo.resolve(Offices.PATH));
    Path models = temp.resolve("models");
    Files.createDirectories(models);
    try (var paths = Files.list(Fixtures.FIXTURES.resolve("models"))) {
      for (Path p : paths.toList()) Files.copy(p, models.resolve(p.getFileName()));
    }
    Path config = root.resolve("config/local.toml");
    Path installation = temp.resolve("package");
    Files.createDirectories(installation.resolve("templates"));
    Files.createDirectories(installation.resolve("lib"));
    Files.copy(
        Fixtures.ROOT.resolve("templates/review-java.html"),
        installation.resolve("templates/review-java.html"));
    Files.copy(
        Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar"),
        installation.resolve("lib/datenportal-integrator.jar"));
    var validator =
        integration.strings(
            "validator_command",
            List.of(
                JavaRuntime.java(),
                "-jar",
                Fixtures.ROOT.resolve("build/validator/ilivalidator.jar").toString()));
    Json.write(
        config,
        "root="
            + Json.text(root.toString())
            + "\ntopics_repo="
            + Json.text(repo.toString())
            + "\nmodel_dirs=["
            + Json.text(models.toString())
            + "]\nvalidator_command="
            + Json.text(validator)
            + "\n[datasheet]\ntransport=\"stdio\"\n");
    new Harness(new Settings(config)).install();
    var parameters =
        ServerParameters.builder(JavaRuntime.java())
            .env(Map.of("DATENPORTAL_AGENT_HOME", installation.toString()))
            .args(
                "-jar",
                installation.resolve("lib/datenportal-integrator.jar").toString(),
                "--config",
                config.toString(),
                "serve")
            .build();
    var transport =
        new StdioClientTransport(
            parameters, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    transport.setStdErrorHandler(System.err::println);
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(60)).build()) {
      client.initialize();
      assertTrue(
          Json.text(
                  client.listTools().tools().stream()
                      .filter(t -> t.name().equals("start"))
                      .findFirst()
                      .orElseThrow()
                      .inputSchema())
              .contains("allOf"));
      assertTrue(
          client.listTools().tools().stream().anyMatch(t -> t.name().equals("office_schema")));
      var schema = call(client, "operation_schema", "operation", "start");
      assertTrue(Json.obj(schema.get("inputSchema")).containsKey("allOf"));
      assertEquals(
          List.of(), call(client, "office_schema", "values", OfficesTest.bfs()).get("missing"));
      String id = Json.required(call(client, "start", "workflow", "office"), "id");
      call(client, "office", "run_id", id, "mode", "create", "values", OfficesTest.bfs());
      var report = call(client, "validate", "run_id", id);
      assertEquals(true, report.get("valid"), Json.pretty(report));
      var denied =
          client.callTool(
              new McpSchema.CallToolRequest("apply_local_changes", Json.map("run_id", id)));
      assertEquals(true, denied.isError());
      call(
          client,
          "approve",
          "run_id",
          id,
          "gate",
          "office",
          "fingerprint",
          report.get("fingerprint"),
          "human_statement",
          "SYNTHETIC ISOLATED INTEGRATION FIXTURE");
      call(client, "apply_local_changes", "run_id", id);
      assertTrue(Json.contents(repo.resolve(Offices.PATH)).contains("ch.bfs"));
      assertEquals(
          Set.of(Offices.PATH),
          Json.obj(call(client, "status", "run_id", id).get("changes")).keySet());
      var invalid = OfficesTest.bfs();
      invalid.put("phoneNumber", "0".repeat(65));
      var rejected =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "office", Json.map("run_id", id, "mode", "update", "values", invalid)));
      assertEquals(true, rejected.isError());
    }
  }
}
