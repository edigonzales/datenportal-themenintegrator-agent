package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class HarnessTest {
  @TempDir Path temp;
  Harness harness;

  @BeforeEach
  void isolatedRoot() {
    Path config = temp.resolve("config/selected.toml");
    Json.write(
        config,
        "root=\"..\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n[datasheet]\ntransport=\"stdio\"\n");
    harness = new Harness(new Settings(config));
    assertEquals(temp, harness.s.root);
    harness.s.values.put(
        "environments", Json.map("fixture", Json.map("token_env", "FIXTURE_TOKEN")));
  }

  Map<String, Object> server() {
    return Json.map("type", "local", "command", List.of("bin/datenportal-agent", "serve"));
  }

  Map<String, Object> legacy(Map<String, Object> entry) {
    return Json.map("mcp", Json.map("datenportal_integrator", entry));
  }

  Map<String, Object> v2(Map<String, Object> entry) {
    return Json.map("mcp", Json.map("servers", Json.map("datenportal_integrator", entry)));
  }

  Map<String, Object> entry(Map<String, Object> config) {
    var mcp = Json.obj(config.get("mcp"));
    assertFalse(mcp.containsKey("datenportal_integrator"));
    return Json.obj(Json.obj(mcp.get("servers")).get("datenportal_integrator"));
  }

  @Test
  void proposalUsesV2AndAbsoluteSelectedPaths() {
    var proposal = harness.proposal();
    var entry = entry(Json.obj(proposal.get("opencode")));
    var command = Json.strings(entry.get("command"));
    assertEquals(harness.launcher(), command.getFirst());
    assertEquals(harness.s.file.toString(), command.get(command.indexOf("--config") + 1));
    assertEquals(harness.arguments(), command.subList(1, command.size()));
    assertFalse(entry.containsKey("enabled"));
    assertEquals(false, entry.get("disabled"));
    assertEquals(Harness.openCodeTimeouts(), entry.get("timeout"));
    assertTrue(
        Json.obj(Json.obj(proposal.get("codex")).get("mcp_servers"))
            .containsKey("datenportal_integrator"));
  }

  @Test
  void newInstallUsesProjectPathsAndDeliveryReader() {
    harness.install();
    var entry = entry(Json.read(temp.resolve("opencode.json")));
    assertEquals(false, entry.get("disabled"));
    assertEquals(Harness.openCodeTimeouts(), entry.get("timeout"));
    var command = DeliveryAcceptance.openCodeCommand(temp);
    assertEquals("bin/datenportal-agent", command.getFirst());
    assertEquals(harness.projectArguments(), command.subList(1, command.size()));
    assertEquals("config/selected.toml", command.get(command.indexOf("--config") + 1));
    assertTrue(Files.isRegularFile(temp.resolve(".codex/config.toml")));
  }

  @Test
  void preservesV2CustomSettingsOtherServersAndGlobals() {
    var original = server();
    original.putAll(
        Json.map(
            "disabled",
            true,
            "timeout",
            Json.map("startup", 75000),
            "cwd",
            ".",
            "codemode",
            false,
            "protocol",
            "legacy"));
    var config = v2(original);
    config.put("model", "fixture-model");
    var mcp = Json.obj(config.get("mcp"));
    mcp.put("timeout", Json.map("execution", 61000));
    var foreign = Json.map("type", "remote", "url", "https://example.org/mcp", "disabled", true);
    Json.obj(mcp.get("servers")).put("other", foreign);
    var updated = harness.updateOpenCode(config);
    var result = entry(updated);
    for (String key : List.of("disabled", "cwd", "codemode", "protocol"))
      assertEquals(original.get(key), result.get(key));
    assertEquals(
        Json.map("startup", 75000, "catalog", 1800000, "execution", 1800000),
        result.get("timeout"));
    assertEquals("fixture-model", updated.get("model"));
    assertEquals(mcp.get("timeout"), Json.obj(updated.get("mcp")).get("timeout"));
    assertEquals(foreign, Json.obj(Json.obj(updated.get("mcp")).get("servers")).get("other"));
    assertEquals(updated, harness.updateOpenCode(updated));
  }

  @Test
  void externalSelectedConfigRemainsAbsolute() {
    Path config = temp.resolve("external.toml");
    Json.write(
        config,
        "root=\"project\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n[datasheet]\ntransport=\"stdio\"\n");
    var other = new Harness(new Settings(config));
    var command = Json.strings(entry(other.updateOpenCode(Json.map())).get("command"));
    assertEquals(config.toString(), command.get(command.indexOf("--config") + 1));
  }

  void unchangedOnConflict(Map<String, Object> config) throws Exception {
    Path open = temp.resolve("opencode.json"), codex = temp.resolve(".codex/config.toml");
    Json.atomic(open, config);
    Json.write(codex, "model=\"fixture\"\n");
    byte[] beforeOpen = Files.readAllBytes(open), beforeCodex = Files.readAllBytes(codex);
    Problem problem = assertThrows(Problem.class, harness::install);
    assertEquals("harness_conflict", problem.code);
    assertArrayEquals(beforeOpen, Files.readAllBytes(open));
    assertArrayEquals(beforeCodex, Files.readAllBytes(codex));
    assertFalse(Files.exists(temp.resolve(".datenportal-integrator/harness-backups")));
  }

  @Test
  void oldEntriesAndDuplicatesDoNotWriteEitherFile() throws Exception {
    for (String field : List.of("disabled", "timeout", "environment", "cwd")) {
      var old = server();
      var modern = server();
      switch (field) {
        case "disabled" -> {
          old.put("enabled", true);
          modern.put("disabled", true);
        }
        case "timeout" -> {
          old.put("timeout", 42000);
          modern.put("timeout", Json.map("execution", 42001));
        }
        case "environment" -> {
          old.put("environment", Json.map("SYNTHETIC", "first"));
          modern.put("environment", Json.map("SYNTHETIC", "second"));
        }
        case "cwd" -> {
          old.put("cwd", ".");
          modern.put("cwd", "other");
        }
      }
      var config = legacy(old);
      Json.obj(config.get("mcp")).put("servers", Json.map("datenportal_integrator", modern));
      unchangedOnConflict(config);
    }
  }

  @Test
  void unknownCommandsAndForeignLegacyServersDoNotWriteEitherFile() throws Exception {
    var unknown = Json.map("command", List.of("custom-wrapper", "serve"));
    unchangedOnConflict(legacy(unknown));
    unchangedOnConflict(v2(unknown));
    unchangedOnConflict(legacy(server()));
    unchangedOnConflict(
        v2(
            Json.map(
                "command",
                List.of("java", "-jar", "build/libs/datenportal-integrator.jar", "serve"))));
    unchangedOnConflict(Json.map("mcp", Json.map("other", server())));
  }

  @Test
  void malformedFieldsDoNotWriteEitherFile() throws Exception {
    for (var bad :
        List.of(
            Json.map("command", "launcher"),
            Json.map("command", List.of(42)),
            Json.map("command", List.of()),
            Json.map("type", "remote"),
            Json.map("enabled", "false"),
            Json.map("disabled", 1),
            Json.map("enabled", true, "disabled", true),
            Json.map("codemode", "false"),
            Json.map("cwd", 1),
            Json.map("protocol", null),
            Json.map("environment", null),
            Json.map("environment", Json.map("TOKEN", 1)),
            Json.map("timeout", 1000),
            Json.map("timeout", 0),
            Json.map("timeout", -1),
            Json.map("timeout", 1.5),
            Json.map("timeout", "1000"),
            Json.map("timeout", null),
            Json.map("timeout", Json.map("startup", false)),
            Json.map("timeout", Json.map("execution", 0)),
            Json.map("timeout", Json.map("other", 1)))) {
      var previous = server();
      previous.putAll(bad);
      unchangedOnConflict(v2(previous));
    }
    unchangedOnConflict(Json.map("mcp", null));
    unchangedOnConflict(Json.map("mcp", Json.map("servers", List.of())));
    unchangedOnConflict(Json.map("mcp", Json.map("timeout", 123)));
    unchangedOnConflict(legacy(Json.map()));
  }

  @Test
  void invalidJsonAndCodexConflictDoNotWriteEitherFile() throws Exception {
    Path open = temp.resolve("opencode.json"), codex = temp.resolve(".codex/config.toml");
    for (String invalid : List.of("{", "null", "[]")) {
      Json.write(open, invalid);
      Json.write(codex, "model=\"fixture\"\n");
      assertEquals("harness_conflict", assertThrows(Problem.class, harness::install).code);
      assertEquals(invalid, Json.contents(open));
      assertEquals("model=\"fixture\"\n", Json.contents(codex));
    }
    Json.atomic(open, v2(server()));
    String before = Json.contents(open);
    Json.write(codex, "[mcp_servers.datenportal_integrator]\ncommand=\"custom-wrapper\"\n");
    assertEquals("harness_conflict", assertThrows(Problem.class, harness::install).code);
    assertEquals(before, Json.contents(open));
    assertTrue(Json.contents(codex).contains("custom-wrapper"));
  }

  @Test
  void preservesCodexTimeoutsAndDeactivationAndRejectsInvalidTimeoutWithoutWrites() {
    Path codex = temp.resolve(".codex/config.toml"), open = temp.resolve("opencode.json");
    Json.write(
        codex,
        "[mcp_servers.datenportal_integrator]\ncommand=\"bin/datenportal-agent\"\nenabled=false\nstartup_timeout_sec=75.5\ntool_timeout_sec=2400\n");
    harness.install();
    var settings = org.tomlj.Toml.parse(Json.contents(codex));
    assertEquals(
        75.5, settings.getDouble("mcp_servers.datenportal_integrator.startup_timeout_sec"));
    assertEquals(2400L, settings.getLong("mcp_servers.datenportal_integrator.tool_timeout_sec"));
    assertEquals(false, settings.getBoolean("mcp_servers.datenportal_integrator.enabled"));
    Json.write(
        codex,
        Json.contents(codex)
            .replace("startup_timeout_sec=75.5", "startup_timeout_sec=\"invalid\""));
    String beforeCodex = Json.contents(codex), beforeOpen = Json.contents(open);
    assertEquals("harness_conflict", assertThrows(Problem.class, harness::install).code);
    assertEquals(beforeCodex, Json.contents(codex));
    assertEquals(beforeOpen, Json.contents(open));
  }

  @Test
  void backupsKeepOriginalBytesAndRepeatedInstallIsStable() throws Exception {
    Path open = temp.resolve("opencode.json"), codex = temp.resolve(".codex/config.toml");
    Json.atomic(open, v2(server()));
    Json.write(codex, "model=\"fixture\"\n");
    String beforeOpen = Json.contents(open), beforeCodex = Json.contents(codex);
    harness.install();
    Path backupDir = temp.resolve(".datenportal-integrator/harness-backups");
    try (var paths = Files.list(backupDir)) {
      var backups = paths.toList();
      assertEquals(2, backups.size());
      assertEquals(
          Set.of(beforeOpen, beforeCodex),
          new HashSet<>(backups.stream().map(Json::contents).toList()));
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
        for (Path backup : backups)
          assertEquals(
              PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(backup));
    }
    String afterOpen = Json.contents(open), afterCodex = Json.contents(codex);
    harness.install();
    assertEquals(afterOpen, Json.contents(open));
    assertEquals(afterCodex, Json.contents(codex));
    harness.install();
    try (var paths = Files.list(backupDir)) {
      assertEquals(4, paths.count());
    }
  }
}
