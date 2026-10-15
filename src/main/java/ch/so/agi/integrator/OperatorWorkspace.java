package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Installs an allowlisted, source-free distribution without moving business state. */
final class OperatorWorkspace {
  static final String MANIFEST = ".datenportal-workspace.json";

  static boolean installed(Path root) {
    if (!Files.isRegularFile(root.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)) return false;
    var manifest = Json.read(root.resolve(MANIFEST));
    if (!Objects.equals(manifest.get("schema_version"), 2)
        || !"operator".equals(manifest.get("kind")))
      throw new Problem(
          "workspace_manifest_invalid",
          "Neuen Java-Arbeitsbereich anlegen; alter Docker-Arbeitsbereich bleibt bei seiner bisherigen Version.");
    return true;
  }

  static Path bundle(Path root) {
    return AgentResources.installed()
        ? AgentResources.resolve(root, "workspace")
        : root.resolve("build/operator-workspace");
  }

  static Object command(List<String> args) {
    boolean preserve =
        args.size() == 3
            && args.getFirst().equals("update")
            && args.get(2).equals("--preserve-run-state");
    if ((!preserve && args.size() != 2) || !Set.of("install", "update").contains(args.getFirst()))
      throw new Problem(
          "invalid_arguments",
          "workspace install|update <absoluter Zielordner> [--preserve-run-state nur bei update]");
    Path target = Path.of(args.get(1));
    if (!target.isAbsolute()) throw new Problem("unsafe_path", "Absoluten Zielordner angeben.");
    Path previousHome =
        args.getFirst().equals("update") ? ApprovalMigration.previousHome(target) : null;
    String currentHome =
        previousHome == null
            ? null
            : Json.str(Json.read(target.resolve(MANIFEST)), "package_home", null);
    var result =
        install(bundle(Path.of("").toAbsolutePath()), target, args.getFirst().equals("update"));
    if (AgentResources.installed()) {
      Path launcher = target.resolve("bin/datenportal-agent");
      Json.write(
          launcher,
          "#!/usr/bin/env bash\nset -e\ncd -- "
              + GitHubCredentials.quote(target.toString())
              + "\nexec "
              + GitHubCredentials.quote(
                  AgentResources.resolve(target, "bin/datenportal-agent").toString())
              + " \"$@\"\n");
      launcher.toFile().setExecutable(true);
      var manifest = Json.read(target.resolve(MANIFEST));
      Json.obj(manifest.get("managed_files")).put("bin/datenportal-agent", Json.sha(launcher));
      manifest.put("package_sha256", Json.digest(manifest.get("managed_files")));
      result.put("package_sha256", manifest.get("package_sha256"));
      manifest.put("package_home", AgentResources.resolve(target, "").toString());
      if (previousHome != null) manifest.put("legacy_package_home", previousHome.toString());
      Json.atomic(target.resolve(MANIFEST), manifest);
      if (previousHome != null && !preserve)
        result.put(
            "approval_migration",
            ApprovalMigration.update(target, previousHome, AgentResources.resolve(target, "")));
      if (currentHome != null && !preserve)
        result.put(
            "data_approval_migration",
            DataApprovalMigration.update(
                target, Path.of(currentHome), AgentResources.resolve(target, "")));
    }
    if (preserve) result.put("run_state_preserved", true);
    return result;
  }

  static Map<String, Object> inventory(Path bundle) {
    var files = Json.map();
    try (var paths = Files.walk(bundle)) {
      for (Path file : paths.sorted().toList()) {
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
        String relative = bundle.relativize(file).toString().replace('\\', '/');
        if (relative.equals(MANIFEST)) continue;
        if (Files.isSymbolicLink(file)) {
          Path link = Files.readSymbolicLink(file);
          if (link.isAbsolute() || !file.getParent().resolve(link).normalize().startsWith(bundle))
            throw new Problem(
                "workspace_bundle_invalid", "Externe Paketverknüpfung.", "path", relative);
          files.put(relative, "link:" + link.toString().replace('\\', '/'));
        } else files.put(relative, Json.sha(file));
      }
    } catch (java.io.IOException e) {
      throw new Problem(
          "workspace_bundle_missing", "Betriebspaket fehlt; operatorWorkspace bauen.");
    }
    if (!files.containsKey("AGENTS.md") || !files.containsKey("bin/datenportal-agent"))
      throw new Problem("workspace_bundle_invalid", "Betriebspaket ist unvollständig.");
    return files;
  }

