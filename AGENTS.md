# datenportal-themenintegrator-agent

For topic integration, read `skills/datenportal-themenintegrator/SKILL.md` and use
this repository's CLI/MCP. Keep all integrator implementation, rules, tests and
runtime files here. Never modify source files in datenportal-dev-stack, the
Java datasheet MCP, Jenkins plugin, GRETL or portal as part of an integration.
Only explicitly staged business changes belong in datenportal-themenrepo.

For development: `uv sync --locked`, `uv run pytest`, `uv run ruff check .`.
The ignored local configuration is `config/local.toml`; start with its example.
No credentials in source, command lines, reports or tests. Persist approvals for
actual human responses, never fabricate them for a real delivery. Automated test
fixtures must identify themselves as tests and cannot target INT/PROD.
