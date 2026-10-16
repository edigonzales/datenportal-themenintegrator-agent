package ch.so.agi.integrator;

import java.util.*;

/** Human rule decisions are independent of the mutable model candidate and its fingerprint. */
final class ModelRuleApprovals {
  static boolean current(Workflow w, Map<String, Object> run) {
    try {
      w.require(run, "model");
      return true;
    } catch (Problem ignored) {
      return false;
    }
  }

  static List<Object> view(Workflow w, Map<String, Object> run) {
    var view = new LinkedHashMap<>(run);
    recover(w, view);
    return effective(view);
  }

  static void record(Workflow w, Map<String, Object> run, Map<String, Object> approval) {
    var model = Json.obj(run.get("model"));
    var records = new ArrayList<>(Json.list(run.get("model_rule_approvals")));
    for (Object value : Json.list(model.get("mapping"))) {
      var mapping = Json.obj(value);
      var spec = Json.obj(mapping.get("type_spec"));
      if (!"NUM_RANGE".equals(Json.obj(spec.get("baseType")).get("kind"))) continue;
      var record =
          Json.map(
              "column",
              mapping.get("column"),
              "attribute",
              mapping.get("attribute"),
              "contract_sha256",
              model.get("contract_sha256"),
              "type_spec",
              spec,
              "model_sha256",
              Json.sha(w.requiredFile(run, "model")),
              "approval",
              approval,
              "range_proposal",
              mapping.get("range_proposal"));
      if (records.stream().noneMatch(old -> Json.digest(old).equals(Json.digest(record))))
        records.add(record);
    }
    run.put("model_rule_approvals", records);
  }

  static void recover(Workflow w, Map<String, Object> run) {
    var approval = Json.obj(Json.obj(run.get("approvals")).get("model"));
    if (Json.str(approval, "human_statement", "").isBlank() || !run.containsKey("model")) return;
    try {
      w.require(run, "model");
    } catch (Problem ignored) {
      return;
    }
    record(w, run, approval);
  }

  static Map<String, Object> find(Map<String, Object> run, Object contract, String column) {
    var records = Json.list(run.get("model_rule_approvals"));
    for (int i = records.size() - 1; i >= 0; i--) {
      var record = Json.obj(records.get(i));
      if (Objects.equals(contract, record.get("contract_sha256"))
          && column.equals(record.get("column"))) return record;
    }
    return Map.of();
  }

  static List<Object> effective(Map<String, Object> run) {
    var model = Json.obj(run.get("model"));
    var result = new ArrayList<Object>();
    for (Object value : Json.list(model.get("mapping"))) {
      var mapping = Json.obj(value);
      if (!"NUM_RANGE"
          .equals(Json.obj(Json.obj(mapping.get("type_spec")).get("baseType")).get("kind")))
        continue;
      var record = find(run, model.get("contract_sha256"), Json.required(mapping, "column"));
      boolean approved =
          !record.isEmpty() && Objects.equals(record.get("type_spec"), mapping.get("type_spec"));
      boolean explicit = Json.bool(Json.obj(mapping.get("confirmed")), "confirmed", false);
      result.add(
          Json.map(
              "column",
              mapping.get("column"),
              "type_spec",
              mapping.get("type_spec"),
              "origin",
              Json.str(Json.obj(mapping.get("range_proposal")), "origin", "explicit_specification"),
              "confirmed",
              approved || explicit,
              "confirmation_source",
              approved ? "model_approval" : explicit ? "explicit_confirmation" : "unconfirmed",
              "evidence",
              approved ? record : Json.obj(mapping.get("confirmed"))));
    }
    return result;
  }
}
