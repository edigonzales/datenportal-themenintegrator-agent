package ch.so.agi.integrator;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

public final class Settings {
  public final Path file, root, topics, stack, runs;
  public final Map<String, Object> values;
  public final int timeout, stackTimeout;

  public Settings(Path config) {
    file = config.toAbsolutePath().normalize();
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
    if (values.containsKey("gretl") || values.containsKey("runtime"))
      throw new Problem(
          "configuration_upgrade_required",
          "Neue Java-Konfigurationsvorlage in neuem Arbeitsbereich verwenden; alte Vorgänge bleiben unverändert.");
    Set<String> allowed =
        Set.of(
            "root",
            "topics_repo",
            "stack_repo",
            "state_dir",
            "datasheet",
            "validator_command",
            "model_dirs",
            "input_roots",
            "compose_files",
            "stack_start_args",
            "environments",
            "timeout_seconds",
            "stack_timeout_seconds",
            "interlis",
            "gretl",
            "runtime");
    for (String key : values.keySet())
      if (!allowed.contains(key))
        throw new Problem("invalid_configuration", "Unbekannte Konfiguration.", "field", key);
    for (String key : allowed) {
      Class<?> type =
          switch (key) {
            case "datasheet", "interlis", "gretl", "runtime", "environments" -> Map.class;
            case "validator_command",
                "model_dirs",
                "input_roots",
                "compose_files",
                "stack_start_args" ->
                List.class;
            case "timeout_seconds", "stack_timeout_seconds" -> Long.class;
            default -> String.class;
          };
      type(values, key, type, key);
      if (values.get(key) instanceof List<?> list
          && (list.stream().anyMatch(v -> !(v instanceof String))
              || (key.equals("validator_command") && list.isEmpty()))) throw invalid(key);
    }
    if (!values.containsKey("datasheet")) throw invalid("datasheet");
    for (String key : List.of("timeout_seconds", "stack_timeout_seconds"))
      if (values.get(key) instanceof Long number && (number < 1 || number > 7200))
        throw invalid(key);
    root = config.toAbsolutePath().getParent().resolve(Json.str(values, "root", "..")).normalize();
    topics = path(Json.required(values, "topics_repo"));
    stack = path(Json.str(values, "stack_repo", ".datenportal-integrator/no-local-stack"));
    runs = path(Json.str(values, "state_dir", ".datenportal-integrator/runs"));
    timeout = Json.number(values, "timeout_seconds", 300);
    stackTimeout = Json.number(values, "stack_timeout_seconds", 1800);
    for (String service : List.of("datasheet", "interlis")) validateMcp(service);
    for (Object profile : Json.obj(values.get("environments")).values()) {
      if (!(profile instanceof Map)) throw invalid("environments");
      var environment = Json.obj(profile);
      for (String field : environment.keySet())
        type(
            environment,
            field,
            Set.of("enabled", "git_write_back", "reload_portal").contains(field)
                ? Boolean.class
                : String.class,
            "environments." + field);
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
      if (!Set.of("local", "int", "prod").contains(Json.required(environment, "kind"))
          || !Set.of("working-tree", "managed-git")
              .contains(Json.str(environment, "repository_mode", "managed-git")))
        throw invalid("environments");
    }
  }

  /** Private synthetic workspace: no publication profiles and no real repository writes. */
  Settings(Settings source, Path privateRepo, Path privateRuns) {
    file = source.file;
    root = source.root;
    topics = privateRepo;
    stack = source.stack;
    runs = privateRuns;
    timeout = source.timeout;
    stackTimeout = source.stackTimeout;
    values = Json.read(Json.text(source.values));
    values.put("topics_repo", privateRepo.toString());
    values.put("state_dir", privateRuns.toString());
    values.put("environments", Json.map());
  }

  public Map<String, Object> mcp(String service) {
    return Json.obj(values.get(service));
  }

  private void validateMcp(String service) {
    var c = mcp(service);
    if (!values.containsKey(service) && service.equals("interlis")) return;
    if (!c.isEmpty() && !(c.size() == 1 && "stdio".equals(c.get("transport"))))
      throw new Problem(
          "configuration_upgrade_required",
          "MCPs verwenden die mitgelieferten Java-JARs über stdio; alte image/url/managed-Konfiguration entfernen.",
          "service",
          service);
  }

  private static Problem invalid(String field) {
    return new Problem(
        "invalid_configuration",
        "Konfigurationsfeld fehlt oder hat einen ungültigen Wert; aktuelle Vorlage verwenden.",
        "field",
        field);
  }

  private static void type(Map<String, Object> values, String key, Class<?> type, String field) {
    if (values.containsKey(key) && !type.isInstance(values.get(key))) throw invalid(field);
  }

  public List<String> modelDirectories() {
    var dirs = new ArrayList<String>();
    Path models = AgentResources.resolve(root, "models");
    if (Files.isDirectory(models)) dirs.add(models.toString());
    dirs.addAll(strings("model_dirs", List.of()));
    return dirs;
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
    return ReviewPolicy.fingerprint(this, AgentResources.resolve(root, ""));
  }

  String legacyFingerprint(Path home) {

    var files = Json.map();
    files.put("runtime", "java21");
    for (var directory : List.of(home.resolve("config"), home.resolve("validation")))
      try (var paths = Files.list(directory)) {
        paths
            .filter(Files::isRegularFile)
            .filter(p -> !p.getFileName().toString().equals("local.toml"))
            .sorted()
            .forEach(p -> files.put(p.toString(), Json.sha(p)));
      } catch (java.io.IOException e) {
        throw new Problem("configuration_missing", "Regeln oder Validierungsdateien fehlen.");
      }
    for (String service : List.of("datasheet", "interlis")) {
      Path jar =
          home.resolve(
              "lib/" + (service.equals("datasheet") ? "datasheet-mcp" : "interlis-mcp") + ".jar");
      if (Files.isRegularFile(jar)) files.put(service + "_jar", Json.sha(jar));
    }
    var directories = new ArrayList<String>();
    if (Files.isDirectory(home.resolve("models")))
      directories.add(home.resolve("models").toString());
    directories.addAll(strings("model_dirs", List.of()));
    for (String directory : directories)
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
    Path own = home.resolve("lib/datenportal-integrator.jar");
    if (Files.isRegularFile(own)) files.put("integrator_jar", Json.sha(own));
    return Json.digest(Json.map("settings", values, "dependencies", files));
  }
}
