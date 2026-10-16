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
    MetadataArtifacts.validate(w.sheet(r), r);
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
    if (!Json.list(args.get("changes")).isEmpty()
        && Objects.equals(Json.obj(r.get("model")).get("contract_sha256"), contract(sheet))) {
      for (Object prior : Json.list(Json.obj(r.get("model")).get("mapping"))) {
        var previous = Json.obj(prior);
        String column = Json.required(previous, "column");
        var saved = Json.obj(previous.get("range_proposal"));
        if (!saved.isEmpty()
            && !proposals.containsKey(column)
            && !Json.obj(confirmations.get(column)).containsKey("type_spec")) {
          proposals.put(
              column,
              Json.map(
                  "type_spec",
                  saved.get("type_spec"),
                  "reason",
                  saved.get("reason"),
                  "negative_values_possible",
                  saved.get("negative_values_possible")));
        }
      }
    }
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
      var explicitConfirmation = confirmation;
      var acceptedRule = ModelRuleApprovals.find(r, contract(sheet), column);
      if (!acceptedRule.isEmpty()) {
        var acceptedSpec = Json.obj(acceptedRule.get("type_spec"));
        if (confirmation.containsKey("type_spec")
            && !acceptedSpec.equals(confirmation.get("type_spec")))
          throw new Problem(
              "confirmed_semantics_conflict",
              "Modellabnahme bestätigt diese Typsemantik; Änderung fachlich klären.",
              "column",
              column);
        confirmation = new LinkedHashMap<>(confirmation);
        confirmation.put("type_spec", acceptedSpec);
        confirmation.put("confirmed", true);
      }
      var typeSpec = Json.obj(confirmation.get("type_spec"));
      if (Json.bool(confirmation, "confirmed", false)) {
        String conflict = NumericModelContract.observationConflict(typeSpec, observed, type);
        if (conflict != null) questions.add(column + ": " + conflict);
      }
      Map<String, Object> proposal =
          new LinkedHashMap<>(Json.obj(acceptedRule.get("range_proposal")));

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
              explicitConfirmation,
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
    if ("PROOF_INCOMPLETE".equals(Json.obj(Json.obj(r.get("model")).get("evidence")).get("status")))
      throw new Problem(
          "model_candidate_blocked",
          "Unvollständigen Kandidaten zuerst klären. Nach Komponentenreparatur validate_model(refresh_evidence=true) verwenden; keine erneute ADD_CONSTRAINT-Ableitung.",
          "evidence",
          Json.obj(r.get("model")).get("evidence"));
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
    var numericExpectations = NumericModelContract.expectations(mappings, args.get("changes"));
    String numericAttempt =
        Json.digest(Json.map("arguments", callArgs, "component", w.interlis.identity()));
    var blockedNumeric = Json.obj(r.get("numeric_contract_blocker"));
    if (numericAttempt.equals(blockedNumeric.get("attempt")))
      throw new Problem(
          "model_numeric_contract_violation",
          "Unveränderter technischer Vertragsfehler. MCP-Komponente reparieren; keine weiteren Wertebereichsvarianten.",
          "blocker",
          blockedNumeric);
    var priorRepair = Json.read(Json.text(Json.obj(r.get("model_repair"))));
    ModelRepairs.reserve(w, r, args);
    r.put(
        "pending_model", Json.map("operation", operation, "arguments", callArgs, "at", Json.now()));
    w.persist(r);
    var derivationInputs = CheckStates.manifest(w, r, "model");
    try {
      result = w.interlis.call(operation, callArgs);
      if (operation.equals("authorIliModel")
          && authoringValid(result)
          && !Json.list(args.get("changes")).isEmpty()) {
        callArgs =
            Json.map(
                "modelPurpose",
                purpose,
                "ruleProfile",
                profile,
                "modelText",
                Json.required(result, "updatedModelText"),
                "request",
                Json.map(
                    "changes",
                    args.get("changes"),
                    "allowPotentiallyBreaking",
                    Json.bool(args, "allow_breaking", false)));
        operation = "applyIliModelChanges";
        result = w.interlis.call(operation, callArgs);
      }
      if (!Json.digest(derivationInputs).equals(Json.digest(CheckStates.manifest(w, r, "model"))))
        throw new Problem(
            "check_inputs_changed",
            "Eingangsdateien oder Prüfkomponenten während Modellableitung verändert; Kandidat nicht übernommen.");
    } catch (Problem error) {
      if (error.code.equals("model_component_upgrade_required")) {
        r.remove("pending_model");
        if (priorRepair.isEmpty()) r.remove("model_repair");
        else r.put("model_repair", priorRepair);
        throw error;
      }
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

    r.remove("pending_model");
    String text =
        Json.str(result, "updatedModelText", Json.str(result, "candidateModelText", null));
    if (text != null && authoringValid(result)) {
      ModelIdentity.verify(text, identity);
      try {
        NumericModelContract.verify(
            text,
            numericExpectations,
            w.settings.modelDirectories().stream()
                .map(p -> p.startsWith("https://") ? p : w.settings.path(p).toString())
                .toList());
        r.remove("numeric_contract_blocker");
      } catch (Problem error) {
        r.put(
            "numeric_contract_blocker",
            Json.map("attempt", numericAttempt, "error", error.result()));
        if (priorRepair.isEmpty()) r.remove("model_repair");
        else r.put("model_repair", priorRepair);
        w.persist(r);
        throw error;
      }
    }

    Diagnostics.record(
        w, r, "ili2c", compilerStatus(Json.obj(result.get("afterReview")), result), result, true);
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
    model.put("numeric_expectations", numericExpectations);
    model.put("derivation", Json.map("operation", operation, "arguments", callArgs));
    model.put("evidence_component_identity", w.interlis.identity());
    model.put("package_ready", false);
    if (Json.bool(Json.obj(r.get("model")), "authoring_valid", false))
      r.put("prepared_model", Json.read(Json.text(r.get("model"))));
    r.put("model", model);
    if (text != null) {
      Path out = w.directory(r).resolve("model").resolve(modelName + ".ili");
      Json.write(out, text);
      w.store.attach(r, out, "model");
      model.put("evidence_model_sha256", Json.sha(out));
      if (authoringValid(result)) {
        preparePackage(r, model, out);
      }
    }
    return Json.map(
        "status",
        result.get("status"),
        "numeric_rule_confirmations",
        ModelRuleApprovals.effective(r),
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
                "numeric_rule_confirmations",
                ModelRuleApprovals.effective(r),
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

    return MetadataArtifacts.target(w, r, Json.map());
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
    if (!CheckStates.current(w, r, "model", check))
      throw new Problem(
          "validation_required",
          "Prüfstand ist veraltet; aktuelle Dateien und Komponenten erneut prüfen.");
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
    return Set.of("GENERATED", "APPLIED", "VERIFIED").contains(Json.str(result, "status", ""))
        && Json.bool(result, "complete", false)
        && !Boolean.FALSE.equals(result.get("proofVerified"))
        && compilerPassed(review, result)
        && Json.bool(review, "validForAutomatedRules", false)
        && Json.list(result.get("openQuestions")).isEmpty();
  }

  private void verifyNumericModel(Map<String, Object> model, Path ili) {
    NumericModelContract.verify(
        Json.contents(ili),
        Json.list(model.getOrDefault("numeric_expectations", model.get("mapping"))),
        w.settings.modelDirectories().stream()
            .map(p -> p.startsWith("https://") ? p : w.settings.path(p).toString())
            .toList());
  }

  private void preparePackage(Map<String, Object> r, Map<String, Object> model, Path ili) {
    verifyNumericModel(model, ili);

    String name = Json.required(Json.obj(model.get("identity")), "name");
    String relative = w.topic(r) + "/dataset.gradle";
    Path old = w.repoFile(r, relative);
    String content =
        task(
            Files.isRegularFile(old) ? Json.contents(old) : "",
            Json.required(r, "identifier"),
            name);
    Path task = w.directory(r).resolve("model/tasks/" + Json.digest(content) + ".gradle");
    if (!Files.exists(task)) Json.write(task, content);
    if (!Json.contents(task).equals(content))
      throw new Problem("artifact_changed", "Gesicherte Taskdatei wurde verändert.");
    // Stage on an isolated state; only publish the complete package together.
    var staged = Json.read(Json.text(r));
    w.stage(staged, w.topic(r) + "/" + name + ".ili", ili);
    w.stage(staged, relative, task);
    model.put("dataset_task_sha256", Json.sha(task));
    model.put("package_ready", true);
    r.put("changes", staged.get("changes"));
    r.put("events", staged.get("events"));
    r.put("prepared_model", Json.read(Json.text(model)));
    r.put(
        "prepared_package",
        Json.map(
            "model_sha256",
            Json.sha(ili),
            "task_sha256",
            Json.sha(task),
            "identity",
            model.get("identity")));
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
                // Configure the existing publication validator; do not register a second task.
                gradle.projectsEvaluated {
                    tasks.named('validateDeliveredCsv').configure {
                        def sharedModels = System.getenv('DATENPORTAL_MODELS_DIR') ?: file('../shared/models').absolutePath
                        modelDirectories(file(%s).absolutePath + ';' + sharedModels)
                        firstLineIsHeader.set(true)
                        valueSeparator.set(';')
                        valueDelimiter.set('"')
                        encoding.set('UTF-8')
                        failOnError.set(true)
                    }
                }
                """
            .formatted(Workspace.groovy(identifier))
        + END
        + "\n";
  }

  public Map<String, Object> validate(Map<String, Object> r) {
    return validate(r, "integration");
  }

  public Map<String, Object> validate(Map<String, Object> r, String scope) {
    return validate(r, scope, false);
  }

  public Map<String, Object> validate(
      Map<String, Object> r, String scope, boolean refreshEvidence) {
    if (!Set.of("model", "integration").contains(scope))
      throw new Problem("invalid_arguments", "scope=model oder integration erforderlich.");

    var model = Json.obj(r.get("model"));
    if (model.isEmpty()) throw new Problem("model_missing", "Modell zuerst ableiten.");
    String profile = profile(Json.str(model, "profile", ""));
    w.exportRequired(r);
    Path ili = w.requiredFile(r, "model");
    var sheet = w.describe(r);
    Path data = w.store.file(r, "data");
    if (data != null) w.require(r, "data");
    ModelIdentity.verify(Json.contents(ili), Json.obj(model.get("identity")));
    if (Json.bool(model, "authoring_valid", false)) verifyNumericModel(model, ili);
    if (refreshEvidence) {
      var beforeVerification = CheckStates.manifest(w, r, "model");
      String text = Json.contents(ili);
      var evidence =
          w.interlis.call(
              "verifyIliModel",
              Json.map(
                  "modelText", text, "modelPurpose", model.get("purpose"), "ruleProfile", profile));
      if (!Json.digest(beforeVerification).equals(Json.digest(CheckStates.manifest(w, r, "model"))))
        throw new Problem(
            "check_inputs_changed",
            "Modell oder Prüfkomponenten während Nachweis verändert; Ergebnis nicht übernommen.");
      if (!text.equals(
          Json.str(evidence, "updatedModelText", Json.str(evidence, "candidateModelText", null))))
        throw new Problem(
            "model_verification_changed", "Nachweis muss unveränderte Modellbytes zurückgeben.");
      Diagnostics.record(
          w, r, "model-proof", authoringValid(evidence) ? "PASSED" : "INCOMPLETE", evidence, false);
      model.put("evidence", evidence);
      model.put("evidence_component_identity", w.interlis.identity());
      model.put("evidence_model_sha256", Json.sha(ili));
      model.put("authoring_valid", authoringValid(evidence));
      if (authoringValid(evidence)) preparePackage(r, model, ili);
    }
    var errors = new ArrayList<Object>();
    if (!Objects.equals(model.get("contract_sha256"), contract(sheet)))
      errors.add(
          Json.map(
              "code",
              "model_contract_changed",
              "message",
              "Datenblatt-Vertrag seit Ableitung verändert."));
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
      model.put("evidence_component_identity", w.interlis.identity());
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
      if (Json.bool(model, "authoring_valid", false)) preparePackage(r, model, ili);
    }
    if (!Json.bool(model, "authoring_valid", false)) {
      model.put("package_ready", false);
      errors.add(
          Json.map(
              "code",
              "model_candidate",
              "message",
              "Compiler-/Regelprüfung oder automatische Constraint-Nachweise sind unvollständig. Fachregel beibehalten; Komponentenursache klären, danach refresh_evidence=true.",
              "constraint_proofs",
              Json.obj(model.get("evidence")).get("constraintProofs")));
    }
    if (!Objects.equals(model.get("evidence_component_identity"), w.interlis.identity()))
      errors.add(
          Json.map(
              "code",
              "model_evidence_stale",
              "message",
              "Modellnachweis stammt von anderer oder unbekannter Prüfkomponente. validate_model(refresh_evidence=true) erforderlich."));
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
    boolean packageReady =
        Json.bool(model, "authoring_valid", false) && Json.bool(model, "package_ready", false);
    if (Json.bool(model, "authoring_valid", false) && !packageReady)
      errors.add(
          Json.map(
              "code",
              "model_package_not_prepared",
              "message",
              "Modell-/Taskpaket ist noch nicht vollständig vorbereitet. Nachweise mit refresh_evidence=true erneuern."));
    Path existingTask = w.repoFile(r, taskRelative);
    if (packageReady
        && Files.isRegularFile(existingTask)
        && Objects.equals(Json.sha(existingTask), model.get("dataset_task_sha256"))) {
      String original = Json.contents(existingTask);
      if (original.contains(BEGIN) && original.contains("tasks.register('validateThemenCsv'")) {
        preparePackage(r, model, ili);
        Store.event(
            r,
            "csv_task_updated",
            Json.map(
                "path",
                taskRelative,
                "reason",
                "Single publication CSV validator; fresh review required."));
      }
    }
    if (packageReady
        && (!Files.isRegularFile(w.repoFile(r, taskRelative))
            || !Objects.equals(
                Json.sha(w.repoFile(r, taskRelative)), model.get("dataset_task_sha256"))))
      errors.add(
          Json.map(
              "code",
              "model_task_changed",
              "message",
              "CSV-Taskdefinition seit Modellableitung verändert; erneut ableiten und prüfen."));
    if (packageReady
        && (!Files.isRegularFile(w.repoFile(r, iliRelative))
            || !Objects.equals(Json.sha(w.repoFile(r, iliRelative)), Json.sha(ili))))
      errors.add(
          Json.map(
              "code",
              "repository_model_mismatch",
              "message",
              "Themenmodell und Prüfmodell unterscheiden sich."));
    var manifest = CheckStates.manifest(w, r, "model");
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
    report.put("data_sha256", data == null ? null : Json.sha(data));
    if (data != null) {
      var current =
          Csv.inspect(
              data,
              Json.read(AgentResources.resolve(w.settings.root, "config/rules.json")),
              Json.list(sheet.get("attributes")));
      report.put("current_data_observations", current.get("columns"));
    }
    if (Json.bool(report, "valid", false)
        && CheckStates.current(w, r, "model", Json.map("manifest", manifest))) {
      ModelRepairs.passed(r);
      if (data != null) {
        String hash = Json.sha(data);
        Object previous =
            model.getOrDefault("validated_data_sha256", model.get("source_data_sha256"));
        model.put("validated_data_sha256", hash);
        if (!Objects.equals(previous, hash))
          Store.event(
              r,
              "model_data_validated",
              Json.map(
                  "previous_data_sha256",
                  previous,
                  "data_sha256",
                  hash,
                  "model_sha256",
                  Json.sha(ili),
                  "scope",
                  scope));
      }
    }
    report.put("model_approval_current", ModelRuleApprovals.current(w, r));
    report.put("numeric_rule_confirmations", ModelRuleApprovals.effective(r));
    report.put("scope", scope);
    report.put("approvable", scope.equals("integration") && Json.bool(report, "valid", false));
    report.put("diagnostic_history", r.get("diagnostic_history"));
    report.put("repair", r.get("model_repair"));
    report.put("fingerprint", w.fingerprint(r, "model"));

    CheckStates.record(
        w, r, scope.equals("model") ? "model_preparation" : "model", report, manifest);
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
