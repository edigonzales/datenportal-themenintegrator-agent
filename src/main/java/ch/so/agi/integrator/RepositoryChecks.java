package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Git identities are separate from the mutable working-copy preimages used by stage/apply. */
final class RepositoryChecks {
  final Repository repository;
  final Workflow w;

  RepositoryChecks(Repository repository) {
    this.repository = repository;
    this.w = repository.w;
  }

  String git(Path cwd, String... args) {
    var command = new ArrayList<>(List.of("git"));
    command.addAll(List.of(args));
    return repository.command(cwd, command.toArray(String[]::new));
  }

  static void capture(Workflow w, Map<String, Object> run, String relative) {
    if (!Files.exists(w.settings.topics.resolve(".git"))) return;
    var bases = Json.obj(run.get("git_bases"));
    if (bases.containsKey(relative)) return;
    var check = new RepositoryChecks(new Repository(w));
    var env =
        Json.obj(
            Json.obj(w.settings.values.get("environments"))
                .get(Json.str(run, "source_environment", "local")));
    String remote = Json.str(env, "git_remote", "origin");
    String branch = Json.str(env, "git_branch", "main");
    String url = check.git(w.settings.topics, "remote", "get-url", remote);
    var repo = GitHub.repository(url);
    String head = check.git(w.settings.topics, "rev-parse", "HEAD^{commit}");
    String current = check.git(w.settings.topics, "branch", "--show-current");
    if (!branch.equals(current))
      throw new Problem(
          "pr_basis_unknown",
          "Git-Basis muss vom konfigurierten Zielbranch stammen.",
          "path",
          relative);
    bases.put(
        relative,
        Json.map(
            "repository",
            repo.owner() + "/" + repo.name(),
            "base",
            branch,
            "commit",
            head,
            "sha256",
            check.hash(w.settings.topics, head, relative)));
    run.put("git_bases", bases);
  }

  String hash(Path checkout, String commit, String relative) {
    String entry = git(checkout, "ls-tree", commit, "--", relative);
    if (entry.isBlank()) return null;
    if (!entry.startsWith("100644 blob ") && !entry.startsWith("100755 blob "))
      throw new Problem(
          "pr_file_type", "Git-Zieldatei ist keine reguläre Datei.", "path", relative);
    Path output = null;
    try {
      output = Files.createTempFile("integrator-git-blob-", ".tmp");
      var result =
          w.process.run(
              List.of("git", "show", commit + ":" + relative),
              checkout,
              w.settings.timeout,
              output);
      if (result.exitCode() != 0)
        throw new Problem("pr_basis_unknown", "Git-Dateistand nicht lesbar.", "path", relative);
      return Json.sha(output);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Temporärer Git-Abgleich nicht möglich.");
    } finally {
      if (output != null)
        try {
          Files.deleteIfExists(output);
        } catch (java.io.IOException ignored) {
        }
    }
  }

  void local(Map<String, Object> run) {
    for (var entry : Json.obj(run.get("changes")).entrySet()) {
      String relative = entry.getKey();
      var change = Json.obj(entry.getValue());
      w.repoFile(run, relative);
      Path local = Json.inside(w.settings.topics, relative);
      String actual = Files.isRegularFile(local) ? Json.sha(local) : null;
      var applied = Json.obj(Json.obj(run.get("applied")).get("hashes"));
      if ((!Objects.equals(actual, change.get("before_sha256"))
              && !Objects.equals(actual, change.get("sha256")))
          || (applied.containsKey(relative)
              && !Objects.equals(actual, applied.get(relative))
              && !Objects.equals(actual, change.get("sha256"))))
        throw problem(
            actual == null ? "local_changes_missing" : "local_changes_modified",
            relative,
            change.get("before_sha256"),
            actual,
            change.get("sha256"),
            "Manuelle Änderung als Kandidat prüfen; keine Datei zurückkopieren.");
    }
  }

  record Inspection(String commit, Map<String, Object> bases, List<Map<String, Object>> files) {}

  Inspection inspect(Map<String, Object> run, Path target, GitHub.Repo repo, String branch) {
    local(run);
    String head = git(target, "rev-parse", "HEAD^{commit}");
    var bases = new LinkedHashMap<>(Json.obj(run.get("git_bases")));
    var files = new ArrayList<Map<String, Object>>();
    for (var entry : Json.obj(run.get("changes")).entrySet()) {
      String relative = entry.getKey();
      var change = Json.obj(entry.getValue());
      var basis = Json.obj(bases.get(relative));
      String targetHash = hash(target, head, relative);
      if (basis.isEmpty()) {
        String localHead = git(w.settings.topics, "rev-parse", "HEAD^{commit}");
        String localHash = hash(w.settings.topics, localHead, relative);
        // Legacy evidence: the same commit, or an unchanged, provable tracked preimage.
        if ((localHash == null && !localHead.equals(head))
            || (localHash != null
                && !Objects.equals(localHash, change.get("before_sha256"))
                && !Objects.equals(localHash, change.get("sha256"))))
          throw problem(
              "pr_basis_unknown",
              relative,
              null,
              targetHash,
              change.get("sha256"),
              "Historische Git-Basis belegen; keine Lieferung wiederholen.");
        basis =
            Json.map(
                "repository",
                repo.owner() + "/" + repo.name(),
                "base",
                branch,
                "commit",
                localHead,
                "sha256",
                localHash);
        bases.put(relative, basis);
      }
      if (!Objects.equals(basis.get("repository"), repo.owner() + "/" + repo.name())
          || !Objects.equals(basis.get("base"), branch))
        throw problem(
            "pr_basis_unknown",
            relative,
            basis.get("sha256"),
            targetHash,
            change.get("sha256"),
            "Repository und Zielbranch der gespeicherten Basis prüfen.");
      String classification;
      if (Objects.equals(targetHash, change.get("sha256"))) classification = "already_on_target";
      else if (Objects.equals(targetHash, basis.get("sha256")))
        classification = targetHash == null ? "addition" : "modification";
      else
        throw problem(
            "pr_base_conflict",
            relative,
            basis.get("sha256"),
            targetHash,
            change.get("sha256"),
            "Zielbranchänderung fachlich abgleichen und betroffenen Kandidaten erneut prüfen.");
      files.add(
          Json.map(
              "path",
              relative,
              "classification",
              classification,
              "basis_sha256",
              basis.get("sha256"),
              "target_sha256",
              targetHash,
              "candidate_sha256",
              change.get("sha256")));
    }
    return new Inspection(head, bases, files);
  }

  static Problem problem(
      String code, String path, Object basis, Object target, Object candidate, String action) {
    return new Problem(
        code,
        "Repository-Abgleich blockiert: " + path,
        "action",
        "prepare_pr",
        "path",
        path,
        "basis_sha256",
        basis,
        "target_sha256",
        target,
        "candidate_sha256",
        candidate,
        "required_action",
        action,
        "next_operation",
        "repository_plan");
  }

  Path cloneTemporary(String remote, String branch) {
    try {
      Path parent = Files.createTempDirectory("integrator-pr-plan-");
      Path checkout = parent.resolve("checkout");
      try {
        git(
            w.settings.root,
            "clone",
            "--single-branch",
            "--branch",
            branch,
            remote,
            checkout.toString());
        return checkout;
      } catch (RuntimeException e) {
        removeTemporary(parent);
        throw e;
      }
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Temporäre PR-Prüfung nicht möglich.");
    }
  }

  static void removeTemporary(Path directory) {
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Temporäre PR-Prüfung konnte nicht entfernt werden.");
    }
  }
}
