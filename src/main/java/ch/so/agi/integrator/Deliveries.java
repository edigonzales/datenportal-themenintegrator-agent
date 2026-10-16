package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Deliveries {
  final Workflow w;

  public Deliveries(Workflow w) {
    this.w = w;
  }

  static Map<String, Object> describe(Map<String, Object> item) {
    return describe(item, false);
  }

  static Map<String, Object> describe(Map<String, Object> item, boolean standaloneSeed) {
    var result = new LinkedHashMap<>(item);
    if (Json.obj(item.get("report")).containsKey("publication"))
      result.putIfAbsent("publication", Json.obj(item.get("report")).get("publication"));
    String phase = Json.str(item, "phase", "");
    boolean seed =
        !phase.equals("prepared")
            && (phase.startsWith("seed")
                || (!item.containsKey("job")
                    && !item.containsKey("report")
                    && !phase.equals("delivery_submitting")));
    result.put("action", seed ? "seed" : "delivery");
    result.put(
        "reference", item.getOrDefault(seed ? "seed" : "job", item.get("submission_target")));
    result.put(
        "next_operation",
        phase.equals("delivery_submitting")
            ? "reconcile"
            : phase.equals("seed_submitting")
                ? "reconcile_seed"
                : phase.equals("failed")
                    ? (seed
                        ? "adopt_seed"
                        : accepted(item)
                            ? "verify_delivery"
                            : retrySafe(item) ? "retry_delivery" : null)
                    : phase.equals("complete") ? null : standaloneSeed ? "seed" : "deliver");
    if (phase.equals("failed") && seed && !standaloneSeed)
      result.put("alternative_operation", "retry_delivery");
    if (phase.equals("running") && "accepted".equals(result.get("publication")))
      result.put("next_operation", "verify_delivery");
    if (phase.equals("failed") && !seed && !accepted(item) && !retrySafe(item)) {
      result.put("blocked_reason", "publication_unconfirmed");
      result.put(
          "required_action",
          "Bestehenden Lauf mit inspect_delivery untersuchen. Publikationsausgang durch Betreiber klären; nur bei belegter Nichtpublikation confirm_delivery_not_published mit tatsächlicher menschlicher Erklärung verwenden. Keine Modellableitung oder Dateiübernahme als Umgehung; deliver und retry_delivery nicht wiederholen.");
    }
    return result;
  }

  static boolean accepted(Map<String, Object> item) {
    return "accepted".equals(Json.obj(item.get("report")).get("publication"));
  }

  static boolean retrySafe(Map<String, Object> item) {
    if (!"failed".equals(item.get("phase"))) return false;
    var report = Json.obj(item.get("report"));
    if (Set.of("accepted", "unknown").contains(Json.str(report, "publication", ""))) return false;
    if (!item.containsKey("job")) return true;
    if (!report.isEmpty())
      return Set.of("not-published", "candidates-uploaded")
          .contains(Json.str(report, "publication", ""));
    var proof = Json.obj(item.get("nonpublication_confirmation"));
    return !proof.isEmpty()
        && Set.of("FAILURE", "ABORTED").contains(Json.str(item, "result", ""))
        && Json.number(item, "report_count", -1) == 0
        && Objects.equals(proof.get("job"), item.get("job"))
        && Objects.equals(proof.get("build_url"), item.get("build_url"))
        && Objects.equals(proof.get("fingerprint"), item.get("fingerprint"))
        && Json.number(proof, "generation", 0) == Json.number(item, "generation", 1);
  }

  static Map<String, Object> states(Map<String, Object> r) {
    var result = Json.map();
    for (String kind : List.of("deliveries", "seeds")) {
      var entries = Json.map();
      Json.obj(r.get(kind))
          .forEach(
              (name, value) -> {
                var state = describe(Json.obj(value), kind.equals("seeds"));
                state.put("environment", name);
                if (kind.equals("deliveries")) state.putIfAbsent("generation", 1);
                entries.put(name, state);
              });
      result.put(kind, entries);
    }
    return result;
  }

  void localFiles(Map<String, Object> r) {
    for (String rel : Json.obj(r.get("changes")).keySet()) {
      Path p = Json.inside(w.settings.topics, rel);
      if (!Files.isRegularFile(p))
        throw new Problem(
            "local_changes_missing",
            "Geprüfte Datei fehlt im Themenrepo. Nicht automatisch wiederherstellen.",
            "path",
            rel,
            "required_action",
            "review_repository_changes");
      if (!Objects.equals(Json.sha(p), Json.obj(Json.obj(r.get("changes")).get(rel)).get("sha256")))
        throw new Problem(
            "local_changes_modified",
            "Datei im Themenrepo wurde verändert. Korrektur als Kandidat prüfen; nicht überschreiben.",
            "path",
            rel,
            "required_action",
            "review_repository_changes");
    }
  }

  public Object adopt(Map<String, Object> r, Map<String, Object> args) {
    String name = Json.required(args, "environment");
    var env = w.settings.environment(name);
    var delivery = Json.obj(Json.obj(r.get("deliveries")).get(name));
    var previous = Json.obj(Json.obj(r.get("seeds")).get(name));
    if ("seed_submitting".equals(previous.get("phase"))
        || (!delivery.isEmpty()
            && (delivery.containsKey("job")
                || delivery.containsKey("report")
                || !Set.of("failed", "seeding", "prepared")
                    .contains(Json.str(delivery, "phase", "")))))
      throw new Problem(
          "seed_adoption_unsafe",
          "Bestehenden Start zuerst aufklären; Seed-Übernahme setzt keine Lieferung zurück.");
    var seed = w.jenkins.apply(name, env).manualSeed(Json.required(args, "build_url"));
    var item =
        Json.map(
            "phase",
            Json.bool(seed, "complete", false) ? "complete" : "seeding",
            "seed",
            seed,
            "at",
            Json.now(),
            "fingerprint",
            w.fingerprint(r, w.primaryGate(r)));
    if (Objects.equals(Json.obj(previous.get("seed")).get("build_url"), seed.get("build_url"))
        && Objects.equals(previous.get("fingerprint"), item.get("fingerprint"))
        && (delivery.isEmpty()
            || "prepared".equals(delivery.get("phase"))
            || ("seeding".equals(delivery.get("phase"))
                && Objects.equals(
                    Json.obj(delivery.get("seed")).get("build_url"), seed.get("build_url"))
                && Objects.equals(delivery.get("fingerprint"), item.get("fingerprint"))))) {
      previous.put("seed", seed);
      previous.put("phase", item.get("phase"));
      return describe(previous, true);
    }
    if (!r.containsKey("seeds")) r.put("seeds", Json.map());
    if (!previous.isEmpty()) {
      if (!r.containsKey("seed_history")) r.put("seed_history", new ArrayList<>());
      Json.list(r.get("seed_history")).add(Json.map("environment", name, "attempt", previous));
    }
    Json.obj(r.get("seeds")).put(name, item);
    if (!delivery.isEmpty() && !"prepared".equals(delivery.get("phase"))) {
      if (!r.containsKey("delivery_history")) r.put("delivery_history", new ArrayList<>());
      Json.list(r.get("delivery_history")).add(Json.map("environment", name, "attempt", delivery));
      Json.obj(r.get("deliveries")).remove(name);
    }
    return describe(item, true);
  }

  void approved(Map<String, Object> r) {
    for (String gate : approvalGates(r)) w.require(r, gate);
  }

  List<String> approvalGates(Map<String, Object> r) {
    var gates = new ArrayList<>(List.of(w.primaryGate(r)));
    if (w.store.file(r, "data") != null) gates.add("data");
    if (r.containsKey("model") && w.primaryGate(r).equals("metadata")) gates.add("model");
    return gates;
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
    if (Json.str(env, "kind", "").equals("local") && !Json.obj(r.get("applied")).isEmpty())
      localFiles(r);
    approved(r);
    // Resolve credentials before any stack changes or new external submission.
    var credentials = w.credentials.apply(name, env);
    if (Json.str(env, "kind", "").equals("local")) {
      if (r.containsKey("pull_request")) new RepositorySync(w).sync(r, env);
      localFiles(r);
      var stack = new Stack(w.settings, w.process);
      stack.ensure();

      if (publication) stack.bootstrap(env, credentials);
    } else {
      w.require(r, "publish:" + name);
      new RepositorySync(w).sync(r, env);
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
            accepted.manifest() == null ? null : accepted.manifest().get("releaseId"),
            "delivery_generation",
            Json.obj(Json.obj(r.get("deliveries")).get(name)).getOrDefault("generation", 1));
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
    var prepared = Json.obj(deliveries.get(name));
    if (!prepared.isEmpty() && !"prepared".equals(prepared.get("phase")))
      return current(r, advance(r, name, env, prepared));
    if (!prepared.isEmpty() && !Objects.equals(prepared.get("content_fingerprint"), content(r)))
      throw new Problem(
          "followup_changed",
          "Stand seit Vorbereitung geändert; prepare_followup_delivery erneut aufrufen.");
    if ("seed_submitting".equals(Json.obj(Json.obj(r.get("seeds")).get(name)).get("phase")))
      throw new Problem(
          "submission_unknown", "Unbestätigten Seed zuerst mit reconcile_seed aufklären.");
    ready(r, name, env, true);
    var adopted = Json.obj(Json.obj(r.get("seeds")).get(name));
    if (!Json.obj(adopted.get("seed")).isEmpty()
        && Set.of("complete", "seeding").contains(Json.str(adopted, "phase", ""))) {
      if (!Objects.equals(adopted.get("fingerprint"), w.fingerprint(r, w.primaryGate(r))))
        throw new Problem(
            "seed_review_changed",
            "Prüfstand seit Seed-Zuordnung geändert; Seed nach Prüfung erneut zuordnen.");
      var item = Json.read(Json.text(adopted));
      generation(item, Json.obj(Json.obj(r.get("delivery_generations")).get(name)), name);
      item.put("phase", "seeding");
      deliveries.put(name, item);
      return describe(item);
    }
    var jenkins = w.jenkins.apply(name, env);
    var item =
        Json.map(
            "phase",
            "seed_submitting",
            "fingerprint",
            w.fingerprint(r, "metadata"),
            "at",
            Json.now());
    deliveries.put(name, item);
    generation(item, Json.obj(Json.obj(r.get("delivery_generations")).get(name)), name);
    item.put(
        "submission_target", Json.map("job", Json.str(env, "seed_job", "gretl-datenportal-seed")));
    w.persist(r);
    item.put("seed", jenkins.seed());
    item.put("phase", "seeding");
    return describe(item);
  }

  void generation(Map<String, Object> item, Map<String, Object> prepared, String name) {
    item.put("environment", name);
    item.put("generation", prepared.getOrDefault("generation", 1));
    if (prepared.containsKey("predecessor")) item.put("predecessor", prepared.get("predecessor"));
  }

  Map<String, Object> snapshot(Map<String, Object> r) {
    return Json.map(
        "sheet",
        w.describe(r),
        "data",
        Json.obj(r.get("files")).get("data"),
        "model",
        Json.obj(r.get("files")).get("model"));
  }

  static String content(Map<String, Object> snapshot, boolean model) {
    return Json.digest(
        Json.map(
            "sheet",
            Xml.business(Json.obj(snapshot.get("sheet")).get("fields")),
            "data",
            Json.obj(snapshot.get("data")).get("sha256"),
            "model",
            model ? Json.obj(snapshot.get("model")).get("sha256") : null));
  }

  String content(Map<String, Object> r) {
    return content(snapshot(r), true);
  }

  Object current(Map<String, Object> r, Object result) {
    var out = Json.obj(result);
    var frozen = Json.obj(out.get("verification"));
    if (!frozen.isEmpty()) {
      boolean changed;
      try {
        changed =
            !content(frozen, frozen.containsKey("model"))
                .equals(content(snapshot(r), frozen.containsKey("model")));
      } catch (Problem error) {
        out.put("current_candidate_delivered", null);
        out.put("candidate_error", error.code);
        return out;
      }
      out.put("current_candidate_delivered", !changed);
      if (changed
          && "accepted".equals(Json.obj(out.get("report")).get("publication"))
          && Set.of("complete", "failed").contains(Json.str(out, "phase", "")))
        out.put("next_operation", "prepare_followup_delivery");
    }
    return out;
  }

  public Object followup(Map<String, Object> r, String name) {
    var env = w.settings.environment(name);
    if (!Json.str(r, "workflow", "topic").equals("topic"))
      throw new Problem("wrong_workflow", "Folgelieferungen gehören zu Themenvorgängen.");
    var deliveries = Json.obj(r.get("deliveries"));
    var previous = Json.obj(deliveries.get(name));
    var seed = Json.obj(Json.obj(r.get("seeds")).get(name));
    syncSeed(r, name, previous);
    if (Set.of("seed_submitting", "seeding").contains(Json.str(seed, "phase", "")))
      throw new Problem("followup_unsafe", "Bestehenden Seed zuerst aufklären oder abschliessen.");
    boolean prepared = "prepared".equals(previous.get("phase"));
    if (!prepared
        && (!Set.of("complete", "failed").contains(Json.str(previous, "phase", ""))
            || !"accepted".equals(Json.obj(previous.get("report")).get("publication"))))
      throw new Problem(
          "followup_unsafe",
          "Nur eine eindeutig angenommene abgeschlossene Lieferung erlaubt eine Folgelieferung. Unbestätigten Upload zuerst aufklären.");
    approved(r);
    if (Json.str(env, "kind", "").equals("local")) localFiles(r);
    String fingerprint = content(r);
    if (prepared
        && Objects.equals(
            previous.get("predecessor_content_fingerprint"),
            content(snapshot(r), Json.bool(previous, "predecessor_has_model", false))))
      throw new Problem(
          "followup_unchanged",
          "Stand entspricht wieder der vorherigen Lieferung; keinen weiteren Upload starten.");
    if (prepared && Objects.equals(previous.get("content_fingerprint"), fingerprint))
      return describe(previous);
    var frozen = Json.obj(previous.get("verification"));
    if (!prepared
        && (frozen.isEmpty()
            || content(frozen, frozen.containsKey("model"))
                .equals(content(snapshot(r), frozen.containsKey("model")))))
      throw new Problem(
          "followup_unchanged",
          "Kein belegter inhaltlich veränderter Lieferstand; bestehende Lieferung nachprüfen.");
    var next =
        Json.map(
            "phase",
            "prepared",
            "environment",
            name,
            "generation",
            prepared
                ? Json.number(previous, "generation", 1)
                : Json.number(previous, "generation", 1) + 1,
            "predecessor",
            prepared
                ? previous.get("predecessor")
                : Json.map(
                    "generation",
                    previous.getOrDefault("generation", 1),
                    "release_id",
                    Json.obj(previous.get("report")).get("releaseId")),
            "content_fingerprint",
            fingerprint,
            "predecessor_content_fingerprint",
            prepared
                ? previous.get("predecessor_content_fingerprint")
                : content(frozen, frozen.containsKey("model")),
            "predecessor_has_model",
            prepared ? previous.get("predecessor_has_model") : frozen.containsKey("model"),
            "fingerprint",
            w.fingerprint(r, "metadata"),
            "at",
            Json.now());
    if (!prepared) {
      r.computeIfAbsent("delivery_history", k -> new ArrayList<>());
      Json.list(r.get("delivery_history")).add(Json.map("environment", name, "attempt", previous));
    }
    deliveries.put(name, next);
    r.computeIfAbsent("delivery_generations", k -> Json.map());
    Json.obj(r.get("delivery_generations"))
        .put(
            name,
            Json.map("generation", next.get("generation"), "predecessor", next.get("predecessor")));
    Json.obj(r.get("publication_plans")).remove(name);
    Json.obj(r.get("approvals")).remove("publish:" + name);
    if (!seed.isEmpty()
        && !Objects.equals(seed.get("fingerprint"), w.fingerprint(r, w.primaryGate(r)))) {
      r.computeIfAbsent("seed_history", k -> new ArrayList<>());
      Json.list(r.get("seed_history")).add(Json.map("environment", name, "attempt", seed));
      Json.obj(r.get("seeds")).remove(name);
    }
    return describe(next);
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
      if (Set.of("complete", "failed").contains(phase)) return describe(item, true);
      var status = w.jenkins.apply(name, env).seedStatus(Json.obj(item.get("seed")));
      if (Json.bool(status, "complete", false)) {
        item.put("result", status.get("result"));
        item.put("phase", Objects.equals(status.get("result"), "SUCCESS") ? "complete" : "failed");
      }
      return describe(item, true);
    }
    var delivery = Json.obj(Json.obj(r.get("deliveries")).get(name));
    if (Set.of("delivery_submitting", "seed_submitting").contains(Json.str(delivery, "phase", "")))
      throw new Problem(
          "submission_unknown",
          "Unbestätigten Start zuerst aufklären; keinen weiteren Seed starten.");
    ready(r, name, env, false);
    var jenkins = w.jenkins.apply(name, env);
    item =
        Json.map(
            "phase",
            "seed_submitting",
            "at",
            Json.now(),
            "fingerprint",
            w.fingerprint(r, w.primaryGate(r)));
    seeds.put(name, item);
    item.put(
        "submission_target", Json.map("job", Json.str(env, "seed_job", "gretl-datenportal-seed")));
    w.persist(r);
    item.put("seed", jenkins.seed());
    item.put("phase", "seeding");
    return describe(item, true);
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
    if (Set.of("complete", "failed").contains(phase)) return describe(item);
    var j = w.jenkins.apply(name, env);
    if (phase.equals("seeding")) {
      var status = j.seedStatus(Json.obj(item.get("seed")));
      syncSeed(r, name, item);
      if (!Json.bool(status, "complete", false)) return describe(item);
      if (!Objects.equals(status.get("result"), "SUCCESS")) {
        item.put("phase", "failed");
        item.put("error", "Seed fehlgeschlagen; Seed-Konsole prüfen.");
        return describe(item);
      }
      if (Json.str(env, "kind", "").equals("local")) localFiles(r);
      approved(r);
      if (!Objects.equals(item.get("fingerprint"), w.fingerprint(r, w.primaryGate(r))))
        throw new Problem(
            "seed_review_changed",
            "Prüfstand seit Seed-Zuordnung geändert; Seed nach Prüfung erneut zuordnen.");
      if (!Json.str(env, "kind", "").equals("local")) {
        w.require(r, "publish:" + name);
        new RepositorySync(w).sync(r, env);
        target(r, name, env);
      } else if (r.containsKey("pull_request")) {
        new RepositorySync(w).sync(r, env);
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
              Json.obj(r.get("files")).get("data"),
              "model",
              Json.obj(r.get("files")).get("model")));
      item.put("phase", "delivery_submitting");
      item.put(
          "submission_target",
          Json.map(
              "endpoint",
              j.base + "/gretl-datenportal/build",
              "organization",
              r.get("organization"),
              "dataset",
              r.get("identifier")));
      w.persist(r);
      item.put(
          "job",
          j.submit(
              Json.required(r, "organization"),
              Json.required(r, "identifier"),
              Json.str(r, "issue", null),
              data,
              metadata,
              comment(r, item)));
      item.put("phase", "running");
      return describe(item);
    }
    return observe(r, name, env, item, j);
  }

  Object observe(
      Map<String, Object> r,
      String name,
      Map<String, Object> env,
      Map<String, Object> item,
      Jenkins j) {
    var job = new LinkedHashMap<>(Json.obj(item.get("job")));
    Object previousBuild = job.get("build");
    var status = j.status(job);
    if (previousBuild != null && !Objects.equals(previousBuild, job.get("build")))
      throw new Problem(
          "wrong_delivery",
          "Jenkins-Status verweist auf einen anderen Build; bestehenden Lauf nicht ersetzen.");
    item.put("job", job);
    if (!Json.bool(status, "complete", false)) {
      item.remove("nonpublication_confirmation");
      item.put("phase", "running");
      return describe(item);
    }
    item.put("result", status.get("status"));
    item.put("build_url", status.get("consoleUrl"));
    try {
      item.put("report", j.report(status));
      item.remove("nonpublication_confirmation");
    } catch (Problem e) {
      if (!e.code.equals("report_missing")) throw e;
      item.putAll(
          Json.map(
              "phase",
              "failed",
              "error",
              "Jenkins-Lauf ohne eindeutigen Publikationsbericht beendet; Konsole prüfen.",
              "build_url",
              status.get("consoleUrl"),
              "result",
              status.get("status")));
      item.put("report_count", e.details.get("report_count"));
      item.put("diagnosis", j.diagnosis(status));
      return describe(item);
    }
    item.remove("error");
    item.remove("report_count");
    item.remove("diagnosis");
    outcome(r, name, env, item);
    item.put("build_url", status.get("consoleUrl"));
    return describe(item);
  }

  void syncSeed(Map<String, Object> r, String name, Map<String, Object> delivery) {
    var seed = Json.obj(Json.obj(r.get("seeds")).get(name));
    var observed = Json.obj(delivery.get("seed"));
    if (observed.get("build_url") != null
        && Json.bool(observed, "complete", false)
        && Objects.equals(observed.get("build_url"), Json.obj(seed.get("seed")).get("build_url"))
        && Objects.equals(delivery.get("fingerprint"), seed.get("fingerprint"))) {
      seed.put("seed", Json.read(Json.text(observed)));
      seed.put("phase", "SUCCESS".equals(observed.get("result")) ? "complete" : "failed");
      seed.put("result", observed.get("result"));
    }
  }

  void outcome(
      Map<String, Object> r, String name, Map<String, Object> env, Map<String, Object> item) {
    var context = Json.obj(item.get("verification"));
    var sheet = Json.obj(context.get("sheet"));
    if (sheet.isEmpty())
      throw new Problem(
          "verification_snapshot_missing",
          "Prüfsnapshot der Lieferung fehlt. Bestehenden Lauf manuell klären; keinen neuen Upload starten.");
    Path data = null;
    if (context.get("data") != null) {
      var frozen = Json.map("id", r.get("id"), "files", Json.map("data", context.get("data")));
      data = w.store.file(frozen, "data");
    }
    item.putAll(w.jenkins.apply(name, env).verify(Json.obj(item.get("report")), sheet, data));
    item.put("phase", Json.bool(item, "verified", false) ? "complete" : "failed");
  }

  public Object inspect(Map<String, Object> r, String name) {
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    if (!item.containsKey("job")
        || Set.of("delivery_submitting", "seed_submitting").contains(Json.str(item, "phase", "")))
      throw new Problem(
          "delivery_inspection_unavailable",
          "Kein eindeutig gespeicherter Lieferlauf; gegebenenfalls zuerst reconcile verwenden.");
    return observe(
        r,
        name,
        w.settings.environment(name),
        item,
        w.jenkins.apply(name, w.settings.environment(name)));
  }

  public Object confirmNotPublished(Map<String, Object> r, Map<String, Object> args) {
    String name = Json.required(args, "environment");
    String statement = Json.required(args, "human_statement").trim();
    String evidence = Json.required(args, "evidence").trim();
    if (statement.isEmpty() || evidence.isEmpty())
      throw new Problem(
          "nonpublication_evidence_required",
          "Begründung und tatsächliche menschliche Nichtpublikationsbestätigung erforderlich.");
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    String build = Json.required(args, "build_url");
    if (!Objects.equals(build, item.get("build_url")))
      throw new Problem(
          "wrong_delivery", "Build-URL muss exakt dem gespeicherten Lieferlauf entsprechen.");
    inspect(r, name);
    w.persist(r);
    if (!Objects.equals(build, item.get("build_url"))
        || !"failed".equals(item.get("phase"))
        || !Set.of("FAILURE", "ABORTED").contains(Json.str(item, "result", ""))
        || !Json.obj(item.get("report")).isEmpty()
        || Json.number(item, "report_count", -1) != 0)
      throw new Problem(
          "nonpublication_confirmation_unsafe",
          "Nur beendeten, fehlgeschlagenen Build ohne Bericht bestätigen; widersprüchliche Zustände zuerst klären.");
    var proof =
        Json.map(
            "run_id",
            r.get("id"),
            "environment",
            name,
            "job",
            Json.read(Json.text(item.get("job"))),
            "build_url",
            build,
            "generation",
            Json.number(item, "generation", 1),
            "fingerprint",
            item.get("fingerprint"),
            "at",
            Json.now(),
            "evidence",
            evidence,
            "human_statement",
            statement);
    item.put("nonpublication_confirmation", proof);
    Store.event(r, "confirm_delivery_not_published", proof);
    return describe(item);
  }

  public Object verify(Map<String, Object> r, String name) {
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    if (!Objects.equals(Json.obj(item.get("report")).get("publication"), "accepted"))
      throw new Problem("publication_unconfirmed", "Keine bestätigte Publikation zum Nachprüfen.");
    outcome(r, name, w.settings.environment(name), item);
    return current(r, describe(item));
  }

  static String comment(Map<String, Object> r, Map<String, Object> item) {
    int generation = Json.number(item, "generation", 1);
    return "Themenintegrator " + r.get("id") + (generation > 1 ? " generation=" + generation : "");
  }

  public Object retry(Map<String, Object> r, String name) {
    approved(r);
    var item = Json.obj(Json.obj(r.get("deliveries")).get(name));
    var report = Json.obj(item.get("report"));
    if (!retrySafe(item))
      throw new Problem(
          "retry_unsafe",
          "Nur eindeutig fehlgeschlagene, nicht publizierte Läufe können neu gestartet werden.");
    if (!r.containsKey("delivery_history")) r.put("delivery_history", new ArrayList<>());
    Json.list(r.get("delivery_history")).add(Json.map("environment", name, "attempt", item));
    Json.obj(r.get("deliveries")).remove(name);
    Json.obj(r.computeIfAbsent("delivery_generations", k -> Json.map()))
        .put(name, Json.map("generation", Json.number(item, "generation", 1) + 1));
    var adopted = Json.obj(Json.obj(r.get("seeds")).get(name));
    if ("manual".equals(Json.obj(adopted.get("seed")).get("origin"))) {
      if (!r.containsKey("seed_history")) r.put("seed_history", new ArrayList<>());
      Json.list(r.get("seed_history")).add(Json.map("environment", name, "attempt", adopted));
      Json.obj(r.get("seeds")).remove(name);
    }
    return Json.map("ready_for_new_attempt", true, "environment", name);
  }

  public Object reconcile(Map<String, Object> r, Map<String, Object> args, boolean seed) {
    String name = Json.required(args, "environment"), queue = Json.required(args, "queue");
    if (!queue.matches("[0-9]+"))
      throw new Problem("invalid_queue", "Numerische Jenkins-Queue-ID erforderlich.");
    var env = w.settings.environment(name);
    var j = w.jenkins.apply(name, env);
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
      if (!Objects.equals(parameters.get("COMMENT"), comment(r, item))
          || !Objects.equals(parameters.get("DATASET"), r.get("identifier"))
          || !Objects.equals(parameters.get("ORGANISATION"), r.get("organization")))
        throw new Problem("wrong_delivery", "Lauf gehört nicht zum gespeicherten Vorgang.");
      item.put("job", candidate);
      item.put("phase", "running");
    }
    return describe(item);
  }
}
