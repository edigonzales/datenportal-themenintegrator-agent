package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Copies the existing stack's published flat GRETL bundle; never rebuilds or updates it. */
public final class GretlRuntime {
  public static Path directory(Settings s) {
    return s.path(
        Json.str(
            s.values, "gretl_offline_jars", ".datenportal-integrator/tools/gretl-runtime/jars"));
  }

  public static String fingerprint(Path jars) {
    if (!Files.isDirectory(jars))
      throw new Problem(
          "gretl_runtime_missing",
          "Vorhandenes Jenkins-GRETL-Bundle mit setup-gretl aufnehmen oder gretl_offline_jars konfigurieren.");
    try (var files = Files.list(jars)) {
      var inventory = new StringBuilder();
      var entries =
          files
              .filter(p -> p.getFileName().toString().endsWith(".jar"))
              .sorted(Comparator.comparing(p -> p.getFileName().toString()))
              .toList();
      if (entries.isEmpty())
        throw new Problem("gretl_runtime_missing", "GRETL-Bundle enthält keine JARs.");
      for (Path p : entries)
        inventory.append(p.getFileName()).append('=').append(Json.sha(p)).append('\n');
      return Json.hash(inventory.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new Problem("gretl_runtime_missing", "GRETL-Bundle nicht lesbar.");
    }
  }

  public static Object setup(Settings s, ProcessRunner process) {
    new Stack(s, process).ensure();
    String id =
        process.checked(
            List.of(
                "docker",
                "ps",
                "-q",
                "--filter",
                "label=com.docker.compose.project.working_dir=" + s.stack,
                "--filter",
                "label=com.docker.compose.service=jenkins"),
            s.stack,
            60);
    if (id.isBlank() || id.lines().count() != 1)
      throw new Problem(
          "ambiguous_stack", "Genau ein passender laufender Jenkins-Container erforderlich.");
    String path =
        process.checked(
            List.of("docker", "exec", id, "printenv", "DATENPORTAL_OFFLINE_JARS_DIR"), s.stack, 60);
    if (!path.startsWith("/") || path.contains("\n") || path.contains(".."))
      throw new Problem(
          "gretl_runtime_missing",
          "Bestehender Jenkins meldet kein verwendbares GRETL-Offline-Bundle.");
    Path destination = directory(s);
    try {
      Files.createDirectories(destination);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Lokales GRETL-Bundle nicht schreibbar.");
    }
    process.checked(
        List.of("docker", "cp", id + ":" + path + "/.", destination.toString()),
        s.stack,
        s.timeout);
    String fingerprint = fingerprint(destination);
    Json.atomic(
        destination.getParent().resolve("runtime.json"),
        Json.map(
            "captured",
            Json.now(),
            "stack",
            s.stack.toString(),
            "container",
            id,
            "source",
            path,
            "sha256",
            fingerprint));
    return Json.map("gretl_offline_jars", destination.toString(), "sha256", fingerprint);
  }
}
