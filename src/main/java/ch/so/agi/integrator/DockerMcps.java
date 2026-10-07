package ch.so.agi.integrator;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Immutable runtime selection. Never inspects container environments or builds sources. */
public final class DockerMcps {
  static final List<String> MODELS =
      List.of("SO_AGI_DataCatalog_Base_20260529.ili", "SO_AGI_DataCatalog_Datasheet_20260523.ili");
  final Settings settings;
  final ProcessRunner process;

  public DockerMcps(Settings settings, ProcessRunner process) {
    this.settings = settings;
    this.process = process;
  }

  Path home() {
    return settings.root.resolve(".datenportal-integrator/tools/mcps");
  }

  Path lockFile() {
    return home().resolve("runtime.json");
  }

  Map<String, Object> lock() {
    if (!Files.isRegularFile(lockFile()))
      throw new Problem("mcp_setup_required", "Docker-MCPs zuerst mit setup-mcps vorbereiten.");
    return Json.obj(Json.read(lockFile()).get("services"));
  }

  public Map<String, Object> inspect(String reference) {
    var r =
        process.run(
            List.of(
                "docker",
                "image",
                "inspect",
                "--format",
                "{\"image_id\":{{json .Id}},\"digests\":{{json .RepoDigests}},\"os\":{{json .Os}},\"architecture\":{{json .Architecture}}}",
                reference),
            settings.root,
            settings.timeout,
            null);
    if (r.exitCode() != 0)
      throw new Problem(
          "mcp_image_missing",
          "Festgelegtes MCP-Image fehlt lokal; setup-mcps ausführen.",
          "image",
          reference);
    return Json.read(r.output());
  }

