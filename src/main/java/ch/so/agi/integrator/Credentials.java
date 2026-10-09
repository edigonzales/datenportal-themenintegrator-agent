package ch.so.agi.integrator;

import com.sun.security.auth.module.UnixSystem;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.Function;

/** Explicit local credentials; never serialize Value or include it in workflow state. */
final class Credentials {
  static final class Value {
    final String username, token, source;

    Value(String username, String token, String source) {
      if (username == null
          || username.isBlank()
          || username.contains(":")
          || token == null
          || token.isBlank()
          || username.chars().anyMatch(Character::isISOControl)
          || token.chars().anyMatch(Character::isISOControl))
        throw new Problem(
            "credentials_invalid", "Benutzer und Token müssen vollständig und einzeilig sein.");
      this.username = username;
      this.token = token;
      this.source = source;
    }

    Map<String, String> bootstrapEnvironment() {
      return Map.of(
          "DATENPORTAL_BOOTSTRAP_USER", username, "DATENPORTAL_BOOTSTRAP_PASSWORD", token);
    }

    @Override
    public String toString() {
      return "[credentials:" + source + "]";
    }
  }

  final Settings settings;
  final Function<String, String> environment;
  final Path directory, file;

  Credentials(Settings settings) {
    this(settings, System::getenv);
  }

  Credentials(Settings settings, Function<String, String> environment) {
    this.settings = settings;
    this.environment = environment;
    directory = settings.root.resolve(".datenportal-integrator/credentials");
    file = directory.resolve("local.json");
  }

  static String base(Map<String, Object> env) {
    return Settings.url(Json.required(env, "jenkins_url"))
        .normalize()
        .toString()
        .replaceAll("/+$", "");
  }

  Value fromEnvironment(Map<String, Object> env) {
    String userKey = Json.str(env, "username_env", "DATENPORTAL_JENKINS_USER");
    String tokenKey = Json.str(env, "token_env", "DATENPORTAL_JENKINS_TOKEN");
    String user = environment.apply(userKey), token = environment.apply(tokenKey);
    boolean u = user != null && !user.isBlank(), t = token != null && !token.isBlank();
    if (u != t)
      throw new Problem(
          "credentials_partial",
          "Zugangsdaten sind nur teilweise gesetzt; kein Mischen mit dem Store.",
          "variables",
          List.of(userKey, tokenKey));
    return u ? new Value(user, token, "environment") : null;
  }

  Value resolve(String profile, Map<String, Object> env) {
    Value explicit = fromEnvironment(env);
    if (explicit != null) return explicit;
    if (Json.str(env, "kind", "").equals("local")) {
      var saved = Json.obj(Json.obj(read(false).get("environments")).get(profile));
      if (!saved.isEmpty()) {
        if (!Objects.equals(saved.get("jenkins_url"), base(env)))
          throw new Problem(
              "credentials_target_changed",
              "Jenkins-Adresse geändert; lokale Zugangsdaten ausdrücklich neu hinterlegen.",
              "environment",
              profile);
        return new Value(
            Json.required(saved, "username"), Json.required(saved, "token"), "local_store");
      }
    }
    throw new Problem(
        "credentials_missing",
        "Jenkins-Zugangsdaten fehlen; lokale Zugangsdaten mit credentials set hinterlegen oder die benannten Variablen setzen.",
        "environment",
        profile,
        "variables",
        List.of(
            Json.str(env, "username_env", "DATENPORTAL_JENKINS_USER"),
            Json.str(env, "token_env", "DATENPORTAL_JENKINS_TOKEN")));
  }

  Map<String, Object> status(String profile) {
    try {
      var value = resolve(profile, settings.environment(profile));
      return Json.map(
          "environment", profile, "credentials_present", true, "credentials_source", value.source);
    } catch (Problem p) {
      var result =
          Json.map(
              "environment", profile, "credentials_present", false, "credentials_source", "none");
      result.putAll(p.result());
      return result;
    }
  }

  void local(Map<String, Object> env) {
    if (!Json.str(env, "kind", "").equals("local"))
      throw new Problem(
          "local_only", "Der Credential-Store ist ausschliesslich für lokale Profile vorgesehen.");
  }

  Map<String, Object> save(String profile, String username, String token) {
    var env = settings.environment(profile);
    local(env);
    var value = new Value(username, token, "local_store");
    secureDirectory(true);
    Path lock = directory.resolve("store.lock");
    createPrivateFile(lock);
    try (var guard = RuntimeGuard.acquire(lock, 10)) {
      createPrivateFile(lock);
      var data = read(true);
      Json.obj(data.get("environments"))
          .put(
              profile,
              Json.map("jenkins_url", base(env), "username", value.username, "token", value.token));
      write(data);
    }
    return Json.map("environment", profile, "stored", true, "credentials_source", "local_store");
  }

  Map<String, Object> remove(String profile) {
    local(settings.environment(profile));
    if (!secureDirectory(false)) return Json.map("environment", profile, "removed", false);
    Path lock = directory.resolve("store.lock");
    createPrivateFile(lock);
    boolean removed;
    try (var guard = RuntimeGuard.acquire(lock, 10)) {
      createPrivateFile(lock);
      var data = read(false);
      removed = Json.obj(data.get("environments")).remove(profile) != null;
      if (removed) write(data);
    }
    return Json.map("environment", profile, "removed", removed);
  }

