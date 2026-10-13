package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.tomlj.Toml;

/** Update only the known agent entry; preserve unrelated settings and secret mappings. */
final class Harness {
  final Settings s;

  Harness(Settings s) {
    this.s = s;
  }

  String launcher() {
    return s.root.resolve("bin/datenportal-agent").toString();
  }

  List<String> codexArguments() {
    var args = new ArrayList<>(List.of("-C", s.root.toString()));
    if (OperatorWorkspace.installed(s.root)) {
      OperatorPolicy.check(s);
      args.add("--strict-config");
      args.addAll(
          List.of(
              "-c",
              "features.shell_tool=false",
              "-c",
              "features.unified_exec=false",
              "-c",
              "features.js_repl=false",
              "-c",
              "features.apps=false",
              "-c",
              "features.remote_plugin=false",
              "-c",
              "default_permissions=\"datenportal\"",
              "-c",
              "permissions.datenportal.filesystem.\":root\"=\"deny\"",
              "-c",
              "permissions.datenportal.filesystem.\":minimal\"=\"read\"",
              "-c",
              "permissions.datenportal.filesystem.\":workspace_roots\"=\"deny\"",
              "-c",
              "permissions.datenportal.network.enabled=false",
              "-c",
              "agents.enabled=false"));
      OperatorPolicy.filesystem(s)
          .forEach(
              (key, value) ->
                  args.addAll(
                      List.of(
                          "-c",
                          "permissions.datenportal.filesystem."
                              + Json.text(key)
                              + "="
                              + Json.text(value))));
      args.addAll(
          List.of(
              "-c",
              "mcp_servers.datenportal_workspace.command=" + Json.text(launcher()),
              "-c",
              "mcp_servers.datenportal_workspace.args=" + Json.text(workspaceArguments()),
              "-c",
              "mcp_servers.datenportal_workspace.startup_timeout_sec=180",
              "-c",
              "mcp_servers.datenportal_workspace.tool_timeout_sec=1800",
              "-c",
              "mcp_servers.datenportal_workspace.required=true",
              "-c",
              "mcp_servers.datenportal_workspace.env.DATENPORTAL_FORWARD_ENV=\"\""));
      for (String name :
          OperatorPolicy.foreignServers(
              s.root, Json.contents(s.root.resolve(".codex/config.toml"))))
        args.addAll(List.of("-c", "mcp_servers." + Json.text(name) + ".enabled=false"));
    }
    entry()
        .forEach(
            (key, value) -> {
              if (value instanceof Map<?, ?> map) {
                map.forEach(
                    (k, v) ->
                        args.addAll(
                            List.of(
                                "-c",
                                "mcp_servers.datenportal_integrator."
                                    + key
                                    + "."
                                    + k
                                    + "="
                                    + Json.text(v))));
              } else
                args.addAll(
                    List.of(
                        "-c",
                        "mcp_servers.datenportal_integrator." + key + "=" + Json.text(value)));
            });
    return args;
  }

  List<String> workspaceArguments() {
    var result = new ArrayList<>(arguments());
    result.set(result.size() - 1, "serve-workspace");
    return result;
  }

  List<String> projectWorkspaceArguments() {
    var result = new ArrayList<>(projectArguments());
    result.set(result.size() - 1, "serve-workspace");
    return result;
  }

  List<String> arguments() {
    var args = new ArrayList<String>();
    String options = System.getenv("DATENPORTAL_LAUNCH_OPTIONS");
    if (options != null && !options.isBlank()) args.addAll(List.of(options.split("\n")));
    args.addAll(List.of("--config", s.file.toString(), "serve"));
    return args;
  }

  List<String> projectArguments() {
    var args = new ArrayList<>(arguments());
    if (s.file.startsWith(s.root))
      args.set(args.indexOf("--config") + 1, s.root.relativize(s.file).toString());
    return args;
  }

