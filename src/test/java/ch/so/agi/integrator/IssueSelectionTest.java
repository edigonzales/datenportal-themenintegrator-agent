package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IssueSelectionTest {
  @TempDir Path temp;

  @Test
  void updatesExistingRunAndPreservesArtifactsWithoutMcpCalls() throws Exception {
    var f = new Fixtures(temp);
    String id = f.topic(true, "Jahr\n2025\n");
    f.workflow.store.edit(
        id,
        r -> {
          r.put("issue", null);
          r.put("draft", Json.map("revision", 2));
          r.put("model", Json.map("identity", "confirmed"));
          r.put("checks", Json.map("data", Json.map("valid", true)));
          r.put("approvals", Json.map("data", Json.map("human_statement", "ISOLATED TEST")));
          r.put("publication_plans", Json.map("local", Json.map("issue", "old")));
          return null;
        });
    var before = f.call("status", "run_id", id);
    var result =
        f.call("set_issue", "run_id", id, "issue", "2025", "human_statement", "TEST: Ausgabe 2025");
    assertEquals(true, result.get("changed"));
    var after = f.call("status", "run_id", id);
    assertEquals("2025", after.get("issue"));
    for (String key : List.of("id", "files", "changes", "draft", "model", "deliveries"))
      assertEquals(before.get(key), after.get(key));
    assertEquals(Json.map(), after.get("checks"));
    assertEquals(Json.map(), after.get("approvals"));
    assertFalse(after.containsKey("publication_plans"));
    assertEquals(
        before.get("approvals"),
        Json.obj(Json.list(after.get("issue_history")).getFirst()).get("approvals"));
    f.workflow.store.edit(
        id,
        r -> {
          r.put("checks", Json.map("data", Json.map("valid", true)));
          return null;
        });
    assertEquals(
        false,
        f.call("set_issue", "run_id", id, "issue", "2025", "human_statement", "TEST: wiederholt")
            .get("changed"));
    assertFalse(Json.obj(f.call("status", "run_id", id).get("checks")).isEmpty());
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("issue_history")).size());
    f.call("set_issue", "run_id", id, "issue", "2026", "human_statement", "TEST: Korrektur");
    f.call("set_issue", "run_id", id, "issue", "2025", "human_statement", "TEST: zurück");
    assertTrue(Json.obj(f.call("status", "run_id", id).get("approvals")).isEmpty());
    try (var paths = Files.list(f.settings.runs)) {
      assertEquals(1, paths.count());
    }
  }

  @Test
  void invalidOrSubmittedChangesAreAtomic() throws Exception {
    var f = new Fixtures(temp);
    String id = f.topic(true, "Jahr\n2025\n");
    for (String value : List.of("../2025", "2025\n", "", "2025/01")) {
      var before = f.call("status", "run_id", id);
      assertThrows(
          Problem.class,
          () -> f.call("set_issue", "run_id", id, "issue", value, "human_statement", "TEST"));
      assertEquals(before, f.call("status", "run_id", id));
    }
    for (String field :
        List.of(
            "pending_metadata",
            "pending_model",
            "applied",
            "pr_submission",
            "pull_request",
            "deliveries",
            "seeds")) {
      f.workflow.store.edit(
          id,
          r -> {
            r.put(field, Json.map("local", Json.map("status", "unknown")));
            return null;
          });
      var before = f.call("status", "run_id", id);
      assertThrows(
          Problem.class,
          () -> f.call("set_issue", "run_id", id, "issue", "2030", "human_statement", "TEST"));
      assertEquals(before, f.call("status", "run_id", id));
      f.workflow.store.edit(
          id,
          r -> {
            r.remove(field);
            return null;
          });
    }
  }
}
