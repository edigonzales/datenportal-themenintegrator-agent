package ch.so.agi.integrator;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Container-to-host loopback probe, with a private, short-lived sibling server. */
final class AgentRuntime {
  static boolean container() {
    return "true".equals(System.getenv("DATENPORTAL_CONTAINER_MODE"));
  }

  static Map<String, Object> check(Settings s) {
    var result =
        Json.map(
            "mode", container() ? "docker" : "local", "java_25", Runtime.version().feature() == 25);
    for (var path : List.of(s.root, s.topics, s.stack, s.file))
      if (!Files.exists(path))
        throw new Problem(
            "mount_missing",
            "Konfigurierter Pfad fehlt; bei Docker --mount-ro/--mount-rw verwenden.",
            "path",
            path.toString());
    for (var path :
        List.of(s.topics.resolve("shared/gradle/init.gradle"), s.stack.resolve("scripts/up.sh")))
      if (!Files.isRegularFile(path))
        throw new Problem(
            "checkout_incomplete",
            "Konfigurierter Checkout ist unvollständig.",
            "path",
            path.toString());
    if (!Files.isWritable(s.root) || (Files.exists(s.runs) && !Files.isWritable(s.runs)))
      throw new Problem(
          "mount_read_only", "Integrator und Vorgangsverzeichnis müssen schreibbar sein.");
    result.put("mounts_valid", true);
    if (!container()) {
      result.put("network", "host-process");
      return result;
    }
    String image = System.getenv("DATENPORTAL_RUNTIME_IMAGE");
    if (image == null || !image.matches("sha256:[a-f0-9]{64}"))
      throw new Problem("agent_image_missing", "Agent über bin/datenportal-agent starten.");
    var p = new ProcessRunner();
    String name = "datenportal-network-probe-" + UUID.randomUUID().toString().substring(0, 12);
    String token = UUID.randomUUID().toString();
    Path jar = AgentResources.resolve(s.root, "build/libs/datenportal-integrator.jar");
    try {
      var command =
          new ArrayList<>(
              List.of(
                  "docker",
                  "run",
                  "-d",
                  "--rm",
                  "--name",
                  name,
                  "--label",
                  "datenportal.integrator.probe=true",
                  "-p",
                  "127.0.0.1::18764",
                  "--entrypoint",
                  "java"));
      if (!AgentResources.installed()) {
        command.addAll(
            List.of("--mount", "type=bind,source=" + jar + ",target=/agent.jar,readonly"));
      }
      command.addAll(
          List.of(
              image,
              "-cp",
              AgentResources.installed() ? jar.toString() : "/agent.jar",
              AgentRuntime.class.getName(),
              token));
      p.checked(command, s.root, 60);
      String port =
          p.checked(
              List.of(
                  "docker",
                  "inspect",
                  "--format",
                  "{{(index (index .NetworkSettings.Ports \"18764/tcp\") 0).HostPort}}",
                  name),
              s.root,
              20);
      var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
      for (int attempt = 0; attempt < 20; attempt++) {
        try {
          var reply =
              client.send(
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                      .timeout(Duration.ofSeconds(1))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          if (reply.statusCode() == 200 && token.equals(reply.body())) {
            result.put("network", "host-loopback-verified");
            result.put("image_id", image);
            return result;
          }
        } catch (InterruptedException e) {
          throw e;
        } catch (Exception ignored) {
        }
        Thread.sleep(250);
      }
      throw new Problem(
          "host_network_unavailable",
          "Host-Networking nicht erreichbar. In Docker Desktop (ab 4.34) Settings > Resources > Network > Enable host networking aktivieren; Linux benötigt lokalen Docker Engine.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Problem("network_probe_interrupted", "Netzwerkprüfung unterbrochen.");
    } finally {
      p.run(List.of("docker", "rm", "-f", name), s.root, 20, null);
    }
  }

  public static void main(String[] args) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 18764), 0);
    byte[] token = args[0].getBytes(java.nio.charset.StandardCharsets.UTF_8);
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(200, token.length);
          try (var out = exchange.getResponseBody()) {
            out.write(token);
          }
        });
    server.start();
    Thread.sleep(60000);
    server.stop(0);
  }
}
