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

  static class Datasheet implements McpClients.ToolClient {
    String xml;
    int revision = 1;
    String draft = "fixture-draft";

    Map<String, Object> snapshot() {
      return Json.map(
          "draft_id", draft, "revision", revision, "kind", "dataset", "data", Json.map());
    }

    @Override
    public Map<String, Object> call(String op, Map<String, Object> args) {
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
    assertEquals("authorIliModel", requests.getFirst().get("name"));
    var args = Json.obj(requests.getFirst().get("arguments"));
    assertEquals("VALIDATION", args.get("modelPurpose"));
    assertEquals("SO-DATA", args.get("ruleProfile"));
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
  void csvAfterDerivationInvalidatesModel() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    Path p = temp.resolve("changed.csv");
    Json.write(p, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", p.toString());
    assertFalse(Json.bool(f.call("validate_model", "run_id", id), "valid", true));
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
    assertTrue(task.contains("preparePublicationWorkspace"));
    assertTrue(task.contains("failOnError.set(true)"));
    assertTrue(task.contains("encoding.set('UTF-8')"));
    assertTrue(task.contains("valueSeparator.set(';')"));
    assertTrue(task.contains("Metadatenlieferungen überspringen"));
    assertEquals(
        1,
        Models.task(task, "ch.so.fixture", "FixtureModel_20261006").split(Models.BEGIN, -1).length
            - 1);
  }

  @Test
  void changedTaskRequiresNewModelReview() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    Path changed = temp.resolve("changed-task.gradle");
    Json.write(changed, "tasks.register('validateThemenCsv') { }\n");
    f.call(
        "stage_change",
        "run_id",
        id,
        "relative_path",
        "agi/ch.so.grundwasser.qualitaet/dataset.gradle",
        "source_path",
        changed.toString());
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
    var report = f.call("validate", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    f.approve(id, "metadata", report);
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    f.approve(id, "model", f.call("validate_model", "run_id", id));
    f.call("apply_local_changes", "run_id", id);
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
    var result = f.call("validate_model", "run_id", id);
    assertEquals(true, result.get("valid"));
    assertEquals(false, Json.obj(result.get("repair")).get("active"));
    assertTrue(Json.contents(Path.of(Json.required(result, "review_path"))).contains("FAILED"));
  }

  @Test
  void csvFailureUsesSameRepairBudgetAndRetainsItsLog() {
    f.call("derive_model", "run_id", id, "identity", identity(), "range_proposals", proposals());
    var healthy = f.workflow.gretl;
    f.workflow.gretl =
        new GretlRuntime(f.settings, new Fixtures.FakeProcess()) {
          @Override
          public Map<String, Object> gradle(
              Workflow w,
              Map<String, Object> run,
              Path snapshot,
              String task,
              List<String> properties,
              String logName) {
            Path log = w.directory(run).resolve("validation/fixture-csv.log");
            Json.write(log, "Error: Jahr outside range\n");
            return Json.map("valid", false, "log", log.toString(), "log_sha256", Json.sha(log));
          }
        };
    var failure = f.call("validate_model", "run_id", id);
    assertEquals(false, failure.get("valid"));
    var series = Json.obj(failure.get("repair"));
    f.workflow.gretl = healthy;
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
    var success = f.call("validate_model", "run_id", id);
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
}
