package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

public final class Reports {
  public static String escape(Object value) {
    return value == null
        ? ""
        : value
            .toString()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  public enum Kind {
    CSV("CSV prüfen"),
    TRANSFORM("CSV-Transformation prüfen"),
    METADATA("Datenblatt und Modell prüfen"),
    MODEL_CANDIDATE("Modellableitung prüfen"),
    MODEL("INTERLIS-Modell und CSV prüfen"),
    ORGANIZATION("Organisation und Berechtigungen prüfen");
    final String title;

    Kind(String title) {
      this.title = title;
    }
  }

  public static String html(Object value) {
    return ReviewHtml.value(value, "", 0);
  }

  public static String render(Path root, Path output, Kind kind, String identifier, Object data) {
    String template = Json.contents(AgentResources.resolve(root, "templates/review-java.html"));
    String body = ReviewHtml.render(kind, identifier, data);
    // Substitute only template tokens, never text introduced by a previous replacement.
    var matcher = java.util.regex.Pattern.compile("\\{\\{(TITLE|CONTENT)\\}\\}").matcher(template);
    String result =
        matcher.replaceAll(
            m ->
                java.util.regex.Matcher.quoteReplacement(
                    m.group(1).equals("TITLE") ? escape(kind.title) : body));
    Json.write(output, result);
    return output.toString();
  }

  public static Map<String, Object> workbook(Path p, String sheet, int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 500)
      throw new Problem("invalid_page", "offset >=0 und limit 1..500 erforderlich.");
    try (var in = Files.newInputStream(p);
        var book = WorkbookFactory.create(in)) {
      if (sheet == null) {
        var sheets = new ArrayList<>();
        for (Sheet s : book)
          sheets.add(
              Json.map(
                  "name",
                  s.getSheetName(),
                  "rows",
                  s.getLastRowNum() + 1,
                  "columns",
                  s.getRow(0) == null ? 0 : s.getRow(0).getLastCellNum()));
        return Json.map("sheets", sheets);
      }
      Sheet s = book.getSheet(sheet);
      if (s == null) throw new Problem("unknown_sheet", "Tabellenblatt fehlt.");
      var rows = new ArrayList<Object>();
      for (int i = offset; i <= s.getLastRowNum() && i < offset + limit; i++) {
        var row = s.getRow(i);
        if (row == null) continue;
        var cells = new ArrayList<Object>();
        for (Cell c : row) {
          boolean formula = c.getCellType() == CellType.FORMULA;
          var type = formula ? c.getCachedFormulaResultType() : c.getCellType();
          Object v =
              switch (type) {
                case STRING -> c.getStringCellValue();
                case NUMERIC ->
                    DateUtil.isCellDateFormatted(c)
                        ? c.getLocalDateTimeCellValue().toString()
                        : c.getNumericCellValue();
                case BOOLEAN -> c.getBooleanCellValue();
                case ERROR -> FormulaError.forInt(c.getErrorCellValue()).getString();
                default -> null;
              };
          if (formula
              && c instanceof org.apache.poi.xssf.usermodel.XSSFCell xc
              && (!xc.getCTCell().isSetV() || xc.getCTCell().getV().isEmpty())) v = null;
          if (v != null || formula)
            cells.add(
                Json.map(
                    "cell",
                    new CellReference(c.getRowIndex(), c.getColumnIndex()).formatAsString(),
                    "value",
                    v,
                    "formula",
                    formula ? "=" + c.getCellFormula() : null,
                    "missing_cached_result",
                    formula && v == null));
        }
        if (!cells.isEmpty()) rows.add(cells);
      }
      return Json.map(
          "file",
          p.getFileName().toString(),
          "sheet",
          sheet,
          "rows",
          rows,
          "next_offset",
          offset + limit <= s.getLastRowNum() ? offset + limit : null);
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("invalid_xlsx", "XLSX kann nicht gelesen werden.");
    }
  }
}
