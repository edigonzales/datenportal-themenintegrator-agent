package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

/** Isolated numerical contract regression through the packaged stdio MCP. */
@Tag("integration")
class DecimalMcpIntegrationTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

  @Test
  void hotellerieDecimalBoundsSurviveAuthoringAndChanges() {
    try (var client = McpClients.interlis(Fixtures.integrationSettings())) {
      NumericModelContract.requireCapability(client.schema("authorIliModel"));
      var attributes = new ArrayList<Object>();
      var mappings = new ArrayList<Object>();
      var bounds =
          List.of(
              List.of("0.0", "100.0"),
              List.of("0.00", "100.00"),
              List.of("0.00", "999.99"),
              List.of("-100.00", "100.00"),
              List.of("0", "100"),
              List.of("0.00", "9007199254740993.00"));
      for (int i = 0; i < bounds.size(); i++) {
        var spec = NumericModelContractTest.spec(bounds.get(i).get(0), bounds.get(i).get(1));
        attributes.add(Json.map("name", "value" + i, "typeSpec", spec));
        mappings.add(Json.map("attribute", "DecimalTest.Data.Row.value" + i, "type_spec", spec));
      }
      var args =
          Json.map(
              "modelPurpose",
              "VALIDATION",
              "ruleProfile",
              "CORE",
              "spec",
              Json.map(
                  "name",
                  "DecimalTest",
                  "uri",
                  "https://example.org",
                  "version",
                  "2026-10-15",
                  "iliVersion",
                  "2.4",
                  "topics",
                  List.of(
                      Json.map(
                          "name",
                          "Data",
                          "classes",
                          List.of(Json.map("name", "Row", "attributes", attributes))))));
      var result = client.call("authorIliModel", Json.read(Json.text(args)));
      assertTrue(result.containsKey("updatedModelText"), Json.pretty(result));
      String text = Json.required(result, "updatedModelText");
      NumericModelContract.verify(text, mappings, List.of());
      assertTrue(text.contains("0.00 .. 999.99"), text);
      var modelFile = temp.resolve("DecimalTest.ili");
      var csvFile = temp.resolve("decimal.csv");
      Json.write(modelFile, text);
      Json.write(csvFile, "value0;value1;value2;value3;value4;value5\n3.4;3.40;0.00;0.00;1;1.00\n");
      var validation =
          new CsvValidation(Fixtures.integrationSettings(), new ProcessRunner())
              .validate(csvFile, modelFile, "DecimalTest", temp.resolve("validation"));
      assertTrue(
          Json.bool(validation, "valid", false),
          Json.pretty(validation)
              + Json.contents(java.nio.file.Path.of(Json.required(validation, "log"))));
      var spec = NumericModelContractTest.spec("0.00", "100.00");
      var changed =
          client.call(
              "applyIliModelChanges",
              Json.map(
                  "modelText",
                  text,
                  "modelPurpose",
                  "VALIDATION",
                  "ruleProfile",
                  "CORE",
                  "request",
                  Json.map(
                      "allowPotentiallyBreaking",
                      true,
                      "changes",
                      List.of(
                          Json.map(
                              "operation",
                              "UPDATE_ATTRIBUTE",
                              "updateAttribute",
                              Json.map(
                                  "attributeFqn",
                                  "DecimalTest.Data.Row.value0",
                                  "patch",
                                  Json.map("typeSpec", spec)))))));
      mappings.set(0, Json.map("attribute", "DecimalTest.Data.Row.value0", "type_spec", spec));
      NumericModelContract.verify(Json.required(changed, "updatedModelText"), mappings, List.of());
      // Both input representations remain supported by the public MCP.
      var invalid = Json.read(Json.text(args));
      var topic = Json.obj(Json.list(Json.obj(invalid.get("spec")).get("topics")).getFirst());
      var clazz = Json.obj(Json.list(topic.get("classes")).getFirst());
      var first = Json.obj(Json.list(clazz.get("attributes")).getFirst());
      first.put("typeSpec", NumericModelContractTest.spec("0.0", "100.00"));
      var rejected = client.call("authorIliModel", invalid);
      assertEquals("INVALID_SPEC", rejected.get("status"), Json.pretty(rejected));
    }
  }
}
