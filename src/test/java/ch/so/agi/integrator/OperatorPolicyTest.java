package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;

class OperatorPolicyTest {
  @TempDir Path temp;
  Settings settings;

  @BeforeEach
  void setup() {
    Json.atomic(
        temp.resolve(OperatorWorkspace.MANIFEST),
        Json.map("schema_version", 1, "kind", "operator"));
    Json.write(
        temp.resolve("config/local.toml"),
        "root=\"..\"\ntopics_repo=\"../repo\"\nstack_repo=\"../stack\"\n[datasheet]\ntransport=\"http\"\nurl=\"http://127.0.0.1:8000/mcp\"\n");
    settings = new Settings(temp.resolve("config/local.toml"));
  }

  @Test
  void installsBothClientsPreservesPersonalFieldsAndIsIdempotent() {
    Json.write(temp.resolve(".codex/config.toml"), "model=\"personal-model\"\n");
    Json.atomic(
        temp.resolve("opencode.json"),
        Json.map("model", "personal/model", "mcp", Json.map("servers", Json.map())));
    var harness = new Harness(settings);
    harness.install();
    String codex = Json.contents(temp.resolve(".codex/config.toml"));
    String open = Json.contents(temp.resolve("opencode.json"));
    harness.install();
    assertEquals(codex, Json.contents(temp.resolve(".codex/config.toml")));
    assertEquals(open, Json.contents(temp.resolve("opencode.json")));
    var parsed = Toml.parse(codex);
    assertFalse(parsed.hasErrors(), parsed.errors().toString());
    assertEquals("personal-model", parsed.getString("model"));
    assertEquals(false, parsed.getBoolean("features.shell_tool"));
    assertEquals(false, parsed.getBoolean("features.unified_exec"));
    assertEquals("datenportal", parsed.getString("default_permissions"));
    assertTrue(parsed.contains("mcp_servers.datenportal_workspace"));
    var json = Json.read(open);
    assertEquals("personal/model", json.get("model"));
    assertEquals("datenportal", json.get("default_agent"));
    var servers = Json.obj(Json.obj(json.get("mcp")).get("servers"));
    assertEquals(false, Json.obj(servers.get("datenportal_integrator")).get("codemode"));
    assertEquals(false, Json.obj(servers.get("datenportal_workspace")).get("codemode"));
    assertEquals(true, Json.obj(OperatorPolicy.check(settings)).get("configuration_valid"));
  }

  @Test
  void disablesAdditionalProjectMcpsWithoutChangingTheirSettings() {
    Json.write(
        temp.resolve(".codex/config.toml"),
        "[mcp_servers.node_repl]\ncommand=\"personal-command\"\nenabled=true\n");
    var harness = new Harness(settings);
    harness.install();
    String once = Json.contents(temp.resolve(".codex/config.toml"));
    harness.install();
    assertEquals(once, Json.contents(temp.resolve(".codex/config.toml")));
    var parsed = Toml.parse(once);
    assertEquals(false, parsed.getBoolean("mcp_servers.node_repl.enabled"));
    assertEquals("personal-command", parsed.getString("mcp_servers.node_repl.command"));
    OperatorPolicy.check(settings);
  }

  @Test
  void oldSandboxStopsBeforeEitherClientIsChanged() {
    Json.write(temp.resolve(".codex/config.toml"), "sandbox_mode=\"danger-full-access\"\n");
    Json.atomic(temp.resolve("opencode.json"), Json.map("model", "existing"));
    String before = Json.contents(temp.resolve("opencode.json"));
    assertEquals(
        "operator_policy_conflict",
        assertThrows(Problem.class, () -> new Harness(settings).install()).code);
    assertEquals(before, Json.contents(temp.resolve("opencode.json")));
    assertFalse(Files.exists(temp.resolve(".datenportal-integrator/harness-backups")));
  }

  @Test
  void changedClientPolicyIsReportedAndBroadAgentRulesCannotReopenShell() {
    new Harness(settings).install();
    var json = Json.read(temp.resolve("opencode.json"));
    Json.obj(json.get("experimental")).remove("policies");
    Json.atomic(temp.resolve("opencode.json"), json);
    assertEquals(
        "operator_policy_conflict",
        assertThrows(Problem.class, () -> OperatorPolicy.check(settings)).code);
    var configured =
        OperatorPolicy.openCode(
            Json.map(
                "agents",
                Json.map(
                    "build",
                    Json.map(
                        "permissions",
                        List.of(
                            Json.map("action", "shell", "resource", "*", "effect", "allow"))))));
    var policies = Json.list(Json.obj(configured.get("experimental")).get("policies"));
    assertTrue(
        policies.contains(
            Json.map("action", "permission", "resource", "shell:*", "effect", "deny")));
  }
}
