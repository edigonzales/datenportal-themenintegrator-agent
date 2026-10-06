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
        "FixtureModel",
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

  @BeforeEach
  void setup() throws Exception {
    reply =
        Json.map(
            "status",
            "GENERATED",
            "complete",
            true,
            "updatedModelText",
            "INTERLIS 2.4;\nMODEL FixtureModel AT \"https://example.org/models\" VERSION \"2026-10-06\" =\n TOPIC Data = CLASS Observation = Jahr : -9223372036854775808..9223372036854775807; END Observation; END Data; END FixtureModel.\n",
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
    var r = f.call("derive_model", "run_id", id, "identity", identity());
    assertFalse(Json.bool(r, "candidate", true));
    assertEquals("authorIliModel", requests.getFirst().get("name"));
    var args = Json.obj(requests.getFirst().get("arguments"));
    assertEquals("VALIDATION", args.get("modelPurpose"));
    assertEquals("SO", args.get("ruleProfile"));
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
  void unknownDescriptionNeedsInput() {
    Path p = temp.resolve("without-description.xtf");
    Json.write(p, Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf")));
    f.call("attach", "run_id", id, "role", "metadata", "path", p.toString());
    var r = f.call("derive_model", "run_id", id, "identity", identity());
    assertEquals("NEEDS_INPUT", r.get("status"));
    assertTrue(requests.isEmpty());
  }

  @Test
  void compilerOrProofFailureRemainsCandidate() {
    reply.put("status", "PROOF_INCOMPLETE");
    reply.put("proofVerified", false);
    reply.put("candidateModelText", reply.remove("updatedModelText"));
    var r = f.call("derive_model", "run_id", id, "identity", identity());
    assertTrue(Json.bool(r, "candidate", false));
    var state = f.call("status", "run_id", id);
    assertTrue(Json.obj(state.get("changes")).isEmpty());
    var report = f.call("validate_model", "run_id", id);
    assertFalse(Json.bool(report, "valid", true));
    f.error("validation_required", () -> f.approve(id, "model", report));
  }

  @Test
  void csvAfterDerivationInvalidatesModel() {
    f.call("derive_model", "run_id", id, "identity", identity());
    Path p = temp.resolve("changed.csv");
    Json.write(p, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", p.toString());
    assertFalse(Json.bool(f.call("validate_model", "run_id", id), "valid", true));
  }

  @Test
  void observedValuesDoNotCreateCodelistsOrKeys() {
    f.call("derive_model", "run_id", id, "identity", identity());
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
    String task = Models.task("ext.existingSetting = true\n", "ch.so.fixture", "FixtureModel");
    assertTrue(task.startsWith("ext.existingSetting"));
    assertTrue(task.contains("preparePublicationWorkspace"));
    assertTrue(task.contains("failOnError.set(true)"));
    assertTrue(task.contains("encoding.set('UTF-8')"));
    assertTrue(task.contains("valueSeparator.set(';')"));
    assertTrue(task.contains("Metadatenlieferungen überspringen"));
    assertEquals(
        1, Models.task(task, "ch.so.fixture", "FixtureModel").split(Models.BEGIN, -1).length - 1);
  }

  @Test
  void changedTaskRequiresNewModelReview() {
    f.call("derive_model", "run_id", id, "identity", identity());
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
    f.call("derive_model", "run_id", id, "identity", identity());
    var report = f.call("validate", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    f.approve(id, "metadata", report);
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    f.approve(id, "model", f.call("validate_model", "run_id", id));
    f.call("apply_local_changes", "run_id", id);
  }
}
