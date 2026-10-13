package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
  @TempDir Path temp;

  Settings parse(String text) {
    Path config = temp.resolve("local.toml");
    Json.write(config, text);
    return new Settings(config);
  }

  String example() {
    return Json.contents(Fixtures.ROOT.resolve("config/local.example.toml"));
  }

  @Test
  void rejectsLegacyRuntimeAndDoesNotRequireStack() {
    assertFalse(parse(example()).values.containsKey("stack_repo"));
    assertEquals("stdio", parse(example()).mcp("datasheet").get("transport"));
    assertEquals(
        "configuration_upgrade_required",
        assertThrows(Problem.class, () -> parse(example() + "\n[gretl]\nmode=\"compose\"\n")).code);
  }

  @Test
  void shippedExampleMatchesSchemaFieldsTypesEnumsAndJavaDefaults() {
    var settings = parse(example());
    var schema = Json.read(Fixtures.ROOT.resolve("config/settings.schema.json"));
    fields(settings.values, schema, schema);
    var properties = Json.obj(schema.get("properties"));
    assertEquals(Json.obj(properties.get("timeout_seconds")).get("default"), settings.timeout);
    assertEquals(
        Json.obj(properties.get("stack_timeout_seconds")).get("default"), settings.stackTimeout);
    var minimal =
        parse("topics_repo=\"topics\"\nstack_repo=\"stack\"\n[datasheet]\ntransport=\"stdio\"\n");
    assertTrue(minimal.mcp("interlis").isEmpty());
  }

  // Cross-check the documented field contract, not a general JSON Schema evaluator.
  void fields(Object value, Map<String, Object> node, Map<String, Object> root) {
    if (node.containsKey("$ref"))
      node =
          Json.obj(
              Json.obj(root.get("$defs"))
                  .get(Json.required(node, "$ref").substring("#/$defs/".length())));
    if (node.containsKey("enum"))
      assertTrue(Json.list(node.get("enum")).contains(value), value.toString());
    String type = Json.str(node, "type", "");
    switch (type) {
      case "object" -> {
        var object = Json.obj(value);
        var properties = Json.obj(node.get("properties"));
        for (Object required : Json.list(node.get("required")))
          assertTrue(object.containsKey(required));
        for (var entry : object.entrySet()) {
          if (properties.containsKey(entry.getKey()))
            fields(entry.getValue(), Json.obj(properties.get(entry.getKey())), root);
          else {
            assertNotEquals(false, node.get("additionalProperties"), entry.getKey());
            if (node.get("additionalProperties") instanceof Map<?, ?>)
              fields(entry.getValue(), Json.obj(node.get("additionalProperties")), root);
          }
        }
      }
      case "array" -> {
        for (Object item : Json.list(value)) fields(item, Json.obj(node.get("items")), root);
      }
      case "string" -> assertInstanceOf(String.class, value);
      case "integer" -> assertInstanceOf(Long.class, value);
      case "boolean" -> assertInstanceOf(Boolean.class, value);
      default -> assertTrue(type.isEmpty());
    }
  }

  @Test
  void rejectsWrongTypesAndUnsupportedValuesBeforeUsingConfiguration() {
    for (var change :
        List.of(
            Map.entry("timeout_seconds = 300", "timeout_seconds = 1.5"),
            Map.entry("stack_timeout_seconds = 1800", "stack_timeout_seconds = 4294967297"),
            Map.entry("model_dirs = []", "model_dirs = [1]"))) {
      assertEquals(
          "invalid_configuration",
          assertThrows(
                  Problem.class, () -> parse(example().replace(change.getKey(), change.getValue())))
              .code,
          change.toString());
    }
  }
}
