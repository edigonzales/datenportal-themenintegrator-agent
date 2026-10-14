package ch.so.agi.integrator;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Journalled, fast-forward-only reconciliation of a reviewed run with its operator checkout. */
final class RepositorySync {
  private final Workflow w;
  private final Path repo;

  RepositorySync(Workflow w) {
    this.w = w;
    repo = w.settings.topics;
  }

  static Path control(Workflow w) {
    if (!Files.exists(w.settings.topics.resolve(".git"))) {
      try {
        return w.settings.topics.toRealPath();
      } catch (IOException e) {
        throw new Problem("repository_sync_conflict", "Repository nicht erreichbar.");
      }
    }
    var result =
        w.process.run(
            List.of("git", "rev-parse", "--git-common-dir"),
            w.settings.topics,
            w.settings.timeout,
            null);
    try {
      if (result.exitCode() == 0)
        return w.settings.topics.resolve(result.output().strip()).toRealPath();
      return w.settings.topics.toRealPath();
    } catch (IOException e) {
      throw new Problem("repository_sync_conflict", "Repository nicht erreichbar.");
    }
  }

  static final class Guard implements AutoCloseable {
    final FileChannel channel;
    final FileLock lock;

    Guard(Path path) {
      FileChannel opened = null;
      try {
        opened = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = opened.tryLock();
        if (acquired == null) throw new IOException("busy");
        channel = opened;
        lock = acquired;
      } catch (IOException | OverlappingFileLockException e) {
        if (opened != null)
          try {
            opened.close();
          } catch (IOException ignored) {
          }
        throw new Problem("repository_locked", "Themenrepository wird bereits bearbeitet.");
      }
    }

    public void close() {
      try {
        lock.release();
        channel.close();
      } catch (IOException e) {
        throw new Problem("repository_locked", "Repository-Sperre nicht freigegeben.");
      }
    }
  }

  static Guard lock(Workflow w) {
    return new Guard(control(w).resolve("datenportal-sync.lock"));
  }

  static void requireNoPending(Workflow w) {
    if (Files.exists(control(w).resolve("datenportal-sync.json")))
      throw new Problem(
          "repository_sync_pending",
          "Unterbrochene Repository-Synchronisierung zuerst fortsetzen.");
  }

  String git(String... args) {
    var command = new ArrayList<>(List.of("git", "--literal-pathspecs"));
    command.addAll(List.of(args));
    var result = w.process.run(command, repo, w.settings.timeout, null);
    check(result.exitCode() == 0, "Git-Schritt fehlgeschlagen: " + args[0]);
    return result.output();
  }

  Set<String> paths(String... args) {
    String output = git(args);
    var paths = new LinkedHashSet<String>();
    for (String p : output.split("\u0000")) if (!p.isEmpty()) paths.add(p);
    return paths;
  }

  void check(boolean ok, String message) {
    if (!ok) throw new Problem("repository_sync_conflict", message);
  }

  String identity() {
    try {
      return repo.toRealPath().toString();
    } catch (IOException e) {
      throw new Problem("repository_sync_conflict", "Repository nicht erreichbar.");
    }
  }

  Map<String, Object> file(String rel) {
    Path path = Json.inside(repo, rel);
    for (Path p = path; !p.equals(repo.toAbsolutePath().normalize()); p = p.getParent())
      check(p != null && !Files.isSymbolicLink(p), "Symbolischer Link im betroffenen Pfad: " + rel);
    if (!Files.exists(path)) return Json.map("exists", false);
    check(Files.isRegularFile(path), "Keine reguläre Datei: " + rel);
    try {
      return Json.map(
          "exists",
          true,
          "bytes",
          Base64.getEncoder().encodeToString(Files.readAllBytes(path)),
          "executable",
          Files.isExecutable(path));
    } catch (IOException e) {
      throw new Problem("repository_sync_conflict", "Datei nicht lesbar: " + rel);
    }
  }

