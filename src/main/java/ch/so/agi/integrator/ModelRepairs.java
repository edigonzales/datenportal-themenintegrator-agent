package ch.so.agi.integrator;

import java.util.*;

/** Persisted separately from model fingerprints. One budget for compiler and CSV repairs. */
final class ModelRepairs {
  static void requireReference(Map<String, Object> run, Map<String, Object> args) {
    var series = Json.obj(run.get("model_repair"));
    if (!Json.bool(series, "active", false)) {
      if (args.containsKey("repair_of"))
        throw new Problem("repair_reference", "Keine offene Reparaturserie.");
      return;
    }
    if (Json.number(series, "attempts", 0) >= 3)
      throw new Problem(
          "repair_limit_reached",
          "Drei Reparaturversuche ausgeschöpft. Menschlicher Neuauftrag erforderlich.",
          "repair",
          series);
    if (!Objects.equals(args.get("repair_of"), series.get("last_failure"))
        || Json.str(args, "repair_reason", "").isBlank())
      throw new Problem(
          "repair_reference",
          "Offene Fehlprüfung mit repair_of und konkreter repair_reason referenzieren.",
          "repair",
          series);
  }

  static void reserve(Workflow w, Map<String, Object> run, Map<String, Object> args) {
    requireReference(run, args);
    var series = Json.obj(run.get("model_repair"));
    if (!Json.bool(series, "active", false)) return;
    int attempt = Json.number(series, "attempts", 0) + 1;
    series.put("attempts", attempt);
    Json.list(series.get("corrections"))
        .add(
            Json.map(
                "attempt",
                attempt,
                "repair_of",
                args.get("repair_of"),
                "reason",
                args.get("repair_reason"),
                "at",
                Json.now()));
    w.persist(run);
  }

  static void failed(Map<String, Object> run, String checkId) {
    var series = Json.obj(run.get("model_repair"));
    if (!Json.bool(series, "active", false)) {
      archive(run, series);
      series =
          Json.map(
              "id",
              UUID.randomUUID().toString(),
              "active",
              true,
              "attempts",
              0,
              "corrections",
              new ArrayList<>(),
              "first_failure",
              checkId);
      run.put("model_repair", series);
    }
    series.put("last_failure", checkId);
  }

  static void passed(Map<String, Object> run) {
    var series = Json.obj(run.get("model_repair"));
    if (!series.isEmpty()) series.put("active", false);
  }

  static Object reset(Map<String, Object> run, Map<String, Object> args) {
    String statement = Json.required(args, "human_statement");
    var series = Json.obj(run.get("model_repair"));
    archive(run, series);
    var next =
        Json.map(
            "id",
            UUID.randomUUID().toString(),
            "active",
            false,
            "attempts",
            0,
            "human_statement",
            statement,
            "at",
            Json.now(),
            "corrections",
            new ArrayList<>());
    run.put("model_repair", next);
    return next;
  }

  private static void archive(Map<String, Object> run, Map<String, Object> series) {
    if (!series.isEmpty())
      Json.list(run.computeIfAbsent("model_repair_history", k -> new ArrayList<>()))
          .add(new LinkedHashMap<>(series));
  }
}
