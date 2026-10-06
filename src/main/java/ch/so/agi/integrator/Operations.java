package ch.so.agi.integrator;

import java.util.*;

public final class Operations {
  public record Operation(String description, Map<String, Object> schema) {}

  public static final Map<String, Operation> ALL = new LinkedHashMap<>();

  static {
    add(
        "start",
        "Vorgang aufnehmen. Originaldateien bleiben unverändert.",
        "organization!",
        "identifier",
        "workflow",
        "issue",
        "source_environment",
        "data_path",
        "metadata_path");
    add("status", "Gespeicherten Vorgang und aktuelle Freigaben lesen.", "run_id!");
    add(
        "attach",
        "Neue Datei aufnehmen und abhängige Freigaben entwerten.",
        "run_id!",
        "path!",
        "role!");
    add("baseline", "Angenommenen Metadatenstand laden.", "run_id!");
    add("analyze", "CSV vollständig prüfen und HTML-Vorschau schreiben.", "run_id!");
    add("provenance", "Herkunft fachlicher Angaben festhalten.", "run_id!", "entries:array!");
    add(
        "xlsx",
        "XLSX-Koordinaten und gespeicherte Formelwerte lesen.",
        "run_id!",
        "sheet",
        "offset:integer",
        "limit:integer");
    add(
        "metadata",
        "Bestehenden Datenblatt-MCP über seine öffentlichen Werkzeuge verwenden.",
        "run_id!",
        "operation!",
        "arguments:object");
    add("validate", "XTF, Dienststellen, CSV-Vertrag und allfälliges Modell prüfen.", "run_id!");
    add(
        "approve",
        "Tatsächliches menschliches OK für einen konkreten Prüfstand protokollieren.",
        "run_id!",
        "gate!",
        "fingerprint!",
        "human_statement!");
    add(
        "stage_change",
        "Erlaubte fachliche Repository-Änderung zur Prüfung sichern.",
        "run_id!",
        "relative_path!",
        "source_path!");
    add(
        "apply_local_changes",
        "Freigegebene Änderungen unter Konfliktprüfung lokal übernehmen.",
        "run_id!");
    add(
        "transform",
        "Java-Konverter mit JUnit-Tests ausführen.",
        "run_id!",
        "converter!",
        "tests!",
        "policy!",
        "instructions!");
    add(
        "publication_plan",
        "Konkreten INT/PROD-/Seed-Plan zur menschlichen Entscheidung zeigen.",
        "run_id!",
        "environment!");
    add(
        "prepare_pr",
        "Isolierten fachlichen PR vorbereiten. Merge erfolgt durch einen Menschen.",
        "run_id!",
        "environment!");
    add(
        "deliver",
        "Lieferung um eine Phase weiterführen; bestehende Läufe weiter aufklären.",
        "run_id!",
        "environment");
    add(
        "seed",
        "Seed einmal starten oder gespeicherten Lauf weiter abfragen.",
        "run_id!",
        "environment");
    add(
        "reconcile",
        "Unbestätigte Lieferung einem bestehenden Jenkins-Lauf zuordnen.",
        "run_id!",
        "environment!",
        "job!",
        "queue!");
    add(
        "office",
        "Office-Katalog mit bestätigten Pflichtangaben ergänzen.",
        "run_id!",
        "values:object!");
    add(
        "reconcile_seed",
        "Unbestätigten Seed einer bestehenden Queue zuordnen.",
        "run_id!",
        "environment!",
        "queue!");
    add(
        "verify_delivery",
        "Angenommene Publikation erneut prüfen; keinen Upload starten.",
        "run_id!",
        "environment!");
    add(
        "retry_delivery",
        "Nur eindeutig fehlgeschlagene, nicht publizierte Lieferung erneut ermöglichen.",
        "run_id!",
        "environment!");
    add(
        "migrate_run",
        "Alten Vorgang mit Vorschau und Sicherung übernehmen; Freigaben historisch halten.",
        "run_id!",
        "apply:boolean");
    add(
        "organization_schema",
        "Office-Regeln aus dem konfigurierten Modell und bestehende Teams liefern.",
        "organization",
        "values:object");
    add(
        "prepare_organization",
        "Organisationsdateien, Teams und Office als Kandidaten vorbereiten.",
        "run_id!",
        "values:object!");
    add("validate_organization", "Office-XTF, YAML, Teamreferenzen und Gradle prüfen.", "run_id!");
    add(
        "derive_model",
        "Explizit angefordertes flaches INTERLIS-Modell über den Fach-MCP ableiten.",
        "run_id!",
        "identity:object!",
        "confirmations:object",
        "changes:array",
        "allow_breaking:boolean");
    add(
        "validate_model",
        "Modellstand und GRETL-CSV-Prüfung in isolierter Arbeitskopie prüfen.",
        "run_id!");
  }

  private static void add(String name, String description, String... arguments) {
    var props = Json.map();
    var required = new ArrayList<String>();
    for (String arg : arguments) {
      boolean needed = arg.endsWith("!");
      String s = needed ? arg.substring(0, arg.length() - 1) : arg;
      String[] parts = s.split(":");
      String key = parts[0];
      var prop = Json.map("type", parts.length > 1 ? parts[1] : "string");
      if (parts.length > 1 && parts[1].equals("array"))
        prop.put("items", Json.map("type", "object"));
      props.put(key, prop);
      if (needed) required.add(key);
    }
    ALL.put(
        name,
        new Operation(
            description,
            Json.map(
                "type",
                "object",
                "properties",
                props,
                "required",
                required,
                "additionalProperties",
                false)));
  }

  public static void validate(String name, Map<String, Object> args) {
    var op = ALL.get(name);
    if (op == null) throw new Problem("unknown_operation", "Operation ist nicht bekannt.");
    var props = Json.obj(op.schema.get("properties"));
    for (String k : args.keySet()) {
      if (!props.containsKey(k))
        throw new Problem("invalid_arguments", "Unbekanntes Argument.", "field", k);
      Object v = args.get(k);
      String t = Json.str(Json.obj(props.get(k)), "type", "");
      boolean ok =
          switch (t) {
            case "string" -> v instanceof String;
            case "integer" -> v instanceof Integer || v instanceof Long;
            case "boolean" -> v instanceof Boolean;
            case "object" -> v instanceof Map;
            case "array" -> v instanceof List;
            default -> false;
          };
      if (!ok)
        throw new Problem(
            "invalid_arguments", "Argument hat einen falschen Typ.", "field", k, "expected", t);
    }
    for (Object k : Json.list(op.schema.get("required")))
      if (!args.containsKey(k))
        throw new Problem("missing_argument", "Pflichtangabe fehlt.", "field", k);
  }
}
