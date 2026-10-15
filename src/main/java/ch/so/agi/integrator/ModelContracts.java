package ch.so.agi.integrator;

import java.util.*;

/** Public change contract obtained from the configured MCP, never inferred by executing changes. */
final class ModelContracts {
  static boolean handles(String name) {
    return name.equals("derive_model") || name.equals("derive_model.changes");
  }

  static Object schema(McpClients.ToolClient client, String name) {
    try {
      var source = client.schema("applyIliModelChanges");
      var request = resolve(source, Json.obj(source.get("properties")).get("request"));
      var changes = resolve(source, Json.obj(request.get("properties")).get("changes"));
      if (!"array".equals(changes.get("type")) || !changes.containsKey("items"))
        throw new IllegalArgumentException();
      Object relocated = relocate(source, source);
      var schema = new LinkedHashMap<>(Operations.ALL.get("derive_model").schema());
      var properties = new LinkedHashMap<>(Json.obj(schema.get("properties")));
      properties.put("changes", relocate(source, changes));
      schema.put("properties", properties);
      schema.put("$defs", Json.map("interlisChangeContract", relocated));
      return Json.map(
          "operation",
          name,
          "inputSchema",
          schema,
          "description",
          "Vollständiger Integrator-Vertrag für derive_model. changes ist das Änderungsargument; Modelltext und Fach-MCP-Aufruf verwaltet der Integrator.");
    } catch (RuntimeException error) {
      throw new Problem(
          "model_contract_unavailable",
          "Vollständiger Änderungsvertrag von applyIliModelChanges nicht verfügbar oder nicht auflösbar. MCP-Verfügbarkeit/Version prüfen; keine ausführenden Probeaufrufe verwenden.");
    }
  }

  private static Object target(Map<String, Object> root, String ref) {
    if (ref.equals("#")) return root;
    if (!ref.startsWith("#/")) throw new IllegalArgumentException();
    Object value = root;
    for (String segment : ref.substring(2).split("/")) {
      String key = segment.replace("~1", "/").replace("~0", "~");
      value =
          value instanceof List<?> list
              ? list.get(Integer.parseInt(key))
              : Json.obj(value).get(key);
      if (value == null) throw new IllegalArgumentException();
    }
    return value;
  }

  private static Map<String, Object> resolve(Map<String, Object> root, Object value) {
    var seen = new HashSet<String>();
    var result = new LinkedHashMap<>(Json.obj(value));
    while (result.containsKey("$ref")) {
      String ref = Json.required(result, "$ref");
      if (!seen.add(ref)) throw new IllegalArgumentException();
      var merged = new LinkedHashMap<>(Json.obj(target(root, ref)));
      result.remove("$ref");
      merged.putAll(result);
      result = merged;
    }
    return result;
  }

  private static Object relocate(Map<String, Object> root, Object value) {
    if (value instanceof List<?> list) return list.stream().map(v -> relocate(root, v)).toList();
    if (!(value instanceof Map<?, ?>)) return value;
    var result = new LinkedHashMap<String, Object>();
    Json.obj(value)
        .forEach(
            (key, v) -> {
              if (Set.of("$id", "$anchor", "$dynamicRef", "$recursiveRef").contains(key))
                throw new IllegalArgumentException();
              if (key.equals("$ref")) {
                String ref = v.toString();
                target(root, ref);
                result.put(key, "#/$defs/interlisChangeContract" + ref.substring(1));
              } else result.put(key, relocate(root, v));
            });
    return result;
  }
}
