package ch.so.agi.integrator;

import java.util.*;

final class MetadataContracts {
  static final List<String> NAMES =
      List.of(
          "describe_schema",
          "create_datasheet",
          "import_xtf",
          "read_datasheet",
          "update_metadata",
          "upsert_attribute",
          "remove_attribute",
          "upsert_issue",
          "remove_issue",
          "validate_datasheet",
          "export_xtf",
          "restore");

  static Object schema(McpClients.ToolClient client, String name) {
    if (!NAMES.contains(name))
      throw new Problem(
          "unknown_operation", "Nicht unterstützte Datenblattoperation.", "operations", NAMES);
    Map<String, Object> arguments;
    if (Set.of("import_xtf", "restore", "export_xtf", "read_datasheet", "validate_datasheet")
        .contains(name))
      arguments =
          Json.map("type", "object", "properties", Json.map(), "additionalProperties", false);
    else {
      arguments = new LinkedHashMap<>(client.schema(name));
      var props = new LinkedHashMap<>(Json.obj(arguments.get("properties")));
      var managed = Set.of("draft_id", "expected_revision");
      managed.forEach(props::remove);
      arguments.put("properties", props);
      arguments.put(
          "required",
          Json.list(arguments.get("required")).stream()
              .filter(v -> !managed.contains(v.toString()))
              .toList());
    }
    var schema = new LinkedHashMap<>(Operations.ALL.get("metadata").schema());
    var props = new LinkedHashMap<>(Json.obj(schema.get("properties")));
    props.put("operation", Json.map("type", "string", "enum", List.of(name)));
    props.put("arguments", arguments);
    schema.put("properties", props);
    if (!Json.list(arguments.get("required")).isEmpty())
      schema.put("required", List.of("run_id", "operation", "arguments"));
    return Json.map(
        "operation",
        "metadata." + name,
        "inputSchema",
        schema,
        "managed_by_integrator",
        List.of("draft_id", "expected_revision", "import source", "export destination"),
        "description",
        name.equals("import_xtf")
            ? "Importiert die bereits gesicherte Quelle in einen bearbeitbaren Entwurf; zur Modellableitung nicht erforderlich."
            : "Argumente des Integrator-Aufrufs metadata; describe_schema beschreibt fachliche Felder.");
  }
}
