package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ModelRangesTest {
  Map<String, Object> proposal(Object min, Object max, boolean negative) {
    return Json.map(
        "type_spec",
        Json.map("baseType", Json.map("kind", "NUM_RANGE", "min", min, "max", max)),
        "reason",
        "Temperaturbereich aus Beschreibung mit Reserve.",
        "negative_values_possible",
        negative);
  }

  @Test
  void boundsScaleAndSignAreValidatedWithoutHumanConfirmation() {
    var observed = Json.map("observed_min", "1.25", "observed_max", "20.50", "observed_scale", 2);
    var valid =
        ModelRanges.proposal("Temperatur", proposal("-50.00", "100.00", true), observed, "DECIMAL");
    assertEquals(false, valid.get("confirmed"));
    assertEquals("agent_estimate", valid.get("origin"));
    assertEquals(2, valid.get("precision"));
    for (var invalid :
        List.of(
            proposal("0.0", "100.0", false),
            proposal("0.00", "10.00", false),
            proposal("0.00", "100.00", true),
            proposal("-50.00", "100.00", false),
            proposal("10.00", "0.00", false)))
      assertThrows(
          Problem.class, () -> ModelRanges.proposal("Temperatur", invalid, observed, "DECIMAL"));
    assertThrows(
        Problem.class,
        () ->
            ModelRanges.proposal(
                "Temperatur", proposal("0.00", "100.00", false), observed, "INTEGER"));
  }
}
