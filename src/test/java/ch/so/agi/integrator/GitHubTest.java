package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class GitHubTest {
  @Test
  void gitUsesJavaCredentialHelperAndDoesNotDiscloseTokenToOtherHosts(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
    var fixture = new Fixtures(temp.resolve("workspace with spaces"));
    new Credentials(fixture.settings).saveGithub("SYNTHETIC-CREDENTIAL");
    for (String source : List.of("store", "GH_TOKEN", "GITHUB_TOKEN")) {
      for (String host : List.of("github.com", "example.org")) {
        var builder = new ProcessBuilder("git", "credential", "fill");
        builder.environment().putAll(GitHubCredentials.environment(fixture.settings));
        builder.environment().remove("GH_TOKEN");
        builder.environment().remove("GITHUB_TOKEN");
        String expected = source.equals("store") ? "SYNTHETIC-CREDENTIAL" : "SYNTHETIC-ENV";
        if (!source.equals("store")) builder.environment().put(source, expected);
        builder.directory(temp.toFile());
        assertFalse(
            GitHubCredentials.environment(fixture.settings)
                .toString()
                .contains("SYNTHETIC-CREDENTIAL"));
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        var child = builder.start();
        try {
          child
              .getOutputStream()
              .write(("protocol=https\nhost=" + host + "\n\n").getBytes(StandardCharsets.UTF_8));
          child.getOutputStream().close();
          assertTrue(child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
          String result = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
          if (host.equals("github.com")) {
            assertEquals(0, child.exitValue());
            assertTrue(result.contains("password=" + expected));
          } else {
            assertNotEquals(0, child.exitValue());
            assertFalse(result.contains("SYNTHETIC"));
          }
        } finally {
          child.destroyForcibly();
        }
      }
    }
  }

  @Test
  void understandsRemotesButRejectsCredentialsAndOtherHosts() {
    for (String remote :
        List.of(
            "https://github.com/org/repo.git",
            "git@github.com:org/repo.git",
            "ssh://git@github.com/org/repo"))
      assertEquals(new GitHub.Repo("org", "repo"), GitHub.repository(remote));
    for (String remote :
        List.of(
            "https://secret@github.com/org/repo",
            "https://elsewhere/org/repo",
            "https://github.com/org/repo/extra",
            "https://github.com/org/repo?token=x"))
      assertThrows(Problem.class, () -> GitHub.repository(remote));
  }

  @Test
  void restPayloadAuthenticationRateLimitAndNoRedirect(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
    var fixture = new Fixtures(temp);
    var credentials = new Credentials(fixture.settings, key -> null);
    credentials.saveGithub("SYNTHETIC");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var requests = new ArrayList<String>();
    server.createContext(
        "/repos/org/repo/pulls",
        exchange -> {
          assertEquals("Bearer SYNTHETIC", exchange.getRequestHeaders().getFirst("Authorization"));
          requests.add(exchange.getRequestMethod());
          String response;
          int code;
          if (exchange.getRequestMethod().equals("POST")) {
            var body =
                Json.read(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("main", body.get("base"));
            assertEquals("codex/test", body.get("head"));
            assertEquals("Line one\n\nLine two", body.get("body"));
            response = "{\"html_url\":\"https://github.com/org/repo/pull/1\",\"number\":1}";
            code = 201;
          } else if (exchange.getRequestURI().getPath().endsWith("/2")) {
            exchange.getResponseHeaders().set("Retry-After", "90");
            response = "{}";
            code = 429;
          } else if (exchange.getRequestURI().getPath().endsWith("/4")) {
            response = "{}";
            code = 401;
          } else if (exchange.getRequestURI().getPath().endsWith("/3")) {
            exchange.getResponseHeaders().set("Location", "https://example.org/secret");
            response = "{}";
            code = 302;
          } else {
            response = "{\"merged\":true,\"base\":{\"ref\":\"main\"},\"head\":{\"sha\":\"abc\"}}";
            code = 200;
          }
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(code, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var api =
          new GitHub(
              URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
              credentials.resolveGithub().token,
              5);
      var repo = new GitHub.Repo("org", "repo");
      assertEquals(
          "https://github.com/org/repo/pull/1",
          api.create(repo, "main", "codex/test", "Title", "Line one\n\nLine two").get("html_url"));
      assertEquals(true, api.get(repo, 1).get("merged"));
      var rate = assertThrows(Problem.class, () -> api.get(repo, 2));
      assertEquals("github_rate_limited", rate.code);
      assertEquals("90", rate.details.get("retry_after"));
      assertFalse(Json.text(rate.result()).contains("SYNTHETIC"));
      assertEquals("github_api_failed", assertThrows(Problem.class, () -> api.get(repo, 3)).code);
      assertEquals(
          "github_authentication_failed", assertThrows(Problem.class, () -> api.get(repo, 4)).code);
      assertEquals(List.of("POST", "GET", "GET", "GET", "GET"), requests);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void ambiguousCreateIsRecoveredWithoutAnotherPost(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
    var f = new Fixtures(temp);
    var repository = new Repository(f.workflow);
    int[] creates = {0};
    var candidate =
        Json.map("html_url", "https://github.com/org/repo/pull/7", "head", Json.map("sha", "abc"));
    repository.github =
        new GitHub(URI.create("http://127.0.0.1"), "SYNTHETIC", 1) {
          @Override
          Map<String, Object> create(
              Repo repo, String base, String branch, String title, String body) {
            creates[0]++;
            throw new AssertionError();
          }

          @Override
          List<Map<String, Object>> find(Repo repo, String branch, String base) {
            return List.of(candidate);
          }
        };
    var run =
        Json.map(
            "pr_submission",
            Json.map(
                "owner",
                "org",
                "repository",
                "repo",
                "head",
                "abc",
                "branch",
                "codex/test",
                "base",
                "main",
                "checkout",
                temp.toString()));
    assertEquals(
        "https://github.com/org/repo/pull/7", Json.obj(repository.recover(run)).get("url"));
    assertEquals(0, creates[0]);
    Json.obj(candidate.get("head")).put("sha", "changed");
    assertEquals(
        "github_pr_ambiguous", assertThrows(Problem.class, () -> repository.recover(run)).code);
    assertEquals(0, creates[0]);
  }
}
