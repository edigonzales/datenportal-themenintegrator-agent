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
    Path csv = w.requiredFile(r, "data");
    w.require(r, "data");
    w.exportRequired(r);
    var sheet = w.describe(r);
    var identity = Json.obj(args.get("identity"));
    var confirmations = Json.obj(args.get("confirmations"));
    var missing = new ArrayList<>();
    for (String field :
        List.of("name", "uri", "version", "technical_contact", "title", "short_description"))
      if (Json.str(identity, field, "").isBlank()) missing.add(field);
    if (!missing.isEmpty())
      return Json.map(
          "status",
          "NEEDS_INPUT",
          "missing",
          missing,
          "defaults",
          Json.map("ili_version", "2.4", "rule_profile", "SO", "model_purpose", "VALIDATION"));
    String modelName = name(identity, "name", null),
        topic = name(identity, "topic", "Data"),
        clazz = name(identity, "class", "Observation");
    try {
      if (!URI.create(Json.required(identity, "uri")).isAbsolute())
        throw new IllegalArgumentException();
      java.time.LocalDate.parse(Json.required(identity, "version"));
    } catch (Exception e) {
      throw new Problem(
          "model_identity", "Absolute Modell-URI und ISO-Versionsdatum erforderlich.");
    }
    if (!Json.str(identity, "ili_version", "2.4").equals("2.4"))
      throw new Problem("unsupported_model_version", "Erste Umsetzung unterstützt INTERLIS 2.4.");
    String purpose = Json.str(identity, "model_purpose", "VALIDATION"),
        profile = Json.str(identity, "rule_profile", "SO");
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
    var typed = new ArrayList<Object>();
    var mappings = new ArrayList<Object>();
    var questions = new ArrayList<>();
    for (Object o : attrs) {
      var a = Json.obj(o);
      String column = Json.required(a, "name");
      var confirmation = Json.obj(confirmations.get(column));
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
      if (Json.str(a, "description", "").isBlank())
        questions.add(column + ": fachliche Beschreibung fehlt.");
      if (!Json.str(a, "code_list", "").isBlank() && !confirmation.containsKey("type_spec"))
        questions.add(column + ": Codeliste ist belegt, aber Werte/Domain noch nicht bestätigt.");
      var typeSpec = Json.obj(confirmation.get("type_spec"));
      if (Set.of("DECIMAL", "NUMERIC").contains(type) && typeSpec.isEmpty())
        questions.add(
            column
                + ": konkreten numerischen Wertebereich und Genauigkeit bestätigen. INTERLIS verlangt min/max für einen instanziierbaren Zahlentyp; beobachtete Grenzen werden nicht automatisch übernommen.");
      if (type.equals("TEXT") && typeSpec.isEmpty() && profile.equals("SO"))
        questions.add(
            column
                + ": maximale Textlänge für das SO-Regelprofil bestätigen; beobachtete Längen sind keine fachliche Grenze.");
      if (type.equals("DATETIME") && typeSpec.isEmpty())
        questions.add(
            column
                + ": Der bestehende GRETL-/INTERLIS-XMLDateTime-Adapter akzeptiert den vorgeschriebenen CSV-Offset +01:00 nicht. Passende belegte Modelldomain klären; keine stille Umdeutung zu TEXT und keine Formatänderung.");
      if (!typeSpec.isEmpty() && !Json.bool(confirmation, "confirmed", false))
        questions.add(column + ": zusätzliche Typsemantik bestätigen.");
      if (typeSpec.isEmpty())
        typeSpec =
            switch (type) {
              case "TEXT" -> Json.map("baseType", Json.map("kind", "TEXT"));
              case "INTEGER" ->
                  Json.map(
                      "baseType",
                      Json.map(
                          "kind",
                          "NUM_RANGE",
                          "min",
                          new java.math.BigDecimal("-9223372036854775808"),
                          "max",
                          new java.math.BigDecimal("9223372036854775807")));
              case "DECIMAL", "NUMERIC" -> Json.map("baseType", Json.map("kind", "NUMERIC"));
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
              typeSpec));
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
                                    Json.required(identity, "short_description"))),
                            "attributes",
                            typed,
                            "constraints",
                            Json.list(identity.get("constraints")))))));
    for (String field : List.of("domains", "units", "imports"))
      if (identity.containsKey(field)) spec.put(field, identity.get(field));
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
    r.put(
        "pending_model", Json.map("operation", operation, "arguments", callArgs, "at", Json.now()));
    w.persist(r);
    result = w.interlis.call(operation, callArgs);
    r.remove("pending_model");
    String text =
        Json.str(result, "updatedModelText", Json.str(result, "candidateModelText", null));
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
        var runtime = w.gretl.selected(false);
        String bundleSha = Json.required(runtime, "sha256");
        model.put("gretl_runtime", runtime);
        model.put("gretl_bundle_sha256", bundleSha);
        Json.write(
            task,
            task(original, Json.required(r, "identifier"), modelName, bundleSha, Json.sha(out)));
        w.stage(r, taskRelative, task);
        model.put("dataset_task_sha256", Json.sha(task));
        // Set reference through the existing Java MCP before the final export. This avoids
        // approval/derivation hash cycles.
        if (!r.containsKey("draft")) w.metadata(r, "import_xtf", Json.map());
        w.metadata(r, "update_metadata", Json.map("values", Json.map("model", modelName)));
        w.metadata(r, "export_xtf", Json.map());
        model.put("review_metadata_sha256", Json.sha(w.sheet(r)));
        String metadataRelative = metadataRelative(r);
        w.stage(r, metadataRelative, w.sheet(r));
      }
    }
    return Json.map(
        "status",
        result.get("status"),
        "candidate",
        !authoringValid(result),
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
            "Modellableitung prüfen",
            Json.map("model_text", text, "model", model, "changes", r.get("changes"))));
  }

  String metadataRelative(Map<String, Object> r) {
    Path dir = w.settings.topics.resolve(w.topic(r));
    if (Files.isDirectory(dir))
      try (var paths = Files.list(dir)) {
        var files =
            paths
                .filter(p -> p.toString().endsWith(".xtf") || p.toString().endsWith(".xml"))
                .toList();
        if (files.size() > 1)
          throw new Problem(
              "ambiguous_repository_metadata", "Mehr als ein Datenblatt im Themenordner.");
        if (!files.isEmpty()) return w.topic(r) + "/" + files.getFirst().getFileName();
      } catch (java.io.IOException e) {
        throw new Problem("repository_unavailable", "Themenordner nicht lesbar.");
      }
    return w.topic(r) + "/datasheet.xtf";
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

  public static String task(String original, String identifier, String model) {
    return task(original, identifier, model, "", "");
  }

  public static String task(
      String original, String identifier, String model, String bundleSha, String modelSha) {
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
                        def expectedBundleSha = %s
                        def modelFile = file(%s)
                        if (expectedModelSha && java.security.MessageDigest.getInstance('SHA-256').digest(modelFile.bytes).encodeHex().toString() != expectedModelSha) throw new GradleException('INTERLIS-Modell entspricht nicht dem freigegebenen Prüfstand')
                        if (expectedBundleSha) {
                            def runtimePath = findProperty('datenportalOfflineJarsDir') ?: System.getenv('DATENPORTAL_OFFLINE_JARS_DIR')
                            if (!runtimePath || !file(runtimePath).isDirectory()) throw new GradleException('Freigegebenes GRETL-Offline-Bundle fehlt')
                            def inventory = file(runtimePath).listFiles().findAll { it.name.endsWith('.jar') }.sort { it.name }.collect { it.name + '=' + java.security.MessageDigest.getInstance('SHA-256').digest(it.bytes).encodeHex().toString() + '\\n' }.join('')
                            if (java.security.MessageDigest.getInstance('SHA-256').digest(inventory.getBytes('UTF-8')).encodeHex().toString() != expectedBundleSha) throw new GradleException('GRETL-Binärstand unterscheidet sich von der frühen CSV-Prüfung')
                        }
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
                Workspace.groovy(bundleSha),
                Workspace.groovy(identifier + "/" + model + ".ili"),
                Workspace.groovy(identifier))
        + END
        + "\n";
  }

  public Map<String, Object> validate(Map<String, Object> r) {
    var model = Json.obj(r.get("model"));
    if (model.isEmpty()) throw new Problem("model_missing", "Modell zuerst ableiten.");
    w.exportRequired(r);
    Path ili = w.requiredFile(r, "model");
    var sheet = w.describe(r);
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
      var result =
          w.interlis.call(
              "reviewIliModel",
              Json.map(
                  "modelText",
                  Json.contents(ili),
                  "modelPurpose",
                  model.get("purpose"),
                  "ruleProfile",
                  model.get("profile")));
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
                Json.str(model, "gretl_bundle_sha256", ""),
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
    if (!Objects.equals(Json.obj(sheet.get("fields")).get("model"), modelName))
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
    Map<String, Object> gretl;
    if (data == null) gretl = Json.map("valid", true, "skipped", true, "reason", "metadata_only");
    else if (!errors.isEmpty())
      gretl = Json.map("valid", false, "skipped", true, "reason", "model_invalid");
    else {
      Path snapshot = new Workspace(w).snapshot(r, "model-csv");
      gretl =
          new Workspace(w)
              .gradle(
                  r,
                  snapshot,
                  "validateThemenCsv",
                  List.of("-Pdataset=" + r.get("identifier"), "-PdataFile=" + data),
                  "model-csv");
      if (Json.bool(gretl, "valid", false)
          && !Json.contents(Path.of(Json.required(gretl, "log")))
              .contains("INTEGRATOR_CSV_VALIDATED=true")) {
        gretl.put("valid", false);
        errors.add(
            Json.map(
                "code",
                "csv_validation_not_executed",
                "message",
                "GRETL hat keine ausgeführte erfolgreiche CSV-Validierung bestätigt."));
      }
    }
    if (gretl.containsKey("log")
        && !Json.bool(gretl, "valid", false)
        && Json.contents(Path.of(Json.required(gretl, "log")))
            .contains("invalid format of datetime value"))
      errors.add(
          Json.map(
              "code",
              "datetime_validator_limit",
              "message",
              "Die bestehende GRETL-/ilivalidator-Prüfung für INTERLIS.XMLDateTime akzeptiert den gültigen CSV-Zeitzonenoffset +01:00 nicht. Eine passende Modelldomain oder separat beauftragte Komponentenänderung ist erforderlich; Daten werden nicht still verändert."));
    var xtf =
        w.validator.validate(
            w.sheet(r), w.offices(r), w.directory(r).resolve("model-xtf-validation"));
    var report =
        Json.map(
            "valid",
            errors.isEmpty() && Json.bool(gretl, "valid", false) && Json.bool(xtf, "valid", false),
            "errors",
            errors,
            "gretl",
            gretl,
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
    report.put("fingerprint", w.fingerprint(r, "model"));
    Json.obj(r.get("checks")).put("model", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("model-review.html"),
            "INTERLIS-Modell und CSV prüfen",
            report));
    return result;
  }
}
