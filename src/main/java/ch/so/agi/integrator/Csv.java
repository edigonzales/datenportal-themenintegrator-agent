package ch.so.agi.integrator;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;

public final class Csv {
  public static boolean accepts(String value, String kind) {
    if (value.isEmpty()) return true;
    try {
      return switch (kind.toUpperCase(Locale.ROOT)) {
        case "TEXT" -> true;
        case "INTEGER" -> value.matches("[+-]?\\d+") && Long.parseLong(value) <= Long.MAX_VALUE;
        case "DECIMAL", "NUMERIC" ->
            value.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
                && new BigDecimal(value) != null;
        case "BOOLEAN" -> Set.of("true", "false").contains(value);
        case "DATE" -> value.matches("\\d{4}-\\d{2}-\\d{2}") && LocalDate.parse(value) != null;
        case "DATETIME" ->
            value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\+01:00")
                && OffsetDateTime.parse(value).getOffset().equals(ZoneOffset.ofHours(1));
        default -> false;
      };
    } catch (RuntimeException e) {
      return false;
    }
  }

  public static CSVFormat format() {
    return CSVFormat.DEFAULT
        .builder()
        .setDelimiter(';')
        .setQuote('"')
        .setIgnoreEmptyLines(false)
        .setLenientEof(false)
        .get();
  }

