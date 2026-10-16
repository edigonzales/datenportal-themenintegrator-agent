package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalChangesTest {
  @TempDir Path temp;

  @Test
  void snapshotsAreAdditiveAndLegacyApprovalsRemainValid() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    var before = f.call("status", "run_id", id);
    String original =
        Json.str(Json.obj(Json.obj(before.get("approvals")).get("metadata")), "fingerprint", "");
    f.workflow.store.edit(
        id,
        r -> {
          assertEquals(original, f.workflow.fingerprint(r, "metadata"));
          Json.obj(Json.obj(r.get("approvals")).get("metadata")).remove("fingerprint_components");
          assertEquals(original, f.workflow.fingerprint(r, "metadata"));
          return null;
        });
    var status = f.call("status", "run_id", id);
    assertEquals(true, Json.obj(status.get("current_approvals")).get("metadata"));
    assertEquals(
        "baseline_unavailable",
        Json.obj(Json.obj(status.get("approval_changes")).get("metadata")).get("state"));
  }

  @Test
  void organizationChangeExplainsStaleApprovalInStatusErrorAndReport() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    Json.write(
        f.repo.resolve("agi/build.gradle"),
        "ext.datenportalOrganization = [id: projectDir.name]\n");
    var status = f.call("status", "run_id", id);
    String changes = Json.text(status.get("approval_changes"));
    assertTrue(changes.contains("context/agi/build.gradle"));
    assertTrue(changes.contains("organization_context"));
    var error = assertThrows(Problem.class, () -> f.call("apply_local_changes", "run_id", id));
    assertTrue(Json.text(error.details.get("approval_changes")).contains("agi/build.gradle"));
    var report = f.call("validate", "run_id", id);
    assertTrue(Json.text(report.get("approval_changes")).contains("agi/build.gradle"));
    assertTrue(
        ReviewHtml.render(Reports.Kind.METADATA, "fixture", report)
            .contains("Änderungen seit Freigabe"));
  }

  @Test
  void topicCandidateChangeUsesRepairPathAndHashesOnly() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          var snapshot = ApprovalChanges.snapshot(f.workflow, r, "metadata");
          assertEquals(
              snapshot,
              Json.obj(Json.obj(r.get("approvals")).get("metadata")).get("fingerprint_components"));
          var changes = Json.obj(r.get("changes"));
          Path candidate = f.workflow.directory(r).resolve("synthetic-task.gradle");
          Json.write(candidate, "// synthetic changed task\n");
          changes.put(
              "agi/ch.so.grundwasser.qualitaet/dataset.gradle",
              Json.map(
                  "before_sha256",
                  null,
                  "sha256",
                  Json.sha(candidate),
                  "path",
                  candidate.toString()));
          var explanation = ApprovalChanges.describe(f.workflow, r, "metadata");
          assertTrue(Json.text(explanation).contains("topic_candidate"));
          assertTrue(Json.text(explanation).contains("Reparaturpfad"));
          return null;
        });
  }
}
