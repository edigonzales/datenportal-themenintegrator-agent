package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.tomlj.Toml;

/** Client restrictions belong only to installed operator workspaces. */
final class OperatorPolicy {
  static final String INSTRUCTIONS =
      "Fachlicher Datenportal-Agent. Verwende die drei Skills, öffentliche MCP-Schemas und Prüfberichte. Keine Implementierungsquellen lesen, suchen oder rekonstruieren. Fehlende Verträge mit datenportal_integrator.operation_schema klären. Dateioperationen über datenportal_workspace; Freigaben ausschliesslich nach tatsächlichen menschlichen Antworten.";

  static String codex(String text, Harness h) {
    checkCodexLayers(h.s.root, text);
    var original = Toml.parse(text);
    String previous = original.getString("mcp_servers.datenportal_workspace.command");
    if (previous != null && !Harness.known(previous, h.launcher()))
      throw conflict("codex.mcp_servers.datenportal_workspace.command");
    text = removeField(text, "permissions.datenportal.filesystem", h.s.root.toString());
    text = section(text, "", Json.map("default_permissions", "datenportal"));
    text =
        section(
            text,
            "features",
            Json.map(
                "shell_tool",
                false,
                "unified_exec",
                false,
                "js_repl",
                false,
                "apps",
                false,
                "remote_plugin",
                false));
    text = section(text, "agents", Json.map("enabled", false));
    text = section(text, "permissions.datenportal.filesystem", filesystem(h.s));
    text = section(text, "permissions.datenportal.network", Json.map("enabled", false));
    for (String name : foreignServers(h.s.root, text)) {
      var disabled = Json.map("enabled", false);
      var existing = Toml.parse(text).getTable("mcp_servers." + Json.text(name));
      // Codex validates each server before applying enabled=false. An override
      // replaces the inherited transport, so a complete inert definition is needed.
      if (existing == null || (!existing.contains("command") && !existing.contains("url"))) {
        disabled.put("command", "bin/datenportal-agent");
        disabled.put("args", List.of("--help"));
      }
      text = section(text, serverSection(text, name), disabled);
    }
    text =
        section(
            text,
            "mcp_servers.datenportal_workspace",
            Json.map(
                "command",
                "bin/datenportal-agent",
                "args",
                h.projectWorkspaceArguments(),
                "startup_timeout_sec",
                180,
                "tool_timeout_sec",
                1800,
                "required",
                true));
    text = section(text, "mcp_servers.datenportal_integrator", Json.map("required", true));
    text =
        section(
            text, "mcp_servers.datenportal_workspace.env", Json.map("DATENPORTAL_FORWARD_ENV", ""));
    if (Toml.parse(text).hasErrors()) throw conflict("codex.generated");
    return text;
  }

  static void checkCodexLayers(Path root, String project) {
    checkSandbox(project, "project");
    String home =
        System.getenv().getOrDefault("DATENPORTAL_CLIENT_HOME", System.getProperty("user.home"));
    Path global =
        Path.of(
            System.getenv()
                .getOrDefault(
                    "DATENPORTAL_CLIENT_CONFIG", Path.of(home, ".codex/config.toml").toString()));
    if (Files.isRegularFile(global) && !global.equals(root.resolve(".codex/config.toml")))
      checkSandbox(Json.contents(global), "user");
    for (Path parent = root.getParent(); parent != null; parent = parent.getParent()) {
      Path config = parent.resolve(".codex/config.toml");
      if (Files.isRegularFile(config) && !config.equals(global))
        checkSandbox(Json.contents(config), "parent");
    }
  }

  static void checkSandbox(String text, String layer) {
    var parsed = Toml.parse(text);
    if (parsed.hasErrors()) throw conflict("codex." + layer);
    for (String key : List.of("sandbox_mode", "sandbox_workspace_write"))
      if (parsed.contains(key)) throw conflict("codex." + layer + "." + key);
    if (!layer.equals("project") && parsed.contains("permissions.datenportal"))
      throw conflict("codex." + layer + ".permissions.datenportal");
  }

  static Problem conflict(String field) {
    return new Problem(
        "operator_policy_conflict",
        "Betriebsprofil kann nicht sicher angewendet werden; widersprüchliche Clienteinstellung bewusst abgleichen.",
        "field",
        field);
  }

  static Map<String, Object> filesystem(Settings s) {
    var result = Json.map(":root", "deny", ":minimal", "read", ":workspace_roots", "deny");
    for (String path : List.of("AGENTS.md", "README.md", "skills", ".agents", "docs"))
      result.put(s.root.resolve(path).toString(), "read");
    return result;
  }

