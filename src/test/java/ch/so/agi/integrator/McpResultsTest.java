package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpResultsTest {
  @TempDir Path root;

  Settings settings() {
    Json.write(
        root.resolve("config/local.toml"),
        "root=\"..\"\ntopics_repo=\"../repo\"\n[datasheet]\ntransport=\"stdio\"\n");
    return new Settings(root.resolve("config/local.toml"));
  }

  @Test
  void statusKeepsFailedMetadataVisibleBesideSuccessfulModel() {
    var checks =
        Json.map(
            "metadata",
            Json.map(
                "valid",
                false,
                "errors",
                List.of(
                    Json.map("code", "issue_required", "message", "Ausgabenbezeichnung fehlt.")),
                "diff",
                "x".repeat(600000)),
            "model",
            Json.map("valid", true, "scope", "integration"));
    var source = Json.map("checks", checks, "current_approvals", Json.map("data", false));
    var result = Json.obj(McpResults.present(settings(), "status", source));
    assertEquals(false, Json.obj(Json.obj(result.get("checks")).get("metadata")).get("valid"));
    assertTrue(Json.text(result).contains("issue_required"));
    assertEquals(Json.map("data", false), result.get("current_approvals"));
    assertTrue(Json.pretty(result).length() < McpResults.LIMIT);
  }

  @Test
  void largeErrorKeepsDiagnosisAndCanBeReadWithoutRepeatingOperation() {
    var s = settings();
    var source =
        Json.map(
            "error",
            "approval_required",
            "message",
            "Menschliche Freigabe fehlt.",
            "diagnostic_history",
            List.of("x".repeat(50000)),
            "errors",
            List.of(Json.map("code", "issue_required", "message", "Ausgabenbezeichnung fehlt.")));
    String before = Json.text(source);
    var result = Json.obj(McpResults.present(s, "prepare_pr", source));
    assertTrue(Json.pretty(result).length() < McpResults.LIMIT);
    assertEquals("approval_required", result.get("error"));
    assertEquals(source.get("errors"), result.get("errors"));
    assertEquals(before, Json.text(source));
    String path = result.get("full_result_path").toString();
    var files = new WorkspaceFiles(s);
    var text = new StringBuilder();
    int offset = 0;
    while (true) {
      var page = Json.obj(files.call("read_file", Json.map("path", path, "offset", offset)));
      assertTrue(page.get("content").toString().length() <= 6000);
      text.append(page.get("content"));
      if (page.get("next_offset") == null) break;
      offset = ((Number) page.get("next_offset")).intValue();
    }
    assertEquals(source, Json.read(text.toString()));
  }

  @Test
  void largeReportAndSingleLineArePagedAndPrivateFilesStayDenied() {
    var s = settings();
    var files = new WorkspaceFiles(s);
    Path report = s.runs.resolve("test/model-review.html");
    Json.write(report, "<html>" + "ü".repeat(600000) + "</html>");
    var first = Json.obj(files.read(report.toString()));
    assertEquals(6000, first.get("next_offset"));
    assertEquals(files.canonical(report.toString()).toUri().toString(), first.get("report_url"));
    assertThrows(Problem.class, () -> files.read(report.toString(), -1, 6000));
    assertThrows(Problem.class, () -> files.read(report.toString(), 0, 6001));
    assertThrows(Problem.class, () -> files.read(s.runs.resolve("test/state.json").toString()));
    Path privateFile = root.resolve("secret.json");
    Json.write(privateFile, "{}");
    Path alias = s.runs.resolve("responses/00000000-0000-0000-0000-000000000000.json");
    try {
      Files.createDirectories(alias.getParent());
      Files.createSymbolicLink(alias, privateFile);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
    assertThrows(Problem.class, () -> files.read(alias.toString()));
    assertThrows(Problem.class, () -> files.openReport(privateFile.toString()));
  }

  @Test
  void hugeStatusPreservesDeliveryAndDecisionDiagnostics() {
    var item =
        Json.map(
            "phase",
            "delivery_submitting",
            "action",
            "delivery",
            "generation",
            2,
            "environment",
            "local",
            "reference",
            Json.map("job", "upload", "queue", "123", "build_url", "http://fixture/14/"),
            "next_operation",
            "reconcile",
            "verification",
            Json.map("sheet", "x".repeat(90000)));
    var source =
        Json.map(
            "external_runs",
            Json.map("deliveries", Json.map("local", item)),
            "decision_blockers",
            List.of(Json.map("field", "access_level", "required_action", "ask_user")));
    for (int i = 0; i < 100; i++) source.put("noise" + i, "x".repeat(900));
    var result = Json.obj(McpResults.present(settings(), "status", source));
    var diagnostics = Json.obj(result.get("diagnostics"));
    var delivery =
        Json.obj(
            Json.obj(Json.obj(diagnostics.get("external_runs")).get("deliveries")).get("local"));
    assertEquals("reconcile", delivery.get("next_operation"));
    assertEquals(item.get("reference"), delivery.get("reference"));
    assertEquals(2, delivery.get("generation"));
    assertEquals(source.get("decision_blockers"), diagnostics.get("decision_blockers"));
    assertTrue(Json.pretty(result).length() < McpResults.LIMIT);
  }

  @Test
  void schemasAreNeverShortened() {
    var schema = Json.map("description", "x".repeat(15000));
    assertSame(schema, McpResults.present(settings(), "operation_schema", schema));
  }
}
