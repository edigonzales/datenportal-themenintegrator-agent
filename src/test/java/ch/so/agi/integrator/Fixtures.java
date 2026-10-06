package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

final class Fixtures {
  static final Path ROOT = Path.of(System.getProperty("integrator.root", ".")).toAbsolutePath();
  static final Path FIXTURES = ROOT.resolve("tests/fixtures");
  final Path temp, repo;
  final Settings settings;
  final Workflow workflow;

  Fixtures(Path temp) throws Exception {
    this(
        temp,
        new FakeProcess(),
        (name, args) -> {
          throw new Problem("fixture_mcp", "MCP fixture unavailable");
        },
        (name, args) -> {
          throw new Problem("fixture_mcp", "MCP fixture unavailable");
        });
  }

  Fixtures(
      Path temp, ProcessRunner process, McpClients.ToolClient data, McpClients.ToolClient model)
      throws Exception {
    this.temp = temp;
    repo = temp.resolve("repo");
    Files.createDirectories(repo.resolve("shared/data"));
    Files.copy(FIXTURES.resolve("offices.xtf"), repo.resolve("shared/data/offices.xtf"));
    Json.write(
        repo.resolve("shared/gretl-datenportal-teams.yaml"),
        "teams:\n  readers:\n    users: [fixture-reader]\n  builders:\n    users: [fixture-builder]\n");
    Path config = temp.resolve("local.toml");
    Json.write(
        config,
        "root = "
            + Json.text(ROOT.toString())
            + "\ntopics_repo = "
            + Json.text(repo.toString())
            + "\nstack_repo = "
            + Json.text(temp.resolve("stack").toString())
            + "\nstate_dir = "
            + Json.text(temp.resolve("runs").toString())
            + "\nvalidator_command = [\"fixture-validator\"]\nmodel_dirs = ["
            + Json.text(FIXTURES.resolve("models").toString())
            + "]\n[environments.local]\nkind=\"local\"\nenabled=true\nrepository_mode=\"working-tree\"\njenkins_url=\"http://localhost:8081/jenkins\"\nportal_url=\"http://localhost:8081\"\nmanifest_url=\"http://localhost:8081/current.json\"\nusername_env=\"INTEGRATOR_TEST_USER\"\ntoken_env=\"INTEGRATOR_TEST_TOKEN\"\n");
    settings = new Settings(config);
    workflow = new Workflow(settings, process, data, model);
  }

  static class FakeProcess extends ProcessRunner {
    final List<List<String>> invocations = new ArrayList<>();

    @Override
    public Result run(List<String> args, Path cwd, int seconds, Path log) {
      invocations.add(args);
      if (log != null)
        Json.write(log, "FIXTURE: external validation successful\nINTEGRATOR_CSV_VALIDATED=true\n");
      return new Result(
          0, "FIXTURE: external validation successful\nINTEGRATOR_CSV_VALIDATED=true\n");
    }
  }

  Map<String, Object> call(String op, Object... pairs) {
    return Json.obj(workflow.call(op, Json.map(pairs)));
  }

  String topic(boolean series, String body) {
    Path csv = temp.resolve("input.csv");
    Json.write(csv, body);
    var args =
        Json.map(
            "organization",
            "agi",
            "identifier",
            series ? "ch.so.bevoelkerung.altersstruktur" : "ch.so.grundwasser.qualitaet",
            "data_path",
            csv.toString(),
            "metadata_path",
            FIXTURES.resolve(series ? "series.xtf" : "dataset.xtf").toString());
    if (series) args.put("issue", "2025");
    return Json.required(Json.obj(workflow.call("start", args)), "id");
  }

  String metadataOnly() {
    return Json.required(
        call(
            "start",
            "organization",
            "agi",
            "identifier",
            "ch.so.grundwasser.qualitaet",
            "metadata_path",
            FIXTURES.resolve("dataset.xtf").toString()),
        "id");
  }

  void approve(String id, String gate, Map<String, Object> report) {
    call(
        "approve",
        "run_id",
        id,
        "gate",
        gate,
        "fingerprint",
        report.get("fingerprint"),
        "human_statement",
        "AUTOMATISIERTE TESTFIXTURE im isolierten Testrepo");
  }

  void approveAll(String id) {
    var analysis = call("analyze", "run_id", id);
    if (!Json.bool(analysis, "skipped", false)) approve(id, "data", analysis);
    var validation = call("validate", "run_id", id);
    org.junit.jupiter.api.Assertions.assertTrue(
        Json.bool(validation, "valid", false), Json.pretty(validation));
    approve(id, "metadata", validation);
  }

  void error(String code, Runnable action) {
    var p = org.junit.jupiter.api.Assertions.assertThrows(Problem.class, action::run);
    org.junit.jupiter.api.Assertions.assertEquals(code, p.code);
  }
}
