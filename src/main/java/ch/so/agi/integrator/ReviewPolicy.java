package ch.so.agi.integrator;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Content identity of review inputs; packaging and installation paths are not rules. */
final class ReviewPolicy {
  private static final Map<String, String> JARS = new ConcurrentHashMap<>();

  static String fingerprint(Settings s, Path home) {
    var files = Json.map();
    files.put(
        "policy",
        Files.isRegularFile(home.resolve("validation/review-policy.json"))
            ? Json.read(home.resolve("validation/review-policy.json"))
            : Json.map("revision", 1));
    Path rules = home.resolve("config/rules.json");
    if (Files.isRegularFile(rules)) files.put("config/rules.json", Json.read(rules));
    directory(files, home.resolve("validation"), "validation", false);
    directory(files, home.resolve("models"), "models", true);
    for (String name : List.of("datasheet-mcp", "interlis-mcp", "ilivalidator")) {
      Path jar = home.resolve("lib/" + name + ".jar");
      if (Files.isRegularFile(jar)) files.put("tool/" + name, jarContent(jar));
    }
    int index = 0;
    for (String directory : s.strings("model_dirs", List.of())) {
      String key = "model_dirs/" + index++;
      if (directory.startsWith("https://")) files.put(key, directory);
      else directory(files, s.path(directory), key, true);
    }
    var config = new LinkedHashMap<>(s.values);
    for (String key :
        List.of(
            "root",
            "topics_repo",
            "stack_repo",
            "state_dir",
            "model_dirs",
            "input_roots",
            "timeout_seconds",
            "stack_timeout_seconds",
            "compose_files",
            "stack_start_args",
            "datasheet",
            "interlis")) config.remove(key);
    if (config.containsKey("validator_command")) {
      var command = new ArrayList<String>();
      for (String part : s.strings("validator_command", List.of())) {
        Path p = s.path(part);
        if (Files.isRegularFile(p) && part.endsWith(".jar")) command.add("jar:" + jarContent(p));
        else if (Files.isRegularFile(p) && !Path.of(part).getFileName().toString().equals("java"))
          command.add("file:" + Json.sha(p));
        else if (Path.of(part).getFileName().toString().equals("java")) command.add("java21");
        else command.add(part);
      }
      config.put("validator_command", command);
    }
    return Json.digest(Json.map("format", 2, "settings", config, "dependencies", files));
  }

  private static void directory(
      Map<String, Object> out, Path directory, String key, boolean models) {
    if (!Files.isDirectory(directory)) return;
    try (var paths = Files.walk(directory)) {
      paths
          .filter(Files::isRegularFile)
          .sorted()
          .forEach(
              p -> {
                String rel = directory.relativize(p).toString().replace('\\', '/');
                if (rel.equals("review-policy.json") || (models && !rel.endsWith(".ili"))) return;
                out.put(key + "/" + rel, Json.sha(p));
              });
    } catch (java.io.IOException e) {
      throw new Problem(
          "review_policy_unavailable", "Prüfregeln nicht lesbar.", "path", directory.toString());
    }
  }

  static String jarContent(Path path) {
    String bytes = Json.sha(path);
    return JARS.computeIfAbsent(
        bytes,
        ignored -> {
          var entries = new TreeMap<String, Object>();
          try (var zip = new ZipFile(path.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
              var entry = iterator.nextElement();
              String name = entry.getName();
              if (entry.isDirectory()
                  || name.equals("META-INF/MANIFEST.MF")
                  || name.startsWith("META-INF/maven/")
                  || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA)")) continue;
              try (var input = zip.getInputStream(entry)) {
                var digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                int n;
                while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n);
                entries.put(name, HexFormat.of().formatHex(digest.digest()));
              }
            }
            return Json.digest(entries);
          } catch (Exception e) {
            // Custom non-JAR fixture/tool: retain its exact content identity.
            return bytes;
          }
        });
  }
}