  public static Reader reader(Path p) throws IOException {
    var r =
        new PushbackReader(
            new InputStreamReader(
                Files.newInputStream(p),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)),
            1);
    int ch = r.read();
    if (ch != -1 && ch != 0xfeff) r.unread(ch);
    return r;
  }

  public static Map<String, Object> inspect(
      Path path, Map<String, Object> rules, List<Object> attributes) {
    var errors = new ArrayList<Object>();
    var warnings = new ArrayList<Object>();
    var header = new ArrayList<String>();
    var stats = new ArrayList<Map<String, Object>>();
    var candidates = new ArrayList<List<String>>();
    var unique = new ArrayList<Set<String>>();
    int[] count = {0};
    long rows = 0;
    java.util.function.Consumer<Map<String, Object>> error =
        e -> {
          count[0]++;
          if (errors.size() < 100) errors.add(e);
        };
    try (var parser = new CSVParser(reader(path), format())) {
      var iter = parser.iterator();
      if (iter.hasNext()) iter.next().forEach(header::add);
      if (header.isEmpty())
        error.accept(Json.map("code", "empty_csv", "message", "CSV enthält keine Kopfzeile."));
      if (header.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != header.size())
        error.accept(
            Json.map("code", "duplicate_header", "message", "Spaltennamen sind nicht eindeutig."));
      for (int i = 0; i < header.size(); i++) {
        String h = header.get(i);
        if (!h.matches(Json.str(rules, "header_pattern", "[A-Za-z][A-Za-z0-9_]*")))
          error.accept(
              Json.map(
                  "code",
                  "invalid_header",
                  "column",
                  i + 1,
                  "value",
                  h,
                  "message",
                  "Ungültiger Spaltenname."));
        if (Set.of("total", "gesamt", "summe").contains(h.toLowerCase(Locale.ROOT)))
          warnings.add(
              Json.map(
                  "code",
                  "possible_total",
                  "column",
                  h,
                  "message",
                  "Möglicherweise redundante Totalspalte; fachlich prüfen."));
        stats.add(
            Json.map(
                "name", h, "missing", 0L, "examples", new ArrayList<>(), "leading_zero", false));
        stats.getLast().put("observed_values", new ArrayList<>());
        stats.getLast().put("distinct_values_truncated", false);
        unique.add(new HashSet<>());
        candidates.add(
            new ArrayList<>(List.of("INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME")));
      }
      if (attributes != null) {
        if (!header.equals(
            attributes.stream().map(Json::obj).map(a -> Json.str(a, "name", "")).toList()))
          error.accept(
              Json.map(
                  "code",
                  "attribute_order",
                  "message",
                  "Spalten und Reihenfolge passen nicht zum Datenblatt."));
        for (Object a : attributes)
          if (!Json.strings(rules.get("supported_types"))
              .contains(Json.str(Json.obj(a), "data_type", "").toUpperCase(Locale.ROOT)))
            error.accept(
                Json.map(
                    "code",
                    "unsupported_type",
                    "column",
                    Json.obj(a).get("name"),
                    "message",
                    "Nicht unterstützter Datentyp."));
      }
      while (iter.hasNext()) {
        CSVRecord row = iter.next();
        rows++;
        if (row.stream().allMatch(String::isBlank)) {
          error.accept(
              Json.map(
                  "code",
                  "empty_row",
                  "line",
                  parser.getCurrentLineNumber(),
                  "message",
                  "Leerzeile ist nicht zulässig."));
          continue;
        }
        if (row.size() != header.size()) {
          error.accept(
              Json.map(
                  "code",
                  "row_width",
                  "line",
                  parser.getCurrentLineNumber(),
                  "actual",
                  row.size(),
                  "message",
                  "Anzahl Zellen stimmt nicht."));
          continue;
        }
        for (int i = 0; i < row.size(); i++) {
          String v = row.get(i);
          var st = stats.get(i);
          var examples = Json.list(st.get("examples"));
          if (v.indexOf('\0') >= 0)
            error.accept(
                Json.map(
                    "code",
                    "nul",
                    "line",
                    parser.getCurrentLineNumber(),
                    "column",
                    header.get(i),
                    "message",
                    "NUL-Zeichen in CSV."));
          if (v.isEmpty()) st.put("missing", ((Number) st.get("missing")).longValue() + 1);
          else {
            if (examples.size() < 5 && !examples.contains(v))
              examples.add(v.substring(0, Math.min(160, v.length())));
            if (v.matches("0\\d+")) st.put("leading_zero", true);
            candidates.get(i).removeIf(t -> !accepts(v, t));
            String valueHash = Json.hash(v.getBytes(StandardCharsets.UTF_8));
            if (!unique.get(i).contains(valueHash)) {
              if (unique.get(i).size() < 1024) unique.get(i).add(valueHash);
              else st.put("distinct_values_truncated", true);
              var observed = Json.list(st.get("observed_values"));
              if (observed.size() < 20) observed.add(v.substring(0, Math.min(160, v.length())));
            }
            if (accepts(v, "DECIMAL")) {
              var number = new BigDecimal(v);
              if (!st.containsKey("numeric_min")
                  || number.compareTo((BigDecimal) st.get("numeric_min")) < 0)
                st.put("numeric_min", number);
              if (!st.containsKey("numeric_max")
                  || number.compareTo((BigDecimal) st.get("numeric_max")) > 0)
                st.put("numeric_max", number);
            }
          }
          if (attributes != null && i < attributes.size()) {
            var a = Json.obj(attributes.get(i));
            if (!Objects.equals(a.get("name"), header.get(i))) continue;
            if (Json.bool(a, "mandatory", false) && v.isBlank())
              error.accept(
                  Json.map(
                      "code",
                      "mandatory",
                      "line",
                      parser.getCurrentLineNumber(),
                      "column",
                      header.get(i),
                      "message",
                      "Pflichtwert fehlt."));
            else if (!v.isEmpty() && !accepts(v, Json.str(a, "data_type", "")))
              error.accept(
                  Json.map(
                      "code",
                      "datatype",
                      "line",
                      parser.getCurrentLineNumber(),
                      "column",
                      header.get(i),
                      "message",
                      "Ungültiger Datentyp/Format.",
                      "value",
                      v.substring(0, Math.min(160, v.length()))));
          }
        }
      }
      if (rows == 0)
        error.accept(
            Json.map("code", "no_observations", "message", "CSV enthält keine Beobachtungen."));
    } catch (IOException | UncheckedIOException e) {
      error.accept(
          Json.map(
              "code",
              "csv_format",
              "message",
              "CSV ist nicht gültiges UTF-8 oder fehlerhaft quotiert."));
    }
    for (int i = 0; i < stats.size(); i++) {
      var st = stats.get(i);
      boolean zero = Json.bool(st, "leading_zero", false);
      st.put("distinct_values_count", unique.get(i).size());
      Object min = st.remove("numeric_min"), max = st.remove("numeric_max");
      st.put(
          "observed_min",
          candidates.get(i).contains("DECIMAL") && min != null ? min.toString() : null);
      st.put(
          "observed_max",
          candidates.get(i).contains("DECIMAL") && max != null ? max.toString() : null);
      st.put(
          "observation_note",
          "Beobachtungen sind Vorschläge; sie begründen keine Fachbereiche, geschlossenen Codelisten oder Schlüssel. Anzahl eindeutiger Werte ist bei Trunkierung eine Untergrenze.");
      st.put(
          "suggested_type",
          zero || ((Number) st.get("missing")).longValue() == rows || candidates.get(i).isEmpty()
              ? "TEXT"
              : candidates.get(i).getFirst());
      if (zero)
        warnings.add(
            Json.map(
                "code",
                "leading_zero",
                "column",
                st.get("name"),
                "message",
                "Führende Nullen; TEXT fachlich prüfen."));
    }
    return Json.map(
        "valid",
        count[0] == 0,
        "rows",
        rows,
        "header",
        header,
        "columns",
        stats,
        "errors",
        errors,
        "error_count",
        count[0],
        "warnings",
        warnings,
        "rules_version",
        rules.get("version"),
        "source",
        rules.get("source"),
        "note",
        "Typvorschläge sind keine fachliche Bestätigung. Alle Datensätze geprüft; maximal 100 Fehler angezeigt.");
  }
}