  List<String> credentials() {
    var names = new LinkedHashSet<String>();
    Json.obj(s.values.get("environments")).values().stream()
        .map(Json::obj)
        .forEach(
            env -> {
              for (String key : List.of("username_env", "token_env")) {
                String name = Json.str(env, key, "");
                if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) names.add(name);
              }
            });
    for (String name : System.getenv().getOrDefault("DATENPORTAL_FORWARD_ENV", "").split("\\s+"))
      if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) names.add(name);
    return new ArrayList<>(names);
  }

  Map<String, Object> entry() {
    var result =
        Json.map(
            "command",
            launcher(),
            "args",
            arguments(),
            "startup_timeout_sec",
            180,
            "tool_timeout_sec",
            1800,
            "env_vars",
            credentials(),
            "env",
            Json.map("DATENPORTAL_FORWARD_ENV", String.join(" ", credentials())));
    if (OperatorWorkspace.installed(s.root)) result.put("required", true);
    return result;
  }

  Map<String, Object> proposal() {
    var result =
        Json.map(
            "codex",
            Json.map("mcp_servers", Json.map("datenportal_integrator", entry())),
            "opencode",
            Json.map(
                "mcp",
                Json.map(
                    "servers",
                    Json.map(
                        "datenportal_integrator",
                        Json.map(
                            "type",
                            "local",
                            "command",
                            Setup.concat(List.of(launcher()), arguments()),
                            "disabled",
                            false,
                            "timeout",
                            openCodeTimeouts(),
                            "environment",
                            Json.map(
                                "DATENPORTAL_FORWARD_ENV", String.join(" ", credentials())))))));
    if (OperatorWorkspace.installed(s.root)) {
      var codex = Json.obj(result.get("codex"));
      codex.put("default_permissions", "datenportal");
      codex.put(
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
      codex.put("agents", Json.map("enabled", false));
      codex.put(
          "permissions",
          Json.map(
              "datenportal",
              Json.map(
                  "filesystem",
                  OperatorPolicy.filesystem(s),
                  "network",
                  Json.map("enabled", false))));
      Json.obj(Json.obj(result.get("codex")).get("mcp_servers"))
          .put(
              "datenportal_workspace",
              Json.map(
                  "command",
                  launcher(),
                  "args",
                  workspaceArguments(),
                  "startup_timeout_sec",
                  180,
                  "tool_timeout_sec",
                  1800,
                  "required",
                  true,
                  "env",
                  Json.map("DATENPORTAL_FORWARD_ENV", "")));
      for (String name :
          OperatorPolicy.foreignServers(
              s.root,
              Files.isRegularFile(s.root.resolve(".codex/config.toml"))
                  ? Json.contents(s.root.resolve(".codex/config.toml"))
                  : ""))
        Json.obj(codex.get("mcp_servers"))
            .put(
                name, Json.map("enabled", false, "command", launcher(), "args", List.of("--help")));
      var open = Json.obj(result.get("opencode"));
      Json.obj(Json.obj(open.get("mcp")).get("servers"))
          .values()
          .forEach(v -> Json.obj(v).put("codemode", false));
      Json.obj(Json.obj(open.get("mcp")).get("servers"))
          .put(
              "datenportal_workspace",
              Json.map(
                  "type",
                  "local",
                  "command",
                  Setup.concat(List.of(launcher()), workspaceArguments()),
                  "codemode",
                  false,
                  "disabled",
                  false,
                  "timeout",
                  openCodeTimeouts(),
                  "environment",
                  Json.map("DATENPORTAL_FORWARD_ENV", "")));
      OperatorPolicy.openCode(open);
    }
    return result;
  }

  static Map<String, Object> openCodeTimeouts() {
    return Json.map("startup", 180000, "catalog", 1800000, "execution", 1800000);
  }

  static Problem openCodeConflict(String field) {
    return new Problem(
        "harness_conflict", "OpenCode-Konfiguration bewusst abgleichen.", "field", field);
  }

  static Map<String, Object> objectField(Map<String, Object> parent, String key, String field) {
    if (!parent.containsKey(key)) return Json.map();
    if (!(parent.get(key) instanceof Map<?, ?>)) throw openCodeConflict(field);
    return Json.obj(parent.get(key));
  }

  static Map<String, Object> timeoutFields(Object value, String field) {
    if (!(value instanceof Map<?, ?>)) throw openCodeConflict(field);
    var result = Json.obj(value);
    for (var entry : result.entrySet()) {
      if (!Set.of("startup", "catalog", "execution").contains(entry.getKey())
          || !positiveInteger(entry.getValue()))
        throw openCodeConflict(field + "." + entry.getKey());
    }
    return result;
  }

  static boolean positiveInteger(Object value) {
    return (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() > 0;
  }

  // Validate the current entry before updating managed launcher fields.
  Map<String, Object> validateOpenCode(Map<String, Object> entry, String field) {
    if (entry.containsKey("enabled")) throw openCodeConflict(field + ".enabled");
    Object command = entry.get("command");
    if (!(command instanceof List<?> rawParts)
        || rawParts.isEmpty()
        || rawParts.stream().anyMatch(p -> !(p instanceof String text) || text.isBlank()))
      throw openCodeConflict(field + ".command");
    var parts = Json.strings(command);
    if (!known(parts.getFirst(), launcher())) throw openCodeConflict(field + ".command");
    if (entry.containsKey("type") && !"local".equals(entry.get("type")))
      throw openCodeConflict(field + ".type");
    for (String key : List.of("disabled", "codemode"))
      if (entry.containsKey(key) && !(entry.get(key) instanceof Boolean))
        throw openCodeConflict(field + "." + key);
    if (entry.containsKey("cwd") && !(entry.get("cwd") instanceof String))
      throw openCodeConflict(field + ".cwd");
    if (entry.containsKey("protocol")
        && (!(entry.get("protocol") instanceof String protocol)
            || !Set.of("legacy", "auto", "2026-07-28").contains(protocol)))
      throw openCodeConflict(field + ".protocol");
    var env = objectField(entry, "environment", field + ".environment");
    if (env.values().stream().anyMatch(v -> !(v instanceof String)))
      throw openCodeConflict(field + ".environment");
    if (entry.containsKey("timeout")) timeoutFields(entry.get("timeout"), field + ".timeout");
    // These managed fields intentionally follow the current init invocation.
    entry.remove("command");
    entry.put("type", "local");
    return entry;
  }

  Map<String, Object> updateOpenCode(Map<String, Object> original) {
    var json = Json.read(Json.text(original));
    if (json == null) throw openCodeConflict("opencode.json");
    var mcp = objectField(json, "mcp", "mcp");
    for (String key : mcp.keySet())
      if (!Set.of("servers", "timeout").contains(key)) throw openCodeConflict("mcp." + key);
    if (mcp.containsKey("timeout")) timeoutFields(mcp.get("timeout"), "mcp.timeout");
    var servers = objectField(mcp, "servers", "mcp.servers");
    var merged =
        servers.containsKey("datenportal_integrator")
            ? validateOpenCode(
                objectField(
                    servers, "datenportal_integrator", "mcp.servers.datenportal_integrator"),
                "mcp.servers.datenportal_integrator")
            : Json.map();
    merged.put("type", "local");
    merged.put("command", Setup.concat(List.of("bin/datenportal-agent"), projectArguments()));
    merged.putIfAbsent("disabled", false);
    var timeouts = objectField(merged, "timeout", "mcp.servers.datenportal_integrator.timeout");
    openCodeTimeouts().forEach(timeouts::putIfAbsent);
    merged.put("timeout", timeouts);
    var env = objectField(merged, "environment", "mcp.servers.datenportal_integrator.environment");
    mergeForward(env, credentials());
    merged.put("environment", env);
    servers.put("datenportal_integrator", merged);
    if (OperatorWorkspace.installed(s.root)) {
      merged.put("codemode", false);
      var helper =
          servers.containsKey("datenportal_workspace")
              ? validateOpenCode(
                  objectField(
                      servers, "datenportal_workspace", "mcp.servers.datenportal_workspace"),
                  "mcp.servers.datenportal_workspace")
              : Json.map();
      helper.put("type", "local");
      helper.put(
          "command", Setup.concat(List.of("bin/datenportal-agent"), projectWorkspaceArguments()));
      helper.put("codemode", false);
      helper.putIfAbsent("disabled", false);
      helper.putIfAbsent("timeout", openCodeTimeouts());
      helper.put("environment", Json.map("DATENPORTAL_FORWARD_ENV", ""));
      servers.put("datenportal_workspace", helper);
    }
    mcp.put("servers", servers);
    json.put("mcp", mcp);
    return OperatorWorkspace.installed(s.root) ? OperatorPolicy.openCode(json) : json;
  }

  static boolean known(String command, String launcher) {
    return command.equals(launcher) || command.equals("bin/datenportal-agent");
  }

  Map<String, Object> install() {
    Path codex = s.root.resolve(".codex/config.toml"), open = s.root.resolve("opencode.json");
    String toml = Files.exists(codex) ? Json.contents(codex) : "";
    String updated = updateToml(toml);
    Map<String, Object> json;
    try {
      json =
          updateOpenCode(
              Files.exists(open)
                  ? Json.read(open)
                  : Json.map("$schema", "https://opencode.ai/config.json"));
    } catch (Problem e) {
      if (e.code.equals("invalid_json")) throw openCodeConflict("opencode.json");
      throw e;
    }
    // Both candidates are checked before either file is replaced.
    backup(codex);
    backup(open);
    atomicText(codex, updated);
    Json.atomic(open, json);
    return Json.map("codex", codex.toString(), "opencode", open.toString());
  }

  static void mergeForward(Map<String, Object> env, List<String> names) {
    var merged = new LinkedHashSet<>(names);
    env.keySet().stream()
        .filter(name -> !name.equals("DATENPORTAL_FORWARD_ENV"))
        .sorted()
        .forEach(merged::add);
    for (String name : Json.str(env, "DATENPORTAL_FORWARD_ENV", "").split("\\s+"))
      if (!name.isBlank()) merged.add(name);
    env.put("DATENPORTAL_FORWARD_ENV", String.join(" ", merged));
  }

  String updateToml(String text) {
    var parsed = Toml.parse(text);
    if (parsed.hasErrors())
      throw new Problem("harness_conflict", "Codex-Konfiguration ist ungültig.");
    String key = "mcp_servers.datenportal_integrator";
    var table = parsed.getTable(key);
    if (table != null) {
      if (!known(Objects.toString(table.getString("command"), ""), launcher()))
        throw new Problem("harness_conflict", "Unbekannten Codex-MCP-Eintrag bewusst abgleichen.");
    }
    var header =
        Pattern.compile("(?m)^\\[mcp_servers\\.datenportal_integrator]\\s*$").matcher(text);
    if (table != null && !header.find())
      throw new Problem(
          "harness_conflict",
          "MCP-Eintrag verwendet eine nicht automatisch bearbeitbare TOML-Schreibweise.");
    int start = table == null ? text.length() : header.end();
    int end = text.length();
    var next = Pattern.compile("(?m)^\\[").matcher(text);
    if (next.find(start)) end = next.start();
    String block = table == null ? "\n" : text.substring(start, end);
    var values = entry();
    if (table != null)
      for (String field : List.of("startup_timeout_sec", "tool_timeout_sec"))
        if (table.contains(field)) {
          if (!(table.get(field) instanceof Number number)
              || !Double.isFinite(number.doubleValue())
              || number.doubleValue() <= 0)
            throw new Problem("harness_conflict", "Ungültiger Codex-MCP-Timeout.", "field", field);
          values.remove(field);
        }
    values.put("command", "bin/datenportal-agent");
    values.put("args", projectArguments());
    var names = new LinkedHashSet<>(credentials());
    if (table != null && table.getTable("env") != null)
      table.getTable("env").keySet().stream()
          .filter(name -> !name.equals("DATENPORTAL_FORWARD_ENV"))
          .forEach(names::add);
    if (table != null && table.getArray("env_vars") != null)
      table.getArray("env_vars").toList().forEach(n -> names.add(n.toString()));
    String previousForward = parsed.getString(key + ".env.DATENPORTAL_FORWARD_ENV");
    if (previousForward != null)
      for (String name : previousForward.split("\\s+")) if (!name.isBlank()) names.add(name);
    values.put("env_vars", new ArrayList<>(names));
    values.remove("env");
    for (var value : values.entrySet()) {
      // Only single-line existing values are replaced. Multiline TOML is preserved by stopping.
      Pattern field =
          Pattern.compile(
              "(?m)^(?:"
                  + Pattern.quote(value.getKey())
                  + "|"
                  + Pattern.quote(Json.text(value.getKey()))
                  + ")\\s*=.*$");
      var match = field.matcher(block);
      String line = value.getKey() + " = " + Json.text(value.getValue());
      if (match.find()) {
        String old = match.group();
        if (old.contains("[") && !old.contains("]"))
          throw new Problem("harness_conflict", "Mehrzeiligen MCP-Eintrag bewusst abgleichen.");
        block = match.replaceFirst(Matcher.quoteReplacement(line));
      } else block += "\n" + line + "\n";
    }
    String result =
        table == null
            ? text + "\n[" + key + "]\n" + block
            : text.substring(0, start) + block + text.substring(end);
    // Environment is an independently preserved child table.
    String envHeader = "[" + key + ".env]";
    var envMatch = Pattern.compile("(?m)^" + Pattern.quote(envHeader) + "\\s*$").matcher(result);
    String forward = "DATENPORTAL_FORWARD_ENV = " + Json.text(String.join(" ", names));
    if (envMatch.find()) {
      int eStart = envMatch.end(), eEnd = result.length();
      var boundary = Pattern.compile("(?m)^\\[").matcher(result);
      if (boundary.find(eStart)) eEnd = boundary.start();
      String envBlock = result.substring(eStart, eEnd);
      var oldForward = parsed.getString(key + ".env.DATENPORTAL_FORWARD_ENV");
      if (oldForward != null)
        for (String n : oldForward.split("\\s+")) if (!n.isBlank()) names.add(n);
      forward = "DATENPORTAL_FORWARD_ENV = " + Json.text(String.join(" ", names));
      var fm = Pattern.compile("(?m)^DATENPORTAL_FORWARD_ENV\\s*=.*$").matcher(envBlock);
      envBlock =
          fm.find()
              ? fm.replaceFirst(Matcher.quoteReplacement(forward))
              : envBlock + "\n" + forward + "\n";
      result = result.substring(0, eStart) + envBlock + result.substring(eEnd);
    } else {
      if (table != null && table.contains("env"))
        throw new Problem("harness_conflict", "Inline-MCP-Umgebung bewusst abgleichen.");
      result += "\n" + envHeader + "\n" + forward + "\n";
    }
    if (Toml.parse(result).hasErrors())
      throw new Problem("harness_conflict", "Generierte Konfiguration ist nicht gültig.");
    return OperatorWorkspace.installed(s.root) ? OperatorPolicy.codex(result, this) : result;
  }

  void backup(Path path) {
    if (!Files.exists(path)) return;
    try {
      Path dir = s.root.resolve(".datenportal-integrator/harness-backups");
      Files.createDirectories(dir);
      Path dest =
          dir.resolve(
              path.getParent().getFileName() + "-" + path.getFileName() + "-" + Json.sha(path));
      if (!Files.exists(dest)) Files.copy(path, dest);
      try {
        Files.setPosixFilePermissions(
            dest, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
      }
    } catch (java.io.IOException e) {
      throw new Problem("harness_backup_failed", "Konfiguration konnte nicht gesichert werden.");
    }
  }

  static void atomicText(Path path, String content) {
    Path tmp = null;
    try {
      Files.createDirectories(path.getParent());
      tmp = Files.createTempFile(path.getParent(), ".harness-", ".tmp");
      Files.writeString(tmp, content);
      Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.io.IOException e) {
      throw new Problem(
          "harness_write_failed", "MCP-Konfiguration konnte nicht gespeichert werden.");
    } finally {
      if (tmp != null)
        try {
          Files.deleteIfExists(tmp);
        } catch (java.io.IOException ignored) {
        }
    }
  }
}
