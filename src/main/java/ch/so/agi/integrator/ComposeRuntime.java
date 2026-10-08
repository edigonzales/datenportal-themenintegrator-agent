package ch.so.agi.integrator;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Local tool services only; never controls the publication stack. */
final class ComposeRuntime {
  static final String HEADER =
      "# Managed by datenportal-integrator; regenerate with setup-mcps/setup-gretl.\n";
  final Settings s;
  final ProcessRunner p;
  final Path home;

  ComposeRuntime(Settings s, ProcessRunner p) {
    this.s = s;
    this.p = p;
    home = s.root.resolve(".datenportal-integrator/tools/compose");
  }

  static boolean managed(Settings s, String service) {
    return service.equals("gretl")
        ? GretlRuntime.mode(s).equals("compose")
        : Json.bool(s.mcp(service), "managed", false);
  }

  String project() {
    return Json.str(Json.obj(s.values.get("runtime")), "project", "datenportal-integrator");
  }

  Map<String, Object> expected(String service) {
    return service.equals("gretl")
        ? new GretlRuntime(s, p).selected(true)
        : new DockerMcps(s, p).selected(service, true);
  }

  static void afterSetup(Settings s, ProcessRunner p) {
    if (List.of("datasheet", "interlis", "gretl").stream().noneMatch(n -> managed(s, n))) return;
    var runtime = new ComposeRuntime(s, p);
    // The setup commands can run in either order. Do not publish partial selections.
    for (String service : List.of("datasheet", "interlis", "gretl")) {
      if (!managed(s, service)) continue;
      try {
        runtime.expected(service);
      } catch (Problem e) {
        if (Set.of("mcp_setup_required", "gretl_setup_required").contains(e.code)) return;
        throw e;
      }
    }
    try (var guard = RuntimeGuard.acquire(runtime.home.resolve("lifecycle.lock"), s.stackTimeout)) {
      runtime.prepare();
    }
  }

