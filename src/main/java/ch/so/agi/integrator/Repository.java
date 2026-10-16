package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Repository {
  final Workflow w;
  GitHub github;

  public Repository(Workflow w) {
    this.w = w;
  }

  String command(Path cwd, String... args) {
    if (!Set.of("clone", "push", "fetch", "ls-remote").contains(args.length > 1 ? args[1] : ""))
      return w.process.checked(List.of(args), cwd, w.settings.timeout);
    var result =
        w.process.run(
            List.of(args),
            cwd,
            w.settings.timeout,
            null,
            GitHubCredentials.environment(w.settings));
    if (result.exitCode() != 0) throw new Problem("git_failed", "Git-Zugriff fehlgeschlagen.");
    return result.output().strip();
  }

  public Object plan(Map<String, Object> run, String environment) {
    Offices.scope(run);
    var env = w.settings.environment(environment);
    String remote = Json.str(env, "git_remote", "origin");
    // Parse before displaying: credential-bearing or ambiguous URLs are rejected without echoing
    // them.
    var repo = GitHub.repository(command(w.settings.topics, "git", "remote", "get-url", remote));
    var blockers = new ArrayList<Map<String, Object>>();
    boolean required = !Json.obj(run.get("changes")).isEmpty();
    if (required) {
      try {
        new RepositoryChecks(this).local(run);
      } catch (Problem error) {
        blockers.add(error.result());
      }
      for (String gate : new Deliveries(w).approvalGates(run)) {
        try {
          w.require(run, gate);
        } catch (Problem error) {
          blockers.add(error.result());
        }
      }
      try {
        w.requireLocalTest(run);
      } catch (Problem error) {
        blockers.add(error.result());
      }
    }
    var check = new RepositoryChecks(this);
    RepositoryChecks.Inspection inspection = null;
    if (required && blockers.isEmpty()) {
      Path temporary = null;
      try {
        temporary =
            check.cloneTemporary(
                command(w.settings.topics, "git", "remote", "get-url", remote),
                Json.str(env, "git_branch", "main"));
        inspection = check.inspect(run, temporary, repo, Json.str(env, "git_branch", "main"));
        new PrPreparation(this).validateExisting(run, repo, Json.str(env, "git_branch", "main"));
      } catch (Problem error) {
        blockers.add(error.result());
      } finally {
        if (temporary != null) RepositoryChecks.removeTemporary(temporary.getParent());
      }
    }
    return Json.map(
        "action",
        "repository_plan",
        "target_commit",
        inspection == null ? null : inspection.commit(),
        "file_comparisons",
        inspection == null ? List.of() : inspection.files(),
        "next_operation",
        blockers.isEmpty() ? "prepare_pr" : "repository_plan",
        "environment",
        environment,
        "remote",
        remote,
        "repository",
        "https://github.com/" + repo.owner() + "/" + repo.name(),
        "base",
        Json.str(env, "git_branch", "main"),
        "changes",
        new ArrayList<>(Json.obj(run.get("changes")).keySet()),
        "required",
        required,
        "ready",
        blockers.isEmpty(),
        "blockers",
        blockers);
  }

  public Object prepare(Map<String, Object> run, String environment) {
    Offices.scope(run);
    var env = w.settings.environment(environment);
    var changes = Json.obj(run.get("changes"));
    if (changes.isEmpty()) return Json.map("required", false);
    new RepositoryChecks(this).local(run);
    new Deliveries(w).approved(run);
    w.requireLocalTest(run);
    if (run.containsKey("pull_request")) return run.get("pull_request");
    if (github == null) github = w.github.get();
    if (run.containsKey("pr_submission")) return recover(run);
    String
        remote =
            command(
                w.settings.topics,
                "git",
                "remote",
                "get-url",
                Json.str(env, "git_remote", "origin")),
        base = Json.str(env, "git_branch", "main");
    GitHub.Repo repo = GitHub.repository(remote);
    var check = new RepositoryChecks(this);
    Path temporary = check.cloneTemporary(remote, base);
    RepositoryChecks.Inspection inspection;
    try {
      inspection = check.inspect(run, temporary, repo, base);
    } finally {
      RepositoryChecks.removeTemporary(temporary.getParent());
    }
    run.put("git_bases", inspection.bases());
    w.persist(run);
    String branch = "codex/themenintegration-" + Json.required(run, "id").substring(0, 12);
    var preparation = new PrPreparation(this);
    Path clone = preparation.checkout(run, repo, remote, base, branch, inspection.commit());
    var progress = Json.obj(run.get("pr_preparation"));
    boolean committed =
        !command(clone, "git", "rev-parse", "HEAD").equals(progress.get("base_commit"));
    if (!committed) {
      for (String rel : changes.keySet()) {
        Path target = Json.inside(clone, rel);
        try {
          Files.createDirectories(target.getParent());
          Files.copy(w.repoFile(run, rel), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
          throw new Problem("file_write_failed", "PR-Dateien nicht schreibbar.");
        }
      }
      var add = new ArrayList<>(List.of("git", "add", "--"));
      add.addAll(changes.keySet());
      w.process.checked(add, clone, w.settings.timeout);
      command(clone, "git", "diff", "--cached", "--check");
      if (command(clone, "git", "diff", "--cached", "--name-only").isBlank()) {
        var result =
            Json.map(
                "required",
                false,
                "reason",
                "already_on_target",
                "owner",
                repo.owner(),
                "repository",
                repo.name(),
                "base",
                base,
                "source_checkout",
                w.settings.topics.toString());
        run.put("pull_request", result);
        return result;
      }
    }
    String subject =
        Offices.standalone(run)
            ? "Office-Katalog"
            : Json.str(run, "identifier", Json.required(run, "organization"));
    String title = "Datenportal: " + subject;
    if (!committed) {
      progress.put("phase", "committing");
      w.persist(run);
      command(clone, "git", "commit", "-m", title);
    }
    String head = command(clone, "git", "rev-parse", "HEAD");
    progress.put("head", head);
    progress.put("phase", "pushing");
    w.persist(run);
    String remoteHead =
        command(clone, "git", "ls-remote", "origin", "refs/heads/" + branch).strip();
    if (!remoteHead.isEmpty() && !remoteHead.split("\\s+")[0].equals(head))
      throw new Problem(
          "pr_remote_conflict",
          "PR-Branch enthält einen anderen Commit. Kein Push.",
          "action",
          "prepare_pr",
          "next_operation",
          "repository_plan");
    if (remoteHead.isEmpty()) command(clone, "git", "push", "-u", "origin", branch);
    progress.put("phase", "pushed");
    w.persist(run);
    Path body = w.directory(run).resolve("pull-request-body.md");
    Json.write(
        body,
        "Übernimmt den geprüften "
            + run.get("workflow")
            + "-Stand für `"
            + subject
            + "`.\n\nFachliche Dateien: "
            + String.join(", ", changes.keySet())
            + ".\n\nPrüfstand: `"
            + w.fingerprint(run, w.primaryGate(run))
            + "`. Prüfdetails liegen in der lokalen HTML-Vorschau.\n\nEin Mensch übernimmt den PR. Anschliessend prüft der Integrator den Zielstand und synchronisiert das lokale Themenrepository. Dieser PR startet keine Anlieferung; INT/PROD benötigen einen separaten Auftrag und eine Publikationsfreigabe.\n");
    run.put(
        "pr_submission",
        Json.map(
            "owner",
            repo.owner(),
            "repository",
            repo.name(),
            "head",
            head,
            "branch",
            branch,
            "source_checkout",
            w.settings.topics.toString(),
            "checkout",
            clone.toString(),
            "base",
            base));
    // Persist before POST, including when the process disappears before a response.
    w.persist(run);
    var response = github.create(repo, base, branch, title, Json.contents(body));
    String url = Json.required(response, "html_url");
    var result =
        Json.map(
            "required",
            true,
            "url",
            url,
            "head",
            head,
            "branch",
            branch,
            "source_checkout",
            w.settings.topics.toString(),
            "owner",
            repo.owner(),
            "repository",
            repo.name(),
            "checkout",
            clone.toString(),
            "base",
            base);
    run.put("pull_request", result);
    return result;
  }

  Object recover(Map<String, Object> run) {
    var pending = Json.obj(run.get("pr_submission"));
    var repo =
        new GitHub.Repo(Json.required(pending, "owner"), Json.required(pending, "repository"));
    var matches =
        github.find(repo, Json.required(pending, "branch"), Json.required(pending, "base")).stream()
            .filter(pr -> Objects.equals(Json.obj(pr.get("head")).get("sha"), pending.get("head")))
            .toList();
    if (matches.size() != 1)
      throw new Problem(
          "github_pr_ambiguous",
          "PR-Ausgang nicht eindeutig; vorhandenen GitHub-Stand klären. Kein erneuter POST.");
    var result = new LinkedHashMap<>(pending);
    result.put("required", true);
    result.put("url", Json.required(matches.getFirst(), "html_url"));
    run.put("pull_request", result);
    return result;
  }

  public String requireMerged(Map<String, Object> r, Map<String, Object> env) {
    var changes = Json.obj(r.get("changes"));
    if (changes.isEmpty()) return null;
    var pr = Json.obj(r.get("pull_request"));
    if (pr.isEmpty())
      throw new Problem(
          "pull_request_required",
          "Repository-Änderungen benötigen einen menschlich übernommenen PR.");
    String branch = Json.str(env, "git_branch", "main"),
        remote = Json.str(env, "git_remote", "origin");
    Path clone = w.settings.topics;
    if (pr.containsKey("source_checkout")
        && !Path.of(Json.required(pr, "source_checkout"))
            .toAbsolutePath()
            .normalize()
            .equals(clone.toAbsolutePath().normalize()))
      throw new Problem(
          "repository_sync_conflict",
          "Konfigurierter Checkout wurde seit der PR-Vorbereitung geändert.");
    if (pr.containsKey("base") && !branch.equals(pr.get("base")))
      throw new Problem(
          "repository_sync_conflict", "Zielbranch wurde seit der PR-Vorbereitung geändert.");
    if (pr.containsKey("owner")
        && !new GitHub.Repo(Json.required(pr, "owner"), Json.required(pr, "repository"))
            .equals(GitHub.repository(command(clone, "git", "remote", "get-url", remote))))
      throw new Problem(
          "repository_sync_conflict", "Repository wurde seit der PR-Vorbereitung geändert.");
    if (Json.bool(pr, "required", false)) {
      if (github == null) github = w.github.get();
      var uri = java.net.URI.create(Json.required(pr, "url"));
      var parts = uri.getPath().split("/");
      if (!"https".equals(uri.getScheme())
          || !"github.com".equals(uri.getHost())
          || parts.length != 5
          || !"pull".equals(parts[3]))
        throw new Problem("github_pr_invalid", "Gespeicherte PR-Adresse ist ungültig.");
      var repo = GitHub.repository("https://github.com/" + parts[1] + "/" + parts[2]);
      if (!repo.equals(GitHub.repository(command(clone, "git", "remote", "get-url", remote))))
        throw new Problem(
            "repository_sync_conflict", "Konfiguriertes Repository entspricht nicht dem PR.");
      var status = github.get(repo, Long.parseLong(parts[4]));
      if (!Json.bool(status, "merged", false)
          || !Objects.equals(Json.obj(status.get("base")).get("ref"), branch))
        throw new Problem(
            "human_merge_required",
            "PR muss auf den vorgesehenen Zielbranch übernommen werden.",
            "url",
            pr.get("url"));
      if (!Objects.equals(Json.obj(status.get("head")).get("sha"), pr.get("head")))
        throw new Problem("pr_changed", "PR wurde seit der Freigabe verändert.");
    }
    command(clone, "git", "fetch", remote, "refs/heads/" + branch);
    String target = command(clone, "git", "rev-parse", "FETCH_HEAD^{commit}");
    for (String rel : changes.keySet()) {
      Path out = w.directory(r).resolve("merged-content.tmp");
      var result =
          w.process.run(List.of("git", "show", target + ":" + rel), clone, w.settings.timeout, out);
      if (result.exitCode() != 0
          || !Objects.equals(Json.sha(out), Json.obj(changes.get(rel)).get("sha256")))
        throw new Problem(
            "merged_content_changed",
            "Zielbranch entspricht nicht dem freigegebenen Dateistand.",
            "path",
            rel);
    }
    return target;
  }

  public Object sync(Map<String, Object> run, String environment) {
    Offices.scope(run);
    Object result = new RepositorySync(w).sync(run, w.settings.environment(environment));
    if (Offices.standalone(run)
        && "complete".equals(Json.obj(run.get("repository_sync")).get("phase")))
      run.put(
          "completion",
          Json.map("status", "office_updated", "message", "Office-Katalog aktualisiert."));
    return result;
  }
}
