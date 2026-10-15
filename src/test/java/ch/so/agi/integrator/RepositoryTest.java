package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryTest {
  @TempDir Path temp;

  @ParameterizedTest
  @ValueSource(strings = {"merge", "squash", "rebase"})
  void isolatedPrExactMergeAndConflicts(String mode) throws Exception {
    var system = new ProcessRunner();
    String[] state = {"OPEN"}, head = {""};
    ProcessRunner process =
        new ProcessRunner() {
          @Override
          public Result run(List<String> args, Path cwd, int timeout, Path log) {
            if (args.getFirst().equals("fixture-validator")) {
              if (log != null) Json.write(log, "FIXTURE validation successful\n");
              return new Result(0, "FIXTURE validation successful\n");
            }
            return system.run(args, cwd, timeout, log);
          }

          @Override
          public Result run(
              List<String> args, Path cwd, int timeout, Path log, Map<String, String> environment) {
            var local = new ArrayList<>(args);
            int remoteIndex = local.indexOf("https://github.com/fixture/repo.git");
            if (remoteIndex >= 0) local.set(remoteIndex, temp.resolve("remote.git").toString());
            var result = system.run(local, cwd, timeout, log);
            if (args.contains("clone") && result.exitCode() == 0) {
              Path clone = Path.of(args.getLast());
              system.checked(
                  List.of("git", "config", "user.name", "Integration Test Fixture"), clone, 60);
              system.checked(
                  List.of("git", "config", "user.email", "fixture@example.org"), clone, 60);
            }
            return result;
          }

          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (args.equals(List.of("git", "remote", "get-url", "origin")))
              return "https://github.com/fixture/repo.git";
            String result = system.checked(args, cwd, timeout);

            if (args.contains("clone")) {
              Path clone = Path.of(args.getLast());
              system.checked(
                  List.of("git", "config", "user.name", "Integration Test Fixture"), clone, 60);
              system.checked(
                  List.of("git", "config", "user.email", "fixture@example.org"), clone, 60);
            }
            return result;
          }
        };
    var f = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    f.workflow.github =
        () ->
            new GitHub(java.net.URI.create("http://127.0.0.1"), "SYNTHETIC", 1) {
              @Override
              Map<String, Object> create(
                  Repo repo, String base, String branch, String title, String body) {
                return Json.map("html_url", "https://github.com/fixture/repo/pull/1");
              }

              @Override
              Map<String, Object> get(Repo repo, long number) {
                return Json.map(
                    "merged",
                    "MERGED".equals(state[0]),
                    "base",
                    Json.map("ref", "main"),
                    "head",
                    Json.map("sha", head[0]));
              }
            };
    String rel = "agi/ch.so.grundwasser.qualitaet/datasheet.xtf";
    Json.write(f.repo.resolve(rel), Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")));
    system.checked(List.of("git", "init", "--initial-branch=main"), f.repo, 60);
    system.checked(List.of("git", "config", "user.name", "Integration Test Fixture"), f.repo, 60);
    system.checked(List.of("git", "config", "user.email", "fixture@example.org"), f.repo, 60);
    system.checked(List.of("git", "add", "."), f.repo, 60);
    system.checked(List.of("git", "commit", "-m", "Isolated synthetic fixture"), f.repo, 60);
    Path remote = temp.resolve("remote.git");
    system.checked(List.of("git", "init", "--bare", remote.toString()), temp, 60);
    system.checked(List.of("git", "remote", "add", "origin", remote.toString()), f.repo, 60);
    system.checked(List.of("git", "push", "-u", "origin", "main"), f.repo, 60);
    var env =
        Json.map(
            "kind",
            "int",
            "enabled",
            true,
            "repository_mode",
            "managed-git",
            "jenkins_url",
            "http://127.0.0.1:8081/jenkins",
            "portal_url",
            "http://127.0.0.1:8081",
            "manifest_url",
            "http://127.0.0.1:8081/current.json",
            "git_remote",
            "origin",
            "git_branch",
            "main");
    Json.obj(f.settings.values.get("environments")).put("int", env);
    var prod = new LinkedHashMap<>(env);
    prod.put("kind", "prod");
    Json.obj(f.settings.values.get("environments")).put("prod", prod);
    var deliveryServer = new JenkinsTest();
    deliveryServer.setup();
    try {
      Json.obj(f.settings.values.get("environments")).put("local", deliveryServer.env);
      f.workflow.jenkins = (name, ignored) -> deliveryServer.client();
      String id = f.metadataOnly();

      Path candidate = temp.resolve("candidate.xtf");
      Json.write(
          candidate,
          Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
              .replace("Wasserqualität Grundwasser", "Revidierte Wasserqualität Grundwasser"));
      f.call("attach", "run_id", id, "role", "metadata", "path", candidate.toString());
      f.call(
          "stage_change", "run_id", id, "relative_path", rel, "source_path", candidate.toString());
      f.approveAll(id);
      // Resume a synthetic seed; exercise upload and publication verification over loopback HTTP.
      deliveryServer.metadata = Json.contents(candidate);
      f.call("apply_local_changes", "run_id", id);
      f.workflow.store.edit(
          id,
          r -> {
            Json.obj(r.get("deliveries"))
                .put(
                    "local",
                    Json.map(
                        "phase",
                        "seeding",
                        "fingerprint",
                        f.workflow.fingerprint(r, "metadata"),
                        "seed",
                        Json.map("queue_url", deliveryServer.base + "/jenkins/queue/item/seed/")));
            return null;
          });
      assertEquals("running", f.call("deliver", "run_id", id, "environment", "local").get("phase"));
      assertEquals(
          "complete", f.call("deliver", "run_id", id, "environment", "local").get("phase"));
      assertEquals(1, deliveryServer.uploads);
      assertTrue(
          f.workflow.store.<Boolean>read(
              id,
              r ->
                  Json.obj(r.get("approvals")).keySet().stream()
                      .noneMatch(k -> k.startsWith("publish:"))));
      String before = Json.sha(f.repo.resolve(rel));
      var result = f.call("prepare_pr", "run_id", id, "environment", "local");
      assertEquals(result, f.call("prepare_pr", "run_id", id, "environment", "local"));
      assertEquals(before, Json.sha(f.repo.resolve(rel)));
      Path clone = Path.of(Json.required(result, "checkout"));
      head[0] = Json.required(result, "head");
      assertEquals(Json.sha(candidate), Json.sha(clone.resolve(rel)));
      f.workflow.store.edit(
          id,
          r -> {
            assertEquals(
                "human_merge_required",
                assertThrows(Problem.class, () -> new Repository(f.workflow).requireMerged(r, env))
                    .code);
            return null;
          });
      system.checked(List.of("git", "switch", "main"), clone, 60);
      if (mode.equals("merge")) {
        system.checked(
            List.of(
                "git",
                "merge",
                "--no-ff",
                Json.required(result, "branch"),
                "-m",
                "Synthetic human merge fixture"),
            clone,
            60);
      } else if (mode.equals("squash")) {
        system.checked(
            List.of("git", "merge", "--squash", Json.required(result, "branch")), clone, 60);
        system.checked(List.of("git", "commit", "-m", "Synthetic squash fixture"), clone, 60);
      } else {
        Json.write(clone.resolve("unrelated.txt"), "synthetic target advancement\n");
        system.checked(List.of("git", "add", "unrelated.txt"), clone, 60);
        system.checked(List.of("git", "commit", "-m", "Synthetic target advancement"), clone, 60);
        system.checked(List.of("git", "cherry-pick", head[0]), clone, 60);
      }
      system.checked(List.of("git", "push", "origin", "main"), clone, 60);
      state[0] = "MERGED";
      f.workflow.credentials =
          (name, ignored) -> new Credentials.Value("synthetic", "synthetic", "fixture");
      f.workflow.jenkins =
          (name, ignored) -> {
            throw new AssertionError("No external start before sync");
          };
      system.checked(List.of("git", "switch", "-c", "human-branch"), f.repo, 60);
      assertEquals(
          "approval_required",
          assertThrows(Problem.class, () -> f.call("seed", "run_id", id, "environment", "int"))
              .code);
      assertEquals(
          "approval_required",
          assertThrows(Problem.class, () -> f.call("deliver", "run_id", id, "environment", "int"))
              .code);
      f.error(
          "repository_sync_conflict",
          () -> f.call("sync_repository", "run_id", id, "environment", "local"));
      system.checked(List.of("git", "switch", "main"), f.repo, 60);

      f.call("sync_repository", "run_id", id, "environment", "local");
      for (String target : List.of("int", "prod")) {
        for (String operation : List.of("seed", "deliver")) {
          f.error(
              "approval_required", () -> f.call(operation, "run_id", id, "environment", target));
        }
      }
      f.workflow.store.edit(
          id,
          r -> {
            new RepositorySync(f.workflow).sync(r, env);
            assertEquals(Json.sha(candidate), Json.sha(f.repo.resolve(rel)));
            assertTrue(
                system.checked(List.of("git", "status", "--porcelain"), f.repo, 60).isBlank());
            return null;
          });
      head[0] = "changed-head";
      f.workflow.store.edit(
          id,
          r -> {
            assertEquals(
                "pr_changed",
                assertThrows(Problem.class, () -> new Repository(f.workflow).requireMerged(r, env))
                    .code);
            return null;
          });
      head[0] = Json.required(result, "head");
      Json.write(clone.resolve(rel), "changed after merge\n");
      system.checked(List.of("git", "add", rel), clone, 60);
      system.checked(List.of("git", "commit", "-m", "Synthetic conflicting follow-up"), clone, 60);
      system.checked(List.of("git", "push", "origin", "main"), clone, 60);
      f.workflow.store.edit(
          id,
          r -> {
            assertEquals(
                "merged_content_changed",
                assertThrows(Problem.class, () -> new Repository(f.workflow).requireMerged(r, env))
                    .code);
            return null;
          });
    } finally {
      deliveryServer.close();
    }
  }
}
