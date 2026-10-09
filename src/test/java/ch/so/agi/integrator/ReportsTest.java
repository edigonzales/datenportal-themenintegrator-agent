package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportsTest {
  @TempDir Path temp;

  static String plain(String html) {
    return html.replaceAll("<[^>]*>", "")
        .replace("&#13;", "\r")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&");
  }

  static Map<String, Object> change(String name, String before, String after) {
    return Json.map(
        "path",
        "/not/read/" + name,
        "before_sha256",
        before == null ? null : ReportChanges.sha(before),
        "sha256",
        ReportChanges.sha(after),
        "diff",
        "--- "
            + name
            + " (Bestand)\n"
            + (before == null ? "" : before)
            + "\n+++ "
            + name
            + " (Kandidat)\n"
            + after);
  }

  @Test
  void allPhasesHandleSuccessfulFailedIncompleteAndEmptyReportsWithoutMutation() {
    for (var kind : Reports.Kind.values()) {
      for (String state : List.of("passed", "failed", "incomplete", "empty")) {
        var report = ReportExamples.report(kind);
        Map<String, Object> tested =
            kind == Reports.Kind.TRANSFORM ? Json.obj(report.get("after")) : report;
        if (state.equals("failed")) {
          tested.put("valid", false);
          tested.put("errors", List.of(Json.map("message", "Synthetischer Fehler <&>")));
        } else if (state.equals("incomplete")) {
          tested.remove("valid");
          tested.put("open_questions", List.of("Fachlichen Wertebereich bestätigen"));
        } else if (state.equals("empty")) report.clear();
        String original = Json.pretty(report);
        Path output = temp.resolve(kind + "-" + state + ".html");
        Reports.render(Fixtures.ROOT, output, kind, "ch.so.fixture", report);
        String html = Json.contents(output);
        assertEquals(original, Json.pretty(report), "Renderer must not mutate evidence");
        for (String heading :
            List.of(
                "Prüfstand",
                "Fachliche Angaben",
                "Vorbereitete Dateien und Änderungen",
                "Technische Details",
                "Vollständige Rohdaten")) assertTrue(html.contains(heading), heading);
        assertTrue(html.contains("ch.so.fixture"));
        if (state.equals("empty") || state.equals("incomplete"))
          assertFalse(html.contains("class=\"status success\""));
        if (state.equals("failed")) assertTrue(html.contains("class=\"status error\""));
        if (kind == Reports.Kind.MODEL_CANDIDATE) {
          assertTrue(html.contains("Gesamtprüfung noch offen"));
          assertFalse(html.contains("class=\"status success\""));
        }
        if (state.equals("incomplete"))
          assertTrue(
              html.indexOf("Fachlichen Wertebereich") < html.indexOf("<h2>Fachliche Angaben"));
        assertTrue(html.contains("default-src 'none'; style-src 'unsafe-inline'"));
        assertFalse(
            Pattern.compile("<(script|iframe|link|img)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(html)
                .find());
        assertFalse(html.contains("onclick="));
      }
    }
  }

  @Test
  void codeTokenizationPreservesEveryCharacterAndEscapesMarkup() {
    var samples =
        Map.of(
            "SQL",
                "-- Kommentar\r\nSELECT 'O''Brien', '<script>&</script>', 'C:\\neu'\nFROM daten WHERE wert < 2;\n",
            "Gradle/Groovy",
                "// Kommentar\ntasks.register('stageThemenCsv', Copy) {\n\tfrom('C:\\neu')\n}\n/* </code><script> */",
            "INTERLIS", "!! Kommentar\nINTERLIS 2.4;\nMODEL Test AT \"<uri>&\" =\nEND Test.\n",
            "XML", "<!-- <script> -->\r\n<a x=\"&amp;\">Text</a>",
            "YAML", "# Beispiel\nname: 'A & B'\nvalid: true\n",
            "JSON", "{\"sql\":\"SELECT 1;\\n\", \"n\":42}");
    samples.forEach(
        (language, text) -> {
          String html = ReportCode.highlight(text, language);
          assertEquals(text, plain(html), language);
          assertFalse(html.contains("<script>"));
          assertTrue(html.contains("tok-"), language);
        });
    assertEquals("Gradle/Groovy", ReportCode.language("x/dataset.gradle"));
    assertEquals("INTERLIS", ReportCode.language("model_text"));
    assertEquals("SQL", ReportCode.language("sqlQuery"));
    assertEquals("&lt;unknown&gt;", ReportCode.highlight("<unknown>", "Text"));
  }

  @Test
  void embeddedJsonIsStrictAndNeverBlindlyUnescaped() {
    String code = "SELECT '\\n', '<&>';\nFROM beispiel";
    String json = Json.pretty(Json.map("sql", code));
    String html = Reports.html(Json.map("response", json, "unknown_field", "bleibt sichtbar"));
    assertTrue(html.contains("Originalwert (JSON-Text)"));
    assertTrue(html.contains("SQL-Abfrage"));
    assertTrue(html.contains("unknown_field"));
    assertTrue(html.contains("bleibt sichtbar"));
    assertTrue(plain(html).contains(code));
    for (String text : List.of("{broken}", "{\"sql\":1} trailing", "[1] [2]", "a\\nb")) {
      String rendered = Reports.html(text);
      assertFalse(rendered.contains("Originalwert (JSON-Text)"));
      assertEquals(text, plain(rendered));
    }
    assertTrue(Reports.html("[{\"name\":\"A\"}]").contains("<table>"));
  }

  @Test
  void templateMarkersAndUntrustedFieldsStayLiteral() {
    String payload = "{{TITLE}} {{CONTENT}} </pre><script>alert('x')</script>";
    Path output = temp.resolve("safe.html");
    Reports.render(Fixtures.ROOT, output, Reports.Kind.CSV, payload, Json.map("unknown", payload));
    String html = Json.contents(output);
    assertTrue(html.contains("{{TITLE}} {{CONTENT}}"));
    assertFalse(html.contains("<script>"));
    assertTrue(html.contains("&lt;/pre&gt;"));
  }

  @Test
  void recordedComparisonsHandleNewChangedAndUnchangedFiles() {
    String name = "org/dataset.gradle";
    for (String before : Arrays.asList(null, "", "same\n", "old\n")) {
      String after = "same\n";
      var change = change(name, before, after);
      var parsed = ReportChanges.parse(name, change);
      assertNotNull(parsed);
      assertEquals(before == null ? "" : before, parsed.before());
      assertEquals(after, parsed.after());
      String html = ReportChanges.render(Json.map(name, change));
      assertTrue(html.contains("Kandidat – vollständiger Inhalt"));
      assertFalse(html.contains("Eingeschränkte Darstellung"));
      if (before == null) assertTrue(html.contains("Neue Datei"));
      else if (before.equals(after)) assertTrue(html.contains("Unveränderte Datei"));
      else assertTrue(html.contains("Geänderte Datei"));
    }
  }

  @Test
  void comparisonMarkersInsideSourceCannotCorruptTheSplit() {
    String name = "dataset.gradle";
    String delimiter = "\n+++ " + name + " (Kandidat)\n";
    String before = "before" + delimiter + "literal marker";
    String after = "after" + delimiter + "literal marker";
    var parsed = ReportChanges.parse(name, change(name, before, after));
    assertEquals(new ReportChanges.Comparison(before, after), parsed);
    var ambiguous = change(name, "before", delimiter.repeat(34));
    assertNull(ReportChanges.parse(name, ambiguous));
    assertTrue(
        ReportChanges.render(Json.map(name, ambiguous)).contains("Eingeschränkte Darstellung"));
  }

  @Test
  void mismatchedMissingAndLegacyEvidenceShowsOnlyTheRecordedComparison() {
    String name = "dataset.gradle";
    var altered = change(name, "old", "new");
    altered.put("sha256", "invalid");
    String html = ReportChanges.render(Json.map(name, altered));
    assertTrue(html.contains("Eingeschränkte Darstellung"));
    assertTrue(plain(html).contains(altered.get("diff").toString()));
    assertFalse(html.contains("Zeilenweise Änderungen"));
    assertTrue(
        ReportChanges.render(Json.map(name, Json.map("diff", "legacy <&>")))
            .contains("legacy &lt;&amp;&gt;"));
    assertDoesNotThrow(() -> ReportChanges.render(Json.map(name, null)));
  }

  @Test
  void lineDiffCanReconstructBothInputsIncludingMissingFinalNewlines() {
    var random = new Random(42);
    for (int i = 0; i < 100; i++) {
      String before = randomText(random), after = randomText(random);
      String html = ReportChanges.diffHtml(new ReportChanges.Comparison(before, after));
      var matcher =
          Pattern.compile("<span class=\"(added|removed|same)\">([\\s\\S]*?)</span>").matcher(html);
      var a = new StringBuilder();
      var b = new StringBuilder();
      while (matcher.find()) {
        String line = plain(matcher.group(2)).substring(2);
        if (!matcher.group(1).equals("added")) a.append(line);
        if (!matcher.group(1).equals("removed")) b.append(line);
      }
      assertEquals(before, a.toString());
      assertEquals(after, b.toString());
    }
  }

  private static String randomText(Random r) {
    var text = new StringBuilder();
    for (int i = r.nextInt(12); i > 0; i--)
      text.append("line <&> ").append(r.nextInt(4)).append(r.nextBoolean() ? "\n" : "\r\n");
    if (r.nextBoolean()) text.append("last");
    return text.toString();
  }

  @Test
  void largeSourcesRemainCompleteWithoutHighlightingOrDiffWork() {
    for (String source :
        List.of("SELECT x;\n".repeat(5001), "ä".repeat(ReportCode.MAX_BYTES / 2 + 1))) {
      assertTrue(ReportCode.large(source));
      assertEquals(source, plain(ReportCode.highlight(source, "SQL")));
      String html = ReportChanges.render(Json.map("file.sql", change("file.sql", "old", source)));
      assertFalse(html.contains("Zeilenweise Änderungen"));
      assertTrue(plain(html).contains(source));
    }
  }

  @Test
  void nestedDiagnosticsAppearBeforeBusinessDataAndBeforeTransformErrorsAreHistorical() {
    var report =
        Json.map(
            "valid",
            false,
            "model",
            Json.map(
                "evidence",
                Json.map(
                    "afterReview",
                    Json.map(
                        "manualChecks",
                        List.of("Semantik prüfen"),
                        "errors",
                        List.of("Modellfehler")))));
    String html = ReviewHtml.render(Reports.Kind.METADATA, null, report);
    assertTrue(html.indexOf("Semantik prüfen") < html.indexOf("<h2>Fachliche Angaben"));
    assertTrue(html.indexOf("Modellfehler") < html.indexOf("<h2>Fachliche Angaben"));
    String transformed =
        ReviewHtml.render(
            Reports.Kind.TRANSFORM,
            null,
            Json.map(
                "before",
                Json.map("errors", List.of("Alter Fehler")),
                "after",
                Json.map("valid", true)));
    assertTrue(transformed.contains("class=\"status success\""));
    assertTrue(transformed.indexOf("Alter Fehler") > transformed.indexOf("<h2>Fachliche Angaben"));
  }
}
