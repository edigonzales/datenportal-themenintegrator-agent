package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic approvals are confined to Fixtures' isolated temporary repository. */
class MetadataArtifactsTest {
  @TempDir Path temp;
  Fixtures f;
  ModelsTest.Datasheet data;
  String id;
  static final String TOPIC = "ch.so.grundwasser.qualitaet";

  @BeforeEach
  void setup() throws Exception {
    data = new ModelsTest.Datasheet();
    f =
        new Fixtures(
            temp,
            new Fixtures.FakeProcess(),
            data,
            (op, args) -> {
              throw new AssertionError(op);
            });
    id = f.metadataOnly();
    metadata("import_xtf", Json.map());
  }

  Map<String, Object> metadata(String op, Map<String, Object> args) {
    return f.call("metadata", "run_id", id, "operation", op, "arguments", args);
  }

  Map<String, Object> state() {
    return f.workflow.store.read(id, r -> Json.read(Json.text(r)));
  }

  @Test
  void exportSchemaExposesManagedFilenameAndSupportedTypes() {
    var contract = Json.obj(MetadataContracts.schema(data, "export_xtf"));
    var schema = Json.obj(contract.get("inputSchema"));
    var arguments = Json.obj(Json.obj(schema.get("properties")).get("arguments"));
    assertTrue(Json.obj(arguments.get("properties")).containsKey("repository_filename"));
    assertEquals(MetadataArtifacts.TYPES, contract.get("supported_data_types"));
    assertEquals(
        new HashSet<>(MetadataArtifacts.TYPES),
        new HashSet<>(
            Json.strings(
                Json.read(Fixtures.ROOT.resolve("config/rules.json")).get("supported_types"))));
  }

  @Test
  void exportsReplaceCandidateAndApplyOnlyReviewedBytes() throws Exception {
    var result = metadata("export_xtf", Json.map());
    String relative = "agi/" + TOPIC + "/meta-" + TOPIC + ".xtf";
    assertEquals(relative, Json.obj(result.get("metadata_source")).get("repository_path"));
    assertFalse(Files.exists(f.repo.resolve(relative)));
    metadata("export_xtf", Json.map());
    assertEquals(1, Json.obj(state().get("changes")).size());
    metadata("export_xtf", Json.map("repository_filename", "custom.xtf"));
    String renamed = "agi/" + TOPIC + "/custom.xtf";
    assertEquals(Set.of(renamed), Json.obj(state().get("changes")).keySet());
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    var report = f.call("validate", "run_id", id);
    assertEquals(true, report.get("valid"), Json.pretty(report));
    f.approve(id, "metadata", report);
    f.call("apply_local_changes", "run_id", id);
    assertEquals(
        Json.obj(Json.obj(state().get("files")).get("metadata")).get("sha256"),
        Json.sha(f.repo.resolve(renamed)));
    assertFalse(Files.exists(f.repo.resolve(relative)));
  }

  @Test
  void identicalAttachPreservesDraftAndDifferentSourceIsRejected() {
    metadata("export_xtf", Json.map());
    var before = state();
    String path = Json.required(Json.obj(Json.obj(before.get("files")).get("metadata")), "path");
    f.call("attach", "run_id", id, "role", "metadata", "path", path);
    assertEquals(before.get("draft"), state().get("draft"));
    assertEquals(before.get("exported_artifact_sha256"), state().get("exported_artifact_sha256"));
    f.error(
        "metadata_source_conflict",
        () ->
            f.call(
                "attach",
                "run_id",
                id,
                "role",
                "metadata",
                "path",
                Fixtures.FIXTURES.resolve("series.xtf").toString()));
    metadata("update_metadata", Json.map("values", Json.map("title", "Updated")));
  }

  @Test
  void identityMismatchIsRejectedBeforeExternalMutationAndAtExport() {
    int calls = data.calls.size();
    f.error(
        "datasheet_identifier_mismatch",
        () ->
            metadata(
                "update_metadata", Json.map("values", Json.map("identifier", "xlsx-proposal"))));
    assertEquals(calls, data.calls.size());
    data.xml = data.xml.replace(TOPIC, "xlsx-proposal");
    f.error("datasheet_identifier_mismatch", () -> metadata("export_xtf", Json.map()));
    assertTrue(Json.obj(state().get("changes")).isEmpty());
    metadata("update_metadata", Json.map("values", Json.map("identifier", TOPIC)));
    metadata("export_xtf", Json.map());
  }

  @Test
  void creationDefaultsIdentifier() {
    String other = Json.required(f.call("start", "organization", "agi", "identifier", TOPIC), "id");
    f.call(
        "metadata",
        "run_id",
        other,
        "operation",
        "create_datasheet",
        "arguments",
        Json.map("kind", "dataset", "values", Json.map("title", "Example")));
    assertEquals(TOPIC, Xml.text(Xml.datasets(Xml.parse(data.xml)).getFirst(), "identifier"));
  }

  @Test
  void invalidTypesAndNamesFailBeforeRegistration() {
    data.xml =
        data.xml
            .replace("INTEGER", "Ganzzahl")
            .replace("NUMERIC", "Ganzzahl")
            .replace("numeric", "Ganzzahl");
    // Fixture has numeric attributes; an unsupported label must never become an active export.
    assertTrue(data.xml.contains("Ganzzahl"));
    var error = assertThrows(Problem.class, () -> metadata("export_xtf", Json.map()));
    assertEquals("unsupported_type", error.code);
    assertEquals(MetadataArtifacts.TYPES, error.details.get("supported_types"));
    assertTrue(Json.obj(state().get("changes")).isEmpty());
    f.error(
        "invalid_repository_filename",
        () -> metadata("export_xtf", Json.map("repository_filename", "../bad.xtf")));
  }

  @Test
  void duplicatesAndRepositoryRediscoveryCannotStartAnotherLoop() {
    metadata("export_xtf", Json.map());
    String path = Json.required(Json.obj(Json.obj(state().get("files")).get("metadata")), "path");
    f.error(
        "metadata_artifact_managed",
        () ->
            f.call(
                "stage_change",
                "run_id",
                id,
                "relative_path",
                "agi/" + TOPIC + "/diagnostic.xtf",
                "source_path",
                path));
    f.error("metadata_source_active", () -> f.call("repository_datasheet", "run_id", id));
    f.workflow.store.edit(
        id,
        r -> {
          f.workflow.stage(r, "agi/" + TOPIC + "/legacy.xtf", Path.of(path));
          return null;
        });
    f.error("ambiguous_repository_metadata", () -> metadata("export_xtf", Json.map()));
  }

  @Test
  void selectedRepositoryFilenameSurvivesExportAndConflict() throws Exception {
    String relative = "agi/" + TOPIC + "/existing.xml";
    Path target = f.repo.resolve(relative);
    Files.createDirectories(target.getParent());
    Files.copy(Fixtures.FIXTURES.resolve("dataset.xtf"), target);
    String other = Json.required(f.call("start", "organization", "agi", "identifier", TOPIC), "id");
    f.call("repository_datasheet", "run_id", other);
    id = other;
    metadata("import_xtf", Json.map());
    metadata("export_xtf", Json.map());
    assertEquals(relative, state().get("metadata_target"));
    var report = f.call("validate", "run_id", id);
    f.approve(id, "metadata", report);
    Json.write(target, "external modification");
    f.error("repository_conflict", () -> f.call("apply_local_changes", "run_id", id));
    assertEquals("external modification", Json.contents(target));
  }
}
