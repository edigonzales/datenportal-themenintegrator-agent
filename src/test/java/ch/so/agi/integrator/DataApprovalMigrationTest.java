package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataApprovalMigrationTest {
  @TempDir Path temp;

  @Test
  void preservesActualResponseOnlyWhenOriginalFingerprintStillMatches() throws Exception {
    var f = new Fixtures(temp);
    String id = f.topic(false, "Jahr\n2025\n");
    var report = f.call("analyze", "run_id", id);
    f.approve(id, "data", report);
    String originalPolicy = f.settings.fingerprint();
    var result =
        f.workflow.store.edit(
            id,
            run ->
                DataApprovalMigration.migrate(
                    f.workflow, run, originalPolicy, "new-proof-component-policy"));
    assertEquals("preserved", Json.obj(result).get("status"));
    f.workflow.store.read(
        id,
        run -> {
          var approval = Json.obj(Json.obj(run.get("approvals")).get("data"));
          assertEquals(
              f.workflow.fingerprint(run, "data", "new-proof-component-policy"),
              approval.get("fingerprint"));
          assertEquals(
              1,
              Json.list(run.get("events")).stream()
                  .map(Json::obj)
                  .filter(e -> "approve".equals(e.get("action")))
                  .count());
          return null;
        });
  }

  @Test
  void cannotPreserveApprovalForChangedBytes() throws Exception {
    var f = new Fixtures(temp);
    String id = f.topic(false, "Jahr\n2025\n");
    f.approve(id, "data", f.call("analyze", "run_id", id));
    Path changed = temp.resolve("other.csv");
    Json.write(changed, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", changed.toString());
    var result =
        f.workflow.store.edit(
            id,
            run ->
                DataApprovalMigration.migrate(
                    f.workflow, run, f.settings.fingerprint(), "new-policy"));
    assertEquals("no_current_data_approval", Json.obj(result).get("status"));
  }
}
