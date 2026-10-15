package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Synthetic authoring faults; actual MCP compiler, datasheet MCP, GRETL and ilivalidator. */
@Tag("integration")
class ModelRepairIntegrationTest {
  @TempDir(cleanup = CleanupMode.NEVER)
  Path temp;

  @ParameterizedTest
  @ValueSource(strings = {"compiler", "csv", "limit"})
  void repairWithActualConfiguredTools(String fault) throws Exception {
    Settings source = Fixtures.integrationSettings();
    try (var datasheet = McpClients.datasheet(source);
        var interlis = McpClients.interlis(source)) {
      int[] calls = {0};
      McpClients.ToolClient faultyAuthor =
          (operation, arguments) -> {
            var request = Json.read(Json.text(arguments));
            if (operation.equals("authorIliModel") && (calls[0]++ == 0 || fault.equals("limit"))) {
              var topic =
                  Json.obj(Json.list(Json.obj(request.get("spec")).get("topics")).getFirst());
              var clazz = Json.obj(Json.list(topic.get("classes")).getFirst());
              var attribute = Json.obj(Json.list(clazz.get("attributes")).getFirst());
              // Inject before the actual MCP compilation, never forge its result or proof.
              attribute.put(
                  "typeSpec",
                  fault.equals("csv")
                      ? Json.map(
                          "baseType", Json.map("kind", "NUM_RANGE", "min", 1900, "max", 2000))
                      : Json.map("domainFqn", "SO_Repair_20261010.MissingSyntheticType"));
            }
            return interlis.call(operation, request);
          };
      var f = new Fixtures(temp, new ProcessRunner(), datasheet, faultyAuthor);
      f.workflow.csvValidation = new CsvValidation(source, f.workflow.process);
      Workspace.copy(source.topics, f.repo);
      f.settings.values.put("validator_command", source.strings("validator_command", List.of()));
      Path csv = temp.resolve("input.csv"), metadata = temp.resolve("metadata.xtf");
      Json.write(csv, "Jahr\n2025\n");
      Json.write(metadata, Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")));
      String id =
          Json.required(
              f.call(
                  "start",
                  "workflow",
                  "model",
                  "organization",
                  "agi",
                  "identifier",
                  "ch.so.grundwasser.qualitaet",
                  "data_path",
                  csv.toString(),
                  "metadata_path",
                  metadata.toString()),
              "id");
      f.approve(id, "data", f.call("analyze", "run_id", id));
      var identity =
          Json.map(
              "name",
              "SO_Repair_20261010",
              "version",
              "2026-10-10",
              "uri",
              "https://example.org/repair");
      if (fault.equals("csv")) {
        f.error(
            "model_numeric_contract_violation",
            () ->
                f.call(
                    "derive_model",
                    "run_id",
                    id,
                    "identity",
                    identity,
                    "range_proposals",
                    ModelsTest.proposals()));
        f.error(
            "model_numeric_contract_violation",
            () ->
                f.call(
                    "derive_model",
                    "run_id",
                    id,
                    "identity",
                    identity,
                    "range_proposals",
                    ModelsTest.proposals()));
        assertEquals(1, calls[0], "Unchanged generator fault must not be retried");
        f.workflow.store.edit(
            id,
            run -> {
              assertNull(f.workflow.store.file(run, "model"));
              assertTrue(Json.obj(run.get("model_repair")).isEmpty());
              return null;
            });
        return;
      }
      var first =
          f.call(
              "derive_model",
              "run_id",
              id,
              "identity",
              identity,
              "range_proposals",
              ModelsTest.proposals());
      assertTrue(Json.bool(first, "candidate", false), Json.pretty(first));
      int repairs = fault.equals("limit") ? 3 : 1;
      for (int attempt = 1; attempt <= repairs; attempt++) {
        var series = Json.obj(f.call("status", "run_id", id).get("model_repair"));
        var args =
            Json.map(
                "run_id",
                id,
                "identity",
                identity,
                "range_proposals",
                ModelsTest.proposals(),
                "repair_of",
                series.get("last_failure"),
                "repair_reason",
                "Synthetische Korrektur " + attempt + ": " + fault);
        f.workflow.call("derive_model", args);
      }
      if (fault.equals("limit")) {
        f.error(
            "repair_limit_reached",
            () -> f.call("derive_model", "run_id", id, "identity", identity));
      } else {
        var success = f.call("validate_model", "run_id", id, "scope", "model");
        assertTrue(Json.bool(success, "valid", false), Json.pretty(success));
      }
      var state = f.call("status", "run_id", id);
      assertEquals(repairs, Json.number(Json.obj(state.get("model_repair")), "attempts", -1));
      for (Object check : Json.list(state.get("diagnostic_history")))
        for (Object item : Json.list(Json.obj(check).get("artifacts"))) {
          var artifact = Json.obj(item);
          assertEquals(artifact.get("sha256"), Json.sha(Path.of(Json.required(artifact, "path"))));
        }
      Json.write(
          temp.resolve("SYNTHETIC_ACCEPTANCE.json"),
          Json.pretty(
              Json.map(
                  "fault",
                  fault,
                  "run_id",
                  id,
                  "repair",
                  state.get("model_repair"),
                  "history",
                  state.get("diagnostic_history"))));
    }
  }
}
