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
      p.checked(cmd, s.stack, s.timeout);
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
      compose(cmd.toArray(String[]::new));
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
    if (Jenkins.manifest(env) != null) {
      services(Set.of("sodata"));
      return Json.map("initialized", false, "reason", "existing_manifest");
    }
    var j = new Jenkins(env);
    if (Json.bool(j.get("api/json"), "quietingDown", false))
      throw new Problem("jenkins_paused", "Jenkins ist bereits administrativ pausiert.");
    j.post("quietDown");
    try {
      var running =
          j.get(
              "computer/api/json?tree=computer[executors[currentExecutable[url]],oneOffExecutors[currentExecutable[url]]]");
      for (Object c : Json.list(running.get("computer")))
        for (String key : List.of("executors", "oneOffExecutors"))
          for (Object executor : Json.list(Json.obj(c).get(key)))
            if (Json.obj(executor).get("currentExecutable") != null)
              throw new Problem(
                  "builds_running",
                  "Laufende Builds abwarten; Erstinitialisierung noch nicht gestartet.");
      if (Jenkins.manifest(env) != null) {
        services(Set.of("sodata"));
        return Json.map("initialized", false, "reason", "manifest_appeared");
      }
      String script =
          """
                    set -euo pipefail
                    work=$(mktemp -d /var/jenkins_home/datenportal-integrator.XXXXXX)
                    tar -C /workspace/themenrepo --exclude=.git --exclude=.gradle --exclude=build --exclude=.DS_Store -cf - . | tar -C "$work" -xf -
                    cd "$work"
                    ./shared/bin/gradlew-java17.sh --no-daemon -I "$work/shared/gradle/init.gradle" initializePublication -Ps3Publish=true -PgitWriteBack=false -PreloadPortal=false
                    """;
      // Pass the script as an argument to the existing container's bash, without shell
      // interpolation on the host.
      compose("exec", "-T", "jenkins", "bash", "-c", script);
    } finally {
      j.post("cancelQuietDown");
    }
    var manifest = Jenkins.manifest(env);
    if (manifest == null)
      throw new Problem("bootstrap_incomplete", "Initialisierung lieferte kein lesbares Manifest.");
    services(Set.of("sodata"));
    return Json.map("initialized", true, "release_id", manifest.get("releaseId"));
  }
}
