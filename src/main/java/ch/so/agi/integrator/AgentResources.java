package ch.so.agi.integrator;

import java.nio.file.Path;

/** Immutable installation resources are separate from the writable workspace. */
final class AgentResources {
  static Path resolve(Path workspace, String relative) {
    String home = System.getenv("DATENPORTAL_AGENT_HOME");
    return (home == null || home.isBlank() ? workspace : Path.of(home)).resolve(relative);
  }

  static Path jar(Path workspace) {
    return resolve(workspace, "build/libs/datenportal-integrator.jar");
  }

  static boolean installed() {
    String home = System.getenv("DATENPORTAL_AGENT_HOME");
    return home != null && !home.isBlank();
  }
}
