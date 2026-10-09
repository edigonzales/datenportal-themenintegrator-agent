package ch.so.agi.integrator;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.tomlj.Toml;

/** Explicit disposable test; never uses business profiles, repositories or approval records. */
final class DeliveryAcceptance {
  static final String DATASET = "ch.so.integrator.fixture", ORGANIZATION = "agi";
  static final String STATEMENT =
      "AUTOMATISIERTE SYNTHETISCHE DELIVERY-TESTFIXTURE; keine fachliche Freigabe";
  final Settings source;
  final ProcessRunner process = new ProcessRunner();

  DeliveryAcceptance(Settings source) {
    this.source = source;
  }

  Path prepare() throws Exception {
    new GretlRuntime(source, process).selected(true);
    for (String service : List.of("datasheet", "interlis"))
      new DockerMcps(source, process).selected(service, true);
    Path directory =
        source.root.resolve(".datenportal-integrator/delivery-acceptance/" + UUID.randomUUID());
    Files.createDirectories(directory);
    Json.write(directory.resolve("SYNTHETIC_TEST_FIXTURE"), STATEMENT + "\n");
    Path root = directory.resolve("agent"),
        repo = directory.resolve("datenportal-themenrepo"),
        stack = directory.resolve("datenportal-dev-stack");
    Files.createDirectories(root.resolve("config"));
    Files.copy(
        AgentResources.resolve(source.root, "config/rules.json"),
        root.resolve("config/rules.json"));
    for (String part : List.of("validation", "templates"))
      Workspace.copy(AgentResources.resolve(source.root, part), root.resolve(part));
    Files.createDirectories(root.resolve("build/libs"));
    Files.copy(
        AgentResources.resolve(source.root, "build/libs/datenportal-integrator.jar"),
        root.resolve("build/libs/datenportal-integrator.jar"));
    for (Path proof :
        List.of(new DockerMcps(source, process).lockFile(), GretlRuntime.file(source))) {
      Path copy = root.resolve(source.root.relativize(proof));
      Files.createDirectories(copy.getParent());
      Files.copy(proof, copy);
    }
    // Preserve the cache boundary as well as its original pinned content hashes.
    Path privateLock =
        root.resolve(source.root.relativize(new DockerMcps(source, process).lockFile()));
    var pinned = Json.read(privateLock);
    var datasheet = Json.obj(Json.obj(pinned.get("services")).get("datasheet"));
    Path originalModels = Path.of(Json.required(datasheet, "model_directory"));
    Path privateModels = root.resolve(source.root.relativize(originalModels));
    Workspace.copy(originalModels, privateModels);
    datasheet.put("model_directory", privateModels.toString());
    Json.atomic(privateLock, pinned);
    Workspace.copy(source.topics.resolve("shared"), repo.resolve("shared"));
    Workspace.copy(source.topics.resolve("gradle"), repo.resolve("gradle"));
    Files.copy(source.topics.resolve("gradlew"), repo.resolve("gradlew"));
    for (String file : List.of("settings.gradle", "build.gradle"))
      Files.copy(source.topics.resolve(file), repo.resolve(file));
    Json.write(repo.resolve("agi/settings.gradle"), "rootProject.name = 'agi'\n");
    Json.write(
        repo.resolve("agi/build.gradle"),
        """
        plugins { id 'ch.so.agi.gretl' }
        ext.datenportalOrganization = [id:'agi', title:'SYNTHETIC TEST FIXTURE', label:'SYNTHETIC TEST FIXTURE', defaultDataset:'ch.so.integrator.fixture']
        apply from: '../shared/gradle/organisation-common.gradle'
        """);
    Json.write(
        repo.resolve("agi/gretl-datenportal-job.yaml"),
        "title: SYNTHETIC TEST FIXTURE\ndescription: Isolated delivery acceptance\npermissions:\n  read:\n    - team: datenportal-read\n  build:\n    - team: agi-build\n");
    String xml =
        Json.contents(AgentResources.resolve(source.root, "tests/fixtures/dataset.xtf"))
            .replace("ch.so.grundwasser.qualitaet", DATASET)
            .replace("Wasserqualität Grundwasser Kanton Solothurn", "SYNTHETIC TEST FIXTURE")
            .replace(
                "<ns2:name>Jahr</ns2:name>",
                "<ns2:name>Jahr</ns2:name><ns2:description>Synthetisches Beobachtungsjahr</ns2:description>");
    Json.write(repo.resolve("agi/" + DATASET + "/meta-" + DATASET + ".xtf"), xml);
    Json.write(directory.resolve("valid.csv"), "Jahr\n2025\n");
    Json.write(directory.resolve("invalid.csv"), "Jahr\nnot-a-number\n");
    // Copy only tracked stack files. In particular, never copy .env or generated Garage secrets.
    Files.createDirectories(stack);
    String tracked = process.checked(List.of("git", "ls-files", "-z"), source.stack, 30);
    for (String name : tracked.split("\u0000")) {
      if (name.isEmpty()) continue;
      Path from = Json.inside(source.stack, name), to = Json.inside(stack, name);
      if (Files.isSymbolicLink(from))
        throw new Problem("unsafe_path", "Stack-Testvorlage enthält einen Symlink.");
      if (Files.isRegularFile(from)) {
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
      }
    }
    String project = "delivery-test-" + directory.getFileName();
    String compose =
        Json.contents(stack.resolve("compose.yaml"))
            .replaceFirst("(?m)^name:.*$", "name: " + project);
    Json.write(stack.resolve("compose.yaml"), compose);
    int publicPort = port(), s3Port = port();
    String example =
        Json.contents(stack.resolve(".env.example"))
            .replaceFirst("(?m)^GARAGE_PUBLIC_PORT=.*$", "GARAGE_PUBLIC_PORT=" + publicPort)
            .replaceFirst(
                "(?m)^DATENPORTAL_JENKINS_IMAGE=.*$",
                "DATENPORTAL_JENKINS_IMAGE="
                    + Json.required(new GretlRuntime(source, process).selected(false), "image_id"));
    example += "\nGARAGE_S3_PORT=" + s3Port + "\nTHEMEN_REPO_BRANCH=main\n";
    Json.write(stack.resolve(".env.example"), example);
    // Existing external initializer only; no new Python implementation.
    process.checked(List.of("bash", "scripts/init.sh"), stack, 60);
    var values = Json.read(Json.text(source.values));
    values.put("root", "..");
    values.put("topics_repo", repo.toString());
    values.put("stack_repo", stack.toString());
    values.put("state_dir", ".datenportal-integrator/runs");
    values.put("compose_files", List.of("compose.yaml"));
    values.put("stack_start_args", List.of());
    values.put(
        "validator_command",
        source.strings("validator_command", List.of()).stream()
            .map(v -> v.endsWith(".jar") ? source.path(v).toString() : v)
            .toList());
    for (String service : List.of("datasheet", "interlis")) {
      if (values.get(service) instanceof Map) Json.obj(values.get(service)).put("managed", false);
    }
    Json.obj(values.get("gretl")).put("mode", "ephemeral");
    String base = "http://localhost:" + publicPort;
    values.put(
        "environments",
        Json.map(
            "local",
            Json.map(
                "kind",
                "local",
                "enabled",
                true,
                "repository_mode",
                "working-tree",
                "jenkins_url",
                base + "/jenkins",
                "portal_url",
                base,
                "manifest_url",
                base + "/ch.so.daten/current.json",
                "username_env",
                "SYNTHETIC_DELIVERY_USER",
                "token_env",
                "SYNTHETIC_DELIVERY_TOKEN",
                "git_write_back",
                false,
                "reload_portal",
                true)));
    writeToml(root.resolve("config/local.toml"), values);
    Files.createDirectories(root.resolve("bin"));
    Json.write(
        root.resolve("bin/datenportal-agent"),
        "#!/usr/bin/env bash\nset -e\nif [[ ${1:-} == --config ]]; then shift 2; fi\nexec "
            + quote(source.root.resolve("bin/datenportal-agent").toString())
            + " --config "
            + quote(root.resolve("config/local.toml").toString())
            + " \"$@\"\n");
    root.resolve("bin/datenportal-agent").toFile().setExecutable(true);
    Files.createDirectories(root.resolve(".agents/skills"));
    Files.createSymbolicLink(
        root.resolve(".agents/skills/datenportal-themenintegrator"),
        source.root.resolve("skills/datenportal-themenintegrator"));
    Json.write(
        root.resolve("AGENTS.md"),
        "SYNTHETIC TEST FIXTURE. Only this private configuration and repository may be used. No business approvals or other environments.\n");
    var privateSettings = new Settings(root.resolve("config/local.toml"));
    new Harness(privateSettings).install();
    // An independent Git root prevents GUI project discovery from selecting the business repo.
    process.checked(List.of("git", "init", "--initial-branch=main"), root, 30);
    process.checked(
        List.of("git", "add", "AGENTS.md", "opencode.json", ".codex/config.toml"), root, 30);
    process.checked(
        List.of(
            "git",
            "-c",
            "user.name=Synthetic Fixture",
            "-c",
            "user.email=fixture@example.org",
            "-c",
            "commit.gpgsign=false",
            "-c",
            "core.hooksPath=/dev/null",
            "commit",
            "-m",
            "Synthetic GUI acceptance fixture"),
        root,
        30);
    Json.atomic(
        directory.resolve("fixture.json"),
        Json.map(
            "synthetic_fixture",
            true,
            "directory",
            directory.toString(),
            "root",
            root.toString(),
            "stack",
            stack.toString(),
            "project",
            project));
    return directory;
  }

