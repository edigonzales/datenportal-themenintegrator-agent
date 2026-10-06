package ch.so.agi.integrator;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.*;
import java.util.*;

public final class Main {
  public static void main(String[] argv) {
    int code = execute(argv);
    if (code != 0) System.exit(code);
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
      if (args.isEmpty() || args.getFirst().equals("--help")) {
        System.out.println(
            "Java 25 Themenintegrator\njava -jar build/libs/datenportal-integrator.jar [--config PATH] doctor|serve|schema OP|call OP [--json JSON|--args-file PATH|-]\nWeitere Helfer: setup-tools, start-datasheet, harness-config, codex");
        return 0;
      }
      String command = args.removeFirst();
      if (command.equals("schema")) {
        var op = Operations.ALL.get(args.getFirst());
        if (op == null) throw new Problem("unknown_operation", "Operation ist nicht bekannt.");
        print(
            Json.map(
                "operation",
                args.getFirst(),
                "description",
                op.description(),
                "inputSchema",
                op.schema()));
        return 0;
      }
      Settings s = Settings.load(config);
      if (command.equals("serve")) {
        serve(s);
        return 0;
      }
      if (Set.of(
              "setup-java",
              "setup-tools",
              "setup-gretl",
              "start-datasheet",
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
      print(new Workflow(s).call(operation, Json.read(raw)));
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
            Runtime.version().feature() == 25,
            "topics_repo",
            Files.isDirectory(s.topics),
            "stack_repo",
            Files.isDirectory(s.stack),
            "configuration_fingerprint",
            s.fingerprint());
    try {
      checks.put("stack", new Stack(s, new ProcessRunner()).inspect());
    } catch (Problem e) {
      checks.put("stack", e.result());
    }
    try {
      checks.put(
          "datasheet_mcp", !McpClients.datasheet(s).call("describe_schema", Json.map()).isEmpty());
    } catch (Problem e) {
      checks.put("datasheet_mcp", e.result());
    }
    var interlis = Json.obj(s.values.get("interlis"));
    checks.put("interlis_configured", !interlis.isEmpty());
    return checks;
  }

  public static void serve(Settings s) throws InterruptedException {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    var ended = new java.util.concurrent.CountDownLatch(1);
    var input =
        new java.io.FilterInputStream(System.in) {
          @Override
          public int read() throws java.io.IOException {
            int result = super.read();
            if (result < 0) ended.countDown();
            return result;
          }

          @Override
          public int read(byte[] b, int off, int len) throws java.io.IOException {
            int result = super.read(b, off, len);
            if (result < 0) ended.countDown();
            return result;
          }
        };
    var mapper = new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper());
    var transport = new StdioServerTransportProvider(mapper, input, System.out);
    var workflow = new Workflow(s);
    var tools = new ArrayList<McpServerFeatures.SyncToolSpecification>();
    Operations.ALL.forEach(
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
                            Object result = workflow.call(name, request.arguments());
                            return McpSchema.CallToolResult.builder()
                                .structuredContent(result)
                                .addTextContent(Json.text(result))
                                .isError(false)
                                .build();
                          } catch (Problem e) {
                            return McpSchema.CallToolResult.builder()
                                .structuredContent(e.result())
                                .addTextContent(Json.text(e.result()))
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
            .serverInfo("datenportal-themenintegrator", "0.2.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .instructions(
                "Menschliche Freigaben nur nach tatsächlicher Antwort protokollieren. Modellableitung ausdrücklich anfordern. Fehlende Benutzer/Office-Fakten erfragen. Bei unklarem Upload vorhandene Laufkennung aufklären.")
            .tools(tools)
            .build();
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    // SDK owns stdin and exits its transport on EOF; wait until input closes.
    ended.await();
    server.close();
  }
}
