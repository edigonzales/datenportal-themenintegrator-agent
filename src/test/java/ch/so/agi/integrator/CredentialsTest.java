package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CredentialsTest {
  @TempDir Path temp;
  Settings settings;
  Credentials credentials;
  Map<String, String> environment = new HashMap<>();

  @BeforeEach
  void setup() {
    Path config = temp.resolve("local.toml");
    Json.write(
        config,
        """
        root="."
        topics_repo="topics"
        stack_repo="stack"
        [environments.local]
        kind="local"
        enabled=true
        jenkins_url="http://localhost:8081/jenkins"
        portal_url="http://localhost:8081"
        manifest_url="http://localhost:8081/current.json"
        username_env="SYNTHETIC_USER"
        token_env="SYNTHETIC_TOKEN"
        """);
    settings = new Settings(config);
    credentials = new Credentials(settings, environment::get);
  }

  @Test
  void atomicStoreHasPrivatePermissionsAndReloadsWithoutCaching() throws Exception {
    credentials.save("local", "fixture-user", "fixture-secret-one");
    assertEquals(
        PosixFilePermissions.fromString("rwx------"),
        Files.getPosixFilePermissions(credentials.directory));
    assertEquals(
        PosixFilePermissions.fromString("rw-------"),
        Files.getPosixFilePermissions(credentials.file));
    assertEquals(
        "fixture-secret-one", credentials.resolve("local", settings.environment("local")).token);
    credentials.save("local", "fixture-user", "fixture-secret-two");
    assertEquals(
        "fixture-secret-two", credentials.resolve("local", settings.environment("local")).token);
    String status = Json.text(credentials.status("local"));
    assertTrue(status.contains("local_store"));
    assertFalse(status.contains("fixture-user"));
    assertFalse(status.contains("fixture-secret"));
    assertFalse(
        credentials
            .resolve("local", settings.environment("local"))
            .toString()
            .contains("fixture-secret"));
    credentials.remove("local");
    assertEquals(false, credentials.status("local").get("credentials_present"));
  }

  @Test
  void EnvironmentOverridesStoreButNeverSuppliesHalfAPair() {
    credentials.save("local", "store-user", "store-secret");
    environment.put("SYNTHETIC_USER", "env-user");
    assertEquals(
        "credentials_partial",
        assertThrows(
                Problem.class, () -> credentials.resolve("local", settings.environment("local")))
            .code);
    environment.put("SYNTHETIC_TOKEN", "env-secret");
    assertEquals("env-secret", credentials.resolve("local", settings.environment("local")).token);
    assertEquals("environment", credentials.status("local").get("credentials_source"));
  }

  @Test
  void ChangedJenkinsTargetAndRemoteProfilesDoNotReceiveLocalSecrets() {
    credentials.save("local", "fixture-user", "fixture-secret");
    var env = Json.obj(Json.obj(settings.values.get("environments")).get("local"));
    env.put("jenkins_url", "http://localhost:9999/jenkins");
    assertEquals(
        "credentials_target_changed",
        assertThrows(
                Problem.class, () -> credentials.resolve("local", settings.environment("local")))
            .code);
    env.put("kind", "prod");
    assertEquals(
        "credentials_missing",
        assertThrows(
                Problem.class, () -> credentials.resolve("local", settings.environment("local")))
            .code);
    assertEquals(
        "local_only",
        assertThrows(
                Problem.class, () -> credentials.save("local", "fixture-user", "fixture-secret"))
            .code);
  }

  @Test
  void PermissionsAndSymlinksAreRejectedWithoutReadingValues() throws Exception {
    credentials.save("local", "fixture-user", "fixture-secret");
    Files.setPosixFilePermissions(credentials.file, PosixFilePermissions.fromString("rw-r--r--"));
    assertEquals("credential_store_permissions", credentials.status("local").get("error"));
    Files.delete(credentials.file);
    Path outside = temp.resolve("outside.json");
    Json.write(outside, "not a credential store");
    Files.createSymbolicLink(credentials.file, outside);
    assertEquals("credential_store_permissions", credentials.status("local").get("error"));
    assertEquals(
        "credential_store_permissions",
        assertThrows(
                Problem.class, () -> credentials.save("local", "fixture-user", "fixture-secret"))
            .code);
    assertEquals("not a credential store", Files.readString(outside));
    Files.delete(credentials.file);
    Files.delete(credentials.directory.resolve("store.lock"));
    Files.delete(credentials.directory);
    Files.createSymbolicLink(credentials.directory, temp);
    assertEquals("credential_store_permissions", credentials.status("local").get("error"));
  }

  @Test
  void ConcurrentChangesRetainBothProfiles() throws Exception {
    Json.obj(settings.values.get("environments"))
        .put("second", new LinkedHashMap<>(settings.environment("local")));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> credentials.save("local", "fixture-user", "first-secret"));
      var second = pool.submit(() -> credentials.save("second", "fixture-user", "second-secret"));
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
    }
    assertEquals("first-secret", credentials.resolve("local", settings.environment("local")).token);
    assertEquals(
        "second-secret", credentials.resolve("second", settings.environment("second")).token);
  }

  @Test
  void InvalidStoreAndMissingCredentialsProduceNoSecretOutput() throws Exception {
    assertEquals("credentials_missing", credentials.status("local").get("error"));
    assertFalse(Files.exists(credentials.directory));
    credentials.save("local", "fixture-user", "fixture-secret");
    Files.writeString(credentials.file, "{secret broken");
    assertEquals("credential_store_invalid", credentials.status("local").get("error"));
    assertFalse(Json.text(credentials.status("local")).contains("secret broken"));
  }

  @Test
  void CliAcceptsOnlyStdinOrExplicitEnvironment() throws Exception {
    environment.put("SYNTHETIC_USER", "fixture-user");
    environment.put("SYNTHETIC_TOKEN", "fixture-env-token");
    assertEquals(true, credentials.command(List.of("set", "local", "--from-env")).get("stored"));
    environment.clear();
    assertEquals(
        "fixture-env-token", credentials.resolve("local", settings.environment("local")).token);
    var original = System.in;
    try {
      System.setIn(
          new java.io.ByteArrayInputStream(
              "fixture-stdin-token\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      var result =
          credentials.command(
              List.of("set", "local", "--username", "fixture-user", "--token-stdin"));
      assertFalse(Json.text(result).contains("fixture-stdin-token"));
      assertEquals(
          "fixture-stdin-token", credentials.resolve("local", settings.environment("local")).token);
    } finally {
      System.setIn(original);
    }
    assertEquals(
        "invalid_arguments",
        assertThrows(
                Problem.class,
                () ->
                    credentials.command(
                        List.of(
                            "set",
                            "local",
                            "--username",
                            "fixture-user",
                            "--token",
                            "argument-token")))
            .code);
  }

  @Test
  void BootstrapSecretsStayOutOfArgumentsAndLogs() throws Exception {
    var value = new Credentials.Value("synthetic-user", "synthetic-secret", "fixture");
    Path log = temp.resolve("child.log");
    var args =
        List.of(
            "bash",
            "-c",
            "printf '%s\\n' \"$DATENPORTAL_BOOTSTRAP_USER\" \"$DATENPORTAL_BOOTSTRAP_PASSWORD\"; printf '%s' \"$DATENPORTAL_BOOTSTRAP_USER:$DATENPORTAL_BOOTSTRAP_PASSWORD\" | base64");
    var result = new ProcessRunner().run(args, temp, 10, log, value.bootstrapEnvironment());
    assertEquals(0, result.exitCode());
    String output = Files.readString(log);
    assertFalse(output.contains("synthetic-user"));
    assertFalse(output.contains("synthetic-secret"));
    assertFalse(
        output.contains(
            Base64.getEncoder().encodeToString("synthetic-user:synthetic-secret".getBytes())));
    assertTrue(output.contains("[redacted]"));
  }

  @Test
  void DeliveryChecksCredentialsBeforeTouchingInfrastructure() throws Exception {
    var f = new Fixtures(temp.resolve("workflow"));
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.workflow.credentials =
        (name, env) -> {
          throw new Problem("credentials_missing", "SYNTHETIC fixture");
        };
    assertEquals(
        "credentials_missing",
        assertThrows(Problem.class, () -> f.call("deliver", "run_id", id)).code);
    assertTrue(Json.obj(f.call("status", "run_id", id).get("deliveries")).isEmpty());
    assertFalse(Files.exists(temp.resolve("workflow/stack")));
  }
}
