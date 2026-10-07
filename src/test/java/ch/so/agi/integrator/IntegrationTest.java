package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class IntegrationTest {
  final List<McpClients.ToolClient> clients = new ArrayList<>();

  McpClients.ToolClient own(McpClients.ToolClient client) {
    clients.add(client);
    return client;
  }

  @AfterEach
  void closeClients() {
    clients.forEach(McpClients.ToolClient::close);
  }

  @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS)
  Path temp;

  String java() {
    return Path.of(System.getProperty("java.home"), "bin/java").toString();
  }

  @Test
  void actualIntegratorStdioAndApprovalStop() throws Exception {
    var f = new Fixtures(temp);
    var params =
        ServerParameters.builder(java())
            .args(
                "-jar",
                Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar").toString(),
                "--config",
                temp.resolve("local.toml").toString(),
                "serve")
            .build();
    var transport =
        new StdioClientTransport(
            params, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    transport.setStdErrorHandler(line -> {});
    try (var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
      client.initialize();
      assertEquals(28, client.listTools().tools().size());
      var result =
          client.callTool(
              new McpSchema.CallToolRequest(
                  "start", Json.map("workflow", "organization", "organization", "fixture")));
      assertFalse(Boolean.TRUE.equals(result.isError()));
      String id = Json.required(Json.obj(result.structuredContent()), "id");
      var blocked =
          client.callTool(
              new McpSchema.CallToolRequest("apply_local_changes", Json.map("run_id", id)));
      assertTrue(Boolean.TRUE.equals(blocked.isError()));
      assertEquals("approval_required", Json.obj(blocked.structuredContent()).get("error"));
      var schema =
          client.callTool(new McpSchema.CallToolRequest("organization_schema", Json.map()));
      assertFalse(Boolean.TRUE.equals(schema.isError()));
      assertEquals(
          6,
          Json.obj(Json.obj(Json.obj(schema.structuredContent()).get("office_rules")).get("fields"))
              .size());
    }
  }

  @Test
  void realValidatorDatasetSeriesAndUnknownOffice() throws Exception {
    Settings s = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    var v = new Validator(s, new ProcessRunner());
    for (String kind : List.of("dataset", "series")) {
      var positive =
          v.validate(
              Fixtures.FIXTURES.resolve(kind + ".xtf"),
              Fixtures.FIXTURES.resolve("offices.xtf"),
              temp.resolve(kind));
      assertTrue(Json.bool(positive, "valid", false), Json.pretty(positive));
      Path bad = temp.resolve(kind + "-unknown.xtf");
      Json.write(
          bad,
          Json.contents(Fixtures.FIXTURES.resolve(kind + ".xtf"))
              .replace(kind.equals("dataset") ? "ch.so.afu" : "ch.so.afin", "unknown.office"));
      assertFalse(
          Json.bool(
              v.validate(
                  bad, Fixtures.FIXTURES.resolve("offices.xtf"), temp.resolve(kind + "-bad")),
              "valid",
              true));
    }
  }

  @Test
  void bothRealMcpAdaptersAndGretlEarlyValidation() throws Exception {
    Settings s = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    var data = own(McpClients.datasheet(s));
    var schema = data.call("describe_schema", Json.map());
    assertFalse(schema.isEmpty());
    var model = own(McpClients.interlis(s));
    var f = new Fixtures(temp, new ProcessRunner(), data, model);
    Workspace.copy(s.topics, f.repo);
    f.settings.values.put("validator_command", s.strings("validator_command", List.of()));
    // Test repository and test approvals are isolated; no upload or source-repository writes.
    Path meta = temp.resolve("metadata.xtf");
    Json.write(
        meta,
        Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"))
            .replace(
                "<ns2:name>Jahr</ns2:name>",
                "<ns2:name>Jahr</ns2:name><ns2:description>Beobachtungsjahr des Testwerts</ns2:description>"));
    Path csv = temp.resolve("input.csv");
    Json.write(csv, "Jahr\n2025\n");
    String id =
        Json.required(
            f.call(
                "start",
                "workflow",
                "model",
                "organization",
                "agi",
                "identifier",
                "ch.so.grundwasser.qualitaet",
                "data_path",
                csv.toString(),
                "metadata_path",
                meta.toString()),
            "id");
    f.approve(id, "data", f.call("analyze", "run_id", id));
    var identity =
        Json.map(
            "name",
            "SO_Fixture_Csv_20261006",
            "uri",
            "https://example.org/models",
            "version",
            "2026-10-06",
            "technical_contact",
            "fixture@example.org",
            "title",
            "Testmodell",
            "short_description",
            "Isolierte Java-Integrationstestfixture");
    identity.put("semantics_confirmed", true);
    identity.put(
        "constraints",
        List.of(
            Json.map(
                "kind",
                "MANDATORY",
                "name",
                "Jahr_ab_1900",
                "iliDoc",
                "Ausdrücklich bestätigte Constraint-Testfixture; Jahr ab 1900.",
                "condition",
                Json.map(
                    "kind",
                    "COMPARE",
                    "operator",
                    ">=",
                    "children",
                    List.of(
                        Json.map("kind", "ATTRIBUTE", "name", "Jahr"),
                        Json.map("kind", "NUMERIC", "value", 1900))))));
    var derived = f.call("derive_model", "run_id", id, "identity", identity);
    assertFalse(
        Json.list(Json.obj(Json.obj(derived.get("model")).get("evidence")).get("constraintProofs"))
            .isEmpty());
    assertFalse(Json.bool(derived, "candidate", true), Json.pretty(derived));
    var report = f.call("validate_model", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    Path task =
        f.workflow.store.edit(
            id, r -> f.workflow.repoFile(r, f.workflow.topic(r) + "/dataset.gradle"));
    assertTrue(Json.contents(task).contains("preparePublicationWorkspace"));
    Path snapshot = Path.of(Json.required(Json.obj(report.get("gretl")), "workspace"));
    Path invalid = temp.resolve("invalid.csv");
    Json.write(invalid, "Jahr\nnot-a-number\n");
    var r =
        f.workflow.store.edit(
            id,
            state ->
                new Workspace(f.workflow)
                    .gradle(
                        state,
                        snapshot,
                        "preparePublicationWorkspace",
                        List.of("-Pdataset=ch.so.grundwasser.qualitaet", "-PdataFile=" + invalid),
                        "invalid-before-publication"));
    assertFalse(Json.bool(r, "valid", true));
    String log = Json.contents(Path.of(Json.required(r, "log")));
    assertTrue(log.contains("validateThemenCsv FAILED"), log);
    assertFalse(log.contains("> Task :preparePublicationWorkspace"), log);

    // Execute the identical staged task inside the existing Jenkins runtime, without
    // seed/upload/publication.
    var process = new ProcessRunner();
    String container =
        process.checked(
            List.of(
                "docker",
                "ps",
                "-q",
                "--filter",
                "label=com.docker.compose.project.working_dir=" + s.stack,
                "--filter",
                "label=com.docker.compose.service=jenkins"),
            s.stack,
            30);
    assertEquals(1, container.lines().count());
    assertTrue(Json.bool(new GretlRuntime(s, process).matchJenkins(), "valid", false));
    assertEquals(
        GretlRuntime.recorded(s).get("sha256"),
        Json.obj(Json.obj(report.get("gretl")).get("runtime")).get("sha256"));
    String work = "/var/jenkins_home/datenportal-integrator-tests/" + id;
    process.checked(List.of("docker", "exec", container, "mkdir", "-p", work), s.stack, 30);
    process.checked(
        List.of("docker", "cp", snapshot + "/.", container + ":" + work + "/"), s.stack, 60);
    process.checked(
        List.of("docker", "cp", csv.toString(), container + ":" + work + "/input.csv"),
        s.stack,
        30);
    process.checked(
        List.of("docker", "cp", invalid.toString(), container + ":" + work + "/invalid.csv"),
        s.stack,
        30);
    process.checked(
        List.of(
            "docker", "exec", "--user", "root", container, "chown", "-R", "jenkins:jenkins", work),
        s.stack,
        30);
    var prefix =
        List.of(
            "docker",
            "exec",
            "-w",
            work + "/agi",
            container,
            "bash",
            work + "/shared/bin/gradlew-java17.sh",
            "--no-daemon",
            "--console=plain",
            "-I",
            work + "/shared/gradle/init.gradle");
    var positiveArgs = new ArrayList<>(prefix);
    positiveArgs.addAll(
        List.of(
            "validateThemenCsv",
            "-Pdataset=ch.so.grundwasser.qualitaet",
            "-PdataFile=" + work + "/input.csv"));
    var positive =
        process.run(positiveArgs, s.stack, 180, temp.resolve("jenkins-csv-positive.log"));
    assertEquals(0, positive.exitCode(), positive.output());
    assertTrue(positive.output().contains("INTEGRATOR_CSV_VALIDATED=true"));
    var negativeArgs = new ArrayList<>(prefix);
    negativeArgs.addAll(
        List.of(
            "preparePublicationWorkspace",
            "-Pdataset=ch.so.grundwasser.qualitaet",
            "-PdataFile=" + work + "/invalid.csv"));
    var negative =
        process.run(negativeArgs, s.stack, 180, temp.resolve("jenkins-csv-negative.log"));
    assertNotEquals(0, negative.exitCode());
    assertTrue(negative.output().contains("validateThemenCsv FAILED"), negative.output());
    assertFalse(
        negative.output().contains("> Task :preparePublicationWorkspace"), negative.output());
  }

  @Test
  void realEmptyOrganizationGradleAndOfficeValidation() throws Exception {
    Settings s = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    var f = new Fixtures(temp, new ProcessRunner(), (n, a) -> Json.map(), (n, a) -> Json.map());
    Workspace.copy(s.topics, f.repo);
    f.settings.values.put("validator_command", s.strings("validator_command", List.of()));
    String id =
        Json.required(
            f.call("start", "workflow", "organization", "organization", "javafixtureorg"), "id");
    f.call(
        "prepare_organization",
        "run_id",
        id,
        "values",
        Json.map(
            "title",
            "Isolierte Organisations-Testfixture",
            "read_teams",
            List.of("datenportal-read"),
            "build_teams",
            List.of("agi-build"),
            "office_identifier",
            "ch.so.agi"));
    var report = f.call("validate_organization", "run_id", id);
    assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
    f.approve(id, "organization", report);
    var result = f.call("apply_local_changes", "run_id", id);
    assertEquals("repository_created", Json.obj(result.get("completion")).get("status"));
    assertFalse(Json.bool(Json.obj(result.get("completion")), "jenkins_job", true));
    assertFalse(
        Json.contents(f.repo.resolve("javafixtureorg/build.gradle")).contains("defaultDataset"));
  }

  @Test
  void realTypedCsvModel() throws Exception {
    Settings s = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    var f =
        new Fixtures(
            temp, new ProcessRunner(), own(McpClients.datasheet(s)), own(McpClients.interlis(s)));
    Workspace.copy(s.topics, f.repo);
    f.settings.values.put("validator_command", s.strings("validator_command", List.of()));
    var doc = Xml.parse(Fixtures.FIXTURES.resolve("dataset.xtf"));
    var dataset = Xml.datasets(doc).getFirst();
    Xml.children(dataset).stream()
        .filter(e -> Xml.local(e).equals("attributes"))
        .toList()
        .forEach(dataset::removeChild);
    String[][] fields = {
      {"Code", "TEXT"},
      {"Jahr", "INTEGER"},
      {"Messwert", "DECIMAL"},
      {"Aktiv", "BOOLEAN"},
      {"Datum", "DATE"},
      {"Zeitpunkt", "DATETIME"}
    };
    for (String[] field : fields) {
      var wrapper = doc.createElementNS(Xml.SHEET, "attributes");
      var attribute = doc.createElementNS(Xml.BASE, "DatasetAttribute");
      for (var entry :
          Json.map(
                  "name",
                  field[0],
                  "dataType",
                  field[1],
                  "description",
                  "Bestätigte Testbeschreibung " + field[0],
                  "mandatory",
                  "true")
              .entrySet()) {
        var value = doc.createElementNS(Xml.BASE, entry.getKey());
        value.setTextContent(entry.getValue().toString());
        attribute.appendChild(value);
      }
      wrapper.appendChild(attribute);
      dataset.appendChild(wrapper);
    }
    Path meta = temp.resolve("typed.xtf"), csv = temp.resolve("typed.csv");
    Json.write(meta, Xml.serialize(doc));
    Json.write(
        csv,
        "Code;Jahr;Messwert;Aktiv;Datum;Zeitpunkt\n001;2025;12.50;true;2026-10-06;2026-10-06T12:00:00+01:00\n");
    String id =
        Json.required(
            f.call(
                "start",
                "workflow",
                "model",
                "organization",
                "agi",
                "identifier",
                "ch.so.grundwasser.qualitaet",
                "data_path",
                csv.toString(),
                "metadata_path",
                meta.toString()),
            "id");
    f.approve(id, "data", f.call("analyze", "run_id", id));
    var identity =
        Json.map(
            "name",
            "SO_Types_20261006",
            "uri",
            "https://example.org/models",
            "version",
            "2026-10-06",
            "technical_contact",
            "fixture@example.org",
            "title",
            "Bestätigte Typ-Testfixture",
            "short_description",
            "Typen und Pflichtigkeit in einer isolierten Fixture");
    var confirmations =
        Json.map(
            "Code",
            Json.map(
                "confirmed",
                true,
                "type_spec",
                Json.map("baseType", Json.map("kind", "TEXT", "length", 3))),
            "Zeitpunkt",
            Json.map("confirmed", true, "type_spec", Json.map("domainFqn", "INTERLIS.XMLDateTime")),
            "Messwert",
            Json.map(
                "confirmed",
                true,
                "type_spec",
                Json.map(
                    "baseType", Json.map("kind", "NUM_RANGE", "min", -9999.99, "max", 9999.99))));
    var derived =
        f.call("derive_model", "run_id", id, "identity", identity, "confirmations", confirmations);
    assertFalse(Json.bool(derived, "candidate", true), Json.pretty(derived));
    var report = f.call("validate_model", "run_id", id);
    assertFalse(Json.bool(report, "valid", true), Json.pretty(report));
    assertTrue(
        Json.list(report.get("errors")).stream()
            .map(Json::obj)
            .anyMatch(e -> Objects.equals(e.get("code"), "datetime_validator_limit")));
    Xml.children(dataset).stream()
        .filter(
            e ->
                Xml.local(e).equals("attributes")
                    && Xml.children(e).stream()
                        .anyMatch(a -> "Zeitpunkt".equals(Xml.text(a, "name"))))
        .toList()
        .forEach(dataset::removeChild);
    Json.write(meta, Xml.serialize(doc));
    Json.write(csv, "Code;Jahr;Messwert;Aktiv;Datum\n001;2025;12.50;true;2026-10-06\n");
    String positiveId =
        Json.required(
            f.call(
                "start",
                "workflow",
                "model",
                "organization",
                "agi",
                "identifier",
                "ch.so.grundwasser.qualitaet",
                "data_path",
                csv.toString(),
                "metadata_path",
                meta.toString()),
            "id");
    f.approve(positiveId, "data", f.call("analyze", "run_id", positiveId));
    identity.put("name", "SO_Types_Positive_20261006");
    var positive =
        f.call(
            "derive_model",
            "run_id",
            positiveId,
            "identity",
            identity,
            "confirmations",
            confirmations);
    assertFalse(Json.bool(positive, "candidate", true), Json.pretty(positive));
    assertTrue(Json.bool(f.call("validate_model", "run_id", positiveId), "valid", false));
  }
}
