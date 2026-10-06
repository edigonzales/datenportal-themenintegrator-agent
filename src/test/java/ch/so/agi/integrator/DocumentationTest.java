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
      var match = links.matcher(Json.contents(file));
      while (match.find()) {
        String target = match.group(1).split("#", 2)[0];
        if (target.isEmpty() || target.contains("://") || target.startsWith("mailto:")) continue;
        assertTrue(Files.exists(file.getParent().resolve(target)), file + " -> " + target);
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
