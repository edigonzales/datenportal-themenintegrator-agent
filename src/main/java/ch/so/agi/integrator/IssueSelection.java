package ch.so.agi.integrator;

import java.util.*;

/** Updates the delivery context without replacing the run or its business artifacts. */
final class IssueSelection {
  static Object set(Map<String, Object> run, Map<String, Object> args) {
    String issue = Json.required(args, "issue");
    String statement = Json.required(args, "human_statement");
    if (!issue.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") || issue.contains(".."))
      throw new Problem(
          "invalid_issue",
          "Ausgabenbezeichnung benötigt 1–128 Buchstaben/Ziffern, Punkt, Bindestrich oder Unterstrich; keine Pfade oder Leerzeichen.");
    if (Objects.equals(run.get("workflow"), "organization"))
      throw new Problem(
          "invalid_workflow", "Organisationsvorgänge haben keine Ausgabenbezeichnung.");
    if (Objects.equals(run.get("issue"), issue))
      return Json.map("run_id", run.get("id"), "issue", issue, "changed", false);
    if (run.containsKey("pending_metadata") || run.containsKey("pending_model"))
      throw new Problem(
          "issue_change_blocked", "Unklaren MCP-Aufruf zuerst im bestehenden Vorgang aufklären.");
    if (!Json.obj(run.get("deliveries")).isEmpty()
        || !Json.obj(run.get("seeds")).isEmpty()
        || run.containsKey("applied")
        || run.containsKey("pr_submission")
        || run.containsKey("pull_request"))
      throw new Problem(
          "issue_change_blocked",
          "Vorgang wurde bereits übernommen oder extern eingereicht. Bestehenden Lauf aufklären; Ausgabe nicht umdeuten.");
    var previous =
        Json.map(
            "at",
            Json.now(),
            "before",
            run.get("issue"),
            "after",
            issue,
            "human_statement",
            statement,
            "checks",
            run.get("checks"),
            "approvals",
            run.get("approvals"),
            "publication_plans",
            run.get("publication_plans"));
    if (!run.containsKey("issue_history")) run.put("issue_history", new ArrayList<>());
    Json.list(run.get("issue_history")).add(previous);
    run.put("issue", issue);
    run.put("checks", Json.map());
    run.put("approvals", Json.map());
    run.remove("publication_plans");
    Store.event(
        run,
        "set_issue",
        Json.map("before", previous.get("before"), "issue", issue, "human_statement", statement));
    return Json.map(
        "run_id",
        run.get("id"),
        "issue",
        issue,
        "changed",
        true,
        "invalidated",
        List.of("checks", "approvals", "publication_plans"),
        "next",
        "analyze und validate sowie bei vorhandenem Modell validate_model(scope=integration) ausführen; aktualisierte Berichte zeigen. Keine Freigabe erteilt.");
  }
}
