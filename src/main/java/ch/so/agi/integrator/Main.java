package ch.so.agi.integrator;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import java.nio.file.*;
import java.util.*;

public final class Main {
  public static void main(String[] argv) {
    int code = execute(argv);
    if (code != 0) System.exit(code);
  }

  static String version() {
    String version = Main.class.getPackage().getImplementationVersion();
    return version == null ? "0.1.0-dev" : version;
  }

  static int execute(String[] argv) {
    try {
      var args = new ArrayList<>(Arrays.asList(argv));
      String config = null;
      int i = args.indexOf("--config");
      if (i >= 0) {
        if (i + 1 >= args.size())
          throw new Problem("invalid_arguments", "--config benötigt einen Pfad.");
        config = args.remove(i + 1);
        args.remove(i);
      }
      if (args.equals(List.of("--version"))) {
        System.out.println(version());
        return 0;
      }
      if (args.isEmpty() || args.getFirst().equals("--help")) {
        System.out.println(
            "Java 21 Themenintegrator\nbin/datenportal-agent [--config PATH] init|doctor|serve|serve-workspace|schema OP|call OP [--json JSON|--args-file PATH]\nworkspace install|update ZIELORDNER\ninit [--skip-smoke]\ncredentials set|status|remove PROFIL\ncredentials github set --token-stdin | --from-env; credentials github status|remove\nacceptance delivery (optional, Docker erforderlich)\nWeitere Helfer: harness-config, codex");
        return 0;
      }
      String command = args.removeFirst();
      if (command.equals("git-credential")) return GitHubCredentials.run(args, config);
      if (command.equals("workspace")) {
        print(OperatorWorkspace.command(args));
        return 0;
      }
      if (command.equals("schema")) {
        if (args.size() != 1) throw new Problem("invalid_arguments", "schema OPERATION");
        if (args.getFirst().startsWith("metadata.") || ModelContracts.handles(args.getFirst())) {
          try (var w = new Workflow(Settings.load(config))) {
            print(w.call("operation_schema", Json.map("operation", args.getFirst())));
          }
        } else print(Operations.schema(args.getFirst()));
        return 0;
      }
      if (command.equals("init")) Initializer.prepareConfig(config);
      Settings s = Settings.load(config);
      if (command.equals("acceptance-prepare") && args.isEmpty()) {
        System.out.println(new DeliveryAcceptance(s).prepare());
        return 0;
      }
      if (Set.of("acceptance-run", "acceptance-cleanup").contains(command) && args.size() == 1) {
        var acceptance = new DeliveryAcceptance(s);
        Path directory = Path.of(args.getFirst());
        if (command.equals("acceptance-cleanup")) {
          acceptance.cleanup(directory);
          print(Json.map("cleaned", true));
        } else print(acceptance.run(directory));
        return 0;
      }
      if (command.equals("credentials")) {
        print(new Credentials(s).command(args));
        return 0;
      }
      if (command.equals("codex-arguments")) {
        for (String argument : new Harness(s).codexArguments()) {
          System.out.write(argument.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          System.out.write(0);
        }
        return 0;
      }
      if (command.equals("init")) {
        print(new Initializer(s).run(args));
        return 0;
      }
      if (command.equals("serve")) {
        serve(s);
        return 0;
      }
      if (command.equals("serve-workspace")) {
        serve(s, true);
        return 0;
      }
      if (Set.of(
              "setup-java",
              "setup-tools",
              "setup-gretl",
              "setup-mcps",
              "runtime-up",
              "harness-config",
              "codex")
          .contains(command)) {
        print(new Setup(s).run(command, args));
        return 0;
      }
      if (command.equals("doctor")) {
        print(doctor(s));
        return 0;
      }
      if (!command.equals("call") || args.isEmpty())
        throw new Problem("invalid_arguments", "call OPERATION benötigt eine Operation.");
      String operation = args.removeFirst(), raw = "{}";
      if (!args.isEmpty()) {
        String flag = args.removeFirst();
        if (args.size() != 1)
          throw new Problem("invalid_arguments", "JSON oder Argumentdatei angeben.");
        String value = args.removeFirst();
        raw =
            switch (flag) {
              case "--json" -> value;
              case "--args-file" ->
                  value.equals("-")
                      ? new String(
                          System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                      : Json.contents(Path.of(value));
              default -> throw new Problem("invalid_arguments", "Unbekanntes CLI-Argument.");
            };
      }
      try (var workflow = new Workflow(s)) {
        print(workflow.call(operation, Json.read(raw)));
      }
      return 0;
    } catch (Problem p) {
      print(p.result());
      return 2;
    } catch (Exception e) {
      print(
          Json.map(
              "error",
              "operation_failed",
              "message",
              "Operation konnte nicht abgeschlossen werden.",
              "exception",
              e.getClass().getSimpleName()));
      return 1;
    }
  }

  static void print(Object o) {
    System.out.println(Json.pretty(o));
  }

  public static Object doctor(Settings s) {
    var checks =
        Json.map(
            "java",
            Runtime.version().feature() == 21,
            "topics_repo",
            Files.isDirectory(s.topics),
            "stack_repo",
            Files.isDirectory(s.stack));
    try {
      checks.put("operator_workspace", OperatorPolicy.check(s));
    } catch (Problem p) {
      checks.put("operator_workspace", p.result());
      checks.put("initialization", Json.map("ready", false, "reason", p.code));
      return checks;
    }
    try {
      checks.put("agent_runtime", AgentRuntime.check(s));
    } catch (Problem p) {
      checks.put("agent_runtime", p.result());
      checks.put("initialization", new Initializer(s).status());
      return checks;
    }
    try {
      checks.put("configuration_fingerprint", s.fingerprint());
    } catch (Problem p) {
      checks.put("configuration_fingerprint", p.result());
    }
    if (Files.isDirectory(s.stack))
      try {
        checks.put("stack", new Stack(s, new ProcessRunner()).inspect());
      } catch (Problem e) {
        checks.put("stack", e.result());
      }
    for (String service : List.of("datasheet", "interlis")) {
      try (var client =
          service.equals("datasheet") ? McpClients.datasheet(s) : McpClients.interlis(s)) {
        var tools = client.tools();
        var expected =
            service.equals("datasheet")
                ? Set.of(
                    "describe_schema",
                    "import_xtf",
                    "read_datasheet",
                    "update_metadata",
                    "upsert_attribute",
                    "remove_attribute",
                    "upsert_issue",
                    "remove_issue",
                    "validate_datasheet",
                    "export_xtf")
                : Set.of(
                    "authorIliModel", "applyIliModelChanges", "reviewIliModel", "reviewIliChange");
        checks.put(
            service + "_mcp",
            Json.map(
                "valid",
                tools.containsAll(expected),
                "tools",
                tools.stream().sorted().toList(),
                "missing_tools",
                expected.stream().filter(t -> !tools.contains(t)).sorted().toList()));
      } catch (Problem p) {
        checks.put(service + "_mcp", p.result());
      }
    }
    checks.put("interlis_configured", !s.mcp("interlis").isEmpty());
    checks.put("initialization", new Initializer(s).status());
    var publication = Json.map();
    Json.obj(s.values.get("environments"))
        .forEach(
            (name, value) -> {
              var status = new Credentials(s).status(name);
              status.put("enabled", Json.bool(Json.obj(value), "enabled", false));
              publication.put(name, status);
            });
    checks.put("publication", publication);
    return checks;
  }

  public static void serve(Settings s) throws InterruptedException {
    serve(s, false);
  }

  static void serve(Settings s, boolean files) throws InterruptedException {
    if (OperatorWorkspace.installed(s.root)) OperatorPolicy.check(s);
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    var ended = new java.util.concurrent.CountDownLatch(1);
    var mapper = new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper());
    var transport =
        new StdioServerTransportProvider(mapper, System.in, System.out) {
          @Override
          public void setSessionFactory(McpServerSession.Factory factory) {
            super.setSessionFactory(
                raw -> factory.create(new SerializedMcpTransport(raw, ended::countDown)));
          }
        };
    var workflow = files ? null : new Workflow(s);
    var workspace = files ? new WorkspaceFiles(s) : null;
    var tools = new ArrayList<McpServerFeatures.SyncToolSpecification>();
    (files ? WorkspaceFiles.TOOLS : Operations.ALL)
        .forEach(
            (name, op) ->
                tools.add(
                    McpServerFeatures.SyncToolSpecification.builder()
                        .tool(
                            McpSchema.Tool.builder()
                                .name(name)
                                .description(op.description())
                                .inputSchema(op.schema())
                                .build())
                        .callHandler(
                            (exchange, request) -> {
                              try {
                                Object result =
                                    files
                                        ? workspace.call(name, request.arguments())
                                        : workflow.call(name, request.arguments());
                                result = McpResults.present(s, name, result);
                                if (!files
                                    && request.arguments().get("run_id") instanceof String runId
                                    && runId.matches("[0-9a-f]{32}")
                                    && result instanceof Map<?, ?>) {
                                  var withReports = new LinkedHashMap<>(Json.obj(result));
                                  var reports = new ArrayList<Object>();
                                  for (String reportName :
                                      List.of(
                                          "data-review.html",
                                          "metadata-review.html",
                                          "model-review.html",
                                          "model-preparation-review.html",
                                          "organization-review.html")) {
                                    var report = s.runs.resolve(runId).resolve(reportName);
                                    if (java.nio.file.Files.isRegularFile(report))
                                      reports.add(
                                          Json.map(
                                              "path",
                                              report.toString(),
                                              "url",
                                              report.toUri().toString()));
                                  }
                                  if (!reports.isEmpty()) {
                                    withReports.put("reports", reports);
                                    withReports.put(
                                        "show_report",
                                        "datenportal_workspace.open_report(path=...) zeigt den Bericht im lokalen Browser; keine Freigabe.");
                                  }
                                  result = withReports;
                                }

                                return McpSchema.CallToolResult.builder()
                                    .structuredContent(result)
                                    .addTextContent(Json.pretty(result))
                                    .isError(false)
                                    .build();
                              } catch (Problem e) {
                                var failure = McpResults.present(s, name, e.result());
                                return McpSchema.CallToolResult.builder()
                                    .structuredContent(failure)
                                    .addTextContent(Json.pretty(failure))
                                    .isError(true)
                                    .build();
                              } catch (Exception e) {
                                var error =
                                    Json.map(
                                        "error",
                                        "operation_failed",
                                        "message",
                                        "Operation konnte nicht abgeschlossen werden.",
                                        "exception",
                                        e.getClass().getSimpleName());
                                return McpSchema.CallToolResult.builder()
                                    .structuredContent(error)
                                    .addTextContent(Json.text(error))
                                    .isError(true)
                                    .build();
                              }
                            })
                        .build()));
    var server =
        McpServer.sync(transport)
            .serverInfo(files ? "datenportal-workspace" : "datenportal-themenintegrator", version())
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .instructions(
                "Öffentliche Werkzeugschemas und Skills verwenden; keine Implementierungsquellen lesen oder durchsuchen. operation_schema erklärt Integrator-Aufrufe. Dateioperationen über datenportal_workspace. Menschliche Freigaben nur nach tatsächlicher Antwort protokollieren. Bei unklarem Upload vorhandene Laufkennung aufklären.")
            .tools(tools)
            .build();
    Runnable close =
        () -> {
          try {
            server.close();
          } finally {
            if (workflow != null) workflow.close();
          }
        };
    Runtime.getRuntime().addShutdownHook(new Thread(close));
    // Follow the transport lifecycle, including failures before stdin reaches EOF.
    ended.await();
    close.run();
  }
}
