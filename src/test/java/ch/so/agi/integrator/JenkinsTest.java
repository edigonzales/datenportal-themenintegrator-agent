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
  int uploads;
  boolean reload = true, missingLocation = false;
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
    if (path.endsWith("/crumbIssuer/api/json"))
      body = "{\"crumbRequestField\":\"Jenkins-Crumb\",\"crumb\":\"fixture-crumb\"}";
    else if (path.endsWith("/gretl-datenportal/build")) {
      uploads++;
      multipart = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      if (!missingLocation)
        x.getResponseHeaders()
            .add("Location", base + "/jenkins/gretl-datenportal/run?job=fixture&queue=123");
      status = 303;
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
    var f = new Fixtures(temp);
    Json.obj(f.settings.values.get("environments")).put("local", env);
    f.workflow.jenkins = ignored -> client();
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
