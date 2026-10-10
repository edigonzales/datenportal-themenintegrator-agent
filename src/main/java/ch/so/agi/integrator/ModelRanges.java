package ch.so.agi.integrator;

import java.math.BigDecimal;
import java.util.*;

/** Checks proposals against complete CSV statistics; never grants human confirmation. */
final class ModelRanges {
  static Map<String, Object> proposal(
      String column, Map<String, Object> proposal, Map<String, Object> observed, String type) {
    if (Json.str(proposal, "reason", "").isBlank()
        || !(proposal.get("negative_values_possible") instanceof Boolean))
      throw new Problem(
          "range_proposal_invalid",
          "Begründung und negative_values_possible erforderlich.",
          "column",
          column);
    var spec = Json.obj(proposal.get("type_spec"));
    var base = Json.obj(spec.get("baseType"));
    try {
      if (!Set.of("INTEGER", "DECIMAL", "NUMERIC").contains(type)
          || !"NUM_RANGE".equals(base.get("kind"))
          || spec.containsKey("domainFqn")) throw new IllegalArgumentException();
      BigDecimal min = new BigDecimal(base.get("min").toString()),
          max = new BigDecimal(base.get("max").toString());
      int scale = Math.max(0, min.scale());
      if (min.compareTo(max) >= 0
          || min.scale() != max.scale()
          || (type.equals("INTEGER") && scale != 0)
          || scale < Json.number(observed, "observed_scale", 0)
          || (Boolean.FALSE.equals(proposal.get("negative_values_possible")) && min.signum() < 0)
          || (Boolean.TRUE.equals(proposal.get("negative_values_possible")) && min.signum() >= 0))
        throw new IllegalArgumentException();
      if (observed.get("observed_min") != null
          && (min.compareTo(new BigDecimal(observed.get("observed_min").toString())) > 0
              || max.compareTo(new BigDecimal(observed.get("observed_max").toString())) < 0))
        throw new IllegalArgumentException();
      return Json.map(
          "type_spec",
          spec,
          "reason",
          proposal.get("reason"),
          "negative_values_possible",
          proposal.get("negative_values_possible"),
          "precision",
          scale,
          "origin",
          "agent_estimate",
          "confirmed",
          false,
          "observed_min",
          observed.get("observed_min"),
          "observed_max",
          observed.get("observed_max"));
    } catch (RuntimeException e) {
      throw new Problem(
          "range_proposal_invalid",
          "Numerischer Bereich/Genauigkeit muss fachliche Vorzeichen und alle beobachteten Werte abdecken.",
          "column",
          column);
    }
  }
}
