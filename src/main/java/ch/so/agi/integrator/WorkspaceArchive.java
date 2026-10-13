package ch.so.agi.integrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.compress.archivers.tar.*;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** Reproducible tar archive preserves the package's internal skill symlinks. */
public final class WorkspaceArchive {
  public static void main(String[] args) throws Exception {
    Path bundle = Path.of(args[1]).toAbsolutePath().normalize();
    if (args[0].equals("manifest") && args.length == 2) {
      var files = OperatorWorkspace.inventory(bundle);
      String version =
          Json.required(Json.read(bundle.resolve("workspace-package.json")), "version");
      Json.atomic(
          bundle.resolve(OperatorWorkspace.MANIFEST),
          Json.map(
              "schema_version",
              2,
              "kind",
              "operator",
              "version",
              version,
              "managed_files",
              files,
              "package_sha256",
              Json.digest(files)));
      return;
    }
    if (!args[0].equals("archive") || args.length != 3)
      throw new IllegalArgumentException("manifest <bundle> | archive <bundle> <archive>");
    archive(bundle, Path.of(args[2]).toAbsolutePath());
  }

  static void archive(Path bundle, Path archive) throws IOException {
    Files.createDirectories(archive.getParent());
    try (var gzip = new GzipCompressorOutputStream(Files.newOutputStream(archive));
        var tar = new TarArchiveOutputStream(gzip);
        var paths = Files.walk(bundle)) {
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      for (Path file : paths.sorted().toList()) {
        if (file.equals(bundle)) continue;
        String name = bundle.relativize(file).toString().replace('\\', '/');
        TarArchiveEntry entry;
        if (Files.isSymbolicLink(file)) {
          entry = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
          entry.setLinkName(Files.readSymbolicLink(file).toString().replace('\\', '/'));
          entry.setMode(0777);
        } else if (Files.isDirectory(file)) {
          entry = new TarArchiveEntry(name + "/");
          entry.setMode(0755);
        } else {
          entry = new TarArchiveEntry(name);
          entry.setSize(Files.size(file));
          entry.setMode(name.startsWith("bin/") ? 0755 : 0644);
        }
        entry.setModTime(0);
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setUserName("");
        entry.setGroupName("");
        tar.putArchiveEntry(entry);
        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.copy(file, tar);
        tar.closeArchiveEntry();
      }
    }
    Files.writeString(
        archive.resolveSibling(archive.getFileName() + ".sha256"),
        Json.sha(archive) + "  " + archive.getFileName() + "\n");
  }
}
