package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic registry responses; no credentials and no network. */
class PublicationTest {
  @TempDir Path temp;

  record Result(int exit, String calls) {}

  Result run(String action, String scenario) throws Exception {
    Path tools = temp.resolve("tools");
    Files.createDirectories(tools);
    Json.write(
        tools.resolve("docker"),
"""
        #!/usr/bin/env bash
        printf '%s\\n' "$*" >> "$TEST_LOG"
        case "$1 $2" in
          'buildx imagetools')
            if [[ " $* " == *' --raw '* ]]; then
              if [[ "$SCENARIO" == unavailable ]]; then echo 'connection refused' >&2; exit 1; fi
if [[ "$SCENARIO" == absent && ! -f "$TEST_LOG.published" ]]; then echo 'manifest unknown' >&2; exit 1; fi
              echo '{"manifests":[]}'
            elif [[ " $* " == *' create '* ]]; then exit 0
            else echo "sha256:$(printf 'a%.0s' {1..64})"; fi;;
          'image inspect')
if [[ " $* " == *revision* ]]; then
              if [[ "$SCENARIO" == different ]]; then echo other-commit; else echo expected-commit; fi
            elif [[ "$SCENARIO" == newer ]]; then echo 0.1.99
            else echo 0.1.12; fi;;
'pull --platform') exit 0;;
          'buildx build') touch "$TEST_LOG.published";;
          'run --rm') if [[ " $* " == *' --version '* ]]; then echo 0.1.12; fi;;
          *) echo unexpected >&2; exit 91;;
        esac
        """);
    Json.write(tools.resolve("jq"), "#!/bin/sh\nexit 0\n");
    tools.resolve("docker").toFile().setExecutable(true);
    tools.resolve("jq").toFile().setExecutable(true);
    Path log = temp.resolve("calls");
    var pb =
        new ProcessBuilder("bash", "runtime/publish-image.sh", action)
            .directory(Fixtures.ROOT.toFile())
            .redirectErrorStream(true);
    pb.environment()
        .putAll(
            Map.of(
                "PATH",
                tools + ":" + System.getenv("PATH"),
                "TEST_LOG",
                log.toString(),
                "SCENARIO",
                scenario,
                "IMAGE",
                "synthetic/agent",
                "AGENT_VERSION",
                "0.1.12",
                "GITHUB_SHA",
                "expected-commit"));
    var p = pb.start();
    p.getInputStream().readAllBytes();
    return new Result(p.waitFor(), Json.contents(log));
  }

  @Test
  void olderRunNeverPromotesLatest() throws Exception {
    var result = run("latest", "newer");
    assertEquals(0, result.exit());
    assertFalse(result.calls().contains("imagetools create"));
  }

  @Test
  void differentCommitCannotOverwriteExistingVersion() throws Exception {
    var result = run("version", "different");
    assertNotEquals(0, result.exit());
    assertFalse(result.calls().contains("buildx build"));
  }

  @Test
  void registryFailureIsNotAnAbsentTag() throws Exception {
    var result = run("version", "unavailable");
    assertNotEquals(0, result.exit());
    assertFalse(result.calls().contains("buildx build"));
  }

  @Test
  void publishesAbsentVersionThenChecksBothArchitectures() throws Exception {
    var result = run("version", "absent");
    assertEquals(0, result.exit());
    assertTrue(result.calls().contains("buildx build --push --platform linux/amd64,linux/arm64"));
    assertTrue(result.calls().contains("pull --platform linux/amd64 synthetic/agent:0.1.12"));
    assertTrue(result.calls().contains("pull --platform linux/arm64 synthetic/agent:0.1.12"));
  }

  @Test
  void rerunVerifiesMatchingVersionWithoutRebuilding() throws Exception {
    var result = run("version", "matching");
    assertEquals(0, result.exit());
    assertFalse(result.calls().contains("buildx build"));
  }
}
