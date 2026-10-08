package ch.so.agi.integrator;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.*;
import java.time.Duration;
import java.util.*;

public final class McpClients {
  public interface ToolClient extends AutoCloseable {
    Map<String, Object> call(String name, Map<String, Object> args);

    default String sessionKey() {
      return null;
    }

    default Set<String> tools() {
      return Set.of();
    }

    @Override
    default void close() {}
  }

  public static ToolClient datasheet(Settings s) {
    return new Managed(s, "datasheet");
  }

  public static ToolClient interlis(Settings s) {
    return new Managed(s, "interlis");
  }

  static final class Managed implements ToolClient {
    final Settings settings;
    final String service;
    final DockerMcps docker;
    McpSyncClient client;
    String container, session, identity;

    Managed(Settings s, String service) {
      settings = s;
      this.service = service;
      docker = new DockerMcps(s, new ProcessRunner());
    }

    synchronized void connect() {
      var config = settings.mcp(service);
      var selected = config.containsKey("image") ? docker.selected(service, false) : Json.map();
      String expectedIdentity = Json.digest(Json.map("config", config, "runtime", selected));
      if (client != null && !expectedIdentity.equals(identity)) close();
      if (client != null) return;
      try {
        if (ComposeRuntime.managed(settings, service)) {
          new ComposeRuntime(settings, new ProcessRunner()).ensure(service);
          connectManagedHttp(config);
          session = UUID.randomUUID().toString();
          identity = expectedIdentity;
          return;
        }
        McpClientTransport transport;
        if (Json.str(config, "transport", "stdio").equals("http")) {
          transport = http(Json.required(config, "url"));
        } else {
          List<String> command;
          if (config.containsKey("image")) {
            selected = docker.selected(service, true);
            docker.reap();
            container = docker.name(service);
            docker.register(container);
            command = docker.launch(container, Json.required(selected, "image_id"));
          } else {
            var jar = settings.path(Json.required(config, "jar"));
            if (!java.nio.file.Files.isRegularFile(jar))
              throw new Problem(
                  "interlis_unavailable",
                  "Konfiguriertes INTERLIS-MCP-JAR fehlt.",
                  "path",
                  jar.toString());
            command =
                List.of(
                    Json.str(
                        config,
                        "java_command",
                        java.nio.file.Path.of(System.getProperty("java.home"), "bin/java")
                            .toString()),
                    "-jar",
                    jar.toString(),
                    "--spring.profiles.active=stdio");
          }
          String logName = container == null ? service : container;
          var stdio =
              new EofStdioTransport(
                  command,
                  settings.root,
                  new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()),
                  line -> {
                    try {
                      var log = docker.home().resolve("logs").resolve(logName + ".log");
                      java.nio.file.Files.createDirectories(log.getParent());
                      if (!java.nio.file.Files.exists(log)
                          || java.nio.file.Files.size(log) < 1024 * 1024)
                        java.nio.file.Files.writeString(
                            log,
                            line + "\n",
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                    } catch (Exception ignored) {
                    }
                  });
          transport = stdio;
        }
        client =
            McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(settings.timeout))
                .initializationTimeout(Duration.ofSeconds(settings.timeout))
                .build();
        client.initialize();
        session = UUID.randomUUID().toString();
        identity = expectedIdentity;
      } catch (Problem p) {
        close();
        throw p;
      } catch (Exception e) {
        close();
        throw new Problem(
            "mcp_unavailable", "Fach-MCP kann nicht initialisiert werden.", "service", service);
      }
    }

