package ch.so.agi.integrator;

import java.util.*;

public final class Operations {
  public record Operation(String description, Map<String, Object> schema) {}

  public static final Map<String, Operation> ALL = new LinkedHashMap<>();

  static {
    add(
        "operation_schema",
        "Öffentlichen Werkzeugvertrag lesen; keine Quellcodesuche. Ohne run_id und ohne Zustandsänderung.",
        "operation!");
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
    add(
        "status",
        "Gespeicherten Vorgang lesen; current_approvals enthält je Gate true nur für den weiterhin gültigen konkreten Prüfstand. Vor Wiederaufnahme und bei unklarem Upload zuerst lesen; kein zweites start.",
        "run_id!");
    add(
        "attach",
        "Neue Datei aufnehmen und abhängige Freigaben entwerten.",
        "run_id!",
        "path!",
        "role!");
    add(
        "repository_datasheet",
        "Datenblatt im Themenordner anhand Modell und Identifier finden und unverändert als Quelle sichern; kein Entwurf. Bei mehreren Kandidaten relative_path wählen.",
        "run_id!",
        "relative_path");
    add(
        "prepare_model_reference",
        "Nach erfolgreicher Modellprüfung Modellreferenz über den Datenblatt-MCP als Kandidat vorbereiten.",
        "run_id!");
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
        "range_proposals:object",
        "repair_of",
        "repair_reason",
        "changes:array",
        "allow_breaking:boolean");
    add(
        "reset_model_repair",
        "Neue Reparaturserie nach ausdrücklichem menschlichem Neuauftrag.",
        "run_id!",
        "human_statement!");
    add(
        "validate_model",
        "Modell und CSV mit Java prüfen. scope=model ist eine Zwischenprüfung ohne Freigabewirkung; integration prüft das vollständige Paket (Standard).",
        "run_id!",
        "scope");
  }

  private static void add(String name, String description, String... arguments) {
    var op = definition(description, arguments);
    if (name.equals("metadata"))
      Json.obj(Json.obj(op.schema().get("properties")).get("operation"))
          .put("enum", MetadataContracts.NAMES);
    ALL.put(name, op);
  }

  static Operation definition(String description, String... arguments) {
    var props = Json.map();
    var required = new ArrayList<String>();
    for (String arg : arguments) {
      boolean needed = arg.endsWith("!");
      String s = needed ? arg.substring(0, arg.length() - 1) : arg;
      String[] parts = s.split(":");
      String key = parts[0];
      var prop =
          Json.map(
              "type", parts.length > 1 ? parts[1] : "string", "description", fieldDescription(key));
      if (key.equals("scope")) prop.put("enum", List.of("model", "integration"));
      if (key.equals("workflow")) prop.put("enum", List.of("topic", "model", "organization"));
      if (key.equals("role"))
        prop.put("enum", List.of("data", "metadata", "metadata_source", "model"));
      if (key.equals("policy")) prop.put("enum", List.of("supplier", "recurring"));
      if (key.equals("identity")) {
        prop.put(
            "properties",
            Json.map(
                "name",
                    Json.map(
                        "type",
                        "string",
                        "description",
                        "Bestätigter INTERLIS-Modellname mit Datumssuffix."),
                "uri", Json.map("type", "string", "description", "Absolute AT-URI."),
                "version",
                    Json.map("type", "string", "description", "ISO-Versionsdatum YYYY-MM-DD.")));
        prop.put("required", List.of("name", "uri", "version"));
      }
      if (parts.length > 1 && parts[1].equals("array"))
        prop.put("items", Json.map("type", "object"));
      props.put(key, prop);
      if (needed) required.add(key);
    }
    return new Operation(
        description,
        Json.map(
            "type",
            "object",
            "properties",
            props,
            "required",
            required,
            "additionalProperties",
            false));
  }

  static String fieldDescription(String key) {
    return switch (key) {
      case "organization" -> "Bestätigter Organisationsidentifier; keine Dienststellenkennung.";
      case "identifier" ->
          "Themen-/Serienidentifier; bei topic/model erforderlich, bei organization weglassen.";
      case "workflow" ->
          "topic für Integration samt Modell und Lieferung; model nur eigenständige Modellierung; organization nur Organisationsanlage. Standard topic.";
      case "issue" -> "Ausgabe einer Serie, z.B. 2025; getrennt vom Serienidentifier.";
      case "run_id" ->
          "Vorhandene 32-stellige Vorgangskennung. Bei Wiederaufnahme status lesen; keinen zweiten Vorgang starten.";
      case "data_path" ->
          "Verifizierter absoluter CSV-Kopiepfad. start nimmt CSV automatisch als original_data und data auf.";
      case "metadata_path" ->
          "Absolute vorhandene Datenblatt-XTF; vorhandenen Stand aus dem Themenrepo verwenden.";
      case "source_environment", "environment" ->
          "Name eines eingerichteten Zielprofils; lokales Profil heisst local. Lieferung benötigt passende echte Freigabe.";
      case "role" ->
          "data=CSV, metadata=Datenblatt-XTF, metadata_source=XLSX, model=ILI. Neue Datei entwertet abhängige Freigaben.";
      case "path" ->
          "Konkreter Dateipfad; Anhänge zuerst mit datenportal_workspace.import_attachment verifiziert übernehmen.";
      case "operation" ->
          "Öffentlicher Werkzeugname. operation_schema(metadata.<operation>) liefert den Integrator-Vertrag; metadata.describe_schema beschreibt fachliche Datenblattfelder.";
      case "arguments" ->
          "Argumentobjekt gemäss operation_schema(metadata.<operation>); Entwurfs-IDs und Revisionen verwaltet der Integrator.";
      case "gate" ->
          "data, metadata, model, organization oder publish:<Profil>, passend zum tatsächlich gezeigten Prüfstand.";
      case "fingerprint" ->
          "Exakter Fingerprint des gezeigten aktuellen Prüfstands; status.current_approvals enthält nur derzeit gültige Freigaben.";
      case "human_statement" ->
          "Tatsächliche menschliche Antwort zum gezeigten Stand. Auftrag, Korrektur oder Agentenschätzung ist kein OK.";
      case "identity" ->
          "Bestätigte Modellidentität mit name, uri und version; Standard INTERLIS 2.4/SO-DATA/VALIDATION.";
      case "confirmations" ->
          "Spaltenbezogene bestätigte Fachregeln. confirmed=true nur für tatsächliche menschliche Entscheidungen.";
      case "range_proposals" ->
          "Begründete Agentenvorschläge für künftige Wertebereiche; getrennt von bestätigten Fachregeln.";
      case "converter" ->
          "JSON-Rezept relativ zu topics/ mit class_name und sources; alle Java-Quellen gehören zum aktuellen Thema.";
      case "tests" -> "JUnit-Testverzeichnis relativ zu topics/ innerhalb desselben Themenordners.";
      case "policy" ->
          "supplier=Lieferantenumstellung oder recurring=wiederkehrender Konverter; Originaldatei bleibt unverändert.";
      case "instructions" ->
          "Bestätigter Zielvertrag und lokal vorbereitete Lieferantenbeschreibung.";
      case "relative_path" ->
          "Erlaubter fachlicher Zielpfad relativ zum Themenrepo; vor Review mit stage_change registrieren.";
      case "source_path" -> "Absoluter Kandidatenpfad mit genau den zu prüfenden Exportbytes.";
      case "content" ->
          "UTF-8-Inhalt eines Konverterrezepts oder einer Java-Konverter/JUnit-Datei, maximal 512 KiB.";
      case "expected_sha256" ->
          "SHA-256 aus read_file; für das Überschreiben einer vorhandenen Arbeitsdatei erforderlich.";
      case "values" ->
          "Bestätigte Fachangaben gemäss organization_schema; Kontakte und Benutzerzuordnungen nicht erfinden.";
      case "repair_of" -> "Prüfkennung model_repair.last_failure aus status; keine Logdatei.";
      case "repair_reason" ->
          "Konkrete Korrektur nach Fehlprüfung; gemeinsam höchstens drei Reparaturversuche.";
      case "allow_breaking" ->
          "Nur auf ausdrücklichen Auftrag inkompatible Modelländerungen zulassen.";
      case "changes" ->
          "Typisierte fachliche Modelländerungen gemäss öffentlichem INTERLIS-Vertrag.";
      case "job", "queue" ->
          "Belegte externe Lauf-/Queue-Kennung; bei unklarem Upload niemals erfinden oder erneut hochladen.";
      case "sheet" ->
          "Konkreter XLSX-Blattname; gespeicherte Formelwerte lesen, Formeln nicht ausführen.";
      case "offset", "limit" -> "Position beziehungsweise maximale Anzahl der XLSX-Quellenzeilen.";
      case "entries" ->
          "Herkunft fachlicher Angaben samt Quellenkoordinaten; Beobachtung und bestätigte Semantik trennen.";
      default -> "Fachlicher Werkzeugparameter " + key + "; öffentliche Skill-Referenz beachten.";
    };
  }

  static Object schema(String name) {
    var op = ALL.get(name);
    if (op == null) throw new Problem("unknown_operation", "Operation ist nicht bekannt.");
    return Json.map("operation", name, "description", op.description(), "inputSchema", op.schema());
  }

  public static void validate(String name, Map<String, Object> args) {
    validate(ALL, name, args);
  }

  static void validate(Map<String, Operation> operations, String name, Map<String, Object> args) {
    var op = operations.get(name);
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
