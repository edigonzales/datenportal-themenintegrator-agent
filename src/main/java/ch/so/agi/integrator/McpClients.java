package ch.so.agi.integrator;

import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.*;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.*;
import java.time.Duration;
import java.util.*;

public final class McpClients {
  public interface ToolClient {
    Map<String, Object> call(String name, Map<String, Object> args);
  }

  public static ToolClient datasheet(Settings s) {
    return (name, args) ->
        call(
            http(Json.str(s.values, "datasheet_mcp_url", "http://127.0.0.1:8000/mcp")),
            name,
            args,
            s.timeout);
  }

  public static ToolClient interlis(Settings s) {
    return (name, args) -> {
      var c = Json.obj(s.values.get("interlis"));
      String transport = Json.str(c, "transport", "stdio");
      if (transport.equals("http"))
        return call(http(Json.required(c, "url")), name, args, s.timeout);
      if (!transport.equals("stdio"))
        throw new Problem("invalid_configuration", "INTERLIS-Transport muss stdio oder http sein.");
      var jar = s.path(Json.required(c, "jar"));
      if (!java.nio.file.Files.isRegularFile(jar))
        throw new Problem(
            "interlis_unavailable",
            "Konfiguriertes INTERLIS-MCP-JAR fehlt.",
            "path",
            jar.toString());
      var cmd =
          ServerParameters.builder(
                  Json.str(
                      c,
                      "java_command",
                      java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java")
                          .toString()))
              .args("-jar", jar.toString(), "--spring.profiles.active=stdio")
              .build();
      var stdio =
          new StdioClientTransport(
              cmd, new JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
      stdio.setStdErrorHandler(line -> {});
      return call(stdio, name, args, s.timeout);
    };
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

  private static Map<String, Object> call(
      McpClientTransport transport, String name, Map<String, Object> args, int timeout) {
    try (var client =
        McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(timeout))
            .initializationTimeout(Duration.ofSeconds(timeout))
            .build()) {
      client.initialize();
      var result = client.callTool(new McpSchema.CallToolRequest(name, args));
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
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("mcp_unavailable", "Fach-MCP-Aufruf nicht bestätigt.", "operation", name);
    }
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
