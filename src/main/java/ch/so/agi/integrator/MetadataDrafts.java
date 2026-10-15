package ch.so.agi.integrator;

import java.util.*;

/** Confirmed draft recovery points; never promote a partially executed creation. */
final class MetadataDrafts {
  static final Set<String> ISSUE_FIELDS = Set.of("issue_label", "is_current_issue");

  static String implementationIdentity() {
    var classes = new ArrayList<String>();
    for (Class<?> type : List.of(MetadataDrafts.class, Workflow.class, McpClients.class)) {
      try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
        classes.add(Base64.getEncoder().encodeToString(Objects.requireNonNull(in).readAllBytes()));
      } catch (java.io.IOException e) {
        throw new Problem(
            "draft_recovery_identity_unavailable",
            "Wiederherstellungsimplementierung nicht lesbar.");
      }
    }
    return Json.digest(classes);
  }

  static void preflight(Map<String, Object> run, String operation, Map<String, Object> input) {
    if (Set.of("create_datasheet", "import_xtf").contains(operation) && run.containsKey("draft"))
      throw new Problem(
          "draft_exists",
          "Bestätigter Entwurf vorhanden. read_datasheet, Bearbeitungsoperationen oder restore verwenden; keine erneute Erstellung/kein erneuter Import.");
    var values = Json.obj(input.get("values"));
    if (Set.of("create_datasheet", "update_metadata").contains(operation)
        && !input.containsKey("issue_id")) {
      for (String field : ISSUE_FIELDS)
        if (values.containsKey(field))
          throw new Problem(
              "metadata_field_scope",
              "Ausgabefeld gehört zu upsert_issue(values=…) ohne issue_id für eine neue Ausgabe; bestehende Ausgabe über ihre zurückgelieferte issue_id bearbeiten.",
              "field",
              field,
              "next_operation",
              "upsert_issue");
    }
    if (input.containsKey("issue_id")) {
      String id = Json.required(input, "issue_id");
      Object resolved = Json.obj(run.get("draft_id_aliases")).getOrDefault(id, id);
      var issues = Json.list(Json.obj(Json.obj(run.get("draft")).get("data")).get("issues"));
      var issue =
          issues.stream()
              .map(Json::obj)
              .filter(i -> Objects.equals(i.get("issue_id"), resolved))
              .findFirst();
      if (issue.isEmpty())
        throw new Problem(
            "metadata_issue_not_found",
            "issue_id bezeichnet eine zurückgelieferte interne Kennung, nicht die Ausgabebezeichnung. Neue Ausgabe mit upsert_issue(values={identifier: …, issue_label: …, …}) ohne issue_id anlegen.",
            "issue_id",
            id,
            "known_issues",
            issues.stream()
                .map(Json::obj)
                .map(
                    i ->
                        Json.map(
                            "issue_id",
                            i.get("issue_id"),
                            "identifier",
                            i.get("identifier"),
                            "issue_label",
                            i.get("issue_label")))
                .toList());
      if (Set.of("update_metadata", "upsert_issue").contains(operation)
          && values.containsKey("identifier")
          && !Objects.equals(values.get("identifier"), issue.get().get("identifier")))
        throw new Problem(
            "datasheet_issue_identifier_mismatch",
            "Identifier der adressierten Ausgabe muss erhalten bleiben.",
            "expected",
            issue.get().get("identifier"),
            "actual",
            values.get("identifier"));
    }
    if (operation.equals("upsert_issue")
        && !input.containsKey("issue_id")
        && Json.str(values, "identifier", "").isBlank())
      throw new Problem(
          "metadata_issue_identifier_required",
          "Fachlichen Ausgabe-Identifier in values.identifier angeben. Vorgangs-issue und issue_label sind keine interne issue_id und werden nicht automatisch als Identifier übernommen.");
  }

  static void checkpoint(Map<String, Object> run) {
    var value = Json.map("base", run.get("draft_base"), "snapshot", run.get("draft"));
    run.put(
        "draft_checkpoint",
        Json.map("value", Json.read(Json.text(value)), "sha256", Json.digest(value)));
    run.remove("draft_restore_blocker");
  }

  static Map<String, Object> recovery(Map<String, Object> run) {
    var copy = Json.read(Json.text(run));
    var checkpoint = Json.obj(run.get("draft_checkpoint"));
    if (!checkpoint.isEmpty()) {
      var value = Json.obj(checkpoint.get("value"));
      if (!Objects.equals(checkpoint.get("sha256"), Json.digest(value))
          || !Objects.equals(value.get("snapshot"), run.get("draft")))
        throw new Problem(
            "draft_checkpoint_invalid",
            "Gesicherter Wiederherstellungspunkt ist inkonsistent; keine MCP-Mutation ausgeführt.");
      copy.put("draft_base", value.get("base"));
      return copy;
    }
    var draft = Json.obj(run.get("draft"));
    if (matchingIdentity(Json.str(run, "draft_base", ""), draft)) return copy;
    var pending = Json.obj(run.get("pending_metadata"));
    var pendingArgs = Json.obj(pending.get("arguments"));
    boolean legacyCreation =
        "create_datasheet".equals(pending.get("operation"))
            && Objects.equals(pendingArgs.get("kind"), draft.get("kind"))
            && !run.containsKey("exported_artifact_sha256")
            && !Json.obj(run.get("files")).containsKey("metadata")
            && Objects.equals(Json.obj(draft.get("data")).get("identifier"), run.get("identifier"))
            && generatedEmptyBase(Json.str(run, "draft_base", ""));
    if (!legacyCreation)
      throw new Problem(
          "draft_recovery_evidence_missing",
          "Basis passt nicht zum bestätigten Snapshot. Herkunft nicht eindeutig rekonstruierbar; keine MCP-Mutation ausgeführt.");
    copy.put(
        "draft_base",
        McpClients.shell(
            Json.required(draft, "kind"),
            Json.required(draft, "basket_id"),
            Json.required(draft, "object_id")));
    return copy;
  }

  static boolean matchingIdentity(String base, Map<String, Object> draft) {
    try {
      var roots = Xml.datasets(Xml.parse(base));
      if (roots.size() != 1) return false;
      var root = roots.getFirst();
      return Objects.equals(root.getAttributeNS(Xml.ILI, "tid"), draft.get("object_id"))
          && root.getParentNode() instanceof org.w3c.dom.Element parent
          && Objects.equals(parent.getAttributeNS(Xml.ILI, "bid"), draft.get("basket_id"))
          && Objects.equals(
              Xml.local(root), "series".equals(draft.get("kind")) ? "DatasetSeries" : "Dataset");
    } catch (RuntimeException ex) {
      return false;
    }
  }

  static boolean generatedEmptyBase(String base) {
    try {
      var doc = Xml.parse(base);
      var roots = Xml.datasets(doc);
      return roots.size() == 1
          && Xml.children(roots.getFirst()).isEmpty()
          && doc.getElementsByTagNameNS(Xml.ILI, "sender").getLength() == 1
          && "datenportal-integrator"
              .equals(doc.getElementsByTagNameNS(Xml.ILI, "sender").item(0).getTextContent());
    } catch (RuntimeException ex) {
      return false;
    }
  }

  static void compare(Map<String, Object> before, Map<String, Object> after) {
    var identities = new ArrayList<String>();
    for (String key : List.of("kind", "basket_id", "object_id"))
      if (!Objects.equals(before.get(key), after.get(key))) identities.add(key);
    var fields = new ArrayList<String>();
    differences(
        Workflow.semantic(before.get("data")),
        Workflow.semantic(after.get("data")),
        "data",
        fields);
    if (!identities.isEmpty() || !fields.isEmpty())
      throw new Problem(
          "draft_restore_mismatch",
          "Wiederherstellung stimmt nicht mit dem bestätigten Snapshot überein. Keine Übernahme; Ursache anhand der betroffenen Felder klären.",
          "identity_fields",
          identities,
          "content_fields",
          fields);
  }

  static void differences(Object a, Object b, String path, List<String> out) {
    if (Objects.equals(a, b)) return;
    if (a instanceof Map<?, ?> am && b instanceof Map<?, ?> bm) {
      var keys = new TreeSet<String>();
      am.keySet().forEach(k -> keys.add(k.toString()));
      bm.keySet().forEach(k -> keys.add(k.toString()));
      for (String key : keys) differences(am.get(key), bm.get(key), path + "." + key, out);
    } else if (a instanceof List<?> al && b instanceof List<?> bl && al.size() == bl.size()) {
      for (int i = 0; i < al.size(); i++)
        differences(al.get(i), bl.get(i), path + "[" + i + "]", out);
    } else out.add(path);
  }
}
