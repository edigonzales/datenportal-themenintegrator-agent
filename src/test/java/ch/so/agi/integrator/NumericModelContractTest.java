package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class NumericModelContractTest {
  static Map<String, Object> spec(String min, String max) {
    return Json.map("baseType", Json.map("kind", "NUM_RANGE", "min", min, "max", max));
  }

  static String model(String min, String max) {
    return "INTERLIS 2.4; MODEL DecimalTest AT \"https://example.org\" VERSION \"2026-10-15\" = TOPIC Data = CLASS Row = value : "
        + min
        + " .. "
        + max
        + "; END Row; END Data; END DecimalTest.";
  }

  static List<Object> mapping(String min, String max) {
    return List.of(
        Json.map("attribute", "DecimalTest.Data.Row.value", "type_spec", spec(min, max)));
  }

  @Test
  void compiledRangesPreserveScale() {
    for (var bounds :
        List.of(
            List.of("0.0", "100.0"),
            List.of("0.00", "100.00"),
            List.of("0.00", "999.99"),
            List.of("-100.00", "100.00"),
            List.of("0", "100"),
            List.of("0.00", "9007199254740993.00"))) {
      NumericModelContract.verify(
          model(bounds.get(0), bounds.get(1)), mapping(bounds.get(0), bounds.get(1)), List.of());
    }
  }

  @Test
  void catchesCompilablePrecisionLossAndChangedZero() {
    for (var bounds : List.of(List.of("0", "100"), List.of("0.01", "100.00"))) {
      var error =
          assertThrows(
              Problem.class,
              () ->
                  NumericModelContract.verify(
                      model(bounds.get(0), bounds.get(1)), mapping("0.00", "100.00"), List.of()));
      assertEquals("model_numeric_contract_violation", error.code);
      assertEquals("DecimalTest.Data.Row.value", error.details.get("attribute"));
    }
  }

  @Test
  void confirmedBoundsMustCoverObservations() {
    var observed = Json.map("observed_min", "3.4", "observed_max", "3.4", "observed_scale", 1);
    assertNotNull(NumericModelContract.observationConflict(spec("0", "100"), observed, "NUMERIC"));
    assertNull(NumericModelContract.observationConflict(spec("0.0", "100.0"), observed, "NUMERIC"));
    assertNull(
        NumericModelContract.observationConflict(spec("0.00", "100.00"), observed, "NUMERIC"));
    assertNotNull(
        NumericModelContract.observationConflict(spec("4.0", "100.0"), observed, "NUMERIC"));
  }

  @Test
  void transportKeepsStringsAndOldComponentsFailExplicitly() {
    var canonical = ModelArguments.spec(spec("0.00", "100.00"), "value");
    assertEquals(canonical, Json.read(Json.text(ModelArguments.mcpSpec(canonical))));
    assertEquals(
        "model_component_upgrade_required",
        assertThrows(Problem.class, () -> NumericModelContract.requireCapability(Map.of())).code);
    NumericModelContract.requireCapability(Json.map("x-interlis-decimal-bounds", "strings-v1"));
  }

  @Test
  void typedChangesCannotOverrideMappedSemantics() {
    var change =
        Json.map(
            "operation",
            "UPDATE_ATTRIBUTE",
            "updateAttribute",
            Json.map(
                "attributeFqn",
                "DecimalTest.Data.Row.value",
                "patch",
                Json.map("typeSpec", spec("0.01", "100.00"))));
    assertEquals(
        "confirmed_semantics_conflict",
        assertThrows(
                Problem.class,
                () -> NumericModelContract.expectations(mapping("0.00", "100.00"), List.of(change)))
            .code);
    var extra =
        Json.map(
            "operation",
            "ADD_ATTRIBUTE",
            "addAttribute",
            Json.map(
                "containerFqn",
                "DecimalTest.Data.Row",
                "attribute",
                Json.map("name", "extra", "typeSpec", spec("0.00", "999.99"))));
    assertEquals(
        2, NumericModelContract.expectations(mapping("0.00", "100.00"), List.of(extra)).size());
  }
}
