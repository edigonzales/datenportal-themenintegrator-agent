package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Content-based discovery; never edits a repository file or opens a datasheet draft. */
final class RepositoryDatasheets {
  final Workflow w;

  RepositoryDatasheets(Workflow w) {
    this.w = w;
  }

  Map<String, Object> discover(Map<String, Object> r) {
    Path folder = Json.inside(w.settings.topics, w.topic(r));
    var candidates = new ArrayList<Object>();
    var diagnostics = new ArrayList<Object>();
    if (!Files.isDirectory(folder))
      return Json.map(
          "candidates",
          candidates,
          "diagnostics",
          List.of(Json.map("code", "topic_directory_missing", "path", folder.toString())));
    try {
      Path realFolder = folder.toRealPath();
      if (!realFolder.equals(w.settings.topics.toRealPath().resolve(w.topic(r)).normalize()))
        throw new Problem("unsafe_path", "Themenordner liegt ausserhalb des Repositorys.");
      try (var paths = Files.list(folder)) {
        for (Path path : paths.sorted().toList()) {
          if (!path.getFileName().toString().matches("(?i).+\\.(xtf|xml)")) continue;
          try {
            if (!path.toRealPath().startsWith(realFolder))
              throw new Problem("unsafe_path", "Datei verlässt den Themenordner.");
            if (!Files.isRegularFile(path)) continue;
            var doc = Xml.parse(path);
            long matches =
                Xml.datasets(doc).stream()
                    .filter(n -> Objects.equals(r.get("identifier"), Xml.text(n, "identifier")))
                    .count();
            if (matches == 1)
              candidates.add(
                  Json.map(
                      "source_path",
                      path.toString(),
                      "relative_path",
                      w.topic(r) + "/" + path.getFileName(),
                      "sha256",
                      Json.sha(path)));
            else
              diagnostics.add(
                  Json.map(
                      "path",
                      path.toString(),
                      "code",
                      matches == 0 ? "datasheet_not_matching" : "ambiguous_datasheet"));
          } catch (Problem e) {
            diagnostics.add(
                Json.map("path", path.toString(), "code", e.code, "message", e.getMessage()));
          } catch (java.io.IOException e) {
            diagnostics.add(Json.map("path", path.toString(), "code", "file_unavailable"));
          }
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem("repository_unavailable", "Themenordner nicht lesbar.");
    }
    return Json.map("candidates", candidates, "diagnostics", diagnostics);
  }

  Map<String, Object> choose(Map<String, Object> r, String relative) {
    var result = discover(r);
    var candidates =
        Json.list(result.get("candidates")).stream()
            .map(Json::obj)
            .filter(c -> relative == null || relative.equals(c.get("relative_path")))
            .toList();
    if (candidates.size() != 1)
      throw new Problem(
          candidates.isEmpty() ? "repository_datasheet_missing" : "ambiguous_repository_metadata",
          "Genau ein passendes Datenblatt auswählen; relative_path bezeichnet einen aufgeführten Repository-Pfad.",
          "candidates",
          result.get("candidates"),
          "diagnostics",
          result.get("diagnostics"));
    var selected = new LinkedHashMap<>(candidates.getFirst());
    selected.put("diagnostics", result.get("diagnostics"));
    return selected;
  }

  Object select(Map<String, Object> r, String relative) {
    var selected = choose(r, relative);
    var prior = Json.obj(r.get("repository_datasheet"));
    boolean same =
        Objects.equals(prior.get("relative_path"), selected.get("relative_path"))
            && Objects.equals(prior.get("sha256"), selected.get("sha256"));
    if (same && !r.containsKey("draft")) {
      Path active = w.store.file(r, "metadata");
      same = active != null && Objects.equals(Json.sha(active), prior.get("sha256"));
    }
    if (r.containsKey("pending_metadata") || (!same && r.containsKey("draft")))
      throw new Problem(
          "metadata_source_conflict",
          "Vorhandenen oder unbestätigten Datenblatt-Entwurf zuerst klären; Quelle wird nicht ersetzt.");
    if (!same) {
      Path path = Path.of(Json.required(selected, "source_path"));
      // Check topic/issue before selecting and preserve the exact source bytes.
      Xml.describe(path, Json.required(r, "identifier"), Json.str(r, "issue", null));
      var saved = w.store.attach(r, path, "metadata");
      if (!Objects.equals(selected.get("sha256"), saved.get("sha256"))
          || !Objects.equals(saved.get("sha256"), Json.sha(Path.of(Json.required(saved, "path")))))
        throw new Problem(
            "input_checksum_mismatch", "Datenblatt wurde während der Übernahme verändert.");
      selected.put("path", saved.get("path"));
      r.put("repository_datasheet", selected);
    } else selected = new LinkedHashMap<>(prior);
    selected.put(
        "datasheet",
        Xml.describe(
            Path.of(Json.required(selected, "path")),
            Json.required(r, "identifier"),
            Json.str(r, "issue", null)));
    selected.put(
        "next_operation",
        "analyze; danach derive_model mit tatsächlicher Datenfreigabe. Kein import_xtf zur Vorbereitung erforderlich.");
    return selected;
  }
}
