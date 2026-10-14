package ch.so.agi.integrator;

import java.nio.file.Path;
import java.util.*;

/** Bounded wire representation. Full workflow results and approval state remain untouched. */
final class McpResults {
  static final int LIMIT = 12000;

  static Object present(Settings settings, String operation, Object result) {
    // Schemas are contracts, never summaries.
    if (operation.equals("operation_schema") || Json.pretty(result).length() <= LIMIT)
      return result;
    Path artifact = settings.runs.resolve("responses").resolve(UUID.randomUUID() + ".json");
    Json.write(artifact, Json.pretty(result));
    var summary =
        result instanceof Map<?, ?>
            ? Json.obj(summarize(result, 0))
            : Json.map("result", summarize(result, 0));
    summary.put("response_compacted", true);
    summary.put("full_result_path", artifact.toString());
    summary.put("full_result_sha256", Json.sha(artifact));
    summary.put(
        "read_more",
        "datenportal_workspace.read_file(path=full_result_path, offset=0); mit next_offset fortsetzen. Keine erneute Prüfung zum Lesen auslösen.");
    if (Json.text(summary).length() > LIMIT) {
      var essential = Json.map();
      for (String key :
          List.of(
              "error",
              "message",
              "code",
              "run_id",
              "id",
              "organization",
              "identifier",
              "issue",
              "model",
              "files",
              "pending_metadata",
              "pending_model",
              "valid",
              "scope",
              "approvable",
              "fingerprint",
              "current_approvals",
              "errors",
              "review_path",
              "checks",
              "changes")) {
        if (summary.containsKey(key)) essential.put(key, summary.get(key));
      }
      essential.put("response_compacted", true);
      essential.put("full_result_path", artifact.toString());
      essential.put("full_result_sha256", Json.sha(artifact));
      essential.put("read_more", summary.get("read_more"));
      summary = essential;
    }
    // Bound even unusually broad objects; never silently imply a complete result.
    if (Json.pretty(summary).length() > LIMIT) {
      for (String key : new ArrayList<>(summary.keySet())) {
        Object value = summary.get(key);
        if (Json.pretty(value).length() > 1500)
          summary.put(key, Json.map("omitted", true, "reason", "Siehe full_result_path"));
      }
    }
    return summary;
  }

  static Object summarize(Object value, int depth) {
    if (value instanceof Map<?, ?> map) {
      var out = Json.map();
      for (var entry : map.entrySet()) {
        String key = entry.getKey().toString();
        if (key.equals("checks") && entry.getValue() instanceof Map<?, ?> checks) {
          var compactChecks = Json.map();
          checks.forEach(
              (name, check) -> {
                var compactCheck = Json.map();
                if (check instanceof Map<?, ?> fields) {
                  for (String field :
                      List.of(
                          "valid",
                          "scope",
                          "approvable",
                          "fingerprint",
                          "errors",
                          "error_count",
                          "warnings",
                          "review_path"))
                    if (fields.containsKey(field))
                      compactCheck.put(field, summarize(fields.get(field), 0));
                }
                compactChecks.put(name.toString(), compactCheck);
              });
          out.put(key, compactChecks);
        } else if (Set.of(
                "diff",
                "events",
                "approvals",
                "draft",
                "baseline",
                "model_text",
                "mapping",
                "diagnostic_history",
                "issue_history",
                "columns",
                "observed",
                "evidence",
                "content")
            .contains(key)) {
          out.put(key, Json.map("omitted", true));
        } else if (depth >= 5 && entry.getValue() instanceof Map<?, ?>) {
          out.put(key, Json.map("omitted", true));
        } else out.put(key, summarize(entry.getValue(), depth + 1));
      }
      return out;
    }
    if (value instanceof List<?> list) {
      var out = new ArrayList<Object>();
      for (Object item : list.stream().limit(8).toList()) out.add(summarize(item, depth + 1));
      if (list.size() > 8) out.add(Json.map("omitted_items", list.size() - 8));
      return out;
    }
    if (value instanceof String text && text.length() > 800)
      return text.substring(0, 800) + "\n[gekürzt; vollständiges Ergebnis siehe full_result_path]";
    return value;
  }
}