  void write(String rel, Map<String, Object> snapshot) throws IOException {
    Path path = Json.inside(repo, rel);
    if (!Json.bool(snapshot, "exists", false)) Files.deleteIfExists(path);
    else {
      Files.createDirectories(path.getParent());
      Files.write(path, Base64.getDecoder().decode(Json.str(snapshot, "bytes", "")));
      check(
          path.toFile().setExecutable(Json.bool(snapshot, "executable", false), false),
          "Dateimodus nicht wiederhergestellt: " + rel);
    }
  }

  boolean overlaps(String a, String b) {
    return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/");
  }

  Map<String, Object> fingerprint(String rel) {
    var snapshot = file(rel);
    snapshot.remove("bytes");
    if (Json.bool(snapshot, "exists", false))
      snapshot.put("sha256", Json.sha(Json.inside(repo, rel)));
    return snapshot;
  }

  Map<String, Object> foreign(Set<String> own) {
    var dirty = paths("diff", "--no-renames", "--name-only", "-z", "--");
    dirty.addAll(
        paths(
            "diff",
            "--no-renames",
            "--cached",
            "--ita-visible-in-index",
            "--name-only",
            "-z",
            "--"));
    dirty.addAll(paths("ls-files", "--others", "--exclude-standard", "-z"));
    dirty.removeAll(own);
    var result = Json.map();
    for (String rel : dirty)
      result.put(
          rel,
          Json.map("file", fingerprint(rel), "index", git("ls-files", "--stage", "-z", "--", rel)));
    return result;
  }

  Map<String, Object> create(Map<String, Object> run, String target, String branch) {
    String head = git("rev-parse", "HEAD").strip();
    check(
        w.process
                .run(
                    List.of("git", "merge-base", "--is-ancestor", head, target),
                    repo,
                    w.settings.timeout,
                    null)
                .exitCode()
            == 0,
        "Lokaler Branch ist vom Zielstand abgewichen.");
    var updated = paths("diff", "--no-renames", "--name-only", "-z", head, target, "--");
    var staged =
        paths(
            "diff",
            "--no-renames",
            "--cached",
            "--ita-visible-in-index",
            "--name-only",
            "-z",
            "--");
    var dirty = paths("diff", "--no-renames", "--name-only", "-z", "--");
    dirty.addAll(paths("ls-files", "--others", "-z"));
    var own = Json.map();
    var changes = Json.obj(run.get("changes"));
    var applied = Json.obj(Json.obj(run.get("applied")).get("hashes"));
    for (String rel : changes.keySet()) {
      check(!staged.contains(rel), "Eigenständig gestagte Vorgangsdatei: " + rel);
      if (!dirty.contains(rel)) continue;
      String expected = Json.required(Json.obj(changes.get(rel)), "sha256");
      Map<String, Object> snapshot = file(rel);
      check(
          expected.equals(applied.get(rel))
              && Json.bool(snapshot, "exists", false)
              && expected.equals(Json.sha(Json.inside(repo, rel))),
          "Lokale Änderung ohne passenden Übernahmenachweis: " + rel);
      check(
          git("ls-tree", target, "--", rel)
              .startsWith(Json.bool(snapshot, "executable", false) ? "100755" : "100644"),
          "Lokaler Dateimodus weicht vom Ziel ab: " + rel);
      own.put(rel, snapshot);
    }
    dirty.addAll(staged);
    for (String rel : dirty) {
      if (own.containsKey(rel)) continue;
      for (String changed : updated)
        check(!overlaps(rel, changed), "Fremde lokale Änderung überschneidet Zielupdate: " + rel);
    }
    var bases = Json.map();
    for (String rel : own.keySet()) bases.put(rel, base(run, head, rel));
    return Json.map(
        "bases",
        bases,
        "run_id",
        run.get("id"),
        "repository",
        identity(),
        "branch",
        branch,
        "old_head",
        head,
        "target",
        target,
        "own",
        own,
        "foreign",
        foreign(own.keySet()),
        "index",
        git("ls-files", "--stage", "-z"),
        "phase",
        "prepared");
  }

