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
    var files = new ArrayList<Path>();
    files.add(Fixtures.ROOT.resolve("README.md"));
    try (var paths = Files.list(Fixtures.ROOT.resolve("docs"))) {
      paths.filter(p -> p.toString().endsWith(".md")).forEach(files::add);
    }
    files.add(Fixtures.ROOT.resolve("skills/datenportal-themenintegrator/SKILL.md"));
    Pattern links = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
    for (Path file : files) {
      anchors(file);
      var match = links.matcher(Json.contents(file));
      while (match.find()) {
        String[] parts = match.group(1).split("#", 2);
        String target = parts[0];
        if (target.contains("://") || target.startsWith("mailto:")) continue;
        Path linked = target.isEmpty() ? file : file.getParent().resolve(target);
        assertTrue(Files.exists(linked), file + " -> " + target);
        if (parts.length == 2 && linked.toString().endsWith(".md"))
          assertTrue(anchors(linked).contains(parts[1]), file + " -> " + match.group(1));
      }
    }
    String skill = Json.contents(files.getLast());
    assertTrue(skill.startsWith("---\n"));
    var meta =
        Json.obj(
            new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(skill.substring(4, skill.indexOf("\n---", 4))));
    assertEquals("datenportal-themenintegrator", meta.get("name"));
    assertFalse(Json.required(meta, "description").isBlank());
  }

  Set<String> anchors(Path file) {
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
  void inlineCliExamplesUseActualArgumentSchemas() {
    Pattern examples = Pattern.compile("call ([a-z_]+) --json '([^'\\n]+)'");
    for (String doc :
        List.of("README.md", "docs/anwenderhandbuch.md", "docs/entwicklerhandbuch.md")) {
      var matcher = examples.matcher(Json.contents(Fixtures.ROOT.resolve(doc)));
      while (matcher.find())
        assertDoesNotThrow(
            () -> Operations.validate(matcher.group(1), Json.read(matcher.group(2))), doc);
    }
  }
}
