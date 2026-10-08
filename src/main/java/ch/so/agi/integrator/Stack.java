package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Stack {
  final Settings s;
  final ProcessRunner p;

  public Stack(Settings s, ProcessRunner p) {
    this.s = s;
    this.p = p;
  }

  private List<String> composeArgs(String... args) {
    var cmd = new ArrayList<>(List.of("docker", "compose"));
    for (String f : s.strings("compose_files", List.of("compose.yaml")))
      cmd.addAll(List.of("-f", f));
    cmd.addAll(List.of(args));
    return cmd;
  }

  private String compose(String... args) {
    return p.checked(composeArgs(args), s.stack, s.timeout);
  }

  private String stackCommand(List<String> args) {
    Path log;
    try {
      Path directory = s.runs.getParent().resolve("stack-logs");
      Files.createDirectories(directory);
      log = Files.createTempFile(directory, "stack-", ".log");
    } catch (java.io.IOException e) {
      throw new Problem("stack_log_unavailable", "Stack-Diagnoselog kann nicht angelegt werden.");
    }
    var result = p.run(args, s.stack, s.stackTimeout, log);
    if (result.exitCode() != 0)
      throw new Problem(
          "stack_command_failed",
          "Stack-Aufruf fehlgeschlagen; Diagnose vor erneutem Start prüfen.",
          "returncode",
          result.exitCode(),
          "log",
          log.toString());
    return result.output().strip();
  }

  public Map<String, Object> inspect() {
    String ids =
        p.checked(
            List.of(
                "docker",
                "ps",
                "-aq",
                "--filter",
                "label=com.docker.compose.project.working_dir=" + s.stack,
                "--filter",
                "label=com.docker.compose.service=jenkins"),
            s.stack,
            60);
    if (ids.isBlank()) return Json.map("present", false, "compatible", true, "running", false);
    if (ids.lines().count() != 1)
      throw new Problem(
          "ambiguous_stack", "Mehrere Jenkins-Container im vorgesehenen Compose-Projekt.");
    try {
      var containers =
          Json.list(
              Json.MAPPER.readValue(
                  p.checked(List.of("docker", "inspect", ids), s.stack, 60), Object.class));
      var c = Json.obj(containers.getFirst());
      var mounts =
          Json.list(c.get("Mounts")).stream()
              .map(Json::obj)
              .filter(m -> Objects.equals(m.get("Destination"), "/workspace/themenrepo"))
              .toList();
      boolean mode =
          Json.strings(Json.obj(c.get("Config")).get("Env"))
              .contains("THEMEN_REPO_MODE=working-tree");
      if (mounts.size() != 1
          || !Path.of(Json.required(mounts.getFirst(), "Source"))
              .toRealPath()
              .equals(s.topics.toRealPath())
          || !mode)
        throw new Problem(
            "stack_mismatch",
            "Vorhandene Instanz verwendet einen anderen Themenbestand oder Modus.");
      var state = Json.obj(c.get("State"));
      return Json.map(
          "present",
          true,
          "compatible",
          true,
          "running",
          state.get("Running"),
          "health",
          Json.str(Json.obj(state.get("Health")), "Status", "unknown"));
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("stack_unavailable", "Docker-Status kann nicht gelesen werden.");
    }
  }

  public Object ensure() {
    var st = inspect();
    boolean running = Json.bool(st, "running", false);
    if (!running) {
      var cmd = new ArrayList<>(List.of("bash", "scripts/up.sh"));
      cmd.addAll(s.strings("stack_start_args", List.of()));
      // Deliveries checks the pinned GRETL image/bundle before allowing the
      // shared bootstrap to publish into a fresh bucket.
      if (!cmd.contains("--infrastructure-only")) cmd.add("--infrastructure-only");
      stackCommand(cmd);
    }
    inspect();
    services(Set.of("garage", "jenkins", "downloads"));
    return Json.map("ready", true, "reused", running);
  }

  public void services(Set<String> required) {
    String json = compose("ps", "--all", "--format", "json");
    List<Object> containers;
    try {
      Object o = Json.MAPPER.readValue(json, Object.class);
      containers = o instanceof List ? Json.list(o) : List.of(o);
    } catch (Exception e) {
      containers =
          json.lines().filter(l -> !l.isBlank()).map(Json::read).map(v -> (Object) v).toList();
    }
    var states = Json.map();
    containers.stream().map(Json::obj).forEach(c -> states.put(Json.str(c, "Service", ""), c));
    var missing =
        required.stream()
            .filter(
                service -> !Objects.equals(Json.obj(states.get(service)).get("State"), "running"))
            .sorted()
            .toList();
    if (!missing.isEmpty()) {
      var cmd =
          new ArrayList<>(List.of("up", "-d", "--no-recreate", "--wait", "--wait-timeout", "120"));
      cmd.addAll(missing);
      stackCommand(composeArgs(cmd.toArray(String[]::new)));
    }
    var bad =
        required.stream()
            .filter(
                service ->
                    !missing.contains(service)
                        && Set.of("unhealthy", "starting")
                            .contains(Json.str(Json.obj(states.get(service)), "Health", "")))
            .toList();
    if (!bad.isEmpty())
      throw new Problem("stack_unhealthy", "Stack ist noch nicht bereit.", "services", bad);
  }

  public Object bootstrap(Map<String, Object> env) {
    if (!Json.str(env, "kind", "").equals("local"))
      throw new Problem("local_only", "Automatischer Erstaufbau ist nur lokal vorgesehen.");
    boolean existing = Jenkins.manifest(env) != null;
    if (existing) {
      services(Set.of("sodata"));
    }
    var cmd = new ArrayList<>(List.of("bash", "scripts/bootstrap.sh"));
    for (String file : s.strings("compose_files", List.of("compose.yaml")))
      cmd.addAll(List.of("-f", file));
    cmd.addAll(List.of("--timeout", Integer.toString(s.stackTimeout)));
    if (existing) cmd.add("--check-only");
    stackCommand(cmd);
    var manifest = Jenkins.manifest(env);
    if (manifest == null)
      throw new Problem("bootstrap_incomplete", "Initialisierung lieferte kein lesbares Manifest.");
    return Json.map(
        "initialized",
        !existing,
        "release_id",
        manifest.get("releaseId"),
        "reason",
        existing ? "existing_manifest" : "stack_bootstrap");
  }
}
