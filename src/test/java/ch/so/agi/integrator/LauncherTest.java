package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {
  @TempDir Path temp;

  @Test
  void rejectsRetiredDockerOptions() {
    var result =
        new ProcessRunner()
            .run(
                List.of(
                    "bash",
                    Fixtures.ROOT.resolve("bin/datenportal-agent").toString(),
                    "--runtime",
                    "docker"),
                Fixtures.ROOT,
                10,
                null,
                Map.of("JAVA_HOME", System.getProperty("java.home")));
    assertEquals(2, result.exitCode());
    assertTrue(result.output().contains("retired"));
  }

  @Test
  void installedLauncherPreservesWorkspaceAndArgumentsWithSpaces() throws Exception {
    Path home = temp.resolve("package space"), work = temp.resolve("work space");
    Files.createDirectories(home.resolve("bin"));
    Files.createDirectories(home.resolve("lib"));
    Files.createDirectories(work);
    Files.copy(
        Fixtures.ROOT.resolve("bin/datenportal-agent"), home.resolve("bin/datenportal-agent"));
    Files.copy(
        Fixtures.ROOT.resolve("build/libs/datenportal-integrator.jar"),
        home.resolve("lib/datenportal-integrator.jar"));
    var result =
        new ProcessRunner()
            .run(
                List.of("bash", home.resolve("bin/datenportal-agent").toString(), "--version"),
                work,
                15,
                null,
                Map.of("JAVA_HOME", System.getProperty("java.home")));
    assertEquals(0, result.exitCode(), result.output());
    assertFalse(Files.exists(work.resolve("build")));
  }
}
