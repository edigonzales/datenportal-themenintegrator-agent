package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class DeliveryStateTest {
  @Test
  void recommendationsUseSamePublicationRulesAsRetry() {
    for (String publication :
        List.of("accepted", "unknown", "not-published", "candidates-uploaded", "unexpected", "")) {
      var item =
          Json.map(
              "phase",
              "failed",
              "job",
              Json.map("job", "fixture", "build", "18"),
              "report",
              Json.map("publication", publication));
      var view = Deliveries.describe(item);
      if (publication.equals("accepted"))
        assertEquals("verify_delivery", view.get("next_operation"));
      else if (Set.of("not-published", "candidates-uploaded").contains(publication))
        assertEquals("retry_delivery", view.get("next_operation"));
      else {
        assertNull(view.get("next_operation"));
        assertEquals("publication_unconfirmed", view.get("blocked_reason"));
      }
      assertEquals("retry_delivery".equals(view.get("next_operation")), Deliveries.retrySafe(item));
    }
  }

  @Test
  void confirmationIsBoundToFailedAttemptAndCannotSurviveChangedEvidence() {
    var item =
        Json.map(
            "phase",
            "failed",
            "job",
            Json.map("build", "18"),
            "build_url",
            "fixture/18/console",
            "fingerprint",
            "old-reviewed-stand",
            "generation",
            1,
            "result",
            "FAILURE",
            "report_count",
            0);
    var proof =
        Json.map(
            "job",
            Json.map("build", "18"),
            "build_url",
            item.get("build_url"),
            "fingerprint",
            item.get("fingerprint"),
            "generation",
            1);
    item.put("nonpublication_confirmation", proof);
    assertTrue(Deliveries.retrySafe(item));
    for (var change :
        Json.map(
                "generation",
                2,
                "result",
                "SUCCESS",
                "report_count",
                2,
                "build_url",
                "fixture/19/console",
                "fingerprint",
                "other")
            .entrySet()) {
      var changed = new LinkedHashMap<>(item);
      changed.put(change.getKey(), change.getValue());
      assertFalse(Deliveries.retrySafe(changed), change.getKey());
    }
    item.put("report", Json.map("publication", "unknown"));
    assertFalse(Deliveries.retrySafe(item));
  }
}
