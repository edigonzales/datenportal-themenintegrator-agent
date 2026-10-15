package ch.so.agi.integrator;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.NumericType;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Checks numerical semantics on compiled attributes, independently of generator claims. */
final class NumericModelContract {
  static void requireCapability(Map<String, Object> schema) {
    if (!"strings-v1".equals(schema.get("x-interlis-decimal-bounds")))
      throw new Problem(
          "model_component_upgrade_required",
          "INTERLIS-MCP mit verlustfreien Dezimalstrings erforderlich; Komponente aktualisieren. Keine alternativen Wertebereiche versuchen.");
  }

  static String observationConflict(
      Map<String, Object> spec, Map<String, Object> observed, String type) {
    var base = Json.obj(spec.get("baseType"));
    if (!"NUM_RANGE".equals(base.get("kind"))) return null;
    var min = new BigDecimal(base.get("min").toString());
    var max = new BigDecimal(base.get("max").toString());
    if ((type.equals("INTEGER") && min.scale() != 0)
        || min.scale() < Json.number(observed, "observed_scale", 0)
        || (observed.get("observed_min") != null
            && (min.compareTo(new BigDecimal(observed.get("observed_min").toString())) > 0
                || max.compareTo(new BigDecimal(observed.get("observed_max").toString())) < 0)))
      return "Bestätigter Bereich "
          + min.toPlainString()
          + " .. "
          + max.toPlainString()
          + " widerspricht Datenblatttyp oder CSV-Beobachtungen "
          + Json.text(observed)
          + ". Fachlich klären; keine automatische Rundung oder Grenzänderung.";
    return null;
  }

  static List<Object> expectations(List<Object> mappings, Object changes) {
    var result = new ArrayList<Object>(mappings);
    for (Object value : Json.list(changes)) {
      var change = Json.obj(value);
      String path = null;
      Map<String, Object> spec = Map.of();
      if ("UPDATE_ATTRIBUTE".equals(change.get("operation"))) {
        var update = Json.obj(change.get("updateAttribute"));
        path = Json.str(update, "attributeFqn", null);
        spec = Json.obj(Json.obj(update.get("patch")).get("typeSpec"));
      } else if ("ADD_ATTRIBUTE".equals(change.get("operation"))) {
        var add = Json.obj(change.get("addAttribute"));
        var attribute = Json.obj(add.get("attribute"));
        path = Json.str(add, "containerFqn", "") + "." + Json.str(attribute, "name", "");
        spec = Json.obj(attribute.get("typeSpec"));
      }
      if (path != null && "NUM_RANGE".equals(Json.obj(spec.get("baseType")).get("kind"))) {
        var canonical = ModelArguments.spec(spec, "changes." + path);
        for (Object old : mappings) {
          var mapping = Json.obj(old);
          if (path.equals(mapping.get("attribute"))
              && !Json.obj(mapping.get("type_spec")).equals(canonical))
            throw new Problem(
                "confirmed_semantics_conflict",
                "Änderungsbatch widerspricht der abgeleiteten Typsemantik; Fachwerte zuerst klären.",
                "attribute",
                path,
                "expected",
                mapping.get("type_spec"),
                "requested",
                canonical);
        }
        result.add(Json.map("attribute", path, "type_spec", canonical));
      }
    }
    return result;
  }

  static void verify(String text, List<Object> mappings, List<String> modelPaths) {
    var numeric =
        mappings.stream()
            .map(Json::obj)
            .filter(
                m ->
                    "NUM_RANGE"
                        .equals(Json.obj(Json.obj(m.get("type_spec")).get("baseType")).get("kind")))
            .toList();
    if (numeric.isEmpty()) return;
    Path directory = null;
    try {
      directory = Files.createTempDirectory("integrator-numeric-contract-");
      Path file = directory.resolve("candidate.ili");
      Files.writeString(file, text);
      var td =
          ch.interlis.ili2c.Main.compileIliFiles(
              new ArrayList<>(List.of(file.toString())), new ArrayList<>(modelPaths), null);
      if (td == null)
        throw new Problem(
            "model_numeric_contract_violation",
            "Modellstruktur nicht kompilierbar; Generatorergebnis nicht übernommen.");
      for (var mapping : numeric) {
        String path = Json.required(mapping, "attribute");
        var expected = Json.obj(Json.obj(mapping.get("type_spec")).get("baseType"));
        var element = td.getElement(path);
        Map<String, Object> actual = Json.map();
        if (element instanceof AttributeDef attr
            && attr.getDomainResolvingAliases() instanceof NumericType type
            && type.getMinimum() != null
            && type.getMaximum() != null) {
          actual.put("min", type.getMinimum().toString());
          actual.put("max", type.getMaximum().toString());
        }
        for (String bound : List.of("min", "max")) {
          var wanted = new BigDecimal(expected.get(bound).toString());
          var found =
              actual.containsKey(bound) ? new BigDecimal(actual.get(bound).toString()) : null;
          if (found == null || !wanted.equals(found))
            throw new Problem(
                "model_numeric_contract_violation",
                "Generator hat Wertebereich oder Dezimalgenauigkeit verändert. Komponente reparieren; bestätigte Fachwerte erhalten.",
                "attribute",
                path,
                "expected",
                expected,
                "actual",
                actual);
        }
      }
    } catch (java.io.IOException ex) {
      throw new Problem(
          "model_numeric_contract_unavailable",
          "Kompilierte Modellstruktur konnte nicht geprüft werden.");
    } finally {
      if (directory != null)
        try {
          Files.deleteIfExists(directory.resolve("candidate.ili"));
          Files.deleteIfExists(directory);
        } catch (java.io.IOException ignored) {
        }
    }
  }
}
