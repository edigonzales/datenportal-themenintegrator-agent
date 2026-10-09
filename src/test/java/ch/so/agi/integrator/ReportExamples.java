package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Synthetic rendering fixtures only; never creates a workflow or records an approval. */
public final class ReportExamples {
  static Map<String, Object> csv() {
    return Json.map(
        "valid",
        true,
        "rows",
        42,
        "header",
        List.of("Code", "Anzahl"),
        "columns",
        List.of(
            Json.map(
                "name",
                "Code",
                "suggested_type",
                "TEXT",
                "missing",
                0,
                "leading_zero",
                true,
                "examples",
                List.of("001", "002"),
                "observed_values",
                List.of("001", "002"),
                "observation_note",
                "Führende Nullen benötigen eine fachliche Typentscheidung."),
            Json.map(
                "name",
                "Anzahl",
                "suggested_type",
                "INTEGER",
                "missing",
                2,
                "leading_zero",
                false,
                "examples",
                List.of("12", "42"),
                "observed_min",
                "12",
                "observed_max",
                "42")),
        "errors",
        List.of(),
        "warnings",
        List.of(Json.map("column", "Code", "message", "Führende Nullen; TEXT fachlich prüfen.")),
        "note",
        "Synthetische Beispieldaten. Beobachtete Werte begründen keine Fachbereiche.",
        "fingerprint",
        "synthetisch-keine-freigabe");
  }

  static Map<String, Object> report(Reports.Kind kind) {
    String ili =
        "INTERLIS 2.4;\nMODEL Beispiel AT \"https://example.org/models\" VERSION \"2026-10-09\" =\n  TOPIC Daten =\n    CLASS Beobachtung =\n      Code : MANDATORY TEXT*3;\n      Anzahl : 0 .. 100000;\n    END Beobachtung;\n  END Daten;\nEND Beispiel.\n";
    String gradle =
        "// Synthetisches Darstellungsbeispiel\ntasks.register('stageThemenCsv', Copy) {\n    from(providers.gradleProperty('dataFile'))\n    into(layout.buildDirectory.dir('integrator-validation'))\n    rename { 'input.csv' }\n}\ntasks.register('validateThemenCsv', CsvValidator) {\n    dependsOn('stageThemenCsv')\n    modelNames('Beispiel')\n    failOnError.set(true)\n}\n";
    var model =
        Json.map(
            "identity",
            Json.map(
                "name",
                "Beispiel",
                "title",
                "Bevölkerung – synthetisches Beispiel",
                "uri",
                "https://example.org/models",
                "version",
                "2026-10-09"),
            "profile",
            "SO",
            "purpose",
            "VALIDATION",
            "authoring_valid",
            true,
            "evidence",
            Json.map(
                "afterReview",
                Json.map("manualChecks", List.of("Wertebereich von Anzahl fachlich bestätigen."))));
    var changes =
        Json.map(
            "statistik/ch.so.beispiel/Beispiel.ili",
            ReportsTest.change("statistik/ch.so.beispiel/Beispiel.ili", null, ili),
            "statistik/ch.so.beispiel/dataset.gradle",
            ReportsTest.change(
                "statistik/ch.so.beispiel/dataset.gradle",
                "// Bisherige Einstellungen\next.owner = 'Statistik'\n",
                "// Bisherige Einstellungen\next.owner = 'Statistik'\n" + gradle));
    var sheet =
        Json.map(
            "identifier",
            "ch.so.beispiel",
            "title",
            "Bevölkerung nach Gebiet",
            "kind",
            "series",
            "creator_ref",
            "statistik",
            "attributes",
            List.of(
                Json.map(
                    "name",
                    "Code",
                    "data_type",
                    "TEXT",
                    "mandatory",
                    true,
                    "description",
                    "Gebietscode mit führenden Nullen"),
                Json.map(
                    "name",
                    "Anzahl",
                    "data_type",
                    "INTEGER",
                    "mandatory",
                    false,
                    "description",
                    "Anzahl Personen")),
            "issues",
            List.of(
                Json.map("label", "2025", "current", false),
                Json.map("label", "2026", "current", true)),
            "fields",
            Json.map(
                "sql",
                "-- Auswahl für die aktuelle Ausgabe\nSELECT code, anzahl\nFROM statistik.bevoelkerung\nWHERE jahr = 2026 AND code <> '000';\n",
                "lieferhinweis",
                "Fehlende Werte bleiben leer."));
    return switch (kind) {
      case CSV -> csv();
      case TRANSFORM ->
          Json.map(
              "before",
              Json.map(
                  "valid",
                  false,
                  "rows",
                  42,
                  "errors",
                  List.of("Lieferformat mit Komma statt Semikolon")),
              "after",
              csv(),
              "transform",
              Json.map(
                  "policy",
                  "supplier",
                  "instructions",
                  "Künftige Lieferungen als UTF-8 mit Semikolon.",
                  "tests_log",
                  "synthetic/converter-tests.log"));
      case METADATA ->
          Json.map(
              "valid",
              true,
              "sheet",
              sheet,
              "csv",
              csv(),
              "model",
              Json.map("model_text", ili, "model", model),
              "changes",
              changes,
              "provenance",
              Json.map("source", "synthetische Prüfdaten"),
              "fingerprint",
              "synthetisch-keine-freigabe");
      case MODEL_CANDIDATE -> Json.map("model_text", ili, "model", model, "changes", changes);
      case MODEL ->
          Json.map(
              "valid",
              true,
              "model_text",
              ili,
              "model",
              model,
              "changes",
              changes,
              "gretl",
              Json.map("valid", true, "log", "synthetic/gretl.log"),
              "fingerprint",
              "synthetisch-keine-freigabe");
      case ORGANIZATION ->
          Json.map(
              "valid",
              true,
              "organization",
              Json.map(
                  "identifier",
                  "statistik",
                  "title",
                  "Statistikdienst",
                  "teams",
                  List.of(Json.map("name", "statistik-leser", "permissions", "Lesen"))),
              "changes",
              Json.map(
                  "statistik/build.gradle",
                  ReportsTest.change(
                      "statistik/build.gradle", null, "apply plugin: 'ch.so.agi.gretl'\n")),
              "jenkins_job",
              false,
              "message",
              "Kein Jenkins-Job ohne Datenthema.",
              "fingerprint",
              "synthetisch-keine-freigabe");
    };
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]), output = Path.of(args[1]);
    Files.createDirectories(output);
    for (var kind : Reports.Kind.values())
      Reports.render(
          root,
          output.resolve(kind.name().toLowerCase(Locale.ROOT) + ".html"),
          kind,
          "ch.so.beispiel (synthetisch)",
          report(kind));
    System.out.println(output.toAbsolutePath());
  }
}
