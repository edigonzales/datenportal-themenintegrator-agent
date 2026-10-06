package ch.so.agi.integrator;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class Jenkins {
  final Map<String, Object> env;
  final String base, authorization;
  final HttpClient http =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .connectTimeout(Duration.ofSeconds(30))
          .build();

  public Jenkins(Map<String, Object> env) {
    this(env, System::getenv);
  }

  Jenkins(Map<String, Object> env, java.util.function.Function<String, String> credentials) {
    this.env = env;
    base = Json.required(env, "jenkins_url").replaceAll("/+$", "");
    String userKey = Json.str(env, "username_env", "DATENPORTAL_JENKINS_USER"),
        tokenKey = Json.str(env, "token_env", "DATENPORTAL_JENKINS_TOKEN");
    String user = credentials.apply(userKey), token = credentials.apply(tokenKey);
    if (user == null || user.isBlank() || token == null || token.isBlank())
      throw new Problem(
          "credentials_missing",
          "Jenkins-Zugangsdaten fehlen in der Umgebung.",
          "variables",
          List.of(userKey, tokenKey));
    authorization =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
  }

  public static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  public String trusted(String location) {
    URI b = URI.create(base + "/"), u = b.resolve(location);
    String p = u.getPath();
    if (!Objects.equals(b.getScheme(), u.getScheme())
        || !Objects.equals(b.getRawAuthority(), u.getRawAuthority())
        || u.getUserInfo() != null
        || !p.startsWith(b.getPath())
        || p.contains("/../"))
      throw new Problem("jenkins_url_mismatch", "Jenkins verweist auf eine andere Basisadresse.");
    return u.toString();
  }

  private HttpResponse<byte[]> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (Exception e) {
      throw new Problem(
          "jenkins_unavailable",
          "Jenkins-Antwort nicht bestätigt; Status vor erneutem Start klären.");
    }
  }

  public Map<String, Object> get(String path) {
    var r =
        send(
            HttpRequest.newBuilder(URI.create(trusted(path)))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authorization)
                .GET()
                .build());
    if (r.statusCode() != 200)
      throw new Problem(
          "jenkins_read_failed", "Jenkins-Antwort ist nicht lesbar.", "status", r.statusCode());
    return Json.read(new String(r.body(), StandardCharsets.UTF_8));
  }

  public HttpResponse<byte[]> post(String path, HttpRequest.BodyPublisher body, String type) {
    var builder =
        HttpRequest.newBuilder(URI.create(trusted(path)))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", authorization)
            .header("Content-Type", type);
    var crumb =
        send(
            HttpRequest.newBuilder(URI.create(base + "/crumbIssuer/api/json"))
                .header("Authorization", authorization)
                .GET()
                .build());
    if (crumb.statusCode() == 200) {
      var c = Json.read(new String(crumb.body(), StandardCharsets.UTF_8));
      builder.header(Json.required(c, "crumbRequestField"), Json.required(c, "crumb"));
    } else if (crumb.statusCode() != 404)
      throw new Problem(
          "jenkins_auth",
          "Jenkins-Anmeldung oder CSRF-Abfrage fehlgeschlagen.",
          "status",
          crumb.statusCode());
    var response = send(builder.POST(body).build());
    if (!Set.of(200, 201, 202, 302, 303).contains(response.statusCode()))
      throw new Problem(
          "jenkins_submit_failed",
          "Jenkins hat Auftrag nicht bestätigt. Status vor erneutem Start klären.",
          "status",
          response.statusCode());
    return response;
  }

  public void post(String path) {
    post(path, HttpRequest.BodyPublishers.noBody(), "application/x-www-form-urlencoded");
  }

  public Map<String, Object> seed() {
    String job = Json.str(env, "seed_job", "gretl-datenportal-seed");
    String path =
        "job/"
            + Arrays.stream(job.split("/"))
                .map(Jenkins::encode)
                .reduce((a, b) -> a + "/job/" + b)
                .orElseThrow()
            + "/build";
    var r = post(path, HttpRequest.BodyPublishers.noBody(), "application/x-www-form-urlencoded");
    String loc =
        r.headers()
            .firstValue("Location")
            .orElseThrow(
                () ->
                    new Problem(
                        "submission_unknown",
                        "Seed gestartet, aber keine Queue-Kennung erhalten."));
    return Json.map("queue_url", trusted(loc));
  }

  public Map<String, Object> seedStatus(Map<String, Object> item) {
    if (!item.containsKey("build_url")) {
      var q = get(Json.required(item, "queue_url").replaceAll("/+$", "") + "/api/json");
      if (Json.bool(q, "cancelled", false))
        throw new Problem("seed_cancelled", "Seed wurde abgebrochen.");
      var executable = Json.obj(q.get("executable"));
      if (executable.isEmpty()) return Json.map("complete", false);
      item.put("build_url", trusted(Json.required(executable, "url")));
    }
    var r = get(Json.required(item, "build_url").replaceAll("/+$", "") + "/api/json");
    return Json.map("complete", !Json.bool(r, "building", false), "result", r.get("result"));
  }

  public Map<String, Object> submit(
      String org, String id, String issue, Path data, Path metadata, String comment) {
    if (data == null && metadata == null)
      throw new Problem("empty_delivery", "Mindestens eine Lieferdatei ist erforderlich.");
    String boundary = "integrator-" + UUID.randomUUID();
    var parts = new ArrayList<HttpRequest.BodyPublisher>();
    var fields =
        Json.map(
            "ORGANISATION",
            org,
            "DATASET",
            id,
            "SERIES_ID",
            data == null || issue == null ? "" : issue,
            "PUBLICATION_MODE",
            "delivery",
            "RELOAD_PORTAL",
            Json.bool(env, "reload_portal", true) ? "true" : "false",
            "COMMENT",
            comment);
    fields.forEach(
        (key, value) ->
            parts.add(
                HttpRequest.BodyPublishers.ofString(
                    "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\""
                        + key
                        + "\"\r\n\r\n"
                        + value
                        + "\r\n")));
    for (var entry : Json.map("DATA_FILE", data, "METADATA_FILE", metadata).entrySet())
      if (entry.getValue() instanceof Path p) {
        parts.add(
            HttpRequest.BodyPublishers.ofString(
                "--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\""
                    + entry.getKey()
                    + "\"; filename=\""
                    + p.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_")
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"));
        try {
          parts.add(HttpRequest.BodyPublishers.ofFile(p));
        } catch (IOException e) {
          throw new Problem("file_missing", "Lieferdatei fehlt.");
        }
        parts.add(HttpRequest.BodyPublishers.ofString("\r\n"));
      }
    parts.add(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n"));
    var r =
        post(
            "gretl-datenportal/build",
            HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new)),
            "multipart/form-data; boundary=" + boundary);
    String location =
        trusted(
            r.headers()
                .firstValue("Location")
                .orElseThrow(
                    () ->
                        new Problem(
                            "submission_unknown",
                            "Lieferung möglicherweise gestartet; keine Laufkennung erhalten.")));
    var q = query(URI.create(location).getRawQuery());
    if (!q.containsKey("job") || !q.containsKey("queue"))
      throw new Problem(
          "submission_unknown", "Jenkins-Rückleitung enthält keine eindeutige Queue-Kennung.");
    return Json.map("job", q.get("job"), "queue", q.get("queue"), "details_url", location);
  }

  static Map<String, Object> query(String raw) {
    var r = Json.map();
    if (raw != null)
      for (String part : raw.split("&")) {
        String[] kv = part.split("=", 2);
        r.put(
            URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
            kv.length < 2 ? "" : URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
      }
    return r;
  }

  public Map<String, Object> status(Map<String, Object> item) {
    var parameters = new ArrayList<String>();
    for (String key : List.of("job", "queue", "build"))
      if (item.get(key) != null) parameters.add(key + "=" + encode(item.get(key).toString()));
    var result = get("gretl-datenportal/runStatus?" + String.join("&", parameters));
    if (result.containsKey("buildNumber")) item.put("build", result.get("buildNumber").toString());
    return result;
  }

  public Map<String, Object> report(Map<String, Object> status) {
    var artifacts =
        Json.list(status.get("artifacts")).stream()
            .map(Json::obj)
            .filter(a -> Objects.equals(a.get("fileName"), "report.json"))
            .toList();
    if (artifacts.size() != 1)
      throw new Problem("report_missing", "Kein eindeutiger Publikationsbericht im Jenkins-Lauf.");
    return get(Json.required(artifacts.getFirst(), "url"));
  }

  public static HttpResponse<byte[]> publicGet(String url) {
    try {
      var r =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NEVER)
              .build()
              .send(
                  HttpRequest.newBuilder(Settings.url(url))
                      .timeout(Duration.ofSeconds(60))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() != 200 && r.statusCode() != 404)
        throw new Problem(
            "remote_read_failed",
            "Bestand nicht lesbar; kein leerer Erstbestand.",
            "status",
            r.statusCode(),
            "url",
            url);
      return r;
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem("remote_read_failed", "Bestand nicht lesbar.", "url", url);
    }
  }

  public static String artifact(Map<String, Object> env, String filename) {
    if (filename == null
        || filename.isBlank()
        || Set.of(".", "..").contains(filename)
        || filename.matches(".*[/\\\\?#%].*"))
      throw new Problem("invalid_artifact", "Manifest enthält unzulässigen Artefaktnamen.");
    return URI.create(Json.required(env, "manifest_url")).resolve(encode(filename)).toString();
  }

  public static Map<String, Object> manifest(Map<String, Object> env) {
    var r = publicGet(Json.required(env, "manifest_url"));
    if (r.statusCode() == 404) return null;
    try {
      var m = Json.read(new String(r.body(), StandardCharsets.UTF_8));
      Json.required(m, "releaseId");
      Json.required(m, "datasheets");
      for (String key : List.of("datasheets", "catalog", "duckdb"))
        if (m.get(key) != null) artifact(env, m.get(key).toString());
      return m;
    } catch (Problem e) {
      throw new Problem(
          "manifest_invalid",
          "Vorhandenes Manifest ist beschädigt; keine Initialisierung erlaubt.");
    }
  }

  public record Accepted(Map<String, Object> manifest, String xml) {}

  public static Accepted accepted(Map<String, Object> env, String id) {
    var m = manifest(env);
    if (m == null) return new Accepted(null, null);
    var r = publicGet(artifact(env, Json.required(m, "datasheets")));
    if (r.statusCode() != 200)
      throw new Problem(
          "accepted_sheet_missing", "Manifest verweist auf fehlende Datenblattsammlung.");
    return new Accepted(m, Xml.select(new String(r.body(), StandardCharsets.UTF_8), id));
  }

  public Map<String, Object> verify(
      Map<String, Object> report, Map<String, Object> sheet, Path source) {
    var result =
        Json.map(
            "publication",
            report.get("publication"),
            "reload",
            report.get("reload"),
            "opendata",
            report.get("opendata"),
            "visible",
            false,
            "verified",
            false);
    if (!Objects.equals(report.get("publication"), "accepted"))
      return message(result, "Lieferung nicht als publiziert bestätigt; Bericht prüfen.");
    var m = manifest(env);
    if (m == null || !Objects.equals(m.get("releaseId"), report.get("releaseId")))
      return message(
          result, "Manifest entspricht nicht diesem Lauf. Keine erneute Lieferung starten.");
    var expected = publicGet(artifact(env, Json.required(m, "catalog")));
    var actual = publicGet(Json.required(env, "portal_url") + "/catalog/published-catalog.xtf");
    if (expected.statusCode() != 200
        || actual.statusCode() != 200
        || !Arrays.equals(expected.body(), actual.body()))
      return message(
          result,
          "Publiziert, aber Portal hat Katalog noch nicht geladen. Reload separat klären; nicht erneut liefern.");
    result.put("reload_observed", true);
    if (!Objects.equals(Json.obj(report.get("opendata")).get("status"), "accepted"))
      return message(result, "Katalog publiziert; RDF-Übernahme nicht bestätigt.");
    var accepted = publicGet(artifact(env, Json.required(m, "datasheets")));
    var roots =
        Xml.datasets(Xml.parse(new String(accepted.body(), StandardCharsets.UTF_8))).stream()
            .filter(n -> Objects.equals(sheet.get("identifier"), Xml.text(n, "identifier")))
            .toList();
    if (roots.size() != 1
        || !Objects.equals(
            Xml.business(Xml.structure(roots.getFirst())), Xml.business(sheet.get("fields"))))
      return message(
          result,
          "Angenommene Metadaten entsprechen nicht freigegebenem Fachinhalt. Bestand und Lauf prüfen.");
    result.put("metadata_verified", true);
    result.put(
        "managed_dates",
        Json.map(
            "issued",
            Xml.text(roots.getFirst(), "issued"),
            "modified",
            Xml.text(roots.getFirst(), "modified")));
    String target = Json.str(sheet, "selected_issue", Json.required(sheet, "identifier"));
    var nodes =
        Xml.all(Xml.parse(new String(actual.body(), StandardCharsets.UTF_8))).stream()
            .filter(
                n ->
                    Set.of("Dataset", "DatasetSeries", "DatasetIssue").contains(Xml.local(n))
                        && target.equals(Xml.text(n, "identifier")))
            .toList();
    if (!Objects.equals(sheet.get("publication_status"), "published")) {
      result.put("verified", nodes.isEmpty());
      return message(
          result,
          nodes.isEmpty()
              ? "Bestand übernommen; Thema entsprechend Status nicht öffentlich."
              : "Thema trotz nicht öffentlichem Status noch im Katalog.");
    }
    if (nodes.size() != 1)
      return message(result, "Thema/Ausgabe fehlt oder ist mehrdeutig im publizierten Katalog.");
    var downloads = new ArrayList<Object>();
    for (var d : Xml.all(nodes.getFirst()))
      if (Xml.local(d).equals("Distribution")) {
        String url = Xml.text(d, "downloadURL");
        if (url == null) continue;
        var download = download(url);
        downloads.add(
            Json.map(
                "format",
                Xml.text(d, "format"),
                "url",
                url,
                "bytes",
                download.get("bytes"),
                "sha256",
                download.get("sha256")));
      }
    result.put("downloads", downloads);
    if (source != null) {
      var formats = Json.map();
      downloads.stream().map(Json::obj).forEach(d -> formats.put(Json.str(d, "format", ""), d));
      if (!formats.keySet().containsAll(Set.of("csv", "xlsx", "parquet"))
          || !Objects.equals(Json.obj(formats.get("csv")).get("sha256"), Json.sha(source)))
        return message(result, "Downloads fehlen oder CSV entspricht nicht der gelieferten Datei.");
    }
    String suffix = "/datasets/" + encode(Json.required(sheet, "identifier"));
    if (Objects.equals(sheet.get("kind"), "series")) {
      suffix = "/series/" + encode(Json.required(sheet, "identifier"));
      if (sheet.get("selected_issue") != null)
        suffix += "/issues/" + encode(sheet.get("selected_issue").toString());
    }
    String url = Json.required(env, "portal_url") + suffix;
    result.put("portal_url", url);
    boolean visible = publicGet(url).statusCode() == 200;
    result.put("visible", visible);
    result.put("verified", visible);
    return message(
        result,
        visible
            ? "Publikation, Portalstand und Downloads bestätigt."
            : "Thema noch nicht im Portal sichtbar.");
  }

  static Map<String, Object> message(Map<String, Object> r, String message) {
    r.put("message", message);
    return r;
  }

  static Map<String, Object> download(String url) {
    try {
      var response =
          HttpClient.newBuilder()
              .followRedirects(HttpClient.Redirect.NEVER)
              .build()
              .send(
                  HttpRequest.newBuilder(Settings.url(url))
                      .timeout(Duration.ofSeconds(60))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofInputStream());
      try (var input = response.body()) {
        if (response.statusCode() != 200)
          throw new Problem(
              "download_missing",
              "Publizierter Download fehlt.",
              "url",
              url,
              "status",
              response.statusCode());
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        long bytes = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
          digest.update(buffer, 0, length);
          bytes += length;
        }
        return Json.map("bytes", bytes, "sha256", HexFormat.of().formatHex(digest.digest()));
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(
          "remote_read_failed", "Download konnte nicht vollständig geprüft werden.", "url", url);
    }
  }
}
