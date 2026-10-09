package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class ProcessRunner {
  public record Result(int exitCode, String output) {}

  public Result run(List<String> args, Path cwd, int seconds, Path log) {
    return run(args, cwd, seconds, log, Map.of());
  }

  /** Sensitive child environment is never added to command arguments or raw log files. */
  public Result run(
      List<String> args, Path cwd, int seconds, Path log, Map<String, String> environment) {
    boolean temporary = log == null;
    try {
      if (log == null) log = Files.createTempFile("integrator-process-", ".log");
      else Files.createDirectories(log.getParent());
      var builder = new ProcessBuilder(args).directory(cwd.toFile()).redirectErrorStream(true);
      builder.environment().putAll(environment);
      if (environment.isEmpty()) builder.redirectOutput(log.toFile());
      Process p = builder.start();
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<?> collector = null;
        if (!environment.isEmpty()) {
          Path target = log;
          collector =
              pool.submit(
                  () -> {
                    try (var reader = p.inputReader();
                        var writer = Files.newBufferedWriter(target)) {
                      String line;
                      while ((line = reader.readLine()) != null) {
                        writer.write(redact(line, environment));
                        writer.newLine();
                        writer.flush();
                      }
                    } catch (java.io.IOException e) {
                      throw new IllegalStateException("Redacted process log unavailable");
                    }
                  });
        }
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
          p.descendants().forEach(ProcessHandle::destroy);
          p.destroyForcibly();
          p.getInputStream().close();
          throw new Problem(
              "command_timeout",
              "Zeitlimit des externen Werkzeugs erreicht.",
              "log",
              log.toString());
        }
        if (collector != null) collector.get(10, TimeUnit.SECONDS);
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

  static String redact(String output, Map<String, String> environment) {
    var secrets = new ArrayList<>(environment.values());
    if (environment.containsKey("DATENPORTAL_BOOTSTRAP_USER")
        && environment.containsKey("DATENPORTAL_BOOTSTRAP_PASSWORD"))
      secrets.add(
          Base64.getEncoder()
              .encodeToString(
                  (environment.get("DATENPORTAL_BOOTSTRAP_USER")
                          + ":"
                          + environment.get("DATENPORTAL_BOOTSTRAP_PASSWORD"))
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    for (String secret :
        secrets.stream()
            .filter(v -> v != null && !v.isEmpty())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList()) output = output.replace(secret, "[redacted]");
    return output;
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
