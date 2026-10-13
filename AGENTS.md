# datenportal-themenintegrator-agent

This checkout is the development repository. Normal business use belongs in the
source-free operator workspace installed with `workspace install`. Business
agents use public MCP schemas and the three skills, never implementation-source
inspection. The implementation/build instructions below apply to explicit
development requests. Operator instructions are packaged from `templates/operator`.

Use this repository's Java CLI/MCP and select the skill by the requested task:

- Topic integration, CSV checks, data/metadata deliveries or their resumption:
  [datenportal-themenintegrator](skills/datenportal-themenintegrator/SKILL.md).
- Standalone validation-model work:
  [datenportal-validierungsmodell](skills/datenportal-validierungsmodell/SKILL.md).
- Standalone organization creation:
  [datenportal-organisation](skills/datenportal-organisation/SKILL.md).

For modeling within topic integration, also read the model skill and keep the
existing `run_id`; do not start a second workflow. A missing office within a
topic is an `office` addition, not a new organization.
Before passing CSV files or chat attachments to the integrator, read and apply
[Eingangsdateien](docs/agent/eingangsdateien.md): check original CSV names before
handoff and automatically copy chat CSV/XTF/XLSX attachments unchanged with
three matching SHA-256 checks. Originals and confirmed semantics remain intact.

Integrator implementation, rules, tests and runtime
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
