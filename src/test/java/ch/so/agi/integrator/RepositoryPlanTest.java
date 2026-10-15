package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic approvals and evidence only in isolated fixtures. */
class RepositoryPlanTest {
  @TempDir Path temp;

  Fixtures fixture(String remote) throws Exception {
    return new Fixtures(
        temp,
        new Fixtures.FakeProcess() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (args.equals(List.of("git", "remote", "get-url", "origin"))) return remote;
            if (args.getFirst().equals("git"))
              throw new AssertionError("Unexpected Git mutation: " + args);
            return super.checked(args, cwd, timeout);
          }
        },
        (n, a) -> {
          throw new AssertionError("No datasheet calls");
        },
        (n, a) -> {
          throw new AssertionError("No model calls");
        });
  }

  void stage(Fixtures f, String id) {
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        "agi/ch.so.grundwasser.qualitaet/datasheet.xtf",
        "source_path",
        Fixtures.FIXTURES.resolve("dataset.xtf").toString());
  }

  void evidence(Fixtures f, String id, boolean verified, boolean current) {
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map(
                      "verified",
                      verified,
                      "fingerprint",
                      current ? f.workflow.fingerprint(r, "metadata") : "old"));
          return null;
        });
  }

  @Test
  void planIsReadOnlyAndListsMissingRequirements() throws Exception {
    var f = fixture("git@github.com:fixture/repo.git");
    String id = f.topic(false, "Jahr\n2025\n");
    stage(f, id);
    Path state = f.workflow.store.directory(id).resolve("state.json");
    String before = Files.readString(state);
    var modified = Files.getLastModifiedTime(state);
    var plan = f.call("repository_plan", "run_id", id, "environment", "local");
    assertEquals("https://github.com/fixture/repo", plan.get("repository"));
    assertEquals("origin", plan.get("remote"));
    assertEquals("main", plan.get("base"));
    assertEquals(false, plan.get("ready"));
    assertEquals(3, Json.list(plan.get("blockers")).size());
    assertEquals(before, Files.readString(state));
    assertEquals(modified, Files.getLastModifiedTime(state));
    assertFalse(
        Files.exists(
            f.workflow.directory(f.workflow.store.read(id, r -> r)).resolve("pr-checkout")));
    f.approveAll(id);
    evidence(f, id, true, true);
    assertEquals(
        true, f.call("repository_plan", "run_id", id, "environment", "local").get("ready"));
  }

  @Test
  void prRejectsMissingApprovalAndUnsuccessfulOrStaleLocalTestBeforeGit() throws Exception {
    var f = fixture("https://github.com/fixture/repo.git");
    String id = f.topic(false, "Jahr\n2025\n");
    stage(f, id);
    f.error("approval_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    f.approveAll(id);
    f.error(
        "local_test_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    for (boolean verified : List.of(false, true)) {
      evidence(f, id, verified, !verified);
      f.error(
          "local_test_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    }
    evidence(f, id, true, true);
    Path changed = temp.resolve("changed.csv");
    Json.write(changed, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", changed.toString());
    f.error("approval_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    f.approveAll(id);
    f.error(
        "local_test_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
  }

  @Test
  void credentialBearingRemoteIsNeverReturned() throws Exception {
    var f = fixture("https://user:SECRET@github.com/fixture/repo.git");
    String id = f.metadataOnly();
    var error =
        assertThrows(
            Problem.class, () -> f.call("repository_plan", "run_id", id, "environment", "local"));
    assertEquals("github_remote_invalid", error.code);
    assertFalse(Json.text(error.result()).contains("SECRET"));
  }

  @Test
  void dataOnlyDoesNotRequirePrOrLocalEvidence() throws Exception {
    var f = fixture("https://github.com/fixture/repo.git");
    String id = f.topic(false, "Jahr\n2025\n");
    assertEquals(false, f.call("prepare_pr", "run_id", id, "environment", "local").get("required"));
  }

  @Test
  void standaloneWorkflowsRequireTheirGateButNoDelivery() throws Exception {
    var f = fixture("https://github.com/fixture/repo.git");
    for (String workflow : List.of("model", "organization")) {
      String id = f.metadataOnly();
      stage(f, id);
      f.workflow.store.edit(
          id,
          r -> {
            r.put("workflow", workflow);
            if (workflow.equals("model"))
              Json.obj(r.get("checks"))
                  .put(
                      "model",
                      Json.map(
                          "manifest", CheckStates.manifest(f.workflow, r, "model"), "valid", true));
            Json.obj(r.get("approvals"))
                .put(
                    workflow,
                    Json.map(
                        "fingerprint",
                        f.workflow.fingerprint(r, workflow),
                        "human_statement",
                        "SYNTHETIC ISOLATED FIXTURE"));
            return null;
          });
      assertEquals(
          true, f.call("repository_plan", "run_id", id, "environment", "local").get("ready"));
      f.workflow.github =
          () -> {
            throw new Problem(
                "fixture_pr_ready", "Reached PR boundary without publication approval");
          };
      f.error("fixture_pr_ready", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    }
  }
}