  public Map<String, Object> setup(boolean update) {
    try {
      Files.createDirectories(home());
      try (var channel =
          FileChannel.open(
              home().resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        var guard = channel.tryLock();
        if (guard == null) throw new Problem("mcp_setup_locked", "MCP-Einrichtung läuft bereits.");
        try (guard) {
          var old = Files.isRegularFile(lockFile()) ? lock() : Json.map();
          var services = Json.map();
          for (String service : List.of("datasheet", "interlis")) {
            var config = settings.mcp(service);
            if (!config.containsKey("image")) continue;
            String reference = Json.required(config, "image");
            var previous = Json.obj(old.get(service));
            Map<String, Object> selected;
            if (!update && reference.equals(previous.get("reference"))) {
              try {
                selected = inspect(Json.required(previous, "image_id"));
              } catch (Problem missing) {
                if (!missing.code.equals("mcp_image_missing")) throw missing;
                pull(Json.required(previous, "digest"));
                selected = inspect(Json.required(previous, "image_id"));
              }
            } else {
              if (update) pull(reference);
              try {
                selected = inspect(reference);
              } catch (Problem missing) {
                if (!missing.code.equals("mcp_image_missing")) throw missing;
                pull(reference);
                selected = inspect(reference);
              }
            }
            var digests = Json.strings(selected.get("digests"));
            if (digests.isEmpty())
              throw new Problem(
                  "mcp_image_unpublished",
                  "MCP-Image hat keinen Registry-Digest.",
                  "image",
                  reference);
            selected.put("reference", reference);
            selected.put(
                "digest",
                digests.stream()
                    .filter(d -> d.startsWith(reference.split("[@:]", 2)[0] + "@"))
                    .findFirst()
                    .orElse(digests.getFirst()));
            selected.remove("digests");
            if (service.equals("datasheet")) {
              Path models =
                  home()
                      .resolve("models")
                      .resolve(Json.required(selected, "image_id").replace("sha256:", ""));
              if (!Files.isDirectory(models)
                  || !validModels(models, Json.obj(previous.get("models"))))
                extract(Json.required(selected, "image_id"), models);
              var hashes = Json.map();
              for (String name : MODELS) hashes.put(name, Json.sha(models.resolve(name)));
              selected.put("models", hashes);
              selected.put("model_directory", models.toString());
            }
            services.put(service, selected);
          }
          Json.atomic(lockFile(), Json.map("schema_version", 1, "services", services));
          return Json.map("runtime_file", lockFile().toString(), "services", services);
        }
      }
    } catch (Problem p) {
      throw p;
    } catch (Exception e) {
      throw new Problem(
          "mcp_setup_failed",
          "Docker-MCP-Einrichtung fehlgeschlagen.",
          "exception",
          e.getClass().getSimpleName());
    }
  }

  private void pull(String reference) {
    var r =
        process.run(
            List.of("docker", "pull", reference),
            settings.root,
            settings.timeout,
            home().resolve("pull.log"));
    if (r.exitCode() != 0)
      throw new Problem(
          "mcp_pull_failed",
          "MCP-Image konnte nicht bezogen werden; Registry-Anmeldung und Referenz prüfen.",
          "image",
          reference,
          "log",
          home().resolve("pull.log").toString());
  }

  private boolean validModels(Path directory, Map<String, Object> hashes) {
    return MODELS.stream()
        .allMatch(
            n ->
                Files.isRegularFile(directory.resolve(n))
                    && Json.sha(directory.resolve(n)).equals(hashes.get(n)));
  }

  private void extract(String image, Path directory) throws Exception {
    String name = name("models");
    Path staging = Files.createTempDirectory(home(), "models-");
    try {
      process.checked(
          List.of(
              "docker",
              "create",
              "--name",
              name,
              "--label",
              "datenportal.integrator.owner=" + owner(),
              image),
          settings.root,
          settings.timeout);
      process.checked(
          List.of("docker", "cp", name + ":/app/models/.", staging.toString()),
          settings.root,
          settings.timeout);
      for (String file : MODELS)
        if (!Files.isRegularFile(staging.resolve(file))
            || Files.isSymbolicLink(staging.resolve(file)))
          throw new Problem(
              "mcp_models_missing", "Originalmodell fehlt im Datenblatt-Image.", "model", file);
      try (var entries = Files.list(staging)) {
        if (entries.anyMatch(p -> !MODELS.contains(p.getFileName().toString())))
          throw new Problem("mcp_models_invalid", "Unerwartete Modellressourcen im Image.");
      }
      if (ch.interlis.ili2c.Main.compileIliFiles(
              new ArrayList<>(MODELS.stream().map(n -> staging.resolve(n).toString()).toList()),
              new ArrayList<>(List.of(staging.toString())),
              null)
          == null)
        throw new Problem("mcp_models_invalid", "Originalmodelle im Image kompilieren nicht.");
      Files.createDirectories(directory.getParent());
      if (Files.exists(directory)) delete(directory);
      Files.move(staging, directory, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      remove(name);
      delete(staging);
    }
  }

  public Map<String, Object> selected(String service, boolean inspectImage) {
    var config = settings.mcp(service);
    var selected = Json.obj(lock().get(service));
    if (!Json.str(selected, "image_id", "").matches("sha256:[a-f0-9]{64}"))
      throw new Problem(
          "mcp_setup_required",
          "Gespeicherte Image-ID fehlt oder ist ungültig; setup-mcps ausführen.");
    if (!Objects.equals(config.get("image"), selected.get("reference")))
      throw new Problem(
          "mcp_setup_required",
          "Geänderte Image-Referenz mit setup-mcps vorbereiten.",
          "service",
          service);
    if (inspectImage) {
      var actual = inspect(Json.required(selected, "image_id"));
      if (!Objects.equals(actual.get("os"), selected.get("os"))
          || !Objects.equals(actual.get("architecture"), selected.get("architecture")))
        throw new Problem(
            "mcp_image_changed", "MCP-Plattform stimmt nicht mit der Einrichtung überein.");
    }
    if (service.equals("datasheet")) {
      Path directory = Path.of(Json.required(selected, "model_directory"));
      if (!directory.normalize().startsWith(home().resolve("models").normalize())
          || !validModels(directory, Json.obj(selected.get("models"))))
        throw new Problem(
            "mcp_models_changed",
            "Originalmodellcache fehlt oder wurde verändert; setup-mcps ausführen.");
    }
    return selected;
  }

  String owner() {
    return Json.hash(settings.root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  String name(String service) {
    return "integrator-" + service + "-" + UUID.randomUUID().toString().replace("-", "");
  }

  List<String> launch(String name, String image) {
    return List.of(
        "docker",
        "run",
        "--rm",
        "-i",
        "--name",
        name,
        "--label",
        "datenportal.integrator.owner=" + owner(),
        "-e",
        "SPRING_PROFILES_ACTIVE=stdio",
        "--pull=never",
        image);
  }

  void register(String name) {
    Json.atomic(
        home().resolve("containers").resolve(name + ".json"),
        Json.map("name", name, "owner", owner(), "pid", ProcessHandle.current().pid()));
  }

  public void reap() {
    Path records = home().resolve("containers");
    if (!Files.isDirectory(records)) return;
    try (var files = Files.list(records)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
        var record = Json.read(file);
        long pid = ((Number) record.get("pid")).longValue();
        if (owner().equals(record.get("owner"))
            && ProcessHandle.of(pid).map(p -> !p.isAlive()).orElse(true))
          remove(Json.required(record, "name"));
      }
    } catch (java.io.IOException e) {
      throw new Problem("mcp_cleanup_failed", "Containerkennungen nicht lesbar.");
    }
  }

  void stopped(String name) {
    // Give EOF shutdown time before a scoped forced removal.
    long until = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < until) {
      var r =
          process.run(
              List.of("docker", "container", "inspect", "--format", "{{.State.Running}}", name),
              settings.root,
              10,
              null);
      if (r.exitCode() != 0 || !r.output().strip().equals("true")) break;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    remove(name);
  }

  void remove(String name) {
    try {
      var found =
          process.run(
              List.of(
                  "docker",
                  "container",
                  "inspect",
                  "--format",
                  "{{index .Config.Labels \"datenportal.integrator.owner\"}}",
                  name),
              settings.root,
              10,
              null);
      boolean removed = false;
      if (found.exitCode() == 0 && owner().equals(found.output().strip()))
        removed =
            process.run(List.of("docker", "rm", "-f", name), settings.root, 10, null).exitCode()
                == 0;
      else if (found.exitCode() != 0) {
        var absence =
            process.run(
                List.of("docker", "ps", "-aq", "--filter", "name=^/" + name + "$"),
                settings.root,
                10,
                null);
        removed = absence.exitCode() == 0 && absence.output().isBlank();
      }
      // A failed inspect can mean an unavailable daemon; keep the recovery record.
      if (removed) Files.deleteIfExists(home().resolve("containers").resolve(name + ".json"));
    } catch (Exception ignored) {
    }
  }

  static void delete(Path directory) throws java.io.IOException {
    if (!Files.exists(directory)) return;
    try (var paths = Files.walk(directory)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
    }
  }
}
