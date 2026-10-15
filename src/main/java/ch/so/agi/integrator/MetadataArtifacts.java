package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** One exported contract and one repository destination per run. */
final class MetadataArtifacts {
  static final List<String> TYPES =
      List.of("TEXT", "INTEGER", "NUMERIC", "DECIMAL", "BOOLEAN", "DATE", "DATETIME");

  static void identifier(Map<String, Object> r, Object actual) {
    if (!Objects.equals(r.get("identifier"), actual))
      throw new Problem(
          "datasheet_identifier_mismatch",
          "Datenblatt-Identifier korrigieren; keine anderen Dateinamen ausprobieren.",
          "expected_identifier",
          r.get("identifier"),
          "found_identifiers",
          Collections.singletonList(actual),
          "next_operation",
          "metadata(update_metadata) mit dem Vorgangs-Identifier; danach export_xtf");
  }

  static void validate(Path path, Map<String, Object> r) {
    Xml.describe(path, Json.required(r, "identifier"), null);
    for (var node : Xml.all(Xml.parse(path))) {
      for (Object value : Xml.attributes(node)) {
        var a = Json.obj(value);
        String type = Json.str(a, "data_type", "");
        if (!TYPES.contains(type.toUpperCase(Locale.ROOT)))
          throw new Problem(
              "unsupported_type",
              "Datenblatt-Typ vor Export korrigieren; keine Typnamen erraten.",
              "column",
              a.get("name"),
              "actual_type",
              type,
              "supported_types",
              TYPES,
              "next_operation",
              "metadata(upsert_attribute); danach export_xtf");
      }
    }
  }

  static List<String> candidates(Workflow w, Map<String, Object> r) {
    return Json.obj(r.get("changes")).keySet().stream()
        .filter(
            p ->
                p.startsWith(w.topic(r) + "/")
                    && p.toLowerCase(Locale.ROOT).matches(".*\\.(xtf|xml)"))
        .sorted()
        .toList();
  }

  static String target(Workflow w, Map<String, Object> r, Map<String, Object> input) {
    var candidates = candidates(w, r);
    if (candidates.size() > 1)
      throw new Problem(
          "ambiguous_repository_metadata",
          "Mehrere Metadaten-Kandidaten im Vorgang. Vormerkungen gezielt bereinigen; keine weiteren Diagnose-Dateien anlegen.",
          "candidates",
          candidates);
    String existing = Json.str(r, "metadata_target", null);
    if (existing == null)
      existing = Json.str(Json.obj(r.get("repository_datasheet")), "relative_path", null);
    if (existing == null && candidates.size() == 1) existing = candidates.getFirst();
    if (input.containsKey("repository_filename")) {
      String name = Json.required(input, "repository_filename");
      if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*\\.xtf") || name.contains(".."))
        throw new Problem(
            "invalid_repository_filename",
            "Einfachen XTF-Dateinamen ohne Verzeichnisse angeben.",
            "filename",
            name);
      return w.topic(r) + "/" + name;
    }
    return existing != null
        ? existing
        : w.topic(r) + "/meta-" + Json.required(r, "identifier") + ".xtf";
  }

  static void stage(Workflow w, Map<String, Object> r, String target, Path artifact) {
    var old = candidates(w, r);
    // Stage on a copy so a conflict cannot discard the last good candidate.
    var next = Json.read(Json.text(r));
    w.stage(next, target, artifact);
    old.stream()
        .filter(p -> !p.equals(target))
        .forEach(p -> Json.obj(next.get("changes")).remove(p));
    r.put("changes", next.get("changes"));
    r.put("events", next.get("events"));
    r.put("metadata_target", target);
  }

  static Map<String, Object> source(Workflow w, Map<String, Object> r) {
    Path active = w.store.file(r, "metadata");
    boolean current = false;
    try {
      w.exportRequired(r);
      current = active != null;
    } catch (Problem ignored) {
    }
    String next =
        current
            ? "validate; bei beauftragter Modellierung derive_model aus dieser XTF"
            : r.containsKey("pending_metadata")
                ? "metadata(restore)"
                : r.containsKey("draft")
                    ? "metadata(export_xtf)"
                    : "repository_datasheet für Bestandsquelle; falls fehlend metadata(create_datasheet)";
    return Json.map(
        "path",
        active == null ? null : active.toString(),
        "sha256",
        active == null ? null : Json.sha(active),
        "current",
        current,
        "repository_path",
        r.get("metadata_target"),
        "next_operation",
        next,
        "contract",
        "XLSX erstellt das Datenblatt; aktuelle XTF, CSV-Beobachtungen und bestätigte Fachregeln bestimmen das Modell.");
  }
}
