package ch.so.agi.integrator;

import java.math.BigDecimal;
import java.util.*;

/** The integrator's scalar authoring contract, independent of human approval provenance. */
final class ModelArguments {
  static Problem invalid(String field, String message) {
    return new Problem(
        "model_arguments_invalid",
        message,
        "field",
        field,
        "next",
        "Argumentdarstellung korrigieren; kein reset_model_repair und keine neue Fachfreigabe erforderlich.");
  }

  static Map<String, Object> object(Object value, String path) {
    if (!(value instanceof Map)) throw invalid(path, "Objekt erforderlich.");
    return new LinkedHashMap<>(Json.obj(value));
  }

  static void keys(Map<String, Object> value, String path, String... allowed) {
    var names = Set.of(allowed);
    for (String key : value.keySet())
      if (!names.contains(key))
        throw invalid(path + "." + key, "Unbekanntes Feld; operation_schema(derive_model) lesen.");
  }

  static BigDecimal decimal(Object value, String path) {
    try {
      if (!(value instanceof Number || value instanceof String))
        throw new IllegalArgumentException();
      return new BigDecimal(value.toString());
    } catch (RuntimeException e) {
      throw invalid(path, "Endliche Dezimalzahl erforderlich.");
    }
  }

  static Map<String, Object> spec(Object value, String path) {
    var spec = object(value, path);
    keys(spec, path, "baseType", "domainFqn");
    if (spec.containsKey("baseType") == spec.containsKey("domainFqn"))
      throw invalid(path, "Genau eines von baseType und domainFqn erforderlich.");
    if (spec.containsKey("domainFqn")) {
      if (!(spec.get("domainFqn") instanceof String s) || s.isBlank())
        throw invalid(path + ".domainFqn", "Qualifizierter Domainname erforderlich.");
      return spec;
    }
    String p = path + ".baseType";
    var base = object(spec.get("baseType"), p);
    String kind = Json.str(base, "kind", "");
    switch (kind) {
      case "NUMERIC", "NUM_RANGE" -> {
        keys(base, p, "kind", "min", "max", "precision", "unitFqn", "refSysFqn", "circular");
        for (String key : List.of("unitFqn", "refSysFqn"))
          if (base.containsKey(key) && !(base.get(key) instanceof String))
            throw invalid(p + "." + key, "Qualifizierter Name erforderlich.");
        if (base.containsKey("circular") && !(base.get("circular") instanceof Boolean))
          throw invalid(p + ".circular", "Boolean erforderlich.");
        if (!base.containsKey("min") || !base.containsKey("max"))
          throw invalid(p, "Instanziierbare Zahlentypen benötigen NUM_RANGE mit min und max.");
        BigDecimal min = decimal(base.get("min"), p + ".min"),
            max = decimal(base.get("max"), p + ".max");
        int scale = Math.max(0, min.scale());
        if (min.compareTo(max) >= 0 || min.scale() != max.scale())
          throw invalid(
              p,
              "min muss kleiner als max sein; beide Grenzen müssen dieselbe Dezimalstellenzahl besitzen.");
        if (base.containsKey("precision")
            && decimal(base.get("precision"), p + ".precision").compareTo(BigDecimal.valueOf(scale))
                != 0)
          throw invalid(
              p + ".precision",
              "precision widerspricht der Dezimalstellenzahl der Grenzen; Dezimalgrenzen als Strings angeben.");
        base.put("kind", "NUM_RANGE");
        base.put("min", min.toPlainString());
        base.put("max", max.toPlainString());
        base.remove("precision");
      }
      case "TEXT", "MTEXT" -> {
        keys(base, p, "kind", "length", "maxLength");
        if (base.containsKey("length") || base.containsKey("maxLength")) {
          BigDecimal length =
              decimal(base.getOrDefault("length", base.get("maxLength")), p + ".length");
          if (base.containsKey("length")
              && base.containsKey("maxLength")
              && length.compareTo(decimal(base.get("maxLength"), p + ".maxLength")) != 0)
            throw invalid(p, "length und maxLength widersprechen sich.");
          try {
            int n = length.intValueExact();
            if (n <= 0) throw new ArithmeticException();
            base = Json.map("kind", kind, "length", n);
          } catch (ArithmeticException e) {
            throw invalid(p + ".length", "Positive ganzzahlige Textlänge erforderlich.");
          }
        }
      }
      case "BOOLEAN" -> keys(base, p, "kind");
      default ->
          throw invalid(
              p + ".kind",
              "Unterstützte Skalartypen: NUM_RANGE, TEXT, MTEXT, BOOLEAN; benannte Typen über domainFqn.");
    }
    return Json.map("baseType", base);
  }

  // Store decimal scale losslessly as strings; the MCP wire contract requires JSON numbers.
  static Map<String, Object> mcpSpec(Map<String, Object> value) {
    var result = new LinkedHashMap<>(value);
    var base = new LinkedHashMap<>(Json.obj(value.get("baseType")));
    if ("NUM_RANGE".equals(base.get("kind"))) {
      base.put("min", decimal(base.get("min"), "min"));
      base.put("max", decimal(base.get("max"), "max"));
      result.put("baseType", base);
    }
    return result;
  }

