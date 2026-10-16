package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataDecisionsTest {
  @TempDir Path temp;

  @Test
  void evidenceChangesMetadataFingerprintButNotDataApproval() throws Exception {
    var f = new Fixtures(temp);
    String id = f.topic(false, "Jahr\n2025\n");
    f.workflow.store.read(
        id,
        r -> {
          String data = f.workflow.fingerprint(r, "data");
          String metadata = f.workflow.fingerprint(r, "metadata");
          r.remove("decision_source");
          assertEquals(data, f.workflow.fingerprint(r, "data"));
          assertNotEquals(metadata, f.workflow.fingerprint(r, "metadata"));
          return null;
        });
  }

  @Test
  void importedValuesAreEvidenceButAgentDefaultsAndGenericApprovalAreNot() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("decision_source");
          return null;
        });
    var status = f.call("status", "run_id", id);
    assertEquals(2, Json.list(status.get("decision_blockers")).size());
    f.error(
        "metadata_decision_required",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "metadata",
                "fingerprint",
                "old",
                "human_statement",
                "OK"));
    for (String origin : List.of("llm", "user")) {
      f.call(
          "provenance",
          "run_id",
          id,
          "entries",
          List.of(
              Json.map(
                  "field", "accessLevel", "value", "open", "origin", origin, "source", "guessed")));
      assertEquals(2, Json.list(f.call("status", "run_id", id).get("decision_blockers")).size());
    }
    var entries = new ArrayList<Object>();
    entries.add(answer("access_level", "open"));
    entries.add(answer("publication_status", "published"));
    f.call("provenance", "run_id", id, "entries", entries);
    f.approveAll(id);
    assertEquals(List.of(), f.call("status", "run_id", id).get("decision_blockers"));
    entries.add(answer("publication_status", "draft"));
    f.call("provenance", "run_id", id, "entries", entries);
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("decision_blockers")).size());
  }

  static Map<String, Object> answer(String field, String value) {
    return Json.map(
        "field",
        field,
        "value",
        value,
        "origin",
        "user",
        "source",
        "isolated test answer",
        "human_statement",
        "AUTOMATISIERTE TESTFIXTURE: " + field + "=" + value);
  }

  @Test
  void spreadsheetEvidenceChecksTheActualCellAndCurrentValue() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    Path xlsx = temp.resolve("source.xlsx");
    try (var book = new XSSFWorkbook()) {
      book.createSheet("Decisions").createRow(0).createCell(0).setCellValue("open");
      try (var out = Files.newOutputStream(xlsx)) {
        book.write(out);
      }
    }
    f.call("attach", "run_id", id, "role", "metadata_source", "path", xlsx.toString());
    var evidence =
        Json.map(
            "field",
            "access_level",
            "value",
            "open",
            "origin",
            "xlsx",
            "source",
            "Decisions!A1",
            "sheet",
            "Decisions",
            "cell",
            "A1");
    f.call("provenance", "run_id", id, "entries", List.of(evidence));
    assertEquals(List.of(), f.call("status", "run_id", id).get("decision_blockers"));
    evidence.put("cell", "A2");
    f.call("provenance", "run_id", id, "entries", List.of(evidence));
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("decision_blockers")).size());
  }
}
