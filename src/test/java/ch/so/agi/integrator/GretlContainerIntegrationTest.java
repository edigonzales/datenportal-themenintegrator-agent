package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class GretlContainerIntegrationTest {
  @TempDir Path temp;

  @Test
  void preparesAndRunsWithoutStackOrHostJava17OrLocalJars() throws Exception {
    Settings source = Settings.load(Fixtures.ROOT.resolve("config/local.toml").toString());
    Path config = temp.resolve("local.toml");
    Json.write(
        config,
        "root=\".\"\ntopics_repo="
            + Json.text(source.topics.toString())
            + "\nstack_repo=\"missing-stack\"\ngretl_java_home=\"/unavailable-java17\"\ngretl_offline_jars=\"/unavailable-jars\"\n[gretl]\nimage="
            + Json.text(GretlRuntime.reference(source))
            + "\n");
    var s = new Settings(config);
    var process = new ProcessRunner();
    assertFalse(Files.exists(s.stack));
    var runtime = new GretlRuntime(s, process);
    runtime.setup(false);
    var expected = runtime.selected(true);
    assertEquals(17, expected.get("java_version"));
    try (var w = new Workflow(s)) {
      Path copy = temp.resolve("snapshot");
      Workspace.copy(source.topics, copy);
      var run = w.store.create("organization", "agi", null, null, "local");
      var report =
          runtime.gradle(w, run, copy, "tasks", List.of("--all"), "without-stack-or-host-java17");
      assertTrue(Json.bool(report, "valid", false), Json.pretty(report));
      assertEquals(expected, Json.obj(report.get("runtime")));
      assertTrue(Files.exists(Path.of(Json.required(report, "log"))));
    }
    assertFalse(Files.exists(s.stack));
    assertFalse(Files.exists(temp.resolve(".datenportal-integrator/tools/gretl-runtime/jars")));
  }
}
