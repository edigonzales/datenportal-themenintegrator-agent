package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {
  public record Result(int exitCode, String output) {}

  public Result run(List<String> args, Path cwd, int seconds, Path log) {
    boolean temporary = log == null;
    try {
      if (log == null) log = Files.createTempFile("integrator-process-", ".log");
      else Files.createDirectories(log.getParent());
      Process p =
          new ProcessBuilder(args)
              .directory(cwd.toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
        p.descendants().forEach(ProcessHandle::destroy);
        p.destroyForcibly();
        throw new Problem(
            "command_timeout", "Zeitlimit des externen Werkzeugs erreicht.", "log", log.toString());
      }
      return new Result(p.exitValue(), Files.readString(log));
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(
          "command_unavailable", "Externes Werkzeug nicht ausführbar.", "tool", args.getFirst());
    } finally {
      if (temporary && log != null)
        try {
          Files.deleteIfExists(log);
        } catch (java.io.IOException ignored) {
        }
    }
  }

  public String checked(List<String> args, Path cwd, int timeout) {
    var r = run(args, cwd, timeout, null);
    if (r.exitCode() != 0)
      throw new Problem(
          "command_failed",
          "Externes Werkzeug meldet einen Fehler.",
          "tool",
          args.getFirst(),
          "returncode",
          r.exitCode());
    return r.output().strip();
  }
}
