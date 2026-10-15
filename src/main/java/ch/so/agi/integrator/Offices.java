package ch.so.agi.integrator;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.TextType;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Shared Office catalog operations; standalone maintenance never creates organization files. */
final class Offices {
  static final String PATH = "shared/data/offices.xtf";
  final Workflow w;

  Offices(Workflow w) {
    this.w = w;
  }

  static boolean standalone(Map<String, Object> r) {
    return "office".equals(r.get("workflow"));
  }

  static void scope(Map<String, Object> r) {
    if (!standalone(r)) return;
    if (Json.obj(r.get("changes")).keySet().stream().anyMatch(p -> !PATH.equals(p))
        || !Json.obj(r.get("files")).isEmpty()
        || r.get("organization") != null
        || r.get("identifier") != null
        || r.get("issue") != null)
      throw new Problem(
          "office_scope", "Office-Pflege darf ausschliesslich shared/data/offices.xtf ändern.");
  }

  static void operation(Map<String, Object> r, String operation) {
    if (standalone(r)
        && !Set.of(
                "status",
                "office",
                "validate",
                "approve",
                "apply_local_changes",
                "repository_plan",
                "prepare_pr",
                "sync_repository")
            .contains(operation))
      throw new Problem(
          "wrong_workflow",
          "Operation gehört nicht zur eigenständigen Office-Pflege.",
          "operation",
          operation);
  }

  List<Map<String, Object>> catalog(Path path) {
    return Xml.all(Xml.parse(path)).stream()
        .filter(e -> Xml.local(e).equals("Office.Office"))
        .map(Xml::structure)
        .toList();
  }

  Object schema(Map<String, Object> args) {
    var rules = officeRules();
    var values = Json.obj(args.get("values"));
    var missing = new ArrayList<String>();
    Json.obj(rules.get("fields"))
        .forEach(
            (field, rule) -> {
              if (Json.bool(Json.obj(rule), "mandatory", false)
                  && Json.str(values, field, "").isBlank()) missing.add(field);
            });
    return Json.map(
        "office_rules",
        rules,
        "existing_offices",
        catalog(w.settings.topics.resolve(PATH)),
        "missing",
        missing);
  }