  Map<String, Object> run(Path directory) throws Exception {
    var fixture = fixture(directory);
    Settings s = new Settings(Path.of(Json.required(fixture, "root")).resolve("config/local.toml"));
    var credentials = new Credentials(s);
    var env = s.environment("local");
    if (!s.root.equals(directory.resolve("agent"))
        || !s.topics.equals(directory.resolve("datenportal-themenrepo"))
        || !s.stack.equals(directory.resolve("datenportal-dev-stack"))
        || Json.obj(s.values.get("environments")).size() != 1)
      throw new Problem(
          "unsafe_path",
          "Delivery-Abnahme verlangt ausschliesslich ihre privaten Pfade und ein lokales Testprofil.");
    var endpoint = Settings.url(Json.required(env, "jenkins_url"));
    if (!endpoint.getScheme().equals("http")
        || !endpoint.getHost().equals("localhost")
        || !endpoint.getPath().equals("/jenkins"))
      throw new Problem("unsafe_path", "Jenkins-Abnahme verlangt die lokale Fixture-Adresse.");
    var before = containers(Path.of(Json.required(fixture, "stack")));
    String jenkinsId = Json.required(Json.obj(before.get("jenkins")), "id");
    var ports =
        Json.read(
            process.checked(
                List.of(
                    "docker", "inspect", "--format", "{{json .NetworkSettings.Ports}}", jenkinsId),
                source.root,
                30));
    int endpointPort = endpoint.getPort();
    boolean privatePort =
        ports.values().stream()
            .filter(Objects::nonNull)
            .flatMap(v -> Json.list(v).stream())
            .map(Json::obj)
            .anyMatch(v -> Objects.equals(v.get("HostPort"), Integer.toString(endpointPort)));
    if (!privatePort)
      throw new Problem(
          "unsafe_path", "Jenkins-Testadresse gehört nicht zum eigenen Fixture-Container.");
    var report =
        Json.map("synthetic_fixture", true, "valid", false, "directory", directory.toString());
    try (var codex = client(s, true);
        var opencode = client(s, false)) {
      // Both real MCP processes are already alive before credentials are stored.
      credentials.save(
          "local", "admin", "admin"); // Explicit JCasC account of this disposable test stack only.
      String id =
          Json.required(
              call(
                  codex,
                  "start",
                  Json.map(
                      "organization",
                      ORGANIZATION,
                      "identifier",
                      DATASET,
                      "data_path",
                      directory.resolve("valid.csv").toString(),
                      "metadata_path",
                      s.topics.resolve("agi/" + DATASET + "/meta-" + DATASET + ".xtf").toString())),
              "id");
      report.put("run_id", id);
      Json.atomic(directory.resolve("progress.json"), report);
      var analysis = call(codex, "analyze", Json.map("run_id", id));
      approve(codex, id, "data", analysis);
      var model =
          call(
              codex,
              "derive_model",
              Json.map(
                  "run_id",
                  id,
                  "identity",
                  Json.map(
                      "name",
                      "SO_Integrator_Delivery_20261009",
                      "uri",
                      "https://example.org/models",
                      "version",
                      "2026-10-09",
                      "technical_contact",
                      "fixture@example.org",
                      "title",
                      "SYNTHETIC TEST FIXTURE",
                      "short_description",
                      "Isolated delivery acceptance")));
      if (Json.bool(model, "candidate", true)) fail("Modellableitung unvollständig", model);
      var checked = call(codex, "validate", Json.map("run_id", id));
      if (!Json.bool(checked, "valid", false)) fail("Fachprüfung fehlgeschlagen", checked);
      approve(codex, id, "metadata", checked);
      approve(codex, id, "model", Json.obj(checked.get("model")));
      call(codex, "apply_local_changes", Json.map("run_id", id));
      report.put("model", Json.obj(checked.get("model")));
      Json.atomic(directory.resolve("progress.json"), report);
      Map<String, Object> delivery = Json.map();
      long deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos();
      int phase = 0;
      while (System.nanoTime() < deadline) {
        delivery =
            call(
                phase++ % 2 == 0 ? codex : opencode,
                "deliver",
                Json.map("run_id", id, "environment", "local"));
        report.put("delivery", delivery);
        Json.atomic(directory.resolve("progress.json"), report);
        if (Set.of("complete", "failed").contains(Json.str(delivery, "phase", ""))) break;
        Thread.sleep(2000);
      }
      var jenkins = new Jenkins(env, credentials.resolve("local", env));
      var positiveStatus = jenkins.status(Json.obj(delivery.get("job")));
      String positive =
          ProcessRunner.redact(
              console(jenkins, positiveStatus),
              credentials.resolve("local", env).bootstrapEnvironment());
      Json.write(directory.resolve("positive-console.log"), positive);
      if (!Json.bool(delivery, "verified", false))
        fail("Anlieferung nicht vollständig bestätigt", delivery);
      if (!positive.contains("INTEGRATOR_CSV_VALIDATED=true")
          || !positive.contains("preparePublicationWorkspace"))
        fail("GRETL-CSV-Prüfung im Lieferjob nicht nachgewiesen", positiveStatus);
      String modelSha =
          Json.sha(s.topics.resolve("agi/" + DATASET + "/SO_Integrator_Delivery_20261009.ili"));
      if (!Json.contents(s.topics.resolve("agi/" + DATASET + "/dataset.gradle")).contains(modelSha))
        fail("Modellbytes sind nicht an die Task gebunden", Json.map());
      var manifest = Jenkins.manifest(env);
      var negativeJob =
          jenkins.submit(
              ORGANIZATION,
              DATASET,
              null,
              directory.resolve("invalid.csv"),
              null,
              "SYNTHETIC NEGATIVE FIXTURE " + directory.getFileName());
      Map<String, Object> negativeStatus = Json.map();
      deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
      while (System.nanoTime() < deadline) {
        negativeStatus = jenkins.status(negativeJob);
        if (Json.bool(negativeStatus, "complete", false)) break;
        Thread.sleep(2000);
      }
      String negative =
          ProcessRunner.redact(
              console(jenkins, negativeStatus),
              credentials.resolve("local", env).bootstrapEnvironment());
      Json.write(directory.resolve("negative-console.log"), negative);
      // Keep only the separately redacted console, never the API's raw embedded log.
      negativeStatus.remove("logText");
      if (!negative.contains("validateThemenCsv FAILED")
          || !negative.contains("not-a-number")
          || !negative.contains("is not a number in attribute Jahr")
          || negative.contains("> Task :preparePublicationWorkspace"))
        fail("Negativfall weist keine frühe CSV-Validierungsverletzung nach", negativeStatus);
      if (!Objects.equals(manifest, Jenkins.manifest(env)))
        fail("Negativfall hat den erfolgreichen Release verändert", negativeStatus);
      if (!Objects.equals(before, containers(s.stack)))
        fail(
            "Anlieferung hat vorhandene Infrastruktur-Container ersetzt oder neu gestartet",
            Json.map());
      report.putAll(
          Json.map(
              "valid",
              true,
              "harnesses",
              List.of("codex", "opencode"),
              "credentials_source",
              credentials.status("local").get("credentials_source"),
              "containers_reused",
              true,
              "infrastructure_before",
              before,
              "infrastructure_after",
              containers(s.stack),
              "negative",
              negativeStatus,
              "negative_job",
              negativeJob,
              "release_unchanged_after_negative",
              true,
              "model_sha256",
              modelSha,
              "csv_sha256",
              Json.sha(directory.resolve("valid.csv")),
              "positive_console_sha256",
              Json.sha(directory.resolve("positive-console.log")),
              "negative_console_sha256",
              Json.sha(directory.resolve("negative-console.log"))));
      Json.atomic(directory.resolve("manifest.json"), manifest);
      report.put("manifest_sha256", Json.sha(directory.resolve("manifest.json")));
      Json.atomic(directory.resolve("report.json"), report);
      return report;
    } catch (Problem p) {
      report.put("problem", p.result());
      Json.atomic(directory.resolve("report.json"), report);
      throw p;
    }
  }