  Map<String, Object> base(Map<String, Object> run, String head, String rel) {
    Path blob = w.directory(run).resolve("sync-base.tmp");
    var result =
        w.process.run(List.of("git", "show", head + ":" + rel), repo, w.settings.timeout, blob);
    if (result.exitCode() != 0) {
      check(git("ls-tree", head, "--", rel).isBlank(), "Ausgangsdatei nicht lesbar: " + rel);
      return Json.map("exists", false);
    }
    try {
      String entry = git("ls-tree", head, "--", rel);
      check(
          entry.startsWith("100644") || entry.startsWith("100755"),
          "Nicht unterstützter Dateityp: " + rel);
      return Json.map(
          "exists",
          true,
          "bytes",
          Base64.getEncoder().encodeToString(Files.readAllBytes(blob)),
          "executable",
          entry.startsWith("100755"));
    } catch (IOException e) {
      throw new Problem("repository_sync_io", "Ausgangsdatei nicht lesbar.");
    }
  }

  // Roll back only our exact intermediate state. External edits always leave the journal intact.
  void rollback(Map<String, Object> run) {
    try (var guard = lock(w)) {
      Path path = control(w).resolve("datenportal-sync.json");
      if (!Files.exists(path)) return;
      var journal = Json.read(path);
      if (!Objects.equals(journal.get("run_id"), run.get("id"))
          || !Objects.equals(journal.get("repository"), identity())
          || !Objects.equals(journal.get("old_head"), git("rev-parse", "HEAD").strip())
          || !Objects.equals(journal.get("index"), git("ls-files", "--stage", "-z"))) return;
      var own = Json.obj(journal.get("own"));
      if (!Objects.equals(journal.get("foreign"), foreign(own.keySet()))) return;
      for (String rel : own.keySet()) {
        var actual = file(rel);
        if (!actual.equals(own.get(rel)) && !actual.equals(Json.obj(journal.get("bases")).get(rel)))
          return;
      }
      for (String rel : own.keySet()) write(rel, Json.obj(own.get(rel)));
      Files.delete(path);
    } catch (Problem | IOException ignored) {
      // Backups remain durable and the next call diagnoses or resumes the exact recorded state.
    }
  }

