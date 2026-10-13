package ch.so.agi.integrator;

import java.nio.file.*;

/** Real installed-image network probe with synthetic paths; no external services or approvals. */
public final class ImageRuntimeAcceptance {
  public static void main(String[] args) throws Exception {
    // Do not supply JAVA_TOOL_OPTIONS in the test launcher: the installed image
    // itself must support IPv4 host loopback on Docker Desktop.
    if (!Boolean.getBoolean("java.net.preferIPv4Stack"))
      throw new AssertionError("Installed runtime must enable IPv4 host networking");
    Path root = Path.of(args[0]);
    Files.createDirectories(root.resolve("config"));
    Files.createDirectories(root.resolve("topics/shared/gradle"));
    Files.createDirectories(root.resolve("stack/scripts"));
    Files.writeString(root.resolve("topics/shared/gradle/init.gradle"), "// synthetic fixture\n");
    Files.writeString(root.resolve("stack/scripts/up.sh"), "# synthetic fixture\n");
    Files.writeString(root.resolve("SYNTHETIC_TEST_FIXTURE"), "Image network probe only\n");
    Files.writeString(
        root.resolve("config/local.toml"),
        "root=\"..\"\ntopics_repo=\"topics\"\nstack_repo=\"stack\"\n[datasheet]\ntransport=\"http\"\nurl=\"http://127.0.0.1:8000/mcp\"\n");
    if (!AgentResources.installed()
        || Files.exists(root.resolve("build/libs/datenportal-integrator.jar")))
      throw new AssertionError("Must use installed image without workspace JAR");
    var result = AgentRuntime.check(new Settings(root.resolve("config/local.toml")));
    if (!"host-loopback-verified".equals(result.get("network"))) throw new AssertionError(result);
    System.out.println(Json.text(result));
  }
}
