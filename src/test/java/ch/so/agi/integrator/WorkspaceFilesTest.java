package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFilesTest {
  @TempDir Path temp;
  Path root;
  WorkspaceFiles files;

  @BeforeEach
  void setup() throws Exception {
    root = temp.resolve("operator");
    Json.write(
        root.resolve("config/local.toml"),
        "root=\"..\"\ntopics_repo=\"../repo\"\nstack_repo=\"../stack\"\n[datasheet]\ntransport=\"stdio\"\n");
    files = new WorkspaceFiles(new Settings(root.resolve("config/local.toml")));
  }

  @Test
  void skillAliasesCopiesAndInternalLinksShareReadBoundary() throws Exception {
    String reference = "model/references/types.md";
    for (String base : List.of("skills", ".agents/skills")) {
      Path p = root.resolve(base).resolve(reference);
      Json.write(p, "reference");
      assertEquals("reference", Json.obj(files.read(base + "/" + reference)).get("content"));
      assertEquals("reference", Json.obj(files.read(p.toString())).get("content"));
      assertEquals(
          1, Json.list(Json.obj(files.list(base + "/model/references")).get("entries")).size());
    }
    Path alias = root.resolve(".agents/skills/alias");
    Files.createSymbolicLink(alias, root.resolve("skills/model"));
    assertEquals(
        "reference",
        Json.obj(files.read(alias.resolve("references/types.md").toString())).get("content"));
    assertTrue(Json.text(files.list(".agents/skills")).contains("skills/model"));
    Path outside = temp.resolve("private");
    Json.write(outside.resolve("secret.md"), "private");
    Files.createSymbolicLink(root.resolve(".agents/skills/escape"), outside);
    assertThrows(Problem.class, () -> files.read(".agents/skills/escape/secret.md"));
    assertThrows(Problem.class, () -> files.list(".agents/skills/escape"));
    assertFalse(Json.text(files.list(".agents/skills")).contains(outside.toRealPath().toString()));
    assertThrows(
        Problem.class,
        () -> files.write(Json.map("path", ".agents/skills/new.md", "content", "no")));
  }

  @Test
  void externalInputsRequireExplicitRootsAndRejectSymlinkEscapes() throws Exception {
    Path workspace = temp.resolve("business/operator");
    Path external = temp.resolve("external");
    Path original = external.resolve("lieferung.csv");
    Json.write(original, "Id;Wert\r\n0012;0\r\n");
    Path config = workspace.resolve("config/local.toml");
    String base = "root=\"..\"\ntopics_repo=\"../repo\"\n";
    String services = "[datasheet]\ntransport=\"stdio\"\n";
    Json.write(config, base + services);
    var restricted = new WorkspaceFiles(new Settings(config));
    var denied =
        assertThrows(Problem.class, () -> restricted.importAttachment("agi", original.toString()));
    assertEquals("input_path_not_allowed", denied.code);
    assertEquals(original.toString(), denied.details.get("path"));
    assertTrue(denied.getMessage().contains("input_roots"));
    assertFalse(denied.getMessage().contains("mount"));
    Path alias = workspace.resolve("alias.csv");
    Files.createSymbolicLink(alias, original);
    assertEquals(
        "input_path_not_allowed",
        assertThrows(Problem.class, () -> restricted.importAttachment("agi", alias.toString()))
            .code);
    for (String inputRoot : List.of(external.toString(), "../../external")) {
      Json.write(config, base + "input_roots=[\"" + inputRoot + "\"]\n" + services);
      var imported =
          new WorkspaceFiles(new Settings(config)).importAttachment("agi", original.toString());
      assertEquals(true, imported.get("verified"));
      assertEquals(imported.get("source_sha256_before"), imported.get("source_sha256_after"));
      assertEquals(imported.get("source_sha256_before"), imported.get("copy_sha256"));
      assertEquals("Id;Wert\r\n0012;0\r\n", Files.readString(original));
      assertArrayEquals(
          Files.readAllBytes(original),
          Files.readAllBytes(Path.of(imported.get("path").toString())));
    }
    var missing =
        assertThrows(
            Problem.class,
            () -> restricted.importAttachment("agi", external.resolve("missing.csv").toString()));
    assertEquals("input_path_unavailable", missing.code);
    assertFalse(missing.getMessage().contains("mount"));
  }

  @Test
  void importsOriginalBytesIntoSeparateDirectoriesWithThreeChecksums() throws Exception {
    Path original = temp.resolve("lieferung.CSV");
    byte[] bytes = "Id;Wert\r\n0012;0\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(original, bytes);
    var first = files.importAttachment("agi", original.toString());
    var second = files.importAttachment("agi", original.toString());
    assertEquals(true, first.get("verified"));
    assertEquals(Json.hash(bytes), first.get("source_sha256_before"));
    assertEquals(first.get("source_sha256_before"), first.get("source_sha256_after"));
    assertEquals(first.get("source_sha256_before"), first.get("copy_sha256"));
    assertNotEquals(first.get("path"), second.get("path"));
    assertArrayEquals(bytes, Files.readAllBytes(original));
    assertArrayEquals(bytes, Files.readAllBytes(Path.of(first.get("path").toString())));
    assertEquals("lieferung.CSV", Path.of(first.get("path").toString()).getFileName().toString());
  }

  @Test
  void rejectsBadNamesSourceAliasesAndOriginalReads() throws Exception {
    Path invalid = temp.resolve("bad-name.csv");
    Json.write(invalid, "original");
    assertEquals(
        "invalid_csv_filename",
        assertThrows(Problem.class, () -> files.importAttachment("agi", invalid.toString())).code);
    Path source = temp.resolve("Workflow.java");
    Json.write(source, "implementation");
    Path link = temp.resolve("good.csv");
    Files.createSymbolicLink(link, source);
    assertEquals(
        "workspace_access_denied",
        assertThrows(Problem.class, () -> files.importAttachment("agi", link.toString())).code);
    assertEquals(
        "workspace_access_denied",
        assertThrows(Problem.class, () -> files.read(invalid.toString())).code);
    assertEquals("original", Json.contents(invalid));
    Path original = temp.resolve("data.csv"), outside = temp.resolve("outside-input");
    Json.write(original, "Id\n0012\n");
    Files.createDirectories(outside);
    Path inputs = root.resolve(".datenportal-integrator/input");
    Files.createDirectories(inputs);
    Files.createSymbolicLink(inputs.resolve("agi"), outside);
    assertEquals(
        "unsafe_path",
        assertThrows(Problem.class, () -> files.importAttachment("agi", original.toString())).code);
    try (var entries = Files.list(outside)) {
      assertEquals(0, entries.count());
    }
  }

  @Test
  void readsReferencesButBlocksSourcesCredentialsStateAndSymlinkEscapes() throws Exception {
    Json.write(root.resolve("skills/model/SKILL.md"), "public-contract");
    assertEquals("public-contract", Json.obj(files.read("skills/model/SKILL.md")).get("content"));
    for (String path :
        List.of(
            "src/main/Workflow.java",
            "config/local.toml",
            ".datenportal-integrator/credentials/local.json",
            ".datenportal-integrator/runs/123/state.json"))
      assertEquals(
          "workspace_access_denied", assertThrows(Problem.class, () -> files.read(path)).code);
    Path secret = temp.resolve("secret.md");
    Json.write(secret, "private");
    Files.createSymbolicLink(root.resolve("skills/model/escape.md"), secret);
    assertEquals(
        "workspace_access_denied",
        assertThrows(Problem.class, () -> files.read("skills/model/escape.md")).code);
    Files.createSymbolicLink(root.resolve("AGENTS.md"), secret);
    assertEquals(
        "workspace_access_denied", assertThrows(Problem.class, () -> files.read("AGENTS.md")).code);
    Path directory = temp.resolve("outside");
    Json.write(directory.resolve("private.java"), "private");
    Files.createDirectories(root.resolve("topics/agi/ch.so.test"));
    Files.createSymbolicLink(root.resolve("topics/agi/ch.so.test/escape"), directory);
    assertEquals(
        "workspace_access_denied",
        assertThrows(
                Problem.class,
                () ->
                    files.write(
                        Json.map(
                            "path",
                            "topics/agi/ch.so.test/escape/private.java",
                            "content",
                            "change")))
            .code);
    assertEquals("private", Json.contents(directory.resolve("private.java")));
  }

  @Test
  void changedSourceIsRejectedAndCopyIsNotReturned() throws Exception {
    Path original = temp.resolve("changing.csv");
    Json.write(original, "before\n");
    var changing =
        new WorkspaceFiles(
            files.s,
            (source, destination) -> {
              Files.copy(source, destination);
              Files.writeString(
                  source,
                  "after\n"); // Simulated concurrent supplier change in this isolated fixture.
            });
    assertEquals(
        "input_checksum_mismatch",
        assertThrows(Problem.class, () -> changing.importAttachment("agi", original.toString()))
            .code);
    try (var inputs = Files.walk(root.resolve(".datenportal-integrator/input"))) {
      assertEquals(0, inputs.filter(Files::isRegularFile).count());
    }
  }

  @Test
  void writesOnlyConvertersAndRequiresCurrentHashForOverwrite() {
    String path = "topics/agi/ch.so.test/Converter.java";
    var created = Json.obj(files.write(Json.map("path", path, "content", "first")));
    assertEquals(
        "workspace_file_conflict",
        assertThrows(Problem.class, () -> files.write(Json.map("path", path, "content", "second")))
            .code);
    files.write(
        Json.map("path", path, "content", "second", "expected_sha256", created.get("sha256")));
    assertEquals("second", Json.obj(files.read(path)).get("content"));
    for (String forbidden :
        List.of(
            "src/Workflow.java",
            "topics/agi/ch.so.test/original.csv",
            "topics/agi/ch.so.test/model.ili",
            "../repo/agi/ch.so.test/datasheet.xtf",
            ".datenportal-integrator/runs/123/state.json"))
      assertEquals(
          "workspace_access_denied",
          assertThrows(
                  Problem.class,
                  () -> files.write(Json.map("path", forbidden, "content", "change")))
              .code);
  }

  @Test
  void integratorCannotBypassImportOrReadSourcesThroughFileArguments() throws Exception {
    Json.atomic(
        root.resolve(OperatorWorkspace.MANIFEST),
        Json.map("schema_version", 2, "kind", "operator"));
    Path original = temp.resolve("data.csv"), source = temp.resolve("Workflow.java");
    Json.write(original, "Id\n0012\n");
    Json.write(source, "private implementation");
    try (var workflow =
        new Workflow(files.s, new ProcessRunner(), (n, a) -> Json.map(), (n, a) -> Json.map())) {
      var start =
          Json.map(
              "organization", "agi", "identifier", "ch.so.test", "data_path", original.toString());
      assertEquals(
          "workspace_access_denied",
          assertThrows(Problem.class, () -> workflow.call("start", start)).code);
      var imported = files.importAttachment("agi", original.toString());
      start.put("data_path", imported.get("path"));
      var run = Json.obj(workflow.call("start", start));
      String id = Json.required(run, "id");
      assertEquals(
          "workspace_access_denied",
          assertThrows(
                  Problem.class,
                  () ->
                      workflow.call(
                          "attach",
                          Json.map("run_id", id, "path", source.toString(), "role", "data")))
              .code);
      assertEquals(
          "workspace_access_denied",
          assertThrows(
                  Problem.class,
                  () ->
                      workflow.call(
                          "stage_change",
                          Json.map(
                              "run_id",
                              id,
                              "relative_path",
                              "agi/ch.so.test/model.ili",
                              "source_path",
                              source.toString())))
              .code);
      Json.write(Path.of(imported.get("path").toString()), "changed\n");
      assertEquals(
          "input_checksum_mismatch",
          assertThrows(
                  Problem.class,
                  () ->
                      workflow.call(
                          "attach",
                          Json.map("run_id", id, "path", imported.get("path"), "role", "data")))
              .code);
    }
  }

  @Test
  void schemaDiscoveryIsPublicAndStatusSemanticsAreExplained() {
    var schema = Json.obj(Operations.schema("attach"));
    var role =
        Json.obj(Json.obj(Json.obj(schema.get("inputSchema")).get("properties")).get("role"));
    assertEquals(List.of("data", "metadata", "metadata_source", "model"), role.get("enum"));
    assertTrue(role.get("description").toString().contains("entwertet"));
    assertEquals(
        "unknown_operation",
        assertThrows(Problem.class, () -> Operations.schema("internal_method")).code);
  }
}
