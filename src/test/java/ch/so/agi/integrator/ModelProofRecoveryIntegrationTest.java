package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Isolated synthetic reconstruction of the blocked eBau run; never touches an operator run. */
@Tag("integration")
class ModelProofRecoveryIntegrationTest {
  @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS)
  Path temp;

  @Test
  void recoversLegacyIncompleteDateUniqueWithoutChangingModelBytes() throws Exception {
    Settings runtime = Fixtures.integrationSettings();
    try (var datasheets = McpClients.datasheet(runtime);
        var interlis = McpClients.interlis(runtime)) {
      var f = new Fixtures(temp, new ProcessRunner(), datasheets, interlis);
      f.workflow.csvValidation = new CsvValidation(runtime, f.workflow.process);
      // Use the packaged real validator for metadata, without any publication services.
      String cfg =
          Json.contents(temp.resolve("local.toml"))
              .replace(
                  "validator_command = [\"fixture-validator\"]",
                  "validator_command = "
                      + Json.text(
                          runtime.strings(
                              "validator_command",
                              List.of(
                                  JavaRuntime.java(),
                                  "-jar",
                                  AgentResources.resolve(runtime.root, "lib/ilivalidator.jar")
                                      .toString()))));
      // A separate workflow uses this explicit fixture configuration.
      Json.write(temp.resolve("local.toml"), cfg);
      try (var w =
          new Workflow(
              new Settings(temp.resolve("local.toml")),
              new ProcessRunner(),
              datasheets,
              interlis)) {
        w.csvValidation = new CsvValidation(runtime, w.process);
        String identifier = "ch.so.ebau_statistik_dossiers";
        String modelName = "SO_eBau_Statistik_Dossiers_20261015";
        String[] columns = {
          "berichtsmonat", "anzahl_gemeinden_in_ebau", "anzahl_ueber_ebau_eingereichte_dossiers"
        };
        var attributes = new StringBuilder();
        for (int i = 0; i < columns.length; i++)
          attributes
              .append("<ns1:attributes><ns2:DatasetAttribute><ns2:name>")
              .append(columns[i])
              .append("</ns2:name><ns2:dataType>")
              .append(i == 0 ? "DATE" : "INTEGER")
              .append(
                  "</ns2:dataType><ns2:mandatory>true</ns2:mandatory></ns2:DatasetAttribute></ns1:attributes>");
        Path metadata = temp.resolve("fixture.xtf"), csv = temp.resolve("fixture.csv");
        Json.write(
            metadata,
            Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
                .replace("ch.so.grundwasser.qualitaet", identifier)
                .replaceAll("(?s)<ns1:attributes>.*?</ns1:attributes>", attributes.toString()));
        String header = String.join(";", columns) + "\n";
        Json.write(csv, header + "2024-11-01;6;12\n2024-12-01;9;36\n");
        var started =
            Json.obj(
                w.call(
                    "start",
                    Json.map(
                        "workflow",
                        "topic",
                        "organization",
                        "agi",
                        "identifier",
                        identifier,
                        "data_path",
                        csv.toString(),
                        "metadata_path",
                        metadata.toString())));
        String id = Json.required(started, "id");
        var dataCheck = Json.obj(w.call("analyze", Json.map("run_id", id)));
        approveFixture(w, id, dataCheck);
        var proposals = Json.map();
        for (int i = 1; i < columns.length; i++)
          proposals.put(
              columns[i],
              Json.map(
                  "type_spec",
                  Json.map(
                      "baseType",
                      Json.map("kind", "NUM_RANGE", "min", "0", "max", i == 1 ? "999" : "9999")),
                  "reason",
                  "SYNTHETIC ISOLATED FIXTURE: nonnegative counts with headroom",
                  "negative_values_possible",
                  false));
        var identity =
            Json.map(
                "name",
                modelName,
                "uri",
                "https://geo.so.ch/models/agi/" + modelName + ".ili",
                "version",
                "2026-10-15");
        var changes =
            List.of(
                Json.map(
                    "operation",
                    "ADD_CONSTRAINT",
                    "addConstraint",
                    Json.map(
                        "containerFqn",
                        modelName + ".Data.Observation",
                        "constraint",
                        Json.map(
                            "kind",
                            "UNIQUE",
                            "name",
                            "UniqueBerichtsmonat",
                            "scope",
                            "GLOBAL",
                            "keyPaths",
                            List.of("berichtsmonat")))));
        var derived =
            Json.obj(
                w.call(
                    "derive_model",
                    Json.map(
                        "run_id",
                        id,
                        "identity",
                        identity,
                        "range_proposals",
                        proposals,
                        "changes",
                        changes,
                        "allow_breaking",
                        true)));
        assertFalse(Json.bool(derived, "candidate", true), Json.pretty(derived));
        String hash = w.store.read(id, run -> Json.sha(w.store.file(run, "model")));
        // Reconstruct an old partially promoted candidate using only this isolated test state.
        w.store.edit(
            id,
            run -> {
              var model = Json.obj(run.get("model"));
              model.put("authoring_valid", false);
              model.remove("dataset_task_sha256");
              model.remove("package_ready");
              model.put("evidence", Json.map("status", "PROOF_INCOMPLETE", "proofVerified", false));
              run.remove("prepared_model");
              return null;
            });
        var blocked = Json.obj(w.call("validate_model", Json.map("run_id", id, "scope", "model")));
        assertFalse(Json.bool(blocked, "valid", true));
        assertFalse(Json.bool(blocked, "approvable", true));
        var refreshed =
            Json.obj(
                w.call(
                    "validate_model",
                    Json.map("run_id", id, "scope", "model", "refresh_evidence", true)));
        assertTrue(Json.bool(refreshed, "valid", false), Json.pretty(refreshed));
        assertEquals(hash, refreshed.get("model_sha256"));
        w.call("prepare_model_reference", Json.map("run_id", id));
        var integrated =
            Json.obj(w.call("validate_model", Json.map("run_id", id, "scope", "integration")));
        assertTrue(Json.bool(integrated, "approvable", false), Json.pretty(integrated));
        Json.write(csv, header + "2024-11-01;6;12\n2024-11-01;9;36\n");
        w.call("attach", Json.map("run_id", id, "role", "data", "path", csv.toString()));
        approveFixture(w, id, Json.obj(w.call("analyze", Json.map("run_id", id))));
        var duplicate = Json.obj(w.call("validate_model", Json.map("run_id", id)));
        assertFalse(Json.bool(duplicate, "valid", true));
        assertTrue(Json.bool(Json.obj(duplicate.get("csv_validation")), "executed", false));
        String log =
            Json.contents(Path.of(Json.required(Json.obj(duplicate.get("csv_validation")), "log")));
        assertTrue(
            log.contains("UniqueBerichtsmonat") || log.toLowerCase().contains("unique"), log);
      }
    }
  }

  private static void approveFixture(Workflow w, String id, Map<String, Object> report) {
    w.call(
        "approve",
        Json.map(
            "run_id",
            id,
            "gate",
            "data",
            "fingerprint",
            report.get("fingerprint"),
            "human_statement",
            "SYNTHETIC ISOLATED TEST FIXTURE ONLY"));
  }
}
