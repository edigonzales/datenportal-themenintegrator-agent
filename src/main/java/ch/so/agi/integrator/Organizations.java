package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class Organizations {
  final Workflow w;

  public Organizations(Workflow w) {
    this.w = w;
  }

  public static Map<String, Object> yaml(Path file) {
    try {
      var options = new LoaderOptions();
      options.setAllowDuplicateKeys(false);
      Object parsed = new Yaml(new SafeConstructor(options)).load(Json.contents(file));
      return Json.obj(parsed);
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("invalid_yaml", "YAML ist nicht gültig.", "path", file.toString());
    }
  }

  public Map<String, Object> officeRules() {
    return new Offices(w).officeRules();
  }

  public Object schema(Map<String, Object> args) {
    var teams = yaml(w.settings.topics.resolve("shared/gretl-datenportal-teams.yaml"));
    var office = officeRules();
    var values = Json.obj(args.get("values"));
    var missing = new ArrayList<>();
    for (String field : List.of("title", "read_teams", "build_teams"))
      if (!values.containsKey(field)) missing.add(field);
    if (!values.containsKey("office_identifier") && !values.containsKey("office"))
      missing.add("office_identifier oder office");
    if (values.containsKey("office"))
      Json.obj(office.get("fields"))
          .forEach(
              (key, rule) -> {
                if (Json.bool(Json.obj(rule), "mandatory", false)
                    && Json.str(Json.obj(values.get("office")), key, "").isBlank())
                  missing.add("office." + key);
              });
    var offices = new Offices(w).catalog(w.settings.topics.resolve(Offices.PATH));
    return Json.map(
        "organization_required",
        List.of(
            "organization", "title", "read_teams", "build_teams", "office_identifier oder office"),
        "office_rules",
        office,
        "existing_teams",
        teams.get("teams"),
        "existing_offices",
        offices,
        "new_team_requires",
        List.of("users (nicht leer)", "confirmed_users"),
        "missing",
        missing,
        "note",
        "Jenkins-Organisationskennung und Office-Identifier sind getrennt. Leere Organisationen erzeugen noch keinen Jenkins-Job.");
  }

  public Object office(Map<String, Object> r, Map<String, Object> values) {
    return new Offices(w).prepare(r, values, null);
  }

  private void organization(Map<String, Object> r) {
    if (!Json.str(r, "workflow", "topic").equals("organization"))
      throw new Problem("wrong_workflow", "Eigenständiger Organisationsvorgang erforderlich.");
  }

  public Object prepare(Map<String, Object> r, Map<String, Object> values) {
    organization(r);
    String org = Json.required(r, "organization");
    if (!org.matches("[a-z][a-z0-9_-]*"))
      throw new Problem(
          "invalid_identifier",
          "Jenkins-Organisation muss aus Kleinbuchstaben, Ziffern, _ oder - bestehen.");
    if (Files.exists(w.settings.topics.resolve(org)) && !r.containsKey("organization_spec"))
      throw new Problem("organization_exists", "Organisation existiert bereits.");
    String title = Json.required(values, "title");
    for (String field : values.keySet())
      if (!Set.of(
              "title",
              "description",
              "read_teams",
              "build_teams",
              "office_identifier",
              "office",
              "new_teams",
              "confirmed_users")
          .contains(field))
        throw new Problem("invalid_arguments", "Unbekannte Organisationsangabe.", "field", field);
    var catalog = yaml(w.repoFile(r, "shared/gretl-datenportal-teams.yaml"));
    var teams = Json.obj(catalog.get("teams"));
    var newTeams = Json.obj(values.get("new_teams"));
    var confirmed = Json.strings(values.get("confirmed_users"));
    for (var entry : newTeams.entrySet()) {
      String name = entry.getKey();
      Store.identifier(name);
      var users = Json.strings(Json.obj(entry.getValue()).get("users"));
      if (users.isEmpty()
          || users.stream().anyMatch(String::isBlank)
          || new HashSet<>(users).size() != users.size())
        throw new Problem(
            "empty_team", "Team benötigt eindeutige, nicht leere Benutzerkennungen.", "team", name);
      if (!confirmed.containsAll(users))
        throw new Problem(
            "users_unconfirmed",
            "Neue Teammitgliedschaften benötigen bestätigte Benutzerkennungen.",
            "team",
            name,
            "missing",
            users.stream().filter(u -> !confirmed.contains(u)).toList());
      if (teams.containsKey(name) && !Json.obj(teams.get(name)).equals(entry.getValue()))
        throw new Problem(
            "team_conflict", "Vorhandenes Team wird nicht überschrieben.", "team", name);
      teams.put(name, entry.getValue());
    }
    var permissions = Json.map();
    for (String level : List.of("read", "build")) {
      var ids = Json.strings(values.get(level + "_teams"));
      if (ids.isEmpty())
        throw new Problem(
            "empty_team",
            "Lese- und Build-Berechtigungen benötigen mindestens ein Team.",
            "permission",
            level);
      for (String id : ids) {
        var team = Json.obj(teams.get(id));
        if (team.isEmpty()) throw new Problem("unknown_team", "Team fehlt im Katalog.", "team", id);
        if (Json.strings(team.get("users")).isEmpty())
          throw new Problem("empty_team", "Team hat keine Mitglieder.", "team", id);
      }
      permissions.put(level, ids.stream().map(id -> Json.map("team", id)).toList());
    }
    String officeId;
    if (values.containsKey("office")) {
      office(r, Json.obj(values.get("office")));
      officeId = Json.required(Json.obj(values.get("office")), "identifier");
      if (values.containsKey("office_identifier")
          && !officeId.equals(values.get("office_identifier")))
        throw new Problem("office_conflict", "Office-Identifier widersprechen sich.");
    } else officeId = Json.required(values, "office_identifier");
    if (!Xml.checkOffices(w.offices(r), officeId).isEmpty())
      throw new Problem(
          "unknown_office",
          "Dienststelle muss eindeutig im Katalog vorhanden sein.",
          "identifier",
          officeId);
    Path candidates = w.directory(r).resolve("organization");
    String description =
        Json.str(values, "description", "Datenportal-Ausführungen: " + title + ".");
    var files =
        Json.map(
            "gretl-datenportal-job.yaml",
            new Yaml()
                .dump(
                    Json.map(
                        "title", title, "description", description, "permissions", permissions)),
            "settings.gradle",
            "rootProject.name = " + Workspace.groovy(org) + "\n",
            "build.gradle",
            "plugins { id 'ch.so.agi.gretl' }\n\ndescription = "
                + Workspace.groovy(description)
                + "\next.datenportalOrganization = [id: "
                + Workspace.groovy(org)
                + ", title: "
                + Workspace.groovy(title)
                + ", label: "
                + Workspace.groovy(title)
                + "]\napply from: '../shared/gradle/organisation-common.gradle'\n");
    files.forEach(
        (name, text) -> {
          Path p = candidates.resolve(name);
          Json.write(p, text.toString());
          w.stage(r, org + "/" + name, p);
        });
    if (!newTeams.isEmpty()) {
      Path p = candidates.resolve("teams.yaml");
      Json.write(p, new Yaml().dump(catalog));
      w.stage(r, "shared/gretl-datenportal-teams.yaml", p);
    }
    r.put(
        "organization_spec",
        Json.map(
            "identifier",
            org,
            "title",
            title,
            "office_identifier",
            officeId,
            "read_teams",
            values.get("read_teams"),
            "build_teams",
            values.get("build_teams"),
            "confirmed_users",
            confirmed));
    return Json.map(
        "prepared",
        true,
        "changes",
        r.get("changes"),
        "next",
        "validate_organization",
        "message",
        "Kein Datenthema und kein defaultDataset angelegt.");
  }

  public Object validate(Map<String, Object> r) {
    organization(r);
    if (!r.containsKey("organization_spec"))
      throw new Problem("organization_missing", "Organisation zuerst vorbereiten.");
    var spec = Json.obj(r.get("organization_spec"));
    String org = Json.required(r, "organization");
    var job = yaml(w.repoFile(r, org + "/gretl-datenportal-job.yaml"));
    var teams = Json.obj(yaml(w.repoFile(r, "shared/gretl-datenportal-teams.yaml")).get("teams"));
    var errors = new ArrayList<Object>();
    var baselineTeams =
        Json.obj(
            yaml(w.settings.topics.resolve("shared/gretl-datenportal-teams.yaml")).get("teams"));
    baselineTeams.forEach(
        (name, team) -> {
          if (!Objects.equals(team, teams.get(name)))
            errors.add(
                Json.map(
                    "code",
                    "team_conflict",
                    "team",
                    name,
                    "message",
                    "Vorhandenes Team wurde verändert."));
        });
    teams.forEach(
        (name, team) -> {
          if (!baselineTeams.containsKey(name)
              && !Json.strings(spec.get("confirmed_users"))
                  .containsAll(Json.strings(Json.obj(team).get("users"))))
            errors.add(
                Json.map(
                    "code",
                    "users_unconfirmed",
                    "team",
                    name,
                    "message",
                    "Neue Teammitglieder sind nicht bestätigt."));
        });
    if (!Objects.equals(job.get("title"), spec.get("title")))
      errors.add(
          Json.map(
              "code",
              "organization_title",
              "message",
              "Organisationstitel stimmen nicht überein."));
    for (String level : List.of("read", "build")) {
      var refs = Json.list(Json.obj(job.get("permissions")).get(level));
      if (refs.isEmpty())
        errors.add(
            Json.map(
                "code", "empty_team", "permission", level, "message", "Berechtigungen sind leer."));
      for (Object ref : refs) {
        String id = Json.str(Json.obj(ref), "team", "");
        var team = Json.obj(teams.get(id));
        if (team.isEmpty() || Json.strings(team.get("users")).isEmpty())
          errors.add(
              Json.map("code", "unknown_team", "team", id, "message", "Team fehlt oder ist leer."));
      }
    }
    errors.addAll(Xml.checkOffices(w.offices(r), Json.required(spec, "office_identifier")));
    var ili = w.validateXtf(r, null, "organization-validation");
    var report =
        Json.map(
            "valid",
            errors.isEmpty() && Json.bool(ili, "valid", false),
            "errors",
            errors,
            "ilivalidator",
            ili,
            "organization",
            spec,
            "changes",
            r.get("changes"),
            "jenkins_job",
            false,
            "message",
            "Der Seed erzeugt für eine Organisation ohne Datenthema noch keinen Jenkins-Job.");
    report.put("diagnostic_history", r.get("diagnostic_history"));
    report.put("fingerprint", w.fingerprint(r, "organization"));
    Json.obj(r.get("checks")).put("organization", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("organization-review.html"),
            Reports.Kind.ORGANIZATION,
            Json.str(r, "organization", null),
            report));
    return result;
  }
}