  Map<String, Object> manifest(Map<String, Object> r) {
    scope(r);
    var change = Json.obj(Json.obj(r.get("changes")).get(PATH));
    String actual = Json.sha(w.settings.topics.resolve(PATH));
    if (!change.isEmpty()
        && !Objects.equals(actual, change.get("before_sha256"))
        && !Objects.equals(actual, change.get("sha256")))
      throw new Problem("repository_conflict", "Office-Katalog seit Vorbereitung verändert.");
    var baseline = Json.obj(r.get("office_baseline"));
    if (!baseline.isEmpty()
        && !Objects.equals(
            Json.sha(Path.of(Json.required(baseline, "path"))), baseline.get("sha256")))
      throw new Problem("change_modified", "Gesicherter Office-Ausgangsstand wurde verändert.");
    var models = new TreeMap<String, Object>();
    for (String dir : w.settings.modelDirectories()) {
      if (dir.startsWith("https://")) {
        models.put(dir, "remote");
        continue;
      }
      Path base = w.settings.path(dir);
      if (Files.isDirectory(base)) {
        try (var paths = Files.walk(base)) {
          paths
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".ili"))
              .forEach(p -> models.put(p.toString(), Json.sha(p)));
        } catch (java.io.IOException e) {
          throw new Problem("office_model_missing", "Modelle nicht lesbar.");
        }
      }
    }
    Path validator = AgentResources.resolve(w.settings.root, "lib/ilivalidator.jar");
    var command =
        w.settings.strings(
            "validator_command", List.of(JavaRuntime.java(), "-jar", validator.toString()));
    var commandArtifacts = new TreeMap<String, Object>();
    for (String arg : command) {
      Path path = w.settings.root.resolve(arg).normalize();
      if (Files.isRegularFile(path)) commandArtifacts.put(path.toString(), Json.sha(path));
    }
    Path integrator = AgentResources.jar(w.settings.root);
    return Json.map(
        "integrator",
        Files.isRegularFile(integrator) ? Json.sha(integrator) : "unavailable",
        "command_artifacts",
        commandArtifacts,
        "version",
        1,
        "candidate",
        Json.sha(w.offices(r)),
        "before",
        change.get("before_sha256"),
        "baseline",
        baseline.get("sha256"),
        "models",
        models,
        "validator",
        Files.isRegularFile(validator) ? Json.sha(validator) : "unavailable",
        "command",
        command,
        "java",
        Runtime.version().toString());
  }

  String fingerprint(Map<String, Object> r) {
    return Json.digest(
        Json.map(
            "workflow", "office", "manifest", manifest(r), "requests", r.get("office_requests")));
  }

  Object validate(Map<String, Object> r) {
    var started = manifest(r);
    Map<String, Object> ili;
    try {
      ili = w.validateXtf(r, null, "office-validation");
    } catch (Problem failure) {
      CheckStates.record(
          w, r, "office", Json.map("valid", false, "errors", List.of(failure.result())), started);
      throw failure;
    }
    var after = catalog(w.offices(r));
    var baseline = Json.obj(r.get("office_baseline"));
    var before = baseline.isEmpty() ? after : catalog(Path.of(Json.required(baseline, "path")));
    var differences = new ArrayList<Object>();
    var fields = Json.obj(officeRules().get("fields")).keySet();
    for (var office : after) {
      var old =
          before.stream()
              .filter(o -> Objects.equals(o.get("identifier"), office.get("identifier")))
              .findFirst()
              .orElse(Json.map());
      for (String field : fields)
        if (!Objects.equals(old.get(field), office.get(field)))
          differences.add(
              Json.map(
                  "identifier",
                  office.get("identifier"),
                  "field",
                  field,
                  "before",
                  old.get(field),
                  "after",
                  office.get(field)));
    }
    var report =
        Json.map(
            "valid",
            Json.bool(ili, "valid", false),
            "errors",
            List.of(),
            "ilivalidator",
            ili,
            "office_changes",
            differences,
            "changes",
            r.get("changes"),
            "message",
            differences.isEmpty()
                ? "Keine Office-Änderungen erforderlich."
                : "Nur der Office-Katalog wird geändert.",
            "fingerprint",
            fingerprint(r),
            "diagnostic_history",
            r.get("diagnostic_history"));
    CheckStates.record(w, r, "office", report, started);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("office-review.html"),
            Reports.Kind.OFFICE,
            null,
            report));
    return result;
  }

  public Map<String, Object> officeRules() {
    var paths = new ArrayList<String>();
    var files = new ArrayList<String>();
    for (String dir : w.settings.modelDirectories())
      if (!dir.startsWith("https://")) {
        paths.add(w.settings.path(dir).toString());
        Path p = w.settings.path(dir).resolve("SO_AGI_DataCatalog_Base_20260529.ili");
        if (Files.isRegularFile(p)) files.add(p.toString());
      }
    if (files.size() != 1)
      throw new Problem(
          "office_model_missing", "Genau ein konfiguriertes Office-Modell erforderlich.");
    var td = ch.interlis.ili2c.Main.compileIliFiles(files, paths, null);
    if (td == null)
      throw new Problem("office_model_invalid", "Konfiguriertes Office-Modell kompiliert nicht.");
    var element = td.getElement("SO_AGI_DataCatalog_Base_20260529.Office.Office");
    if (!(element instanceof AbstractClassDef<?> office))
      throw new Problem("office_model_missing", "Office-Klasse fehlt im konfigurierten Modell.");
    var rules = Json.map();
    var iter = office.getAttributes();
    while (iter.hasNext()) {
      if (!(iter.next() instanceof AttributeDef a)) continue;
      var type = a.getDomainResolvingAliases();
      rules.put(
          a.getName(),
          Json.map(
              "mandatory",
              a.getDomain().isMandatoryConsideringAliases(),
              "max_length",
              type instanceof TextType t ? t.getMaxLength() : null,
              "uri",
              a.isDomainUri()));
    }
    return Json.map(
        "fields",
        rules,
        "model",
        files.getFirst(),
        "model_sha256",
        Json.sha(Path.of(files.getFirst())),
        "unique",
        List.of("identifier", "name", "abbreviation"));
  }

  public Object prepare(Map<String, Object> r, Map<String, Object> values, String mode) {
    if (standalone(r)) {
      scope(r);
      if (!Set.of("create", "update").contains(mode == null ? "" : mode))
        throw new Problem(
            "office_mode_required", "Office-Pflege benötigt mode=create oder mode=update.");
      if (r.containsKey("pull_request") || r.containsKey("pr_submission"))
        throw new Problem(
            "office_pr_pending",
            "Bestehenden PR zuerst abschliessen; neue Änderungen in einem neuen Vorgang.");
    }
    var rules = Json.obj(officeRules().get("fields"));
    for (String field : values.keySet())
      if (!rules.containsKey(field) || !(values.get(field) instanceof String))
        throw new Problem("office_fields", "Unbekanntes Office-Feld.", "field", field);
    for (var entry : rules.entrySet()) {
      String field = entry.getKey(), value = Json.str(values, field, "");
      var rule = Json.obj(entry.getValue());
      if (Json.bool(rule, "mandatory", false) && value.isBlank())
        throw new Problem("office_fields", "Pflichtangabe der Dienststelle fehlt.", "field", field);
      int max = Json.number(rule, "max_length", 0);
      if (max > 0 && value.codePointCount(0, value.length()) > max)
        throw new Problem(
            "office_length", "Office-Wert ist zu lang.", "field", field, "max_length", max);
      if (Json.bool(rule, "uri", false) && !value.isEmpty())
        try {
          URI uri = URI.create(value);
          if (!uri.isAbsolute()
              || uri.getScheme() == null
              || uri.getRawSchemeSpecificPart().isEmpty()) throw new IllegalArgumentException();
        } catch (Exception e) {
          throw new Problem("office_uri", "Absolute URI erforderlich.", "field", field);
        }
    }
    var doc = Xml.parse(w.offices(r));
    var offices = Xml.all(doc).stream().filter(e -> Xml.local(e).equals("Office.Office")).toList();
    String id = Json.required(values, "identifier");
    var existing = offices.stream().filter(e -> id.equals(Xml.text(e, "identifier"))).toList();
    if (existing.size() > 1) throw new Problem("duplicate_office", "Doppelter Office-Identifier.");
    if (standalone(r)) {
      boolean identical =
          !existing.isEmpty()
              && rules.keySet().stream()
                  .allMatch(
                      field ->
                          Objects.equals(
                              Json.str(values, field, ""), Xml.text(existing.getFirst(), field)));
      var previous = Json.obj(Json.obj(r.get("office_requests")).get(id));
      if ("create".equals(mode)
          && !existing.isEmpty()
          && !(identical
              && "create".equals(previous.get("mode"))
              && Objects.equals(previous.get("values"), values)))
        throw new Problem(
            "office_exists",
            "Dienststelle existiert bereits; Korrektur ausdrücklich mit mode=update beauftragen.");
      if ("update".equals(mode) && existing.isEmpty())
        throw new Problem("office_not_found", "Zu korrigierende Dienststelle fehlt.");
      if (identical) return Json.map("changed", false, "identifier", id);
    }
    for (var o : offices)
      if (!id.equals(Xml.text(o, "identifier")))
        for (String field : List.of("name", "abbreviation"))
          if (Objects.equals(values.get(field), Xml.text(o, field)))
            throw new Problem(
                "duplicate_office",
                "Office-Name oder Abkürzung bereits vorhanden.",
                "field",
                field);
    var baskets =
        Xml.all(doc).stream()
            .filter(e -> Xml.BASE.equals(e.getNamespaceURI()) && Xml.local(e).equals("Office"))
            .toList();
    if (baskets.size() != 1)
      throw new Problem("office_catalog_invalid", "Genau ein Office-Basket erforderlich.");
    if (standalone(r)
        && (!r.containsKey("office_baseline")
            || Objects.equals(
                Json.sha(w.settings.topics.resolve(PATH)),
                Json.obj(Json.obj(r.get("applied")).get("hashes")).get(PATH)))) {
      Path baseline = CheckStates.snapshot(w.directory(r), w.settings.topics.resolve(PATH));
      r.put("office_baseline", Json.map("path", baseline.toString(), "sha256", Json.sha(baseline)));
    }
    var node =
        existing.isEmpty() ? doc.createElementNS(Xml.BASE, "Office.Office") : existing.getFirst();
    if (existing.isEmpty()) {
      node.setAttributeNS(Xml.ILI, "i:tid", id);
      baskets.getFirst().appendChild(node);
    }
    while (node.hasChildNodes()) node.removeChild(node.getFirstChild());
    for (String field : rules.keySet()) {
      var c = doc.createElementNS(Xml.BASE, field);
      c.setTextContent(Json.str(values, field, ""));
      node.appendChild(c);
    }
    Path candidate = w.directory(r).resolve("offices-candidate.xtf");
    Json.write(candidate, Xml.serialize(doc));
    var result = w.stage(r, PATH, candidate);
    if (standalone(r)) {
      Json.obj(r.computeIfAbsent("office_requests", k -> Json.map()))
          .put(id, Json.map("mode", mode, "values", new LinkedHashMap<>(values)));
      r.remove("completion");
    }
    return result;
  }
}