    void connectManagedHttp(Map<String, Object> config) {
      long deadline =
          System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(settings.stackTimeout);
      String exception = "unknown";
      while (System.nanoTime() < deadline) {
        try {
          client =
              McpClient.sync(http(Json.required(config, "url")))
                  .requestTimeout(Duration.ofSeconds(settings.timeout))
                  .initializationTimeout(Duration.ofSeconds(Math.min(10, settings.timeout)))
                  .build();
          client.initialize();
          var available =
              client.listTools().tools().stream()
                  .map(McpSchema.Tool::name)
                  .collect(java.util.stream.Collectors.toSet());
          if (!available.containsAll(expectedTools(service)))
            throw new Problem(
                "runtime_tools_missing",
                "Compose-MCP meldet nicht den erwarteten Werkzeugkatalog.",
                "service",
                service);
          return;
        } catch (Problem e) {
          close();
          throw e;
        } catch (Exception e) {
          exception = e.getClass().getSimpleName();
          close();
          try {
            Thread.sleep(250);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Problem("runtime_interrupted", "MCP-Start unterbrochen.");
          }
        }
      }
      throw new Problem(
          "runtime_start_timeout",
          "MCP-Initialisierung wurde nicht rechtzeitig bereit; Compose-Logs pruefen.",
          "service",
          service,
          "exception",
          exception);
    }

    @Override
    public synchronized String sessionKey() {
      connect();
      return session;
    }

    @Override
    public synchronized Set<String> tools() {
      connect();
      try {
        return client.listTools().tools().stream()
            .map(McpSchema.Tool::name)
            .collect(java.util.stream.Collectors.toSet());
      } catch (Exception e) {
        close();
        throw new Problem(
            "mcp_unavailable", "MCP-Werkzeugkatalog nicht erreichbar.", "service", service);
      }
    }

    @Override
    public synchronized Map<String, Object> call(String name, Map<String, Object> args) {
      connect();
      if (ComposeRuntime.managed(settings, service)) {
        var actual = new ComposeRuntime(settings, new ProcessRunner()).inspect(service);
        if (!"running".equals(actual.get("state"))) {
          close();
          throw new Problem(
              "runtime_unhealthy",
              "Compose-MCP wurde waehrend der Sitzung angehalten.",
              "service",
              service);
        }
      }
      try {
        return result(client.callTool(new McpSchema.CallToolRequest(name, args)));
      } catch (Problem p) {
        throw p;
      } catch (Exception e) {
        close();
        throw new Problem(
            "mcp_unavailable",
            "Fach-MCP-Aufruf nicht bestätigt.",
            "operation",
            name,
            "service",
            service);
      }
    }

