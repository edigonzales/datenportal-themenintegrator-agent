package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** File MCP has its own path boundary; MCP processes do not inherit client sandboxes. */
final class WorkspaceFiles {
  static final int MAX_TEXT = 512 * 1024;
  static final Map<String, Operations.Operation> TOOLS = new LinkedHashMap<>();

  static {
    tool(
        "import_attachment",
        "Original CSV/XTF/XLSX unverändert übernehmen; drei SHA-256 prüfen. Keine Freigabe.",
        "organization!",
        "path!");
    tool(
        "read_file",
        "Zulässige Datei abschnittweise lesen (max. 6000 Zeichen). next_offset verwenden. report_url im Browser öffnen; keine erneute Prüfung zum Lesen. Keine Quellen/Secrets/state.json.",
        "path!",
        "offset:integer",
        "limit:integer");
    tool(
        "open_report",
        "Vorhandenen HTML-Prüfbericht im lokalen Standardbrowser anzeigen. Keine Prüfung/Freigabe/Publikation.",
        "path!");
    tool(
        "list_files",
        "Zulässige Dateien eines konkreten Fachverzeichnisses auflisten; keine rekursive Quellcodesuche.",
        "path!");
    tool(
        "write_file",
        "Nur Konverterrezepte und Java-Konverter/JUnit unter topics/<org>/<id> schreiben. Kein Datenblatt-/Modell-/Zustandseditor.",
        "path!",
        "content!",
        "expected_sha256");
  }

  static void tool(String name, String description, String... fields) {
    TOOLS.put(name, Operations.definition(description, fields));
  }

  final Settings s;

  @FunctionalInterface
  interface CopyAction {
    void copy(Path source, Path destination) throws java.io.IOException;
  }

  final CopyAction copier;

  WorkspaceFiles(Settings s) {
    this(s, (source, destination) -> Files.copy(source, destination));
  }

  WorkspaceFiles(Settings s, CopyAction copier) {
    this.s = s;
    this.copier = copier;
  }

  Path packagePath(String relative) {
    try {
      return s.root.toRealPath().resolve(relative).normalize();
    } catch (java.io.IOException e) {
      throw denied();
    }
  }

  Object call(String operation, Map<String, Object> args) {
    Operations.validate(TOOLS, operation, args);
    return switch (operation) {
      case "import_attachment" ->
          importAttachment(Json.required(args, "organization"), Json.required(args, "path"));
      case "read_file" ->
          read(
              Json.required(args, "path"),
              Json.number(args, "offset", 0),
              Json.number(args, "limit", 6000));
      case "open_report" -> openReport(Json.required(args, "path"));
      case "list_files" -> list(Json.required(args, "path"));
      case "write_file" -> write(args);
      default -> throw new Problem("unknown_operation", "Unbekanntes Dateiwerkzeug.");
    };
  }

  Path canonical(String value) {
    Path path = s.root.resolve(value).toAbsolutePath().normalize();
    try {
      Path ancestor = path;
      while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
      return ancestor.toRealPath().resolve(ancestor.relativize(path)).normalize();
    } catch (java.io.IOException | NullPointerException e) {
      throw new Problem("unsafe_path", "Dateipfad kann nicht aufgelöst werden.");
    }
  }

  boolean beneath(Path path, Path base) {
    try {
      return path.startsWith(base.toRealPath());
    } catch (java.io.IOException e) {
      return path.startsWith(base.toAbsolutePath().normalize());
    }
  }

  boolean readable(Path path) {
    String name = path.getFileName().toString();
    if (name.startsWith(".") || Set.of("state.json", "credentials.json").contains(name))
      return false;
    if (referencePath(path) && name.endsWith(".md")) return true;
    if (path.equals(packagePath("AGENTS.md"))
        || path.equals(packagePath("README.md"))
        || path.equals(packagePath("docs/anwenderhandbuch.md"))) return true;
    if (path.startsWith(packagePath("topics"))) return converterPath(path);
    if (path.startsWith(packagePath(".datenportal-integrator/input")))
      return name.matches("(?i).+\\.(csv|xtf)");
    if (beneath(path, s.runs)
        && beneath(path, s.runs.resolve("responses"))
        && name.matches("[0-9a-f-]{36}[.]json")) return true;
    if (beneath(path, s.runs)) return name.matches("(?i).+\\.(html|log|ili|xtf|xml|csv|gradle)");
    if (beneath(path, s.topics)) {
      Path relative = s.topics.toAbsolutePath().normalize().relativize(path);
      if (relative.getNameCount() < 2) return false;
      for (Path part : relative)
        if (part.toString().startsWith(".")
            || Set.of("src", "build", "config").contains(part.toString())) return false;
      return name.matches("(?i).+\\.(xtf|xml|ili|gradle|yaml|yml)");
    }
    return false;
  }

