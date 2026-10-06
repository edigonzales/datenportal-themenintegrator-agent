package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Repository {
  final Workflow w;

  public Repository(Workflow w) {
    this.w = w;
  }

  String command(Path cwd, String... args) {
    return w.process.checked(List.of(args), cwd, w.settings.timeout);
  }

  public Object prepare(Map<String, Object> run, String environment) {
    var env = w.settings.environment(environment);
    w.require(run, "publish:" + environment);
    var changes = Json.obj(run.get("changes"));
    if (changes.isEmpty()) return Json.map("required", false);
    if (run.containsKey("pull_request")) return run.get("pull_request");
    String
        remote =
            command(
                w.settings.topics,
                "git",
                "remote",
                "get-url",
                Json.str(env, "git_remote", "origin")),
        base = Json.str(env, "git_branch", "main");
    Path clone = w.directory(run).resolve("pr-checkout");
    if (Files.exists(clone))
      throw new Problem(
          "pr_checkout_exists",
          "PR-Arbeitskopie existiert bereits; ihren Zustand vor erneutem Push prüfen.",
          "path",
          clone.toString());
    command(
        w.settings.root,
        "git",
        "clone",
        "--single-branch",
        "--branch",
        base,
        remote,
        clone.toString());
    String branch = "codex/themenintegration-" + Json.required(run, "id").substring(0, 12);
    command(clone, "git", "switch", "-c", branch);
    for (String rel : changes.keySet()) {
      Path target = Json.inside(clone, rel);
      String actual = Files.exists(target) ? Json.sha(target) : null;
      var c = Json.obj(changes.get(rel));
      if (!Objects.equals(actual, c.get("before_sha256"))
          && !Objects.equals(actual, c.get("sha256")))
        throw new Problem(
            "pr_base_conflict",
            "Zielbranch unterscheidet sich vom geprüften Ausgangsstand.",
            "path",
            rel);
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
      var result = Json.map("required", false, "reason", "already_on_target");
      run.put("pull_request", result);
      return result;
    }
    String subject = Json.str(run, "identifier", Json.required(run, "organization"));
    String title = "Datenportal: " + subject;
    command(clone, "git", "commit", "-m", title);
    String head = command(clone, "git", "rev-parse", "HEAD");
    command(clone, "git", "push", "-u", "origin", branch);
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
            + "`. Prüfdetails liegen in der lokalen HTML-Vorschau.\n\nEin Mensch übernimmt den PR. Anschliessend prüft der Integrator den Zielstand und führt den ausdrücklich freigegebenen Seed beziehungsweise die Lieferung aus.\n");
    String url =
        command(
            clone,
            "gh",
            "pr",
            "create",
            "--base",
            base,
            "--head",
            branch,
            "--title",
            title,
            "--body-file",
            body.toString());
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
            "checkout",
            clone.toString(),
            "base",
            base);
    run.put("pull_request", result);
    return result;
  }

  public void requireMerged(Map<String, Object> r, Map<String, Object> env) {
    var changes = Json.obj(r.get("changes"));
    if (changes.isEmpty()) return;
    var pr = Json.obj(r.get("pull_request"));
    if (pr.isEmpty())
      throw new Problem(
          "pull_request_required",
          "Repository-Änderungen benötigen einen menschlich übernommenen PR.");
    String branch = Json.str(env, "git_branch", "main"),
        remote = Json.str(env, "git_remote", "origin");
    Path clone = w.settings.topics;
    if (Json.bool(pr, "required", false)) {
      clone = Path.of(Json.required(pr, "checkout"));
      remote = "origin";
      var status =
          Json.read(
              command(
                  clone,
                  "gh",
                  "pr",
                  "view",
                  Json.required(pr, "url"),
                  "--json",
                  "state,baseRefName,headRefOid,mergeCommit"));
      if (!Objects.equals(status.get("state"), "MERGED")
          || !Objects.equals(status.get("baseRefName"), branch))
        throw new Problem(
            "human_merge_required",
            "PR muss auf den vorgesehenen Zielbranch übernommen werden.",
            "url",
            pr.get("url"));
      if (!Objects.equals(status.get("headRefOid"), pr.get("head")))
        throw new Problem("pr_changed", "PR wurde seit der Freigabe verändert.");
    }
    command(clone, "git", "fetch", remote, branch);
    for (String rel : changes.keySet()) {
      Path out = w.directory(r).resolve("merged-content.tmp");
      var result =
          w.process.run(
              List.of("git", "show", remote + "/" + branch + ":" + rel),
              clone,
              w.settings.timeout,
              out);
      if (result.exitCode() != 0
          || !Objects.equals(Json.sha(out), Json.obj(changes.get(rel)).get("sha256")))
        throw new Problem(
            "merged_content_changed",
            "Zielbranch entspricht nicht dem freigegebenen Dateistand.",
            "path",
            rel);
    }
  }
}
