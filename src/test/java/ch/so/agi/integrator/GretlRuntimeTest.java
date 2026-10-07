package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class GretlRuntimeTest {
  @TempDir Path temp;
  static final String A = "sha256:" + "a".repeat(64), B = "sha256:" + "b".repeat(64);
  static final String DESCRIPTION =
      "/opt/java/openjdk17\n/opt/datenportal/offline-bundle/jars\n/opt/datenportal/offline-bundle/gradle-user-home\n17\nb.jar="
          + "b".repeat(64)
          + "\na.jar="
          + "a".repeat(64)
          + "\n";

  Settings settings() {
    Path config = temp.resolve("local.toml");
    Json.write(
        config,
        "root=\".\"\ntopics_repo=\"topics\"\nstack_repo=\"missing-stack\"\n[gretl]\nimage=\"sogis/jenkins:v1\"\n");
    try {
      Files.createDirectories(temp.resolve("config"));
      Files.createDirectories(temp.resolve("validation"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new Settings(config);
  }

  class Docker extends ProcessRunner {
    final List<List<String>> calls = new ArrayList<>();
    String tag = A, jenkinsImage = A;
    String description = DESCRIPTION;
    boolean failedTask, timedOut, failedCopy;

    @Override
    public Result run(List<String> args, Path cwd, int seconds, Path log) {
      calls.add(args);
      if (args.size() > 2 && args.get(1).equals("image")) {
        String id = args.getLast().startsWith("sha256:") ? args.getLast() : tag;
        return new Result(
            0,
            Json.text(
                Json.map(
                    "image_id",
                    id,
                    "digests",
                    List.of("sogis/jenkins@" + id),
                    "os",
                    "linux",
                    "architecture",
                    "arm64")));
      }
      if (args.contains(GretlRuntime.DESCRIBE)) return new Result(0, description);
      if (args.contains("container") && args.contains("inspect"))
        return new Result(
            0,
            args.contains("{{.Image}}")
                ? jenkinsImage
                : Json.hash(temp.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      if (args.get(1).equals("ps")) return new Result(0, "existing-jenkins");
      if (args.get(1).equals("cp") && failedCopy) return new Result(1, "copy failed");
      if (args.contains("validateThemenCsv")) {
        Json.write(
            log,
            "FIXTURE GRETL log\n"
                + (failedTask ? "validateThemenCsv FAILED" : "INTEGRATOR_CSV_VALIDATED=true"));
        if (timedOut) throw new Problem("command_timeout", "FIXTURE timeout");
        return new Result(failedTask ? 1 : 0, Json.contents(log));
      }
      if (log != null) Json.write(log, "FIXTURE\n");
      return new Result(0, "");
    }
  }

  @Test
  void setupPinsImageWithoutStartingStackAndIgnoresLegacyHostPaths() {
    var s = settings();
    s.values.put("gretl_java_home", "/missing-java17");
    s.values.put("gretl_offline_jars", "/missing-jars");
    var p = new Docker();
    var runtime = new GretlRuntime(s, p);
    runtime.setup(false);
    assertEquals(17, runtime.selected(false).get("java_version"));
    assertFalse(p.calls.stream().anyMatch(c -> c.contains("scripts/up.sh") || c.contains("-v")));
    String before = s.fingerprint();
    p.tag = B;
    runtime.setup(false);
    assertEquals(before, s.fingerprint());
    assertFalse(p.calls.stream().anyMatch(c -> c.contains("pull")));
    runtime.setup(true);
    assertNotEquals(before, s.fingerprint());
    assertEquals(B, runtime.selected(false).get("image_id"));
    String activeFingerprint = s.fingerprint();
    s.values.put("gretl_java_home", "/different-missing-java17");
    assertEquals(activeFingerprint, s.fingerprint());
    assertEquals(List.of("gretl_java_home", "gretl_offline_jars"), GretlRuntime.deprecated(s));
  }

  @Test
  void bundleInventoryUsesSameCanonicalHashAsJenkinsTask() {
    var data = GretlRuntime.parseDescription(DESCRIPTION);
    String expected =
        Json.hash(
            ("a.jar=" + "a".repeat(64) + "\nb.jar=" + "b".repeat(64) + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(expected, data.get("sha256"));
    assertThrows(
        Problem.class,
        () -> GretlRuntime.parseDescription(DESCRIPTION.replace("\n17\n", "\n21\n")));
    assertThrows(
        Problem.class,
        () -> GretlRuntime.parseDescription(DESCRIPTION.replace("b.jar=", "../../b.jar=")));
  }

  @Test
  void mapsOnlyExplicitFileArgumentsIncludingSpacesAndKeepsOtherValues() throws Exception {
    Path csv = temp.resolve("CSV with spaces.csv"), init = temp.resolve("extra init.gradle");
    var inputs = new ArrayList<GretlRuntime.Input>();
    var mapped =
        GretlRuntime.mapProperties(
            List.of("-PdataFile=" + csv, "--all", "-I", init.toString(), "-Pdataset=topic"),
            inputs);
    assertEquals(
        List.of(
            "-PdataFile=/tmp/integrator-inputs/data.csv",
            "--all",
            "-I",
            "/tmp/integrator-inputs/init-3.gradle",
            "-Pdataset=topic"),
        mapped);
    assertEquals(csv, inputs.getFirst().source());
    assertEquals(init, inputs.getLast().source());
    assertThrows(Problem.class, () -> GretlRuntime.mapProperties(List.of("-I"), new ArrayList<>()));
  }

  @Test
  void mismatchedJenkinsImageAndBundleStopWithoutMutation() {
    var s = settings();
    var p = new Docker();
    var runtime = new GretlRuntime(s, p);
    runtime.setup(false);
    assertTrue(Json.bool(runtime.matchJenkins(), "valid", false));
    p.jenkinsImage = B;
    assertEquals("gretl_runtime_mismatch", assertThrows(Problem.class, runtime::matchJenkins).code);
    p.jenkinsImage = A;
    p.description = DESCRIPTION.replace("b.jar=" + "b".repeat(64), "b.jar=" + "c".repeat(64));
    assertEquals("gretl_runtime_mismatch", assertThrows(Problem.class, runtime::matchJenkins).code);
    assertFalse(
        p.calls.stream().anyMatch(c -> c.contains("buildWithParameters") || c.contains("restart")));
  }

  @Test
  void changedReferenceRequiresExplicitPreparation() {
    var s = settings();
    var runtime = new GretlRuntime(s, new Docker());
    runtime.setup(false);
    Json.obj(s.values.get("gretl")).put("image", "sogis/jenkins:v2");
    assertEquals(
        "gretl_setup_required", assertThrows(Problem.class, () -> runtime.selected(false)).code);
  }

  @Test
  void taskFailureAndTimeoutAlwaysCleanOwnContainerAndKeepLog() throws Exception {
    var s = settings();
    var p = new Docker();
    var runtime = new GretlRuntime(s, p);
    runtime.setup(false);
    Path snapshot = temp.resolve("snapshot"), csv = temp.resolve("input.csv");
    Files.createDirectories(snapshot);
    Json.write(csv, "Jahr\n2025\n");
    var f = new Fixtures(temp.resolve("fixture"));
    String id = f.metadataOnly();
    var run = f.workflow.store.edit(id, r -> new LinkedHashMap<>(r));
    p.failedTask = true;
    var report =
        runtime.gradle(
            f.workflow,
            run,
            snapshot,
            "validateThemenCsv",
            List.of("-PdataFile=" + csv),
            "negative");
    assertFalse(Json.bool(report, "valid", true));
    assertTrue(Files.isRegularFile(Path.of(Json.required(report, "log"))));
    assertEquals(A, Json.obj(report.get("runtime")).get("image_id"));
    assertTrue(p.calls.stream().anyMatch(c -> c.contains("rm")));
    p.calls.clear();
    p.timedOut = true;
    assertEquals(
        "command_timeout",
        assertThrows(
                Problem.class,
                () ->
                    runtime.gradle(
                        f.workflow,
                        run,
                        snapshot,
                        "validateThemenCsv",
                        List.of("-PdataFile=" + csv),
                        "timeout"))
            .code);
    assertTrue(p.calls.stream().anyMatch(c -> c.contains("rm")));
    assertTrue(
        p.calls.stream().anyMatch(c -> c.get(1).equals("cp") && c.get(2).contains("/build/.")),
        "Capture reports even after timeout");
    assertTrue(Files.isRegularFile(f.workflow.directory(run).resolve("validation/timeout.log")));
  }
}
