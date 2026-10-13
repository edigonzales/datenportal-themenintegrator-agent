package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RepositoryDatasheetsTest {
  @TempDir Path temp;
  Fixtures f;
  String id;
  Path folder;
  String xml;

  @BeforeEach
  void setup() throws Exception {
    f = new Fixtures(temp);
    id =
        Json.required(
            f.call("start", "organization", "agi", "identifier", "ch.so.grundwasser.qualitaet"),
            "id");
    folder = f.repo.resolve("agi/ch.so.grundwasser.qualitaet");
    Files.createDirectories(folder);
    xml = Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"));
  }

  @Test
  void recognizesContentsAndKeepsSnapshotSeparateFromAcceptedBaseline() {
    Path source = folder.resolve("beliebig.XML");
    Json.write(source, xml);
    Json.write(
        folder.resolve("office.xtf"), Json.contents(Fixtures.FIXTURES.resolve("offices.xtf")));
    Json.write(
        folder.resolve("wrong.xtf"), xml.replace("ch.so.grundwasser.qualitaet", "ch.so.other"));
    Json.write(
        folder.resolve("wrong-model.xtf"),
        xml.replace("SO_AGI_DataCatalog_Datasheet_20260523", "OtherModel"));
    Json.write(folder.resolve("broken.xml"), "<broken>");
    f.workflow.store.edit(
        id,
        r ->
            f.workflow.store.attach(
                r, Fixtures.FIXTURES.resolve("series.xtf"), "accepted_metadata"));
    var selected = f.call("repository_datasheet", "run_id", id);
    assertEquals(source.toString(), selected.get("source_path"));
    assertEquals(Json.sha(source), selected.get("sha256"));
    assertEquals(xml, Json.contents(Path.of(Json.required(selected, "path"))));
    assertFalse(Json.list(selected.get("diagnostics")).isEmpty());
    String before = f.workflow.store.edit(id, r -> f.workflow.fingerprint(r, "metadata"));
    f.call("repository_datasheet", "run_id", id);
    assertEquals(before, f.workflow.store.edit(id, r -> f.workflow.fingerprint(r, "metadata")));
    f.workflow.store.edit(
        id,
        r -> {
          assertFalse(r.containsKey("draft"));
          assertEquals(Json.sha(source), Json.sha(f.workflow.sheet(r)));
          assertEquals(
              Json.sha(Fixtures.FIXTURES.resolve("series.xtf")),
              Json.sha(f.workflow.store.file(r, "accepted_metadata")));
          return null;
        });
    assertEquals(xml, Json.contents(source));
  }

  @Test
  void multipleMatchesRequireExplicitSelectionAndNeverGuess() {
    Json.write(folder.resolve("first.xtf"), xml);
    Json.write(folder.resolve("second.xml"), xml);
    f.error("ambiguous_repository_metadata", () -> f.call("repository_datasheet", "run_id", id));
    var selected =
        f.call(
            "repository_datasheet",
            "run_id",
            id,
            "relative_path",
            "agi/ch.so.grundwasser.qualitaet/second.xml");
    assertEquals(folder.resolve("second.xml").toString(), selected.get("source_path"));
    f.error(
        "repository_datasheet_missing",
        () -> f.call("repository_datasheet", "run_id", id, "relative_path", "../outside.xtf"));
  }

  @Test
  void missingInvalidXmlAndEscapingSymlinksCannotBecomeSources() throws Exception {
    Path outside = temp.resolve("outside.xtf");
    Json.write(outside, xml);
    Files.createSymbolicLink(folder.resolve("alias.xtf"), outside);
    Json.write(folder.resolve("xxe.xml"), "<!DOCTYPE a SYSTEM 'file:///etc/passwd'><a/>");
    var failure = assertThrows(Problem.class, () -> f.call("repository_datasheet", "run_id", id));
    assertEquals("repository_datasheet_missing", failure.code);
    assertEquals(2, Json.list(failure.details.get("diagnostics")).size());
    assertFalse(Json.obj(f.call("status", "run_id", id).get("files")).containsKey("metadata"));
  }

  @Test
  void changedSourceCannotReplaceDraft() {
    Path source = folder.resolve("data.xtf");
    Json.write(source, xml);
    f.call("repository_datasheet", "run_id", id);
    f.workflow.store.edit(
        id,
        r -> {
          r.put("draft", Json.map("fixture", true));
          return null;
        });
    Json.write(source, xml + "\n");
    f.error("metadata_source_conflict", () -> f.call("repository_datasheet", "run_id", id));
  }

  @Test
  void seriesUsesTopicIdentifierAndIssue() throws Exception {
    id =
        Json.required(
            f.call(
                "start",
                "organization",
                "agi",
                "identifier",
                "ch.so.bevoelkerung.altersstruktur",
                "issue",
                "2025"),
            "id");
    Path source = f.repo.resolve("agi/ch.so.bevoelkerung.altersstruktur/series_any_name.xtf");
    Json.write(source, Json.contents(Fixtures.FIXTURES.resolve("series.xtf")));
    var selected = f.call("repository_datasheet", "run_id", id);
    assertEquals("series", Json.obj(selected.get("datasheet")).get("kind"));
  }

  @Test
  void metadataContractsListNamesAndManageImportInternally() {
    var schema = f.call("operation_schema", "operation", "metadata");
    assertTrue(
        Json.list(
                Json.obj(
                        Json.obj(Json.obj(schema.get("inputSchema")).get("properties"))
                            .get("operation"))
                    .get("enum"))
            .contains("import_xtf"));
    var nested = f.call("operation_schema", "operation", "metadata.import_xtf");
    assertTrue(
        Json.obj(
                Json.obj(
                        Json.obj(Json.obj(nested.get("inputSchema")).get("properties"))
                            .get("arguments"))
                    .get("properties"))
            .isEmpty());
  }
}
