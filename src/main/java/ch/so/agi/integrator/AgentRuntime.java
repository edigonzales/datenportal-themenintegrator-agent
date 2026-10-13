package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

final class AgentRuntime {
  static Map<String, Object> check(Settings s) {
    JavaRuntime.check();
    for (Path path : List.of(s.root, s.topics))
      if (!Files.isDirectory(path))
        throw new Problem(
            "path_missing", "Konfiguriertes Verzeichnis fehlt.", "path", path.toString());
    if (!Files.isWritable(s.root))
      throw new Problem("workspace_read_only", "Arbeitsbereich ist nicht schreibbar.");
    return Json.map("mode", "java", "java_21", true, "paths_valid", true);
  }
}
