#!/usr/bin/env python3
"""Print portable, absolute MCP snippets; do not overwrite personal harness settings."""

import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
command = [
    "uv",
    "run",
    "--locked",
    "--directory",
    str(root),
    "datenportal-integrator",
    "--config",
    str(root / "config/local.toml"),
    "serve",
]
if len(sys.argv) > 1 and sys.argv[1] == "opencode":
    print(
        json.dumps(
            {
                "mcp": {
                    "datenportal_integrator": {
                        "type": "local",
                        "command": command,
                        "enabled": True,
                        "timeout": 600000,
                    }
                }
            },
            indent=2,
        )
    )
else:
    print("[mcp_servers.datenportal_integrator]")
    print('command = "uv"')
    print("args = " + json.dumps(command[1:]))
    print("tool_timeout_sec = 600")
    print(
        'env_vars = ["DATENPORTAL_LOCAL_USER", "DATENPORTAL_LOCAL_TOKEN", "DATENPORTAL_JENKINS_USER", "DATENPORTAL_JENKINS_TOKEN"]'
    )
