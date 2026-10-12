package ch.so.agi.integrator;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Offline response replay for skill decisions, not an integration or human approval test. */
final class SkillReplay {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]).toRealPath();
    if (!Files.isRegularFile(root.resolve("SYNTHETIC_TEST_FIXTURE")))
      throw new IllegalArgumentException("Only marked synthetic fixtures may be replayed");
    Path responses = args.length > 1 ? Path.of(args[1]).toRealPath() : root.resolve("responses");
    if (args.length > 1 && !Files.isRegularFile(responses.resolve("SYNTHETIC_TEST_FIXTURE")))
      throw new IllegalArgumentException("Only marked recorded responses may be replayed");
    var ended = new CountDownLatch(1);
    var input =
        new java.io.FilterInputStream(System.in) {
          @Override
          public int read() throws java.io.IOException {
            int result = super.read();
            if (result < 0) ended.countDown();
            return result;
          }

          @Override
          public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
            int result = super.read(bytes, offset, length);
            if (result < 0) ended.countDown();
            return result;
          }
        };
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    var transport =
        new StdioServerTransportProvider(
            new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()),
            input,
            System.out);
    var specifications = new ArrayList<McpServerFeatures.SyncToolSpecification>();
    Operations.ALL.forEach(
        (name, operation) ->
            specifications.add(
                McpServerFeatures.SyncToolSpecification.builder()
                    .tool(
                        McpSchema.Tool.builder()
                            .name(name)
                            .description(operation.description())
                            .inputSchema(operation.schema())
                            .build())
                    .callHandler(
                        (exchange, request) -> {
                          Object result;
                          boolean error = false;
                          try {
                            Files.writeString(
                                root.resolve("calls.jsonl"),
                                Json.text(
                                        Json.map(
                                            "operation", name, "arguments", request.arguments()))
                                    + "\n",
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND);
                            Operations.validate(name, request.arguments());
                            String response = name;
                            if (name.equals("metadata"))
                              response += "-" + Json.required(request.arguments(), "operation");
                            Path file = Json.inside(responses, response + ".json");
                            if (!Files.isRegularFile(file))
                              throw new Problem(
                                  "unexpected_fixture_operation",
                                  "No recorded response",
                                  "operation",
                                  response);
                            result = Json.read(file);
                            Path artifacts = responses.resolve("artifacts").resolve(response);
                            if (Files.isDirectory(artifacts)) Workspace.copy(artifacts, root);
                          } catch (Problem problem) {
                            result = problem.result();
                            error = true;
                          } catch (Exception exception) {
                            result = Json.map("error", "fixture_failed");
                            error = true;
                          }
                          return McpSchema.CallToolResult.builder()
                              .structuredContent(result)
                              .addTextContent(Json.text(result))
                              .isError(error)
                              .build();
                        })
                    .build()));
    var server =
        McpServer.sync(transport)
            .serverInfo("synthetic_skill_replay", "1")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(specifications)
            .build();
    try {
      ended.await();
    } finally {
      server.closeGracefully();
    }
  }
}