  static Map<String, Object> confirmation(Object value, String path) {
    var c = object(value, path);
    keys(c, path, "confirmed", "type_spec", "numeric_identifier_confirmed", "note");
    for (String key : List.of("confirmed", "numeric_identifier_confirmed"))
      if (c.containsKey(key) && !(c.get(key) instanceof Boolean))
        throw invalid(path + "." + key, "Boolean erforderlich.");
    if (c.containsKey("note") && !(c.get("note") instanceof String))
      throw invalid(path + ".note", "Text erforderlich.");
    if (c.containsKey("type_spec"))
      c.put("type_spec", spec(c.get("type_spec"), path + ".type_spec"));
    return c;
  }

  static Map<String, Object> confirmations(Object value) {
    var result = Json.map();
    if (value == null) return result;
    object(value, "confirmations")
        .forEach((column, c) -> result.put(column, confirmation(c, "confirmations." + column)));
    return result;
  }

  static Map<String, Object> proposals(Object value) {
    var result = Json.map();
    if (value == null) return result;
    object(value, "range_proposals")
        .forEach(
            (column, v) -> {
              String p = "range_proposals." + column;
              var proposal = object(v, p);
              keys(proposal, p, "type_spec", "reason", "negative_values_possible");
              proposal.put("type_spec", spec(proposal.get("type_spec"), p + ".type_spec"));
              result.put(column, proposal);
            });
    return result;
  }

  static Object semantics(Map<String, Object> c) {
    var result = new LinkedHashMap<>(c);
    result.remove("note");
    if (Boolean.FALSE.equals(result.get("numeric_identifier_confirmed")))
      result.remove("numeric_identifier_confirmed");
    return result;
  }

  /** Repair only representation in existing mappings, atomically, with old/new evidence. */
  static void normalizeStored(Map<String, Object> run) {
    var model = Json.obj(run.get("model"));
    var replacements = new ArrayList<Object>();
    var corrections = new ArrayList<Object>();
    for (Object value : Json.list(model.get("mapping"))) {
      var original = Json.obj(value);
      var row = new LinkedHashMap<>(original);
      String column = Json.required(row, "column");
      var saved = Json.obj(row.get("confirmed"));
      if (!saved.isEmpty()) row.put("confirmed", confirmation(saved, "stored." + column));
      if (row.containsKey("type_spec"))
        row.put("type_spec", spec(row.get("type_spec"), "stored." + column + ".type_spec"));
      if (!Json.digest(original).equals(Json.digest(row)))
        corrections.add(Json.map("column", column, "before", original, "after", row));
      replacements.add(row);
    }
    if (!corrections.isEmpty()) {
      model.put("mapping", replacements);
      Json.list(run.computeIfAbsent("model_argument_corrections", k -> new ArrayList<>()))
          .add(
              Json.map(
                  "at",
                  Json.now(),
                  "reason",
                  "Gleichwertige technische Darstellung; Fachwerte unverändert",
                  "corrections",
                  corrections));
    }
  }

  static Map<String, Object> schema(boolean proposal) {
    var base =
        Json.map(
            "type",
            "object",
            "properties",
            Json.map(
                "kind",
                Json.map(
                    "type", "string", "enum", List.of("NUM_RANGE", "TEXT", "MTEXT", "BOOLEAN")),
                "min",
                Json.map("type", List.of("string", "number")),
                "max",
                Json.map("type", List.of("string", "number")),
                "length",
                Json.map("type", "integer", "minimum", 1),
                "unitFqn",
                Json.map("type", "string"),
                "refSysFqn",
                Json.map("type", "string"),
                "circular",
                Json.map("type", "boolean")),
            "required",
            List.of("kind"),
            "additionalProperties",
            false,
            "description",
            "NUM_RANGE: min/max mit gleicher Dezimalstellenzahl (z.B. Strings -9999.99 und 9999.99); TEXT/MTEXT: length. NUMERIC mit Grenzen und maxLength werden als gleichwertige Altform normalisiert.");
    var type =
        Json.map(
            "type",
            "object",
            "properties",
            Json.map("baseType", base, "domainFqn", Json.map("type", "string")),
            "oneOf",
            List.of(
                Json.map("required", List.of("baseType")),
                Json.map("required", List.of("domainFqn"))),
            "additionalProperties",
            false);
    var fields = Json.map("type_spec", type);
    if (proposal) {
      fields.put("reason", Json.map("type", "string"));
      fields.put("negative_values_possible", Json.map("type", "boolean"));
    } else {
      fields.put("confirmed", Json.map("type", "boolean"));
      fields.put("numeric_identifier_confirmed", Json.map("type", "boolean"));
      fields.put("note", Json.map("type", "string"));
    }
    return Json.map(
        "type",
        "object",
        "additionalProperties",
        Json.map("type", "object", "properties", fields, "additionalProperties", false),
        "examples",
        List.of(
            proposal
                ? Json.map(
                    "Anzahl",
                    Json.map(
                        "type_spec",
                        Json.map(
                            "baseType",
                            Json.map("kind", "NUM_RANGE", "min", "0", "max", "9999999")),
                        "reason",
                        "Nichtnegative Zählwerte mit begründetem Spielraum",
                        "negative_values_possible",
                        false))
                : Json.map(
                    "Name",
                    Json.map(
                        "confirmed",
                        true,
                        "type_spec",
                        Json.map("baseType", Json.map("kind", "TEXT", "length", 100))))));
  }
}
