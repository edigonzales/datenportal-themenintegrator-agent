package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

final class JavaRuntime {
  static String java() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  static String classpath() {
    try {
      Path location =
          Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (Files.isRegularFile(location)) return location.toString();
      Path built =
          location.getParent().getParent().getParent().resolve("libs/datenportal-integrator.jar");
      if (Files.isRegularFile(built)) return built.toString();
    } catch (Exception ignored) {
    }
    return Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
        .map(p -> Path.of(p).toAbsolutePath().normalize().toString())
        .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
  }

  static void check() {
    if (Runtime.version().feature() != 21
        || javax.tools.ToolProvider.getSystemJavaCompiler() == null)
      throw new Problem("jdk_required", "JDK 21 mit Compiler erforderlich; JAVA_HOME setzen.");
  }

  static Path mcp(Settings s, String service) {
    return AgentResources.resolve(
        s.root, "lib/" + (service.equals("datasheet") ? "datasheet-mcp" : "interlis-mcp") + ".jar");
  }

  static Map<String, Object> checkTools(Settings s) {
    check();
    var files = Json.map();
    for (String name : List.of("datasheet", "interlis")) {
      Path jar = mcp(s, name);
      if (!Files.isRegularFile(jar))
        throw new Problem(
            "package_incomplete",
            "Veröffentlichtes MCP-JAR fehlt; vollständiges Betriebspaket installieren.",
            "path",
            jar.toString());
      files.put(name, Json.sha(jar));
    }
    Path validator = AgentResources.resolve(s.root, "lib/ilivalidator.jar");
    if (!Files.isRegularFile(validator))
      throw new Problem("package_incomplete", "ilivalidator fehlt im Betriebspaket.");
    files.put("ilivalidator", Json.sha(validator));
    if (AgentResources.installed()) {
      Path manifest = AgentResources.resolve(s.root, "distribution.json");
      if (!Files.isRegularFile(manifest))
        throw new Problem("package_incomplete", "Versionsmanifest fehlt im Betriebspaket.");
      var expected = Json.obj(Json.read(manifest).get("files"));
      for (String name :
          List.of("datenportal-integrator", "datasheet-mcp", "interlis-mcp", "ilivalidator")) {
        String relative = "lib/" + name + ".jar";
        if (!Objects.equals(
            expected.get(relative), Json.sha(AgentResources.resolve(s.root, relative))))
          throw new Problem(
              "package_changed",
              "Programmdatei unterscheidet sich vom Paketmanifest.",
              "file",
              relative);
      }
    }
    return files;
  }
}
