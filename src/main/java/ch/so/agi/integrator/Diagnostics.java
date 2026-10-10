package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/**
 * Immutable, checksummed diagnostic snapshots. Paths never participate in model approval hashes.
 */
final class Diagnostics {
  static Map<String, Object> record(
      Workflow w,
      Map<String, Object> run,
      String type,
      String result,
      Object evidence,
      boolean repairable) {
    String id = UUID.randomUUID().toString();
    Path directory = w.directory(run).resolve("diagnostics").resolve(id).toAbsolutePath();
    Path raw = directory.resolve("evidence.json"), log = directory.resolve("diagnostics.log");
    Json.write(raw, Json.pretty(evidence));
    var text =
        new StringBuilder(
            type.equals("ili2c")
                ? "Aus strukturierten MCP-Diagnosen erzeugtes Protokoll (kein originales ili2c-Log).\n"
                : "Diagnoseprotokoll des Integrators; originale Werkzeuglogs sind separat verlinkt.\n");
    text.append("Prüfung: ").append(type).append("\nErgebnis: ").append(result).append("\n\n");
    messages(evidence, "Werkzeug", text);
    Json.write(log, text.toString());
    var artifacts = new ArrayList<Object>();
    artifacts.add(artifact(type, result, log));
    artifacts.add(artifact(type, result, raw));
    collect(evidence, w.directory(run), directory, type, result, artifacts);
    if (repairable && result.equals("FAILED")) ModelRepairs.failed(run, id);
    var series = Json.obj(run.get("model_repair"));
    var check =
        Json.map(
            "check_id",
            id,
            "check_type",
            type,
            "result",
            result,
            "at",
            Json.now(),
            "attempt",
            Json.bool(series, "active", false) ? Json.number(series, "attempts", 0) : 0,
            "repair_series",
            Json.bool(series, "active", false) ? series.get("id") : null,
            "artifacts",
            artifacts);
    Json.list(run.computeIfAbsent("diagnostic_history", k -> new ArrayList<>())).add(check);
    w.persist(run);
    return check;
  }

  private static void messages(Object value, String location, StringBuilder text) {
    if (value instanceof Map<?, ?> map) {
      if (map.get("message") != null) {
        text.append(location)
            .append(": ")
            .append(map.getOrDefault("severity", null))
            .append(" ")
            .append(map.get("message"))
            .append('\n');
        for (String key : List.of("file", "line", "sourceExcerpt"))
          if (map.get(key) != null) text.append(key).append(": ").append(map.get(key)).append('\n');
      }
      for (var entry : map.entrySet()) {
        if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>)
          messages(entry.getValue(), location + "/" + entry.getKey(), text);
      }
    } else if (value instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        Object item = list.get(i);
        if (item instanceof String
            && (location.endsWith("/messages") || location.endsWith("/diagnostics")))
          text.append(item).append('\n');
        else messages(item, location + "[" + i + "]", text);
      }
    }
  }

  static Map<String, Object> artifact(String type, String result, Path file) {
    return Json.map(
        "check_type",
        type,
        "result",
        result,
        "path",
        file.toAbsolutePath().toString(),
        "sha256",
        Json.sha(file));
  }

  private static void collect(
      Object evidence, Path run, Path target, String type, String result, List<Object> artifacts) {
    if (evidence instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        if (Set.of("log", "reports").contains(entry.getKey())
            && entry.getValue() instanceof String value) {
          try {
            Path source = Path.of(value).toRealPath();
            if (!source.startsWith(run.toRealPath())) continue;
            var files =
                Files.isDirectory(source) ? Files.walk(source) : java.util.stream.Stream.of(source);
            try (files) {
              for (Path file :
                  files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (!file.toRealPath().startsWith(run.toRealPath())) continue;
                Path copy =
                    target.resolve("artifact-" + artifacts.size() + "-" + file.getFileName());
                Files.copy(file, copy);
                artifacts.add(artifact(type, result, copy));
              }
            }
          } catch (java.io.IOException | java.nio.file.InvalidPathException ignored) {
          }
        } else collect(entry.getValue(), run, target, type, result, artifacts);
      }
    } else if (evidence instanceof List<?> list)
      list.forEach(v -> collect(v, run, target, type, result, artifacts));
  }

  static String html(Path runDirectory, Object history) {
    var html = new StringBuilder("<section><h2>Prüfprotokolle und Versuchshistorie</h2>");
    for (Object item : Json.list(history)) {
      var check = Json.obj(item);
      html.append("<h3>")
          .append(Reports.escape(check.get("check_type")))
          .append(" · ")
          .append(Reports.escape(check.get("result")))
          .append(" · Reparaturversuch ")
          .append(Reports.escape(check.get("attempt")))
          .append("</h3><ul>");
      for (Object a : Json.list(check.get("artifacts"))) {
        var artifact = Json.obj(a);
        try {
          Path file = Path.of(Json.required(artifact, "path")).toRealPath();
          if (!file.startsWith(runDirectory.toRealPath().resolve("diagnostics"))
              || !Objects.equals(Json.sha(file), artifact.get("sha256"))) continue;
          html.append("<li><a class=\"text\" href=\"")
              .append(Reports.escape(file.toUri().toASCIIString()))
              .append("\">")
              .append(Reports.escape(file.toString()))
              .append("</a></li>");
        } catch (java.io.IOException | Problem | InvalidPathException ignored) {
        }
      }
      html.append("</ul>");
    }
    return html.append("</section>").toString();
  }
}
