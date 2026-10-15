package ch.so.agi.integrator;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Small REST boundary. No implicit retries for writes and no credential-bearing redirects. */
class GitHub {
  record Repo(String owner, String name) {
    String path() {
      return "/repos/" + owner + "/" + name;
    }
  }

  private final HttpClient client;
  private final URI endpoint;
  private final String token;
  private final int timeout;

  GitHub(Settings settings) {
    this(
        URI.create("https://api.github.com"),
        new Credentials(settings).resolveGithub().token,
        settings.timeout);
  }

  GitHub(URI endpoint, String token, int timeout) {
    this.endpoint = endpoint;
    this.token = token;
    this.timeout = timeout;
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(timeout))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  static Repo repository(String remote) {
    String normalized = remote.trim().replaceFirst("^git@github\\.com:", "https://github.com/");
    URI uri;
    try {
      uri = URI.create(normalized);
    } catch (Exception e) {
      throw new Problem("github_remote_invalid", "GitHub-Remote ist ungültig.");
    }
    if (!"github.com".equalsIgnoreCase(uri.getHost())
        || uri.getPort() != -1
        || !Set.of("https", "ssh").contains(uri.getScheme())
        || uri.getQuery() != null
        || uri.getFragment() != null
        || (uri.getUserInfo() != null
            && !("ssh".equals(uri.getScheme()) && "git".equals(uri.getUserInfo()))))
      throw new Problem(
          "github_remote_invalid",
          "GitHub-HTTPS- oder SSH-Remote ohne eingebettete Zugangsdaten erforderlich.");
    String[] parts = uri.getPath().replaceFirst("\\.git$", "").split("/");
    if (parts.length != 3
        || !parts[1].matches("[A-Za-z0-9_-]+")
        || !parts[2].matches("[A-Za-z0-9_.-]+")
        || parts[2].equals(".."))
      throw new Problem("github_remote_invalid", "GitHub-Repository nicht eindeutig.");
    return new Repo(parts[1], parts[2]);
  }

  Map<String, Object> create(Repo repo, String base, String branch, String title, String body) {
    return Json.obj(
        request(
            "POST",
            repo.path() + "/pulls",
            Json.map("base", base, "head", branch, "title", title, "body", body)));
  }

  Map<String, Object> get(Repo repo, long number) {
    return Json.obj(request("GET", repo.path() + "/pulls/" + number, null));
  }

  List<Map<String, Object>> find(Repo repo, String branch, String base) {
    String query =
        "?state=all&head="
            + URLEncoder.encode(
                repo.owner() + ":" + branch, java.nio.charset.StandardCharsets.UTF_8)
            + "&base="
            + URLEncoder.encode(base, java.nio.charset.StandardCharsets.UTF_8)
            + "&per_page=100";
    return Json.list(request("GET", repo.path() + "/pulls" + query, null)).stream()
        .map(Json::obj)
        .toList();
  }

  Object request(String method, String path, Object body) {
    try {
      var builder =
          HttpRequest.newBuilder(endpoint.resolve(path))
              .timeout(Duration.ofSeconds(timeout))
              .header("Authorization", "Bearer " + token)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("User-Agent", "datenportal-integrator");
      if (body == null) builder.GET();
      else
        builder
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(Json.text(body)));
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status >= 200 && status < 300)
        return new tools.jackson.databind.json.JsonMapper()
            .readValue(response.body(), Object.class);
      if (status == 429
          || (status == 403
              && (response.headers().firstValue("x-ratelimit-remaining").orElse("").equals("0")
                  || response.headers().firstValue("retry-after").isPresent()
                  || response.body().toLowerCase(Locale.ROOT).contains("rate limit"))))
        throw new Problem(
            "github_rate_limited",
            "GitHub-Limit erreicht; später fortsetzen.",
            "retry_after",
            response.headers().firstValue("retry-after").orElse("60"),
            "reset_at",
            response.headers().firstValue("x-ratelimit-reset").orElse(""));
      throw new Problem(
          status == 401
              ? "github_authentication_failed"
              : status == 403 ? "github_access_denied" : "github_api_failed",
          "GitHub-Aufruf fehlgeschlagen.",
          "status",
          status);
    } catch (Problem e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Problem(
          "github_interrupted", "GitHub-Aufruf unterbrochen; Ergebnis vor Wiederholung klären.");
    } catch (Exception e) {
      throw new Problem(
          "github_unavailable",
          "GitHub-Antwort nicht bestätigt; bei Schreibzugriff Ergebnis vor Wiederholung klären.");
    }
  }
}
