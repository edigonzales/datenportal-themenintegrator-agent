package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OperatorWorkspaceTest {
  @TempDir Path temp;

  Path bundle() {
    return Fixtures.ROOT.resolve("build/operator-workspace");
  }

  @Test
  void distributionIsSelfContainedAndHasNoImplementation() throws Exception {
    assertTrue(OperatorWorkspace.installed(bundle()));
    var inventory = OperatorWorkspace.inventory(bundle());
    assertTrue(inventory.containsKey("docs/anwenderhandbuch.md"));
    assertFalse(
        inventory.keySet().stream()
            .anyMatch(
                p ->
                    p.startsWith("src/")
                        || p.startsWith("tests/")
                        || p.endsWith(".java")
                        || p.contains("entwicklerhandbuch")
                        || p.equals("gradlew")));
    for (String skill :
        List.of(
            "datenportal-themenintegrator",
            "datenportal-validierungsmodell",
            "datenportal-organisation")) {
      Path link = bundle().resolve(".agents/skills/" + skill);
      assertTrue(Files.isSymbolicLink(link));
      assertTrue(link.toRealPath().startsWith(bundle().toRealPath()));
    }
    var link = Pattern.compile("\\]\\(([^)]+)\\)");
    for (String name : inventory.keySet()) {
      if (!name.endsWith(".md") || name.startsWith(".agents/")) continue;
      Path file = bundle().resolve(name);
      var matches = link.matcher(Files.readString(file));
      while (matches.find()) {
        String value = matches.group(1).split("#", 2)[0];
        if (value.isEmpty() || value.contains(":")) continue;
        Path referenced = file.getParent().resolve(value).normalize();
        assertTrue(referenced.startsWith(bundle()), file + " -> " + value);
        assertTrue(Files.exists(referenced), file + " -> " + value);
      }
    }
  }

  @Test
  void updatePreservesLocalConfigurationStateInputsAndUnknownFiles() throws Exception {
    Path target = temp.resolve("operator");
    OperatorWorkspace.install(bundle(), target, false);
    assertTrue(OperatorWorkspace.installed(target));
    Json.write(target.resolve("config/local.toml"), "private = true\n");
    Json.write(
        target.resolve(".datenportal-integrator/runs/existing/state.json"), "existing-state");
    Json.write(target.resolve("notes.txt"), "user-notes");
    OperatorWorkspace.install(bundle(), target, true);
    assertEquals("private = true\n", Json.contents(target.resolve("config/local.toml")));
    assertEquals(
        "existing-state",
        Json.contents(target.resolve(".datenportal-integrator/runs/existing/state.json")));
    assertEquals("user-notes", Json.contents(target.resolve("notes.txt")));
    assertTrue(Files.isExecutable(target.resolve("bin/datenportal-agent")));
  }

  @Test
  void changedManagedFileConflictsBeforeAnyFileIsReplaced() throws Exception {
    Path target = temp.resolve("operator");
    OperatorWorkspace.install(bundle(), target, false);
    Json.write(target.resolve("README.md"), "individual-change");
    String manifest = Json.contents(target.resolve(OperatorWorkspace.MANIFEST));
    var error =
        assertThrows(Problem.class, () -> OperatorWorkspace.install(bundle(), target, true));
    assertEquals("workspace_update_conflict", error.code);
    assertEquals(List.of("README.md"), error.details.get("paths"));
    assertEquals("individual-change", Json.contents(target.resolve("README.md")));
    assertEquals(manifest, Json.contents(target.resolve(OperatorWorkspace.MANIFEST)));
  }

  @Test
  void nonemptyInstallAndDirectorySymlinkAreRejected() throws Exception {
    Path target = temp.resolve("operator");
    Json.write(target.resolve("original.csv"), "original");
    assertEquals(
        "workspace_not_empty",
        assertThrows(Problem.class, () -> OperatorWorkspace.install(bundle(), target, false)).code);
    assertEquals("original", Json.contents(target.resolve("original.csv")));
    Path link = temp.resolve("linked");
    Files.createSymbolicLink(link, target);
    assertEquals(
        "unsafe_path",
        assertThrows(Problem.class, () -> OperatorWorkspace.install(bundle(), link, false)).code);
  }

  @Test
  void archiveKeepsSkillSymlinksManifestAndReproducibleBytes() throws Exception {
    Path first = temp.resolve("first.tar.gz"), second = temp.resolve("second.tar.gz");
    WorkspaceArchive.archive(bundle(), first);
    WorkspaceArchive.archive(bundle(), second);
    assertEquals(Json.sha(first), Json.sha(second));
    try (var gzip =
            new org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream(
                Files.newInputStream(first));
        var tar = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzip)) {
      boolean manifest = false;
      int links = 0;
      org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (entry.getName().equals(OperatorWorkspace.MANIFEST)) manifest = true;
        if (entry.getName().startsWith(".agents/skills/") && !entry.isDirectory()) {
          assertTrue(entry.isSymbolicLink(), entry.getName());
          assertTrue(entry.getLinkName().startsWith("../../skills/"));
          links++;
        }
      }
      assertTrue(manifest);
      assertEquals(3, links);
    }
  }
}