  Object sync(Map<String, Object> run, Map<String, Object> env) {
    try (var guard = lock(w)) {
      Path journal = control(w).resolve("datenportal-sync.json");
      Map<String, Object> transaction = Files.exists(journal) ? Json.read(journal) : null;
      if (transaction != null) {
        check(
            Objects.equals(transaction.get("run_id"), run.get("id"))
                && Objects.equals(transaction.get("repository"), identity()),
            "Ein anderer Vorgang oder Checkout hat eine offene Synchronisierung.");
      }
      String target = new Repository(w).requireMerged(run, env);
      if (target == null) {
        check(transaction == null, "Offene Synchronisierung hat keine passenden Änderungen.");
        var result = Json.map("phase", "not_required");
        run.put("repository_sync", result);
        return result;
      }
      check(
          Path.of(git("rev-parse", "--show-toplevel").strip())
              .toRealPath()
              .toString()
              .equals(identity()),
          "topics_repo muss die Wurzel des Checkouts sein.");
      for (String entry : paths("ls-files", "-v", "-z"))
        check(
            !Character.isLowerCase(entry.charAt(0)) && entry.charAt(0) != 'S',
            "Index mit assume-unchanged oder skip-worktree zuerst klären.");
      for (String rel : Json.obj(run.get("changes")).keySet()) {
        String entry = git("ls-tree", target, "--", rel);
        check(
            entry.startsWith("100644") || entry.startsWith("100755"),
            "Nicht unterstützte Zieldatei: " + rel);
      }
      String branch = Json.str(env, "git_branch", "main");
      check(
          git("symbolic-ref", "--quiet", "--short", "HEAD").strip().equals(branch),
          "Lokaler Checkout steht nicht auf dem Zielbranch.");
      for (String marker :
          List.of(
              "MERGE_HEAD",
              "CHERRY_PICK_HEAD",
              "REVERT_HEAD",
              "rebase-merge",
              "rebase-apply",
              "sequencer",
              "BISECT_LOG",
              "index.lock"))
        check(
            !Files.exists(repo.resolve(git("rev-parse", "--git-path", marker).strip())),
            "Laufende Git-Operation: " + marker);
      check(git("ls-files", "--unmerged").isBlank(), "Ungelöste Git-Konflikte.");
      if (transaction == null) {
        transaction = create(run, target, branch);
        Json.atomic(journal, transaction);
      }
      check(
          Objects.equals(transaction.get("target"), target)
              && Objects.equals(transaction.get("branch"), branch),
          "Zielstand seit Beginn der Synchronisierung verändert; Journal zuerst klären.");
      var own = Json.obj(transaction.get("own"));
      String old = Json.required(transaction, "old_head");
      String current = git("rev-parse", "HEAD").strip();
      check(
          Objects.equals(transaction.get("foreign"), foreign(own.keySet())),
          "Fremde lokale Änderungen seit Beginn verändert.");
      if (!current.equals(target)) {
        check(current.equals(old), "Lokaler HEAD seit Beginn verändert.");
        check(
            Objects.equals(transaction.get("index"), git("ls-files", "--stage", "-z")),
            "Index seit Beginn verändert.");
        for (String rel : own.keySet()) {
          // A crash may leave a subset already restored. Only original or exact HEAD bytes are
          // accepted.
          var before = Json.obj(own.get(rel));
          Map<String, Object> base = Json.obj(Json.obj(transaction.get("bases")).get(rel));
          var actual = file(rel);
          check(
              actual.equals(before) || actual.equals(base),
              "Vorgangsdatei seit Beginn verändert: " + rel);
          write(rel, base);
        }
        transaction.put("phase", "restored");
        Json.atomic(journal, transaction);
        git(
            "-c",
            "core.hooksPath=/dev/null",
            "-c",
            "merge.autostash=false",
            "merge",
            "--ff-only",
            "--no-edit",
            target);
      }
      check(git("rev-parse", "HEAD").strip().equals(target), "Zielcommit nicht erreicht.");
      for (String rel : Json.obj(run.get("changes")).keySet())
        check(
            Files.isRegularFile(Json.inside(repo, rel))
                && Objects.equals(
                    Json.sha(Json.inside(repo, rel)),
                    Json.obj(Json.obj(run.get("changes")).get(rel)).get("sha256")),
            "Vorgangsdatei nach Update abweichend: " + rel);
      check(
          Objects.equals(transaction.get("foreign"), foreign(own.keySet())),
          "Fremde Änderungen nach Update abweichend.");
      for (String rel : Json.obj(run.get("changes")).keySet()) {
        check(
            git("diff", "--name-only", "HEAD", "--", rel).isBlank()
                && git("diff", "--cached", "--name-only", "--", rel).isBlank(),
            "Vorgangsdatei nach Update nicht sauber: " + rel);
      }
      var result =
          Json.map(
              "phase",
              "complete",
              "target",
              target,
              "repository",
              identity(),
              "files",
              new ArrayList<>(own.keySet()),
              "at",
              Json.now());
      run.put("repository_sync", result);
      w.persist(run);
      Files.delete(journal);
      return result;
    } catch (Problem e) {
      rollback(run);
      run.put(
          "repository_sync",
          Json.map("phase", "blocked", "reason", e.code, "message", e.getMessage()));
      throw e;
    } catch (IOException e) {
      rollback(run);
      run.put("repository_sync", Json.map("phase", "blocked", "reason", "repository_sync_io"));
      throw new Problem(
          "repository_sync_io",
          "Synchronisierung unterbrochen; Journal für Wiederaufnahme erhalten.");
    }
  }
}
