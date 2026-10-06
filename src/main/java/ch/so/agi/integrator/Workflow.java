package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Workflow {
  final Settings settings;
  final Store store;
  final ProcessRunner process;
  final McpClients.ToolClient datasheets, interlis;
  final Validator validator;
  java.util.function.Function<Map<String, Object>, Jenkins> jenkins = Jenkins::new;

  public Workflow(Settings s) {
    this(s, new ProcessRunner(), McpClients.datasheet(s), McpClients.interlis(s));
  }

  public Workflow(Settings s, ProcessRunner p, McpClients.ToolClient d, McpClients.ToolClient i) {
    settings = s;
    store = new Store(s.runs);
    process = p;
    datasheets = d;
    interlis = i;
    validator = new Validator(s, p);
  }

  public Object call(String operation, Map<String, Object> args) {
    Operations.validate(operation, args);
    if (operation.equals("start")) return start(args);
    if (operation.equals("migrate_run"))
      return store.migrate(Json.required(args, "run_id"), Json.bool(args, "apply", false));
    if (operation.equals("metadata") && Json.str(args, "operation", "").equals("describe_schema"))
      return datasheets.call("describe_schema", Json.obj(args.get("arguments")));
    if (operation.equals("organization_schema")) return new Organizations(this).schema(args);
    return store.edit(
        Json.required(args, "run_id"),
        r ->
            switch (operation) {
              case "status" -> status(r);
              case "attach" -> attach(r, args);
              case "provenance" -> provenance(r, args);
              case "xlsx" ->
                  Reports.workbook(
                      requiredFile(r, "metadata_source"),
                      Json.str(args, "sheet", null),
                      Json.number(args, "offset", 0),
                      Json.number(args, "limit", 100));
              case "analyze" -> analyze(r);
              case "baseline" -> baseline(r);
              case "stage_change" ->
                  stage(
                      r,
                      Json.required(args, "relative_path"),
                      Path.of(Json.required(args, "source_path")));
              case "validate" -> validate(r);
              case "approve" -> approve(r, args);
              case "metadata" ->
                  metadata(r, Json.required(args, "operation"), Json.obj(args.get("arguments")));
              case "office" -> new Organizations(this).office(r, Json.obj(args.get("values")));
              case "prepare_organization" ->
                  new Organizations(this).prepare(r, Json.obj(args.get("values")));
              case "validate_organization" -> new Organizations(this).validate(r);
              case "derive_model" -> new Models(this).derive(r, args);
              case "validate_model" -> new Models(this).validate(r);
              case "transform" -> new Converters(this).transform(r, args);
              case "apply_local_changes" -> apply(r);
              case "publication_plan" ->
                  new Deliveries(this).plan(r, Json.required(args, "environment"));
              case "prepare_pr" ->
                  new Repository(this).prepare(r, Json.required(args, "environment"));
              case "deliver" ->
                  new Deliveries(this).deliver(r, Json.str(args, "environment", "local"));
              case "seed" -> new Deliveries(this).seed(r, Json.str(args, "environment", "local"));
              case "reconcile" -> new Deliveries(this).reconcile(r, args, false);
              case "reconcile_seed" -> new Deliveries(this).reconcile(r, args, true);
              case "verify_delivery" ->
                  new Deliveries(this).verify(r, Json.required(args, "environment"));
              case "retry_delivery" ->
                  new Deliveries(this).retry(r, Json.required(args, "environment"));
              default -> throw new Problem("unknown_operation", "Operation ist nicht bekannt.");
            });
  }

  private Object start(Map<String, Object> a) {
    String workflow = Json.str(a, "workflow", "topic");
    if (workflow.equals("organization")
        && (a.containsKey("data_path") || a.containsKey("metadata_path") || a.containsKey("issue")))
      throw new Problem(
          "invalid_arguments", "Organisationsvorgang benötigt keine Lieferdateien/Ausgabe.");
    var r =
        store.create(
            workflow,
            Json.required(a, "organization"),
            Json.str(a, "identifier", null),
            Json.str(a, "issue", null),
            Json.str(a, "source_environment", "local"));
    return store.edit(
        Json.required(r, "id"),
        run -> {
          if (a.containsKey("data_path")) {
            store.attach(run, Path.of(Json.required(a, "data_path")), "original_data");
            store.attach(run, Path.of(Json.required(a, "data_path")), "data");
          }
          if (a.containsKey("metadata_path"))
            store.attach(run, Path.of(Json.required(a, "metadata_path")), "metadata");
          return status(run);
        });
  }

  public Path directory(Map<String, Object> r) {
    return store.directory(Json.required(r, "id"));
  }

  public void persist(Map<String, Object> r) {
    Json.atomic(directory(r).resolve("state.json"), r);
  }

  public Path requiredFile(Map<String, Object> r, String role) {
    var p = store.file(r, role);
    if (p == null)
      throw new Problem(
          role.equals("metadata_source") ? "source_missing" : "file_missing",
          "Benötigtes Artefakt fehlt.",
          "role",
          role);
    return p;
  }

  public Path sheet(Map<String, Object> r) {
    Path p = store.file(r, "metadata");
    if (p == null) p = store.file(r, "accepted_metadata");
    if (p == null)
      throw new Problem(
          "metadata_missing",
          "Datenblatt fehlt; Ausgangsbestand laden oder via Java-MCP erstellen.");
    return p;
  }

  public Map<String, Object> describe(Map<String, Object> r) {
    return Xml.describe(
        sheet(r),
        Json.required(r, "identifier"),
        store.file(r, "data") == null ? null : Json.str(r, "issue", null));
  }

  public String primaryGate(Map<String, Object> r) {
    return switch (Json.str(r, "workflow", "topic")) {
      case "organization" -> "organization";
      case "model" -> "model";
      default -> "metadata";
    };
  }

  public String topic(Map<String, Object> r) {
    return Json.required(r, "organization") + "/" + Json.required(r, "identifier");
  }

  public Path repoFile(Map<String, Object> r, String relative) {
    Path target = Json.inside(settings.topics, relative);
    var change = Json.obj(Json.obj(r.get("changes")).get(relative));
    if (change.isEmpty()) return target;
    Path p = Path.of(Json.required(change, "path"));
    if (!p.toAbsolutePath().normalize().startsWith(directory(r).toAbsolutePath().normalize())
        || !Files.isRegularFile(p)
        || !Json.sha(p).equals(change.get("sha256")))
      throw new Problem(
          "change_modified", "Vorbereitete Repository-Datei wurde verändert.", "path", relative);
    return p;
  }

  public Path offices(Map<String, Object> r) {
    return repoFile(r, "shared/data/offices.xtf");
  }

  public String fingerprint(Map<String, Object> r, String gate) {
    var context = Json.map();
    Path shared = settings.topics.resolve("shared");
    if (Files.isDirectory(shared))
      try (var paths = Files.walk(shared)) {
        paths
            .filter(Files::isRegularFile)
            .filter(
                p ->
                    !p.toString().contains("/.gradle/")
                        && !p.toString().contains("/build/")
                        && !p.getFileName().toString().equals("offices.xtf"))
            .sorted()
            .forEach(
                p -> {
                  String rel = settings.topics.relativize(p).toString();
                  context.put(rel, Json.sha(repoFile(r, rel)));
                });
      } catch (java.io.IOException e) {
        throw new Problem("repository_unavailable", "Shared-Dateien nicht lesbar.");
      }
    String org = Json.required(r, "organization");
    for (String n : List.of("build.gradle", "settings.gradle", "gretl-datenportal-job.yaml")) {
      Path p = repoFile(r, org + "/" + n);
      if (Files.exists(p)) context.put(org + "/" + n, Json.sha(p));
    }
    if (r.get("identifier") != null) {
      Path recipes =
          settings.root.resolve("topics").resolve(org).resolve(Json.required(r, "identifier"));
      if (Files.isDirectory(recipes))
        try (var paths = Files.walk(recipes)) {
          paths
              .filter(Files::isRegularFile)
              .filter(p -> !p.toString().contains("/build/") && !p.toString().contains("/.gradle/"))
              .sorted()
              .forEach(p -> context.put(p.toString(), Json.sha(p)));
        } catch (java.io.IOException e) {
          throw new Problem("converter_unavailable", "Themenrezept nicht lesbar.");
        }
    }
    var files = Json.map();
    for (String role : Json.obj(r.get("files")).keySet())
      if (!gate.equals("data") || role.equals("data")) {
        Path p = store.file(r, role);
        files.put(role, Json.sha(p));
      }
    var changes = Json.map();
    if (!gate.equals("data"))
      Json.obj(r.get("changes"))
          .forEach(
              (k, v) -> {
                repoFile(r, k);
                var c = Json.obj(v);
                changes.put(
                    k,
                    Json.map("before_sha256", c.get("before_sha256"), "sha256", c.get("sha256")));
              });
    Object plan = null;
    if (gate.startsWith("publish:")) {
      var raw =
          new LinkedHashMap<>(
              Json.obj(Json.obj(r.get("publication_plans")).get(gate.substring(8))));
      raw.remove("fingerprint");
      plan = raw;
    }
    return Json.digest(
        Json.map(
            "gate",
            gate,
            "workflow",
            r.get("workflow"),
            "organization",
            org,
            "identifier",
            r.get("identifier"),
            "issue",
            r.get("issue"),
            "files",
            files,
            "settings",
            settings.fingerprint(),
            "context",
            context,
            "offices",
            gate.equals("data") ? null : Json.sha(offices(r)),
            "changes",
            changes,
            "provenance",
            gate.equals("data") ? null : r.get("provenance"),
            "transform",
            r.get("transform"),
            "baseline",
            r.get("baseline"),
            "model",
            gate.equals("data") ? null : r.get("model"),
            "organization_spec",
            gate.equals("data") ? null : r.get("organization_spec"),
            "publication_plan",
            plan));
  }

  public void exportRequired(Map<String, Object> r) {
    if (r.containsKey("pending_metadata")
        || (r.containsKey("draft")
            && !Objects.equals(
                r.get("exported_revision"), Json.obj(r.get("draft")).get("revision"))))
      throw new Problem(
          "draft_not_exported", "Aktuellen Entwurf zuerst exportieren, prüfen und freigeben.");
  }

  public void require(Map<String, Object> r, String gate) {
    if (!gate.equals("data") && !Json.str(r, "workflow", "topic").equals("organization"))
      exportRequired(r);
    var approval = Json.obj(Json.obj(r.get("approvals")).get(gate));
    if (!Objects.equals(approval.get("fingerprint"), fingerprint(r, gate)))
      throw new Problem(
          "approval_required",
          "Menschliche Freigabe fehlt oder ist nach Änderungen ungültig.",
          "gate",
          gate);
  }

  private Object status(Map<String, Object> r) {
    var result = new LinkedHashMap<>(r);
    var valid = Json.map();
    for (String gate : Json.obj(r.get("approvals")).keySet()) {
      try {
        require(r, gate);
        valid.put(gate, true);
      } catch (Problem e) {
        valid.put(gate, false);
      }
    }
    result.put("current_approvals", valid);
    if (r.get("identifier") != null) {
      Path recipe = settings.root.resolve("topics").resolve(topic(r)).resolve("integration.json");
      if (Files.isRegularFile(recipe)) result.put("topic_recipe", Json.read(recipe));
    }
    return result;
  }

  private Object attach(Map<String, Object> r, Map<String, Object> a) {
    String role = Json.required(a, "role");
    if (!Set.of("data", "metadata", "metadata_source", "model").contains(role))
      throw new Problem("invalid_role", "Erlaubt: data, metadata, metadata_source, model.");
    var result = store.attach(r, Path.of(Json.required(a, "path")), role);
    if (role.equals("data")) store.attach(r, Path.of(Json.required(a, "path")), "original_data");
    if (role.equals("metadata")) {
      r.remove("draft");
      r.remove("draft_base");
      r.remove("exported_revision");
    }
    return result;
  }

  private Object provenance(Map<String, Object> r, Map<String, Object> a) {
    var entries = Json.list(a.get("entries"));
    for (Object o : entries) {
      var e = Json.obj(o);
      if (!Set.of("user", "xlsx", "accepted", "llm").contains(Json.str(e, "origin", ""))
          || Json.str(e, "field", "").isBlank()
          || Json.str(e, "source", "").isBlank())
        throw new Problem(
            "provenance_invalid",
            "Angabe benötigt field, origin (user/xlsx/accepted/llm) und source.");
    }
    r.put("provenance", entries);
    return Json.map("entries", entries);
  }

  private Object baseline(Map<String, Object> r) {
    var env = settings.environment(Json.required(r, "source_environment"));
    var accepted = Jenkins.accepted(env, Json.required(r, "identifier"));
    r.put(
        "baseline",
        Json.map(
            "environment",
            r.get("source_environment"),
            "release_id",
            accepted.manifest() == null ? null : accepted.manifest().get("releaseId"),
            "sheet_hash",
            accepted.xml() == null ? null : Json.digest(accepted.xml())));
    if (accepted.xml() != null) {
      Path p = directory(r).resolve("accepted.xtf");
      Json.write(p, accepted.xml());
      store.attach(r, p, "accepted_metadata");
    } else {
      Path folder = Json.inside(settings.topics, topic(r));
      if (Files.isDirectory(folder))
        try (var paths = Files.list(folder)) {
          var xs =
              paths
                  .filter(p -> p.toString().endsWith(".xtf") || p.toString().endsWith(".xml"))
                  .toList();
          if (xs.size() > 1)
            throw new Problem(
                "ambiguous_repository_metadata", "Mehr als ein Datenblatt im Themenordner.");
          if (!xs.isEmpty()) store.attach(r, xs.getFirst(), "accepted_metadata");
        } catch (java.io.IOException e) {
          throw new Problem("repository_unavailable", "Themenordner nicht lesbar.");
        }
    }
    return Json.map(
        "baseline", r.get("baseline"), "available", store.file(r, "accepted_metadata") != null);
  }

  private Object analyze(Map<String, Object> r) {
    Path p = store.file(r, "data");
    if (p == null) return Json.map("skipped", true, "reason", "metadata_only");
    var report = Csv.inspect(p, Json.read(settings.root.resolve("config/rules.json")), null);
    report.put("fingerprint", fingerprint(r, "data"));
    Json.obj(r.get("checks")).put("data", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            settings.root, directory(r).resolve("data-review.html"), "CSV prüfen", report));
    return result;
  }

  public Object stage(Map<String, Object> r, String rel, Path source) {
    Path dest = Json.inside(settings.topics, rel);
    String org = Json.required(r, "organization");
    boolean allowed =
        Set.of(
                "shared/data/offices.xtf",
                "shared/gretl-datenportal-teams.yaml",
                org + "/build.gradle",
                org + "/settings.gradle",
                org + "/gretl-datenportal-job.yaml")
            .contains(rel);
    if (r.get("identifier") != null && dest.getParent().equals(settings.topics.resolve(topic(r))))
      allowed |=
          rel.endsWith(".xtf")
              || rel.endsWith(".xml")
              || rel.endsWith(".ili")
              || dest.getFileName().toString().equals("dataset.gradle");
    if (!allowed)
      throw new Problem(
          "change_not_allowed",
          "Datei gehört nicht zu den erlaubten fachlichen Änderungen.",
          "path",
          rel);
    var changes = Json.obj(r.get("changes"));
    var prev = Json.obj(changes.get(rel));
    String before =
        prev.containsKey("before_sha256")
            ? (String) prev.get("before_sha256")
            : Files.exists(dest) ? Json.sha(dest) : null;
    String sha = Json.sha(source);
    Path staged = directory(r).resolve("changes").resolve(sha + "-" + dest.getFileName());
    try {
      Files.createDirectories(staged.getParent());
      if (!Files.exists(staged)) Files.copy(source, staged);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Änderung kann nicht gesichert werden.");
    }
    String old = Files.exists(dest) ? Json.contents(dest) : "";
    String candidate = Json.contents(staged);
    var change =
        Json.map(
            "path",
            staged.toString(),
            "before_sha256",
            before,
            "sha256",
            sha,
            "diff",
            "--- " + rel + " (Bestand)\n" + old + "\n+++ " + rel + " (Kandidat)\n" + candidate);
    changes.put(rel, change);
    Store.event(r, "stage_change", Json.map("path", rel, "sha256", sha));
    return change;
  }

  private Object validate(Map<String, Object> r) {
    if (Json.str(r, "workflow", "topic").equals("organization"))
      return new Organizations(this).validate(r);
    exportRequired(r);
    var sheet = describe(r);
    Path data = store.file(r, "data");
    var errors = new ArrayList<Object>();
    var warnings = new ArrayList<Object>();
    if (sheet.get("kind").equals("series") && data != null && r.get("issue") == null)
      errors.add(
          Json.map("code", "issue_required", "message", "Ausgabenbezeichnung (SERIES_ID) fehlt."));
    if (sheet.get("kind").equals("dataset") && r.get("issue") != null)
      errors.add(
          Json.map("code", "unexpected_issue", "message", "Ausgabe nur bei Serien angeben."));
    if (sheet.get("kind").equals("series")
        && Json.list(sheet.get("issues")).stream()
                .map(Json::obj)
                .filter(i -> Json.bool(i, "current", false))
                .count()
            != 1)
      errors.add(
          Json.map(
              "code",
              "current_issue",
              "message",
              "Genau eine aktuelle Serienausgabe erforderlich."));
    var attrs = Json.list(sheet.get("attributes"));
    var names =
        attrs.stream()
            .map(Json::obj)
            .map(a -> Json.str(a, "name", "").toLowerCase(Locale.ROOT))
            .toList();
    if (new HashSet<>(names).size() != names.size())
      errors.add(
          Json.map(
              "code",
              "duplicate_attribute",
              "message",
              "Datenblattattribute sind nicht eindeutig."));
    for (Object o : attrs)
      if (Json.str(Json.obj(o), "description", "").isBlank())
        warnings.add(
            Json.map(
                "code",
                "attribute_description",
                "attribute",
                Json.obj(o).get("name"),
                "message",
                "Attributbeschreibung fehlt."));
    errors.addAll(Xml.checkOffices(offices(r), Json.str(sheet, "creator_ref", null)));
    var ili = validator.validate(sheet(r), offices(r), directory(r).resolve("validation"));
    for (Object o : Json.list(ili.get("checks")))
      for (Object msg : Json.list(Json.obj(o).get("messages")))
        (msg.toString().startsWith("Error:") ? errors : warnings)
            .add(Json.map("code", "ilivalidator", "message", msg));
    Map<String, Object> csv = null;
    if (data != null) {
      if (attrs.isEmpty())
        errors.add(
            Json.map(
                "code",
                "attributes_missing",
                "message",
                "Jede Datenspalte benötigt ein Attribut."));
      csv = Csv.inspect(data, Json.read(settings.root.resolve("config/rules.json")), attrs);
      errors.addAll(Json.list(csv.get("errors")));
      warnings.addAll(Json.list(csv.get("warnings")));
    }
    for (String rel : Json.obj(r.get("changes")).keySet())
      if (rel.startsWith(topic(r) + "/")
          && (rel.endsWith(".xtf") || rel.endsWith(".xml"))
          && !Json.sha(repoFile(r, rel)).equals(Json.sha(sheet(r))))
        errors.add(
            Json.map(
                "code",
                "repository_sheet_mismatch",
                "message",
                "PR-Datenblatt und Lieferdatenblatt unterscheiden sich."));
    Map<String, Object> model = null;
    if (r.containsKey("model")) {
      model = new Models(this).validate(r);
      if (!Json.bool(model, "valid", false))
        errors.add(
            Json.map("code", "model_invalid", "message", "Modell/CSV-Prüfung nicht erfolgreich."));
    }
    var report =
        Json.map(
            "valid",
            errors.isEmpty()
                && Json.bool(ili, "valid", false)
                && (csv == null || Json.bool(csv, "valid", false)),
            "errors",
            errors,
            "warnings",
            warnings,
            "ilivalidator",
            ili,
            "csv",
            csv,
            "sheet",
            sheet,
            "model",
            model,
            "changes",
            r.get("changes"),
            "provenance",
            r.get("provenance"));
    report.put("fingerprint", fingerprint(r, "metadata"));
    Json.obj(r.get("checks")).put("metadata", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            settings.root,
            directory(r).resolve("metadata-review.html"),
            "Datenblatt und Modell prüfen",
            report));
    return result;
  }

  private Object approve(Map<String, Object> r, Map<String, Object> a) {
    String gate = Json.required(a, "gate");
    if (!Set.of("data", "metadata", "model", "organization").contains(gate)
        && !gate.startsWith("publish:")) throw new Problem("invalid_gate", "Unbekannte Freigabe.");
    String statement = Json.str(a, "human_statement", "");
    if (statement.isBlank())
      throw new Problem(
          "human_statement_missing", "Ausdrückliche menschliche Freigabe protokollieren.");
    String fp = fingerprint(r, gate);
    if (!fp.equals(a.get("fingerprint")))
      throw new Problem("stale_review", "Prüfstand wurde verändert; erneut prüfen.");
    if (!gate.equals("data") && !Json.str(r, "workflow", "topic").equals("organization"))
      exportRequired(r);
    if (!gate.startsWith("publish:")) {
      var check = Json.obj(Json.obj(r.get("checks")).get(gate));
      if (!Json.bool(check, "valid", false) || !Objects.equals(check.get("fingerprint"), fp))
        throw new Problem("validation_required", "Aktuelle erfolgreiche Prüfung fehlt.");
    }
    if (!gate.equals("data") && store.file(r, "data") != null) require(r, "data");
    if (gate.startsWith("publish:")) {
      require(r, primaryGate(r));
      String env = gate.substring(8);
      settings.environment(env);
      var plan = Json.obj(Json.obj(r.get("publication_plans")).get(env));
      if (!Objects.equals(plan.get("fingerprint"), fp))
        throw new Problem(
            "publication_review_required", "Konkreten Publikationsplan zuerst anzeigen.");
      if (primaryGate(r).equals("metadata")
          && !Json.obj(r.get("deliveries")).entrySet().stream()
              .anyMatch(
                  e ->
                      Json.bool(Json.obj(e.getValue()), "verified", false)
                          && Objects.equals(
                              Json.obj(e.getValue()).get("fingerprint"), fingerprint(r, "metadata"))
                          && Json.str(settings.environment(e.getKey()), "kind", "")
                              .equals("local")))
        throw new Problem(
            "local_test_required", "Erfolgreicher lokaler Gesamttest für diesen Stand fehlt.");
    }
    var record = Json.map("at", Json.now(), "fingerprint", fp, "human_statement", statement);
    Json.obj(r.get("approvals")).put(gate, record);
    Store.event(
        r, "approve", Json.map("gate", gate, "fingerprint", fp, "human_statement", statement));
    return Json.map(
        "approved", gate, "at", record.get("at"), "fingerprint", fp, "human_statement", statement);
  }

  public Map<String, Object> metadata(Map<String, Object> r, String op, Map<String, Object> input) {
    if (!Set.of(
            "create_datasheet",
            "import_xtf",
            "read_datasheet",
            "update_metadata",
            "upsert_attribute",
            "remove_attribute",
            "upsert_issue",
            "remove_issue",
            "validate_datasheet",
            "export_xtf",
            "restore")
        .contains(op))
      throw new Problem("unknown_operation", "Nicht unterstützte Datenblattoperation.");
    if (store.file(r, "data") != null) require(r, "data");
    if (r.containsKey("pending_metadata") && !op.equals("restore"))
      throw new Problem(
          "metadata_uncertain",
          "Letzte MCP-Änderung nicht bestätigt; restore stellt gesicherten Stand her.",
          "pending",
          r.get("pending_metadata"));
    if (op.equals("restore")) {
      if (!r.containsKey("draft")) {
        if (!r.containsKey("pending_metadata"))
          throw new Problem("draft_missing", "Kein gesicherter Entwurf vorhanden.");
        Object pending = r.remove("pending_metadata");
        Store.event(r, "recover_unconfirmed_initial_draft", Json.map("operation", pending));
        return Json.map(
            "restored",
            false,
            "unconfirmed_operation",
            pending,
            "note",
            "Noch kein bestätigter Entwurf. Der dokumentierte Auftrag kann nach Prüfung bewusst erneut ausgeführt werden; keine Lieferung wurde gestartet.");
      }
      var result = McpClients.restore(datasheets, r);
      Object pending = r.remove("pending_metadata");
      r.put("draft", result);
      r.put("exported_revision", null);
      result.put("unconfirmed_operation", pending);
      result.put(
          "note",
          "Gesicherten Stand wiederhergestellt; aktuelle IDs verwenden und unbestätigten Auftrag nach Prüfung erneut ausführen.");
      return result;
    }
    if (Set.of("create_datasheet", "import_xtf").contains(op)) {
      if (op.equals("create_datasheet")
          && !Set.of("dataset", "series").contains(Json.str(input, "kind", "")))
        throw new Problem("invalid_kind", "kind muss dataset oder series sein.");
      r.put("pending_metadata", Json.map("operation", op, "arguments", input, "at", Json.now()));
      persist(r);
      Map<String, Object> result;
      if (op.equals("create_datasheet"))
        result =
            McpClients.create(
                datasheets, Json.required(input, "kind"), Json.obj(input.get("values")), r);
      else {
        String xml = Json.contents(sheet(r));
        result = datasheets.call(op, Json.map("xml", xml));
        r.put("draft_base", xml);
      }
      r.put("draft", result);
      r.put("exported_revision", op.equals("import_xtf") ? result.get("revision") : null);
      r.remove("pending_metadata");
      return result;
    }
    var draft = Json.obj(r.get("draft"));
    if (draft.isEmpty())
      throw new Problem("draft_missing", "Zuerst Datenblatt importieren oder erstellen.");
    var current = datasheets.call("read_datasheet", Json.map("draft_id", draft.get("draft_id")));
    if (!Objects.equals(current.get("revision"), draft.get("revision")))
      throw new Problem("draft_conflict", "Entwurf ausserhalb des Vorgangs bearbeitet.");
    var args = new LinkedHashMap<>(input);
    args.put("draft_id", current.get("draft_id"));
    if (!op.equals("read_datasheet")) args.put("expected_revision", current.get("revision"));
    if (op.equals("export_xtf")) args.put("include_xml", true);
    boolean mutation = !Set.of("read_datasheet", "validate_datasheet", "export_xtf").contains(op);
    if (mutation) {
      r.put("pending_metadata", Json.map("operation", op, "arguments", input, "at", Json.now()));
      persist(r);
    }
    var result = datasheets.call(op, args);
    if (mutation) {
      r.put("draft", result);
      r.remove("pending_metadata");
      Json.obj(r.get("files")).remove("metadata");
      Json.obj(r.get("checks")).remove("metadata");
    }
    if (op.equals("export_xtf")) {
      Path out = directory(r).resolve("export.xtf");
      Json.write(out, Json.required(result, "xml"));
      r.put("draft_base", result.remove("xml"));
      result.put("artifact", store.attach(r, out, "metadata"));
      r.put("exported_revision", current.get("revision"));
    }
    return result;
  }

  private Object apply(Map<String, Object> r) {
    require(r, primaryGate(r));
    if (r.containsKey("model") && primaryGate(r).equals("metadata")) require(r, "model");
    var changes = Json.obj(r.get("changes"));
    for (String rel : changes.keySet()) {
      Path dest = Json.inside(settings.topics, rel);
      String actual = Files.exists(dest) ? Json.sha(dest) : null;
      var c = Json.obj(changes.get(rel));
      if (!Objects.equals(actual, c.get("before_sha256"))
          && !Objects.equals(actual, c.get("sha256"))
          && (!Json.obj(Json.obj(r.get("applied")).get("hashes")).containsKey(rel)
              || !Objects.equals(
                  actual, Json.obj(Json.obj(r.get("applied")).get("hashes")).get(rel))))
        throw new Problem(
            "repository_conflict", "Zieldatei wurde inzwischen verändert.", "path", rel);
      repoFile(r, rel);
    }
    // Restore originals if one of the filesystem writes fails, after all conflicts were checked.
    var originals = new LinkedHashMap<Path, byte[]>();
    try {
      for (String rel : changes.keySet()) {
        Path dest = Json.inside(settings.topics, rel);
        originals.put(dest, Files.exists(dest) ? Files.readAllBytes(dest) : null);
        Files.createDirectories(dest.getParent());
        Files.copy(repoFile(r, rel), dest, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (java.io.IOException e) {
      originals.forEach(
          (p, b) -> {
            try {
              if (b == null) Files.deleteIfExists(p);
              else Files.write(p, b);
            } catch (java.io.IOException ignored) {
            }
          });
      throw new Problem(
          "repository_write_failed", "Übernahme fehlgeschlagen; Rücksicherung wurde versucht.");
    }
    var appliedHashes = Json.map();
    changes.forEach(
        (relative, change) -> appliedHashes.put(relative, Json.obj(change).get("sha256")));
    r.put(
        "applied", Json.map("at", Json.now(), "files", changes.keySet(), "hashes", appliedHashes));
    if (primaryGate(r).equals("organization"))
      r.put(
          "completion",
          Json.map(
              "status",
              "repository_created",
              "jenkins_job",
              false,
              "message",
              "Organisation im Repo angelegt. Jenkins-Job entsteht erst mit dem ersten Thema."));
    return Json.map("applied", changes.keySet(), "completion", r.get("completion"));
  }
}
