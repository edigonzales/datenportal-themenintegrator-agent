package ch.so.agi.integrator;

import java.net.URI;
import java.nio.file.*;
import java.util.*;

public final class Models {
  static final String BEGIN = "// BEGIN datenportal-themenintegrator CsvValidator",
      END = "// END datenportal-themenintegrator CsvValidator";
  final Workflow w;

  public Models(Workflow w) {
    this.w = w;
  }

  static String profile(String value) {
    if (!Set.of("CORE", "SO-GIS", "SO-DATA").contains(value))
      throw new Problem(
          "model_profile", "Zulässige Regelprofile: CORE, SO-GIS, SO-DATA.", "profile", value);
    return value;
  }

  static String name(Map<String, Object> m, String key, String fallback) {
    String s = Json.str(m, key, fallback);
    if (s == null || !s.matches("[A-Za-z][A-Za-z0-9_]*"))
      throw new Problem("model_identity", "INTERLIS-Identifier erforderlich.", "field", key);
    return s;
  }

  String contract(Map<String, Object> sheet) {
    return Json.digest(sheet.get("attributes"));
  }

  public Object derive(Map<String, Object> r, Map<String, Object> args) {
    if (Json.str(r, "workflow", "topic").equals("organization"))
      throw new Problem("wrong_workflow", "Organisationsvorgang modelliert keine CSV.");

    var confirmations = ModelArguments.confirmations(args.get("confirmations"));
    var proposals = ModelArguments.proposals(args.get("range_proposals"));
    ModelArguments.keys(
        Json.obj(args.get("identity")),
        "identity",
        "name",
        "uri",
        "version",
        "rule_profile",
        "model_purpose",
        "ili_version",
        "topic",
        "class",
        "language",
        "title",
        "short_description",
        "technical_contact",
        "further_information",
        "class_description",
        "domains",
        "units",
        "imports",
        "constraints",
        "semantics_confirmed");
    if (!Json.bool(Json.obj(r.get("model")), "authoring_valid", false))
      ModelArguments.normalizeStored(r);
    ModelRepairs.requireReference(r, args);

    Path csv = w.requiredFile(r, "data");
    w.require(r, "data");
    w.exportRequired(r);
    var sheet = w.describe(r);
    var identity = new LinkedHashMap<>(Json.obj(args.get("identity")));

    String profile =
        Json.str(
            identity, "rule_profile", Json.str(Json.obj(r.get("model")), "profile", "SO-DATA"));
    profile(profile);
    identity.put("rule_profile", profile);

    var missing = new ArrayList<>();
    for (String field :
        (profile.equals("SO-DATA")
            ? List.of("name", "uri", "version")
            : List.of("name", "uri", "version", "technical_contact", "title", "short_description")))
      if (Json.str(identity, field, "").isBlank()) missing.add(field);
    if (!missing.isEmpty())
      return Json.map(
          "status",
          "NEEDS_INPUT",
          "missing",
          missing,
          "defaults",
          Json.map("ili_version", "2.4", "rule_profile", profile, "model_purpose", "VALIDATION"));
    String modelName = name(identity, "name", null),
        topic = name(identity, "topic", "Data"),
        clazz = name(identity, "class", "Observation");
    try {
      if (!URI.create(Json.required(identity, "uri")).isAbsolute())
        throw new IllegalArgumentException();
      var date = java.time.LocalDate.parse(Json.required(identity, "version"));
      if (profile.equals("SO-DATA")
          && (!Json.required(identity, "version").matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
              || !modelName.endsWith(
                  "_" + date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))))
        throw new IllegalArgumentException();

    } catch (Exception e) {
      throw new Problem(
          "model_identity",
          "Absolute Modell-URI und ISO-Versionsdatum erforderlich; bei SO-DATA muss der Datumssuffix des Namens dem Versionsdatum entsprechen. Widersprüche menschlich klären, kein Datum still ändern.");
    }
    if (!Json.str(identity, "ili_version", "2.4").equals("2.4"))
      throw new Problem("unsupported_model_version", "Erste Umsetzung unterstützt INTERLIS 2.4.");
    String purpose = Json.str(identity, "model_purpose", "VALIDATION");
    var attrs = Json.list(sheet.get("attributes"));
    var inspection =
        Csv.inspect(
            csv, Json.read(AgentResources.resolve(w.settings.root, "config/rules.json")), attrs);
    if (!Json.bool(inspection, "valid", false))
      throw new Problem(
          "csv_contract_invalid", "CSV entspricht nicht dem Datenblatt.", "report", inspection);
    if (sheet.get("kind").equals("series")) {
      var root =
          Xml.datasets(Xml.parse(w.sheet(r))).stream()
              .filter(n -> Objects.equals(sheet.get("identifier"), Xml.text(n, "identifier")))
              .findFirst()
              .orElseThrow();
      var parent = Xml.attributes(root);
      for (var issue : Xml.all(root))
        if (Xml.local(issue).equals("DatasetIssue")) {
          var issueAttrs = Xml.attributes(issue);
          if (issueAttrs.isEmpty()) issueAttrs = parent;
          if (!issueAttrs.equals(attrs))
            return Json.map(
                "status",
                "NEEDS_INPUT",
                "missing",
                List.of(
                    "Serienausgaben haben unterschiedliche Datenverträge; gemeinsame Struktur klären."));
        }
    }
    var columnNames = attrs.stream().map(Json::obj).map(a -> Json.required(a, "name")).toList();
    for (String column : proposals.keySet())
      if (!columnNames.contains(column))
        throw new Problem("range_proposal_invalid", "Unbekannte CSV-Spalte.", "column", column);
    for (String column : confirmations.keySet())
      if (!columnNames.contains(column))
        throw ModelArguments.invalid("confirmations." + column, "Unbekannte CSV-Spalte.");
    var typed = new ArrayList<Object>();

