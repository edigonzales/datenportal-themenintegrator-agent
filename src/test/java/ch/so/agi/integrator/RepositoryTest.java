package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RepositoryTest {
  @TempDir Path temp;

  @Test
  void isolatedPrExactMergeAndConflicts() throws Exception {
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
    String id = f.metadataOnly();
    Path candidate = temp.resolve("candidate.xtf");
    Json.write(
        candidate,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace("Wasserqualität Grundwasser", "Revidierte Wasserqualität Grundwasser"));
    f.call("attach", "run_id", id, "role", "metadata", "path", candidate.toString());
    f.call("stage_change", "run_id", id, "relative_path", rel, "source_path", candidate.toString());
    f.approveAll(id);
    // Synthetic fixture injects an explicit reviewed plan without a production endpoint.
    f.workflow.store.edit(
        id,
        r -> {
          r.put(
              "publication_plans",
              Json.map("int", Json.map("environment", "int", "branch", "main")));
          Json.obj(r.get("approvals"))
              .put(
                  "publish:int",
                  Json.map(
                      "fingerprint",
                      f.workflow.fingerprint(r, "publish:int"),
                      "human_statement",
                      "AUTOMATISIERTE ISOLIERTE PR-TESTFIXTURE"));
          return null;
        });
    String before = Json.sha(f.repo.resolve(rel));
    var result = f.call("prepare_pr", "run_id", id, "environment", "int");
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
    system.checked(List.of("git", "push", "origin", "main"), clone, 60);
    state[0] = "MERGED";
    f.workflow.store.edit(
        id,
        r -> {
          new Repository(f.workflow).requireMerged(r, env);
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
  }
}
