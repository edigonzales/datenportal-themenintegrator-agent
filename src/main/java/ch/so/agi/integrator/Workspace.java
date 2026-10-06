package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Workspace {
  final Workflow w;

  public Workspace(Workflow w) {
    this.w = w;
  }

  public Path snapshot(Map<String, Object> run, String purpose) {
    Path dest =
        w.directory(run).resolve("workspaces").resolve(purpose + "-" + System.currentTimeMillis());
    copy(w.settings.topics, dest);
    for (String rel : Json.obj(run.get("changes")).keySet()) {
      Path target = Json.inside(dest, rel);
      try {
        Files.createDirectories(target.getParent());
        Files.copy(w.repoFile(run, rel), target, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.io.IOException e) {
        throw new Problem("workspace_failed", "Arbeitskopie kann nicht erstellt werden.");
      }
    }
    return dest;
  }

  public static void copy(Path source, Path dest) {
    try {
      Files.createDirectories(dest);
      try (var paths = Files.walk(source)) {
        for (Path p : paths.toList()) {
          Path rel = source.relativize(p);
          if (rel.toString().isEmpty()) continue;
          boolean excluded = false;
          for (Path part : rel)
            if (Set.of(
                    ".git", ".gradle", "build", ".datenportal-integrator", ".venv", "__pycache__")
                .contains(part.toString())) {
              excluded = true;
              break;
            }
          if (excluded) continue;
          if (Files.isSymbolicLink(p))
            throw new Problem(
                "unsafe_path", "Arbeitskopie enthält einen Symlink.", "path", rel.toString());
          Path t = dest.resolve(rel);
          if (Files.isDirectory(p)) Files.createDirectories(t);
          else {
            Files.createDirectories(t.getParent());
            Files.copy(
                p, t, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
          }
        }
      }
    } catch (java.io.IOException e) {
      throw new Problem("workspace_failed", "Repository kann nicht kopiert werden.");
    }
  }

  public Map<String, Object> gradle(
      Map<String, Object> run,
      Path snapshot,
      String task,
      List<String> properties,
      String logName) {
    var args =
        new ArrayList<>(
            List.of(
                "bash",
                snapshot.resolve("shared/bin/gradlew-java17.sh").toString(),
                "--no-daemon",
                "--console=plain",
                "-I",
                snapshot.resolve("shared/gradle/init.gradle").toString(),
                task));
    args.addAll(properties);
    Path bundle = GretlRuntime.directory(w.settings);
    if (Files.isDirectory(bundle)) args.add("-PdatenportalOfflineJarsDir=" + bundle);
    String javaHome =
        Json.str(w.settings.values, "gretl_java_home", System.getenv("GRADLE_JAVA_HOME_17"));
    if (javaHome != null) {
      args.addFirst("GRADLE_JAVA_HOME_17=" + w.settings.path(javaHome));
      args.addFirst("env");
    }
    Path log = w.directory(run).resolve("validation").resolve(logName + ".log");
    var result =
        w.process.run(
            args, snapshot.resolve(Json.required(run, "organization")), w.settings.timeout, log);
    return Json.map(
        "valid",
        result.exitCode() == 0,
        "returncode",
        result.exitCode(),
        "diagnostics",
        result.exitCode() == 0
            ? List.of()
            : result
                .output()
                .lines()
                .skip(Math.max(0, result.output().lines().count() - 60))
                .toList(),
        "log",
        log.toString(),
        "log_sha256",
        Json.sha(log),
        "workspace",
        snapshot.toString(),
        "messages",
        result
            .output()
            .lines()
            .filter(
                l ->
                    l.contains("Error")
                        || l.contains("Warning")
                        || l.contains("FAILED")
                        || l.contains("Exception"))
            .limit(100)
            .toList());
  }

  public static String groovy(String value) {
    return "'"
        + value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n")
        + "'";
  }
}