  boolean referencePath(Path path) {
    return List.of("skills", ".agents/skills", "docs/agent").stream()
        .map(this::packagePath)
        .anyMatch(path::startsWith);
  }

  boolean converterPath(Path path) {
    Path base = packagePath("topics");
    if (!path.startsWith(base)) return false;
    Path relative = base.relativize(path);
    if (relative.getNameCount() < 3) return false;
    for (Path part : relative)
      if (part.toString().startsWith(".")
          || Set.of("src", "build", "config").contains(part.toString())) return false;
    return path.getFileName().toString().matches("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.(java|json)");
  }

  Object read(String value) {
    return read(value, 0, 6000);
  }

  Object read(String value, int offset, int limit) {
    Path path = canonical(value);
    if (!readable(path)) throw denied();
    if (offset < 0 || limit < 1 || limit > 6000)
      throw new Problem(
          "invalid_arguments", "offset muss >= 0 und limit zwischen 1 und 6000 liegen.");
    try {
      if (!Files.isRegularFile(path) || Files.size(path) > 32 * 1024 * 1024)
        throw new Problem("workspace_file_size", "Textdatei fehlt oder ist grösser als 32 MiB.");
      String text = Files.readString(path);
      if (offset > text.length())
        throw new Problem("invalid_arguments", "offset liegt hinter dem Dateiende.");
      int end = Math.min(text.length(), offset + limit);
      var result =
          Json.map(
              "path",
              path.toString(),
              "content",
              text.substring(offset, end),
              "sha256",
              Json.sha(path),
              "offset",
              offset,
              "next_offset",
              end < text.length() ? end : null,
              "total_characters",
              text.length(),
              "complete",
              end == text.length());
      if (path.getFileName().toString().endsWith(".html"))
        result.put("report_url", path.toUri().toString());
      return result;
    } catch (java.io.IOException e) {
      throw new Problem("file_missing", "Fachdatei ist nicht lesbar.");
    }
  }

  Object openReport(String value) {
    Path path = canonical(value);
    if (!readable(path)
        || !beneath(path, s.runs)
        || !path.getFileName().toString().endsWith("-review.html")
        || !Files.isRegularFile(path)) throw denied();
    try {
      if (System.getProperty("os.name").startsWith("Mac")) {
        if (new ProcessBuilder("/usr/bin/open", path.toString()).start().waitFor() != 0)
          throw new java.io.IOException("Browserstart fehlgeschlagen");
      } else if (java.awt.Desktop.isDesktopSupported()) {
        java.awt.Desktop.getDesktop().browse(path.toUri());
      } else {
        throw new java.io.IOException("Kein lokaler Browser verfügbar");
      }
      return Json.map(
          "opened", true, "path", path.toString(), "report_url", path.toUri().toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Problem(
          "report_open_failed", "Browserstart unterbrochen.", "path", path.toString());
    } catch (java.io.IOException | UnsupportedOperationException e) {
      throw new Problem(
          "report_open_failed",
          "Bericht über den angegebenen Dateipfad im Browser öffnen.",
          "path",
          path.toString());
    }
  }

  Object list(String value) {
    Path path = canonical(value);
    if (!List.of(
                packagePath("skills"),
                packagePath(".agents/skills"),
                packagePath("docs"),
                packagePath("topics"),
                packagePath(".datenportal-integrator/input"))
            .stream()
            .anyMatch(path::startsWith)
        && !beneath(path, s.runs)
        && !beneath(path, s.topics)) throw denied();
    try (var files = Files.list(path)) {
      var entries = new ArrayList<Object>();
      for (Path file : files.sorted().limit(500).toList()) {
        Path real = canonical(file.toString());
        if (Files.isRegularFile(real) && readable(real))
          entries.add(Json.map("path", real.toString(), "directory", false));
        else if (Files.isDirectory(real)
            && (!Files.isSymbolicLink(file) || referencePath(real))
            && !file.getFileName().toString().startsWith(".")
            && !Set.of("src", "build", "config").contains(file.getFileName().toString()))
          entries.add(Json.map("path", real.toString(), "directory", true));
      }
      return Json.map("path", path.toString(), "entries", entries);
    } catch (java.io.IOException e) {
      throw new Problem("file_missing", "Fachverzeichnis ist nicht lesbar.");
    }
  }

  Object write(Map<String, Object> args) {
    Path path = canonical(Json.required(args, "path"));
    if (!converterPath(path)) throw denied();
    String content = Json.required(args, "content");
    if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT)
      throw new Problem("workspace_file_size", "Arbeitsdatei ist grösser als 512 KiB.");
    if (Files.exists(path) && !Objects.equals(Json.sha(path), args.get("expected_sha256")))
      throw new Problem(
          "workspace_file_conflict", "Vor Änderung aktuellen Stand lesen und SHA-256 angeben.");
    Json.write(path, content);
    return Json.map("path", path.toString(), "sha256", Json.sha(path));
  }