    @Override
    public synchronized void close() {
      if (client != null) {
        try {
          client.closeGracefully();
        } catch (Exception ignored) {
        }
        client = null;
      }
      if (container != null) {
        docker.stopped(container);
        container = null;
      }
      session = null;
    }
  }

  static Set<String> expectedTools(String service) {
    return service.equals("datasheet")
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
        : Set.of("authorIliModel", "applyIliModelChanges", "reviewIliModel", "reviewIliChange");
  }

  private static McpClientTransport http(String url) {
    var u = Settings.url(url);
    String base = u.getScheme() + "://" + u.getRawAuthority();
    String endpoint = u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
    return HttpClientStreamableHttpTransport.builder(base)
        .endpoint(endpoint)
        .openConnectionOnStartup(false)
        .build();
  }

  private static Map<String, Object> result(McpSchema.CallToolResult result) {
    Object values = result.structuredContent();
    if (values == null) {
      String joined =
          result.content().stream()
              .filter(c -> c instanceof McpSchema.TextContent)
              .map(c -> ((McpSchema.TextContent) c).text())
              .reduce("", (a, b) -> a + b + "\n");
      try {
        values = Json.read(joined);
      } catch (Problem ignored) {
        values = Json.map("message", joined.substring(0, Math.min(4096, joined.length())));
      }
    }
    var m = Json.obj(values);
    if (Boolean.TRUE.equals(result.isError()))
      throw new Problem(
          Json.str(m, "code", "mcp_error"), "Fach-MCP meldet einen Fehler.", "response", m);
    return m;
  }

  public static Map<String, Object> create(
      ToolClient client, String kind, Map<String, Object> values, Map<String, Object> run) {
    String tag =
        switch (kind) {
          case "dataset" -> "Dataset";
          case "series" -> "DatasetSeries";
          default -> throw new Problem("invalid_kind", "kind muss dataset oder series sein.");
        };
    String shell =
        "<transfer xmlns=\""
            + Xml.ILI
            + "\" xmlns:i=\""
            + Xml.ILI
            + "\" xmlns:d=\""
            + Xml.SHEET
            + "\"><headersection><models><model>SO_AGI_DataCatalog_Datasheet_20260523</model><model>SO_AGI_DataCatalog_Base_20260529</model></models><sender>datenportal-integrator</sender></headersection><datasection><d:Metadata i:bid=\"b"
            + UUID.randomUUID().toString().replace("-", "")
            + "\"><d:"
            + tag
            + " i:tid=\"o"
            + UUID.randomUUID().toString().replace("-", "")
            + "\"/></d:Metadata></datasection></transfer>";
    run.put("draft_base", shell);
    var current = client.call("import_xtf", Json.map("xml", shell));
    if (!values.isEmpty())
      current =
          client.call(
              "update_metadata",
              Json.map(
                  "draft_id",
                  current.get("draft_id"),
                  "expected_revision",
                  current.get("revision"),
                  "values",
                  values));
    return current;
  }

  public static Map<String, Object> restore(ToolClient client, Map<String, Object> run) {
    var snapshot = Json.obj(run.get("draft"));
    var target = Json.obj(snapshot.get("data"));
    var current =
        run.containsKey("draft_base")
            ? client.call("import_xtf", Json.map("xml", run.get("draft_base")))
            : create(client, Json.required(snapshot, "kind"), Json.map(), run);
    for (Object i : new ArrayList<>(Json.list(Json.obj(current.get("data")).get("issues"))))
      current =
          change(
              client, current, "remove_issue", Json.map("issue_id", Json.obj(i).get("issue_id")));
    for (Object a : new ArrayList<>(Json.list(Json.obj(current.get("data")).get("attributes"))))
      current =
          change(
              client,
              current,
              "remove_attribute",
              Json.map("attribute_id", Json.obj(a).get("attribute_id")));
    var meta = Json.map();
    for (String k : Json.obj(current.get("data")).keySet())
      if (!Set.of("attributes", "issues").contains(k) && !target.containsKey(k)) meta.put(k, null);
    target.forEach(
        (k, v) -> {
          if (!Set.of("attributes", "issues").contains(k)) meta.put(k, v);
        });
    if (meta.containsKey("contact_point"))
      current =
          change(
              client,
              current,
              "update_metadata",
              Json.map("values", Json.map("contact_point", null)));
    current = change(client, current, "update_metadata", Json.map("values", meta));
    for (Object o : Json.list(target.get("attributes"))) {
      var a = new LinkedHashMap<>(Json.obj(o));
      a.remove("attribute_id");
      current = change(client, current, "upsert_attribute", Json.map("values", a));
    }
    for (Object o : Json.list(target.get("issues"))) {
      var i = new LinkedHashMap<>(Json.obj(o));
      i.remove("issue_id");
      i.remove("attributes");
      current = change(client, current, "upsert_issue", Json.map("values", i));
      var added =
          Json.list(Json.obj(current.get("data")).get("issues")).stream()
              .map(Json::obj)
              .filter(v -> Objects.equals(v.get("identifier"), i.get("identifier")))
              .findFirst()
              .orElseThrow();
      for (Object a : Json.list(Json.obj(o).get("attributes"))) {
        var values = new LinkedHashMap<>(Json.obj(a));
        values.remove("attribute_id");
        current =
            change(
                client,
                current,
                "upsert_attribute",
                Json.map("issue_id", added.get("issue_id"), "values", values));
      }
    }
    return current;
  }

  private static Map<String, Object> change(
      ToolClient c, Map<String, Object> current, String op, Map<String, Object> args) {
    var a =
        Json.map("draft_id", current.get("draft_id"), "expected_revision", current.get("revision"));
    a.putAll(args);
    return c.call(op, a);
  }
}
