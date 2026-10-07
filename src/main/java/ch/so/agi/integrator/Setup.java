package ch.so.agi.integrator;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class Setup {
  final Settings s;

  public Setup(Settings s) {
    this.s = s;
  }

  public Object run(String command, List<String> args) {
    return switch (command) {
      case "setup-tools" -> tools();
      case "setup-java" -> javaLauncher();
      case "setup-gretl" -> {
        if (!args.isEmpty() && !args.equals(List.of("--update")))
          throw new Problem("invalid_arguments", "setup-gretl [--update]");
        yield new GretlRuntime(s, new ProcessRunner()).setup(!args.isEmpty());
      }
      case "setup-mcps" -> {
        if (!args.isEmpty() && !args.equals(List.of("--update")))
          throw new Problem("invalid_arguments", "setup-mcps [--update]");
        yield new DockerMcps(s, new ProcessRunner()).setup(!args.isEmpty());
      }
      case "start-datasheet" ->
          Json.map(
              "deprecated",
              true,
              "hint",
              "setup-mcps ausführen. stdio-MCPs werden vom Integrator bei Bedarf gestartet; kein Quellbuild.");
      case "harness-config" -> harness();
      case "codex" -> codex(args);
      default -> throw new Problem("unknown_operation", "Unbekannter Helfer.");
    };
  }

  Object tools() {
    javaLauncher();
    try {
      var r =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build()
              .send(
                  HttpRequest.newBuilder(
                          URI.create(
                              "https://downloads.interlis.ch/ilivalidator/ilivalidator-1.15.0.zip"))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() != 200
          || !Json.hash(r.body())
              .equals("df7ee8961743e3dfc586f646af4f63d49dabf920e98a4836a32edf676d0b20c7"))
        throw new Problem(
            "tool_checksum",
            "ilivalidator-Download stimmt nicht mit der festgelegten Prüfsumme überein.");
      Path dest = s.root.resolve(".datenportal-integrator/tools/ilivalidator");
      Files.createDirectories(dest);
      try (var zip = new ZipInputStream(new ByteArrayInputStream(r.body()))) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          Path p = Json.inside(dest, entry.getName());
          if (entry.isDirectory()) Files.createDirectories(p);
          else {
            Files.createDirectories(p.getParent());
            Files.copy(zip, p, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
      return Json.map("validator_jar", dest.resolve("ilivalidator-1.15.0.jar").toString());
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("tool_setup_failed", "ilivalidator konnte nicht installiert werden.");
    }
  }

  String java() {
    return Path.of(System.getProperty("java.home"), "bin/java").toString();
  }

  Object javaLauncher() {
    Path link = s.root.resolve(".datenportal-integrator/tools/java");
    try {
      Files.createDirectories(link.getParent());
      if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
        if (!Files.isSymbolicLink(link))
          throw new Problem(
              "launcher_conflict", "Lokaler Java-Launcher ist kein verwalteter Symlink.");
        Files.delete(link);
      }
      Files.createSymbolicLink(link, Path.of(java()));
      return Json.map("java_launcher", link.toString(), "java", java());
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(
          "launcher_unavailable",
          "Java-Launcher konnte nicht angelegt werden; absoluten JDK-Pfad aus harness-config verwenden.");
    }
  }

  List<String> serverArgs() {
    return List.of(
        "-jar",
        s.root.resolve("build/libs/datenportal-integrator.jar").toString(),
        "--config",
        s.root.resolve("config/local.toml").toString(),
        "serve");
  }

  Object harness() {
    var credentials =
        List.of(
            "DATENPORTAL_LOCAL_USER",
            "DATENPORTAL_LOCAL_TOKEN",
            "DATENPORTAL_JENKINS_USER",
            "DATENPORTAL_JENKINS_TOKEN");
    return Json.map(
        "codex",
        Json.map(
            "mcp_servers",
            Json.map(
                "datenportal_integrator",
                Json.map(
                    "command",
                    java(),
                    "args",
                    serverArgs(),
                    "tool_timeout_sec",
                    600,
                    "env_vars",
                    credentials))),
        "opencode",
        Json.map(
            "mcp",
            Json.map(
                "datenportal_integrator",
                Json.map(
                    "type",
                    "local",
                    "command",
                    concat(List.of(java()), serverArgs()),
                    "enabled",
                    true))));
  }

  Object codex(List<String> extra) {
    var command = new ArrayList<>(List.of("codex", "-C", s.root.toString()));
    var config =
        Json.map(
            "mcp_servers.datenportal_integrator.command",
            java(),
            "mcp_servers.datenportal_integrator.args",
            serverArgs(),
            "mcp_servers.datenportal_integrator.tool_timeout_sec",
            600,
            "mcp_servers.datenportal_integrator.env_vars",
            List.of(
                "DATENPORTAL_LOCAL_USER",
                "DATENPORTAL_LOCAL_TOKEN",
                "DATENPORTAL_JENKINS_USER",
                "DATENPORTAL_JENKINS_TOKEN"));
    config.forEach((k, v) -> command.addAll(List.of("-c", k + "=" + Json.text(v))));
    command.addAll(extra);
    try {
      var process = new ProcessBuilder(command).inheritIO().start();
      return Json.map("exit_code", process.waitFor());
    } catch (Exception e) {
      throw new Problem(
          "codex_start_failed", "Codex ist nicht im PATH oder konnte nicht gestartet werden.");
    }
  }

  static List<String> concat(List<String> a, List<String> b) {
    var r = new ArrayList<>(a);
    r.addAll(b);
    return r;
  }
}