    var mappings = new ArrayList<Object>();
    var questions = new ArrayList<>();
    for (Object o : attrs) {
      var a = Json.obj(o);
      String column = Json.required(a, "name");
      var confirmation = Json.obj(confirmations.get(column));
      for (Object prior : Json.list(Json.obj(r.get("model")).get("mapping"))) {
        var previous = Json.obj(prior);
        var saved =
            ModelArguments.confirmation(Json.obj(previous.get("confirmed")), "stored." + column);
        if (column.equals(previous.get("column")) && Json.bool(saved, "confirmed", false)) {
          if (!confirmation.isEmpty()
              && !Json.digest(ModelArguments.semantics(saved))
                  .equals(Json.digest(ModelArguments.semantics(confirmation))))
            throw new Problem(
                "confirmed_semantics_conflict",
                "Bestätigte Typsemantik darf nicht still ersetzt werden.",
                "column",
                column);
          confirmation = saved;
        }
      }

      String type = Json.str(a, "data_type", "").toUpperCase(Locale.ROOT);
      var observed =
          Json.list(inspection.get("columns")).stream()
              .map(Json::obj)
              .filter(s -> column.equals(s.get("name")))
              .findFirst()
              .orElseThrow();
      if (Json.bool(observed, "leading_zero", false)
          && !type.equals("TEXT")
          && !Json.bool(confirmation, "numeric_identifier_confirmed", false))
        questions.add(
            column
                + ": führende Nullen; numerische Interpretation bestätigen oder TEXT verwenden.");
      if (!profile.equals("SO-DATA") && Json.str(a, "description", "").isBlank())
        questions.add(column + ": fachliche Beschreibung fehlt.");
      if (!Json.str(a, "code_list", "").isBlank() && !confirmation.containsKey("type_spec"))
        questions.add(column + ": Codeliste ist belegt, aber Werte/Domain noch nicht bestätigt.");
      var typeSpec = Json.obj(confirmation.get("type_spec"));
      Map<String, Object> proposal = Json.map();
      if (proposals.containsKey(column)) {
        proposal = ModelRanges.proposal(column, Json.obj(proposals.get(column)), observed, type);
        if (!typeSpec.isEmpty()
            && !Json.digest(typeSpec).equals(Json.digest(proposal.get("type_spec"))))
          throw new Problem(
              "range_proposal_conflict",
              "Vorschlag widerspricht vorhandener Spezifikation.",
              "column",
              column);
        if (typeSpec.isEmpty()) typeSpec = Json.obj(proposal.get("type_spec"));
      }
      if (Set.of("INTEGER", "DECIMAL", "NUMERIC").contains(type) && typeSpec.isEmpty())
        questions.add(
            column
                + ": konkreten numerischen Wertebereich und Genauigkeit bestätigen. INTERLIS verlangt min/max für einen instanziierbaren Zahlentyp; beobachtete Grenzen werden nicht automatisch übernommen.");
      if (type.equals("TEXT")
          && typeSpec.isEmpty()
          && Set.of("SO-GIS", "SO-DATA").contains(profile))
        questions.add(
            column
                + ": maximale Textlänge für das gewählte Regelprofil bestätigen; beobachtete Längen sind keine fachliche Grenze.");
      if (!typeSpec.isEmpty() && proposal.isEmpty() && !Json.bool(confirmation, "confirmed", false))
        questions.add(column + ": zusätzliche Typsemantik bestätigen.");
      if (typeSpec.isEmpty())
        typeSpec =
            switch (type) {
              case "TEXT" -> Json.map("baseType", Json.map("kind", "TEXT"));
              case "INTEGER", "DECIMAL", "NUMERIC" ->
                  Json.map("baseType", Json.map("kind", "NUMERIC"));
              case "BOOLEAN" -> Json.map("baseType", Json.map("kind", "BOOLEAN"));
              case "DATE" -> Json.map("domainFqn", "INTERLIS.XMLDate");
              case "DATETIME" -> Json.map("domainFqn", "INTERLIS.XMLDateTime");
              default ->
                  throw new Problem(
                      "unsupported_type", "Nicht unterstützter Datenblatttyp.", "column", column);
            };
      String doc = Json.str(a, "description", "");
      if (!Json.str(a, "unit", "").isBlank()) doc += " Einheit: " + a.get("unit") + ".";
      typed.add(
          Json.map(
              "name",
              column,
              "mandatory",
              Json.bool(a, "mandatory", false),
              "collection",
              "NONE",
              "iliDoc",
              doc,
              "typeSpec",
              ModelArguments.mcpSpec(typeSpec)));
      mappings.add(
          Json.map(
              "column",
              column,
              "attribute",
              modelName + "." + topic + "." + clazz + "." + column,
              "datasheet",
              a,
              "type_spec",
              typeSpec,
              "observed",
              observed,
              "range_proposal",
              proposal,
              "confirmed",
              confirmation,
              "origin",
              "Datenblatt; Beobachtungen sind Vorschläge, keine fachlichen Wertebereiche/Schlüssel."));
    }
    for (String field : List.of("domains", "units", "constraints", "imports"))
      if (identity.containsKey(field) && !Json.bool(identity, "semantics_confirmed", false))
        questions.add(field + ": zusätzliche Fachsemantik ausdrücklich bestätigen.");
    if (!questions.isEmpty())
      return Json.map("status", "NEEDS_INPUT", "open_questions", questions, "mapping", mappings);
    var spec =
        Json.map(
            "name",
            modelName,
            "uri",
            identity.get("uri"),
            "version",
            identity.get("version"),
            "iliVersion",
            "2.4",
            "language",
            Json.str(identity, "language", "de"),
            "iliDoc",
            identity.get("short_description"),
            "metaAttributes",
            List.of(
                Json.map("name", "title", "value", identity.get("title")),
                Json.map("name", "shortDescription", "value", identity.get("short_description")),
                Json.map("name", "technicalContact", "value", identity.get("technical_contact")),
                Json.map(
                    "name",
                    "furtherInformation",
                    "value",
                    Json.str(identity, "further_information", Json.required(identity, "uri")))),
            "topics",
            List.of(
                Json.map(
                    "name",
                    topic,
                    "oidDomainFqn",
                    "INTERLIS.ANYOID",
                    "classes",
                    List.of(
                        Json.map(
                            "name",
                            clazz,
                            "iliDoc",
                            Json.str(
                                identity,
                                "class_description",
                                Json.str(
                                    Json.obj(sheet.get("fields")),
                                    "description",
                                    Json.str(identity, "short_description", ""))),
                            "attributes",
                            typed,
                            "constraints",
                            Json.list(identity.get("constraints")))))));
    for (String field : List.of("domains", "units", "imports"))
      if (identity.containsKey(field)) spec.put(field, identity.get(field));
    if (profile.equals("SO-DATA")) spec = new LinkedHashMap<>(Json.obj(withoutDescriptions(spec)));
    Map<String, Object> result;
    String operation;
    Path existing = w.store.file(r, "model");
    if (r.containsKey("model") && !Json.bool(Json.obj(r.get("model")), "authoring_valid", false))
      existing = null;
    if (existing == null) {
      Path candidate = w.repoFile(r, w.topic(r) + "/" + modelName + ".ili");
      if (Files.isRegularFile(candidate)) existing = candidate;
    }
    Map<String, Object> callArgs = Json.map("modelPurpose", purpose, "ruleProfile", profile);
    if (existing != null) {
      if (Json.list(args.get("changes")).isEmpty())
        return Json.map(
            "status",
            "NEEDS_INPUT",
            "open_questions",
            List.of(
                "Vorhandenes Modell benötigt einen ausdrücklich beauftragten typisierten Änderungsbatch (changes)."),
            "model_path",
            existing.toString());
      operation = "applyIliModelChanges";
      callArgs.put("modelText", Json.contents(existing));
      callArgs.put(
          "request",
          Json.map(
              "changes",
              args.get("changes"),
              "allowPotentiallyBreaking",
              Json.bool(args, "allow_breaking", false)));
    } else {
      operation = "authorIliModel";
      callArgs.put("spec", spec);
    }
    var priorRepair = Json.read(Json.text(Json.obj(r.get("model_repair"))));
    ModelRepairs.reserve(w, r, args);
    r.put(
        "pending_model", Json.map("operation", operation, "arguments", callArgs, "at", Json.now()));
    w.persist(r);
    try {
      result = w.interlis.call(operation, callArgs);
    } catch (Problem error) {
      if (Json.str(Json.obj(error.details.get("response")), "message", "")
          .contains("input validation failed")) {
        r.remove("pending_model");
        if (priorRepair.isEmpty()) r.remove("model_repair");
        else r.put("model_repair", priorRepair);
        throw new Problem(
            "model_arguments_invalid",
            "Fach-MCP hat die Argumentdarstellung abgewiesen; keine Modellprüfung ausgeführt. Argumente korrigieren, kein Reset.",
            "response",
            error.details.get("response"));
      }
      var check = Diagnostics.record(w, r, "ili2c", "ERROR", error.result(), false);
      error.details.put("diagnostics", check);
      throw error;
    }
    if ("INVALID_SPEC".equals(result.get("status"))
        || "INVALID_SPEC".equals(result.get("reasonCode"))) {
      r.remove("pending_model");
      if (priorRepair.isEmpty()) r.remove("model_repair");
      else r.put("model_repair", priorRepair);
      Diagnostics.record(w, r, "model_arguments", "INVALID_SPEC", result, false);
      return Json.map(
          "status",
          "INVALID_SPEC",
          "candidate",
          true,
          "candidate_created",
          false,
          "evidence",
          result,
          "diagnostic_history",
          r.get("diagnostic_history"),
          "next",
          "Argumente gemäss operation_schema(derive_model) korrigieren; bestätigte Fachwerte erhalten. Kein reset_model_repair.");
    }
    Diagnostics.record(
        w, r, "ili2c", compilerStatus(Json.obj(result.get("afterReview")), result), result, true);

