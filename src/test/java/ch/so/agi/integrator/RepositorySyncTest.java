package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RepositorySyncTest {
  @TempDir Path temp;
  Fixtures f;
  Path remote, writer;
  Map<String, Object> run, env;
  String target, old;
  boolean failMerge, crashMerge, crashAfterMerge;
  final ProcessRunner system = new ProcessRunner();

  String git(Path cwd, String... args) {
    var cmd = new ArrayList<>(List.of("git"));
    cmd.addAll(List.of(args));
    return system.checked(cmd, cwd, 60);
  }

  @BeforeEach
  void setup() throws Exception {
    ProcessRunner process =
        new ProcessRunner() {
          @Override
          public Result run(
              List<String> args, Path cwd, int timeout, Path log, Map<String, String> environment) {
            if (cwd.equals(f == null ? null : f.repo) && args.contains("--ff-only")) {
              if (crashMerge) throw new AssertionError("synthetic process crash before merge");
              if (failMerge) return new Result(1, "synthetic failure");
              var result = system.run(args, cwd, timeout, log, environment);
              if (crashAfterMerge) throw new AssertionError("synthetic process crash after merge");
              return result;
            }
            return system.run(args, cwd, timeout, log, environment);
          }
        };
    f = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    git(f.repo, "init", "--initial-branch=main");
    git(f.repo, "config", "user.name", "Synthetic fixture");
    git(f.repo, "config", "user.email", "fixture@example.org");
    Json.write(f.repo.resolve("sheet.xtf"), "old\n");
    Json.write(f.repo.resolve("foreign.txt"), "original\n");
    git(f.repo, "add", ".");
    git(f.repo, "commit", "-m", "synthetic base");
    old = git(f.repo, "rev-parse", "HEAD");
    remote = temp.resolve("remote.git");
    writer = temp.resolve("writer");
    git(temp, "init", "--bare", "--initial-branch=main", remote.toString());
    git(f.repo, "remote", "add", "origin", remote.toString());
    git(f.repo, "push", "origin", "main");
    git(temp, "clone", "--branch", "main", remote.toString(), writer.toString());
    git(writer, "config", "user.name", "Synthetic fixture");
    git(writer, "config", "user.email", "fixture@example.org");
    Json.write(writer.resolve("sheet.xtf"), "approved\n");
    Json.write(writer.resolve("model.ili"), "approved model\n");
    git(writer, "add", ".");
    git(writer, "commit", "-m", "synthetic reviewed target");
    git(writer, "push", "origin", "main");
    target = git(writer, "rev-parse", "HEAD");
    run = f.workflow.store.create("topic", "agi", "ch.so.fixture", null, "local");
    var changes = Json.map();
    var hashes = Json.map();
    for (String rel : List.of("sheet.xtf", "model.ili")) {
      Files.copy(writer.resolve(rel), f.repo.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
      String sha = Json.sha(writer.resolve(rel));
      changes.put(rel, Json.map("sha256", sha));
      hashes.put(rel, sha);
    }
    run.put("changes", changes);
    run.put("applied", Json.map("hashes", hashes));
    run.put("pull_request", Json.map("required", false, "reason", "already_on_target"));
    f.workflow.persist(run);
    env = Json.map("git_branch", "main", "git_remote", "origin");
  }

  Object sync() {
    return new RepositorySync(f.workflow).sync(run, env);
  }

  void blocked() {
    assertThrows(Problem.class, this::sync);
    assertEquals(old, git(f.repo, "rev-parse", "HEAD"));
  }

  @Test
  void blocksIntentToAddAsIndependentIndexChange() {
    git(f.repo, "add", "--intent-to-add", "model.ili");
    blocked();
    assertFalse(git(f.repo, "ls-files", "--stage", "--", "model.ili").isBlank());
  }

  @Test
  void preservesUnusualForeignNamesAndResumesEmptyBase() throws Exception {
    String name = " leading\nname.txt";
    Json.write(f.repo.resolve(name), "foreign\n");
    // An empty approved file exercises a valid empty Base64 backup during rollback.
    Json.write(writer.resolve("model.ili"), "");
    git(writer, "add", ".");
    git(writer, "commit", "-m", "empty synthetic model");
    git(writer, "push", "origin", "main");
    target = git(writer, "rev-parse", "HEAD");
    Json.write(f.repo.resolve("model.ili"), "");
    String sha = Json.sha(f.repo.resolve("model.ili"));
    Json.obj(Json.obj(run.get("changes")).get("model.ili")).put("sha256", sha);
    Json.obj(Json.obj(run.get("applied")).get("hashes")).put("model.ili", sha);
    failMerge = true;
    blocked();
    assertEquals("", Files.readString(f.repo.resolve("model.ili")));
    failMerge = false;
    sync();
    assertEquals("foreign\n", Files.readString(f.repo.resolve(name)));
  }

  @Test
  void rejectsIgnoredCollisionsAndHiddenIndexChanges() throws Exception {
    Json.write(f.repo.resolve(".git/info/exclude"), "blocked-dir\n");
    Json.write(f.repo.resolve("blocked-dir/local.txt"), "ignored local\n");
    Json.write(writer.resolve("blocked-dir"), "remote file\n");
    git(writer, "add", ".");
    git(writer, "commit", "-m", "path collision");
    git(writer, "push", "origin", "main");
    blocked();
    assertEquals("ignored local\n", Files.readString(f.repo.resolve("blocked-dir/local.txt")));
    Files.delete(f.repo.resolve("blocked-dir/local.txt"));
    Files.delete(f.repo.resolve("blocked-dir"));
    git(f.repo, "update-index", "--assume-unchanged", "sheet.xtf");
    blocked();
    assertEquals("approved\n", Files.readString(f.repo.resolve("sheet.xtf")));
  }

  @Test
  void preservesForeignIndexWorktreeAndUntrackedIncludingCancellation() throws Exception {
    Json.write(f.repo.resolve("foreign.txt"), "staged\n");
    git(f.repo, "add", "foreign.txt");
    Json.write(f.repo.resolve("foreign.txt"), "original\n"); // worktree equals HEAD, index does not
    Json.write(f.repo.resolve("new foreign.txt"), "untracked\n");
    sync();
    assertEquals(target, git(f.repo, "rev-parse", "HEAD"));
    assertEquals("staged", git(f.repo, "show", ":foreign.txt"));
    assertEquals("original\n", Files.readString(f.repo.resolve("foreign.txt")));
    assertEquals("untracked\n", Files.readString(f.repo.resolve("new foreign.txt")));
    assertTrue(git(f.repo, "diff", "HEAD", "--", "sheet.xtf", "model.ili").isBlank());
    sync();
    assertEquals("complete", Json.obj(run.get("repository_sync")).get("phase"));
  }

  @Test
  void rejectsChangedOrUnprovenOrStagedOwnFiles() throws Exception {
    run.remove("applied");
    blocked();
    run.put(
        "applied",
        Json.map(
            "hashes",
            Json.map(
                "sheet.xtf",
                Json.sha(f.repo.resolve("sheet.xtf")),
                "model.ili",
                Json.sha(f.repo.resolve("model.ili")))));
    Json.write(f.repo.resolve("sheet.xtf"), "human edit\n");
    blocked();
    Files.copy(
        writer.resolve("sheet.xtf"),
        f.repo.resolve("sheet.xtf"),
        StandardCopyOption.REPLACE_EXISTING);
    git(f.repo, "add", "sheet.xtf");
    blocked();
    assertEquals("approved", git(f.repo, "show", ":sheet.xtf"));
  }

  @Test
  void blocksOverlapAndWrongBranchAndDivergence() throws Exception {
    Json.write(writer.resolve("foreign.txt"), "remote edit\n");
    git(writer, "add", ".");
    git(writer, "commit", "-m", "remote");
    git(writer, "push", "origin", "main");
    Json.write(f.repo.resolve("foreign.txt"), "human edit\n");
    blocked();
    git(f.repo, "restore", "foreign.txt");
    git(f.repo, "switch", "-c", "human");
    blocked();
    git(f.repo, "switch", "main");
    Json.write(f.repo.resolve("foreign.txt"), "human commit\n");
    git(f.repo, "add", "foreign.txt");
    git(f.repo, "commit", "-m", "divergence");
    assertThrows(Problem.class, this::sync);
  }

  @Test
  void rollsBackFailedFastForward() throws Exception {
    failMerge = true;
    blocked();
    assertEquals("approved\n", Files.readString(f.repo.resolve("sheet.xtf")));
    assertTrue(Files.exists(f.repo.resolve("model.ili")));
    assertFalse(Files.exists(RepositorySync.control(f.workflow).resolve("datenportal-sync.json")));
    failMerge = false;
    sync();
  }

  @Test
  void resumesCrashesBeforeAndAfterFastForward() throws Exception {
    crashMerge = true;
    assertThrows(AssertionError.class, this::sync);
    assertEquals("old\n", Files.readString(f.repo.resolve("sheet.xtf")));
    assertFalse(Files.exists(f.repo.resolve("model.ili")));
    assertThrows(Problem.class, () -> RepositorySync.requireNoPending(f.workflow));
    crashMerge = false;
    crashAfterMerge = true;
    assertThrows(AssertionError.class, this::sync);
    assertEquals(target, git(f.repo, "rev-parse", "HEAD"));
    crashAfterMerge = false;
    sync();
    assertFalse(Files.exists(RepositorySync.control(f.workflow).resolve("datenportal-sync.json")));
  }

  @Test
  void doesNotOverwriteEditsAfterCrash() throws Exception {
    crashMerge = true;
    assertThrows(AssertionError.class, this::sync);
    crashMerge = false;
    Json.write(f.repo.resolve("sheet.xtf"), "edit after crash\n");
    blocked();
    assertEquals("edit after crash\n", Files.readString(f.repo.resolve("sheet.xtf")));
    assertTrue(Files.exists(RepositorySync.control(f.workflow).resolve("datenportal-sync.json")));
  }

  @Test
  void rejectsConcurrentRunAndSupportsCleanCheckoutWithoutApplied() throws Exception {
    try (var lock = RepositorySync.lock(f.workflow)) {
      assertEquals("repository_locked", assertThrows(Problem.class, this::sync).code);
    }
    git(f.repo, "restore", "sheet.xtf");
    Files.delete(f.repo.resolve("model.ili"));
    run.remove("applied");
    sync();
    assertEquals(target, git(f.repo, "rev-parse", "HEAD"));
  }

  @Test
  void changedRemoteBytesBlockWithoutCleanup() throws Exception {
    Json.write(writer.resolve("sheet.xtf"), "different\n");
    git(writer, "add", ".");
    git(writer, "commit", "-m", "changed target");
    git(writer, "push", "origin", "main");
    assertEquals("merged_content_changed", assertThrows(Problem.class, this::sync).code);
    assertEquals("approved\n", Files.readString(f.repo.resolve("sheet.xtf")));
  }
}
