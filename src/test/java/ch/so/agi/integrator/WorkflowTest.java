package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WorkflowTest {
  @TempDir Path temp;
  Fixtures f;

  @BeforeEach
  void setup() throws Exception {
    f = new Fixtures(temp);
  }

  @Test
  void approvalsRequireCurrentSuccessfulChecks() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.error(
        "validation_required",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "data",
                "fingerprint",
                f.workflow.store.edit(id, r -> f.workflow.fingerprint(r, "data")),
                "human_statement",
                "TEST"));
    var r = f.call("analyze", "run_id", id);
    f.error(
        "stale_review",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "data",
                "fingerprint",
                "stale",
                "human_statement",
                "TEST"));
    f.approve(id, "data", r);
  }

  @Test
  void csvChangesInvalidateBoth() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    Path updated = temp.resolve("new.csv");
    Json.write(updated, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "path", updated.toString(), "role", "data");
    var current = Json.obj(f.call("status", "run_id", id).get("current_approvals"));
    assertEquals(false, current.get("data"));
    assertEquals(false, current.get("metadata"));
  }

  @Test
  void metadataChangesKeepCsvGate() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    Path p = temp.resolve("changed.xtf");
    Json.write(
        p,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace("Wasserqualität Grundwasser", "Neue Wasserqualität"));
    f.call("attach", "run_id", id, "path", p.toString(), "role", "metadata");
    var current = Json.obj(f.call("status", "run_id", id).get("current_approvals"));
    assertEquals(true, current.get("data"));
    assertEquals(false, current.get("metadata"));
  }

  @Test
  void tamperedArtifactBlocked() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.workflow.store.edit(
        id,
        r -> {
          Json.write(f.workflow.requiredFile(r, "data"), "tampered");
          return null;
        });
    f.error("artifact_changed", () -> f.call("analyze", "run_id", id));
  }

  @Test
  void metadataGateNeedsCsvGate() {
    String id = f.topic(false, "Jahr\n2025\n");
    var report = f.call("validate", "run_id", id);
    f.error("approval_required", () -> f.approve(id, "metadata", report));
  }

  @Test
  void metadataOnly() {
    String id = f.metadataOnly();
    assertTrue(Json.bool(f.call("analyze", "run_id", id), "skipped", false));
    f.approveAll(id);
  }

  @Test
  void seriesRequiresIssue() {
    Path data = temp.resolve("data.csv");
    Json.write(data, "Jahrgang\n2025\n");
    String id =
        Json.required(
            f.call(
                "start",
                "organization",
                "agi",
                "identifier",
                "ch.so.bevoelkerung.altersstruktur",
                "data_path",
                data.toString(),
                "metadata_path",
                Fixtures.FIXTURES.resolve("series.xtf").toString()),
            "id");
    var result = f.call("validate", "run_id", id);
    assertFalse(Json.bool(result, "valid", true));
    assertTrue(
        Json.list(result.get("errors")).stream()
            .map(Json::obj)
            .anyMatch(e -> Objects.equals(e.get("code"), "issue_required")));
  }

  @Test
  void seriesIssueIdentifierSeparatedFromLabel() {
    String id = f.topic(true, "Jahrgang\n2025\n");
    var r = f.call("validate", "run_id", id);
    assertTrue(Json.bool(r, "valid", false));
    assertEquals(
        "ch.so.bevoelkerung.altersstruktur_2025", Json.obj(r.get("sheet")).get("selected_issue"));
  }

  @Test
  void unknownOffice() {
    String id = f.metadataOnly();
    Path p = temp.resolve("unknown.xtf");
    Json.write(
        p, Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")).replace("ch.so.afu", "unknown"));
    f.call("attach", "run_id", id, "path", p.toString(), "role", "metadata");
    assertFalse(Json.bool(f.call("validate", "run_id", id), "valid", true));
  }

  @Test
  void survivesNewInstance() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    var other = new Workflow(f.settings);
    assertEquals(
        Json.obj(f.call("status", "run_id", id).get("current_approvals")),
        Json.obj(Json.obj(other.call("status", Json.map("run_id", id))).get("current_approvals")));
  }

  @Test
  void pendingUnexportedDraftBlocked() {
    String id = f.metadataOnly();
    f.workflow.store.edit(
        id,
        r -> {
          r.put("draft", Json.map("revision", 3));
          r.put("exported_revision", 2);
          return null;
        });
    f.error("draft_not_exported", () -> f.call("validate", "run_id", id));
  }

  @Test
  void stagingIsolatedAndConflictsBlocked() {
    String id = f.metadataOnly();
    String rel = "agi/ch.so.grundwasser.qualitaet/datasheet.xtf";
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        rel,
        "source_path",
        Fixtures.FIXTURES.resolve("dataset.xtf").toString());
    assertFalse(Files.exists(f.repo.resolve(rel)));
    f.approveAll(id);
    Json.write(f.repo.resolve(rel), "other person's change");
    f.error("repository_conflict", () -> f.call("apply_local_changes", "run_id", id));
  }

  @Test
  void appliedStandKeepsApproval() {
    String id = f.metadataOnly();
    String rel = "agi/ch.so.grundwasser.qualitaet/datasheet.xtf";
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        rel,
        "source_path",
        Fixtures.FIXTURES.resolve("dataset.xtf").toString());
    f.approveAll(id);
    f.call("apply_local_changes", "run_id", id);
    assertEquals(
        true, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("metadata"));
  }

  @Test
  void forbiddenFilesAndTraversal() {
    String id = f.metadataOnly();
    for (String rel :
        List.of("shared/gradle/organisation-common.gradle", "../outside", "agi/build/other.gradle"))
      f.error(
          rel.contains("..") ? "unsafe_path" : "change_not_allowed",
          () ->
              f.call(
                  "stage_change",
                  "run_id",
                  id,
                  "relative_path",
                  rel,
                  "source_path",
                  Fixtures.FIXTURES.resolve("dataset.xtf").toString()));
  }

  @Test
  void provenanceInvalidatesMetadataOnly() {
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.call(
        "provenance",
        "run_id",
        id,
        "entries",
        List.of(Json.map("field", "title", "origin", "user", "source", "Human supplied title")));
    var valid = Json.obj(f.call("status", "run_id", id).get("current_approvals"));
    assertEquals(true, valid.get("data"));
    assertEquals(false, valid.get("metadata"));
  }

  @Test
  void previewEscapesHtml() {
    assertFalse(
        Reports.html(Json.map("field", "<script>alert('x')</script>")).contains("<script>"));
    assertTrue(Reports.html("<script>").contains("&lt;script&gt;"));
  }

  @Test
  void secureXmlRejectsEntity() {
    f.error(
        "invalid_xml",
        () -> Xml.parse("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>"));
  }

  @Test
  void businessNormalizationOnlyIgnoresManagedDates() {
    var a =
        Json.map(
            "title",
            "A",
            "issued",
            "2025",
            "attributes",
            Json.map("DatasetAttribute", List.of(Json.map("name", "x"), Json.map("name", "y"))),
            "keywords",
            List.of("one"));
    var b =
        Json.map(
            "title",
            "A",
            "modified",
            "2026",
            "attributes",
            List.of(
                Json.map("DatasetAttribute", Json.map("name", "x")),
                Json.map("DatasetAttribute", Json.map("name", "y"))),
            "keywords",
            "one");
    assertEquals(Xml.business(a), Xml.business(b));
    b.put("title", "B");
    assertNotEquals(Xml.business(a), Xml.business(b));
  }

  @Test
  void organizationNeedsNoInventedDataset() {
    String id =
        Json.required(
            f.call("start", "workflow", "organization", "organization", "fixtureorg"), "id");
    assertNull(f.call("status", "run_id", id).get("identifier"));
    f.error("wrong_workflow", () -> f.call("deliver", "run_id", id));
  }

  @Test
  void unknownOperationsArgumentsRejected() {
    f.error("unknown_operation", () -> f.call("invent"));
    f.error(
        "invalid_arguments",
        () -> f.call("start", "organization", "agi", "identifier", "topic", "surprise", true));
    f.error("invalid_arguments", () -> f.call("start", "organization", 5));
  }

  @Test
  void realDeliveryCannotRetryAcceptedOrUnknown() {
    String id = f.metadataOnly();
    f.approveAll(id);
    for (String p : List.of("accepted", "unknown")) {
      f.workflow.store.edit(
          id,
          r -> {
            Json.obj(r.get("deliveries"))
                .put("local", Json.map("phase", "failed", "report", Json.map("publication", p)));
            return null;
          });
      f.error("retry_unsafe", () -> f.call("retry_delivery", "run_id", id, "environment", "local"));
    }
  }

  @Test
  void unknownSubmissionNeverRestarts() {
    String id = f.metadataOnly();
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries")).put("local", Json.map("phase", "delivery_submitting"));
          return null;
        });
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
  }

  @Test
  void correctionAfterOwnApplyRemainsPossible() {
    String id = f.metadataOnly();
    String rel = "agi/ch.so.grundwasser.qualitaet/datasheet.xtf";
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        rel,
        "source_path",
        Fixtures.FIXTURES.resolve("dataset.xtf").toString());
    f.approveAll(id);
    f.call("apply_local_changes", "run_id", id);
    Path updated = temp.resolve("updated.xtf");
    Json.write(
        updated,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace("Wasserqualität Grundwasser", "Korrigierte Wasserqualität Grundwasser"));
    f.call("attach", "run_id", id, "role", "metadata", "path", updated.toString());
    f.call("stage_change", "run_id", id, "relative_path", rel, "source_path", updated.toString());
    f.approveAll(id);
    f.call("apply_local_changes", "run_id", id);
    assertEquals(Json.sha(updated), Json.sha(f.repo.resolve(rel)));
  }

  @Test
  void foreignDeletionRemainsConflict() throws Exception {
    String id = f.metadataOnly();
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        "shared/data/offices.xtf",
        "source_path",
        Fixtures.FIXTURES.resolve("offices.xtf").toString());
    f.approveAll(id);
    Files.delete(f.repo.resolve("shared/data/offices.xtf"));
    f.error("repository_conflict", () -> f.call("apply_local_changes", "run_id", id));
  }

  @Test
  void migratedApprovalsHistoricalPendingAndJobsPreserved() {
    String id = f.metadataOnly();
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          r.put("schema_version", 1);
          r.remove("workflow");
          r.put("pending_metadata", Json.map("operation", "update_metadata"));
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map("phase", "delivery_submitting", "job", Json.map("queue", "123")));
          return null;
        });
    f.error("migration_required", () -> f.call("status", "run_id", id));
    assertFalse(Json.bool(f.call("migrate_run", "run_id", id), "applied", true));
    var result = f.call("migrate_run", "run_id", id, "apply", true);
    assertTrue(Files.isRegularFile(Path.of(Json.required(result, "backup"))));
    var r = f.call("status", "run_id", id);
    assertTrue(Json.obj(r.get("approvals")).isEmpty());
    assertFalse(Json.obj(r.get("historical_approvals")).isEmpty());
    assertEquals(
        "123",
        Json.obj(Json.obj(Json.obj(r.get("deliveries")).get("local")).get("job")).get("queue"));
    assertNotNull(r.get("pending_metadata"));
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
  }
}
