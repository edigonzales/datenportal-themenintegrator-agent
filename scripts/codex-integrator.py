#!/usr/bin/env python3
"""Launch Codex with this MCP explicitly, without editing personal configuration."""

import json
import os
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
args = [
    "run",
    "--locked",
    "--directory",
    str(root),
    "datenportal-integrator",
    "--config",
    str(root / "config/local.toml"),
    "serve",
]
settings = {
    "mcp_servers.datenportal_integrator.command": "uv",
    "mcp_servers.datenportal_integrator.args": args,
    "mcp_servers.datenportal_integrator.tool_timeout_sec": 600,
    "mcp_servers.datenportal_integrator.env_vars": [
        "DATENPORTAL_LOCAL_USER",
        "DATENPORTAL_LOCAL_TOKEN",
        "DATENPORTAL_JENKINS_USER",
        "DATENPORTAL_JENKINS_TOKEN",
    ],
}
command = ["codex", "-C", str(root)]
for name, value in settings.items():
    command.extend(["-c", name + "=" + json.dumps(value)])
command.extend(sys.argv[1:])
os.execvpe("codex", command, os.environ)
