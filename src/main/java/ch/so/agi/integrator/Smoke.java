package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Explicitly synthetic technical fixture; never applies changes or publishes. */
final class Smoke {
  final Settings source;

  Smoke(Settings source) {
    this.source = source;
  }

  Map<String, Object> run() {
    Path dir =
        source.root.resolve(".datenportal-integrator/smoke").resolve(UUID.randomUUID().toString());
    Path repo = dir.resolve("repository");
    Json.write(
        dir.resolve("SYNTHETIC_TEST_FIXTURE"),
        "Technical init smoke only; approvals cannot authorize business changes.\n");
    Workspace.copy(source.topics, repo);
    Settings isolated = new Settings(source, repo, dir.resolve("runs"));
    Path csv = dir.resolve("valid.csv"),
        invalid = dir.resolve("invalid.csv"),
        meta = dir.resolve("metadata.xtf");
    Json.write(csv, "Jahr\n2025\n");
    Json.write(invalid, "Jahr\nnot-a-number\n");
    Json.write(
        meta,
        Json.contents(source.root.resolve("tests/fixtures/dataset.xtf"))
            .replace(
                "<ns2:name>Jahr</ns2:name>",
                "<ns2:name>Jahr</ns2:name><ns2:description>Beobachtungsjahr der synthetischen Testfixture</ns2:description>"));
    try (var w =
        new Workflow(
            isolated,
            new ProcessRunner(),
            McpClients.datasheet(source),
            McpClients.interlis(source))) {
      w.gretl = new GretlRuntime(source, w.process);
      String id =
          Json.required(
              Json.obj(
                  w.call(
                      "start",
                      Json.map(
                          "workflow",
                          "model",
                          "organization",
                          "agi",
                          "identifier",
                          "ch.so.grundwasser.qualitaet",
                          "data_path",
                          csv.toString(),
                          "metadata_path",
                          meta.toString()))),
              "id");
      var analysis = Json.obj(w.call("analyze", Json.map("run_id", id)));
      w.call(
          "approve",
          Json.map(
              "run_id",
              id,
              "gate",
              "data",
              "fingerprint",
              analysis.get("fingerprint"),
              "human_statement",
              "AUTOMATISIERTE SYNTHETISCHE INIT-TESTFIXTURE im isolierten Repository; keine fachliche Freigabe"));
      var identity =
          Json.map(
              "name",
              "SO_Integrator_Smoke_20261009",
              "uri",
              "https://example.org/models",
              "version",
              "2026-10-09",
              "technical_contact",
              "fixture@example.org",
              "title",
              "Synthetischer Funktionstest",
              "short_description",
              "Isolierte technische Init-Testfixture");
      var derived = Json.obj(w.call("derive_model", Json.map("run_id", id, "identity", identity)));
      if (Json.bool(derived, "candidate", true)) fail("Modellableitung unvollständig", derived);
      var positive = Json.obj(w.call("validate_model", Json.map("run_id", id)));
      if (!Json.bool(positive, "valid", false)) fail("Positivprüfung fehlgeschlagen", positive);
      Path snapshot = Path.of(Json.required(Json.obj(positive.get("gretl")), "workspace"));
      var negative =
          w.store.edit(
              id,
              state ->
                  new Workspace(w)
                      .gradle(
                          state,
                          snapshot,
                          "validateThemenCsv",
                          List.of("-Pdataset=ch.so.grundwasser.qualitaet", "-PdataFile=" + invalid),
                          "smoke-negative"));
      if (!validationRejected(negative))
        fail("Negativprüfung hat keine CSV-Validierungsverletzung nachgewiesen", negative);
      var report =
          Json.map(
              "valid",
              true,
              "synthetic_fixture",
              true,
              "directory",
              dir.toString(),
              "run_id",
              id,
              "positive",
              positive,
              "negative",
              negative,
              "inputs",
              Json.map(
                  "csv", Json.sha(csv), "xtf", Json.sha(meta), "invalid_csv", Json.sha(invalid)));
      Json.atomic(dir.resolve("report.json"), report);
      // Keep source artifacts, model, validation logs and state; discard only private working
      // copies.
      remove(repo);
      remove(w.store.directory(id).resolve("workspaces"));
      return Json.map(
          "valid",
          true,
          "synthetic_fixture",
          true,
          "report",
          dir.resolve("report.json").toString(),
          "report_sha256",
          Json.sha(dir.resolve("report.json")));
    }
  }

  static boolean validationRejected(Map<String, Object> report) {
    if (Json.bool(report, "valid", true) || !report.containsKey("log")) return false;
    String log = Json.contents(Path.of(Json.required(report, "log")));
    return log.contains("validateThemenCsv FAILED")
        && log.contains("not-a-number")
        && log.contains("is not a number in attribute Jahr");
  }

  static void fail(String message, Map<String, Object> evidence) {
    throw new Problem("smoke_failed", message, "evidence", evidence);
  }

  static void remove(Path path) {
    if (!Files.exists(path)) return;
    try (var paths = Files.walk(path)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
    } catch (java.io.IOException e) {
      throw new Problem(
          "smoke_cleanup_failed",
          "Private Arbeitskopie konnte nicht bereinigt werden.",
          "path",
          path.toString());
    }
  }
}
