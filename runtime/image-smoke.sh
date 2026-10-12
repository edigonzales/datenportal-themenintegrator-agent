#!/usr/bin/env bash
# No workspace, source tree, build cache or host JAR is mounted.
set -euo pipefail
image=${1:?image required}
platform=${2:?platform required}
version=$(docker run --rm --platform "$platform" --network none "$image" --version)
[[ $version == "${AGENT_VERSION:-0.1.0-dev}" ]]
docker run --rm --platform "$platform" --network none "$image" --help >/dev/null
# Synthetic configuration exercises the real stdio MCP initialization and resource paths.
docker run --rm --platform "$platform" --network none -e "EXPECTED_AGENT_VERSION=$version" --entrypoint bash "$image" -euc '
    mkdir -p /tmp/agent/config
    printf "root = \"..\"\ntopics_repo = \"/tmp/topics\"\nstack_repo = \"/tmp/stack\"\n[datasheet]\ntransport = \"http\"\nurl = \"http://127.0.0.1:8000/mcp\"\n" > /tmp/agent/config/local.toml
    test -r "$DATENPORTAL_AGENT_HOME/templates/review-java.html"
    test -r "$DATENPORTAL_AGENT_HOME/validation/office-check.ini"
    test -r "$DATENPORTAL_AGENT_HOME/tests/fixtures/dataset.xtf"
    test -r "$DATENPORTAL_AGENT_HOME/config/rules.json"
    jar="$DATENPORTAL_AGENT_HOME/build/libs/datenportal-integrator.jar"
    cat > /tmp/ImageSmoke.java <<"JAVA"
package ch.so.agi.integrator;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
public class ImageSmoke implements CsvConverter {
  public void convert(Path input, Path output) throws Exception {
    Files.writeString(output, Files.readString(input).replace("2025", "2026"));
  }
  public static void main(String[] args) throws Exception {
    Path workspace = Path.of("/tmp/agent");
    if (!AgentResources.jar(workspace).startsWith("/opt/datenportal-agent/")) throw new AssertionError();
    Reports.render(workspace, workspace.resolve("review.html"), Reports.Kind.CSV, "ch.so.image.smoke", Map.of("valid", true));
    String review = Files.readString(workspace.resolve("review.html"));
    if (!review.contains("CSV prüfen") || !review.contains("ch.so.image.smoke") || !review.contains("Prüfung bestanden")) throw new AssertionError("Rendered image report missing phase, identifier or result");
    Path config = Path.of("config/local.toml");
    if (Files.exists(config)) throw new AssertionError("Cold image unexpectedly has local configuration");
    Initializer.prepareConfig(null);
    byte[] embedded = Files.readAllBytes(AgentResources.resolve(workspace, "config/local.example.toml"));
    if (!Arrays.equals(embedded, Files.readAllBytes(config))) throw new AssertionError("Embedded defaults differ");
    var settings = new Settings(config);
    Map<String, Object> compose;
    try (var input = ImageSmoke.class.getResourceAsStream("/compose.yaml")) {
      compose = Json.obj(new Yaml(new SafeConstructor(new LoaderOptions())).load(input));
    }
    var services = Json.obj(compose.get("services"));
    for (String tool : List.of("datasheet", "interlis", "gretl")) {
      String image = Json.required(Json.obj(settings.values.get(tool)), "image");
      if (!image.matches("[^@]+@sha256:[a-f0-9]{64}") || !image.equals(Json.obj(services.get(tool)).get("image")))
        throw new AssertionError("Inconsistent image pin: " + tool);
    }
    if (!GretlRuntime.reference(settings).equals(GretlRuntime.DEFAULT_IMAGE)) throw new AssertionError("GRETL fallback differs");
    byte[] existing = (Files.readString(config).replace(GretlRuntime.DEFAULT_IMAGE, "sogis/fixture:existing") + "\n# Existing installation\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Files.write(config, existing);
    Initializer.prepareConfig(null);
    if (!Arrays.equals(existing, Files.readAllBytes(config))) throw new AssertionError("Existing configuration changed");
    Files.writeString(Path.of("/tmp/input.csv"), "Jahr\n2025\n");
    var params = io.modelcontextprotocol.client.transport.ServerParameters.builder(
        Path.of(System.getProperty("java.home"), "bin/java").toString())
        .args("-jar", AgentResources.jar(workspace).toString(), "--config", "/tmp/agent/config/local.toml", "serve").build();
    var transport = new io.modelcontextprotocol.client.transport.StdioClientTransport(params,
        new io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper(new tools.jackson.databind.json.JsonMapper()));
    try (var client = io.modelcontextprotocol.client.McpClient.sync(transport)
        .initializationTimeout(java.time.Duration.ofSeconds(30)).build()) {
      var initialized = client.initialize();
      if (!initialized.serverInfo().version().equals(System.getenv("EXPECTED_AGENT_VERSION"))) throw new AssertionError("MCP version differs");
      if (client.listTools().tools().size() != Operations.ALL.size()) throw new AssertionError("MCP tools missing");
    }
  }
}
JAVA
    javac --release 25 -cp "$jar" -d /tmp /tmp/ImageSmoke.java
    java -cp "/tmp:$jar" ch.so.agi.integrator.ImageSmoke
    java -cp "/tmp:$jar" ch.so.agi.integrator.ConverterMain ch.so.agi.integrator.ImageSmoke /tmp/input.csv /tmp/output.csv
    grep -q 2026 /tmp/output.csv
    grep -q 2025 /tmp/input.csv
  '
