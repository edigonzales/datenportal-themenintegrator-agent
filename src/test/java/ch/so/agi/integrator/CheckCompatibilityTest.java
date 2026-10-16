package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CheckCompatibilityTest {
  @Test
  void exactReviewedArtifactPairPreservesOnlyIdenticalOtherEvidence() {
    String old = "a".repeat(64), current = "b".repeat(64);
    var before =
        Json.map(
            "components",
            Json.map("integrator", old, "interlis", "same"),
            "files",
            Json.map("data", "same"));
    var actual = Json.read(Json.text(before));
    Json.obj(actual.get("components")).put("integrator", current);
    var pair = Json.map("from", old, "to", current);
    String saved = Json.text(before);
    assertTrue(CheckStates.compatible(before, actual, pair));
    assertEquals(saved, Json.text(before));
    assertFalse(CheckStates.compatible(before, actual, Json.map()));
    assertFalse(
        CheckStates.compatible(before, actual, Json.map("from", old, "to", "c".repeat(64))));
    Json.obj(actual.get("components")).put("interlis", "changed");
    assertFalse(CheckStates.compatible(before, actual, pair));
    Json.obj(actual.get("components")).put("interlis", "same");
    Json.obj(actual.get("files")).put("data", "changed");
    assertFalse(CheckStates.compatible(before, actual, pair));
  }
}