  void prepare() {
    try {
      Files.createDirectories(home.resolve("runtime"));
      for (String file :
          List.of("gretl-entrypoint.sh", "gretl-wait.sh", "gretl-job.sh", "gretl-recover.sh")) {
        try (var input = ComposeRuntime.class.getResourceAsStream("/runtime/" + file)) {
          if (input == null) throw new IllegalStateException("Missing runtime asset");
          Files.copy(input, home.resolve("runtime/" + file), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      Map<String, Object> config;
      try (var input = ComposeRuntime.class.getResourceAsStream("/compose.yaml")) {
        config = Json.obj(new Yaml(new SafeConstructor(new LoaderOptions())).load(input));
      }
      config.put("name", project());
      var services = Json.obj(config.get("services"));
      for (String name : List.of("datasheet", "interlis", "gretl")) {
        var service = Json.obj(services.get(name));
        Json.obj(service.get("labels")).put("datenportal.integrator.root", s.root.toString());
        if (!managed(s, name)) {
          service.put("profiles", List.of("unmanaged"));
          continue;
        }
        var identity = expected(name);
        service.put("image", Json.required(identity, "image_id"));
        if (!name.equals("gretl")) {
          var url = Settings.url(Json.required(s.mcp(name), "url"));
          int containerPort = name.equals("datasheet") ? 8000 : 8080;
          service.put("ports", List.of("127.0.0.1:" + url.getPort() + ":" + containerPort));
          if (name.equals("datasheet"))
            Json.obj(service.get("environment"))
                .put("DATASHEET_PUBLIC_BASE_URL", "http://127.0.0.1:" + url.getPort());
        } else {
          String key = Json.required(identity, "image_id").substring(7);
          Json.obj(service.get("environment")).put("INTEGRATOR_RUNTIME_KEY", key);
          Json.obj(Json.obj(config.get("volumes")).get("gretl-data"))
              .put("name", project() + "-gradle-" + key);
          service.put(
              "volumes",
              List.of(
                  "gretl-data:/var/lib/integrator",
                  Json.map(
                      "type",
                      "bind",
                      "source",
                      home.resolve("runtime").toString(),
                      "target",
                      "/opt/integrator-runtime",
                      "read_only",
                      true),
                  Json.map(
                      "type",
                      "bind",
                      "source",
                      s.topics.toString(),
                      "target",
                      "/opt/integrator-topic",
                      "read_only",
                      true)));
        }
      }
      Json.atomic(home.resolve("compose.json"), config);
      Path override = s.root.resolve("compose.override.yaml");
      if (Files.exists(override) && !Json.contents(override).startsWith(HEADER))
        throw new Problem(
            "runtime_configuration_conflict",
            "Vorhandene compose.override.yaml gehoert nicht zum Integrator.");
      Path tmp = Files.createTempFile(s.root, ".compose-override-", ".tmp");
      try {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String overrideYaml =
            new Yaml(options)
                .dump(config)
                .replaceAll("(?m)^(\\s+ports:)", "$1 !override")
                .replaceAll("(?m)^( {4}volumes:)", "$1 !override");
        Files.writeString(tmp, HEADER + overrideYaml);
        Files.move(
            tmp, override, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(
          "runtime_configuration_failed", "Compose-Konfiguration konnte nicht erzeugt werden.");
    }
  }

  List<String> command(String... args) {
    var result =
        new ArrayList<>(
            List.of(
                "docker",
                "compose",
                "-p",
                project(),
                "-f",
                home.resolve("compose.json").toString()));
    result.addAll(List.of(args));
    return result;
  }

  Map<String, Object> inspect(String service) {
    String ids =
        p.checked(
            List.of(
                "docker",
                "ps",
                "-aq",
                "--filter",
                "label=com.docker.compose.project=" + project(),
                "--filter",
                "label=com.docker.compose.service=" + service),
            s.root,
            60);
    if (ids.isBlank()) return Json.map("present", false);
    if (ids.lines().count() != 1)
      throw new Problem("runtime_ambiguous", "Mehrere Container fuer denselben Runtime-Dienst.");
    String format =
        "{\"id\":{{json .Id}},\"image_id\":{{json .Image}},\"state\":{{json .State.Status}},\"health\":{{json .State.Health.Status}},\"root\":{{json (index .Config.Labels \"datenportal.integrator.root\")}},\"ports\":{{json .HostConfig.PortBindings}},\"mounts\":{{json .Mounts}}}";
    // Some services have no Docker healthcheck.
    format =
        format.replace(
            "{{json .State.Health.Status}}",
            "{{if (index .State \"Health\")}}{{json .State.Health.Status}}{{else}}\"none\"{{end}}");
    var actual =
        Json.read(
            p.checked(
                List.of("docker", "container", "inspect", "--format", format, ids), s.root, 60));
    actual.put("present", true);
    var identity = expected(service);
    if (!Objects.equals(actual.get("root"), s.root.toString())
        || !Objects.equals(actual.get("image_id"), identity.get("image_id")))
      throw new Problem(
          "runtime_mismatch",
          "Compose-Dienst verwendet einen anderen Arbeitsbereich oder Image-Stand.",
          "service",
          service);
    if (!service.equals("gretl")) {
      int inner = service.equals("datasheet") ? 8000 : 8080;
      var bindings = Json.list(Json.obj(actual.get("ports")).get(inner + "/tcp"));
      String port = Integer.toString(Settings.url(Json.required(s.mcp(service), "url")).getPort());
      if (bindings.size() != 1
          || !"127.0.0.1".equals(Json.obj(bindings.getFirst()).get("HostIp"))
          || !port.equals(Json.obj(bindings.getFirst()).get("HostPort")))
        throw new Problem(
            "runtime_mismatch",
            "MCP-Portfreigabe stimmt nicht mit der lokalen Konfiguration ueberein.",
            "service",
            service);
    } else {
      String volume = project() + "-gradle-" + Json.required(identity, "image_id").substring(7);
      boolean matching =
          Json.list(actual.get("mounts")).stream()
              .map(Json::obj)
              .anyMatch(
                  m ->
                      "/var/lib/integrator".equals(m.get("Destination"))
                          && volume.equals(m.get("Name"))
                          && Json.bool(m, "RW", false));
      if (!matching)
        throw new Problem("runtime_mismatch", "GRETL verwendet nicht den eigenen Image-Cache.");
    }
    actual.remove("ports");
    actual.remove("mounts");
    return actual;
  }

  String ensure(String service) {
    if (!managed(s, service))
      throw new Problem("runtime_unmanaged", "Dienst ist nicht Compose-verwaltet.");
    try (var guard = RuntimeGuard.acquire(home.resolve("lifecycle.lock"), s.stackTimeout)) {
      prepare();
      var state = inspect(service);
      if (!"running".equals(state.get("state"))) {
        Path log = home.resolve(service + "-start.log");
        var result =
            p.run(
                command("up", "-d", "--no-recreate", "--pull", "never", service),
                s.root,
                s.stackTimeout,
                log);
        if (result.exitCode() != 0)
          throw new Problem(
              "runtime_start_failed",
              "Compose-Dienst konnte nicht starten; Docker und Portbelegung pruefen.",
              "service",
              service,
              "log",
              log.toString());
      }
      String container = await(service);
      if (service.equals("gretl")
          && !Objects.equals(
              expected(service).get("sha256"),
              new GretlRuntime(s, p).describe(container).get("sha256")))
        throw new Problem(
            "gretl_runtime_mismatch", "GRETL-Bundle weicht vom vorbereiteten Image-Nachweis ab.");
      return container;
    }
  }

  String await(String service) {
    long deadline =
        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(s.stackTimeout);
    while (System.nanoTime() < deadline) {
      var state = inspect(service);
      if ("running".equals(state.get("state"))) {
        if (service.equals("gretl") && "healthy".equals(state.get("health")))
          return Json.required(state, "id");
        if (!service.equals("gretl")) {
          var url = Settings.url(Json.required(s.mcp(service), "url"));
          try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", url.getPort()), 500);
            return Json.required(state, "id");
          } catch (Exception ignored) {
          }
        }
      } else if (Set.of("exited", "dead", "restarting").contains(state.get("state"))) {
        throw new Problem(
            "runtime_unhealthy",
            "Runtime-Dienst startet nicht stabil; Compose-Logs pruefen.",
            "service",
            service);
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new Problem("runtime_interrupted", "Runtime-Start unterbrochen.");
      }
    }
    throw new Problem(
        "runtime_start_timeout",
        "Runtime-Dienst wurde nicht rechtzeitig bereit.",
        "service",
        service);
  }

  void recover(String container) {
    ProcessRunner.Result result;
    try {
      result =
          p.run(
              List.of(
                  "docker",
                  "exec",
                  "--user",
                  "jenkins",
                  container,
                  "bash",
                  "/opt/integrator-runtime/gretl-recover.sh",
                  Integer.toString(s.stackTimeout)),
              s.root,
              s.stackTimeout + 5,
              home.resolve("recovery.log"));
    } catch (Problem e) {
      if (!e.code.equals("command_timeout")) throw e;
      result = new ProcessRunner.Result(76, "recovery timed out");
    }
    if (result.exitCode() == 0) return;
    try (var guard = RuntimeGuard.acquire(home.resolve("lifecycle.lock"), s.stackTimeout)) {
      var state = inspect("gretl");
      if (!container.equals(state.get("id")))
        throw new Problem(
            "runtime_mismatch", "GRETL-Container wurde waehrend einer Pruefung ersetzt.");
      p.checked(List.of("docker", "restart", container), s.root, 60);
      await("gretl");
      // A restart kills all processes from the previous task; clear its active marker.
      p.checked(
          List.of(
              "docker",
              "exec",
              "--user",
              "jenkins",
              container,
              "rm",
              "-f",
              "/var/lib/integrator/active"),
          s.root,
          30);
    }
  }

  Map<String, Object> up() {
    var result = Json.map();
    for (String service : List.of("datasheet", "interlis", "gretl"))
      if (managed(s, service)) {
        ensure(service);
        if (!service.equals("gretl"))
          try (var client =
              service.equals("datasheet") ? McpClients.datasheet(s) : McpClients.interlis(s)) {
            client.tools();
          }
        result.put(service, inspect(service));
      }
    return result;
  }
}
