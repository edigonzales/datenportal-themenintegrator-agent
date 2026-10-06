package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Deliveries {
  final Workflow w;

  public Deliveries(Workflow w) {
    this.w = w;
  }

  void approved(Map<String, Object> r) {
    if (Json.bool(r, "converter_migration_required", false))
      throw new Problem(
          "converter_migration_required",
          "Python-Konverter zuerst nach Java portieren und erneut prüfen.");
    w.require(r, w.primaryGate(r));
    if (w.store.file(r, "data") != null) w.require(r, "data");
    if (r.containsKey("model") && w.primaryGate(r).equals("metadata")) w.require(r, "model");
  }

  void target(Map<String, Object> r, String name, Map<String, Object> env) {
    var plan = Json.obj(Json.obj(r.get("publication_plans")).get(name));
    if (plan.isEmpty()) throw new Problem("publication_review_required", "Publikationsplan fehlt.");
    if (r.get("identifier") != null) {
      var a = Jenkins.accepted(env, Json.required(r, "identifier"));
      if (!Objects.equals(
          a.xml() == null ? null : Json.digest(a.xml()), plan.get("target_sheet_hash")))
        throw new Problem(
            "target_changed",
            "Metadaten im Ziel wurden verändert; erneut vergleichen und freigeben.");
    }
  }

  void ready(Map<String, Object> r, String name, Map<String, Object> env, boolean publication) {
    approved(r);
    if (Json.str(env, "kind", "").equals("local")) {
      for (String rel : Json.obj(r.get("changes")).keySet()) {
        Path p = Json.inside(w.settings.topics, rel);
        if (!Files.isRegularFile(p)
            || !Objects.equals(
                Json.sha(p), Json.obj(Json.obj(r.get("changes")).get(rel)).get("sha256")))
          throw new Problem(
              "local_changes_missing",
              "Freigegebene Änderungen zuerst lokal übernehmen.",
              "path",
              rel);
      }
      var stack = new Stack(w.settings, w.process);
      stack.ensure();
      if (publication) stack.bootstrap(env);
    } else {
      w.require(r, "publish:" + name);
      new Repository(w).requireMerged(r, env);
      target(r, name, env);
    }
  }

  public Object plan(Map<String, Object> r, String name) {
    approved(r);
    var env = w.settings.environment(name);
    Jenkins.Accepted accepted =
        r.get("identifier") == null
            ? new Jenkins.Accepted(null, null)
            : Jenkins.accepted(env, Json.required(r, "identifier"));
    var files = Json.map();
    Json.obj(r.get("files"))
        .forEach(
            (k, v) -> {
              if (Set.of("data", "metadata", "model").contains(k))
                files.put(k, Json.obj(v).get("sha256"));
            });
    var plan =
        Json.map(
            "environment",
            name,
            "kind",
            env.get("kind"),
            "workflow",
            r.get("workflow"),
            "action",
            w.primaryGate(r).equals("metadata") ? "publication" : "repository_and_optional_seed",
            "jenkins_url",
            env.get("jenkins_url"),
            "portal_url",
            env.get("portal_url"),
            "branch",
            Json.str(env, "git_branch", "main"),
            "files",
            files,
            "changes",
            new ArrayList<>(Json.obj(r.get("changes")).keySet()),
            "issue",
            r.get("issue"),
            "target_sheet_hash",
            accepted.xml() == null ? null : Json.digest(accepted.xml()),
            "target_release",
            accepted.manifest() == null ? null : accepted.manifest().get("releaseId"));
    if (!r.containsKey("publication_plans")) r.put("publication_plans", Json.map());
    Json.obj(r.get("publication_plans")).put(name, plan);
    plan.put("fingerprint", w.fingerprint(r, "publish:" + name));
    return plan;
  }

  public Object deliver(Map<String, Object> r, String name) {
    if (!Json.str(r, "workflow", "topic").equals("topic"))
      throw new Problem(
          "wrong_workflow",
          "Nur Themenvorgänge liefern Daten. Organisation und Modell benötigen keine Publikation.");
    var env = w.settings.environment(name);
    var deliveries = Json.obj(r.get("deliveries"));
    if (deliveries.containsKey(name)) return advance(r, name, env, Json.obj(deliveries.get(name)));
    ready(r, name, env, true);
    var jenkins = w.jenkins.apply(env);
    var item =
        Json.map(
            "phase",
            "seed_submitting",
            "fingerprint",
            w.fingerprint(r, "metadata"),
            "at",
            Json.now());
    deliveries.put(name, item);
    w.persist(r);
    item.put("seed", jenkins.seed());
    item.put("phase", "seeding");
    return item;
  }

  public Object seed(Map<String, Object> r, String name) {
    var env = w.settings.environment(name);
    if (!r.containsKey("seeds")) r.put("seeds", Json.map());
    var seeds = Json.obj(r.get("seeds"));
    var item = Json.obj(seeds.get(name));
    if (!item.isEmpty()) {
      String phase = Json.str(item, "phase", "");
      if (phase.equals("seed_submitting"))
        throw new Problem(
            "submission_unknown", "Seed-Start unbestätigt. Queue zuordnen; nicht erneut starten.");
      if (Set.of("complete", "failed").contains(phase)) return item;
      var status = w.jenkins.apply(env).seedStatus(Json.obj(item.get("seed")));
      if (Json.bool(status, "complete", false)) {
        item.put("result", status.get("result"));
        item.put("phase", Objects.equals(status.get("result"), "SUCCESS") ? "complete" : "failed");
      }
      return item;
    }
    ready(r, name, env, false);
    var jenkins = w.jenkins.apply(env);
    item =
        Json.map(
            "phase",
            "seed_submitting",
            "at",
            Json.now(),
            "fingerprint",
            w.fingerprint(r, w.primaryGate(r)));
    seeds.put(name, item);
    w.persist(r);
    item.put("seed", jenkins.seed());
    item.put("phase", "seeding");
    return item;
  }

  Object advance(
      Map<String, Object> r, String name, Map<String, Object> env, Map<String, Object> item) {
    String phase = Json.required(item, "phase");
    if (Set.of("seed_submitting", "delivery_submitting").contains(phase))
      throw new Problem(
          "submission_unknown",
          "Start nicht bestätigt. Bestehenden Lauf zuordnen; niemals blind wiederholen.",
          "state",
          item);
    if (Set.of("complete", "failed").contains(phase)) return item;
    var j = w.jenkins.apply(env);
    if (phase.equals("seeding")) {
      var status = j.seedStatus(Json.obj(item.get("seed")));
      if (!Json.bool(status, "complete", false)) return item;
      if (!Objects.equals(status.get("result"), "SUCCESS")) {
        item.put("phase", "failed");
        item.put("error", "Seed fehlgeschlagen; Seed-Konsole prüfen.");
        return item;
      }
      approved(r);
      if (!Json.str(env, "kind", "").equals("local")) {
        w.require(r, "publish:" + name);
        new Repository(w).requireMerged(r, env);
        target(r, name, env);
      }
      Path metadata = w.store.file(r, "metadata"), data = w.store.file(r, "data");
      if (data != null && metadata == null) {
        var current = Jenkins.accepted(env, Json.required(r, "identifier"));
        if (!Objects.equals(
            current.xml() == null ? null : Json.digest(current.xml()),
            Json.obj(r.get("baseline")).get("sheet_hash")))
          throw new Problem(
              "target_changed",
              "Wirksames Datenblatt wurde verändert; Daten erneut dagegen prüfen.");
      }
      item.put(
          "verification",
          Json.map(
              "sheet",
              Json.obj(Json.obj(r.get("checks")).get("metadata")).get("sheet"),
              "data",
              Json.obj(r.get("files")).get("data")));
      item.put("phase", "delivery_submitting");
      w.persist(r);
      item.put(
          "job",
          j.submit(
              Json.required(r, "organization"),
              Json.required(r, "identifier"),
              Json.str(r, "issue", null),
              data,
              metadata,
              "Themenintegrator " + r.get("id")));
      item.put("phase", "running");
      return item;
    }
    var status = j.status(Json.obj(item.get("job")));
    if (!Json.bool(status, "complete", false)) return item;
    try {
      item.put("report", j.report(status));
    } catch (Problem e) {
      if (!e.code.equals("report_missing")) throw e;
      item.putAll(
          Json.map(
              "phase",
              "failed",
              "error",
              "Jenkins-Lauf ohne Publikationsbericht beendet; Konsole prüfen.",
              "build_url",
              status.get("consoleUrl"),
              "result",
              status.get("status")));
      return item;
    }
    outcome(r, env, item);
    item.put("build_url", status.get("consoleUrl"));
    return item;
  }

  void outcome(Map<String, Object> r, Map<String, Object> env, Map<String, Object> item) {
    var context = Json.obj(item.get("verification"));
    if (context.isEmpty()) context = recoverVerification(r, item);
    var sheet = Json.obj(context.get("sheet"));
    if (sheet.isEmpty())
      throw new Problem(
          "verification_snapshot_missing",
          "Prüfkontext des laufenden Vorgangs fehlt. Migration erfordert manuelle Aufklärung.");
    Path data = null;
    if (context.get("data") != null) {
      var frozen = Json.map("id", r.get("id"), "files", Json.map("data", context.get("data")));
      data = w.store.file(frozen, "data");
    }
    item.putAll(w.jenkins.apply(env).verify(Json.obj(item.get("report")), sheet, data));
    item.put("phase", Json.bool(item, "verified", false) ? "complete" : "failed");
  }

  Map<String, Object> recoverVerification(Map<String, Object> run, Map<String, Object> item) {
    var report = Json.obj(item.get("report"));
    if (!Objects.equals(report.get("dataset"), run.get("identifier"))
        || !Objects.equals(report.get("comment"), "Themenintegrator " + run.get("id")))
      return Json.map();
    Map<String, Object> meta = deliveryArtifact(run, Json.str(report, "metadataFileName", null));
    if (meta.isEmpty()) return Json.map();
    Map<String, Object> data = deliveryArtifact(run, Json.str(report, "dataFileName", null));
    if (!Json.str(report, "dataFileName", "").isEmpty() && data.isEmpty()) return Json.map();
    Path sheetPath = Path.of(Json.required(meta, "path"));
    var sheet =
        Xml.describe(
            sheetPath,
            Json.required(run, "identifier"),
            data.isEmpty() ? null : Json.str(report, "issue", Json.str(run, "issue", null)));
    var context =
        Json.map(
            "sheet",
            sheet,
            "data",
            data.isEmpty() ? null : data,
            "metadata_sha256",
            meta.get("sha256"),
            "recovered_from",
            "content-addressed filenames in confirmed Jenkins report");
    item.put("verification", context);
    Store.event(
        run,
        "recover_verification",
        Json.map("metadata_sha256", meta.get("sha256"), "data_sha256", data.get("sha256")));
    return context;
  }

  Map<String, Object> deliveryArtifact(Map<String, Object> run, String name) {
    if (name == null || !name.matches("[a-f0-9]{64}\\.(csv|xtf|xml)")) return Json.map();
    Path path = w.directory(run).resolve("files").resolve(name);
    String hash = name.substring(0, 64);
    if (!Files.isRegularFile(path) || !Json.sha(path).equals(hash)) return Json.map();
    return Json.map("path", path.toString(), "sha256", hash, "original_name", name);
  }

  public Object verify(Map<String, Object> r, String name) {
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    if (!Objects.equals(Json.obj(item.get("report")).get("publication"), "accepted"))
      throw new Problem("publication_unconfirmed", "Keine bestätigte Publikation zum Nachprüfen.");
    outcome(r, w.settings.environment(name), item);
    return item;
  }

  public Object retry(Map<String, Object> r, String name) {
    approved(r);
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    var report = Json.obj(item.get("report"));
    if (!Objects.equals(item.get("phase"), "failed")
        || Set.of("accepted", "unknown").contains(Json.str(report, "publication", ""))
        || (item.containsKey("job") && report.isEmpty()))
      throw new Problem(
          "retry_unsafe",
          "Nur eindeutig fehlgeschlagene, nicht publizierte Läufe können neu gestartet werden.");
    if (!r.containsKey("delivery_history")) r.put("delivery_history", new ArrayList<>());
    Json.list(r.get("delivery_history")).add(Json.map("environment", name, "attempt", item));
    Json.obj(r.get("deliveries")).remove(name);
    return Json.map("ready_for_new_attempt", true, "environment", name);
  }

  public Object reconcile(Map<String, Object> r, Map<String, Object> args, boolean seed) {
    String name = Json.required(args, "environment"), queue = Json.required(args, "queue");
    if (!queue.matches("[0-9]+"))
      throw new Problem("invalid_queue", "Numerische Jenkins-Queue-ID erforderlich.");
    var env = w.settings.environment(name);
    var j = w.jenkins.apply(env);
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    if (seed && !Objects.equals(item.get("phase"), "seed_submitting"))
      item = Json.obj(Json.obj(r.get("seeds")).get(name));
    if (!Objects.equals(item.get("phase"), seed ? "seed_submitting" : "delivery_submitting"))
      throw new Problem("reconcile_not_needed", "Kein unbestätigter Start vorhanden.");
    if (seed) {
      String url = j.base + "/queue/item/" + queue + "/";
      var q = j.get(url + "api/json");
      var task = Json.obj(q.get("task"));
      if (!Json.str(task, "fullName", Json.str(task, "name", ""))
          .equals(Json.str(env, "seed_job", "gretl-datenportal-seed")))
        throw new Problem("wrong_seed", "Queue-Eintrag gehört nicht zum konfigurierten Seed-Job.");
      item.put("seed", Json.map("queue_url", url));
      item.put("phase", "seeding");
    } else {
      var candidate = Json.map("job", Json.required(args, "job"), "queue", queue);
      var status = j.status(candidate);
      var parameters = Json.map();
      Json.list(status.get("parameters")).stream()
          .map(Json::obj)
          .forEach(p -> parameters.put(Json.str(p, "name", ""), p.get("value")));
      if (!Objects.equals(parameters.get("COMMENT"), "Themenintegrator " + r.get("id"))
          || !Objects.equals(parameters.get("DATASET"), r.get("identifier"))
          || !Objects.equals(parameters.get("ORGANISATION"), r.get("organization")))
        throw new Problem("wrong_delivery", "Lauf gehört nicht zum gespeicherten Vorgang.");
      item.put("job", candidate);
      item.put("phase", "running");
    }
    return item;
  }
}
