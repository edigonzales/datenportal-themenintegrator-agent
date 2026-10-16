package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** All approvals here are synthetic and restricted to isolated temporary repositories. */
class OfficesTest {
  @TempDir Path temp;
  Fixtures f;
  String id;

  static Map<String, Object> bfs() {
    return Json.map(
        "identifier",
        "ch.bfs",
        "name",
        "Bundesamt für Statistik",
        "abbreviation",
        "BFS",
        "email",
        "mailto:info@bfs.admin.ch",
        "officeAtWeb",
        "https://www.bfs.admin.ch/",
        "phoneNumber",
        "058 463 66 66");
  }

  @BeforeEach
  void setup() throws Exception {
    f = new Fixtures(temp);
    id = Json.required(f.call("start", "workflow", "office"), "id");
  }

  Map<String, Object> prepare() {
    return f.call("office", "run_id", id, "mode", "create", "values", bfs());
  }

  Map<String, Object> review() {
    return f.call("validate", "run_id", id);
  }

  Map<String, Object> state() {
    return f.workflow.store.read(id, r -> r);
  }

  @Test
  void bfsWithoutOrganizationAndOnlyCatalogChanges() throws Exception {
    Files.delete(f.repo.resolve("shared/gretl-datenportal-teams.yaml"));
    var schema = f.call("office_schema", "values", bfs());
    assertEquals(List.of(), schema.get("missing"));
    assertFalse(schema.containsKey("existing_teams"));
    assertNull(state().get("organization"));
    String original = Json.sha(f.repo.resolve(Offices.PATH));
    prepare();
    assertEquals(original, Json.sha(f.repo.resolve(Offices.PATH)));
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    var report = review();
    assertEquals(true, report.get("valid"));
    String html = Files.readString(Path.of(Json.required(report, "review_path")));
    assertTrue(html.contains("Bundesamt für Statistik"));
    assertTrue(html.contains("058 463 66 66"));
    assertEquals(6, Json.list(report.get("office_changes")).size());
    f.approve(id, "office", report);
    var applied = f.call("apply_local_changes", "run_id", id);
    assertEquals(Set.of(Offices.PATH), Json.obj(state().get("changes")).keySet());
    assertEquals("office_updated", Json.obj(applied.get("completion")).get("status"));
    assertTrue(Json.contents(f.repo.resolve(Offices.PATH)).contains("ch.bfs"));
    assertEquals(
        true, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("office"));
    assertEquals(false, prepare().get("changed"));
    assertEquals(report.get("fingerprint"), f.workflow.fingerprint(state(), "office"));
    f.call("apply_local_changes", "run_id", id);
  }

  @Test
  void correctionPreservesOtherOfficesAndObjectIdentity() {
    var before = new Offices(f.workflow).catalog(f.repo.resolve(Offices.PATH));
    var office = new LinkedHashMap<>(before.getFirst());
    String identifier = Json.required(office, "identifier");
    var node =
        Xml.all(Xml.parse(f.repo.resolve(Offices.PATH))).stream()
            .filter(e -> identifier.equals(Xml.text(e, "identifier")))
            .findFirst()
            .orElseThrow();
    String tid = node.getAttributeNS(Xml.ILI, "tid");
    office.put("phoneNumber", "032 000 00 00");
    f.call("office", "run_id", id, "mode", "update", "values", office);
    var after = new Offices(f.workflow).catalog(f.workflow.offices(state()));
    assertEquals(before.size(), after.size());
    assertEquals(before.subList(1, before.size()), after.subList(1, after.size()));
    var updated =
        Xml.all(Xml.parse(f.workflow.offices(state()))).stream()
            .filter(e -> identifier.equals(Xml.text(e, "identifier")))
            .findFirst()
            .orElseThrow();
    assertEquals(tid, updated.getAttributeNS(Xml.ILI, "tid"));
    var report = review();
    assertEquals(1, Json.list(report.get("office_changes")).size());
    assertEquals(
        "phoneNumber", Json.obj(Json.list(report.get("office_changes")).getFirst()).get("field"));
    f.approve(id, "office", report);
    assertEquals(
        false, f.call("office", "run_id", id, "mode", "update", "values", office).get("changed"));
    assertEquals(
        true, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("office"));
  }

