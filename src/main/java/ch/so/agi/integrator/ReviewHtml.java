package ch.so.agi.integrator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.*;

/** Stateless view of a saved report. No file reads, mutations or approval decisions. */
final class ReviewHtml {
  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("sheet", "Datenblatt"),
          Map.entry("transform", "Umwandlung und Lieferhinweise"),
          Map.entry("task_configuration", "Validierungseinstellungen"),
          Map.entry("jenkins_job", "Jenkins-Job vorhanden"),
          Map.entry("identifier", "Themenkennung"),
          Map.entry("title", "Titel"),
          Map.entry("name", "Name"),
          Map.entry("description", "Beschreibung"),
          Map.entry("kind", "Art"),
          Map.entry("fields", "Weitere Datenblattfelder"),
          Map.entry("attributes", "Datenattribute"),
          Map.entry("data_type", "Datentyp"),
          Map.entry("mandatory", "Pflichtwert"),
          Map.entry("unit", "Einheit"),
          Map.entry("code_list", "Codeliste"),
          Map.entry("columns", "Spalten und Beobachtungen"),
          Map.entry("rows", "Datensätze"),
          Map.entry("header", "Kopfzeile"),
          Map.entry("missing", "Fehlende Werte"),
          Map.entry("examples", "Beispiele"),
          Map.entry("suggested_type", "Typvorschlag"),
          Map.entry("leading_zero", "Führende Nullen"),
          Map.entry("observed_min", "Beobachtetes Minimum"),
          Map.entry("observed_max", "Beobachtetes Maximum"),
          Map.entry("observed_values", "Beobachtete Werte"),
          Map.entry("distinct_values_count", "Anzahl unterschiedlicher Werte"),
          Map.entry("distinct_values_truncated", "Werteliste begrenzt"),
          Map.entry("observation_note", "Hinweis zur Beobachtung"),
          Map.entry("issues", "Serienausgaben"),
          Map.entry("selected_issue", "Gewählte Ausgabe"),
          Map.entry("label", "Bezeichnung"),
          Map.entry("current", "Aktuelle Ausgabe"),
          Map.entry("publication_status", "Publikationsstatus"),
          Map.entry("creator_ref", "Zuständige Dienststelle"),
          Map.entry("organization", "Organisation"),
          Map.entry("identity", "Modellidentität"),
          Map.entry("mapping", "Spaltenzuordnung"),
          Map.entry("profile", "Prüfprofil"),
          Map.entry("purpose", "Zweck"),
          Map.entry("valid", "Prüfung bestanden"),
          Map.entry("errors", "Fehler"),
          Map.entry("warnings", "Warnungen"),
          Map.entry("error_count", "Fehler insgesamt"),
          Map.entry("message", "Meldung"),
          Map.entry("code", "Meldungscode"),
          Map.entry("column", "Spalte"),
          Map.entry("row", "Zeile"),
          Map.entry("note", "Hinweis"),
          Map.entry("status", "Status"),
          Map.entry("open_questions", "Offene Fragen"),
          Map.entry("manualChecks", "Manuelle Prüfpunkte"),
          Map.entry("fingerprint", "Prüfsumme des Prüfstands"),
          Map.entry("path", "Gespeicherter Dateipfad"),
          Map.entry("sha256", "Prüfsumme Kandidat"),
          Map.entry("before_sha256", "Prüfsumme Bestand"),
          Map.entry("sql", "SQL-Abfrage"),
          Map.entry("query", "Abfrage"),
          Map.entry("instructions", "Lieferhinweise"),
          Map.entry("policy", "Umstellungsstrategie"),
          Map.entry("tests_log", "Testprotokoll"),
          Map.entry("technical_contact", "Technischer Kontakt"),
          Map.entry("short_description", "Kurzbeschreibung"),
          Map.entry("version", "Version"),
          Map.entry("uri", "Modell-URI"),
          Map.entry("offices", "Dienststellen"),
          Map.entry("teams", "Teams"),
          Map.entry("members", "Mitglieder"),
          Map.entry("permissions", "Berechtigungen"));

  static String label(String key) {
    return LABELS.getOrDefault(key, key);
  }

  static String details(String title, String content) {
    return details(title, content, false);
  }

  static String details(String title, String content, boolean open) {
    return "<details"
        + (open ? " open" : "")
        + "><summary>"
        + Reports.escape(title)
        + "</summary><div class=\"detail-body\">"
        + content
        + "</div></details>";
  }

  static String section(String title, String content) {
    return "<section><h2>" + Reports.escape(title) + "</h2>" + content + "</section>";
  }

  static String render(Reports.Kind kind, String identifier, Object data) {
    Map<?, ?> report = data instanceof Map<?, ?> m ? m : Map.of();
    Object current = kind == Reports.Kind.TRANSFORM ? report.get("after") : report;
    Map<?, ?> currentMap = current instanceof Map<?, ?> m ? m : Map.of();
    var errors = new LinkedHashSet<String>();
    var warnings = new LinkedHashSet<String>();
    var questions = new LinkedHashSet<String>();
    diagnostics(current, errors, warnings, questions, 0);
    Object valid = currentMap.get("valid");
    boolean failed = Boolean.FALSE.equals(valid) || !errors.isEmpty();
    String status =
        failed
            ? "Prüfung fehlgeschlagen"
            : Boolean.TRUE.equals(valid)
                ? "Prüfung bestanden"
                : "Nicht geprüft – kein vollständiges Prüfergebnis vorhanden";
    if (kind == Reports.Kind.MODEL_CANDIDATE)
      status =
          failed
              ? "Modellkandidat mit Fehlern – Gesamtprüfung noch offen"
              : "Modellkandidat – Gesamtprüfung noch offen";
    else if (kind == Reports.Kind.TRANSFORM) status = "CSV nach Umwandlung: " + status;
    String css =
        failed
            ? "error"
            : Boolean.TRUE.equals(valid) && kind != Reports.Kind.MODEL_CANDIDATE
                ? "success"
                : "neutral";
    var overview =
        new StringBuilder("<p class=\"eyebrow\">")
            .append(Reports.escape(kind.title))
            .append("</p>");
    if (identifier != null && !identifier.isBlank())
      overview
          .append("<p><strong>Themenkennung / Organisation:</strong> ")
          .append(Reports.escape(identifier))
          .append("</p>");
    overview
        .append("<p class=\"status ")
        .append(css)
        .append("\">")
        .append(Reports.escape(status))
        .append("</p>");
    overview.append(
        "<p>Dies ist ein Prüfbericht. Eine erfolgreiche Prüfung ersetzt keine menschliche Freigabe.</p>");
    appendMessages(overview, "Fehler", errors, "error");
    appendMessages(overview, "Warnungen", warnings, "warning");
    appendMessages(overview, "Offene Fragen und fachliche Prüfpunkte", questions, "notice");
    var out = new StringBuilder(section("Prüfstand", overview.toString()));
    var remaining = new LinkedHashMap<Object, Object>(report);
    var business = new StringBuilder();
    switch (kind) {
      case CSV -> take(business, remaining, "rows", "columns", "header", "note");
      case TRANSFORM -> {
        business.append("<h3>Vor der Umwandlung</h3>").append(value(report.get("before"), "", 0));
        business.append("<h3>Nach der Umwandlung</h3>").append(value(report.get("after"), "", 0));
        remaining.remove("before");
        remaining.remove("after");
        take(business, remaining, "transform");
      }
      case METADATA -> {
        take(business, remaining, "sheet");
        business.append(modelSummary(report.get("model")));
      }
      case MODEL_CANDIDATE, MODEL -> business.append(modelSummary(report));
      case ORGANIZATION -> take(business, remaining, "organization", "message", "jenkins_job");
    }
    if (business.isEmpty())
      business.append("<p>Keine fachlichen Angaben in diesem Bericht enthalten.</p>");
    out.append(section("Fachliche Angaben", business.toString()));
    out.append(
        section(
            "Vorbereitete Dateien und Änderungen", ReportChanges.render(report.get("changes"))));
    remaining.remove("changes");
    remaining.remove("errors");
    remaining.remove("warnings");
    out.append(
        section(
            "Technische Details",
            details("Nachweise und ergänzende Felder", value(remaining, "", 0))));
    out.append(details("Vollständige Rohdaten", ReportCode.block(Json.pretty(data), "JSON")));
    return out.toString();
  }

  private static void take(StringBuilder out, Map<Object, Object> source, String... keys) {
    for (String key : keys)
      if (source.containsKey(key) && source.get(key) != null) {
        out.append("<h3>")
            .append(Reports.escape(label(key)))
            .append("</h3>")
            .append(value(source.remove(key), key, 0));
      }
  }

  private static String modelSummary(Object data) {
    if (!(data instanceof Map<?, ?> report)) return "";
    var out = new StringBuilder();
    if (report.get("model_text") instanceof String text)
      out.append("<h3>INTERLIS-Modell</h3>").append(ReportCode.block(text, "INTERLIS"));
    Object model = report.get("model");
    if (!(model instanceof Map<?, ?>)) model = report;
    if (model instanceof Map<?, ?> map) {
      var selected = new LinkedHashMap<Object, Object>();
      for (String key : List.of("identity", "mapping", "profile", "purpose", "task_configuration"))
        if (map.containsKey(key)) selected.put(key, map.get(key));
      out.append(value(selected, "", 0));
    }
    return out.toString();
  }

  private static void appendMessages(
      StringBuilder out, String title, Set<String> messages, String css) {
    if (messages.isEmpty()) return;
    out.append("<div class=\"")
        .append(css)
        .append(" messages\"><h3>")
        .append(title)
        .append("</h3>");
    messages.forEach(out::append);
    out.append("</div>");
  }

  private static void diagnostics(
      Object data, Set<String> errors, Set<String> warnings, Set<String> questions, int depth) {
    if (depth > 16) return;
    if (data instanceof Map<?, ?> map)
      map.forEach(
          (k, v) -> {
            String key = k.toString();
            Set<String> target =
                switch (key) {
                  case "errors", "violations" -> errors;
                  case "warnings" -> warnings;
                  case "open_questions",
                      "manualChecks",
                      "manual_checks",
                      "manualReviewPoints",
                      "questions" ->
                      questions;
                  default -> null;
                };
            if (target != null && present(v)) target.add(value(v, key, 0));
            else if (!Set.of("changes", "before", "fields", "identity").contains(key))
              diagnostics(v, errors, warnings, questions, depth + 1);
          });
    else if (data instanceof List<?> list)
      for (Object item : list) diagnostics(item, errors, warnings, questions, depth + 1);
  }

  private static boolean present(Object value) {
    return value != null
        && (!(value instanceof Collection<?> c) || !c.isEmpty())
        && (!(value instanceof Map<?, ?> m) || !m.isEmpty())
        && (!(value instanceof String s) || !s.isBlank());
  }

  static String value(Object data, String hint, int depth) {
    if (data == null) return "<span class=\"muted\">Nicht angegeben</span>";
    if (depth > 12) return ReportCode.block(Json.pretty(data), "JSON");
    if (data instanceof Map<?, ?> map) {
      if (map.isEmpty()) return "<p class=\"muted\">Keine Angaben</p>";
      var out = new StringBuilder("<dl class=\"fields\">");
      map.forEach(
          (key, v) -> {
            String name = key.toString();
            String childHint =
                Set.of("text", "value").contains(name) && !ReportCode.language(hint).equals("Text")
                    ? hint
                    : name;
            out.append("<div><dt>")
                .append(Reports.escape(label(name)))
                .append("</dt><dd>")
                .append(value(v, childHint, depth + 1))
                .append("</dd></div>");
          });
      return out.append("</dl>").toString();
    }
    if (data instanceof List<?> list) {
      if (list.isEmpty()) return "<p class=\"muted\">Keine Einträge</p>";
      if (list.stream().allMatch(v -> v instanceof Map<?, ?>)) return table(list, depth + 1);
      var out = new StringBuilder("<ul>");
      for (Object item : list)
        out.append("<li>").append(value(item, hint, depth + 1)).append("</li>");
      return out.append("</ul>").toString();
    }
    if (data instanceof Boolean b) return b ? "Ja" : "Nein";
    String text = data.toString(), language = ReportCode.language(hint);
    if (!language.equals("Text")) return ReportCode.block(text, language);
    if (data instanceof String && !ReportCode.large(text)) {
      String stripped = text.strip();
      if (stripped.startsWith("{") || stripped.startsWith("[")) {
        try {
          Object decoded =
              Json.MAPPER
                  .readerFor(Object.class)
                  .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                  .readValue(text);
          if (decoded instanceof Map<?, ?> || decoded instanceof List<?>)
            return value(decoded, "", depth + 1)
                + details("Originalwert (JSON-Text)", ReportCode.block(text, "JSON"));
        } catch (java.io.IOException ignored) {
          /* Ordinary text remains ordinary text. */
        }
      }
    }
    return "<span class=\"text\">" + Reports.escape(text) + "</span>";
  }

  private static String table(List<?> list, int depth) {
    var keys = new LinkedHashSet<Object>();
    for (Object item : list) keys.addAll(((Map<?, ?>) item).keySet());
    var visible = keys.stream().limit(6).toList();
    boolean extra = keys.size() > visible.size();
    var out =
        new StringBuilder(
            "<div class=\"table-scroll\" tabindex=\"0\" role=\"region\" aria-label=\"Datentabelle\"><table><thead><tr>");
    visible.forEach(
        k ->
            out.append("<th scope=\"col\">")
                .append(Reports.escape(label(k.toString())))
                .append("</th>"));
    if (extra) out.append("<th scope=\"col\">Weitere Angaben</th>");
    out.append("</tr></thead><tbody>");
    for (Object item : list) {
      Map<?, ?> row = (Map<?, ?>) item;
      out.append("<tr>");
      visible.forEach(
          k ->
              out.append("<td>")
                  .append(value(row.get(k), k.toString(), depth + 1))
                  .append("</td>"));
      if (extra) {
        var rest = new LinkedHashMap<Object, Object>(row);
        visible.forEach(rest::remove);
        out.append("<td>").append(details("Details", value(rest, "", depth + 1))).append("</td>");
      }
      out.append("</tr>");
    }
    return out.append("</tbody></table></div>").toString();
  }
}
