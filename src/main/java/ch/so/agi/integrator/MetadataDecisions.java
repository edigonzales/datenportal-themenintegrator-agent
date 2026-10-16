package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

/** Evidence for decisions that must never be supplied by an agent's default. */
final class MetadataDecisions {
  static final List<String> FIELDS = List.of("access_level", "publication_status");

  static void source(Map<String, Object> r) {
    r.put("decision_source", Json.read(Json.text(Json.obj(r.get("files")).get("metadata"))));
  }

  static String field(String field) {
    return switch (field) {
      case "accessLevel" -> "access_level";
      case "publicationStatus" -> "publication_status";
      default -> field;
    };
  }

  static Map<String, Object> sourceSheet(Workflow w, Map<String, Object> r) {
    Object source = r.get("decision_source");
    if (source == null) source = Json.obj(r.get("files")).get("accepted_metadata");
    if (source == null) return Json.map();
    var frozen = Json.map("id", r.get("id"), "files", Json.map("metadata", source));
    return Xml.describe(
        w.store.file(frozen, "metadata"),
        Json.required(r, "identifier"),
        w.store.file(r, "data") == null ? null : Json.str(r, "issue", null));
  }

  static boolean supported(Workflow w, Map<String, Object> r, Map<String, Object> e) {
    String field = field(Json.str(e, "field", ""));
    String value = Json.str(e, "value", "");
    if (value.isBlank() || Json.str(e, "source", "").isBlank()) return false;
    switch (Json.str(e, "origin", "")) {
      case "user":
        return !Json.str(e, "human_statement", "").isBlank();
      case "accepted":
        return Objects.equals(sourceSheet(w, r).get(field), value);
      case "xlsx":
        Path path = w.store.file(r, "metadata_source");
        if (path == null) return false;
        try (var in = Files.newInputStream(path);
            var book = WorkbookFactory.create(in)) {
          var sheet = book.getSheet(Json.required(e, "sheet"));
          var ref = new CellReference(Json.required(e, "cell"));
          var row = sheet == null ? null : sheet.getRow(ref.getRow());
          var cell = row == null ? null : row.getCell(ref.getCol());
          return cell != null
              && cell.getCellType() == CellType.STRING
              && value.equals(cell.getStringCellValue().trim());
        } catch (Exception error) {
          return false;
        }
      default:
        return false;
    }
  }

  static List<Object> blockers(Workflow w, Map<String, Object> r, Map<String, Object> sheet) {
    var blockers = new ArrayList<Object>();
    if (!Json.str(r, "workflow", "topic").equals("topic")) return blockers;
    Map<String, Object> source;
    try {
      source = sourceSheet(w, r);
    } catch (Problem e) {
      source = Json.map();
    }
    for (String field : FIELDS) {
      var entries =
          Json.list(r.get("provenance")).stream()
              .map(Json::obj)
              .filter(e -> field.equals(field(Json.str(e, "field", ""))))
              .toList();
      Object value = sheet.get(field);
      boolean valid = value != null && !value.toString().isBlank();
      if (entries.isEmpty())
        valid &= Objects.equals(value, source.get(field)) && source.containsKey(field);
      else
        valid &=
            entries.stream()
                .allMatch(e -> Objects.equals(value, e.get("value")) && supported(w, r, e));
      if (source.get(field) != null
          && !Objects.equals(value, source.get(field))
          && entries.stream().noneMatch(e -> "user".equals(e.get("origin")) && supported(w, r, e)))
        valid = false;
      if (!valid)
        blockers.add(
            Json.map(
                "code",
                "metadata_decision_required",
                "field",
                field,
                "value",
                value,
                "message",
                "Eindeutige belegte Vorgabe fehlt oder widerspricht dem Entwurf. Zwingend beim Benutzer nachfragen.",
                "next_operation",
                "provenance",
                "required_action",
                "ask_user"));
    }
    return blockers;
  }

  static void require(Workflow w, Map<String, Object> r) {
    if (!Json.str(r, "workflow", "topic").equals("topic")) return;
    var blockers = blockers(w, r, w.describe(r));
    if (!blockers.isEmpty())
      throw new Problem(
          "metadata_decision_required",
          "Zugangslevel und Publikationsstatus ausdrücklich klären; kein allgemeines Freigabe-OK verwenden.",
          "decision_blockers",
          blockers);
  }
}
