package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticsTest {
  @TempDir Path temp;

  @Test
  void immutableEvidenceReadableMessagesAndVerifiedLocalLinks() throws Exception {
    var f = new Fixtures(temp);
    String id =
        Json.required(f.call("start", "workflow", "organization", "organization", "fixture"), "id");
    f.workflow.store.edit(
        id,
        run -> {
          var source =
              Json.map(
                  "compilerDiagnostics",
                  List.of(
                      Json.map(
                          "severity",
                          "ERROR",
                          "line",
                          4,
                          "message",
                          "Unknown type",
                          "sourceExcerpt",
                          "line one\nline two")));
          String before = Json.text(source);
          var a = Diagnostics.record(f.workflow, run, "ili2c", "FAILED", source, true);
          var b = Diagnostics.record(f.workflow, run, "ili2c", "PASSED", Json.map(), false);
          assertNotEquals(a.get("check_id"), b.get("check_id"));
          assertEquals(before, Json.text(source));
          var artifact = Json.obj(Json.list(a.get("artifacts")).getFirst());
          Path path = Path.of(Json.required(artifact, "path"));
          assertTrue(Json.contents(path).contains("line one\nline two"));
          assertTrue(
              Diagnostics.html(f.workflow.directory(run), List.of(a)).contains("href=\"file:"));
          Json.write(path, "tampered");
          assertFalse(
              Diagnostics.html(f.workflow.directory(run), List.of(a))
                  .contains(path.toUri().toASCIIString()));
          Path outside = temp.resolve("outside.log");
          Json.write(outside, "secret");
          var foreign =
              Json.map("artifacts", List.of(Diagnostics.artifact("ili2c", "FAILED", outside)));
          assertFalse(
              Diagnostics.html(f.workflow.directory(run), List.of(foreign)).contains("href="));
          return null;
        });
  }
}
