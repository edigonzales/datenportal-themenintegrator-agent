package ch.so.agi.integrator;

import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

public final class Converters {
  final Workflow w;

  public Converters(Workflow w) {
    this.w = w;
  }

  public Object transform(Map<String, Object> r, Map<String, Object> args) {
    String policy = Json.required(args, "policy"),
        instructions = Json.required(args, "instructions");
    if (!Set.of("supplier", "recurring").contains(policy))
      throw new Problem(
          "transform_policy", "supplier/recurring und Lieferantenbeschreibung erforderlich.");
    Path root = w.settings.root.resolve("topics"),
        recipe = Json.inside(root, Json.required(args, "converter")),
        tests = Json.inside(root, Json.required(args, "tests")),
        topic = root.resolve(w.topic(r));
    if (!recipe.startsWith(topic) || !tests.startsWith(topic))
      throw new Problem(
          "converter_scope", "Konverter und Tests müssen zum aktuellen Thema gehören.");
    if (recipe.toString().endsWith(".py"))
      throw new Problem(
          "converter_migration_required",
          "Python-Rezept zuerst nach Java portieren; keine automatische Ausführung.");
    var spec = Json.read(recipe);
    String className = Json.required(spec, "class_name");
    if (!className.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*"))
      throw new Problem("converter_invalid", "Java-Klassenname erforderlich.");
    var sources = new ArrayList<Path>();
    for (String source : Json.strings(spec.get("sources"))) sources.add(Json.inside(topic, source));
    if (sources.isEmpty())
      throw new Problem("converter_invalid", "Java-Quellen im Konverterrezept fehlen.");
    if (!Files.isDirectory(tests))
      throw new Problem("converter_tests_missing", "JUnit-Testverzeichnis fehlt.");
    try (var paths = Files.walk(tests)) {
      paths.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
    } catch (java.io.IOException e) {
      throw new Problem("converter_tests_missing", "JUnit-Tests nicht lesbar.");
    }
    Path
        build =
            w.directory(r).resolve("converter").resolve(Long.toString(System.currentTimeMillis())),
        classes = build.resolve("classes");
    try {
      Files.createDirectories(classes);
    } catch (java.io.IOException e) {
      throw new Problem("file_write_failed", "Konverter-Arbeitsverzeichnis fehlt.");
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null)
      throw new Problem("jdk_required", "Konverter benötigen JDK 25 mit javac.");
    String cp = System.getProperty("java.class.path");
    var output = new StringWriter();
    try (var manager =
        compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
      boolean ok =
          compiler
              .getTask(
                  output,
                  manager,
                  null,
                  List.of(
                      "--release",
                      "25",
                      "-encoding",
                      "UTF-8",
                      "-classpath",
                      cp,
                      "-d",
                      classes.toString()),
                  null,
                  manager.getJavaFileObjectsFromPaths(sources))
              .call();
      Json.write(build.resolve("compile.log"), output.toString());
      if (!ok)
        throw new Problem(
            "converter_compile_failed",
            "Java-Konverter/JUnit-Tests kompilieren nicht.",
            "log",
            build.resolve("compile.log").toString());
    } catch (java.io.IOException e) {
      throw new Problem("converter_compile_failed", "Java-Konverter kann nicht kompiliert werden.");
    }
    String java = Path.of(System.getProperty("java.home"), "bin/java").toString(),
        classpath = classes + System.getProperty("path.separator") + cp;
    Path log = build.resolve("tests.log");
    var result =
        w.process.run(
            List.of(
                java,
                "-cp",
                classpath,
                "org.junit.platform.console.ConsoleLauncher",
                "execute",
                "--scan-classpath=" + classes,
                "--fail-if-no-tests",
                "--disable-banner"),
            w.settings.root,
            w.settings.timeout,
            log);
    if (result.exitCode() != 0)
      throw new Problem(
          "converter_tests_failed",
          "Konvertertests fehlgeschlagen; Original bleibt erhalten.",
          "log",
          log.toString());
    Path input = w.store.file(r, "original_data");
    if (input == null) input = w.requiredFile(r, "data");
    String before = Json.sha(input);
    Path workingInput = build.resolve("input.csv");
    try {
      Files.copy(input, workingInput);
    } catch (java.io.IOException e) {
      throw new Problem(
          "file_write_failed", "Arbeitskopie für Konverter kann nicht erstellt werden.");
    }
    Path converted = build.resolve("converted.csv");
    result =
        w.process.run(
            List.of(
                java,
                "-cp",
                classpath,
                "ch.so.agi.integrator.ConverterMain",
                className,
                workingInput.toString(),
                converted.toString()),
            w.settings.root,
            w.settings.timeout,
            build.resolve("converter.log"));
    if (!Json.sha(input).equals(before) || !Json.sha(workingInput).equals(before))
      throw new Problem(
          "original_modified", "Konverter hat die unveränderliche Eingabe verändert.");
    if (result.exitCode() != 0 || !Files.isRegularFile(converted))
      throw new Problem("converter_failed", "Konverter hat keine erfolgreiche Ausgabe erzeugt.");
    var technical =
        Csv.inspect(converted, Json.read(w.settings.root.resolve("config/rules.json")), null);
    if (!Json.bool(technical, "valid", false))
      throw new Problem(
          "converter_output_invalid", "Konverterausgabe verletzt CSV-Regeln.", "report", technical);
    var hashes = Json.map();
    sources.forEach(p -> hashes.put(p.toString(), Json.sha(p)));
    var transform =
        Json.map(
            "converter",
            args.get("converter"),
            "tests",
            args.get("tests"),
            "policy",
            policy,
            "instructions",
            instructions,
            "recipe_sha256",
            Json.sha(recipe),
            "source_hashes",
            hashes,
            "input_sha256",
            before,
            "output_sha256",
            Json.sha(converted),
            "tests_log",
            log.toString());
    r.put("transform", transform);
    r.remove("converter_migration_required");
    w.store.attach(r, converted, "data");
    Json.atomic(
        topic.resolve("integration.json"),
        Json.map(
            "language",
            "java",
            "converter",
            args.get("converter"),
            "tests",
            args.get("tests"),
            "policy",
            policy,
            "instructions",
            instructions));
    Path supplier = w.directory(r).resolve("supplier-instructions.md");
    Json.write(supplier, instructions + "\n");
    return Json.map(
        "transform",
        transform,
        "supplier_instructions",
        supplier.toString(),
        "tests_log",
        log.toString(),
        "review_path",
        Reports.render(
            w.settings.root,
            w.directory(r).resolve("transform-review.html"),
            "CSV-Transformation prüfen",
            Json.map(
                "before",
                Csv.inspect(input, Json.read(w.settings.root.resolve("config/rules.json")), null),
                "after",
                technical,
                "transform",
                transform)));
  }
}
