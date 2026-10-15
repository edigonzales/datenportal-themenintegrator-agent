package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class MetadataDraftsTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

  @Test
  void rejectionAndAmbiguousTransportKeepConfirmedCheckpoint() throws Exception {
    var snapshot = Json.obj(run().get("draft"));
    snapshot.put("draft_id", "test");
    snapshot.put("revision", 1);
    var mode = new String[] {"ok"};
    var calls = new int[] {0};
    McpClients.ToolClient client =
        (name, args) -> {
          calls[0]++;
          if (name.equals("read_datasheet") || name.equals("import_xtf")) return snapshot;
          if (mode[0].equals("reject"))
            throw new Problem(
                "invalid_arguments",
                "Rejected",
                "response",
                Json.map("error", "invalid_arguments"));
          if (mode[0].equals("transport")) throw new Problem("mcp_unavailable", "Unknown outcome");
          return snapshot;
        };
    var f = new Fixtures(temp, new Fixtures.FakeProcess(), client, (n, a) -> Json.map());
    String id =
        Json.required(f.call("start", "organization", "agi", "identifier", "ch.so.test"), "id");
    mode[0] = "reject";
    f.error(
        "invalid_arguments",
        () ->
            f.call(
                "metadata",
                "run_id",
                id,
                "operation",
                "create_datasheet",
                "arguments",
                Json.map("kind", "series")));
    var rejected = f.workflow.store.edit(id, r -> Json.read(Json.text(r)));
    assertFalse(rejected.containsKey("pending_metadata"));
    assertFalse(rejected.containsKey("draft_base"));
    assertFalse(rejected.containsKey("draft"));
    f.workflow.store.edit(
        id,
        r -> {
          r.putAll(run());
          r.put("draft", snapshot);
          MetadataDrafts.checkpoint(r);
          return null;
        });
    var checkpoint = f.workflow.store.edit(id, r -> r.get("draft_checkpoint"));
    f.error(
        "invalid_arguments",
        () ->
            f.call(
                "metadata",
                "run_id",
                id,
                "operation",
                "update_metadata",
                "arguments",
                Json.map("values", Json.map("title", "Rejected"))));
    assertFalse((boolean) f.workflow.store.edit(id, r -> r.containsKey("pending_metadata")));
    mode[0] = "transport";
    f.error(
        "mcp_unavailable",
        () ->
            f.call(
                "metadata",
                "run_id",
                id,
                "operation",
                "update_metadata",
                "arguments",
                Json.map("values", Json.map("title", "Unknown"))));
    assertTrue((boolean) f.workflow.store.edit(id, r -> r.containsKey("pending_metadata")));
    assertEquals(checkpoint, f.workflow.store.edit(id, r -> r.get("draft_checkpoint")));
    f.error("mcp_unavailable", () -> f.call("metadata", "run_id", id, "operation", "restore"));
    int count = calls[0];
    f.error(
        "draft_restore_blocked", () -> f.call("metadata", "run_id", id, "operation", "restore"));
    assertEquals(count, calls[0]);
    assertEquals(checkpoint, f.workflow.store.edit(id, r -> r.get("draft_checkpoint")));
  }

  @Test
  void mismatchingRestoreNeverPromotesAndDoesNotRepeatMutations() throws Exception {
    var r = run();
    MetadataDrafts.checkpoint(r);
    String confirmed = Json.text(r.get("draft_checkpoint"));
    var wrong = Json.read(Json.text(Json.obj(r.get("draft"))));
    wrong.put("basket_id", "different");
    Json.obj(wrong.get("data")).put("title", "Unexpected");
    int[] calls = {0};
    var f =
        new Fixtures(
            temp,
            new Fixtures.FakeProcess(),
            (n, a) -> {
              calls[0]++;
              return wrong;
            },
            (n, a) -> Json.map());
    var mismatch = assertThrows(Problem.class, () -> f.workflow.restoreDraft(r));
    assertEquals("draft_restore_mismatch", mismatch.code);
    assertEquals(List.of("basket_id"), mismatch.details.get("identity_fields"));
    assertEquals(List.of("data.title"), mismatch.details.get("content_fields"));
    int previous = calls[0];
    error("draft_restore_blocked", () -> f.workflow.restoreDraft(r));
    assertEquals(previous, calls[0]);
    assertEquals(confirmed, Json.text(r.get("draft_checkpoint")));
    assertEquals(Json.obj(run().get("draft")), r.get("draft"));
  }

  Map<String, Object> run() {
    return Json.map(
        "identifier",
        "ch.so.test",
        "draft_base",
        McpClients.shell("series", "b1", "o1"),
        "draft",
        Json.map(
            "kind",
            "series",
            "basket_id",
            "b1",
            "object_id",
            "o1",
            "data",
            Json.map(
                "identifier",
                "ch.so.test",
                "title",
                "Confirmed",
                "issues",
                List.of(
                    Json.map(
                        "issue_id",
                        "internal",
                        "identifier",
                        "ch.so.test.edition",
                        "issue_label",
                        "2025")))));
  }

  void error(String code, Runnable action) {
    assertEquals(code, assertThrows(Problem.class, action::run).code);
  }

  @Test
  void scopesAndIdentifiersAreDistinct() {
    var r = run();
    for (String op : List.of("create_datasheet", "import_xtf"))
      error("draft_exists", () -> MetadataDrafts.preflight(r, op, Json.map()));
    error(
        "metadata_field_scope",
        () ->
            MetadataDrafts.preflight(
                r, "update_metadata", Json.map("values", Json.map("issue_label", "2025"))));
    error(
        "metadata_issue_not_found",
        () -> MetadataDrafts.preflight(r, "upsert_issue", Json.map("issue_id", "2025")));
    error(
        "metadata_issue_identifier_required",
        () ->
            MetadataDrafts.preflight(
                r, "upsert_issue", Json.map("values", Json.map("issue_label", "2025"))));
    error(
        "datasheet_issue_identifier_mismatch",
        () ->
            MetadataDrafts.preflight(
                r,
                "update_metadata",
                Json.map("issue_id", "internal", "values", Json.map("identifier", "ch.so.test"))));
    MetadataDrafts.preflight(
        r,
        "update_metadata",
        Json.map("issue_id", "internal", "values", Json.map("identifier", "ch.so.test.edition")));
    r.put("draft_id_aliases", Json.map("historic", "internal"));
    MetadataDrafts.preflight(
        r,
        "upsert_issue",
        Json.map("issue_id", "historic", "values", Json.map("title", "Updated")));
  }

  @Test
  void checkpointIsIndependentAndTamperingFails() {
    var r = run();
    MetadataDrafts.checkpoint(r);
    String base = Json.required(r, "draft_base");
    r.put("draft_base", "corrupt mutable base");
    assertEquals(base, MetadataDrafts.recovery(r).get("draft_base"));
    Json.obj(Json.obj(r.get("draft")).get("data")).put("title", "Tampered");
    error("draft_checkpoint_invalid", () -> MetadataDrafts.recovery(r));
  }

  @Test
  void legacyRecoveryRequiresEvidenceAndPreservesOriginalIdentities() {
    var r = run();
    r.put("draft_base", McpClients.shell("series", "wrongB", "wrongO"));
    error("draft_recovery_evidence_missing", () -> MetadataDrafts.recovery(r));
    r.put(
        "pending_metadata",
        Json.map("operation", "create_datasheet", "arguments", Json.map("kind", "series")));
    var recovered = MetadataDrafts.recovery(r);
    assertTrue(
        MetadataDrafts.matchingIdentity(
            Json.required(recovered, "draft_base"), Json.obj(r.get("draft"))));
    assertNotEquals(r.get("draft_base"), recovered.get("draft_base"));
    r.put("files", Json.map("metadata", "imported"));
    error("draft_recovery_evidence_missing", () -> MetadataDrafts.recovery(r));
  }

  @Test
  void diagnosticsSeparateIdentityAndContentAndIgnoreEphemeralIds() {
    var before = Json.obj(run().get("draft"));
    var after = Json.read(Json.text(before));
    Json.obj(Json.list(Json.obj(after.get("data")).get("issues")).getFirst())
        .put("issue_id", "new");
    MetadataDrafts.compare(before, after);
    after.put("basket_id", "wrong");
    Json.obj(after.get("data")).put("title", "Wrong");
    var p = assertThrows(Problem.class, () -> MetadataDrafts.compare(before, after));
    assertEquals(List.of("basket_id"), p.details.get("identity_fields"));
    assertEquals(List.of("data.title"), p.details.get("content_fields"));
  }
}
