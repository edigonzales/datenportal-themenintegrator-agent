package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

public final class Validator {
  final Settings settings;
  final ProcessRunner process;

  public Validator(Settings s, ProcessRunner p) {
    settings = s;
    process = p;
  }

  public Map<String, Object> validate(Path xtf, Path offices, Path output) {
    var checks = new ArrayList<Object>();
    checks.add(check("offices", offices, null, output));
    if (xtf != null) checks.add(check("datasheet", xtf, offices, output));
    return Json.map(
        "valid",
        checks.stream().map(Json::obj).allMatch(c -> Json.bool(c, "valid", false)),
        "checks",
        checks,
        "xtf_sha256",
        xtf == null ? null : Json.sha(xtf),
        "offices_sha256",
        Json.sha(offices));
  }

  private Map<String, Object> check(String name, Path file, Path reference, Path out) {
    var args =
        new ArrayList<>(
            settings.strings("validator_command", List.of("java", "-jar", "ilivalidator.jar")));
    var dirs = new ArrayList<String>();
    for (String p : settings.strings("model_dirs", List.of()))
      dirs.add(p.startsWith("https://") ? p : settings.path(p).toString());
    dirs.add(settings.root.resolve("validation").toString());
    args.addAll(List.of("--modeldir", String.join(";", dirs)));
    if (reference != null)
      args.addAll(
          List.of(
              "--config",
              settings.root.resolve("validation/office-check.ini").toString(),
              "--allObjectsAccessible",
              "--refdata",
              reference.toString()));
    args.add(file.toString());
    Path log = out.resolve(name + ".log");
    var result = process.run(args, settings.root, settings.timeout, log);
    return Json.map(
        "name",
        name,
        "valid",
        result.exitCode() == 0,
        "returncode",
        result.exitCode(),
        "log",
        log.toString(),
        "log_sha256",
        Json.sha(log),
        "messages",
        result
            .output()
            .lines()
            .filter(l -> l.startsWith("Error:") || l.startsWith("Warning:"))
            .limit(100)
            .toList());
  }
}
