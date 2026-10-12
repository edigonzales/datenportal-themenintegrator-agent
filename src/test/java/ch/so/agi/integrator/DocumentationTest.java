package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class DocumentationTest {
  @Test
  void localMarkdownLinksAndSkillFrontmatterResolve() throws Exception {
    verifyGuidance(Fixtures.ROOT);
  }

  static List<Path> markdownFiles(Path root) throws Exception {
    var files = new ArrayList<Path>();
    files.add(root.resolve("README.md"));
    files.add(root.resolve("AGENTS.md"));
    for (String directory : List.of("docs", "skills")) {
      try (var paths = Files.walk(root.resolve(directory))) {
        paths.filter(p -> p.toString().endsWith(".md")).sorted().forEach(files::add);
      }
    }
    return files;
  }

  static void verifyGuidance(Path root) throws Exception {
    var names = new HashSet<String>();
    Pattern links = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
    for (Path file : markdownFiles(root)) {
      anchors(file);
      var match = links.matcher(Json.contents(file));
      while (match.find()) {
        String[] parts = match.group(1).split("#", 2);
        String target = parts[0];
        if (target.contains("://") || target.startsWith("mailto:")) continue;
        Path linked = target.isEmpty() ? file : file.getParent().resolve(target);
        assertTrue(Files.exists(linked), file + " -> " + target);
        assertTrue(linked.toRealPath().startsWith(root.toRealPath()), file + " -> " + target);
        if (parts.length == 2 && linked.toString().endsWith(".md"))
          assertTrue(anchors(linked).contains(parts[1]), file + " -> " + match.group(1));
      }
      if (!file.getFileName().toString().equals("SKILL.md")) continue;
      String skill = Json.contents(file);
      assertTrue(skill.startsWith("---\n"), file.toString());
      int end = skill.indexOf("\n---\n", 4);
      assertTrue(end > 4, file.toString());
      var options = new LoaderOptions();
      options.setAllowDuplicateKeys(false);
      var meta = Json.obj(new Yaml(new SafeConstructor(options)).load(skill.substring(4, end)));
      String name = Json.required(meta, "name");
      assertEquals(file.getParent().getFileName().toString(), name);
      assertTrue(name.matches("[a-z0-9]+(?:-[a-z0-9]+)*") && name.length() < 64);
      assertTrue(names.add(name), "Duplicate skill: " + name);
      assertFalse(Json.required(meta, "description").isBlank(), file.toString());
      Path entry = root.resolve(".agents/skills").resolve(name);
      assertTrue(Files.isSymbolicLink(entry), entry.toString());
      assertFalse(Files.readSymbolicLink(entry).isAbsolute(), entry.toString());
      assertEquals(file.getParent().toRealPath(), entry.toRealPath());
    }
    assertFalse(names.isEmpty());
    try (var entries = Files.list(root.resolve(".agents/skills"))) {
      assertEquals(names, new HashSet<>(entries.map(p -> p.getFileName().toString()).toList()));
    }
  }

  static Set<String> anchors(Path file) {
    var result = new HashSet<String>();
    var counts = new HashMap<String, Integer>();
    boolean fenced = false;
    for (String line : Json.contents(file).lines().toList()) {
      if (line.startsWith("```")) fenced = !fenced;
      if (fenced || !line.matches("^#{1,6} .*")) continue;
      String base =
          line.replaceFirst("^#+ +", "")
              .toLowerCase(Locale.ROOT)
              .replaceAll("[^\\p{L}\\p{N}_ -]", "")
              .replace(' ', '-');
      int count = counts.merge(base, 1, Integer::sum) - 1;
      result.add(base + (count == 0 ? "" : "-" + count));
    }
    assertFalse(fenced, "Unclosed code fence: " + file);
    return result;
  }

  @Test
  void inlineCliExamplesUseActualArgumentSchemas() throws Exception {
    Pattern examples = Pattern.compile("call ([a-z_]+) --json '([^'\\n]+)'");
    for (Path doc : markdownFiles(Fixtures.ROOT)) {
      var matcher = examples.matcher(Json.contents(doc));
      while (matcher.find())
        assertDoesNotThrow(
            () -> Operations.validate(matcher.group(1), Json.read(matcher.group(2))),
            doc.toString());
    }
  }
}
