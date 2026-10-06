package ch.so.agi.integrator;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

public final class Settings {
  public final Path root, topics, stack, runs;
  public final Map<String, Object> values;
  public final int timeout;

  public Settings(Path config) {
    if (!Files.isRegularFile(config))
      throw new Problem(
          "configuration_missing",
          "Konfiguration fehlt.",
          "path",
          config.toString(),
          "hint",
          "config/local.example.toml nach config/local.toml kopieren.");
    try {
      var parsed = Toml.parse(config);
      if (parsed.hasErrors())
        throw new Problem("invalid_configuration", "TOML-Konfiguration ist ungültig.");
      values = table(parsed);
    } catch (java.io.IOException e) {
      throw new Problem("invalid_configuration", "TOML-Konfiguration nicht lesbar.");
    }
    Set<String> allowed =
        Set.of(
            "root",
            "topics_repo",
            "stack_repo",
            "state_dir",
            "datasheet_mcp_url",
            "validator_command",
            "model_dirs",
            "compose_files",
            "stack_start_args",
            "environments",
            "timeout_seconds",
            "interlis",
            "gretl_java_home",
            "gretl_offline_jars");
    for (String key : values.keySet())
      if (!allowed.contains(key))
        throw new Problem("invalid_configuration", "Unbekannte Konfiguration.", "field", key);
    root = config.toAbsolutePath().getParent().resolve(Json.str(values, "root", "..")).normalize();
    topics = path(Json.required(values, "topics_repo"));
    stack = path(Json.required(values, "stack_repo"));
    runs = path(Json.str(values, "state_dir", ".datenportal-integrator/runs"));
    timeout = Json.number(values, "timeout_seconds", 300);
    if (timeout < 1 || timeout > 7200)
      throw new Problem(
          "invalid_configuration", "timeout_seconds muss zwischen 1 und 7200 liegen.");
    url(Json.str(values, "datasheet_mcp_url", "http://127.0.0.1:8000/mcp"));
    var interlis = Json.obj(values.get("interlis"));
    for (String field : interlis.keySet())
      if (!Set.of("transport", "jar", "java_command", "url").contains(field))
        throw new Problem(
            "invalid_configuration", "Unbekannte INTERLIS-Einstellung.", "field", field);
    for (Object profile : Json.obj(values.get("environments")).values()) {
      var environment = Json.obj(profile);
      for (String field : environment.keySet())
        if (!Set.of(
                "kind",
                "jenkins_url",
                "portal_url",
                "manifest_url",
                "seed_job",
                "username_env",
                "token_env",
                "git_remote",
                "git_branch",
                "enabled",
                "repository_mode",
                "git_write_back",
                "reload_portal")
            .contains(field))
          throw new Problem(
              "invalid_configuration", "Unbekannte Umgebungseinstellung.", "field", field);
      for (String field : List.of("kind", "jenkins_url", "portal_url", "manifest_url"))
        Json.required(environment, field);
      for (String field : List.of("jenkins_url", "portal_url", "manifest_url"))
        url(Json.required(environment, field));
    }
    if (interlis.containsKey("url")) url(Json.required(interlis, "url"));
  }

  private static Object convert(Object value) {
    if (value instanceof TomlTable t) return table(t);
    if (value instanceof TomlArray a) {
      var l = new ArrayList<>();
      for (int i = 0; i < a.size(); i++) l.add(convert(a.get(i)));
      return l;
    }
    return value;
  }

  private static Map<String, Object> table(TomlTable t) {
    var m = Json.map();
    for (String key : t.keySet()) m.put(key, convert(t.get(key)));
    return m;
  }

  public static Settings load(String option) {
    return new Settings(
        Path.of(
                option != null
                    ? option
                    : System.getenv()
                        .getOrDefault("DATENPORTAL_INTEGRATOR_CONFIG", "config/local.toml"))
            .toAbsolutePath()
            .normalize());
  }

  public Path path(String p) {
    if (p.startsWith("~/")) p = System.getProperty("user.home") + p.substring(1);
    return root.resolve(p).toAbsolutePath().normalize();
  }

  public List<String> strings(String key, List<String> fallback) {
    return values.containsKey(key) ? Json.strings(values.get(key)) : fallback;
  }

  public static URI url(String s) {
    try {
      var u = URI.create(s);
      if (!Set.of("http", "https").contains(u.getScheme())
          || u.getHost() == null
          || u.getUserInfo() != null
          || u.getFragment() != null) throw new IllegalArgumentException();
      return u;
    } catch (Exception e) {
      throw new Problem(
          "invalid_configuration", "HTTP(S)-Adresse ohne eingebettete Zugangsdaten erforderlich.");
    }
  }

  public Map<String, Object> environment(String name) {
    var e = Json.obj(Json.obj(values.get("environments")).get(name));
    if (!Json.bool(e, "enabled", false))
      throw new Problem(
          "environment_disabled",
          "Umgebung fehlt oder ist nicht freigeschaltet.",
          "environment",
          name);
    var kind = Json.required(e, "kind");
    if (!Set.of("local", "int", "prod").contains(kind))
      throw new Problem("invalid_configuration", "Ungültige Umgebungsart.");
    for (var key : List.of("jenkins_url", "portal_url", "manifest_url")) {
      var u = url(Json.required(e, key));
      if (kind.equals("local")
          && !Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(u.getHost()))
        throw new Problem(
            "local_endpoint_required", "Lokale Automatik darf nur Loopback-Adressen verwenden.");
    }
    if (Json.bool(e, "git_write_back", false)
        || (!kind.equals("local")
            && !Json.str(e, "repository_mode", "managed-git").equals("managed-git")))
      throw new Problem(
          "runtime_incompatible",
          "PR-Verfahren verlangt managed-git und deaktiviertes Git-Rückschreiben.");
    return e;
  }

  public String fingerprint() {
    var files = Json.map();
    Path bundle = GretlRuntime.directory(this);
    if (Files.isDirectory(bundle)) files.put("gretl_bundle", GretlRuntime.fingerprint(bundle));
    for (var directory : List.of(root.resolve("config"), root.resolve("validation")))
      try (var paths = Files.list(directory)) {
        paths
            .filter(Files::isRegularFile)
            .filter(p -> !p.getFileName().toString().equals("local.toml"))
            .sorted()
            .forEach(p -> files.put(p.toString(), Json.sha(p)));
      } catch (java.io.IOException e) {
        throw new Problem("configuration_missing", "Regeln oder Validierungsdateien fehlen.");
      }
    for (String directory : strings("model_dirs", List.of()))
      if (!directory.startsWith("https://"))
        try (var paths = Files.list(path(directory))) {
          paths
              .filter(p -> p.toString().endsWith(".ili"))
              .sorted()
              .forEach(p -> files.put(p.toString(), Json.sha(p)));
        } catch (java.io.IOException e) {
          throw new Problem(
              "model_directory_missing", "INTERLIS-Modellverzeichnis fehlt.", "path", directory);
        }
    for (String part : strings("validator_command", List.of()))
      if (part.endsWith(".jar") && Files.isRegularFile(path(part)))
        files.put(part, Json.sha(path(part)));
    var interlis = Json.obj(values.get("interlis"));
    String jar = Json.str(interlis, "jar", "");
    if (!jar.isEmpty() && Files.isRegularFile(path(jar)))
      files.put("interlis_jar", Json.sha(path(jar)));
    Path own = root.resolve("build/libs/datenportal-integrator.jar");
    if (Files.isRegularFile(own)) files.put("integrator_jar", Json.sha(own));
    return Json.digest(Json.map("settings", values, "dependencies", files));
  }
}
