package ch.so.agi.integrator;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Presentation only: token spans never change the source text. */
final class ReportCode {
  static final int MAX_BYTES = 1024 * 1024;
  static final int MAX_LINES = 5000;

  static boolean large(String text) {
    return text.length() > MAX_BYTES
        || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
        || text.lines().limit(MAX_LINES + 1L).count() > MAX_LINES;
  }

  static String language(String hint) {
    String h = hint.toLowerCase(Locale.ROOT);
    if (h.endsWith(".sql")
        || Set.of("sql", "query", "sqlquery", "sql_query", "query_text").contains(h)) return "SQL";
    if (h.endsWith(".gradle")
        || h.endsWith(".groovy")
        || Set.of("gradle_text", "task_text").contains(h)) return "Gradle/Groovy";
    if (h.endsWith(".ili") || h.equals("model_text")) return "INTERLIS";
    if (h.endsWith(".xml") || h.endsWith(".xtf") || h.equals("xml")) return "XML";
    if (h.endsWith(".yaml") || h.endsWith(".yml")) return "YAML";
    if (h.endsWith(".json") || h.equals("json")) return "JSON";
    return "Text";
  }

  private static final Set<String> KEYWORDS =
      Set.of(
          "select",
          "from",
          "where",
          "as",
          "join",
          "left",
          "right",
          "inner",
          "outer",
          "on",
          "and",
          "or",
          "not",
          "null",
          "true",
          "false",
          "case",
          "when",
          "then",
          "else",
          "end",
          "group",
          "by",
          "order",
          "having",
          "distinct",
          "union",
          "all",
          "with",
          "insert",
          "update",
          "delete",
          "into",
          "values",
          "create",
          "table",
          "is",
          "in",
          "like",
          "asc",
          "desc",
          "def",
          "class",
          "import",
          "if",
          "return",
          "new",
          "throw",
          "tasks",
          "register",
          "dependson",
          "copy",
          "dofirst",
          "dolast",
          "onlyif",
          "interlis",
          "model",
          "topic",
          "structure",
          "domain",
          "mandatory",
          "text",
          "numeric",
          "extends",
          "association",
          "of",
          "unique",
          "constraint",
          "end.",
          "version",
          "at");

  static String block(String text, String language) {
    return "<div class=\"code-block\"><div class=\"code-label\">"
        + Reports.escape(language)
        + "</div><pre tabindex=\"0\"><code>"
        + highlight(text, language)
        + "</code></pre></div>";
  }

  static String escape(String text) {
    return Reports.escape(text).replace("\r", "&#13;");
  }

  static String highlight(String text, String language) {
    if (language.equals("Text") || large(text)) return escape(text);
    var out = new StringBuilder();
    for (int i = 0; i < text.length(); ) {
      int start = i;
      String token = null;
      char c = text.charAt(i);
      boolean sql = language.equals("SQL"),
          ili = language.equals("INTERLIS"),
          xml = language.equals("XML");
      if ((xml && text.startsWith("<!--", i)) || text.startsWith("/*", i)) {
        String close = xml && text.startsWith("<!--", i) ? "-->" : "*/";
        int end = text.indexOf(close, i + (close.equals("-->") ? 4 : 2));
        i = end < 0 ? text.length() : end + close.length();
        token = "comment";
      } else if ((sql && text.startsWith("--", i))
          || (ili && text.startsWith("!!", i))
          || (language.equals("Gradle/Groovy") && text.startsWith("//", i))
          || (language.equals("YAML") && c == '#')) {
        while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
        token = "comment";
      } else if (c == '\'' || c == '"') {
        char quote = c;
        i++;
        while (i < text.length()) {
          char next = text.charAt(i++);
          if (next == '\\' && i < text.length()) i++;
          else if (next == quote) {
            if (sql && i < text.length() && text.charAt(i) == quote) i++;
            else break;
          }
        }
        token = "string";
      } else if (Character.isDigit(c)) {
        while (i < text.length()
            && (Character.isLetterOrDigit(text.charAt(i)) || ".".indexOf(text.charAt(i)) >= 0)) i++;
        token = "number";
      } else if (Character.isLetter(c) || c == '_') {
        while (i < text.length()
            && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
        if (KEYWORDS.contains(text.substring(start, i).toLowerCase(Locale.ROOT))) token = "keyword";
      } else i++;
      String escaped = escape(text.substring(start, i));
      out.append(
          token == null ? escaped : "<span class=\"tok-" + token + "\">" + escaped + "</span>");
    }
    return out.toString();
  }
}
