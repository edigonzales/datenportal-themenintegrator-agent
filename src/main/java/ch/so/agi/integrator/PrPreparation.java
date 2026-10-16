package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Only provably owned preparation work may be resumed. Never reset or clean a checkout. */
final class PrPreparation {
  final Repository r;
  final Workflow w;
  final RepositoryChecks check;

  PrPreparation(Repository r) {
    this.r = r;
    w = r.w;
    check = new RepositoryChecks(r);
  }

  Map<String, Object> candidates(Map<String, Object> run) {
    var result = Json.map();
    Json.obj(run.get("changes"))
        .forEach((path, value) -> result.put(path, Json.obj(value).get("sha256")));
    return result;
  }

  Problem conflict(String detail) {
    return new Problem(
        "pr_checkout_conflict",
        detail,
        "action",
        "prepare_pr",
        "required_action",
        "PR-Arbeitskopie prüfen; unbekannte Änderungen erhalten. Keine Lieferung wiederholen.",
        "next_operation",
        "repository_plan");
  }

  void validateExisting(Map<String, Object> run, GitHub.Repo repo, String base) {
    Path checkout = w.directory(run).resolve("pr-checkout");
    var progress = Json.obj(run.get("pr_preparation"));
    if (!Files.exists(checkout)) {
      if (!progress.isEmpty() && !"cloning".equals(progress.get("phase")))
        throw conflict("Gespeicherte PR-Arbeitskopie fehlt.");
      return;
    }
    String branch = "codex/themenintegration-" + Json.required(run, "id").substring(0, 12);
    if (Files.isSymbolicLink(checkout) || !Files.isDirectory(checkout.resolve(".git")))
      throw conflict("PR-Arbeitskopie ist kein eigener Git-Checkout.");
    if (!repo.equals(GitHub.repository(check.git(checkout, "remote", "get-url", "origin"))))
      throw conflict("PR-Repository stimmt nicht überein.");
    String current = check.git(checkout, "branch", "--show-current");
    if (!branch.equals(current)
        && !("cloning".equals(progress.get("phase")) && base.equals(current)))
      throw conflict("PR-Branch stimmt nicht überein.");
    String head = check.git(checkout, "rev-parse", "HEAD^{commit}");
    String baseCommit;
    if (progress.isEmpty()) {
      baseCommit = check.git(w.settings.topics, "rev-parse", "HEAD^{commit}");
      if (!head.equals(baseCommit)
          || !head.equals(check.git(checkout, "rev-parse", "refs/remotes/origin/" + base)))
        throw conflict("Historischer PR-Checkout hat keine eindeutige Basis.");
    } else {
      if (!Objects.equals(progress.get("run_id"), run.get("id"))
          || !Objects.equals(progress.get("repository"), repo.owner() + "/" + repo.name())
          || !Objects.equals(progress.get("base"), base)
          || !Objects.equals(progress.get("candidates"), candidates(run)))
        throw conflict("PR-Vorbereitung gehört zu einem anderen Prüfstand.");
      baseCommit = Json.required(progress, "base_commit");
      if (!head.equals(baseCommit)) {
        if (!Set.of("committing", "pushing", "pushed").contains(Json.str(progress, "phase", "")))
          throw conflict("Unerwarteter PR-Commit.");
        if (!check.git(checkout, "rev-parse", "HEAD^").equals(baseCommit)
            || check.git(checkout, "rev-list", "--parents", "-n", "1", "HEAD").split(" ").length
                != 2) throw conflict("Unerwartete PR-Commit-Historie.");
        if (progress.containsKey("head") && !head.equals(progress.get("head")))
          throw conflict("PR-Commit wurde verändert.");
        for (var e : candidates(run).entrySet())
          if (!Objects.equals(check.hash(checkout, head, e.getKey()), e.getValue()))
            throw conflict("PR-Commit enthält abweichende Kandidaten.");
        for (String path : check.git(checkout, "diff", "--name-only", baseCommit, head).split("\n"))
          if (!path.isBlank() && !candidates(run).containsKey(path))
            throw conflict("PR-Commit enthält fremde Dateien.");
      }
    }
    // Include ignored files: they are also user work and may never be silently removed.
    var status =
        w.process.run(
            List.of("git", "status", "--porcelain=v1", "--untracked-files=all", "--ignored"),
            checkout,
            w.settings.timeout,
            null);
    if (status.exitCode() != 0) throw conflict("Git-Status nicht lesbar.");
    String dirty = status.output();
    if (!head.equals(baseCommit) && !dirty.isBlank())
      throw conflict("Committeter PR-Checkout wurde lokal verändert.");
    for (String line : dirty.split("\n")) {
      if (line.isBlank()) continue;
      if (line.length() < 4) throw conflict("Unbekannter Git-Status.");
      String path = line.substring(3);
      if (!candidates(run).containsKey(path))
        throw conflict("PR-Arbeitskopie enthält fremde Dateien: " + path);
      Path file = Json.inside(checkout, path);
      if (Files.isSymbolicLink(file)
          || !Files.isRegularFile(file)
          || !Objects.equals(Json.sha(file), candidates(run).get(path)))
        throw conflict("PR-Arbeitskopie enthält manuelle Änderungen: " + path);
    }
  }

  Path checkout(
      Map<String, Object> run,
      GitHub.Repo repo,
      String remote,
      String base,
      String branch,
      String target) {
    Path checkout = w.directory(run).resolve("pr-checkout");
    validateExisting(run, repo, base);
    var progress = Json.obj(run.get("pr_preparation"));
    if (Files.exists(checkout)
        && (progress.isEmpty() || !Objects.equals(progress.get("base_commit"), target))) {
      if (!progress.isEmpty()
          && !check.git(checkout, "rev-parse", "HEAD").equals(progress.get("base_commit")))
        return checkout; // The current target was checked separately; preserve the committed
      // branch.
      try {
        Files.move(checkout, checkout.resolveSibling("pr-checkout-archive-" + UUID.randomUUID()));
      } catch (java.io.IOException e) {
        throw conflict("Historische PR-Arbeitskopie konnte nicht archiviert werden.");
      }
      progress = Json.map();
    }
    if (progress.isEmpty()) {
      progress =
          Json.map(
              "run_id",
              run.get("id"),
              "repository",
              repo.owner() + "/" + repo.name(),
              "base",
              base,
              "base_commit",
              target,
              "branch",
              branch,
              "candidates",
              candidates(run),
              "phase",
              "cloning");
      run.put("pr_preparation", progress);
      w.persist(run);
    }
    if (!Files.exists(checkout))
      check.git(
          w.settings.root,
          "clone",
          "--single-branch",
          "--branch",
          base,
          remote,
          checkout.toString());
    if (!check.git(checkout, "rev-parse", "HEAD").equals(target)
        && "cloning".equals(progress.get("phase")))
      throw conflict(
          "Zielbranch änderte sich während des Klonens; repository_plan erneut ausführen.");
    if (base.equals(check.git(checkout, "branch", "--show-current")))
      check.git(checkout, "switch", "-c", branch);
    if ("cloning".equals(progress.get("phase"))) {
      progress.put("phase", "copying");
      w.persist(run);
    }
    return checkout;
  }
}
