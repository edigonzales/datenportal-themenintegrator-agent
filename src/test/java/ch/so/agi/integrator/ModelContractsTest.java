package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ModelContractsTest {
  static McpClients.ToolClient client(Map<String, Object> schema) {
    return new McpClients.ToolClient() {
      public Map<String, Object> call(String name, Map<String, Object> args) {
        fail("Schema discovery must not execute model changes");
        return null;
      }

      public Map<String, Object> schema(String name) {
        assertEquals("applyIliModelChanges", name);
        return schema;
      }
    };
  }

  @Test
  void preservesNestedAndRecursiveReferencesWithoutMutatingSource() {
    var source =
        Json.map(
            "type",
            "object",
            "properties",
            Json.map("request", Json.map("$ref", "#/$defs/Request")),
            "$defs",
            Json.map(
                "Request",
                Json.map(
                    "type",
                    "object",
                    "properties",
                    Json.map(
                        "changes",
                        Json.map("type", "array", "items", Json.map("$ref", "#/$defs/Change")))),
                "Change",
                Json.map(
                    "oneOf",
                    List.of(
                        Json.map(
                            "type",
                            "object",
                            "required",
                            List.of("operation", "addConstraint"),
                            "properties",
                            Json.map(
                                "operation",
                                Json.map("const", "ADD_CONSTRAINT"),
                                "addConstraint",
                                Json.map("$ref", "#/$defs/Constraint"))))),
                "Constraint",
                Json.map(
                    "type",
                    "object",
                    "properties",
                    Json.map("nested", Json.map("$ref", "#/$defs/Constraint")))));
    String original = Json.text(source);
    var whole = Json.obj(ModelContracts.schema(client(source), "derive_model"));
    var focused = Json.obj(ModelContracts.schema(client(source), "derive_model.changes"));
    assertEquals(whole.get("inputSchema"), focused.get("inputSchema"));
    String output = Json.text(whole);
    assertTrue(output.contains("#/$defs/interlisChangeContract/$defs/Constraint"));
    assertTrue(output.contains("ADD_CONSTRAINT"));
    assertTrue(output.contains("addConstraint"));
    assertEquals(original, Json.text(source));
  }

  @Test
  void unavailableOrUnresolvableContractsFailExplicitly() {
    for (var schema :
        List.of(
            Json.map(),
            Json.map("properties", Json.map("request", Json.map("$ref", "#/missing"))),
            Json.map(
                "properties",
                Json.map("request", Json.map("$ref", "https://example.org/schema"))))) {
      var error =
          assertThrows(Problem.class, () -> ModelContracts.schema(client(schema), "derive_model"));
      assertEquals("model_contract_unavailable", error.code);
    }
    var error =
        assertThrows(
            Problem.class, () -> ModelContracts.schema((name, args) -> Json.map(), "derive_model"));
    assertEquals("model_contract_unavailable", error.code);
  }
}
