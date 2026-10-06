package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class CsvTest {
  @TempDir Path temp;
  final Map<String, Object> rules = Json.read(Fixtures.ROOT.resolve("config/rules.json"));

  static Stream<Object[]> invalid() {
    return Stream.of(
        new Object[] {"a;A\n1;2\n", "duplicate_header"},
        new Object[] {"2025;a\n1;2\n", "invalid_header"},
        new Object[] {"a\n\n", "empty_row"},
        new Object[] {"a;b\n1;2;3\n", "row_width"},
        new Object[] {"a\n\"unclosed\n", "csv_format"},
        new Object[] {"a,b\n1,2\n", "invalid_header"},
        new Object[] {"a\n", "no_observations"},
        new Object[] {"a\n\0\n", "nul"});
  }

  @ParameterizedTest
  @MethodSource("invalid")
  void rejects(String body, String code) {
    Path p = temp.resolve("input.csv");
    Json.write(p, body);
    var result = Csv.inspect(p, rules, null);
    assertFalse(Json.bool(result, "valid", true));
    assertTrue(
        Json.list(result.get("errors")).stream()
            .map(Json::obj)
            .anyMatch(e -> Objects.equals(e.get("code"), code)),
        Json.pretty(result));
  }

  @ParameterizedTest
  @CsvSource({
    "INTEGER,2.5,false",
    "INTEGER,9223372036854775808,false",
    "INTEGER,-9223372036854775808,true",
    "DECIMAL,NaN,false",
    "DECIMAL,1e3,true",
    "DECIMAL,1'000,false",
    "BOOLEAN,true,true",
    "BOOLEAN,yes,false",
    "DATE,2025-02-30,false",
    "DATE,2025-02-28,true",
    "DATETIME,2025-01-01T12:00:00+01:00,true",
    "DATETIME,2025-01-01T12:00:00Z,false",
    "TEXT,001,true"
  })
  void types(String kind, String value, boolean valid) {
    assertEquals(valid, Csv.accepts(value, kind));
  }

  @Test
  void quotedZeroesAndMissing() {
    Path p = temp.resolve("input.csv");
    Json.write(
        p,
        "\uFEFFkennung;text;wert\n001;\"Französisch; Deutsch\";2.5\n002;\"Messstelle \"\"Nord\"\"\";\n");
    var r = Csv.inspect(p, rules, null);
    assertTrue(Json.bool(r, "valid", false));
    var cols = Json.list(r.get("columns"));
    assertEquals("TEXT", Json.obj(cols.get(0)).get("suggested_type"));
    assertEquals(
        List.of("Französisch; Deutsch", "Messstelle \"Nord\""),
        Json.obj(cols.get(1)).get("examples"));
    assertEquals(1L, Json.obj(cols.get(2)).get("missing"));
  }

  @Test
  void fullScanAndErrorCap() {
    Path p = temp.resolve("input.csv");
    Json.write(p, "a;b\n" + "1;2\n".repeat(10000) + "x\n".repeat(110));
    var r = Csv.inspect(p, rules, null);
    assertEquals(10110L, r.get("rows"));
    assertEquals(110, r.get("error_count"));
    assertEquals(100, Json.list(r.get("errors")).size());
  }

  @Test
  void malformedUtf8() throws Exception {
    Path p = temp.resolve("input.csv");
    Files.write(p, new byte[] {'a', '\n', (byte) 0xff});
    assertFalse(Json.bool(Csv.inspect(p, rules, null), "valid", true));
  }

  @Test
  void metadataContract() {
    Path p = temp.resolve("input.csv");
    Json.write(p, "a;b\n;bad\n");
    var attrs =
        List.of(
            (Object) Json.map("name", "a", "data_type", "TEXT", "mandatory", true),
            Json.map("name", "b", "data_type", "INTEGER", "mandatory", false));
    var result = Csv.inspect(p, rules, attrs);
    assertEquals(2, result.get("error_count"));
    Json.write(p, "b;a\n1;value\n");
    assertTrue(
        Json.list(Csv.inspect(p, rules, attrs).get("errors")).stream()
            .map(Json::obj)
            .anyMatch(e -> e.get("code").equals("attribute_order")));
  }
}
