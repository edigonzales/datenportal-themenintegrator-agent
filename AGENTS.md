# datenportal-themenintegrator-agent

For integration, read `skills/datenportal-themenintegrator/SKILL.md` and use this
repository's Java CLI/MCP. Integrator implementation, rules, tests and runtime
files belong here. Never modify source files in the dev-stack, datasheet MCP,
interlis-mcp, Jenkins plugin, GRETL or portal. Only staged, reviewed business
changes belong in datenportal-themenrepo.

The default build/runtime uses Docker: `./bin/datenportal-agent init`,
`./bin/datenportal-agent gradle test jar spotlessCheck`.
Host JDK 25 remains optional via `--runtime local` or direct Gradle.
Run `./bin/datenportal-agent gradle integrationTest` with the actual configured tools for integration
verification. GRETL uses the existing Java-17 wrapper and versions inside its pinned Jenkins image. Never add a host GRETL/JDK-17 fallback or copy its JAR bundle to the host.
The local configuration is the ignored `config/local.toml`; secrets remain in
environment variables or a credential store. Never print credential-bearing
configuration or runtime Docker inspection output.

Persist approvals only for actual human responses. Automated approvals are
permitted solely in clearly marked, isolated test fixtures; they cannot authorize
real topic-repository changes or INT/PROD publication. Resume recorded external
runs; never retry an ambiguous upload. Keep actual, simulated and open acceptance
checks distinct in docs/abnahme.md. Python is being replaced by Java; do not add
new Python implementation, launchers, converter recipes or tests.
