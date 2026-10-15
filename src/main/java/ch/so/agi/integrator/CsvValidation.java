package ch.so.agi.integrator;

import java.nio.file.*;
import java.util.*;

/** Independent early CSV validation. Jenkins performs its own GRETL validation. */
public class CsvValidation {
  private final Settings settings;
  private final ProcessRunner process;

  public CsvValidation(Settings settings, ProcessRunner process) {
    this.settings = settings;
    this.process = process;
  }

  public Map<String, Object> validate(Path data, Path model, String modelName, Path output) {
    output = output.resolve(UUID.randomUUID().toString());
    data = CheckStates.snapshot(output.resolve("data"), data);
    model = CheckStates.snapshot(output.resolve("model"), model);
    Path log = output.resolve("csv.log");
    var dirs = new ArrayList<String>();
    dirs.add(model.toAbsolutePath().getParent().toString());
    dirs.addAll(settings.modelDirectories());
    var result =
        process.run(
            List.of(
                JavaRuntime.java(),
                "-cp",
                JavaRuntime.classpath(),
                CsvValidationMain.class.getName(),
                data.toAbsolutePath().toString(),
                modelName,
                String.join(";", dirs)),
            settings.root,
            settings.timeout,
            log);
    boolean executed = result.output().contains("INTEGRATOR_CSV_VALIDATED=");
    return Json.map(
        "valid",
        result.exitCode() == 0 && result.output().contains("INTEGRATOR_CSV_VALIDATED=true"),
        "executed",
        executed,
        "returncode",
        result.exitCode(),
        "log",
        log.toString(),
        "log_sha256",
        Json.sha(log),
        "data_sha256",
        Json.sha(data),
        "model_sha256",
        Json.sha(model));
  }
}