  Map<String, Object> command(List<String> args) throws java.io.IOException {
    if (args.size() < 2)
      throw new Problem("invalid_arguments", "credentials set|status|remove PROFIL");
    String operation = args.get(0), profile = args.get(1);
    Store.identifier(profile);
    if (operation.equals("status") && args.size() == 2) return status(profile);
    if (operation.equals("remove") && args.size() == 2) return remove(profile);
    if (operation.equals("set")) {
      if (args.size() == 3 && args.get(2).equals("--from-env")) {
        var env = settings.environment(profile);
        local(env);
        var value = fromEnvironment(env);
        if (value == null)
          throw new Problem("credentials_missing", "Die konfigurierten Umgebungsvariablen fehlen.");
        return save(profile, value.username, value.token);
      }
      if (args.size() == 5
          && args.get(2).equals("--username")
          && args.get(4).equals("--token-stdin")) {
        byte[] bytes = System.in.readNBytes(65537);
        if (bytes.length > 65536) throw new Problem("credentials_invalid", "Token zu lang.");
        String token =
            new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\\r?\\n$", "");
        return save(profile, args.get(3), token);
      }
    }
    throw new Problem(
        "invalid_arguments",
        "credentials set PROFIL --username USER --token-stdin | --from-env; status|remove PROFIL");
  }

  private boolean secureDirectory(boolean create) {
    try {
      Path parent = directory.getParent();
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) checkOwnedDirectory(parent);
      else if (create) {
        try {
          Files.createDirectory(parent);
        } catch (FileAlreadyExistsException ignored) {
        }
        checkOwnedDirectory(parent);
      } else return false;
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
        if (!create) return false;
        try {
          Files.createDirectory(
              directory,
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (FileAlreadyExistsException ignored) {
        }
      }
      check(directory, true);
      return true;
    } catch (Problem p) {
      throw p;
    } catch (Exception e) {
      throw new Problem(
          "credential_store_unavailable", "Geschütztes Credential-Verzeichnis nicht verfügbar.");
    }
  }

  private static void checkOwnedDirectory(Path p) throws java.io.IOException {
    if (Files.isSymbolicLink(p)
        || !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
        || ((Number) Files.getAttribute(p, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue()
            != new UnixSystem().getUid())
      throw new Problem(
          "credential_store_permissions",
          "Credential-Pfad muss dem aufrufenden Benutzer gehören und darf kein Symlink sein.");
  }

  private static void check(Path p, boolean dir) throws java.io.IOException {
    if (dir) checkOwnedDirectory(p);
    else if (Files.isSymbolicLink(p)
        || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
        || ((Number) Files.getAttribute(p, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue()
            != new UnixSystem().getUid())
      throw new Problem(
          "credential_store_permissions",
          "Credential-Datei muss dem aufrufenden Benutzer gehören und darf kein Symlink sein.");
    if (!Files.getPosixFilePermissions(p, LinkOption.NOFOLLOW_LINKS)
        .equals(PosixFilePermissions.fromString(dir ? "rwx------" : "rw-------")))
      throw new Problem(
          "credential_store_permissions",
          "Credential-Store benötigt Verzeichnisrechte 0700 und Dateirechte 0600.");
  }

  private static void createPrivateFile(Path p) {
    try {
      try {
        Files.createFile(
            p, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      } catch (FileAlreadyExistsException ignored) {
      }
      check(p, false);
    } catch (Problem problem) {
      throw problem;
    } catch (Exception e) {
      throw new Problem("credential_store_unavailable", "Credential-Sperre nicht verfügbar.");
    }
  }

  private Map<String, Object> read(boolean create) {
    if (!secureDirectory(create) || !Files.exists(file, LinkOption.NOFOLLOW_LINKS))
      return Json.map("version", 1, "environments", Json.map());
    try {
      check(file, false);
      var data = Json.read(file);
      if (Json.number(data, "version", 0) != 1 || !(data.get("environments") instanceof Map))
        throw new IllegalArgumentException();
      return data;
    } catch (Problem p) {
      if (p.code.equals("credential_store_permissions")) throw p;
      throw new Problem(
          "credential_store_invalid",
          "Credential-Store ist ungültig; keine Werte werden ausgegeben.");
    } catch (Exception e) {
      throw new Problem("credential_store_invalid", "Credential-Store ist nicht sicher lesbar.");
    }
  }

  private void write(Map<String, Object> data) {
    Path tmp = null;
    try {
      tmp =
          Files.createTempFile(
              directory,
              "credentials-",
              ".tmp",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      try (var channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
        var bytes =
            java.nio.ByteBuffer.wrap(
                Json.pretty(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) channel.write(bytes);
        channel.force(true);
      }
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new Problem(
          "credential_store_write_failed",
          "Credential-Store konnte nicht atomar gespeichert werden.");
    } finally {
      if (tmp != null)
        try {
          Files.deleteIfExists(tmp);
        } catch (Exception ignored) {
        }
    }
  }
}
