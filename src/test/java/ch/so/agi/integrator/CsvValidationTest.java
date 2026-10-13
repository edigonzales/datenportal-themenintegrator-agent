package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvValidationTest {
  @TempDir Path temp;

  @Test
  void validatesRealCsvWithoutGretlAndRejectsInvalidValue() throws Exception {
    var fixture = new Fixtures(temp);
    Path model = temp.resolve("CsvModel.ili"), csv = temp.resolve("data.csv");
    Json.write(
        model,
        "INTERLIS 2.3;\nMODEL CsvModel (en) AT \"https://example.org\" VERSION \"2026-10-13\" =\nTOPIC Data =\nCLASS Row =\nJahr : MANDATORY 1900 .. 2100;\nEND Row;\nEND Data;\nEND CsvModel.\n");
    var validator = new CsvValidation(fixture.settings, new ProcessRunner());
    Json.write(csv, "Jahr\n2025\n");
    var good = validator.validate(csv, model, "CsvModel", temp.resolve("positive"));
    assertEquals(true, good.get("valid"), Json.contents(Path.of(Json.required(good, "log"))));
    for (String value : new String[] {"not-a-number", "1800", ""}) {
      Json.write(csv, "Jahr\n\"" + value + "\"\n");
      var bad = validator.validate(csv, model, "CsvModel", temp.resolve("negative"));
      assertEquals(false, bad.get("valid"), value);
      assertEquals(true, bad.get("executed"));
    }
  }
}
