package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitializerTest {
  @TempDir Path temp;

  @Test
  void smokeReuseRequiresCurrentFingerprintAndIntactEvidence() {
    Path report = temp.resolve("report.json");
    Json.atomic(report, Json.map("valid", true, "synthetic_fixture", true));
    var smoke =
        Json.map(
            "valid",
            true,
            "fingerprint",
            "current",
            "report",
            report.toString(),
            "report_sha256",
            Json.sha(report));
    assertTrue(Initializer.smokeCurrent(smoke, "current"));
    assertFalse(Initializer.smokeCurrent(smoke, "changed"));
    Json.atomic(report, Json.map("valid", false));
    assertFalse(Initializer.smokeCurrent(smoke, "current"));
  }

  @Test
  void harnessPreservesOtherServersAndCredentialsAndUsesSelectedConfig() throws Exception {
    var f = new Fixtures(temp);
    var harness = new Harness(f.settings);
    String original =
        "model=\"example\"\n[mcp_servers.other]\ncommand=\"other\"\n"
            + "[mcp_servers.datenportal_integrator]\ncommand=\"java\"\nargs=[\"-jar\",\"build/libs/datenportal-integrator.jar\",\"serve\"]\nenv_vars=[\"EXTRA_TOKEN\"]\n"
            + "[mcp_servers.datenportal_integrator.env]\nSECRET_MAPPING=\"preserved\"\nDATENPORTAL_FORWARD_ENV=\"EXTRA_TOKEN\"\n";
    String updated = harness.updateToml(original);
    var parsed = org.tomlj.Toml.parse(updated);
    assertFalse(parsed.hasErrors(), updated);
    assertEquals("other", parsed.getString("mcp_servers.other.command"));
    assertEquals(
        "preserved", parsed.getString("mcp_servers.datenportal_integrator.env.SECRET_MAPPING"));
    var arguments = parsed.getArray("mcp_servers.datenportal_integrator.args").toList();
    assertEquals(
        f.settings.file,
        f.settings.root.resolve(arguments.get(arguments.indexOf("--config") + 1).toString()));
    assertTrue(
        parsed
            .getString("mcp_servers.datenportal_integrator.env.DATENPORTAL_FORWARD_ENV")
            .contains("EXTRA_TOKEN"));
    assertTrue(
        parsed
            .getString("mcp_servers.datenportal_integrator.env.DATENPORTAL_FORWARD_ENV")
            .contains("SECRET_MAPPING"));
    assertEquals(updated, harness.updateToml(updated));
  }

  @Test
  void unknownHarnessEntryIsNotOverwritten() throws Exception {
    var f = new Fixtures(temp);
    assertThrows(
        Problem.class,
        () ->
            new Harness(f.settings)
                .updateToml(
                    "[mcp_servers.datenportal_integrator]\ncommand=\"custom-wrapper\"\nargs=[]\n"));
  }

  @Test
  void syntheticSettingsHaveNoPublicationProfilesAndKeepSourceIntact() throws Exception {
    var f = new Fixtures(temp);
    var isolated = new Settings(f.settings, temp.resolve("private"), temp.resolve("private-runs"));
    assertTrue(Json.obj(isolated.values.get("environments")).isEmpty());
    assertFalse(Json.obj(f.settings.values.get("environments")).isEmpty());
    assertEquals(temp.resolve("private"), isolated.topics);
    assertEquals(f.settings.file, isolated.file);
  }

  @Test
  void infrastructureFailureCannotPassNegativeSmoke() {
    Path log = temp.resolve("negative.log");
    var report = Json.map("valid", false, "log", log.toString());
    Json.write(log, "> Task :validateThemenCsv FAILED\nError: connection refused\n");
    assertFalse(Smoke.validationRejected(report));
    Json.write(
        log,
        "> Task :validateThemenCsv FAILED\nline 3: value <not-a-number> is not a number in attribute Jahr\n");
    assertTrue(Smoke.validationRejected(report));
    report.put("valid", true);
    assertFalse(Smoke.validationRejected(report));
  }
}
