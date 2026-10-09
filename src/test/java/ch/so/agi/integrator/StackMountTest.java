package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class StackMountTest {
  @TempDir Path temp;

  Stack stack(String operatingSystem) throws Exception {
    var f = new Fixtures(temp);
    return new Stack(
        f.settings,
        new ProcessRunner() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            assertEquals(List.of("docker", "info", "--format", "{{.OperatingSystem}}"), args);
            return operatingSystem;
          }
        });
  }

  @Test
  void DesktopAliasResolvesSpacesAndSymlinks() throws Exception {
    var s = stack("Docker Desktop");
    Path real = temp.resolve("topic repo with spaces"), alias = temp.resolve("alias");
    Files.createDirectory(real);
    Files.createSymbolicLink(alias, real);
    assertEquals(real.toRealPath(), s.mountSource(alias.toString()));
    assertEquals(real.toRealPath(), s.mountSource("/host_mnt" + alias));
  }

  @Test
  void NativeLinuxAndMissingDesktopSourcesAreNeverGuessed() throws Exception {
    var s = stack("Ubuntu 24.04");
    assertEquals(
        "stack_mount_unavailable",
        assertThrows(Problem.class, () -> s.mountSource("/host_mnt" + temp)).code);
    assertEquals(
        "stack_mount_unavailable",
        assertThrows(Problem.class, () -> s.mountSource("relative/path")).code);
    assertEquals(
        "stack_mount_unavailable",
        assertThrows(Problem.class, () -> s.mountSource("/missing-source")).code);
  }

  @Test
  void DesktopAliasCannotAuthorizeADifferentRepository() throws Exception {
    var f = new Fixtures(temp);
    Path foreign = Files.createDirectory(temp.resolve("foreign"));
    var calls = new ArrayList<List<String>>();
    var s =
        new Stack(
            f.settings,
            new ProcessRunner() {
              @Override
              public String checked(List<String> args, Path cwd, int timeout) {
                calls.add(args);
                if (args.contains("-aq")) return "fixture-container";
                if (args.contains("inspect"))
                  return Json.text(
                      Json.map(
                          "mounts",
                          List.of(Json.map("type", "bind", "source", "/host_mnt" + foreign)),
                          "working_tree",
                          true,
                          "running",
                          true));
                if (args.contains("info")) return "Docker Desktop";
                throw new AssertionError("Foreign stack must not be changed");
              }
            });
    assertEquals("stack_mismatch", assertThrows(Problem.class, s::ensure).code);
    assertFalse(calls.stream().anyMatch(a -> a.contains("--force-recreate") || a.contains("up")));
  }
}
