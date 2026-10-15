package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic approvals and delivery evidence, exclusively in isolated test repositories. */
class PublicationApprovalTest {
  @TempDir Path temp;

  void profile(Fixtures f, String name, String kind) {
    var env = new LinkedHashMap<>(f.settings.environment("local"));
    env.put("kind", kind);
    if (!kind.equals("local")) env.put("repository_mode", "managed-git");
    Json.obj(f.settings.values.get("environments")).put(name, env);
  }

  Map<String, Object> publicationReview(Fixtures f, String id, String name, boolean plan) {
    return f.workflow.store.edit(
        id,
        r -> {
          if (plan) {
            r.computeIfAbsent("publication_plans", ignored -> Json.map());
            Json.obj(r.get("publication_plans")).put(name, Json.map("environment", name));
          }
          String fp = f.workflow.fingerprint(r, "publish:" + name);
          if (plan) Json.obj(Json.obj(r.get("publication_plans")).get(name)).put("fingerprint", fp);
          return Json.map("fingerprint", fp);
        });
  }

  void evidence(Fixtures f, String id, String fingerprint, boolean verified) {
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put("local", Json.map("verified", verified, "fingerprint", fingerprint));
          return null;
        });
  }

  @Test
  void localProfilesRejectAdditionalApprovalBeforeCheckingPlanOrDelivery() throws Exception {
    for (String name : List.of("local", "sandbox")) {
      var f = new Fixtures(temp.resolve(name));
      profile(f, name, "local");
      String id = f.topic(false, "Jahr\n2025\n");
      f.approveAll(id);
      for (boolean complete : List.of(false, true)) {
        if (complete) {
          evidence(
              f, id, f.workflow.fingerprint(f.workflow.store.edit(id, r -> r), "metadata"), true);
        }
        var review = publicationReview(f, id, name, complete);
        String before = Json.text(f.workflow.store.edit(id, r -> r).get("approvals"));
        f.error(
            "local_publication_approval_not_required",
            () -> f.approve(id, "publish:" + name, review));
        assertEquals(before, Json.text(f.workflow.store.edit(id, r -> r).get("approvals")));
      }
    }
  }

  @Test
  void remoteApprovalsRequireCurrentVerifiedLocalDelivery() throws Exception {
    for (String kind : List.of("int", "prod")) {
      var f = new Fixtures(temp.resolve(kind));
      profile(f, kind, kind);
      String id = f.topic(false, "Jahr\n2025\n");
      f.approveAll(id);
      var review = publicationReview(f, id, kind, true);
      f.error("local_test_required", () -> f.approve(id, "publish:" + kind, review));
      evidence(f, id, "stale-fingerprint", true);
      f.error("local_test_required", () -> f.approve(id, "publish:" + kind, review));
      String fp = f.workflow.fingerprint(f.workflow.store.edit(id, r -> r), "metadata");
      evidence(f, id, fp, false);
      f.error("local_test_required", () -> f.approve(id, "publish:" + kind, review));
      assertFalse(
          Json.obj(f.workflow.store.edit(id, r -> r).get("approvals"))
              .containsKey("publish:" + kind));
      evidence(f, id, fp, true);
      f.approve(id, "publish:" + kind, review);
      assertTrue(
          Json.obj(f.workflow.store.edit(id, r -> r).get("approvals"))
              .containsKey("publish:" + kind));
    }
  }

  @Test
  void localDeliveryStillRequiresBusinessApprovalAndAppliedChanges() throws Exception {
    var process =
        new Fixtures.FakeProcess() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (!args.getFirst().equals("docker")) return super.checked(args, cwd, timeout);
            if (args.contains("-aq")) return "fixture-container";
            if (args.contains("inspect"))
              return Json.text(
                  Json.map(
                      "mounts",
                      List.of(Json.map("type", "bind", "source", temp.resolve("repo").toString())),
                      "working_tree",
                      true,
                      "running",
                      true));
            if (args.contains("compose") && args.contains("ps"))
              return Json.text(
                  List.of("garage", "jenkins", "downloads").stream()
                      .map(service -> Json.map("Service", service, "State", "running"))
                      .toList());
            throw new AssertionError("Unexpected Docker operation in isolated fixture: " + args);
          }
        };
    var f =
        new Fixtures(
            temp,
            process,
            (name, args) -> {
              throw new AssertionError("Unexpected datasheet call");
            },
            (name, args) -> {
              throw new AssertionError("Unexpected model call");
            });
    f.workflow.credentials =
        (name, env) -> new Credentials.Value("synthetic", "synthetic", "fixture");
    String id = f.topic(false, "Jahr\n2025\n");
    String rel = "agi/ch.so.grundwasser.qualitaet/datasheet.xtf";
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        rel,
        "source_path",
        Fixtures.FIXTURES.resolve("dataset.xtf").toString());
    f.error("approval_required", () -> f.call("deliver", "run_id", id, "environment", "local"));
    f.approveAll(id);
    f.error("local_changes_missing", () -> f.call("deliver", "run_id", id, "environment", "local"));
    f.call("apply_local_changes", "run_id", id);
    var run = f.workflow.store.edit(id, r -> r);
    assertDoesNotThrow(
        () ->
            new Deliveries(f.workflow).ready(run, "local", f.settings.environment("local"), false));
    assertFalse(
        Json.obj(f.workflow.store.edit(id, r -> r).get("approvals")).containsKey("publish:local"));
  }
}
