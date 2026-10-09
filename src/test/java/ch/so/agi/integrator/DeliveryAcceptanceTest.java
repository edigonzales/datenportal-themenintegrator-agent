package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeliveryAcceptanceTest {
  @TempDir Path temp;

  @Test
  void CleanupCannotSelectAnotherRootOrComposeProject() throws Exception {
    Json.write(
        temp.resolve("config.toml"), "root=\".\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n");
    var acceptance = new DeliveryAcceptance(new Settings(temp.resolve("config.toml")));
    Path fixture = temp.resolve(".datenportal-integrator/delivery-acceptance/fixture");
    Files.createDirectories(fixture);
    Json.write(fixture.resolve("SYNTHETIC_TEST_FIXTURE"), DeliveryAcceptance.STATEMENT);
    var record =
        Json.map(
            "directory",
            fixture.toString(),
            "synthetic_fixture",
            true,
            "root",
            temp.toString(),
            "stack",
            fixture.resolve("datenportal-dev-stack").toString(),
            "project",
            "delivery-test-fixture");
    Json.atomic(fixture.resolve("fixture.json"), record);
    assertEquals(
        "unsafe_path", assertThrows(Problem.class, () -> acceptance.cleanup(fixture)).code);
    record.put("root", fixture.resolve("agent").toString());
    record.put("project", "datenportal-dev-stack");
    Json.atomic(fixture.resolve("fixture.json"), record);
    assertEquals(
        "unsafe_path", assertThrows(Problem.class, () -> acceptance.cleanup(fixture)).code);
    assertTrue(Files.exists(temp.resolve("config.toml")));
    record.put("project", "delivery-test-fixture");
    Json.atomic(fixture.resolve("fixture.json"), record);
    Json.write(
        fixture.resolve("agent/config/local.toml"),
        "root=" + Json.text(temp.toString()) + "\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n");
    assertEquals(
        "unsafe_path", assertThrows(Problem.class, () -> acceptance.cleanup(fixture)).code);
    Files.createSymbolicLink(fixture.resolve("datenportal-themenrepo"), temp);
    assertEquals(
        "unsafe_path", assertThrows(Problem.class, () -> acceptance.fixture(fixture)).code);
  }
}
