package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ExternalTest {
  @TempDir Path temp;

  @Test
  void manifestOnly404MeansEmpty() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int[] status = {404};
    String[] body = {"missing"};
    server.createContext(
        "/manifest",
        exchange -> {
          byte[] bytes = body[0].getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status[0], bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    var env =
        Json.map("manifest_url", "http://127.0.0.1:" + server.getAddress().getPort() + "/manifest");
    try {
      assertNull(Jenkins.manifest(env));
      status[0] = 503;
      assertEquals(
          "remote_read_failed", assertThrows(Problem.class, () -> Jenkins.manifest(env)).code);
      status[0] = 200;
      body[0] = "broken";
      assertEquals(
          "manifest_invalid", assertThrows(Problem.class, () -> Jenkins.manifest(env)).code);
      body[0] =
          "{\"releaseId\":\"release\",\"datasheets\":\"sheets.xtf\",\"catalog\":\"catalog.xtf\"}";
      assertEquals("release", Jenkins.manifest(env).get("releaseId"));
      body[0] = "{\"releaseId\":\"release\",\"datasheets\":\"../outside.xtf\"}";
      assertEquals(
          "manifest_invalid", assertThrows(Problem.class, () -> Jenkins.manifest(env)).code);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void matchingStackReusedAndMissingServicesStarted() throws Exception {
    var calls = new ArrayList<List<String>>();
    ProcessRunner process =
        new ProcessRunner() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            calls.add(args);
            if (args.contains("inspect"))
              return Json.text(
                  Json.map(
                      "mounts",
                      List.of(Json.map("type", "bind", "source", temp.resolve("repo").toString())),
                      "working_tree",
                      true,
                      "running",
                      true,
                      "health",
                      "healthy"));
            if (args.contains("-aq")) return "container-id";
            if (args.contains("--format"))
              return Json.text(
                  List.of(Json.map("Service", "jenkins", "State", "running", "Health", "healthy")));
            return "";
          }

          @Override
          public Result run(List<String> args, Path cwd, int timeout, Path log) {
            calls.add(args);
            return new Result(0, "");
          }
        };
    var f = new Fixtures(temp, process, (n, a) -> Json.map(), (n, a) -> Json.map());
    var result = Json.obj(new Stack(f.settings, process).ensure());
    assertTrue(Json.bool(result, "reused", false));
    assertTrue(
        calls.stream()
            .anyMatch(
                a ->
                    a.contains("--no-recreate")
                        && a.contains("garage")
                        && a.contains("downloads")));
    assertFalse(calls.stream().anyMatch(a -> a.contains("scripts/up.sh")));
  }

  @Test
  void mismatchedStackStopsBeforeStart() throws Exception {
    var f = new Fixtures(temp);
    ProcessRunner process =
        new ProcessRunner() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            if (args.contains("-aq")) return "id";
            if (args.contains("inspect"))
              return Json.text(
                  Json.map("mounts", List.of(), "working_tree", false, "running", true));
            throw new AssertionError("Stack must not start");
          }
        };
    assertEquals(
        "stack_mismatch",
        assertThrows(Problem.class, () -> new Stack(f.settings, process).ensure()).code);
  }

  @Test
  void unhealthyServicesNotReady() throws Exception {
    var f = new Fixtures(temp);
    ProcessRunner process =
        new ProcessRunner() {
          @Override
          public String checked(List<String> args, Path cwd, int timeout) {
            return Json.text(
                List.of(Json.map("Service", "jenkins", "State", "running", "Health", "starting")));
          }
        };
    assertEquals(
        "stack_unhealthy",
        assertThrows(
                Problem.class, () -> new Stack(f.settings, process).services(Set.of("jenkins")))
            .code);
  }

  @Test
  void localProfileCannotTargetRemote() throws Exception {
    var f = new Fixtures(temp);
    Json.obj(Json.obj(f.settings.values.get("environments")).get("local"))
        .put("portal_url", "https://example.org");
    assertEquals(
        "local_endpoint_required",
        assertThrows(Problem.class, () -> f.settings.environment("local")).code);
  }

  @Test
  void changedRuntimeSettingsInvalidateApproval() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    f.settings.values.put("timeout_seconds", 301);
    assertEquals(
        false, Json.obj(f.call("status", "run_id", id).get("current_approvals")).get("metadata"));
  }

  @Test
  void publicationCannotBeVerifiedWithoutAcceptedReport() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    assertEquals(
        "publication_unconfirmed",
        assertThrows(
                Problem.class,
                () -> f.call("verify_delivery", "run_id", id, "environment", "local"))
            .code);
  }

  @Test
  void reportMissingUploadCannotRetry() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          Json.obj(r.get("deliveries"))
              .put("local", Json.map("phase", "failed", "job", Json.map("queue", "123")));
          return null;
        });
    f.error("retry_unsafe", () -> f.call("retry_delivery", "run_id", id, "environment", "local"));
  }

  @Test
  void incompleteMetadataMutationRetainsJournal() throws Exception {
    var f =
        new Fixtures(
            temp,
            new Fixtures.FakeProcess(),
            (name, args) -> {
              if (name.equals("import_xtf") || name.equals("read_datasheet"))
                return Json.map("draft_id", "fixture", "revision", 1, "data", Json.map());
              throw new Problem("mcp_unavailable", "unconfirmed fixture mutation");
            },
            (n, a) -> Json.map());
    String id = f.metadataOnly();
    f.call("metadata", "run_id", id, "operation", "import_xtf");
    f.error(
        "mcp_unavailable",
        () ->
            f.call(
                "metadata",
                "run_id",
                id,
                "operation",
                "update_metadata",
                "arguments",
                Json.map("values", Json.map("title", "Changed"))));
    assertEquals(
        "update_metadata",
        Json.obj(f.call("status", "run_id", id).get("pending_metadata")).get("operation"));
    f.error(
        "metadata_uncertain", () -> f.call("metadata", "run_id", id, "operation", "export_xtf"));
  }

  @Test
  void migrationDoesNotUploadExistingRun() throws Exception {
    var f = new Fixtures(temp);
    String id = f.metadataOnly();
    f.workflow.store.edit(
        id,
        r -> {
          r.put("schema_version", 1);
          Json.obj(r.get("deliveries"))
              .put(
                  "local",
                  Json.map(
                      "phase",
                      "complete",
                      "job",
                      Json.map("queue", "old", "build", "23"),
                      "report",
                      Json.map("publication", "accepted")));
          return null;
        });
    f.call("migrate_run", "run_id", id, "apply", true);
    assertEquals("complete", f.call("deliver", "run_id", id).get("phase"));
  }
}
