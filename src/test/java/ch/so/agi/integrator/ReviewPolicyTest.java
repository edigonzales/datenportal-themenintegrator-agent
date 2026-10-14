package ch.so.agi.integrator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ReviewPolicyTest {
  @TempDir Path temp;

  Path home(String name) {
    Path p = temp.resolve(name);
    Json.write(p.resolve("config/rules.json"), "{\"delimiter\":\";\"}");
    Json.write(p.resolve("config/local.example.toml"), "example only");
    Json.write(p.resolve("validation/office-check.ini"), "validation rules");
    Json.write(p.resolve("models/example.ili"), "model content");
    Json.write(p.resolve("lib/datenportal-integrator.jar"), "integrator binary " + name);
    Json.write(p.resolve("lib/ilivalidator.jar"), "validator content");
    Json.write(p.resolve("lib/datasheet-mcp.jar"), "datasheet content");
    Json.write(p.resolve("lib/interlis-mcp.jar"), "interlis content");
    return p;
  }

  @Test
  void migratedRunsDoNotNeedThePreviousPackageOnFutureUpdates() throws Exception {
    var f = new Fixtures(temp.resolve("fixture"));
    f.topic(false, "Jahr\n2025\n");
    Path workspace = temp.resolve("workspace");
    Json.write(workspace.resolve("config/local.toml"), Json.contents(f.settings.file));
    assertEquals(
        "already_current",
        Json.obj(
                ApprovalMigration.update(
                    workspace, temp.resolve("removed-package"), temp.resolve("new-package")))
            .get("status"));
  }

  @Test
  void previousPackageIsReadOnlyFromTheManagedLauncher() {
    Path workspace = temp.resolve("workspace");
    Path old = temp.resolve("package with ' quote");
    Path launcher = workspace.resolve("bin/datenportal-agent");
    Json.write(
        launcher,
        "#!/bin/bash\nexec "
            + GitHubCredentials.quote(old.resolve("bin/datenportal-agent").toString())
            + " \"$@\"\n");
    Json.atomic(
        workspace.resolve(OperatorWorkspace.MANIFEST),
        Json.map(
            "schema_version",
            2,
            "kind",
            "operator",
            "managed_files",
            Json.map("bin/datenportal-agent", Json.sha(launcher))));
    assertEquals(old, ApprovalMigration.previousHome(workspace));
    Json.write(launcher, "exec '/different/bin/datenportal-agent' \"$@\"\n");
    assertNull(ApprovalMigration.previousHome(workspace));
  }

  @Test
  void packageLocationVersionExamplesAndIntegratorBinaryAreNotRules() throws Exception {
    var f = new Fixtures(temp.resolve("fixture"));
    Path a = home("old"), b = home("new");
    Json.write(b.resolve("validation/review-policy.json"), "{\"revision\":1}");
    Json.write(b.resolve("config/local.example.toml"), "new documentation");
    assertEquals(ReviewPolicy.fingerprint(f.settings, a), ReviewPolicy.fingerprint(f.settings, b));
    assertNotEquals(f.settings.legacyFingerprint(a), f.settings.legacyFingerprint(b));
    f.settings.values.put("input_roots", List.of("/some/new/input"));
    f.settings.values.put("timeout_seconds", 600L);
    assertEquals(ReviewPolicy.fingerprint(f.settings, a), ReviewPolicy.fingerprint(f.settings, b));
  }

  @Test
  void changedRulesModelsOrValidatorRemainRelevant() throws Exception {
    var f = new Fixtures(temp.resolve("fixture"));
    Path a = home("old");
    for (String file :
        List.of(
            "config/rules.json",
            "validation/office-check.ini",
            "models/example.ili",
            "lib/ilivalidator.jar",
            "lib/datasheet-mcp.jar",
            "lib/interlis-mcp.jar",
            "validation/review-policy.json")) {
      Path b = home(UUID.randomUUID().toString());
      Json.write(b.resolve(file), file.endsWith(".json") ? "{\"revision\":2}" : "changed content");
      assertNotEquals(
          ReviewPolicy.fingerprint(f.settings, a), ReviewPolicy.fingerprint(f.settings, b), file);
    }
  }

  @Test
  void archiveMetadataDoesNotChangeToolIdentity() throws Exception {
    Path a = temp.resolve("a.jar"), b = temp.resolve("b.jar"), c = temp.resolve("c.jar");
    jar(a, "old", "same rule");
    jar(b, "new", "same rule");
    jar(c, "new", "different rule");
    assertNotEquals(Json.sha(a), Json.sha(b));
    assertEquals(ReviewPolicy.jarContent(a), ReviewPolicy.jarContent(b));
    assertNotEquals(ReviewPolicy.jarContent(a), ReviewPolicy.jarContent(c));
  }

  void jar(Path p, String version, String rule) throws Exception {
    try (var zip = new ZipOutputStream(Files.newOutputStream(p))) {
      for (var e : Map.of("META-INF/MANIFEST.MF", version, "Rule.class", rule).entrySet()) {
        zip.putNextEntry(new ZipEntry(e.getKey()));
        zip.write(e.getValue().getBytes());
        zip.closeEntry();
      }
    }
  }

  String legacy(Fixtures f, Path home) {
    String id = f.topic(false, "Jahr\n2025\n");
    f.approveAll(id);
    f.workflow.store.edit(
        id,
        r -> {
          r.remove("fingerprint_version");
          for (String gate : List.of("data", "metadata")) {
            String fp = f.workflow.fingerprint(r, gate, f.settings.legacyFingerprint(home));
            Json.obj(Json.obj(r.get("approvals")).get(gate)).put("fingerprint", fp);
            Json.obj(Json.obj(r.get("checks")).get(gate)).put("fingerprint", fp);
          }
          r.put(
              "deliveries",
              Json.map(
                  "local",
                  Json.map(
                      "phase",
                      "complete",
                      "publication",
                      "accepted",
                      "fingerprint",
                      Json.obj(Json.obj(r.get("checks")).get("metadata")).get("fingerprint"))));
          return null;
        });
    return id;
  }

  @Test
  void migrateOnlyMatchingHistoricalFingerprintsAndPreserveHumanEvidence() throws Exception {
    var f = new Fixtures(temp.resolve("fixture"));
    Path a = home("old"), b = home("new");
    String id = legacy(f, a);
    f.workflow.store.edit(
        id,
        r -> {
          var approvals = Json.read(Json.text(r.get("approvals")));
          assertEquals(
              "migrated", Json.obj(ApprovalMigration.migrate(f.workflow, r, a, b)).get("status"));
          for (String gate : List.of("data", "metadata")) {
            var approval = Json.obj(Json.obj(r.get("approvals")).get(gate));
            assertEquals(
                f.workflow.fingerprint(r, gate, ReviewPolicy.fingerprint(f.settings, b)),
                approval.get("fingerprint"));
            assertEquals(
                Json.obj(approvals.get(gate)).get("human_statement"),
                approval.get("human_statement"));
            assertEquals(Json.obj(approvals.get(gate)).get("at"), approval.get("at"));
          }
          assertEquals(
              "accepted", Json.obj(Json.obj(r.get("deliveries")).get("local")).get("publication"));
          String after = Json.text(r);
          ApprovalMigration.migrate(f.workflow, r, a, b);
          assertEquals(after, Json.text(r));
          return null;
        });
  }

  @Test
  void changedRuleOrDataCannotReviveAnApproval() throws Exception {
    var f = new Fixtures(temp.resolve("fixture"));
    Path a = home("old"), b = home("new");
    String id = legacy(f, a);
    Json.write(b.resolve("validation/office-check.ini"), "changed rule");
    f.workflow.store.edit(
        id,
        r -> {
          String before = Json.text(r);
          assertEquals(
              "rules_changed",
              Json.obj(ApprovalMigration.migrate(f.workflow, r, a, b)).get("status"));
          assertEquals(before, Json.text(r));
          return null;
        });
    final Path next = home("new");
    Path changed = temp.resolve("changed.csv");
    Json.write(changed, "Jahr\n2026\n");
    f.call("attach", "run_id", id, "role", "data", "path", changed.toString());
    f.workflow.store.edit(
        id,
        r -> {
          String approval = Json.text(r.get("approvals"));
          ApprovalMigration.migrate(f.workflow, r, a, next);
          assertEquals(approval, Json.text(r.get("approvals")));
          assertNotEquals(
              f.workflow.fingerprint(r, "data", ReviewPolicy.fingerprint(f.settings, next)),
              Json.obj(Json.obj(r.get("approvals")).get("data")).get("fingerprint"));
          return null;
        });
  }
}
