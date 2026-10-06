package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ReportsAndConvertersTest {
  @TempDir Path temp;

  @Test
  void cachedFormulaCoordinateAndPaging() throws Exception {
    Path p = temp.resolve("source.xlsx");
    try (var book = new XSSFWorkbook()) {
      var sheet = book.createSheet("Metadata");
      sheet.createRow(0).createCell(0).setCellValue("Title");
      sheet.getRow(0).createCell(1).setCellValue("Quoted <script>");
      sheet.createRow(1).createCell(1).setCellFormula("1+2");
      book.getCreationHelper()
          .createFormulaEvaluator()
          .evaluateFormulaCell(sheet.getRow(1).getCell(1));
      sheet.createRow(2).createCell(1).setCellFormula("2+2");
      try (var out = Files.newOutputStream(p)) {
        book.write(out);
      }
    }
    assertEquals(
        "Metadata",
        Json.obj(Json.list(Reports.workbook(p, null, 0, 10).get("sheets")).getFirst()).get("name"));
    var page = Reports.workbook(p, "Metadata", 1, 1);
    var cell = Json.obj(Json.list(Json.list(page.get("rows")).getFirst()).getFirst());
    assertEquals("B2", cell.get("cell"));
    assertEquals(3.0, cell.get("value"));
    assertEquals("=1+2", cell.get("formula"));
    assertEquals(2, page.get("next_offset"));
  }

  @Test
  void javaConverterRunsJUnitAndPreservesInput() throws Exception {
    var f = new Fixtures(temp, new ProcessRunner(), (n, a) -> Json.map(), (n, a) -> Json.map());
    String id = f.topic(false, "Jahr\n2025\n");
    Path topic = Fixtures.ROOT.resolve("topics/agi/ch.so.grundwasser.qualitaet");
    // Use an isolated integrator root so test recipes never touch versioned topics.
    Path root = temp.resolve("integrator");
    Workspace.copy(Fixtures.ROOT.resolve("config"), root.resolve("config"));
    Workspace.copy(Fixtures.ROOT.resolve("templates"), root.resolve("templates"));
    Workspace.copy(Fixtures.ROOT.resolve("validation"), root.resolve("validation"));
    var text =
        Json.contents(temp.resolve("local.toml"))
            .replace(Json.text(Fixtures.ROOT.toString()), Json.text(root.toString()));
    Json.write(temp.resolve("local.toml"), text);
    var w =
        new Workflow(
            new Settings(temp.resolve("local.toml")),
            new ProcessRunner(),
            (n, a) -> Json.map(),
            (n, a) -> Json.map());
    topic = root.resolve("topics/agi/ch.so.grundwasser.qualitaet");
    Path tests = topic.resolve("tests");
    Json.write(
        topic.resolve("Converter.java"),
        "import ch.so.agi.integrator.CsvConverter; import java.nio.file.*; public class Converter implements CsvConverter { public void convert(Path input, Path output) throws Exception { Files.writeString(output, Files.readString(input).replace(\"2025\",\"2026\")); } }");
    Json.write(
        tests.resolve("ConverterTest.java"),
        "import org.junit.jupiter.api.Test; import org.junit.jupiter.api.io.TempDir; import java.nio.file.*; import static org.junit.jupiter.api.Assertions.*; public class ConverterTest { @TempDir Path dir; @Test void preserves() throws Exception { Path input=dir.resolve(\"in.csv\"),output=dir.resolve(\"out.csv\");Files.writeString(input,\"Jahr\\n2025\\n\");new Converter().convert(input,output);assertTrue(Files.readString(output).contains(\"2026\"));assertTrue(Files.readString(input).contains(\"2025\")); } }");
    Json.atomic(
        topic.resolve("converter.json"),
        Json.map("class_name", "Converter", "sources", List.of("Converter.java")));
    Map<String, Object> args =
        Json.map(
            "run_id",
            id,
            "converter",
            "agi/ch.so.grundwasser.qualitaet/converter.json",
            "tests",
            "agi/ch.so.grundwasser.qualitaet/tests",
            "policy",
            "supplier",
            "instructions",
            "Lieferantenumstellung: Jahr als INTEGER.");
    var result = Json.obj(w.call("transform", args));
    assertTrue(Files.isRegularFile(Path.of(Json.required(result, "tests_log"))));
    w.store.edit(
        id,
        r -> {
          assertTrue(Json.contents(w.requiredFile(r, "data")).contains("2026"));
          assertTrue(Json.contents(w.requiredFile(r, "original_data")).contains("2025"));
          return null;
        });
    Json.write(
        tests.resolve("ConverterTest.java"),
        "import org.junit.jupiter.api.Test; public class ConverterTest { @Test void fails() { throw new AssertionError(\"intentional test failure\"); } }");
    var p = assertThrows(Problem.class, () -> w.call("transform", args));
    assertEquals("converter_tests_failed", p.code);
  }
}
