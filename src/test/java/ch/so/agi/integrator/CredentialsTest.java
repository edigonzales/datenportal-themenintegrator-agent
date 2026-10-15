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
        [datasheet]
        transport="stdio"
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
  void explicitStoreSelectionSurvivesHarnessRegenerationAndRejectsEnvironmentFallback()
      throws Exception {
    String config = Files.readString(settings.file);
    Files.writeString(settings.file, "github_token_source=\"store\"\n" + config);
    settings = new Settings(settings.file);
    credentials = new Credentials(settings, environment::get);
    environment.put("GH_TOKEN", "inherited-secret");
    environment.put("GITHUB_TOKEN", "other-inherited-secret");
    assertEquals(
        "github_credentials_missing", assertThrows(Problem.class, credentials::resolveGithub).code);
    credentials.saveGithub("store-secret");
    new Harness(settings).install();
    new Harness(new Settings(settings.file)).install();
    credentials = new Credentials(new Settings(settings.file), environment::get);
    assertEquals("store-secret", credentials.resolveGithub().token);
    assertEquals("local_store", credentials.githubStatus().get("credentials_source"));
    assertEquals("store", credentials.githubStatus().get("configured_source"));
    Files.writeString(credentials.file, "{broken");
    assertEquals(
        "credential_store_invalid", assertThrows(Problem.class, credentials::resolveGithub).code);
    Files.writeString(settings.file, "github_token_source=\"unknown\"\n" + config);
    assertEquals(
        "invalid_configuration",
        assertThrows(Problem.class, () -> new Settings(settings.file)).code);
  }

  @Test
  void githubPriorityRotationAndLegacyStore() throws Exception {
    credentials.save("local", "user", "jenkins-secret");
    assertEquals(
        "github_credentials_missing", assertThrows(Problem.class, credentials::resolveGithub).code);
    credentials.saveGithub("store-secret");
    assertEquals("store-secret", credentials.resolveGithub().token);
    environment.put("GITHUB_TOKEN", "github-secret");
    environment.put("GH_TOKEN", "gh-secret");
    assertEquals("gh-secret", credentials.resolveGithub().token);
    assertEquals("GH_TOKEN", credentials.githubStatus().get("credentials_source"));
    assertEquals(true, credentials.githubStatus().get("stored"));
    assertFalse(Json.text(credentials.githubStatus()).contains("secret"));
    environment.put("GH_TOKEN", " ");
    assertEquals("github-secret", credentials.resolveGithub().token);
    credentials.command(List.of("github", "set", "--from-env"));
    environment.clear();
    assertEquals("github-secret", credentials.resolveGithub().token);
    credentials.save("local", "user", "rotated-jenkins");
    assertEquals("github-secret", credentials.resolveGithub().token);
    credentials.command(List.of("github", "remove"));
    assertEquals(false, credentials.githubStatus().get("stored"));
    assertEquals(
        "rotated-jenkins", credentials.resolve("local", settings.environment("local")).token);
  }

  @Test
  void githubStoreFailuresAreIgnoredOnlyWithEnvironment() throws Exception {
    credentials.saveGithub("store-secret");
    Files.writeString(credentials.file, "{broken-secret");
    assertEquals(
        "credential_store_invalid", assertThrows(Problem.class, credentials::resolveGithub).code);
    environment.put("GH_TOKEN", "env-secret");
    assertEquals("env-secret", credentials.resolveGithub().token);
    assertEquals(true, credentials.githubStatus().get("credentials_present"));
    assertEquals("credential_store_invalid", credentials.githubStatus().get("store_error"));
    assertFalse(Json.text(credentials.githubStatus()).contains("secret"));
    Files.setPosixFilePermissions(credentials.file, PosixFilePermissions.fromString("rw-r--r--"));
    assertEquals("env-secret", credentials.resolveGithub().token);
    environment.clear();
    assertEquals(
        "credential_store_permissions",
        assertThrows(Problem.class, credentials::resolveGithub).code);
    Files.setPosixFilePermissions(credentials.file, PosixFilePermissions.fromString("rw-------"));
    Json.write(
        credentials.file,
        Json.pretty(
            Json.map(
                "version",
                1,
                "environments",
                Json.map(),
                "github",
                Json.map("host", "other.org", "token", "secret"))));
    assertEquals(
        "credential_store_invalid", assertThrows(Problem.class, credentials::resolveGithub).code);
  }

  @Test
  void concurrentGithubAndJenkinsChangesRetainBoth() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> credentials.save("local", "user", "jenkins-secret"));
      var second = pool.submit(() -> credentials.saveGithub("github-secret"));
      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);
    }
    assertEquals("github-secret", credentials.resolveGithub().token);
    assertEquals(
        "jenkins-secret", credentials.resolve("local", settings.environment("local")).token);
    credentials.remove("local");
    assertEquals("github-secret", credentials.resolveGithub().token);
  }

  @Test
  void githubCliValidatesStdinAndRejectsTokenArguments() throws Exception {
    var original = System.in;
    try {
      for (String token : List.of("stdin-secret\r\n", "", "bad\nsecret", "x".repeat(65537))) {
        System.setIn(
            new java.io.ByteArrayInputStream(
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (token.startsWith("stdin")) {
          credentials.command(List.of("github", "set", "--token-stdin"));
          assertEquals("stdin-secret", credentials.resolveGithub().token);
        } else {
          assertEquals(
              "credentials_invalid",
              assertThrows(
                      Problem.class,
                      () -> credentials.command(List.of("github", "set", "--token-stdin")))
                  .code);
        }
      }
    } finally {
      System.setIn(original);
    }
    assertEquals(
        "invalid_arguments",
        assertThrows(
                Problem.class,
                () -> credentials.command(List.of("github", "set", "--token", "secret")))
            .code);
    assertEquals(
        "github_credentials_missing",
        assertThrows(
                Problem.class, () -> credentials.command(List.of("github", "set", "--from-env")))
            .code);
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
