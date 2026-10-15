package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class JenkinsTest {
  @TempDir Path temp;
  HttpServer server;
  String base, metadata, catalog;
  int uploads, seedStarts;
  boolean upload404, seedRunning;
  String seedResult = "SUCCESS";
  boolean reload = true, missingLocation = false;
  boolean requireSessionCookie;
  String multipart;
  Map<String, Object> env;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    metadata = Json.contents(Fixtures.FIXTURES.resolve("dataset.xtf"));
    catalog =
        "<catalog><Dataset><identifier>ch.so.grundwasser.qualitaet</identifier>"
            + List.of("csv", "xlsx", "parquet").stream()
                .map(
                    f ->
                        "<Distribution><format>"
                            + f
                            + "</format><downloadURL>"
                            + base
                            + "/downloads/file."
                            + f
                            + "</downloadURL></Distribution>")
                .reduce("", String::concat)
            + "</Dataset></catalog>";
    server.createContext("/", this::handle);
    server.start();
    env =
        Json.map(
            "kind",
            "local",
            "enabled",
            true,
            "repository_mode",
            "working-tree",
            "jenkins_url",
            base + "/jenkins",
            "portal_url",
            base,
            "manifest_url",
            base + "/current.json",
            "username_env",
            "FIXTURE_USER",
            "token_env",
            "FIXTURE_TOKEN");
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  Jenkins client() {
    return new Jenkins(env, key -> "synthetic-fixture");
  }

  void handle(HttpExchange x) throws java.io.IOException {
    String path = x.getRequestURI().getPath(), body = "{}";
    int status = 200;
    if (path.endsWith("/crumbIssuer/api/json")) {
      body = "{\"crumbRequestField\":\"Jenkins-Crumb\",\"crumb\":\"fixture-crumb\"}";
      if (requireSessionCookie)
        x.getResponseHeaders()
            .add("Set-Cookie", "JSESSIONID=synthetic-session; Path=/jenkins; HttpOnly");
    } else if (path.endsWith("/gretl-datenportal/build")
        && requireSessionCookie
        && (!Objects.toString(x.getRequestHeaders().getFirst("Cookie"), "")
                .contains("JSESSIONID=synthetic-session")
            || !"fixture-crumb".equals(x.getRequestHeaders().getFirst("Jenkins-Crumb")))) {
      status = 403;
      body = "Session cookie and crumb must match";
    } else if (path.endsWith("/gretl-datenportal/build")) {
      uploads++;
      multipart = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (!missingLocation)
        x.getResponseHeaders()
            .add("Location", base + "/jenkins/gretl-datenportal/run?job=fixture&queue=123");
      status = upload404 ? 404 : 303;
    } else if (path.endsWith("/gretl-datenportal/runStatus"))
      body =
          Json.text(
              Json.map(
                  "complete",
                  true,
                  "buildNumber",
                  14,
                  "artifacts",
                  List.of(
                      Json.map("fileName", "report.json", "url", base + "/jenkins/report.json"))));
    else if (path.endsWith("/queue/item/seed/api/json"))
      body = Json.text(Json.map("executable", Json.map("url", base + "/jenkins/job/seed/1/")));
    else if (path.endsWith("/job/seed/1/api/json"))
      body = "{\"building\":false,\"result\":\"SUCCESS\"}";
    else if (path.endsWith("/job/gretl-datenportal-seed/build")) {
      seedStarts++;
      x.getResponseHeaders().add("Location", base + "/jenkins/queue/item/900/");
      status = 201;
    } else if (path.endsWith("/queue/item/900/api/json"))
      body =
          Json.text(
              Json.map(
                  "executable", Json.map("url", base + "/jenkins/job/gretl-datenportal-seed/42/")));
    else if (path.endsWith("/job/gretl-datenportal-seed/42/api/json"))
      body =
          Json.text(Json.map("building", seedRunning, "result", seedRunning ? null : seedResult));
    else if (path.equals("/current.json"))
      body =
          "{\"releaseId\":\"fixture-release\",\"datasheets\":\"sheets.xtf\",\"catalog\":\"catalog.xtf\"}";
    else if (path.equals("/sheets.xtf")) body = metadata;
    else if (path.equals("/catalog.xtf")) body = catalog;
    else if (path.equals("/catalog/published-catalog.xtf"))
      body = reload ? catalog : "<old-catalog/>";
    else if (path.endsWith("/report.json"))
      body =
          "{\"publication\":\"accepted\",\"releaseId\":\"fixture-release\",\"reload\":\"failed\",\"opendata\":{\"status\":\"accepted\"}}";
    else if (path.endsWith("/file.csv")) body = "Jahr\n2025\n";
    else if (path.contains("/downloads/")) body = "fixture bytes";
    else if (path.contains("/datasets/")) body = "<html>fixture</html>";
    else {
      status = 404;
      body = "missing";
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    x.sendResponseHeaders(status, bytes.length);
    x.getResponseBody().write(bytes);
    x.close();
  }

  @Test
  void existingSeedRemainsQueryableWithBlockedRepositorySync() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.workflow.store.edit(
        id,
        r -> {
          r.put("repository_sync", Json.map("phase", "blocked"));
          r.put(
              "seeds",
              Json.map(
                  "local",
                  Json.map(
                      "phase",
                      "seeding",
                      "seed",
                      Json.map("queue_url", base + "/jenkins/queue/item/seed/"))));
          return null;
        });
    assertEquals("complete", f.call("seed", "run_id", id, "environment", "local").get("phase"));
    assertEquals(0, uploads);
    assertEquals(
        "blocked", Json.obj(f.call("status", "run_id", id).get("repository_sync")).get("phase"));
  }

  @Test
  void PasswordAuthenticationKeepsTheCrumbSessionCookie() {
    requireSessionCookie = true;
    Path data = temp.resolve("synthetic.csv");
    Json.write(data, "Jahr\n2025\n");
    assertEquals(
        "123",
        client()
            .submit("agi", "ch.so.fixture", null, data, null, "Synthetic fixture")
            .get("queue"));
    assertEquals(1, uploads);
  }

  @Test
  void multipartUsesExistingContractAndTrustedQueue() {
    Path data = temp.resolve("input.csv");
    Json.write(data, "Jahr\n2025\n");
    var result =
        client().submit("agi", "ch.so.fixture", "2025", data, null, "Themenintegrator fixture");
    assertEquals("123", result.get("queue"));
    assertTrue(multipart.contains("name=\"SERIES_ID\"\r\n\r\n2025"));
    assertTrue(multipart.contains("name=\"DATA_FILE\""));
    assertFalse(multipart.contains("name=\"METADATA_FILE\""));
    assertEquals(1, uploads);
  }

  @Test
  void metadataOnlyDoesNotSendIssueOrData() {
    Path meta = temp.resolve("metadata.xtf");
    Json.write(meta, metadata);
    client().submit("agi", "ch.so.fixture", "2025", null, meta, "fixture");
    assertTrue(multipart.contains("name=\"SERIES_ID\"\r\n\r\n\r\n"));
    assertFalse(multipart.contains("name=\"DATA_FILE\""));
  }

  @Test
  void externalRedirectNeverReceivesCredentials() {
    var c = client();
    assertEquals(
        "jenkins_url_mismatch",
        assertThrows(Problem.class, () -> c.trusted("https://example.org/steal")).code);
    assertEquals(
        "jenkins_url_mismatch",
        assertThrows(Problem.class, () -> c.trusted(base + "/outside")).code);
  }

  Fixtures workflow() throws Exception {
    // Isolated simulated stack: no Docker commands or business publication.
    var process =
        new Fixtures.FakeProcess() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (!args.getFirst().equals("docker")) return super.checked(args, cwd, timeout);
            if (args.contains("-aq")) return "fixture-container";
            if (args.contains("inspect"))
              return Json.text(
                  Json.map(
                      "mounts",
                      List.of(Json.map("type", "bind", "source", temp.resolve("repo").toString())),
                      "working_tree",
                      true,
                      "running",
                      true));
            if (args.contains("compose") && args.contains("ps"))
              return Json.text(
                  List.of("garage", "jenkins", "downloads", "sodata").stream()
                      .map(service -> Json.map("Service", service, "State", "running"))
                      .toList());
            throw new AssertionError("Unexpected Docker call " + args);
          }
        };
    var f =
        new Fixtures(
            temp,
            process,
            (name, args) -> {
              throw new AssertionError("Unexpected datasheet call");
            },
            (name, args) -> {
              throw new AssertionError("Unexpected model call");
            });

    Json.obj(f.settings.values.get("environments")).put("local", env);
    f.workflow.jenkins = (name, ignored) -> client();
    f.workflow.credentials =
        (name, ignored) -> new Credentials.Value("synthetic-user", "synthetic-token", "fixture");
    return f;
  }

  @Test
  void resumedUploadOccursOnceAndFrozenDataSurvivesAttachment() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map(
                      "phase",
                      "seeding",
                      "fingerprint",
                      f.workflow.fingerprint(r, "metadata"),
                      "seed",
                      Json.map("queue_url", base + "/jenkins/queue/item/seed/")));
          return null;
        });
    assertFalse(
        Json.obj(f.workflow.store.edit(id, r -> r).get("approvals")).containsKey("publish:local"));
    assertEquals("running", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, uploads);
    Path changed = temp.resolve("changed.csv");
    Json.write(changed, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", changed.toString());
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, uploads);
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, uploads);
  }

  @Test
  void unconfirmedUploadIsPersistedAndNeverRepeated() throws Exception {
    missingLocation = true;
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map(
                      "phase",
                      "seeding",
                      "seed",
                      Json.map("queue_url", base + "/jenkins/queue/item/seed/")));
          return null;
        });
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
    assertEquals(
        "delivery_submitting",
        Json.obj(Json.obj(f.call("status", "run_id", id).get("deliveries")).get("local"))
            .get("phase"));
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
    assertEquals(1, uploads);
  }

  @Test
  void reloadFailureDoesNotUploadAgain() throws Exception {
    reload = false;
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map(
                      "phase",
                      "seeding",
                      "seed",
                      Json.map("queue_url", base + "/jenkins/queue/item/seed/")));
          return null;
        });
    f.call("deliver", "run_id", id);
    var result = f.call("deliver", "run_id", id);
    assertEquals("accepted", result.get("publication"));
    assertEquals("failed", result.get("phase"));
    f.error("retry_unsafe", () -> f.call("retry_delivery", "run_id", id, "environment", "local"));
    reload = true;
    assertEquals(
        "complete", f.call("verify_delivery", "run_id", id, "environment", "local").get("phase"));
    assertEquals(1, uploads);
  }

  Map<String, Object> adopt(Fixtures f, String id) {
    return f.call(
        "adopt_seed",
        "run_id",
        id,
        "environment",
        "local",
        "build_url",
        base + "/jenkins/job/gretl-datenportal-seed/42/");
  }

  @Test
  void failedSeedCanBeReplacedWithoutAnotherSeedOrDuplicateUpload() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    seedResult = "FAILURE";
    assertEquals("seeding", f.call("deliver", "run_id", id).get("phase"));
    assertEquals("failed", f.call("deliver", "run_id", id).get("phase"));
    seedResult = "SUCCESS";
    adopt(f, id);
    adopt(f, id);
    var state = f.call("status", "run_id", id);
    assertEquals(1, Json.list(state.get("delivery_history")).size());
    assertTrue(Json.list(state.get("seed_history")).isEmpty());
    assertEquals(0, uploads);
    assertEquals("seeding", f.call("deliver", "run_id", id).get("phase"));
    adopt(f, id);
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("delivery_history")).size());
    assertEquals("running", f.call("deliver", "run_id", id).get("phase"));
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, uploads);
    assertEquals(1, seedStarts);
  }

  @Test
  void runningManualSeedIsPolledAndRequiresApprovals() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    seedRunning = true;
    adopt(f, id);
    f.error("approval_required", () -> f.call("deliver", "run_id", id));
    f.approveAll(id);
    f.call("deliver", "run_id", id);
    assertEquals("seeding", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(0, uploads);
    seedRunning = false;
    assertEquals("running", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(0, seedStarts);
  }

  @Test
  void manualSeedMustBelongToConfiguredSuccessfulJob() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    seedResult = "FAILURE";
    f.error("seed_failed", () -> adopt(f, id));
    for (String url :
        List.of(
            base + "/jenkins/job/other/42/",
            base + "/jenkins/queue/item/42/",
            base + "/jenkins/job/gretl-datenportal-seed/42/?x=1"))
      f.error(
          "wrong_seed",
          () -> f.call("adopt_seed", "run_id", id, "environment", "local", "build_url", url));
    f.error(
        "jenkins_url_mismatch",
        () ->
            f.call(
                "adopt_seed",
                "run_id",
                id,
                "environment",
                "local",
                "build_url",
                "https://example.org/job/gretl-datenportal-seed/42/"));
    assertTrue(Json.obj(f.call("status", "run_id", id).get("seeds")).isEmpty());
    assertEquals(0, uploads);
    assertEquals(0, seedStarts);
  }

  @Test
  void successfulSeedThenUpload404IsDeliveryFailureAndCannotBeResetByAdoption() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    adopt(f, id);
    f.call("deliver", "run_id", id);
    upload404 = true;
    var error = assertThrows(Problem.class, () -> f.call("deliver", "run_id", id));
    assertEquals("jenkins_submit_failed", error.code);
    var external = Json.obj(error.details.get("external_state"));
    assertEquals("delivery", external.get("action"));
    assertEquals("reconcile", external.get("next_operation"));
    assertEquals(
        base + "/jenkins/gretl-datenportal/build",
        Json.obj(external.get("reference")).get("endpoint"));
    f.error("seed_adoption_unsafe", () -> adopt(f, id));
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
    f.error("retry_unsafe", () -> f.call("retry_delivery", "run_id", id, "environment", "local"));
    f.error(
        "reconcile_not_needed",
        () -> f.call("reconcile_seed", "run_id", id, "environment", "local", "queue", "900"));
    assertEquals(1, uploads);
    assertEquals(0, seedStarts);
  }

  @Test
  void manualRepositoryChangesAreNotRestoredAndCanBecomeReviewedCandidates() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    String rel = "agi/ch.so.grundwasser.qualitaet/dataset.gradle";
    Path candidate = temp.resolve("dataset.gradle");
    Json.write(candidate, "// synthetic original\n");
    f.call("stage_change", "run_id", id, "relative_path", rel, "source_path", candidate.toString());
    f.approveAll(id);
    f.call("apply_local_changes", "run_id", id);
    adopt(f, id);
    f.call("deliver", "run_id", id);
    Path target = f.repo.resolve(rel);
    Json.write(target, "// synthetic manual correction\n");
    f.error("local_changes_modified", () -> f.call("deliver", "run_id", id));
    f.error("repository_conflict", () -> f.call("apply_local_changes", "run_id", id));
    assertEquals("// synthetic manual correction\n", Json.contents(target));
    Files.delete(target);
    f.error("local_changes_missing", () -> f.call("deliver", "run_id", id));
    f.error("repository_conflict", () -> f.call("apply_local_changes", "run_id", id));
    assertFalse(Files.exists(target));
    Json.write(target, "// synthetic manual correction\n");
    f.call("stage_change", "run_id", id, "relative_path", rel, "source_path", target.toString());
    f.error("approval_required", () -> f.call("apply_local_changes", "run_id", id));
    f.approveAll(id);
    f.call("apply_local_changes", "run_id", id);
    assertEquals("// synthetic manual correction\n", Json.contents(target));
    assertEquals(0, uploads);
  }

  @Test
  void failedAdoptedSeedCanBeRetriedWithOwnSeed() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    seedRunning = true;
    adopt(f, id);
    f.call("deliver", "run_id", id);
    seedRunning = false;
    seedResult = "FAILURE";
    assertEquals("failed", f.call("deliver", "run_id", id).get("phase"));
    f.call("retry_delivery", "run_id", id, "environment", "local");
    seedResult = "SUCCESS";
    f.call("deliver", "run_id", id);
    assertEquals("running", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, seedStarts);
    assertEquals(1, uploads);
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("seed_history")).size());
  }

  @Test
  void ownSeedWithoutManualReferenceStillDeliversOnce() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    assertEquals("seed", f.call("deliver", "run_id", id).get("action"));
    assertEquals("delivery", f.call("deliver", "run_id", id).get("action"));
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(1, seedStarts);
    assertEquals(1, uploads);
  }

  @Test
  void changedReviewRequiresRenewedSeedAssociation() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    adopt(f, id);
    f.call("deliver", "run_id", id);
    Path changed = temp.resolve("corrected.csv");
    Json.write(changed, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", changed.toString());
    f.approveAll(id);
    f.error("seed_review_changed", () -> f.call("deliver", "run_id", id));
    adopt(f, id);
    assertEquals(1, Json.list(f.call("status", "run_id", id).get("seed_history")).size());
    f.call("deliver", "run_id", id);
    assertEquals("running", f.call("deliver", "run_id", id).get("phase"));
    assertEquals(0, seedStarts);
  }

  @Test
  void manualSeedDoesNotGrantTargetPublicationApproval() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    var target = new LinkedHashMap<>(env);
    target.put("kind", "int");
    target.put("repository_mode", "managed-git");
    Json.obj(f.settings.values.get("environments")).put("int", target);
    f.call(
        "adopt_seed",
        "run_id",
        id,
        "environment",
        "int",
        "build_url",
        base + "/jenkins/job/gretl-datenportal-seed/42/");
    f.error("approval_required", () -> f.call("deliver", "run_id", id, "environment", "int"));
    assertEquals(0, uploads);
    assertEquals(0, seedStarts);
  }

  @Test
  void unknownSeedBlocksNewDeliveryAndAdoption() throws Exception {
    var f = workflow();
    String id = f.topic(false, "Jahr\n2025\n");
    f.workflow.store.edit(
        id,
        r -> {
          r.put("seeds", Json.map("local", Json.map("phase", "seed_submitting")));
          return null;
        });
    f.error("seed_adoption_unsafe", () -> adopt(f, id));
    f.error("submission_unknown", () -> f.call("deliver", "run_id", id));
    assertEquals(0, seedStarts);
  }

  @Test
  void acceptedMetadataDriftNotIgnored() throws Exception {
    Path p = temp.resolve("data.csv");
    Json.write(p, "Jahr\n2025\n");
    metadata = metadata.replace("Wasserqualität Grundwasser", "Unexpected changed title");
    var outcome =
        client()
            .verify(
                Json.map(
                    "publication",
                    "accepted",
                    "releaseId",
                    "fixture-release",
                    "opendata",
                    Json.map("status", "accepted")),
                Xml.describe(Fixtures.FIXTURES.resolve("dataset.xtf"), null, null),
                p);
    assertFalse(Json.bool(outcome, "verified", true));
    assertTrue(Json.required(outcome, "message").contains("Metadaten"));
  }
}
