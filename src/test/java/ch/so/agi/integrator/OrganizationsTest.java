package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OrganizationsTest {
  @TempDir Path temp;
  Fixtures f;
  String id;

  @BeforeEach
  void setup() throws Exception {
    f = new Fixtures(temp);
    id = Json.required(f.call("start", "workflow", "organization", "organization", "neworg"), "id");
  }

  Map<String, Object> values() {
    return Json.map(
        "title",
        "Neue Organisation",
        "read_teams",
        List.of("readers"),
        "build_teams",
        List.of("builders"),
        "office_identifier",
        "ch.so.afu");
  }

  @Test
  void schemaUsesActualModel() {
    var schema = f.call("organization_schema");
    var fields = Json.obj(Json.obj(schema.get("office_rules")).get("fields"));
    assertEquals(6, fields.size());
    assertEquals(10, Json.obj(fields.get("abbreviation")).get("max_length"));
    assertTrue(Json.bool(Json.obj(fields.get("phoneNumber")), "mandatory", false));
    assertTrue(Json.bool(Json.obj(fields.get("email")), "uri", false));
  }

  @Test
  void prepareAndValidateWithoutDataset() {
    f.call("prepare_organization", "run_id", id, "values", values());
    var report = f.call("validate_organization", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    assertFalse(Files.exists(f.repo.resolve("neworg")));
    assertFalse(Json.pretty(report).contains("defaultDataset"));
    f.approve(id, "organization", report);
    var result = f.call("apply_local_changes", "run_id", id);
    assertEquals("repository_created", Json.obj(result.get("completion")).get("status"));
    assertFalse(Json.bool(Json.obj(result.get("completion")), "jenkins_job", true));
    assertEquals(
        true,
        Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("organization"));
  }

  @Test
  void missingOfficeContact() {
    var office =
        Json.map(
            "identifier",
            "ch.so.fixture",
            "name",
            "Fixture",
            "abbreviation",
            "FX",
            "phoneNumber",
            "+41",
            "email",
            "mailto:fixture@example.org");
    f.error("office_fields", () -> f.call("office", "run_id", id, "values", office));
  }

  @Test
  void lengthsAndUri() {
    var office =
        Json.map(
            "identifier",
            "ch.so.fixture",
            "name",
            "Fixture",
            "abbreviation",
            "WAYTOOLONGABBREVIATION",
            "phoneNumber",
            "+41",
            "email",
            "mailto:fixture@example.org",
            "officeAtWeb",
            "https://example.org");
    f.error("office_length", () -> f.call("office", "run_id", id, "values", office));
    office.put("abbreviation", "FX");
    office.put("email", "not-an-uri");
    f.error("office_uri", () -> f.call("office", "run_id", id, "values", office));
  }

  @Test
  void duplicateOffice() {
    var office =
        Json.map(
            "identifier",
            "ch.so.fixture",
            "name",
            "Amt für Umwelt",
            "abbreviation",
            "FX",
            "phoneNumber",
            "+41",
            "email",
            "mailto:fixture@example.org",
            "officeAtWeb",
            "https://example.org");
    f.error("duplicate_office", () -> f.call("office", "run_id", id, "values", office));
  }

  @Test
  void unknownAndEmptyTeams() {
    var values = values();
    values.put("read_teams", List.of("unknown"));
    f.error("unknown_team", () -> f.call("prepare_organization", "run_id", id, "values", values));
    values.put("read_teams", List.of());
    f.error("empty_team", () -> f.call("prepare_organization", "run_id", id, "values", values));
  }

  @Test
  void confirmedNewTeamPreservesExisting() {
    var values = values();
    values.put("new_teams", Json.map("newbuilders", Json.map("users", List.of("confirmed-user"))));
    values.put("build_teams", List.of("newbuilders"));
    f.error(
        "users_unconfirmed", () -> f.call("prepare_organization", "run_id", id, "values", values));
    values.put("confirmed_users", List.of("confirmed-user"));
    f.call("prepare_organization", "run_id", id, "values", values);
    var state = f.call("status", "run_id", id);
    Path candidate =
        Path.of(
            Json.required(
                Json.obj(Json.obj(state.get("changes")).get("shared/gretl-datenportal-teams.yaml")),
                "path"));
    var teams = Json.obj(Organizations.yaml(candidate).get("teams"));
    assertTrue(teams.keySet().containsAll(Set.of("readers", "builders", "newbuilders")));
  }

  @Test
  void existingTeamCannotBeOverwritten() {
    var values = values();
    values.put("new_teams", Json.map("builders", Json.map("users", List.of("new-user"))));
    values.put("confirmed_users", List.of("new-user"));
    f.error("team_conflict", () -> f.call("prepare_organization", "run_id", id, "values", values));
  }

  @Test
  void newOfficeIsStaged() {
    var v = values();
    v.remove("office_identifier");
    v.put(
        "office",
        Json.map(
            "identifier",
            "ch.so.fixture",
            "name",
            "Fixtureoffice",
            "abbreviation",
            "FX",
            "phoneNumber",
            "+41",
            "email",
            "mailto:fixture@example.org",
            "officeAtWeb",
            "https://example.org"));
    String before = Json.sha(f.repo.resolve("shared/data/offices.xtf"));
    f.call("prepare_organization", "run_id", id, "values", v);
    assertEquals(before, Json.sha(f.repo.resolve("shared/data/offices.xtf")));
    assertTrue(
        Json.obj(f.call("status", "run_id", id).get("changes"))
            .containsKey("shared/data/offices.xtf"));
  }

  @Test
  void applyRequiresActualOrganizationApproval() {
    f.call("prepare_organization", "run_id", id, "values", values());
    f.call("validate_organization", "run_id", id);
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
  }

  @Test
  void existingOrganizationConflict() {
    Json.write(f.repo.resolve("neworg/settings.gradle"), "existing\n");
    f.error(
        "organization_exists",
        () -> f.call("prepare_organization", "run_id", id, "values", values()));
  }

  @Test
  void emptyNewTeamRejected() {
    var v = values();
    v.put("new_teams", Json.map("empty", Json.map("users", List.of())));
    f.error("empty_team", () -> f.call("prepare_organization", "run_id", id, "values", v));
  }
}