  Map<String, Object> fixture(Path directory) {
    Path expected =
        source
            .root
            .resolve(".datenportal-integrator/delivery-acceptance")
            .toAbsolutePath()
            .normalize();
    Path selected = directory.toAbsolutePath().normalize();
    if (!selected.getParent().equals(expected)
        || !Files.isRegularFile(selected.resolve("SYNTHETIC_TEST_FIXTURE")))
      throw new Problem(
          "unsafe_path", "Nur eine eigene markierte Delivery-Testfixture ist zulässig.");
    var fixture = Json.read(selected.resolve("fixture.json"));
    if (!Json.bool(fixture, "synthetic_fixture", false)
        || !Objects.equals(fixture.get("directory"), selected.toString()))
      throw new Problem("unsafe_path", "Delivery-Testfixture stimmt nicht überein.");
    if (Files.isSymbolicLink(selected)
        || !Objects.equals(fixture.get("root"), selected.resolve("agent").toString())
        || !Objects.equals(
            fixture.get("stack"), selected.resolve("datenportal-dev-stack").toString())
        || !Objects.equals(fixture.get("project"), "delivery-test-" + selected.getFileName())
        || Files.isSymbolicLink(selected.resolve("agent"))
        || Files.isSymbolicLink(selected.resolve("datenportal-themenrepo"))
        || Files.isSymbolicLink(selected.resolve("datenportal-dev-stack")))
      throw new Problem(
          "unsafe_path", "Delivery-Testfixture darf keine anderen Pfade oder Projekte auswählen.");
    return fixture;
  }