  static Set<String> foreignServers(Path root, String project) {
    var names = new TreeSet<String>();
    var contents = new ArrayList<String>(List.of(project));
    String home =
        System.getenv().getOrDefault("DATENPORTAL_CLIENT_HOME", System.getProperty("user.home"));
    Path global =
        Path.of(
            System.getenv()
                .getOrDefault(
                    "DATENPORTAL_CLIENT_CONFIG", Path.of(home, ".codex/config.toml").toString()));
    if (Files.isRegularFile(global)) contents.add(Json.contents(global));
    for (Path parent = root.getParent(); parent != null; parent = parent.getParent()) {
      Path config = parent.resolve(".codex/config.toml");
      if (Files.isRegularFile(config) && !config.equals(global))
        contents.add(Json.contents(config));
    }
    for (String content : contents) {
      var table = Toml.parse(content).getTable("mcp_servers");
      if (table != null) names.addAll(table.keySet());
    }
    names.removeAll(Set.of("datenportal_integrator", "datenportal_workspace"));
    return names;
  }

  static String removeField(String text, String section, String key) {
    var header = Pattern.compile("(?m)^\\[" + Pattern.quote(section) + "]\\s*$").matcher(text);
    if (!header.find()) return text;
    int start = header.end();
    var next = Pattern.compile("(?m)^\\[").matcher(text);
    int end = next.find(start) ? next.start() : text.length();
    String block =
        text.substring(start, end)
            .replaceAll("(?m)^" + Pattern.quote(Json.text(key)) + "\\s*=.*$", "");
    return text.substring(0, start) + block + text.substring(end);
  }

  static String serverSection(String text, String name) {
    String bare = "mcp_servers." + name;
    if (name.matches("[A-Za-z0-9_-]+")
        && Pattern.compile("(?m)^\\[" + Pattern.quote(bare) + "]\\s*$").matcher(text).find())
      return bare;
    return "mcp_servers." + Json.text(name);
  }

  static String section(String text, String name, Map<String, Object> values) {
    var header = Pattern.compile("(?m)^\\[" + Pattern.quote(name) + "]\\s*$").matcher(text);
    int start, end;
    boolean exists = !name.isEmpty() && header.find();
    if (name.isEmpty()) start = 0;
    else start = exists ? header.end() : text.length();
    var next = Pattern.compile("(?m)^\\[").matcher(text);
    end = next.find(start) ? next.start() : text.length();
    String block = text.substring(start, end);
    for (var entry : values.entrySet()) {
      String key = entry.getKey();
      var field =
          Pattern.compile(
                  "(?m)^(?:"
                      + Pattern.quote(key)
                      + "|"
                      + Pattern.quote(Json.text(key))
                      + ")\\s*=.*$")
              .matcher(block);
      String line = Json.text(key) + " = " + Json.text(entry.getValue());
      if (field.find()) {
        if (field.group().contains("[") && !field.group().contains("]"))
          throw conflict(name + "." + key);
        block = field.replaceFirst(Matcher.quoteReplacement(line));
      } else block += "\n" + line + "\n";
    }
    return exists || name.isEmpty()
        ? text.substring(0, start) + block + text.substring(end)
        : text + "\n[" + name + "]\n" + block;
  }

  static Map<String, Object> openCode(Map<String, Object> json) {
    json.put("default_agent", "datenportal");
    var agents = Harness.objectField(json, "agents", "agents");
    var agent = Harness.objectField(agents, "datenportal", "agents.datenportal");
    agent.put("mode", "primary");
    agent.put("description", "Datenportal-Fachabläufe über Skills und MCP ohne Quellcodesuche");
    agent.put("system", INSTRUCTIONS);
    agents.put("datenportal", agent);
    json.put("agents", agents);
    var rules = new ArrayList<>(Json.list(json.get("permissions")));
    var managedRules = new ArrayList<Object>();
    var policies = new ArrayList<Object>();
    managedRules.add(Json.map("action", "*", "resource", "*", "effect", "deny"));
    policies.add(Json.map("action", "permission", "resource", "*", "effect", "deny"));
    for (String action : List.of("shell", "glob", "grep", "subagent", "edit", "execute", "read")) {
      managedRules.add(Json.map("action", action, "resource", "*", "effect", "deny"));
      policies.add(Json.map("action", "permission", "resource", action + ":*", "effect", "deny"));
    }
    for (String path :
        List.of("AGENTS.md", "README.md", "skills/*", ".agents/skills/*", "docs/*")) {
      managedRules.add(Json.map("action", "read", "resource", path, "effect", "allow"));
      policies.add(Json.map("action", "permission", "resource", "read:" + path, "effect", "allow"));
    }
    for (String action :
        List.of("skill", "question", "datenportal_integrator_*", "datenportal_workspace_*")) {
      managedRules.add(Json.map("action", action, "resource", "*", "effect", "allow"));
      policies.add(Json.map("action", "permission", "resource", action + ":*", "effect", "allow"));
    }
    // Idempotent: managed rules replace their previous identical values.
    rules.removeAll(managedRules);
    rules.addAll(managedRules);
    json.put("permissions", rules);
    var experimental = Harness.objectField(json, "experimental", "experimental");
    var old = new ArrayList<>(Json.list(experimental.get("policies")));
    old.removeAll(policies);
    old.addAll(policies);
    experimental.put("policies", old);
    json.put("experimental", experimental);
    return json;
  }