    r.remove("pending_model");
    String text =
        Json.str(result, "updatedModelText", Json.str(result, "candidateModelText", null));
    if (text != null && authoringValid(result)) ModelIdentity.verify(text, identity);
    var model =
        Json.map(
            "identity",
            identity,
            "mapping",
            mappings,
            "contract_sha256",
            contract(sheet),
            "source_data_sha256",
            Json.sha(csv),
            "source_metadata_sha256",
            Json.sha(w.sheet(r)),
            "evidence",
            result,
            "purpose",
            purpose,
            "profile",
            profile,
            "authoring_valid",
            authoringValid(result),
            "task_configuration",
            Json.map("header", true, "encoding", "UTF-8", "separator", ";", "quote", "\""));
    r.put("model", model);
    if (text != null) {
      Path out = w.directory(r).resolve("model").resolve(modelName + ".ili");
      Json.write(out, text);
      w.store.attach(r, out, "model");
      model.put("evidence_model_sha256", Json.sha(out));
      if (authoringValid(result)) {
        w.stage(r, w.topic(r) + "/" + modelName + ".ili", out);
        String taskRelative = w.topic(r) + "/dataset.gradle";
        Path taskExisting = w.repoFile(r, taskRelative);
        String original = Files.exists(taskExisting) ? Json.contents(taskExisting) : "";
        Path task = w.directory(r).resolve("model/dataset.gradle");
        Json.write(task, task(original, Json.required(r, "identifier"), modelName, Json.sha(out)));
        w.stage(r, taskRelative, task);
        model.put("dataset_task_sha256", Json.sha(task));
      }
    }
    return Json.map(
        "status",
        result.get("status"),
        "candidate",
        !authoringValid(result),
        "diagnostic_history",
        r.get("diagnostic_history"),
        "repair",
        r.get("model_repair"),
        "model",
        model,
        "next",
        authoringValid(result)
            ? "validate_model und validate"
            : "Fach-MCP-Diagnosen/offene Fragen klären",
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("model-candidate.html"),
            Reports.Kind.MODEL_CANDIDATE,
            Json.str(r, "identifier", null),
            Json.map(
                "model_text",
                text,
                "model",
                model,
                "changes",
                r.get("changes"),
                "diagnostic_history",
                r.get("diagnostic_history"),
                "repair",
                r.get("model_repair"))));
  }

  static Object withoutDescriptions(Object value) {
    if (value instanceof Map<?, ?> map) {
      var copy = new LinkedHashMap<String, Object>();
      map.forEach(
          (key, child) -> {
            if (!Set.of("iliDoc", "metaAttributes").contains(key))
              copy.put(key.toString(), withoutDescriptions(child));
          });
      return copy;
    }
    if (value instanceof List<?> list)
      return list.stream().map(Models::withoutDescriptions).toList();
    return value;
  }

  String metadataRelative(Map<String, Object> r) {

    var selected = Json.obj(r.get("repository_datasheet"));
    if (!selected.isEmpty()) return Json.required(selected, "relative_path");
    var resolver = new RepositoryDatasheets(w);
    if (!Json.list(resolver.discover(r).get("candidates")).isEmpty())
      return Json.required(resolver.choose(r, null), "relative_path");
    return w.topic(r) + "/datasheet.xtf";
  }

  public Object prepareReference(Map<String, Object> r) {
    w.exportRequired(r);
    var model = Json.obj(r.get("model"));
    if (model.isEmpty()) throw new Problem("model_missing", "Modell zuerst ableiten.");
    if (w.store.file(r, "data") != null) w.require(r, "data");
    String modelName = Json.required(Json.obj(model.get("identity")), "name");
    if (Objects.equals(model.get("prepared_reference_fingerprint"), referenceFingerprint(r)))
      return Json.map("changed", false, "next_operation", "validate_model(scope=integration)");
    var check = Json.obj(Json.obj(r.get("checks")).get("model_preparation"));
    if (!Json.bool(check, "valid", false)
        || !Objects.equals(check.get("fingerprint"), w.fingerprint(r, "model")))
      throw new Problem(
          "validation_required",
          "Zuerst validate_model(scope=model) für den aktuellen Stand erfolgreich ausführen.");
    String relative = metadataRelative(r);
    var source = Json.obj(r.get("repository_datasheet"));
    if (!source.isEmpty()) {
      var selected = new RepositoryDatasheets(w).choose(r, relative);
      if (!Objects.equals(source.get("sha256"), selected.get("sha256")))
        throw new Problem(
            "repository_datasheet_changed",
            "Repository-Datenblatt seit Auswahl verändert; Quelle und Modellgrundlage erneut prüfen.");
    }
    if (!Objects.equals(Json.obj(w.describe(r).get("fields")).get("model"), modelName)) {
      if (!r.containsKey("draft")) w.metadata(r, "import_xtf", Json.map());
      w.metadata(r, "update_metadata", Json.map("values", Json.map("model", modelName)));
      w.metadata(r, "export_xtf", Json.map());
    }
    model.put("review_metadata_sha256", Json.sha(w.sheet(r)));
    w.stage(r, relative, w.sheet(r));
    model.put("prepared_reference_fingerprint", referenceFingerprint(r));
    return Json.map(
        "changed",
        true,
        "relative_path",
        relative,
        "metadata_sha256",
        Json.sha(w.sheet(r)),
        "next_operation",
        "validate_model(scope=integration)");
  }

  String referenceFingerprint(Map<String, Object> r) {
    return Json.digest(
        Json.map(
            "model",
            Json.sha(w.requiredFile(r, "model")),
            "metadata",
            Json.sha(w.sheet(r)),
            "data",
            w.store.file(r, "data") == null ? null : Json.sha(w.store.file(r, "data")),
            "changes",
            r.get("changes")));
  }

  static boolean authoringValid(Map<String, Object> result) {
    var review = Json.obj(result.get("afterReview"));
    return Set.of("GENERATED", "APPLIED").contains(Json.str(result, "status", ""))
        && Json.bool(result, "complete", false)
        && !Boolean.FALSE.equals(result.get("proofVerified"))
        && compilerPassed(review, result)
        && Json.bool(review, "validForAutomatedRules", false)
        && Json.list(result.get("openQuestions")).isEmpty();
  }

  static boolean compilerPassed(Map<String, Object> review, Map<String, Object> result) {
    if (review.containsKey("compilerValid")) return Json.bool(review, "compilerValid", false);
    return Objects.equals(
        Json.obj(Json.obj(result.get("evidence")).get("compiler")).get("status"), "PASSED");
  }

  static String compilerStatus(Map<String, Object> review, Map<String, Object> result) {
    if (compilerPassed(review, result)) return "PASSED";
    if (Boolean.FALSE.equals(review.get("compilerValid"))
        || "FAILED"
            .equals(Json.obj(Json.obj(result.get("evidence")).get("compiler")).get("status")))
      return "FAILED";
    return "NOT_CHECKED";
  }

  public static String task(String original, String identifier, String model) {
    return task(original, identifier, model, "");
  }

  public static String task(String original, String identifier, String model, String modelSha) {
    int start = original.indexOf(BEGIN), end = original.indexOf(END);
    if ((start >= 0) != (end >= 0) || end >= 0 && end < start)
      throw new Problem(
          "dataset_task_conflict", "Unvollständiger Integrator-Block in dataset.gradle.");
    if (start >= 0)
      original = original.substring(0, start) + original.substring(end + END.length());
    if (original.contains("validateThemenCsv") || original.contains("stageThemenCsv"))
      throw new Problem(
          "dataset_task_conflict", "Vorhandene Taskdefinition muss ausdrücklich geklärt werden.");
    return original.stripTrailing()
        + "\n\n"
        + BEGIN
        + "\n"
        + """
                def integratorCsvInput = providers.gradleProperty('dataFile').orElse('')
                // Jenkins file parameters have no extension. CsvValidator requires .csv.
                def integratorValidationCsv = layout.buildDirectory.file('integrator-validation/input.csv')
                def integratorStageCsv = tasks.register('stageThemenCsv', Copy) {
                    onlyIf('CSV-Datenlieferung vorhanden') { !integratorCsvInput.get().trim().isEmpty() }
                    from(integratorCsvInput.map { value -> value.trim().isEmpty() ? [] : [file(value)] })
                    into(layout.buildDirectory.dir('integrator-validation'))
                    rename { 'input.csv' }
                }
                def integratorCsvValidator = project.plugins.getPlugin('ch.so.agi.gretl').class.classLoader.loadClass('ch.so.agi.gretl.tasks.CsvValidator')
                tasks.register('validateThemenCsv', integratorCsvValidator) {
                    dependsOn(integratorStageCsv)
                    onlyIf('CSV-Datenlieferung vorhanden; Metadatenlieferungen überspringen') { !integratorCsvInput.get().trim().isEmpty() }
                    dataFiles(integratorCsvInput.map { value -> value.trim().isEmpty() ? [] : [integratorValidationCsv.get().asFile] })
                    modelNames(%s)
                    modelDirectories(file(%s).absolutePath)
                    firstLineIsHeader.set(true)
                    valueSeparator.set(';')
                    valueDelimiter.set('"')
                    encoding.set('UTF-8')
                    logFile(layout.buildDirectory.file('integrator-validation/csv.log'))
                    failOnError.set(true)
                    doFirst {
                        if (!file(integratorCsvInput.get()).isFile()) throw new GradleException('CSV-Lieferdatei fehlt')
                        if (java.nio.file.Files.mismatch(file(integratorCsvInput.get()).toPath(), integratorValidationCsv.get().asFile.toPath()) != -1L) throw new GradleException('CSV-Prüfkopie ist nicht bytegleich')
                        def expectedModelSha = %s
                        def modelFile = file(%s)
                        if (expectedModelSha && java.security.MessageDigest.getInstance('SHA-256').digest(modelFile.bytes).encodeHex().toString() != expectedModelSha) throw new GradleException('INTERLIS-Modell entspricht nicht dem freigegebenen Prüfstand')
                    }
                    doLast { println('INTEGRATOR_CSV_VALIDATED=' + validationOk) }
                }
                tasks.configureEach { task ->
                    if (task.name == 'preparePublicationWorkspace') task.dependsOn('validateThemenCsv')
                }
                // The publication validator also needs the reviewed topic-local model.
                gradle.projectsEvaluated {
                    tasks.matching { it.name == 'validateDeliveredCsv' }.configureEach {
                        def sharedModels = System.getenv('DATENPORTAL_MODELS_DIR') ?: file('../shared/models').absolutePath
                        modelDirectories(file(%s).absolutePath + ';' + sharedModels)
                    }
                }
                """
            .formatted(
                Workspace.groovy(model),
                Workspace.groovy(identifier),
                Workspace.groovy(modelSha),
                Workspace.groovy(identifier + "/" + model + ".ili"),
                Workspace.groovy(identifier))
        + END
        + "\n";
  }

  public Map<String, Object> validate(Map<String, Object> r) {
    return validate(r, "integration");
  }

  public Map<String, Object> validate(Map<String, Object> r, String scope) {
    if (!Set.of("model", "integration").contains(scope))
      throw new Problem("invalid_arguments", "scope=model oder integration erforderlich.");

    var model = Json.obj(r.get("model"));
    if (model.isEmpty()) throw new Problem("model_missing", "Modell zuerst ableiten.");
    String profile = profile(Json.str(model, "profile", ""));
    w.exportRequired(r);
    Path ili = w.requiredFile(r, "model");
    var sheet = w.describe(r);
    ModelIdentity.verify(Json.contents(ili), Json.obj(model.get("identity")));
    var errors = new ArrayList<Object>();
    if (!Objects.equals(model.get("contract_sha256"), contract(sheet)))
      errors.add(
          Json.map(
              "code",
              "model_contract_changed",
              "message",
              "Datenblatt-Vertrag seit Ableitung verändert."));
    Path data = w.store.file(r, "data");
    if (data != null && !Objects.equals(model.get("source_data_sha256"), Json.sha(data)))
      errors.add(
          Json.map(
              "code",
              "model_data_changed",
              "message",
              "CSV seit Ableitung verändert; erneut ableiten."));
    if (!Objects.equals(Json.sha(ili), model.get("evidence_model_sha256"))) {
      Map<String, Object> result;
      try {
        result =
            w.interlis.call(
                "reviewIliModel",
                Json.map(
                    "modelText",
                    Json.contents(ili),
                    "modelPurpose",
                    model.get("purpose"),
                    "ruleProfile",
                    profile));
      } catch (Problem error) {
        error.details.put(
            "diagnostics", Diagnostics.record(w, r, "ili2c", "ERROR", error.result(), false));
        throw error;
      }
      Diagnostics.record(w, r, "ili2c", compilerStatus(result, result), result, true);
      model.put("external_review", result);
      model.put("evidence_model_sha256", Json.sha(ili));
      model.put(
          "authoring_valid",
          compilerPassed(result, result) && Json.bool(result, "validForAutomatedRules", false));
      // A separately changed model needs new constraint proofs through a high-level change
      // operation.
      String declarations =
          Json.contents(ili)
              .replaceAll("(?s)/\\*.*?\\*/", "")
              .replaceAll("(?m)!![^\\n]*", "")
              .replaceAll("\"[^\"]*\"", "");
      if (declarations.matches("(?s).*\\b(CONSTRAINT|UNIQUE|EXISTENCE)\\b.*"))
        model.put("authoring_valid", false);
      if (Json.bool(model, "authoring_valid", false)) {
        String name = Json.required(Json.obj(model.get("identity")), "name");
        w.stage(r, w.topic(r) + "/" + name + ".ili", ili);
        String relative = w.topic(r) + "/dataset.gradle";
        Path candidate = w.directory(r).resolve("model/dataset.gradle");
        Json.write(
            candidate,
            task(
                Json.contents(w.repoFile(r, relative)),
                Json.required(r, "identifier"),
                name,
                Json.sha(ili)));
        w.stage(r, relative, candidate);
        model.put("dataset_task_sha256", Json.sha(candidate));
      }
    }
    if (!Json.bool(model, "authoring_valid", false))
      errors.add(
          Json.map(
              "code",
              "model_candidate",
              "message",
              "Compiler-/Regelprüfung oder automatische Constraint-Nachweise sind unvollständig."));
    String modelName = Json.required(Json.obj(model.get("identity")), "name");
    if (scope.equals("integration")
        && !Objects.equals(Json.obj(sheet.get("fields")).get("model"), modelName))
      errors.add(
          Json.map(
              "code",
              "model_reference",
              "message",
              "Datenblatt-Modellreferenz fehlt oder stimmt nicht überein."));
    String iliRelative = w.topic(r) + "/" + modelName + ".ili",
        taskRelative = w.topic(r) + "/dataset.gradle";
    if (!Files.isRegularFile(w.repoFile(r, taskRelative))
        || !Objects.equals(Json.sha(w.repoFile(r, taskRelative)), model.get("dataset_task_sha256")))
      errors.add(
          Json.map(
              "code",
              "model_task_changed",
              "message",
              "CSV-Taskdefinition seit Modellableitung verändert; erneut ableiten und prüfen."));
    if (!Files.isRegularFile(w.repoFile(r, iliRelative))
        || !Objects.equals(Json.sha(w.repoFile(r, iliRelative)), Json.sha(ili)))
      errors.add(
          Json.map(
              "code",
              "repository_model_mismatch",
              "message",
              "Themenmodell und Prüfmodell unterscheiden sich."));
    Map<String, Object> csvValidation;
    if (data == null)
      csvValidation = Json.map("valid", true, "skipped", true, "reason", "metadata_only");
    else if (!errors.isEmpty())
      csvValidation = Json.map("valid", false, "skipped", true, "reason", "model_invalid");
    else {
      csvValidation =
          w.csvValidation.validate(data, ili, modelName, w.directory(r).resolve("model-csv"));
    }
    if (!Json.bool(csvValidation, "skipped", false))
      Diagnostics.record(
          w,
          r,
          "csv-validation",
          Json.bool(csvValidation, "valid", false) ? "PASSED" : "FAILED",
          csvValidation,
          true);
    if (csvValidation.containsKey("log")
        && !Json.bool(csvValidation, "valid", false)
        && Json.contents(Path.of(Json.required(csvValidation, "log")))
            .contains("invalid format of datetime value"))
      errors.add(
          Json.map(
              "code",
              "datetime_validator_limit",
              "message",
              "Die lokale ilivalidator-Prüfung für INTERLIS.XMLDateTime akzeptiert den gültigen CSV-Zeitzonenoffset +01:00 nicht. Eine passende Modelldomain oder separat beauftragte Komponentenänderung ist erforderlich; Daten werden nicht still verändert."));
    var xtf = w.validateXtf(r, w.sheet(r), "model-xtf-validation");
    var report =
        Json.map(
            "valid",
            errors.isEmpty()
                && Json.bool(csvValidation, "valid", false)
                && Json.bool(xtf, "valid", false),
            "errors",
            errors,
            "csv_validation",
            csvValidation,
            "ilivalidator",
            xtf,
            "model_text",
            Json.contents(ili),
            "model",
            model,
            "model_sha256",
            Json.sha(ili),
            "task_sha256",
            Files.exists(w.repoFile(r, taskRelative))
                ? Json.sha(w.repoFile(r, taskRelative))
                : null,
            "metadata_sha256",
            Json.sha(w.sheet(r)),
            "changes",
            r.get("changes"));
    if (Json.bool(report, "valid", false)) ModelRepairs.passed(r);
    report.put("scope", scope);
    report.put("approvable", scope.equals("integration"));
    report.put("diagnostic_history", r.get("diagnostic_history"));
    report.put("repair", r.get("model_repair"));
    report.put("fingerprint", w.fingerprint(r, "model"));

    Json.obj(r.get("checks")).put(scope.equals("model") ? "model_preparation" : "model", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r)
                .resolve(
                    scope.equals("model") ? "model-preparation-review.html" : "model-review.html"),
            Reports.Kind.MODEL,
            Json.str(r, "identifier", null),
            report));
    return result;
  }
}