  void cleanup(Path directory) {
    var fixture = fixture(directory);
    var root = Path.of(Json.required(fixture, "root"));
    var settings = new Settings(root.resolve("config/local.toml"));
    if (!settings.root.equals(root))
      throw new Problem(
          "unsafe_path", "Cleanup-Konfiguration muss im eigenen Fixture-Root bleiben.");
    if (!Files.isRegularFile(directory.resolve("report.json")))
      Json.atomic(
          directory.resolve("report.json"),
          Json.map(
              "synthetic_fixture",
              true,
              "valid",
              false,
              "error",
              "acceptance_incomplete",
              "message",
              "Abnahme vor Abschluss abgebrochen; Startup- und Fortschrittsnachweise prüfen."));
    new Credentials(settings).remove("local");
    try {
      Files.deleteIfExists(root.resolve(".datenportal-integrator/credentials/local.json"));
    } catch (Exception ignored) {
    }
    Path stack = Path.of(Json.required(fixture, "stack"));
    var result =
        process.run(
            List.of(
                "docker",
                "compose",
                "--project-name",
                Json.required(fixture, "project"),
                "-f",
                "compose.yaml",
                "down",
                "--remove-orphans"),
            stack,
            120,
            directory.resolve("cleanup.log"));
    if (result.exitCode() != 0)
      throw new Problem(
          "acceptance_cleanup_failed",
          "Eigene Testcontainer konnten nicht vollständig entfernt werden.");
    String volumes =
        process.checked(
            List.of(
                "docker",
                "volume",
                "ls",
                "-q",
                "--filter",
                "label=com.docker.compose.project=" + Json.required(fixture, "project")),
            source.root,
            30);
    if (!volumes.isBlank()) {
      var remove = new ArrayList<>(List.of("docker", "volume", "rm"));
      remove.addAll(volumes.lines().toList());
      process.checked(remove, source.root, 60);
    }
    try {
      Files.deleteIfExists(stack.resolve(".env"));
      Files.deleteIfExists(stack.resolve("services/garage/garage.toml"));
    } catch (Exception e) {
      throw new Problem(
          "acceptance_cleanup_failed", "Eigene Testsecrets konnten nicht entfernt werden.");
    }
  }

