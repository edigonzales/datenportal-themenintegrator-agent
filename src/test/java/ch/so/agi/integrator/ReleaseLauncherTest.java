package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Isolated Docker simulation; never pulls or publishes real images. */
class ReleaseLauncherTest {
  @TempDir Path temp;
  Path repo, tools, log, socket;
  ServerSocketChannel server;
  static final String ID = "sha256:" + "a".repeat(64);
  static final String DIGEST = "sogis/datenportal-themenintegrator-agent@sha256:" + "b".repeat(64);

  @BeforeEach
  void prepare() throws Exception {
    repo = temp.resolve("workspace");
    tools = temp.resolve("tools");
    log = temp.resolve("calls");
    Files.createDirectories(repo.resolve("bin"));
    Files.createDirectories(tools);
    Files.copy(
        Fixtures.ROOT.resolve("bin/datenportal-agent"), repo.resolve("bin/datenportal-agent"));
    Json.write(
        tools.resolve("docker"),
        """
        #!/usr/bin/env bash
        printf '<%s>\\n' "$@" >> "$TEST_LOG"
        case "$1" in
          context) printf 'unix://%s\\n' "$TEST_SOCKET";;
          version) echo 29;;
          pull) [[ ${TEST_FAILURE:-} != pull ]] || exit 71;;
          image)
            if [[ " $* " == *' .RepoDigests'* ]]; then echo "$TEST_DIGEST"
            elif [[ " $* " == *' --format '* ]]; then echo "$TEST_ID"
            else [[ ${TEST_FAILURE:-} != missing ]] || exit 1; fi;;
          run)
            if [[ " $* " == *' --help '* ]]; then [[ ${TEST_FAILURE:-} != smoke ]] || exit 72
            elif [[ " $* " == *' --entrypoint stat '* ]]; then echo 0
            elif [[ " $* " == *' java '* ]]; then echo '{"ok":true}'; fi;;
          *) echo 'unexpected docker command' >&2; exit 80;;
        esac
        """);
    tools.resolve("docker").toFile().setExecutable(true);
    socket = Path.of("/tmp/release-launcher-" + UUID.randomUUID().toString().substring(0, 8));
    server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    server.bind(UnixDomainSocketAddress.of(socket));
  }

  @AfterEach
  void close() throws Exception {
    if (server != null) server.close();
    if (socket != null) Files.deleteIfExists(socket);
  }

  int launch(String failure, String... args) throws Exception {
    var command =
        new ArrayList<>(List.of("bash", repo.resolve("bin/datenportal-agent").toString()));
    command.addAll(List.of(args));
    var pb = new ProcessBuilder(command).redirectErrorStream(true);
    var env = pb.environment();
    env.remove("DOCKER_HOST");
    env.remove("DOCKER_CONTEXT");
    env.put("PATH", tools + ":" + System.getenv("PATH"));
    env.put("TEST_LOG", log.toString());
    env.put("TEST_SOCKET", socket.toString());
    env.put("TEST_ID", ID);
    env.put("TEST_DIGEST", DIGEST);
    env.put("TEST_FAILURE", failure);
    var p = pb.start();
    p.getInputStream().readAllBytes();
    return p.waitFor();
  }

  Path selection() {
    return repo.resolve(".datenportal-integrator/launcher/release-image");
  }

  @Test
  void firstStartPinsReleaseAndNextStartDoesNotPullOrBuild() throws Exception {
    assertEquals(0, launch("", "serve"));
    assertTrue(Json.contents(selection()).contains(DIGEST + "\n" + ID));
    assertTrue(Json.contents(log).contains("<pull>"));
    Json.write(log, "");
    assertEquals(0, launch("", "serve"));
    String calls = Json.contents(log);
    assertFalse(calls.contains("<pull>"));
    assertFalse(calls.contains("<build>"));
    assertFalse(calls.contains("gradlew"));
    assertTrue(calls.contains("</opt/datenportal-agent/build/libs/datenportal-integrator.jar>"));
    assertTrue(calls.contains("--runtime\ndocker"));
  }

  @Test
  void failedUpdateLeavesSelectionUntouchedAndSuccessfulUpdateChecksStartup() throws Exception {
    assertEquals(0, launch("", "serve"));
    String original = Json.contents(selection());
    for (String failure : List.of("pull", "smoke")) {
      assertNotEquals(0, launch(failure, "--update-agent", "serve"));
      assertEquals(original, Json.contents(selection()));
      assertFalse(Files.exists(selection().resolveSibling("build.lock")));
    }
    Json.write(log, "");
    assertEquals(0, launch("", "--update-agent", "serve"));
    assertTrue(Json.contents(log).contains("<--help>"));
  }

  @Test
  void missingLocalImageRestoresRecordedDigest() throws Exception {
    assertEquals(0, launch("", "serve"));
    Json.write(log, "");
    assertEquals(0, launch("missing", "serve"));
    assertTrue(Json.contents(log).contains("<pull>\n<" + DIGEST + ">"));
    assertFalse(Json.contents(log).contains(":latest>"));
  }
}