  static Object check(Settings s) {
    if (!OperatorWorkspace.installed(s.root)) return Json.map("operator", false);
    var manifest = Json.read(s.root.resolve(OperatorWorkspace.MANIFEST));
    if (manifest.containsKey("version") && !Main.version().equals(manifest.get("version")))
      throw new Problem(
          "operator_package_version_mismatch",
          "Programm und Betriebspaket haben unterschiedliche Versionen; workspace update vor init ausführen.");
    Path codex = s.root.resolve(".codex/config.toml"), open = s.root.resolve("opencode.json");
    if (!Files.isRegularFile(codex) || !Files.isRegularFile(open))
      throw conflict("client_configuration_missing");
    String text = Json.contents(codex);
    checkCodexLayers(s.root, text);
    var parsed = Toml.parse(text);
    for (String name : foreignServers(s.root, text))
      if (!Boolean.FALSE.equals(parsed.getBoolean("mcp_servers." + Json.text(name) + ".enabled")))
        throw conflict("codex.additional_mcp_server");
    if (!"datenportal".equals(parsed.getString("default_permissions"))
        || !Boolean.FALSE.equals(parsed.getBoolean("features.shell_tool"))
        || !Boolean.FALSE.equals(parsed.getBoolean("features.unified_exec"))
        || !Boolean.FALSE.equals(parsed.getBoolean("features.js_repl"))
        || !Boolean.FALSE.equals(parsed.getBoolean("features.apps"))
        || !Boolean.FALSE.equals(parsed.getBoolean("features.remote_plugin"))
        || !Boolean.FALSE.equals(parsed.getBoolean("agents.enabled"))
        || !Boolean.FALSE.equals(parsed.getBoolean("permissions.datenportal.network.enabled"))
        || !Boolean.TRUE.equals(parsed.getBoolean("mcp_servers.datenportal_integrator.required"))
        || !Boolean.TRUE.equals(parsed.getBoolean("mcp_servers.datenportal_workspace.required"))
        || !"deny"
            .equals(parsed.getString("permissions.datenportal.filesystem.\":workspace_roots\""))
        || filesystem(s).entrySet().stream()
            .anyMatch(
                e ->
                    !e.getValue()
                        .equals(
                            parsed.getString(
                                "permissions.datenportal.filesystem." + Json.text(e.getKey()))))
        || !"deny".equals(parsed.getString("permissions.datenportal.filesystem.\":root\"")))
      throw conflict("codex.operator_profile");
    var actualFilesystem = parsed.getTable("permissions.datenportal.filesystem");
    if (actualFilesystem != null)
      for (String key : actualFilesystem.keySet())
        if (!filesystem(s).containsKey(key) && !"deny".equals(actualFilesystem.get(key)))
          throw conflict("codex.additional_filesystem_grant");
    var configured = Json.read(open);
    if (!"datenportal".equals(configured.get("default_agent")))
      throw conflict("opencode.default_agent");
    var expected = openCode(Json.read(Json.text(configured)));
    if (!Objects.equals(expected.get("permissions"), configured.get("permissions"))
        || !Objects.equals(expected.get("experimental"), configured.get("experimental")))
      throw conflict("opencode.permissions");
    return Json.map(
        "operator",
        true,
        "configuration_valid",
        true,
        "client_enforcement",
        "Separat mit tatsächlichen Codex-/OpenCode-Sitzungen prüfen; Konfigurationsprüfung ist kein GUI-Nachweis.");
  }
}
