package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Isolated synthetic business data and approvals, actual configured MCPs and validators. */
@Tag("integration")
class ModelRevalidationIntegrationTest {
  @TempDir Path temp;

  @Test
  void changeContractAndUniqueWithCorrectedCsv() throws Exception {
    Settings source = Fixtures.integrationSettings();
    try (var datasheet = McpClients.datasheet(source);
        var interlis = McpClients.interlis(source)) {
      var calls = new ArrayList<String>();
      McpClients.ToolClient tracked =
          new McpClients.ToolClient() {
            public Map<String, Object> call(String name, Map<String, Object> args) {
              calls.add(name);
              return interlis.call(name, args);
            }

            public Map<String, Object> schema(String name) {
              return interlis.schema(name);
            }
          };
      var f = new Fixtures(temp, new ProcessRunner(), datasheet, tracked);
      f.settings.values.put(
          "validator_command",
          List.of(
              JavaRuntime.java(),
              "-jar",
              AgentResources.resolve(source.root, "lib/ilivalidator.jar").toString()));
      f.settings.values.put("model_dirs", source.modelDirectories());
      var contract = f.call("operation_schema", "operation", "derive_model.changes");
      assertTrue(calls.isEmpty());
      assertTrue(Json.text(contract).contains("keyPaths"));
      assertEquals(
          contract.get("inputSchema"),
          f.call("operation_schema", "operation", "derive_model").get("inputSchema"));
      var cli =
          new ProcessRunner()
              .run(
                  List.of(
                      JavaRuntime.java(),
                      "-cp",
                      JavaRuntime.classpath(),
                      Main.class.getName(),
                      "--config",
                      source.file.toString(),
                      "schema",
                      "derive_model.changes"),
                  temp,
                  120,
                  temp.resolve("schema-cli.log"));
      assertEquals(0, cli.exitCode(), cli.output());
      // The MCP may log startup text; compare its JSON stdout by locating the result object.
      String json = cli.output().substring(cli.output().indexOf("{\n"));
      assertEquals(contract, Json.read(json));

      String id = f.topic(false, "Jahr\n2025\n");
      f.approve(id, "data", f.call("analyze", "run_id", id));
      var identity =
          Json.map(
              "name",
              "SO_Revalidation_20261015",
              "version",
              "2026-10-15",
              "uri",
              "https://example.org/revalidation");
      var generated =
          f.call(
              "derive_model",
              "run_id",
              id,
              "identity",
              identity,
              "range_proposals",
              ModelsTest.proposals());
      assertEquals("GENERATED", generated.get("status"), Json.pretty(generated));
      assertEquals(true, f.call("validate_model", "run_id", id, "scope", "model").get("valid"));
      f.call("prepare_model_reference", "run_id", id);
      var initial = f.call("validate_model", "run_id", id);
      assertEquals(true, initial.get("valid"), Json.pretty(initial));
      f.approve(id, "model", initial);
      var approvedRules = f.call("status", "run_id", id).get("numeric_rule_confirmations");
      assertEquals(true, Json.obj(Json.list(approvedRules).getFirst()).get("confirmed"));
      Object originalSource = Json.obj(initial.get("model")).get("source_data_sha256");
      int before = calls.size();
      attach(f, id, "Jahr\n2026\n");
      var corrected = f.call("validate_model", "run_id", id);
      assertEquals(approvedRules, corrected.get("numeric_rule_confirmations"));
      assertEquals(true, corrected.get("valid"), Json.pretty(corrected));
      assertEquals(before, calls.size());
      assertEquals(originalSource, Json.obj(corrected.get("model")).get("source_data_sha256"));
      assertEquals(initial.get("model_sha256"), corrected.get("model_sha256"));
      assertEquals(
          false, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("model"));
      assertEquals(
          corrected.get("fingerprint"), f.call("validate_model", "run_id", id).get("fingerprint"));

      var unique =
          Json.map(
              "operation",
              "ADD_CONSTRAINT",
              "addConstraint",
              Json.map(
                  "containerFqn",
                  "SO_Revalidation_20261015.Data.Observation",
                  "constraint",
                  Json.map(
                      "kind",
                      "UNIQUE",
                      "name",
                      "UniqueJahr",
                      "scope",
                      "GLOBAL",
                      "keyPaths",
                      List.of("Jahr"))));
      // Explicit synthetic authorization: UNIQUE may reject formerly valid data.
      var changed =
          f.call(
              "derive_model",
              "run_id",
              id,
              "identity",
              identity,
              "changes",
              List.of(unique),
              "allow_breaking",
              true);
      assertEquals("APPLIED", changed.get("status"), Json.pretty(changed));
      var valid = f.call("validate_model", "run_id", id);
      assertEquals(true, valid.get("valid"), Json.pretty(valid));
      assertTrue(Json.required(valid, "model_text").contains("UNIQUE"));
      Object binding = Json.obj(valid.get("model")).get("validated_data_sha256");
      attach(f, id, "Jahr\n2026\n2026\n");
      var invalid = f.call("validate_model", "run_id", id);
      assertEquals(false, invalid.get("valid"));
      assertEquals(false, Json.obj(invalid.get("csv_validation")).get("valid"));
      assertEquals(binding, Json.obj(invalid.get("model")).get("validated_data_sha256"));
      attach(f, id, "Jahr\n2200\n");
      assertEquals(false, f.call("validate_model", "run_id", id).get("valid"));
    }
  }

  private void attach(Fixtures f, String id, String content) {
    Path csv = temp.resolve("corrected.csv");
    Json.write(csv, content);
    f.call("attach", "run_id", id, "role", "data", "path", csv.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
  }
}
