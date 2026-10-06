from __future__ import annotations

import shutil

import httpx

from .common import IntegrationError
from .stack import Stack


def doctor(settings):
    checks = {
        "topics_repo": settings.topics_repo.is_dir(),
        "stack_repo": settings.stack_repo.is_dir(),
        "offices": (settings.topics_repo / "shared/data/offices.xtf").is_file(),
        "tools": {
            name: bool(shutil.which(name)) for name in ["git", "gh", "docker", settings.validator_command[0]]
        },
    }
    try:
        response = httpx.get(settings.datasheet_mcp_url.rsplit("/mcp", 1)[0] + "/actuator/health", timeout=3)
        checks["datasheet_mcp"] = response.status_code == 200 and response.json().get("status") == "UP"
    except (httpx.HTTPError, ValueError):
        checks["datasheet_mcp"] = False
    try:
        checks["stack"] = Stack(settings).inspect()
    except IntegrationError as e:
        checks["stack"] = e.as_dict()
    checks["environments"] = {
        name: {
            "enabled": env.enabled,
            "kind": env.kind,
            "credentials_present": bool(
                __import__("os").getenv(env.username_env) and __import__("os").getenv(env.token_env)
            ),
        }
        for name, env in settings.environments.items()
    }
    return checks
