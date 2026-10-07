package ch.so.agi.integrator;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.TextType;
import java.net.URI;
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
    var offices =
        Xml.all(Xml.parse(w.settings.topics.resolve("shared/data/offices.xtf"))).stream()
            .filter(e -> Xml.local(e).equals("Office.Office"))
            .map(Xml::structure)
            .toList();
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
    return w.stage(r, "shared/data/offices.xtf", candidate);
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
    var ili =
        w.validator.validate(null, w.offices(r), w.directory(r).resolve("organization-validation"));
    Path snapshot = new Workspace(w).snapshot(r, "organization");
    Path assertions = w.directory(r).resolve("organization-validation/check-organization.gradle");
    Json.write(
        assertions,
        "gradle.projectsEvaluated {\n def p=gradle.rootProject\n def organization=p.ext.datenportalOrganization\n if(p.name != "
            + Workspace.groovy(org)
            + " || organization.id != "
            + Workspace.groovy(org)
            + " || organization.title != "
            + Workspace.groovy(Json.required(spec, "title"))
            + ") throw new GradleException('Organisation: Settings, Kennung und Titel sind inkonsistent')\n if(organization.defaultDataset) throw new GradleException('Leere Organisation darf kein defaultDataset definieren')\n}\n");
    var gradle =
        new Workspace(w)
            .gradle(
                r,
                snapshot,
                "tasks",
                List.of("--all", "-I", assertions.toString()),
                "organization-gradle");
    var report =
        Json.map(
            "valid",
            errors.isEmpty() && Json.bool(ili, "valid", false) && Json.bool(gradle, "valid", false),
            "errors",
            errors,
            "ilivalidator",
            ili,
            "gradle",
            gradle,
            "organization",
            spec,
            "changes",
            r.get("changes"),
            "jenkins_job",
            false,
            "message",
            "Der Seed erzeugt für eine Organisation ohne Datenthema noch keinen Jenkins-Job.");
    report.put("fingerprint", w.fingerprint(r, "organization"));
    Json.obj(r.get("checks")).put("organization", report);
    var result = new LinkedHashMap<>(report);
    result.put(
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("organization-review.html"),
            "Organisation und Berechtigungen prüfen",
            report));
    return result;
  }
}