  static Map<String, Object> install(Path bundle, Path destination, boolean update) {
    bundle = bundle.toAbsolutePath().normalize();
    Path target = destination.toAbsolutePath().normalize();
    if (target.startsWith(bundle) || bundle.startsWith(target))
      throw new Problem("unsafe_path", "Paketquelle und Ziel dürfen sich nicht überlappen.");
    var candidates = inventory(bundle);
    Map<String, Object> previous = Json.map();
    var conflicts = new ArrayList<String>();
    try {
      if (Files.isSymbolicLink(target)) throw new Problem("unsafe_path", "Ziel ist ein Symlink.");
      if (update) {
        if (!installed(target))
          throw new Problem(
              "workspace_not_installed", "Ziel ist kein installiertes Betriebspaket.");
        previous = Json.obj(Json.read(target.resolve(MANIFEST)).get("managed_files"));
      } else if (Files.exists(target)) {
        try (var files = Files.list(target)) {
          if (files.findAny().isPresent())
            throw new Problem(
                "workspace_not_empty", "Installation benötigt einen leeren Zielordner.");
        }
      }
      var all = new TreeSet<String>(previous.keySet());
      all.addAll(candidates.keySet());
      for (String name : all) {
        Path file = safeTarget(target, name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
          if (previous.containsKey(name)) conflicts.add(name);
          continue;
        }
        String actual = identity(file);
        if (!Objects.equals(actual, previous.get(name))) conflicts.add(name);
      }
      if (!conflicts.isEmpty())
        throw new Problem(
            "workspace_update_conflict",
            "Individuelle Änderungen bleiben erhalten.",
            "paths",
            conflicts);
      Files.createDirectories(target);
      for (String name : previous.keySet())
        if (!candidates.containsKey(name)) Files.delete(safeTarget(target, name));
      for (var entry : candidates.entrySet()) {
        Path file = safeTarget(target, entry.getKey());
        Files.createDirectories(file.getParent());
        String value = entry.getValue().toString();
        if (value.startsWith("link:")) {
          Files.deleteIfExists(file);
          Files.createSymbolicLink(file, Path.of(value.substring(5)));
        } else {
          Path temporary = Files.createTempFile(file.getParent(), ".package-", ".tmp");
          try {
            Files.copy(
                bundle.resolve(entry.getKey()), temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                temporary,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
          } finally {
            Files.deleteIfExists(temporary);
          }
          if (entry.getKey().startsWith("bin/")) file.toFile().setExecutable(true, false);
        }
      }
      String version =
          Json.required(Json.read(bundle.resolve("workspace-package.json")), "version");
      Json.atomic(
          target.resolve(MANIFEST),
          Json.map(
              "schema_version",
              2,
              "kind",
              "operator",
              "version",
              version,
              "managed_files",
              candidates,
              "package_sha256",
              Json.digest(candidates)));
      return Json.map(
          "workspace",
          target.toString(),
          "version",
          version,
          "updated",
          update,
          "package_sha256",
          Json.digest(candidates),
          "next",
          "Konfiguration einrichten und bin/datenportal-agent init ausführen.");
    } catch (java.io.IOException e) {
      throw new Problem(
          "workspace_install_failed", "Betriebspaket konnte nicht installiert werden.");
    }
  }

  static Path safeTarget(Path root, String name) {
    Path result = root.resolve(name).normalize();
    if (Path.of(name).isAbsolute() || !result.startsWith(root) || result.equals(root))
      throw new Problem("unsafe_path", "Ungültiger Paketpfad.");
    for (Path parent = result.getParent();
        parent != null && parent.startsWith(root);
        parent = parent.getParent())
      if (Files.isSymbolicLink(parent))
        throw new Problem("unsafe_path", "Paketpfad enthält einen Symlink.");
    return result;
  }

  static String identity(Path file) throws java.io.IOException {
    return Files.isSymbolicLink(file)
        ? "link:" + Files.readSymbolicLink(file).toString().replace('\\', '/')
        : Files.isRegularFile(file) ? Json.sha(file) : "directory";
  }
}