  Map<String, Object> containers(Path stack) {
    var states = Json.map();
    for (String service : List.of("jenkins", "downloads", "garage")) {
      String id =
          process
              .checked(
                  List.of(
                      "docker",
                      "ps",
                      "-q",
                      "--filter",
                      "label=com.docker.compose.project.working_dir=" + stack,
                      "--filter",
                      "label=com.docker.compose.service=" + service),
                  source.root,
                  30)
              .strip();
      if (id.isEmpty() || id.contains("\n"))
        fail("Testdienst fehlt oder ist mehrdeutig", Json.map("service", service));
      String started =
          process.checked(
              List.of("docker", "inspect", "--format", "{{.State.StartedAt}}", id),
              source.root,
              30);
      states.put(service, Json.map("id", id, "started", started));
    }
    return states;
  }

  io.modelcontextprotocol.client.McpSyncClient client(Settings s, boolean codex)
      throws java.io.IOException {
    String command;
    List<String> args;
    if (codex) {
      var table =
          Toml.parse(s.root.resolve(".codex/config.toml"))
              .getTable("mcp_servers.datenportal_integrator");
      command = table.getString("command");
      args = table.getArray("args").toList().stream().map(Object::toString).toList();
    } else {
      var entry =
          Json.obj(
              Json.obj(Json.read(s.root.resolve("opencode.json")).get("mcp"))
                  .get("datenportal_integrator"));
      var parts = Json.strings(entry.get("command"));
      command = parts.getFirst();
      args = parts.subList(1, parts.size());
    }
    Path executable = s.root.resolve(command).toAbsolutePath();
    var params =
        ServerParameters.builder(executable.toString())
            .args(args)
            .env(Map.of("JAVA_HOME", "/no-host-jdk", "DATENPORTAL_FORWARD_ENV", ""))
            .build();
    var transport =
        new StdioClientTransport(
            params, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    transport.setStdErrorHandler(line -> {});
    var client =
        McpClient.sync(transport)
            .initializationTimeout(Duration.ofMinutes(5))
            .requestTimeout(Duration.ofMinutes(30))
            .build();
    client.initialize();
    return client;
  }

  static Map<String, Object> call(
      io.modelcontextprotocol.client.McpSyncClient client,
      String operation,
      Map<String, Object> args) {
    var result = client.callTool(new McpSchema.CallToolRequest(operation, args));
    var data = Json.obj(result.structuredContent());
    if (Boolean.TRUE.equals(result.isError()))
      fail("MCP-Abnahme fehlgeschlagen: " + operation, data);
    return data;
  }

  static void approve(
      io.modelcontextprotocol.client.McpSyncClient client,
      String id,
      String gate,
      Map<String, Object> check) {
    call(
        client,
        "approve",
        Json.map(
            "run_id",
            id,
            "gate",
            gate,
            "fingerprint",
            check.get("fingerprint"),
            "human_statement",
            STATEMENT));
  }

  static String console(Jenkins jenkins, Map<String, Object> status) {
    String url = Json.required(status, "consoleUrl").replaceAll("/+$", "");
    if (!url.endsWith("/console"))
      throw new Problem(
          "acceptance_evidence_missing", "Jenkins-Konsole hat keine bekannte Adresse.");
    return new String(jenkins.readEvidence(url + "Text"), StandardCharsets.UTF_8);
  }

  static void fail(String message, Object report) {
    throw new Problem("acceptance_failed", message, "evidence", report);
  }

  static String quote(String value) {
    return "'" + value.replace("'", "'\\''") + "'";
  }

  static int port() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  static void writeToml(Path file, Map<String, Object> values) {
    var text = new StringBuilder();
    toml(values, "", text);
    Json.write(file, text.toString());
  }

  static void toml(Map<String, Object> values, String section, StringBuilder text) {
    if (!section.isEmpty()) text.append("\n[").append(section).append("]\n");
    values.forEach(
        (key, value) -> {
          if (!(value instanceof Map))
            text.append(Json.text(key)).append(" = ").append(Json.text(value)).append("\n");
        });
    values.forEach(
        (key, value) -> {
          if (value instanceof Map)
            toml(
                Json.obj(value),
                section.isEmpty() ? Json.text(key) : section + "." + Json.text(key),
                text);
        });
  }
}
