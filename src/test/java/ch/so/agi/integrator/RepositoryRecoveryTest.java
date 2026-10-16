package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real isolated Git, synthetic approvals and GitHub. Never contacts a publication service. */
class RepositoryRecoveryTest {
  @TempDir Path temp;
  final ProcessRunner system = new ProcessRunner();
  Fixtures f;
  Path remote;
  String id;
  String interrupt = "";
  boolean after;
  int posts, pushes;
  String prHead;
  final String rel = "agi/ch.so.grundwasser.qualitaet/dataset.gradle";

  String git(Path cwd, String... args) {
    var command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    return system.checked(command, cwd, 60);
  }

  @BeforeEach
  void setup() throws Exception {
    remote = temp.resolve("remote.git");
    var process =
        new Fixtures.FakeProcess() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (args.equals(List.of("git", "remote", "get-url", "origin")))
              return "https://github.com/fixture/repo.git";
            boolean stop = !interrupt.isEmpty() && args.contains(interrupt);
            if (stop && !after) {
              interrupt = "";
              throw new AssertionError("SYNTHETIC interruption");
            }
            String result = system.checked(args, cwd, timeout);
            if (stop) {
              interrupt = "";
              throw new AssertionError("SYNTHETIC interruption");
            }
            return result;
          }

          @Override
          public Result run(List<String> args, Path cwd, int timeout, Path log) {
            if (args.getFirst().equals("fixture-validator"))
              return super.run(args, cwd, timeout, log);
            return system.run(args, cwd, timeout, log);
          }

