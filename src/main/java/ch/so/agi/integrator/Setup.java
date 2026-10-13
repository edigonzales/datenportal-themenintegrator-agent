package ch.so.agi.integrator;

import java.util.*;

public final class Setup {
  final Settings s;

  public Setup(Settings s) {
    this.s = s;
  }

  public Object run(String command, List<String> args) {
    return switch (command) {
      case "harness-config" -> new Harness(s).proposal();
      case "codex" -> codex(args);
      default ->
          throw new Problem(
              "command_retired",
              "Java-Betriebspaket installieren und init verwenden; separate Runtime-Installation entfällt.");
    };
  }

  Object codex(List<String> extra) {
    var command = new ArrayList<>(List.of("codex"));
    command.addAll(new Harness(s).codexArguments());
    command.addAll(extra);
    try {
      return Json.map("exit_code", new ProcessBuilder(command).inheritIO().start().waitFor());
    } catch (Exception e) {
      throw new Problem("codex_start_failed", "Codex konnte nicht gestartet werden.");
    }
  }

  static List<String> concat(List<String> a, List<String> b) {
    var r = new ArrayList<>(a);
    r.addAll(b);
    return r;
  }
}