  @Test
  void unchangedUpdateCreatesNoCandidateOrPr() {
    var office = new Offices(f.workflow).catalog(f.repo.resolve(Offices.PATH)).getFirst();
    assertEquals(
        false, f.call("office", "run_id", id, "mode", "update", "values", office).get("changed"));
    assertTrue(Json.obj(state().get("changes")).isEmpty());
    assertEquals(false, f.call("prepare_pr", "run_id", id, "environment", "local").get("required"));
  }

  @Test
  void explicitModesAndFieldRules() {
    f.error("office_mode_required", () -> f.call("office", "run_id", id, "values", bfs()));
    f.error(
        "office_not_found",
        () -> f.call("office", "run_id", id, "mode", "update", "values", bfs()));
    var existing = new Offices(f.workflow).catalog(f.repo.resolve(Offices.PATH)).getFirst();
    f.error(
        "office_exists",
        () -> f.call("office", "run_id", id, "mode", "create", "values", existing));
    for (String field : bfs().keySet()) {
      var values = bfs();
      values.remove(field);
      f.error(
          "office_fields",
          () -> f.call("office", "run_id", id, "mode", "create", "values", values));
    }
    var invalid = bfs();
    invalid.put("email", "plain-address");
    f.error(
        "office_uri", () -> f.call("office", "run_id", id, "mode", "create", "values", invalid));
    invalid.put("email", bfs().get("email"));
    invalid.put("abbreviation", "TOO-LONG-ABBREVIATION");
    f.error(
        "office_length", () -> f.call("office", "run_id", id, "mode", "create", "values", invalid));
    invalid.put("abbreviation", existing.get("abbreviation"));
    f.error(
        "duplicate_office",
        () -> f.call("office", "run_id", id, "mode", "create", "values", invalid));
  }

  @Test
  void foreignInputsOperationsAndGatesAreRejected() {
    for (String field :
        List.of("organization", "identifier", "issue", "data_path", "metadata_path"))
      f.error(
          "invalid_arguments", () -> f.call("start", "workflow", "office", field, "unexpected"));
    for (String op :
        List.of("analyze", "baseline", "validate_organization", "repository_datasheet"))
      f.error("wrong_workflow", () -> f.call(op, "run_id", id));
    for (String op : List.of("deliver", "seed", "publication_plan"))
      f.error("wrong_workflow", () -> f.call(op, "run_id", id, "environment", "local"));
    f.error(
        "wrong_workflow", () -> f.call("metadata", "run_id", id, "operation", "describe_schema"));
    f.error(
        "wrong_workflow",
        () ->
            f.call(
                "stage_change",
                "run_id",
                id,
                "relative_path",
                "shared/gretl-datenportal-teams.yaml",
                "source_path",
                f.repo.resolve("shared/gretl-datenportal-teams.yaml").toString()));
    f.error(
        "invalid_gate",
        () ->
            f.call(
                "approve",
                "run_id",
                id,
                "gate",
                "organization",
                "fingerprint",
                "x",
                "human_statement",
                "SYNTHETIC FIXTURE"));
  }

