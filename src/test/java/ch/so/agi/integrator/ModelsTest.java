package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ModelsTest {
  @TempDir Path temp;
  Fixtures f;
  String id;
  final List<Map<String, Object>> requests = new ArrayList<>();
  Map<String, Object> reply;
  boolean reviewUnavailable;
  boolean componentOutdated;

  static class Datasheet implements McpClients.ToolClient {
    final List<String> calls = new ArrayList<>();
    String xml;
    int revision = 1;
    String draft = "fixture-draft";

    Map<String, Object> snapshot() {
      return Json.map(
          "draft_id", draft, "revision", revision, "kind", "dataset", "data", Json.map());
    }

    @Override
    public Map<String, Object> call(String op, Map<String, Object> args) {
      calls.add(op);
      if (op.equals("import_xtf")) {
        xml = Json.required(args, "xml");
        revision = 1;
        return snapshot();
      }
      if (op.equals("read_datasheet")) return snapshot();
      if (op.equals("update_metadata")) {
        var doc = Xml.parse(xml);
        var n = Xml.datasets(doc).getFirst();
        Json.obj(args.get("values"))
            .forEach(
                (k, v) -> {
                  var found =
                      Xml.children(n).stream().filter(e -> Xml.local(e).equals(k)).findFirst();
                  var e =
                      found.orElseGet(
                          () -> {
                            var created = doc.createElementNS(Xml.SHEET, k);
                            n.appendChild(created);
                            return created;
                          });
                  e.setTextContent(v.toString());
                });
        xml = Xml.serialize(doc);
        revision++;
        return snapshot();
      }
      if (op.equals("export_xtf")) return Json.map("xml", xml, "valid", true);
      throw new Problem("fixture", "unexpected op");
    }
  }

  void prepareReference() {
    var check = f.call("validate_model", "run_id", id, "scope", "model");
    assertTrue(Json.bool(check, "valid", false), Json.pretty(check));
    f.call("prepare_model_reference", "run_id", id);
  }

  Map<String, Object> identity() {
    return Json.map(
        "name",
        "FixtureModel_20261006",
        "uri",
        "https://example.org/models",
        "version",
        "2026-10-06",
        "technical_contact",
        "fixture@example.org",
        "title",
        "Fixture model",
        "short_description",
        "Automatisierter Test");
  }

  static Map<String, Object> proposals() {
    return Json.map(
        "Jahr",
        Json.map(
            "type_spec",
            Json.map("baseType", Json.map("kind", "NUM_RANGE", "min", 1900, "max", 2100)),
            "reason",
            "Beobachtungsjahr der modernen Messreihe; Spielraum bis 2100.",
            "negative_values_possible",
            false));
  }

  @BeforeEach
  void setup() throws Exception {
    reply =
        Json.map(
            "status",
            "GENERATED",
            "complete",
            true,
            "updatedModelText",
            "INTERLIS 2.4;\nMODEL FixtureModel_20261006 AT \"https://example.org/models\" VERSION \"2026-10-06\" =\n TOPIC Data = CLASS Observation = Jahr : 1900..2100; END Observation; END Data; END FixtureModel_20261006.\n",
            "proofVerified",
            true,
            "afterReview",
            Json.map(
                "compilerValid",
                true,
                "validForAutomatedRules",
                true,
                "manualChecks",
                List.of(Json.map("message", "Fachsemantik prüfen"))),
            "openQuestions",
            List.of());
    f =
        new Fixtures(
            temp,
            new Fixtures.FakeProcess(),
            new Datasheet(),
            (name, args) -> {
              if (componentOutdated) NumericModelContract.requireCapability(Map.of());
              requests.add(Json.map("name", name, "arguments", args));
              if (name.equals("reviewIliModel") && reviewUnavailable)
                throw new Problem("mcp_unavailable", "Synthetischer Verbindungsfehler");
              return reply;
            });
    Path metadata = temp.resolve("metadata.xtf");
    Json.write(
        metadata,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace(
                "<ns2:name>Jahr</ns2:name>",
                "<ns2:name>Jahr</ns2:name><ns2:description>Beobachtungsjahr</ns2:description>"));
    Path csv = temp.resolve("input.csv");
    Json.write(csv, "Jahr\n2025\n");
    id =
        Json.required(
            f.call(
                "start",
                "organization",
                "agi",
                "identifier",
                "ch.so.grundwasser.qualitaet",
                "data_path",
                csv.toString(),
                "metadata_path",
                metadata.toString()),
            "id");
    f.approve(id, "data", f.call("analyze", "run_id", id));
  }

  @Test
  void currentExportIsModelContractEvenWithContradictoryXlsxSource() {
    // Synthetic source sentinel: derive_model must never parse or derive from this attachment.
    Path source = temp.resolve("conflicting.xlsx");
    Json.write(source, "XLSX proposal: other_identifier; other_column; Ganzzahl");
    f.call("attach", "run_id", id, "role", "metadata_source", "path", source.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
    f.call("metadata", "run_id", id, "operation", "import_xtf");
    f.call("metadata", "run_id", id, "operation", "export_xtf");
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    String request = Json.text(requests.getFirst());
    assertTrue(request.contains("Jahr"));
    assertFalse(request.contains("other_column"));
    f.call(
        "metadata",
        "run_id",
        id,
        "operation",
        "update_metadata",
        "arguments",
        Json.map("values", Json.map("title", "Changed")));
    int calls = requests.size();
    f.error(
        "draft_not_exported", () -> f.call("derive_model", "run_id", id, "identity", identity()));
    assertEquals(calls, requests.size());
  }

  @Test
  void missingIdentityIsQuestionNotInvention() {
    var r = f.call("derive_model", "run_id", id, "identity", Json.map());
    assertEquals("NEEDS_INPUT", r.get("status"));
    assertTrue(requests.isEmpty());
  }

  @Test
  void flatModelUsesTypedToolAndStagesAllFiles() {
    var r =
        f.call(
            "derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertFalse(Json.bool(r, "candidate", true));
    f.error("validation_required", () -> f.call("prepare_model_reference", "run_id", id));
    assertEquals("authorIliModel", requests.getFirst().get("name"));
    var args = Json.obj(requests.getFirst().get("arguments"));
    assertEquals("VALIDATION", args.get("modelPurpose"));
    assertEquals("SO-DATA", args.get("ruleProfile"));
    assertTrue(((Datasheet) f.workflow.datasheets).calls.isEmpty());
    var preliminary = f.call("validate_model", "run_id", id, "scope", "model");
    assertTrue(Json.bool(preliminary, "valid", false), Json.pretty(preliminary));
    assertTrue(((Datasheet) f.workflow.datasheets).calls.isEmpty());
    f.error("validation_required", () -> f.approve(id, "model", preliminary));
    f.call("prepare_model_reference", "run_id", id);
    int calls = ((Datasheet) f.workflow.datasheets).calls.size();
    f.call("prepare_model_reference", "run_id", id);
    assertEquals(calls, ((Datasheet) f.workflow.datasheets).calls.size());
    var state = f.call("status", "run_id", id);
    var changes = Json.obj(state.get("changes"));
    assertEquals(3, changes.size());
    assertTrue(changes.keySet().stream().anyMatch(k -> k.endsWith(".ili")));
    var report = f.call("validate_model", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    assertEquals(1, requests.size(), "No redundant compiler/review call");
    f.approve(id, "model", report);
  }

  @Test
  void referencePreparationRetainsRepositoryFilenameAndOriginalBytes() {
    Path source = f.repo.resolve("agi/ch.so.grundwasser.qualitaet/special.XML");
    String original = f.workflow.store.edit(id, run -> Json.contents(f.workflow.sheet(run)));
    Json.write(source, original);
    f.call("repository_datasheet", "run_id", id);
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    assertEquals(original, Json.contents(source));
    assertTrue(
        Json.obj(f.call("status", "run_id", id).get("changes"))
            .containsKey("agi/ch.so.grundwasser.qualitaet/special.XML"));
    assertTrue(Json.bool(f.call("validate_model", "run_id", id), "valid", false));
  }

  @Test
  void referencePreparationRejectsConcurrentRepositoryEdit() {
    Path source = f.repo.resolve("agi/ch.so.grundwasser.qualitaet/special.xtf");
    String original = f.workflow.store.edit(id, run -> Json.contents(f.workflow.sheet(run)));
    Json.write(source, original);
    f.call("repository_datasheet", "run_id", id);
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertTrue(Json.bool(f.call("validate_model", "run_id", id, "scope", "model"), "valid", false));
    Json.write(source, original + "\n");
    f.error("repository_datasheet_changed", () -> f.call("prepare_model_reference", "run_id", id));
    assertTrue(((Datasheet) f.workflow.datasheets).calls.isEmpty());
  }

  @Test
  void missingDescriptionIsNotRequiredForSoData() {
    Path p = temp.resolve("without-description.xtf");
    Json.write(p, Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")));
    f.call("attach", "run_id", id, "role", "metadata", "path", p.toString());
    var r =
        f.call(
            "derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertEquals("GENERATED", r.get("status"));
    assertEquals(1, requests.size());
  }

  @Test
  void compilerOrProofFailureRemainsCandidate() {
    reply.put("status", "PROOF_INCOMPLETE");
    reply.put("proofVerified", false);
    reply.put("candidateModelText", reply.remove("updatedModelText"));
    var r =
        f.call(
            "derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertTrue(Json.bool(r, "candidate", false));
    var state = f.call("status", "run_id", id);
    assertTrue(Json.obj(state.get("changes")).isEmpty());
    var report = f.call("validate_model", "run_id", id);
    assertFalse(Json.bool(report, "valid", true));
    f.error("validation_required", () -> f.approve(id, "model", report));
  }

  @Test
  void correctedCsvReusesModelAfterDataApprovalAndKeepsDerivationHistory() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var first = f.call("validate_model", "run_id", id);
    f.approve(id, "model", first);
    var original = Json.obj(first.get("model"));
    Object source = original.get("source_data_sha256");
    Object modelHash = first.get("model_sha256");
    int calls = requests.size();
    Path p = temp.resolve("changed.csv");
    Json.write(p, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", p.toString());
    f.error("approval_required", () -> f.call("validate_model", "run_id", id));
    f.approve(id, "data", f.call("analyze", "run_id", id));
    var result = f.call("validate_model", "run_id", id);
    assertEquals(true, result.get("valid"), Json.pretty(result));
    assertEquals(calls, requests.size());
    assertEquals(modelHash, result.get("model_sha256"));
    assertEquals(source, Json.obj(result.get("model")).get("source_data_sha256"));
    assertEquals(Json.sha(p), Json.obj(result.get("model")).get("validated_data_sha256"));
    assertEquals(Json.sha(p), result.get("data_sha256"));
    assertFalse(
        Json.bool(
            Json.obj(f.call("status", "run_id", id).get("current_approvals")), "model", true));
    var repeated = f.call("validate_model", "run_id", id);
    assertEquals(result.get("fingerprint"), repeated.get("fingerprint"));
    assertTrue(
        Json.contents(Path.of(Json.required(result, "review_path")))
            .contains("Aktuell geprüfte CSV"));
    assertTrue(Json.text(result.get("current_data_observations")).contains("2026"));
    assertTrue(Json.text(Json.obj(result.get("model")).get("mapping")).contains("2025"));
    var state = Json.read(f.workflow.store.directory(id).resolve("state.json"));
    assertEquals(
        1,
        Json.list(state.get("events")).stream()
            .map(Json::obj)
            .filter(e -> "model_data_validated".equals(e.get("action")))
            .count());
  }

  @Test
  void incompleteProofCanBeRefreshedWithoutAnotherChangeOrLostTaskBinding() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    String text = Json.required(reply, "updatedModelText");
    reply.put("status", "PROOF_INCOMPLETE");
    reply.put("proofVerified", false);
    reply.put("candidateModelText", reply.remove("updatedModelText"));
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "changes",
        List.of(Json.map("operation", "ADD_CONSTRAINT")));
    var failed = f.call("validate_model", "run_id", id, "scope", "model");
    assertEquals(
        List.of("model_candidate"),
        Json.list(failed.get("errors")).stream().map(Json::obj).map(e -> e.get("code")).toList());
    assertFalse(Json.bool(failed, "approvable", true));
    f.error(
        "model_candidate_blocked",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "changes",
                List.of(Json.map("operation", "ADD_CONSTRAINT"))));
    reply.put("status", "VERIFIED");
    reply.put("proofVerified", true);
    reply.put("updatedModelText", reply.remove("candidateModelText"));
    var refreshed =
        f.call("validate_model", "run_id", id, "scope", "model", "refresh_evidence", true);
    assertTrue(Json.bool(refreshed, "valid", false), Json.pretty(refreshed));
    assertEquals("verifyIliModel", requests.getLast().get("name"));
    assertEquals(text, Json.obj(requests.getLast().get("arguments")).get("modelText"));
    f.call("prepare_model_reference", "run_id", id);
    assertTrue(Json.bool(f.call("validate_model", "run_id", id), "approvable", false));
  }

  @Test
  void initialDerivationAppliesRequestedChanges() {
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        proposals(),
        "changes",
        List.of(Json.map("operation", "ADD_CONSTRAINT")));
    assertEquals(
        List.of("authorIliModel", "applyIliModelChanges"),
        requests.stream().map(r -> r.get("name")).toList());
  }

  @Test
  void changedInputsDuringValidationCannotBecomeCurrentOrApprovable() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    f.workflow.csvValidation =
        new CsvValidation(f.settings, f.workflow.process) {
          @Override
          public Map<String, Object> validate(Path data, Path model, String name, Path output) {
            Json.write(f.repo.resolve("shared/models/new-dependency.ili"), "changed during check");
            return Json.map("valid", true);
          }
        };
    var result = f.call("validate_model", "run_id", id, "scope", "model");
    assertFalse(Json.bool(result, "valid", true));
    assertEquals("stale", result.get("state"));
    assertTrue(Json.text(result.get("errors")).contains("check_inputs_changed"));
  }

  @Test
  void identicalContentDifferentPathPreservesApprovalAndHistoricalReport() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var report = f.call("validate_model", "run_id", id);
    f.approve(id, "model", report);
    Path original = Path.of(Json.required(report, "review_path"));
    String hash = Json.sha(original);
    Path copy = temp.resolve("same-content.csv");
    Json.write(copy, "Jahr\n2025\n");
    f.call("attach", "run_id", id, "role", "data", "path", copy.toString());
    assertTrue(
        Json.bool(
            Json.obj(f.call("status", "run_id", id).get("current_approvals")), "model", false));
    Json.write(copy, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", copy.toString());
    assertFalse(
        Json.bool(
            Json.obj(f.call("status", "run_id", id).get("current_approvals")), "model", true));
    assertEquals(hash, Json.sha(original));
    assertEquals(
        "stale",
        Json.obj(Json.obj(f.call("status", "run_id", id).get("check_states")).get("model"))
            .get("state"));
  }

  @Test
  void incompletePackagePreparationDoesNotPublishHalfAPackage() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    String before = Json.digest(f.call("status", "run_id", id).get("changes"));
    Json.write(f.repo.resolve("agi/ch.so.grundwasser.qualitaet/dataset.gradle"), "external edit");
    f.error(
        "repository_conflict",
        () -> f.call("validate_model", "run_id", id, "scope", "model", "refresh_evidence", true));
    assertEquals(before, Json.digest(f.call("status", "run_id", id).get("changes")));
  }

  @Test
  void changedCsvFailureDoesNotReplaceSuccessfulDataBinding() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var first = f.call("validate_model", "run_id", id);
    Object previous = Json.obj(first.get("model")).get("validated_data_sha256");
    Path p = temp.resolve("invalid_range.csv");
    Json.write(p, "Jahr\n2200\n");
    f.call("attach", "run_id", id, "role", "data", "path", p.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
    f.workflow.csvValidation = new CsvValidation(f.settings, new ProcessRunner());
    var failed = f.call("validate_model", "run_id", id);
    assertEquals(false, failed.get("valid"));
    assertEquals(previous, Json.obj(failed.get("model")).get("validated_data_sha256"));
    assertEquals(Json.sha(p), failed.get("data_sha256"));
    assertFalse(Json.list(failed.get("diagnostic_history")).isEmpty());
  }

  @Test
  void changeBatchReusesUnconfirmedSavedRanges() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    reply.put("status", "APPLIED");
    var result =
        f.call(
            "derive_model",
            "run_id",
            id,
            "identity",
            identity(),
            "changes",
            List.of(Json.map("operation", "REMOVE_COMMENTS")));
    assertEquals("APPLIED", result.get("status"));
    var state = f.call("status", "run_id", id);
    var mapping = Json.obj(Json.list(Json.obj(state.get("model")).get("mapping")).getFirst());
    assertEquals(false, Json.obj(mapping.get("range_proposal")).get("confirmed"));
    assertEquals("agent_estimate", Json.obj(mapping.get("range_proposal")).get("origin"));
    assertEquals("applyIliModelChanges", requests.getLast().get("name"));
  }

  @Test
  void oldRunWithoutValidatedHashCanBeResumedWithoutAuthoring() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("model")).remove("validated_data_sha256");
          return null;
        });
    Path csv = temp.resolve("resumed.csv");
    Json.write(csv, "Jahr\n2027\n");
    f.call("attach", "run_id", id, "role", "data", "path", csv.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
    try (var resumed =
        new Workflow(
            f.settings,
            new Fixtures.FakeProcess(),
            new Datasheet(),
            new McpClients.ToolClient() {
              @Override
              public String identity() {
                return f.workflow.interlis.identity();
              }

              @Override
              public Map<String, Object> call(String op, Map<String, Object> args) {
                fail("No model operation required after restart");
                return Json.map();
              }
            })) {
      // Exported metadata is available; avoid requiring a live synthetic draft session.
      resumed.store.edit(
          id,
          r -> {
            r.remove("draft");
            return null;
          });
      var result = Json.obj(resumed.call("validate_model", Json.map("run_id", id)));
      assertEquals(true, result.get("valid"), Json.pretty(result));
      assertEquals(Json.sha(csv), Json.obj(result.get("model")).get("validated_data_sha256"));
    }
  }

  @Test
  void changedContractAndIncompleteProofStillBlockNewData() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var original = f.call("validate_model", "run_id", id);
    Object binding = Json.obj(original.get("model")).get("validated_data_sha256");
    Path csv = temp.resolve("corrected.csv");
    Json.write(csv, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", csv.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("model")).put("authoring_valid", false);
          return null;
        });
    var invalid = f.call("validate_model", "run_id", id);
    assertEquals(false, invalid.get("valid"));
    assertEquals(binding, Json.obj(invalid.get("model")).get("validated_data_sha256"));
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("model")).put("authoring_valid", true);
          Json.obj(r.get("model")).put("contract_sha256", "synthetic-different-contract");
          return null;
        });
    invalid = f.call("validate_model", "run_id", id);
    assertEquals(false, invalid.get("valid"));
    assertTrue(Json.text(invalid.get("errors")).contains("model_contract_changed"));
    assertEquals(binding, Json.obj(invalid.get("model")).get("validated_data_sha256"));
    int before = requests.size();
    var changed =
        f.call(
            "derive_model",
            "run_id",
            id,
            "identity",
            identity(),
            "changes",
            List.of(Json.map("operation", "REMOVE_COMMENTS")));
    assertEquals("NEEDS_INPUT", changed.get("status"));
    assertEquals(before, requests.size());
  }

  @Test
  void observedValuesDoNotCreateCodelistsOrKeys() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    String spec = Json.text(Json.obj(requests.getFirst().get("arguments")).get("spec"));
    assertFalse(spec.contains("UNIQUE"));
    assertFalse(spec.contains("enumItems"));
    assertFalse(spec.contains("2025"));
  }

  @Test
  void constraintsNeedConfirmedSemantics() {
    var identity = identity();
    identity.put("constraints", List.of(Json.map("kind", "MANDATORY", "condition", "Jahr > 2000")));
    var result = f.call("derive_model", "run_id", id, "identity", identity);
    assertEquals("NEEDS_INPUT", result.get("status"));
    assertTrue(requests.isEmpty());
  }

  @Test
  void gretlTaskPreservesExistingHookAndStopsPublication() {
    String task =
        Models.task("ext.existingSetting = true\n", "ch.so.fixture", "FixtureModel_20261006");
    assertTrue(task.startsWith("ext.existingSetting"));
    assertTrue(task.contains("tasks.named('validateDeliveredCsv')"));
    assertFalse(task.contains("preparePublicationWorkspace"));
    assertFalse(task.contains("tasks.register"));
    assertFalse(task.contains("stageThemenCsv"));
    assertFalse(task.contains("validateThemenCsv"));
    assertFalse(task.contains("MessageDigest"));
    assertFalse(task.contains("Files.mismatch"));
    assertTrue(task.contains("failOnError.set(true)"));
    assertTrue(task.contains("encoding.set('UTF-8')"));
    assertTrue(task.contains("valueSeparator.set(';')"));
    assertFalse(task.contains("onlyIf"));
    assertEquals(
        1,
        Models.task(task, "ch.so.fixture", "FixtureModel_20261006").split(Models.BEGIN, -1).length
            - 1);
  }

  @Test
  void legacyGeneratedBlockIsReplacedCompletely() {
    String original =
        "ext.before = true\n"
            + Models.BEGIN
            + "\ntasks.register('stageThemenCsv') {}\ntasks.register('validateThemenCsv') {}\n"
            + "// Files.mismatch MessageDigest expectedModelSha\n"
            + Models.END
            + "\next.after = true\n";
    String task = Models.task(original, "ch.so.fixture", "FixtureModel_20261006");
    assertTrue(task.contains("ext.before = true"));
    assertTrue(task.contains("ext.after = true"));
    assertFalse(task.contains("stageThemenCsv"));
    assertFalse(task.contains("validateThemenCsv"));
    assertFalse(task.contains("MessageDigest"));
    assertEquals(1, task.split("tasks.named", -1).length - 1);
  }

  @Test
  void legacyRunRefreshesOnlyTaskAndRequiresNewReview() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    f.call("validate_model", "run_id", id, "scope", "model");
    f.call("prepare_model_reference", "run_id", id);
    f.workflow.store.edit(
        id,
        r -> {
          Path legacy = temp.resolve("legacy.gradle");
          Json.write(
              legacy,
              "ext.keep = true\n"
                  + Models.BEGIN
                  + "\ntasks.register('validateThemenCsv') {}\n"
                  + Models.END
                  + "\n");
          f.workflow.stage(r, f.workflow.topic(r) + "/dataset.gradle", legacy);
          Json.obj(r.get("model")).put("dataset_task_sha256", Json.sha(legacy));
          // A historical approved state, deliberately not revalidated by the new runtime.
          Json.obj(r.get("approvals"))
              .put(
                  "model",
                  Json.map(
                      "fingerprint",
                      f.workflow.fingerprint(r, "model"),
                      "human_statement",
                      "SYNTHETIC ISOLATED TEST"));
          r.put(
              "deliveries",
              Json.map("local", Json.map("phase", "failed", "job", "historical-build")));
          return null;
        });
    String before = f.workflow.store.edit(id, r -> Json.sha(f.workflow.store.file(r, "model")));
    int calls = requests.size();
    var checked = f.call("validate_model", "run_id", id);
    assertTrue(Json.bool(checked, "valid", false), Json.pretty(checked));
    assertEquals(calls, requests.size());
    assertEquals(
        before, f.workflow.store.edit(id, r -> Json.sha(f.workflow.store.file(r, "model"))));
    var state = f.call("status", "run_id", id);
    assertEquals(false, Json.obj(state.get("current_approvals")).get("model"));
    assertEquals("failed", Json.obj(Json.obj(state.get("deliveries")).get("local")).get("phase"));
    String fingerprint = Json.required(checked, "fingerprint");
    assertEquals(fingerprint, f.call("validate_model", "run_id", id).get("fingerprint"));
  }

  @Test
  void changedTaskRequiresNewModelReview() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    Path changed = temp.resolve("changed-task.gradle");
    Json.write(changed, "tasks.register('validateThemenCsv') { }\n");
    f.error(
        "model_artifact_managed",
        () ->
            f.call(
                "stage_change",
                "run_id",
                id,
                "relative_path",
                "agi/ch.so.grundwasser.qualitaet/dataset.gradle",
                "source_path",
                changed.toString()));
    f.workflow.store.edit(
        id, r -> f.workflow.stage(r, "agi/ch.so.grundwasser.qualitaet/dataset.gradle", changed));
    var report = f.call("validate_model", "run_id", id);
    assertFalse(Json.bool(report, "valid", true));
    assertTrue(
        Json.list(report.get("errors")).stream()
            .map(Json::obj)
            .anyMatch(e -> Objects.equals(e.get("code"), "model_task_changed")));
  }

  @Test
  void modelGateRequiredForJointApply() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var report = f.call("validate", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    f.approve(id, "metadata", report);
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    f.approve(id, "model", f.call("validate_model", "run_id", id));
    f.call("apply_local_changes", "run_id", id);
  }

  @Test
  void failedApprovalNeverConfirmsRules() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var report = f.call("validate_model", "run_id", id);
    f.error(
        "stale_review",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "model",
                "fingerprint",
                "stale",
                "human_statement",
                "Synthetische isolierte Testfreigabe"));
    f.error(
        "human_statement_missing",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "model",
                "fingerprint",
                report.get("fingerprint"),
                "human_statement",
                ""));
    var state = f.call("status", "run_id", id);
    assertTrue(Json.list(state.get("model_rule_approvals")).isEmpty());
    assertEquals(
        false,
        Json.obj(Json.list(state.get("numeric_rule_confirmations")).getFirst()).get("confirmed"));
  }

  @Test
  void newCsvCannotExpandAnApprovedRange() {
    approveNumericModel();
    Path csv = temp.resolve("outside.csv");
    Json.write(csv, "Jahr\n2200\n");
    f.call("attach", "run_id", id, "role", "data", "path", csv.toString());
    f.approve(id, "data", f.call("analyze", "run_id", id));
    var derived = f.call("derive_model", "run_id", id, "identity", identity());
    assertEquals("NEEDS_INPUT", derived.get("status"));
    assertTrue(Json.text(derived.get("open_questions")).contains("Bestätigter Bereich"));
    var state = f.call("status", "run_id", id);
    assertEquals(
        true,
        Json.obj(Json.list(state.get("numeric_rule_confirmations")).getFirst()).get("confirmed"));
    assertEquals(false, Json.obj(state.get("current_approvals")).get("model"));
  }

  void approveNumericModel() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    prepareReference();
    var checked = f.call("validate", "run_id", id);
    f.approve(id, "metadata", checked);
    f.approve(id, "model", Json.obj(checked.get("model")));
  }

  @Test
  void modelApprovalConfirmsRulesWithoutChangingFingerprints() throws Exception {
    approveNumericModel();
    var state = f.call("status", "run_id", id);
    assertEquals(true, Json.obj(state.get("current_approvals")).get("model"));
    assertEquals(true, Json.obj(state.get("current_approvals")).get("metadata"));
    var rule = Json.obj(Json.list(state.get("numeric_rule_confirmations")).getFirst());
    assertEquals(true, rule.get("confirmed"));
    assertEquals("agent_estimate", rule.get("origin"));
    assertEquals("model_approval", rule.get("confirmation_source"));
    var evidence = Json.obj(rule.get("evidence"));
    assertNotNull(evidence.get("model_sha256"));
    assertFalse(Json.str(Json.obj(evidence.get("approval")), "human_statement", "").isBlank());
    var report = f.call("validate_model", "run_id", id);
    assertEquals(state.get("numeric_rule_confirmations"), report.get("numeric_rule_confirmations"));
    assertTrue(
        Files.readString(Path.of(Json.required(report, "review_path")))
            .contains("Bestätigung numerischer Fachregeln"));
    assertEquals(
        true, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("model"));
  }

  @Test
  void approvedRulesSurviveIndependentChangeAndRejectConflicts() {
    approveNumericModel();
    reply.put("status", "APPLIED");
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "changes",
        List.of(Json.map("operation", "REMOVE_COMMENTS")));
    var state = f.call("status", "run_id", id);
    assertEquals(false, Json.obj(state.get("current_approvals")).get("model"));
    assertEquals(
        true,
        Json.obj(Json.list(state.get("numeric_rule_confirmations")).getFirst()).get("confirmed"));
    var changed = proposals();
    Json.obj(Json.obj(Json.obj(changed.get("Jahr")).get("type_spec")).get("baseType"))
        .put("max", "2200");
    f.error(
        "range_proposal_conflict",
        () ->
            f.call(
                "derive_model", "run_id", id, "identity", identity(), "range_proposals", changed));
  }

  @Test
  void oldApprovalRecoveredOnlyOnWriteAndOnlyWhenCurrent() throws Exception {
    approveNumericModel();
    f.workflow.store.edit(id, r -> r.remove("model_rule_approvals"));
    Path statePath = f.workflow.store.directory(id).resolve("state.json");
    String before = Files.readString(statePath);
    f.call("status", "run_id", id);
    assertEquals(before, Files.readString(statePath));
    f.call("validate_model", "run_id", id);
    assertFalse(Json.list(f.call("status", "run_id", id).get("model_rule_approvals")).isEmpty());
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("model_rule_approvals");
          Json.obj(Json.obj(r.get("approvals")).get("model")).put("fingerprint", "stale");
          return null;
        });
    f.call("validate_model", "run_id", id);
    assertTrue(Json.list(f.call("status", "run_id", id).get("model_rule_approvals")).isEmpty());
  }

  @Test
  void ruleMatchingIncludesContractAndDecimalPrecision() {
    approveNumericModel();
    var state = f.call("status", "run_id", id);
    var model = Json.obj(state.get("model"));
    var mapping = Json.obj(Json.list(model.get("mapping")).getFirst());
    Json.obj(Json.obj(mapping.get("type_spec")).get("baseType")).put("max", "2100.0");
    assertEquals(false, Json.obj(ModelRuleApprovals.effective(state).getFirst()).get("confirmed"));
    model.put("contract_sha256", "different");
    assertTrue(ModelRuleApprovals.find(state, "different", "Jahr").isEmpty());
  }

  @Test
  void proposalIsNotAConfirmationAndRechecksPreserveApproval() {
    var result =
        f.call(
            "derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    var mapping = Json.obj(Json.list(Json.obj(result.get("model")).get("mapping")).getFirst());
    assertEquals(false, Json.obj(mapping.get("range_proposal")).get("confirmed"));
    assertTrue(Json.obj(mapping.get("confirmed")).isEmpty());
    String spec = Json.text(Json.obj(requests.getFirst().get("arguments")).get("spec"));
    assertFalse(spec.contains("iliDoc"));
    assertFalse(spec.contains("metaAttributes"));
    prepareReference();
    var check = f.call("validate_model", "run_id", id);
    f.approve(id, "model", check);
    f.call("validate_model", "run_id", id);
    assertEquals(
        true, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("model"));
  }

  @Test
  void reviewInfrastructureFailureKeepsLogWithoutOpeningRepairSeries() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    f.workflow.store.edit(
        id,
        run -> {
          // Force a fresh review in this isolated fixture.
          Json.obj(run.get("model")).put("evidence_model_sha256", "outdated-fixture-proof");
          return null;
        });
    reviewUnavailable = true;
    f.error("mcp_unavailable", () -> f.call("validate_model", "run_id", id));
    var state = f.call("status", "run_id", id);
    var check = Json.obj(Json.list(state.get("diagnostic_history")).getLast());
    assertEquals("ERROR", check.get("result"));
    assertFalse(Json.bool(Json.obj(state.get("model_repair")), "active", false));
    var log = Json.obj(Json.list(check.get("artifacts")).getFirst());
    assertEquals(log.get("sha256"), Json.sha(Path.of(Json.required(log, "path"))));
  }

  @Test
  void rejectsSoAliasForNewAndStoredModelsWithoutChangingCandidate() {
    var identity = identity();
    identity.put("rule_profile", "SO");
    f.error(
        "model_profile",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity,
                "range_proposals",
                proposals()));
    assertTrue(requests.isEmpty());
    identity.put("rule_profile", "SO-GIS");
    var result =
        f.call("derive_model", "run_id", id, "identity", identity, "range_proposals", proposals());
    assertEquals("SO-GIS", Json.obj(result.get("model")).get("profile"));
    f.workflow.store.edit(
        id,
        run -> {
          Json.obj(run.get("model")).put("profile", "SO");
          return null;
        });
    var before = f.call("status", "run_id", id);
    f.error("model_profile", () -> f.call("validate_model", "run_id", id));
    var after = f.call("status", "run_id", id);
    for (String field : List.of("model", "files", "approvals"))
      assertEquals(before.get(field), after.get(field));
  }

  @Test
  void integerWithoutSpecificationRequiresInput() {
    var result = f.call("derive_model", "run_id", id, "identity", identity());
    assertEquals("NEEDS_INPUT", result.get("status"));
    assertTrue(requests.isEmpty());
  }

  @Test
  void compilerRepairsAreBoundedAcrossResumeAndLogsStayAvailable() {
    reply.put("status", "CANDIDATE_MODEL_INVALID");
    Json.obj(reply.get("afterReview")).put("compilerValid", false);
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    var first = f.call("status", "run_id", id);
    var artifacts =
        Json.list(Json.obj(Json.list(first.get("diagnostic_history")).getFirst()).get("artifacts"));
    var log = Json.obj(artifacts.getFirst());
    f.error(
        "repair_reference",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "range_proposals",
                proposals()));
    for (int i = 0; i < 3; i++) {
      // Read status through the normal persisted workflow between attempts.
      var series = Json.obj(f.call("status", "run_id", id).get("model_repair"));
      f.call(
          "derive_model",
          "run_id",
          id,
          "identity",
          identity(),
          "range_proposals",
          proposals(),
          "repair_of",
          series.get("last_failure"),
          "repair_reason",
          "Isolierte Testkorrektur " + (i + 1));
    }
    f.error(
        "repair_limit_reached",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "range_proposals",
                proposals()));
    assertEquals(log.get("sha256"), Json.sha(Path.of(Json.required(log, "path"))));
    String html =
        Json.contents(
            f.workflow.store.edit(
                id, run -> f.workflow.directory(run).resolve("model-candidate.html")));
    assertTrue(html.contains("href=\"file:"));
    f.call(
        "reset_model_repair",
        "run_id",
        id,
        "human_statement",
        "SIMULIERTER Neuauftrag in isolierter Testfixture");
    assertFalse(Json.list(f.call("status", "run_id", id).get("model_repair_history")).isEmpty());
  }

  @Test
  void compilerFailureCanBeRepairedAndPreviousLogRemainsLinked() {
    reply.put("status", "CANDIDATE_MODEL_INVALID");
    Json.obj(reply.get("afterReview")).put("compilerValid", false);
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    var series = Json.obj(f.call("status", "run_id", id).get("model_repair"));
    reply.put("status", "GENERATED");
    Json.obj(reply.get("afterReview")).put("compilerValid", true);
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        proposals(),
        "repair_of",
        series.get("last_failure"),
        "repair_reason",
        "Synthetisch: ungültige Syntax korrigiert");
    var result = f.call("validate_model", "run_id", id, "scope", "model");
    assertEquals(true, result.get("valid"));
    assertEquals(false, Json.obj(result.get("repair")).get("active"));
    assertTrue(Json.contents(Path.of(Json.required(result, "review_path"))).contains("FAILED"));
  }

  @Test
  void csvFailureUsesSameRepairBudgetAndRetainsItsLog() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    var healthy = f.workflow.csvValidation;
    f.workflow.csvValidation =
        new CsvValidation(f.settings, new Fixtures.FakeProcess()) {
          @Override
          public Map<String, Object> validate(Path data, Path model, String name, Path output) {
            Path log = output.resolve("fixture-csv.log");
            Json.write(log, "Error: Jahr outside range\n");
            return Json.map("valid", false, "log", log.toString(), "log_sha256", Json.sha(log));
          }
        };
    var failure = f.call("validate_model", "run_id", id, "scope", "model");
    assertEquals(false, failure.get("valid"));
    var series = Json.obj(failure.get("repair"));
    f.workflow.csvValidation = healthy;
    reply.put("status", "APPLIED");
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        proposals(),
        "changes",
        List.of(Json.map("operation", "UPDATE_ATTRIBUTE")),
        "repair_of",
        series.get("last_failure"),
        "repair_reason",
        "Synthetisch: Wertebereich passend zum Vorschlag korrigiert");
    var success = f.call("validate_model", "run_id", id, "scope", "model");
    assertEquals(true, success.get("valid"));
    assertEquals(1, Json.number(Json.obj(success.get("repair")), "attempts", -1));
    assertTrue(
        Json.contents(Path.of(Json.required(success, "review_path"))).contains("fixture-csv.log"));
  }

  @Test
  void savedProfileIsKeptAndChangedProposalReasonRequiresNewApproval() {
    var oldIdentity = identity();
    oldIdentity.put("rule_profile", "CORE");
    f.call("derive_model", "run_id", id, "identity", oldIdentity, "range_proposals", proposals());
    prepareReference();
    f.approve(id, "model", f.call("validate_model", "run_id", id));
    var changed = proposals();
    Json.obj(changed.get("Jahr")).put("reason", "Neue fachliche Begründung zur Prüfung");
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        changed,
        "changes",
        List.of(Json.map("operation", "UPDATE_ATTRIBUTE")));
    assertEquals("CORE", Json.obj(requests.getLast().get("arguments")).get("ruleProfile"));
    assertEquals(
        false, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("model"));
  }

  @Test
  void confirmedSpecificationCannotBeOverriddenByAnEstimate() {
    var confirmation =
        Json.map(
            "Jahr",
            Json.map(
                "confirmed",
                true,
                "type_spec",
                Json.obj(proposals().get("Jahr")).get("type_spec")));
    f.call("derive_model", "run_id", id, "identity", identity(), "confirmations", confirmation);
    var changed = proposals();
    Json.obj(Json.obj(Json.obj(changed.get("Jahr")).get("type_spec")).get("baseType"))
        .put("min", 1800);
    f.error(
        "range_proposal_conflict",
        () ->
            f.call(
                "derive_model", "run_id", id, "identity", identity(), "range_proposals", changed));
    assertEquals(1, requests.size());
  }

  @Test
  void invalidArgumentsAndRejectedMcpSpecDoNotPoisonConfirmedState() {
    var bad = Json.map("Jahr", Json.map("confirmed", true, "min", 1900, "max", 2100));
    f.error(
        "model_arguments_invalid",
        () -> f.call("derive_model", "run_id", id, "identity", identity(), "confirmations", bad));
    assertTrue(requests.isEmpty());
    assertFalse(f.workflow.store.<Boolean>edit(id, r -> r.containsKey("model")));
    var success = reply;
    reply =
        Json.map(
            "status",
            "INVALID_SPEC",
            "reasonCode",
            "INVALID_SPEC",
            "reason",
            "Invalid technical specification");
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertFalse(f.workflow.store.<Boolean>edit(id, r -> r.containsKey("model")));
    var invalid =
        f.call(
            "derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    assertTrue(
        Json.bool(invalid, "candidate", false)); // Existing contract: not accepted/validated.
    assertFalse(Json.bool(invalid, "candidate_created", true));
    reply = success;
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    String oldModel = f.workflow.store.edit(id, r -> Json.digest(r.get("model")));
    reply = Json.map("status", "INVALID_SPEC", "reasonCode", "INVALID_SPEC");
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        proposals(),
        "changes",
        List.of(Json.map("operation", "UPDATE_ATTRIBUTE")));
    assertEquals(oldModel, f.workflow.store.edit(id, r -> Json.digest(r.get("model"))));
  }

  @Test
  void persistedInvalidNumericConfirmationCanBeCorrectedButNotChanged() {
    var legacy =
        Json.map("baseType", Json.map("kind", "NUMERIC", "min", 1900, "max", 2100, "precision", 0));
    f.workflow.store.edit(
        id,
        r -> {
          r.put(
              "model",
              Json.map(
                  "authoring_valid",
                  false,
                  "mapping",
                  List.of(
                      Json.map(
                          "column",
                          "Jahr",
                          "type_spec",
                          legacy,
                          "confirmed",
                          Json.map("confirmed", true, "type_spec", legacy)))));
          return null;
        });
    var confirmation =
        Json.map(
            "Jahr",
            Json.map(
                "confirmed",
                true,
                "type_spec",
                Json.obj(proposals().get("Jahr")).get("type_spec")));
    f.call("derive_model", "run_id", id, "identity", identity(), "confirmations", confirmation);
    assertEquals(1, requests.size());
    assertTrue(
        f.workflow.store.<Boolean>edit(
            id, r -> !Json.list(r.get("model_argument_corrections")).isEmpty()));
    var current = f.call("status", "run_id", id);
    assertEquals(true, Json.obj(current.get("current_approvals")).get("data"));
    Json.obj(Json.obj(Json.obj(confirmation.get("Jahr")).get("type_spec")).get("baseType"))
        .put("max", 2200);
    f.error(
        "confirmed_semantics_conflict",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "confirmations",
                confirmation));
    assertEquals(1, requests.size());
  }

  @Test
  void rejectedSpecDoesNotConsumeRepairBudget() {
    f.workflow.store.edit(
        id,
        r -> {
          ModelRepairs.failed(r, "previous-check");
          return null;
        });
    String before = f.workflow.store.edit(id, r -> Json.digest(r.get("model_repair")));
    reply = Json.map("status", "INVALID_SPEC", "reason", "Malformed spec");
    f.call(
        "derive_model",
        "run_id",
        id,
        "identity",
        identity(),
        "range_proposals",
        proposals(),
        "repair_of",
        "previous-check",
        "repair_reason",
        "Technical correction");
    assertEquals(before, f.workflow.store.edit(id, r -> Json.digest(r.get("model_repair"))));
    assertFalse(f.workflow.store.<Boolean>edit(id, r -> r.containsKey("pending_model")));
  }

  @Test
  void corruptNumericGeneratorIsBlockedWithoutRepeatedCalls() {
    reply.put(
        "updatedModelText",
        Json.required(reply, "updatedModelText").replace("1900..2100", "1900..2099"));
    f.error(
        "model_numeric_contract_violation",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "range_proposals",
                proposals()));
    assertEquals(1, requests.size());
    f.error(
        "model_numeric_contract_violation",
        () ->
            f.call(
                "derive_model",
                "run_id",
                id,
                "identity",
                identity(),
                "range_proposals",
                proposals()));
    assertEquals(1, requests.size());
    f.workflow.store.edit(
        id,
        run -> {
          assertNull(f.workflow.store.file(run, "model"));
          assertTrue(Json.obj(run.get("changes")).isEmpty());
          return null;
        });
  }

  @Test
  void outdatedComponentDoesNotLeavePendingWorkOrDiagnostics() {
    componentOutdated = true;
    for (int attempt = 0; attempt < 2; attempt++) {
      f.error(
          "model_component_upgrade_required",
          () ->
              f.call(
                  "derive_model",
                  "run_id",
                  id,
                  "identity",
                  identity(),
                  "range_proposals",
                  proposals()));
    }
    assertTrue(requests.isEmpty());
    f.workflow.store.edit(
        id,
        run -> {
          assertFalse(run.containsKey("pending_model"));
          assertTrue(Json.obj(run.get("model_repair")).isEmpty());
          return null;
        });
  }
}
