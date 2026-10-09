package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {
  @TempDir Path temp;

  @Test
  void missingDockerHasConcreteDiagnostic() throws Exception {
    Path tools = temp.resolve("tools"), repo = temp.resolve("repo");
    Files.createDirectories(tools);
    Files.createDirectories(repo.resolve("bin"));
    Files.createSymbolicLink(tools.resolve("dirname"), Path.of("/usr/bin/dirname"));
    Files.createSymbolicLink(tools.resolve("mkdir"), Path.of("/bin/mkdir"));
    Files.copy(
        Fixtures.ROOT.resolve("bin/datenportal-agent"), repo.resolve("bin/datenportal-agent"));
    var pb =
        new ProcessBuilder("/bin/bash", repo.resolve("bin/datenportal-agent").toString(), "serve")
            .redirectErrorStream(true);
    pb.environment().put("PATH", tools.toString());
    var process = pb.start();
    assertTrue(new String(process.getInputStream().readAllBytes()).contains("Docker is required"));
    assertEquals(2, process.waitFor());
  }

  @Test
  void rejectsMissingMountBeforeDockerAndPreservesSpaces() throws Exception {
    Path repo = temp.resolve("repo with spaces"), bin = repo.resolve("bin");
    Files.createDirectories(bin);
    Files.copy(Fixtures.ROOT.resolve("bin/datenportal-agent"), bin.resolve("datenportal-agent"));
    var p = new ProcessRunner();
    var missing =
        p.run(
            List.of(
                "bash",
                bin.resolve("datenportal-agent").toString(),
                "--mount-ro",
                temp.resolve("missing").toString(),
                "serve"),
            repo,
            10,
            null);
    assertEquals(2, missing.exitCode());
    assertTrue(missing.output().contains("existing directory"));
    Path fake = temp.resolve("fake");
    Files.createDirectories(fake);
    Json.write(
        fake.resolve("docker"),
        "#!/bin/sh\ncase \"$1\" in context) printf 'ssh://remote\\n';; *) exit 91;; esac\n");
    fake.resolve("docker").toFile().setExecutable(true);
    var pb =
        new ProcessBuilder(
                "bash",
                bin.resolve("datenportal-agent").toString(),
                "--mount-ro",
                repo.toString(),
                "serve")
            .directory(repo.toFile())
            .redirectErrorStream(true);
    pb.environment().remove("DOCKER_HOST");
    pb.environment().put("PATH", fake + ":" + System.getenv("PATH"));
    var remote = pb.start();
    assertTrue(
        new String(remote.getInputStream().readAllBytes())
            .contains("Remote Docker is unsupported"));
    assertEquals(2, remote.waitFor());
  }

  @Test
  void malformedRuntimeDoesNotRunTools() {
    var result =
        new ProcessRunner()
            .run(
                List.of(
                    "bash",
                    Fixtures.ROOT.resolve("bin/datenportal-agent").toString(),
                    "--runtime",
                    "unknown",
                    "serve"),
                Fixtures.ROOT,
                10,
                null);
    assertEquals(2, result.exitCode());
  }

  @Test
  void dockerArgumentsKeepMountsJsonAndSecretNamesWithoutValues() throws Exception {
    Path repo = temp.resolve("checkout space"),
        bin = repo.resolve("bin"),
        fake = temp.resolve("tools");
    Files.createDirectories(bin);
    Files.createDirectories(fake);
    Files.createDirectories(repo.resolve("runtime"));
    Files.copy(Fixtures.ROOT.resolve("bin/datenportal-agent"), bin.resolve("datenportal-agent"));
    Json.write(repo.resolve("runtime/Dockerfile"), "# isolated launcher fixture\n");
    Path input = temp.resolve("input space"), alias = temp.resolve("input-link");
    Files.createDirectories(input);
    Files.createSymbolicLink(alias, input);
    Path log = temp.resolve("docker.args");
    Json.write(
        fake.resolve("docker"),
        """
        #!/usr/bin/env bash
        printf '<%s>\\n' "$@" >> "$TEST_LOG"
        case "$1" in
          context) printf 'unix://%s\\n' "$TEST_SOCKET";;
          version) printf '29.0\\n';;
          image) printf 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\\n';;
          run)
            case " $* " in
              *' stat -c '*) printf '0\\n';;
              *' java '*) printf '{"fixture":true}\\n';;
            esac;;
        esac
        """);
    fake.resolve("docker").toFile().setExecutable(true);
    Path socket = Path.of("/tmp/dp-launcher-" + UUID.randomUUID().toString().substring(0, 8));
    try (var server =
        java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)) {
      server.bind(java.net.UnixDomainSocketAddress.of(socket));
      var pb =
          new ProcessBuilder(
              "bash",
              bin.resolve("datenportal-agent").toString(),
              "--mount-ro",
              alias.toString(),
              "--mount-rw",
              temp.toString(),
              "call",
              "example",
              "--json",
              "{\"text\":\"a b;$literal\"}");
      pb.environment().remove("DOCKER_HOST");
      pb.environment().remove("DOCKER_CONTEXT");
      pb.environment().put("PATH", fake + ":" + System.getenv("PATH"));
      pb.environment().put("TEST_SOCKET", socket.toString());
      pb.environment().put("TEST_LOG", log.toString());
      pb.environment().put("DATENPORTAL_FORWARD_ENV", "EXAMPLE_TOKEN");
      pb.environment().put("EXAMPLE_TOKEN", "SYNTHETIC_SECRET_VALUE");
      var process = pb.redirectError(temp.resolve("stderr").toFile()).start();
      assertEquals(
          "{\"fixture\":true}", new String(process.getInputStream().readAllBytes()).strip());
      assertEquals(0, process.waitFor(), Json.contents(temp.resolve("stderr")));
      String calls = Json.contents(log);
      assertTrue(
          calls.contains(
              "source=" + input.toRealPath() + ",target=" + input.toRealPath() + ",readonly"));
      assertTrue(calls.contains("<{\"text\":\"a b;$literal\"}>"));
      assertTrue(calls.contains("<EXAMPLE_TOKEN>"));
      assertFalse(calls.contains("SYNTHETIC_SECRET_VALUE"));
      assertFalse(Files.exists(repo.resolve(".datenportal-integrator/launcher/build.lock")));
    } finally {
      Files.deleteIfExists(socket);
    }
  }
}
