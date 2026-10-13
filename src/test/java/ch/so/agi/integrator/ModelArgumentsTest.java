package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModelArgumentsTest {
  @Test
  void equivalentNumericAndTextFormsNormalizeWithoutLosingScale() {
    assertEquals(
        ModelArguments.spec(
            Json.map("baseType", Json.map("kind", "NUM_RANGE", "min", "0.00", "max", "999.00")),
            "x"),
        ModelArguments.spec(
            Json.map(
                "baseType",
                Json.map("kind", "NUMERIC", "min", "0.00", "max", "999.00", "precision", 2)),
            "x"));
    assertEquals(
        ModelArguments.spec(Json.map("baseType", Json.map("kind", "TEXT", "length", 100)), "x"),
        ModelArguments.spec(Json.map("baseType", Json.map("kind", "TEXT", "maxLength", 100)), "x"));
  }

  @Test
  void inconsistentOrUnknownFieldsAreRejected() {
    for (var base :
        java.util.List.of(
            Json.map("kind", "NUMERIC", "min", 0),
            Json.map("kind", "NUM_RANGE", "min", 10, "max", 0),
            Json.map("kind", "NUM_RANGE", "min", "0.0", "max", "99.00"),
            Json.map("kind", "NUM_RANGE", "min", 0, "max", 99, "precision", 2),
            Json.map("kind", "TEXT", "length", 2, "maxLength", 3),
            Json.map("kind", "TEXT", "length", 2.5),
            Json.map("kind", "TEXT", "max_length", 100)))
      assertThrows(Problem.class, () -> ModelArguments.spec(Json.map("baseType", base), "x"));
    var error =
        assertThrows(
            Problem.class,
            () ->
                ModelArguments.confirmation(
                    Json.map("confirmed", true, "min", 0), "confirmations.Anzahl"));
    assertEquals("confirmations.Anzahl.min", error.details.get("field"));
  }
}