  @Test
  void correctionsInvalidateReviewAndApproval() {
    prepare();
    var report = review();
    f.approve(id, "office", report);
    var changed = bfs();
    changed.put("phoneNumber", "058 000 00 00");
    f.call("office", "run_id", id, "mode", "update", "values", changed);
    f.error("stale_review", () -> f.approve(id, "office", report));
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    f.error("approval_required", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
    f.approve(id, "office", review());
    f.call("apply_local_changes", "run_id", id);
  }

  @Test
  void tamperedCandidateAndConcurrentCatalogAreBlocked() {
    prepare();
    f.approve(id, "office", review());
    Path candidate = f.workflow.offices(state());
    String bytes = Json.contents(candidate);
    Json.write(candidate, bytes + " ");
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    Json.write(candidate, bytes);
    Json.write(f.repo.resolve(Offices.PATH), Json.contents(f.repo.resolve(Offices.PATH)) + " ");
    assertEquals(
        false, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("office"));
    f.error(
        "local_changes_modified", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
  }

  @Test
  void modelChangesInvalidateApprovalWithoutCallingMcp() throws Exception {
    Path models = temp.resolve("models");
    Files.createDirectories(models);
    try (var paths = Files.list(Fixtures.FIXTURES.resolve("models"))) {
      for (Path p : paths.toList()) Files.copy(p, models.resolve(p.getFileName()));
    }
    f.settings.values.put("model_dirs", List.of(models.toString()));
    prepare();
    f.approve(id, "office", review());
    Path base = models.resolve("SO_AGI_DataCatalog_Base_20260529.ili");
    Files.writeString(base, "\n!! Changed test dependency\n", StandardOpenOption.APPEND);
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
  }

  @Test
  void failedRevalidationBlocksPreviouslyApprovedBytes() throws Exception {
    boolean[] fail = {false};
    boolean[] crash = {false};
    var isolated =
        new Fixtures(
            temp.resolve("failure"),
            new Fixtures.FakeProcess() {
              @Override
              public Result run(List<String> args, Path cwd, int timeout, Path log) {
                if (crash[0])
                  throw new Problem("synthetic_validator_unavailable", "SYNTHETIC fixture failure");
                if (!fail[0]) return super.run(args, cwd, timeout, log);
                if (log != null) Json.write(log, "Error: SYNTHETIC validation failure\n");
                return new Result(1, "Error: SYNTHETIC validation failure\n");
              }
            },
            (n, a) -> {
              throw new AssertionError("No datasheet calls");
            },
            (n, a) -> {
              throw new AssertionError("No model calls");
            });
    String run = Json.required(isolated.call("start", "workflow", "office"), "id");
    isolated.call("office", "run_id", run, "mode", "create", "values", bfs());
    isolated.approve(run, "office", isolated.call("validate", "run_id", run));
    fail[0] = true;
    var failed = isolated.call("validate", "run_id", run);
    assertEquals(false, failed.get("valid"));
    isolated.error("validation_required", () -> isolated.approve(run, "office", failed));
    isolated.error("approval_required", () -> isolated.call("apply_local_changes", "run_id", run));
    fail[0] = false;
    isolated.approve(run, "office", isolated.call("validate", "run_id", run));
    crash[0] = true;
    isolated.error(
        "synthetic_validator_unavailable", () -> isolated.call("validate", "run_id", run));
    isolated.error("approval_required", () -> isolated.call("apply_local_changes", "run_id", run));
  }

  @Test
  void subsequentCorrectionReviewsAgainstAppliedCatalog() {
    prepare();
    f.approve(id, "office", review());
    f.call("apply_local_changes", "run_id", id);
    var correction = bfs();
    correction.put("phoneNumber", "058 000 00 00");
    f.call("office", "run_id", id, "mode", "update", "values", correction);
    var changes = Json.list(review().get("office_changes"));
    assertEquals(1, changes.size());
    assertEquals("058 463 66 66", Json.obj(changes.getFirst()).get("before"));
    assertEquals("058 000 00 00", Json.obj(changes.getFirst()).get("after"));
  }

  @Test
  void scopeCheckedAtApplyAndPrEvenForTamperedState() {
    prepare();
    f.approve(id, "office", review());
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("changes")).put("other.txt", Json.map());
          return null;
        });
    f.error("office_scope", () -> f.call("apply_local_changes", "run_id", id));
    f.error("office_scope", () -> f.call("prepare_pr", "run_id", id, "environment", "local"));
  }
}