  void handoff(String value, String role) {
    Path path = canonical(value);
    String extension =
        switch (role) {
          case "data" -> "csv";
          case "metadata" -> "xtf";
          case "metadata_source" -> "xlsx";
          case "model" -> "ili";
          case "change" -> "xtf|ili|gradle|yaml|yml";
          default -> throw denied();
        };
    if (!path.getFileName().toString().matches("(?i).+\\.(" + extension + ")")) throw denied();
    if (path.startsWith(packagePath(".datenportal-integrator/input"))) {
      Path receipt = path.getParent().resolve(".verified-input.json");
      if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)) throw denied();
      var proof = Json.read(receipt);
      String sha = Json.sha(path);
      if (!path.getFileName().toString().equals(proof.get("name"))
          || !sha.equals(proof.get("source_sha256_before"))
          || !sha.equals(proof.get("source_sha256_after"))
          || !sha.equals(proof.get("copy_sha256")))
        throw new Problem("input_checksum_mismatch", "Verifizierte Eingangskopie wurde verändert.");
      return;
    }
    if (beneath(path, s.runs)) return;
    if (!Set.of("data", "metadata_source").contains(role) && readable(path)) return;
    throw denied();
  }

  Map<String, Object> importAttachment(String organization, String value) {
    Store.identifier(organization);
    Path original = Path.of(value);
    if (!original.isAbsolute()) throw new Problem("unsafe_path", "Absoluten Originalpfad angeben.");
    String name = original.getFileName().toString();
    if (name.toLowerCase(Locale.ROOT).endsWith(".csv") && !name.matches("(?i)^[a-z0-9_]+\\.csv$"))
      throw new Problem(
          "invalid_csv_filename",
          "CSV-Originalnamen nur mit ASCII-Buchstaben, Ziffern und Unterstrichen verwenden; Umbenennung ausdrücklich beauftragen.");
    Path source = canonical(value);
    if (!name.matches("(?i).+\\.(csv|xtf|xlsx)")
        || !source.getFileName().toString().matches("(?i).+\\.(csv|xtf|xlsx)")) throw denied();
    if (!Files.isRegularFile(source))
      throw new Problem(
          "input_path_unavailable",
          "Originaldatei ist nicht vorhanden; vorhandenen, lesbaren absoluten Originalpfad angeben, Datei nicht rekonstruieren.",
          "path",
          value);
    var roots = new ArrayList<Path>();
    roots.add(s.root.getParent());
    for (String root : s.strings("input_roots", List.of())) roots.add(s.path(root));
    String configuredRoots = System.getenv("DATENPORTAL_INPUT_ROOTS");
    if (configuredRoots != null)
      for (String root : configuredRoots.split("\n")) if (!root.isBlank()) roots.add(Path.of(root));
    if (roots.stream().noneMatch(root -> beneath(source, root)))
      throw new Problem(
          "input_path_not_allowed",
          "Originalpfad ist nicht freigegeben; Eingangsverzeichnis in input_roots in config/local.toml ergänzen und MCP-Verbindungen neu starten.",
          "path",
          value);
    Path base = packagePath(".datenportal-integrator/input");
    try {
      if (!canonical(base.toString()).startsWith(packagePath(".datenportal-integrator/input")))
        throw denied();
      Files.createDirectories(base);
      Path directory = Json.inside(base, organization + "/" + UUID.randomUUID());
      Files.createDirectories(directory);
      Path copy = directory.resolve(name);
      String before = Json.sha(source);
      copier.copy(source, copy);
      String after = Json.sha(source), copied = Json.sha(copy);
      if (!before.equals(after) || !before.equals(copied)) {
        Files.delete(copy);
        throw new Problem(
            "input_checksum_mismatch",
            "Original hat sich verändert; Datei wird nicht weitergereicht.");
      }
      Json.atomic(
          directory.resolve(".verified-input.json"),
          Json.map(
              "name", name,
              "source_sha256_before", before,
              "source_sha256_after", after,
              "copy_sha256", copied));
      return Json.map(
          "path",
          copy.toString(),
          "source_sha256_before",
          before,
          "source_sha256_after",
          after,
          "copy_sha256",
          copied,
          "verified",
          true);
    } catch (java.io.IOException e) {
      throw new Problem(
          "input_copy_failed",
          "Originalpfad oder Eingang ist nicht zugänglich; keine Datei rekonstruieren.");
    }
  }

  static Problem denied() {
    return new Problem(
        "workspace_access_denied", "Pfad gehört nicht zu den freigegebenen fachlichen Dateien.");
  }
}