          @Override
          public Result run(
              List<String> args, Path cwd, int timeout, Path log, Map<String, String> env) {
            var local = new ArrayList<>(args);
            int index = local.indexOf("https://github.com/fixture/repo.git");
            if (index >= 0) local.set(index, remote.toString());
            boolean stop = !interrupt.isEmpty() && args.contains(interrupt);
            if (stop && !after) {
              interrupt = "";
              throw new AssertionError("SYNTHETIC interruption");
            }
            var result = system.run(local, cwd, timeout, log);
            if (args.contains("clone") && result.exitCode() == 0) {
              git(Path.of(args.getLast()), "config", "user.name", "Synthetic fixture");
              git(Path.of(args.getLast()), "config", "user.email", "fixture@example.org");
            }
            if (args.contains("push")) pushes++;
            if (stop) {
              interrupt = "";
              throw new AssertionError("SYNTHETIC interruption");
            }
            return result;
          }
        };
    f = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    f.workflow.jenkins =
        (n, e) -> {
          throw new AssertionError("No delivery");
        };
    git(f.repo, "init", "--initial-branch=main");
    git(f.repo, "config", "user.name", "Synthetic fixture");
    git(f.repo, "config", "user.email", "fixture@example.org");
    git(f.repo, "add", ".");
    git(f.repo, "commit", "-m", "Synthetic base");
    git(temp, "init", "--bare", remote.toString());
    git(f.repo, "remote", "add", "origin", remote.toString());
    git(f.repo, "push", "-u", "origin", "main");
    f.workflow.github =
        () ->
            new GitHub(java.net.URI.create("http://127.0.0.1"), "SYNTHETIC", 1) {
              @Override
              Map<String, Object> create(
                  Repo repo, String base, String branch, String title, String body) {
                posts++;
                prHead = git(remote, "rev-parse", "refs/heads/" + branch);
                if (interrupt.equals("post")) {
                  interrupt = "";
                  throw new AssertionError("SYNTHETIC lost POST response");
                }
                return Json.map("html_url", "https://github.com/fixture/repo/pull/1");
              }

              @Override
              List<Map<String, Object>> find(Repo repo, String branch, String base) {
                return prHead == null
                    ? List.of()
                    : List.of(
                        Json.map(
                            "html_url",
                            "https://github.com/fixture/repo/pull/1",
                            "head",
                            Json.map("sha", prHead)));
              }
            };
    id = f.metadataOnly();
  }

  void stage(String path, String contents) {
    Path source =
        temp.resolve("candidate-" + UUID.randomUUID() + "-" + Path.of(path).getFileName());
    Json.write(source, contents);
    f.call("stage_change", "run_id", id, "relative_path", path, "source_path", source.toString());
  }

  void ready() {
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map("verified", true, "fingerprint", f.workflow.fingerprint(r, "metadata")));
          return null;
        });
  }

  Map<String, Object> plan() {
    return f.call("repository_plan", "run_id", id, "environment", "local");
  }

  Map<String, Object> prepare() {
    return f.call("prepare_pr", "run_id", id, "environment", "local");
  }

  Path checkout() {
    return f.workflow.store.directory(id).resolve("pr-checkout");
  }

  @Test
  void untrackedLegacyTopicAndPartialCheckoutAreRecoveredWithoutDelivery() throws Exception {
    Json.write(f.repo.resolve(rel), "// locally repaired\n");
    stage(rel, "// locally repaired\n");
    String model = "agi/ch.so.grundwasser.qualitaet/Example.ili";
    stage(model, "INTERLIS 2.3;\n");
    String meta = "agi/ch.so.grundwasser.qualitaet/meta.xtf";
    Json.write(f.repo.resolve(meta), Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")));
    stage(
        meta,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace("Wasserqualität", "Geprüfte Wasserqualität"));
    String metadataCandidate =
        f.workflow.store.read(
            id, r -> Json.required(Json.obj(Json.obj(r.get("changes")).get(meta)), "path"));
    f.call("attach", "run_id", id, "role", "metadata", "path", metadataCandidate);
    ready();
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("git_bases");
          return null;
        });
    git(temp, "clone", remote.toString(), checkout().toString());
    git(checkout(), "switch", "-c", "codex/themenintegration-" + id.substring(0, 12));
    Json.write(checkout().resolve(model), "INTERLIS 2.3;\n");
    String before = Json.contents(f.workflow.store.directory(id).resolve("state.json"));
    var plan = plan();
    assertEquals(true, plan.get("ready"), Json.pretty(plan));
    assertEquals(before, Json.contents(f.workflow.store.directory(id).resolve("state.json")));
    assertEquals(3, Json.list(plan.get("file_comparisons")).size());
    var pr = prepare();
    assertEquals(pr, prepare());
    assertEquals(1, posts);
    assertEquals(1, pushes);
    assertEquals("// locally repaired\n", Json.contents(f.repo.resolve(rel)));
    try (var files = Files.list(checkout().getParent())) {
      assertEquals(
          1,
          files.filter(p -> p.getFileName().toString().startsWith("pr-checkout-archive-")).count());
    }
    assertEquals(3, git(checkout(), "diff", "--name-only", "HEAD^", "HEAD").lines().count());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"commit-before", "commit-after", "push-before", "push-after", "post-after"})
  void interruptionsResumeExactlyOnce(String point) {
    stage(rel, "// candidate\n");
    ready();
    interrupt = point.split("-")[0];
    after = point.endsWith("after");
    assertThrows(AssertionError.class, this::prepare);
    var pr = prepare();
    assertEquals(pr, prepare());
    assertEquals(1, posts);
    assertEquals(1, pushes);
  }

  @Test
  void remoteChangesAfterPlanBlockBeforeCopying() {
    stage(rel, "// candidate\n");
    ready();
    assertEquals(true, plan().get("ready"));
    Json.write(f.repo.resolve(rel), "// other contributor\n");
    git(f.repo, "add", rel);
    git(f.repo, "commit", "-m", "Other contributor");
    git(f.repo, "push", "origin", "main");
    // Restore only the test working copy to the original preimage, leaving remote conflict intact.
    try {
      Files.delete(f.repo.resolve(rel));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    f.error("pr_base_conflict", this::prepare);
    assertFalse(Files.exists(checkout()));
    assertEquals(0, posts);
  }

  @Test
  void foreignCheckoutContentIsPreservedAndBlocksPlan() throws Exception {
    stage(rel, "// candidate\n");
    ready();
    interrupt = "commit";
    after = false;
    assertThrows(AssertionError.class, this::prepare);
    Path foreign = checkout().resolve("manual.txt");
    Json.write(foreign, "keep me");
    assertEquals(false, plan().get("ready"));
    f.error("pr_checkout_conflict", this::prepare);
    assertEquals("keep me", Json.contents(foreign));
  }

  @Test
  void manualLocalChangeAndRemovalRemainDistinct() throws Exception {
    Json.write(f.repo.resolve(rel), "// base\n");
    stage(rel, "// candidate\n");
    ready();
    var r = f.workflow.store.read(id, x -> x);
    var repository = new Repository(f.workflow);
    var checks = new RepositoryChecks(repository);
    Json.write(f.repo.resolve(rel), "// manual\n");
    f.error(
        "local_changes_modified",
        () -> checks.inspect(r, f.repo, new GitHub.Repo("fixture", "repo"), "main"));
    Files.delete(f.repo.resolve(rel));
    f.error(
        "local_changes_missing",
        () -> checks.inspect(r, f.repo, new GitHub.Repo("fixture", "repo"), "main"));
  }

  @Test
  void unchangedTargetNeedsNoPrAndBasisSurvivesRestaging() {
    Json.write(f.repo.resolve(rel), "// base\n");
    git(f.repo, "add", rel);
    git(f.repo, "commit", "-m", "Tracked base");
    git(f.repo, "push", "origin", "main");
    stage(rel, "// base\n");
    var basis = f.workflow.store.read(id, r -> Json.obj(r.get("git_bases")).get(rel));
    stage(rel, "// base\n");
    assertEquals(basis, f.workflow.store.read(id, r -> Json.obj(r.get("git_bases")).get(rel)));
    ready();
    assertEquals(false, prepare().get("required"));
    assertEquals(0, posts);
    assertEquals(0, pushes);
  }

  @Test
  void trackedRemoteDeletionIsNotAFileAddition() {
    Json.write(f.repo.resolve(rel), "// tracked\n");
    git(f.repo, "add", rel);
    git(f.repo, "commit", "-m", "Tracked base");
    git(f.repo, "push", "origin", "main");
    stage(rel, "// candidate\n");
    ready();
    git(f.repo, "rm", rel);
    git(f.repo, "commit", "-m", "Remote deletion");
    git(f.repo, "push", "origin", "main");
    Json.write(f.repo.resolve(rel), "// tracked\n");
    f.error("pr_base_conflict", this::prepare);
    assertEquals(0, posts);
  }

  @Test
  void missingHistoricalBasisBlocksInsteadOfGuessing() {
    stage(rel, "// candidate\n");
    ready();
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("git_bases");
          return null;
        });
    Path other = temp.resolve("other");
    git(temp, "clone", remote.toString(), other.toString());
    git(other, "config", "user.name", "Synthetic fixture");
    git(other, "config", "user.email", "fixture@example.org");
    Json.write(other.resolve("unrelated.txt"), "remote work");
    git(other, "add", ".");
    git(other, "commit", "-m", "Advance remote");
    git(other, "push", "origin", "main");
    f.error("pr_basis_unknown", this::prepare);
    assertFalse(Files.exists(checkout()));
  }

  @Test
  void lostPostBeforeAcceptanceNeverBlindlyRepeats() {
    stage(rel, "// candidate\n");
    ready();
    f.workflow.github =
        () ->
            new GitHub(java.net.URI.create("http://127.0.0.1"), "SYNTHETIC", 1) {
              @Override
              Map<String, Object> create(
                  Repo repo, String base, String branch, String title, String body) {
                posts++;
                throw new AssertionError("SYNTHETIC ambiguous request");
              }

              @Override
              List<Map<String, Object>> find(Repo repo, String branch, String base) {
                return List.of();
              }
            };
    assertThrows(AssertionError.class, this::prepare);
    f.error("github_pr_ambiguous", this::prepare);
    assertEquals(1, posts);
  }

  @Test
  void gitControlFlagsDoNotRedactCommitDigitsButSecretsRemainRedacted() {
    String commit = "0123456789abcdef0123456789abcdef01234567";
    var env = new HashMap<String, String>();
    env.put("GIT_TERMINAL_PROMPT", "0");
    env.put("GIT_CONFIG_COUNT", "2");
    env.put("TOKEN", "synthetic-secret");
    assertEquals(commit + " [redacted]", ProcessRunner.redact(commit + " synthetic-secret", env));
  }

  @Test
  void legacyTrackedPreimageCannotBeReplacedByANewerGitBase() {
    Json.write(f.repo.resolve(rel), "// old tracked\n");
    git(f.repo, "add", rel);
    git(f.repo, "commit", "-m", "Old base");
    git(f.repo, "push", "origin", "main");
    stage(rel, "// candidate\n");
    ready();
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("git_bases");
          return null;
        });
    Json.write(f.repo.resolve(rel), "// new upstream\n");
    git(f.repo, "add", rel);
    git(f.repo, "commit", "-m", "New upstream");
    git(f.repo, "push", "origin", "main");
    Json.write(f.repo.resolve(rel), "// old tracked\n");
    f.error("pr_basis_unknown", this::prepare);
  }
}
