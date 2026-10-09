package ch.so.agi.integrator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Uses only the recorded comparison, never the current repository or arbitrary file paths. */
final class ReportChanges {
  record Comparison(String before, String after) {}

  record Line(char marker, String text) {}

  static String sha(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static Comparison parse(String name, Map<?, ?> change) {
    if (!(change.get("diff") instanceof String diff) || !(change.get("sha256") instanceof String))
      return null;
    String prefix = "--- " + name + " (Bestand)\n";
    String middle = "\n+++ " + name + " (Kandidat)\n";
    if (!diff.startsWith(prefix)) return null;
    Comparison match = null;
    // Avoid quadratic hashing of arbitrarily many fabricated separators.
    int attempts = 0;
    for (int i = diff.indexOf(middle, prefix.length()); i >= 0; i = diff.indexOf(middle, i + 1)) {
      if (++attempts > 32) return null;
      String before = diff.substring(prefix.length(), i),
          after = diff.substring(i + middle.length());
      boolean beforeMatches =
          change.get("before_sha256") == null
              ? before.isEmpty()
              : sha(before).equals(change.get("before_sha256"));
      if (beforeMatches && sha(after).equals(change.get("sha256"))) {
        if (match != null) return null;
        match = new Comparison(before, after);
      }
    }
    return match;
  }

  static String render(Object changes) {
    if (!(changes instanceof Map<?, ?> map) || map.isEmpty())
      return "<p>Keine Dateiänderungen in diesem Bericht enthalten.</p>";
    var out = new StringBuilder();
    map.forEach(
        (key, value) -> {
          String name = key.toString();
          out.append("<article class=\"file\"><h3>").append(Reports.escape(name)).append("</h3>");
          if (!(value instanceof Map<?, ?> change)) out.append(ReviewHtml.value(value, name, 0));
          else {
            String diff = change.get("diff") instanceof String s ? s : "";
            Comparison pair =
                diff.getBytes(StandardCharsets.UTF_8).length > 2L * ReportCode.MAX_BYTES + 4096
                    ? null
                    : parse(name, change);
            if (pair == null) {
              out.append(
                  "<p class=\"notice\">Eingeschränkte Darstellung: Der gespeicherte Vergleich lässt sich nicht eindeutig mit den Prüfsummen abgleichen. Es werden keine Änderungen daraus abgeleitet.</p>");
              out.append(
                  ReviewHtml.details(
                      "Gespeicherter Vergleich (vollständig)", ReportCode.block(diff, "Text")));
            } else {
              String language = ReportCode.language(name);
              out.append("<p>")
                  .append(
                      change.get("before_sha256") == null
                          ? "Neue Datei"
                          : pair.before.equals(pair.after)
                              ? "Unveränderte Datei"
                              : "Geänderte Datei")
                  .append(" · gespeicherte Prüfsummen stimmen überein.</p>");
              if (ReportCode.large(pair.before) || ReportCode.large(pair.after)) {
                out.append(
                    "<p class=\"notice\">Grosse Datei: vollständige Texte ohne zeilenweisen Vergleich und Syntaxfarben.</p>");
                language = "Text";
              } else {
                out.append(
                    ReviewHtml.details(
                        "Zeilenweise Änderungen (+ hinzugefügt, − entfernt)", diffHtml(pair)));
              }
              out.append(
                  ReviewHtml.details(
                      "Kandidat – vollständiger Inhalt",
                      ReportCode.block(pair.after, language),
                      true));
              if (change.get("before_sha256") != null)
                out.append(
                    ReviewHtml.details(
                        "Bestand – vollständiger Inhalt", ReportCode.block(pair.before, language)));
            }
            var metadata = new LinkedHashMap<Object, Object>(change);
            metadata.remove("diff");
            out.append(ReviewHtml.details("Dateinachweise", ReviewHtml.value(metadata, "", 0)));
          }
          out.append("</article>");
        });
    return out.toString();
  }

  static String diffHtml(Comparison pair) {
    List<String> before = lines(pair.before), after = lines(pair.after);
    var rows = new ArrayList<Line>();
    compare(before, 0, before.size(), after, 0, after.size(), rows);
    var out = new StringBuilder("<pre class=\"diff\" tabindex=\"0\"><code>");
    for (Line row : rows) {
      out.append("<span class=\"")
          .append(row.marker == '+' ? "added" : row.marker == '-' ? "removed" : "same")
          .append("\">")
          .append(row.marker)
          .append(' ')
          .append(ReportCode.escape(row.text))
          .append("</span>");
      if (!row.text.endsWith("\n") && !row.text.endsWith("\r"))
        out.append("\n<span class=\"muted\">\\ Kein Zeilenumbruch am Dateiende</span>\n");
    }
    return out.append("</code></pre>").toString();
  }

  static List<String> lines(String text) {
    if (text.isEmpty()) return List.of();
    return Arrays.asList(text.split("(?<=\n)|(?<=\r)(?!\n)", -1)).stream()
        .filter(s -> !s.isEmpty())
        .toList();
  }

  // Hirschberg LCS: linear auxiliary memory, bounded to 5,000 lines per side by the caller.
  private static void compare(
      List<String> a, int a0, int a1, List<String> b, int b0, int b1, List<Line> out) {
    if (a0 == a1) {
      for (int j = b0; j < b1; j++) out.add(new Line('+', b.get(j)));
      return;
    }
    if (b0 == b1) {
      for (int i = a0; i < a1; i++) out.add(new Line('-', a.get(i)));
      return;
    }
    if (a1 - a0 == 1) {
      int match = -1;
      for (int j = b0; j < b1; j++)
        if (a.get(a0).equals(b.get(j))) {
          match = j;
          break;
        }
      if (match < 0) out.add(new Line('-', a.get(a0)));
      for (int j = b0; j < b1; j++) out.add(new Line(j == match ? ' ' : '+', b.get(j)));
      return;
    }
    int mid = (a0 + a1) / 2, width = b1 - b0;
    int[] left = lengths(a, a0, mid, b, b0, b1, false),
        right = lengths(a, mid, a1, b, b0, b1, true);
    int split = 0;
    for (int j = 1; j <= width; j++)
      if (left[j] + right[width - j] > left[split] + right[width - split]) split = j;
    compare(a, a0, mid, b, b0, b0 + split, out);
    compare(a, mid, a1, b, b0 + split, b1, out);
  }

  private static int[] lengths(
      List<String> a, int a0, int a1, List<String> b, int b0, int b1, boolean reverse) {
    int[] row = new int[b1 - b0 + 1];
    for (int i = 0; i < a1 - a0; i++) {
      int previous = 0;
      for (int j = 1; j < row.length; j++) {
        int old = row[j];
        row[j] =
            a.get(reverse ? a1 - 1 - i : a0 + i).equals(b.get(reverse ? b1 - j : b0 + j - 1))
                ? previous + 1
                : Math.max(row[j], row[j - 1]);
        previous = old;
      }
    }
    return row;
  }
}
