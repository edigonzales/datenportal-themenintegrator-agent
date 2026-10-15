package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real local Git; synthetic human approvals and GitHub responses, no network or publication. */
class OfficeRepositoryTest {
  @TempDir Path temp;
  final ProcessRunner system = new ProcessRunner();

  String git(Path cwd, String... args) {
    var command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    return system.checked(command, cwd, 60);
  }

  @Test
  void officePrAndHumanMergeSyncWithoutDelivery() throws Exception {
    Path remote = temp.resolve("remote.git");
    boolean[] interruptSync = {false};
    var process =
        new Fixtures.FakeProcess() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (args.equals(List.of("git", "remote", "get-url", "origin")))
              return "https://github.com/fixture/repo.git";
            return system.checked(args, cwd, timeout);
          }

          @Override
          public Result run(List<String> args, Path cwd, int timeout, Path log) {
            if (interruptSync[0] && args.contains("--ff-only"))
              throw new AssertionError(
                  "SYNTHETIC process interruption after restoring tracked bytes");
            if (args.getFirst().equals("fixture-validator"))
              return super.run(args, cwd, timeout, log);
            return system.run(args, cwd, timeout, log);
          }

          @Override
          public Result run(
              List<String> args, Path cwd, int timeout, Path log, Map<String, String> environment) {
            var local = new ArrayList<>(args);
            int index = local.indexOf("https://github.com/fixture/repo.git");
            if (index >= 0) local.set(index, remote.toString());
            var result = system.run(local, cwd, timeout, log);
            if (args.contains("clone") && result.exitCode() == 0) {
              git(Path.of(args.getLast()), "config", "user.name", "Synthetic fixture");
              git(Path.of(args.getLast()), "config", "user.email", "fixture@example.org");
            }
            return result;
          }
        };
    var f =
        new Fixtures(
            temp,
            process,
            (n, a) -> {
              throw new AssertionError("No datasheet");
            },
            (n, a) -> {
              throw new AssertionError("No model MCP");
            });
    f.workflow.jenkins =
        (n, e) -> {
          throw new AssertionError("No Jenkins");
        };
    git(f.repo, "init", "--initial-branch=main");
    git(f.repo, "config", "user.name", "Synthetic fixture");
    git(f.repo, "config", "user.email", "fixture@example.org");
    git(f.repo, "add", ".");
    git(f.repo, "commit", "-m", "Synthetic base");
    git(temp, "init", "--bare", remote.toString());
    git(f.repo, "remote", "add", "origin", remote.toString());
    git(f.repo, "push", "-u", "origin", "main");
    String[] head = {""};
    boolean[] merged = {false};
    int[] posts = {0};
    f.workflow.github =
        () ->
            new GitHub(java.net.URI.create("http://127.0.0.1"), "SYNTHETIC", 1) {
              @Override
              Map<String, Object> create(
                  Repo repo, String base, String branch, String title, String body) {
                posts[0]++;
                assertEquals("Datenportal: Office-Katalog", title);
                return Json.map("html_url", "https://github.com/fixture/repo/pull/1");
              }

              @Override
              Map<String, Object> get(Repo repo, long number) {
                return Json.map(
                    "merged",
                    merged[0],
                    "base",
                    Json.map("ref", "main"),
                    "head",
                    Json.map("sha", head[0]));
              }
            };
    String id = Json.required(f.call("start", "workflow", "office"), "id");
    f.call("office", "run_id", id, "mode", "create", "values", OfficesTest.bfs());
    assertEquals(
        false, f.call("repository_plan", "run_id", id, "environment", "local").get("ready"));
    f.error("approval_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    f.approve(id, "office", f.call("validate", "run_id", id));
    assertEquals(
        true, f.call("repository_plan", "run_id", id, "environment", "local").get("ready"));
    f.call("apply_local_changes", "run_id", id);
    var pr = f.call("prepare_pr", "run_id", id, "environment", "local");
    assertEquals(pr, f.call("prepare_pr", "run_id", id, "environment", "local"));
    assertEquals(1, posts[0]);
    head[0] = Json.required(pr, "head");
    Path clone = Path.of(Json.required(pr, "checkout"));
    assertEquals(
        Offices.PATH, git(clone, "diff-tree", "--no-commit-id", "--name-only", "-r", head[0]));
    f.error(
        "human_merge_required",
        () -> f.call("sync_repository", "run_id", id, "environment", "local"));
    git(clone, "switch", "main");
    git(clone, "merge", "--no-ff", Json.required(pr, "branch"), "-m", "Synthetic human merge");
    git(clone, "push", "origin", "main");
    merged[0] = true;
    interruptSync[0] = true;
    assertThrows(
        AssertionError.class,
        () -> f.call("sync_repository", "run_id", id, "environment", "local"));
    interruptSync[0] = false;
    assertEquals(
        "complete", f.call("sync_repository", "run_id", id, "environment", "local").get("phase"));
    assertTrue(git(f.repo, "status", "--porcelain").isBlank());
    assertTrue(Json.contents(f.repo.resolve(Offices.PATH)).contains("ch.bfs"));
    var status = f.call("status", "run_id", id);
    assertEquals("office_updated", Json.obj(status.get("completion")).get("status"));
    assertTrue(Json.obj(status.get("deliveries")).isEmpty());
    assertEquals(Set.of("office"), Json.obj(status.get("approvals")).keySet());
    assertEquals(
        "complete", f.call("sync_repository", "run_id", id, "environment", "local").get("phase"));
  }
}
