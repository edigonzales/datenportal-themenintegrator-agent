from __future__ import annotations

import os
import tomllib
from pathlib import Path
from typing import Literal
from urllib.parse import urlparse

from pydantic import BaseModel, ConfigDict, Field, field_validator

from .common import IntegrationError, digest, sha


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Environment(Strict):
    kind: Literal["local", "int", "prod"]
    jenkins_url: str
    portal_url: str
    manifest_url: str
    seed_job: str = "gretl-datenportal-seed"
    username_env: str = "DATENPORTAL_JENKINS_USER"
    token_env: str = "DATENPORTAL_JENKINS_TOKEN"
    git_remote: str = "origin"
    git_branch: str = "main"
    enabled: bool = False
    # An operator attests the existing managed-git runtime, without changing it.
    repository_mode: Literal["working-tree", "managed-git"] = "managed-git"
    git_write_back: bool = False
    reload_portal: bool = True

    @field_validator("jenkins_url", "portal_url", "manifest_url")
    @classmethod
    def url(cls, value):
        parsed = urlparse(value)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.hostname
            or parsed.username
            or parsed.password
        ):
            raise ValueError("HTTP(S)-Adresse ohne eingebettete Zugangsdaten erforderlich")
        return value.rstrip("/")


class Settings(Strict):
    topics_repo: Path
    stack_repo: Path
    state_dir: Path = Path(".datenportal-integrator/runs")
    datasheet_mcp_url: str = "http://127.0.0.1:8000/mcp"
    validator_command: list[str] = Field(default_factory=lambda: ["java", "-jar", "ilivalidator.jar"])
    model_dirs: list[str] = Field(default_factory=list)
    compose_files: list[str] = Field(default_factory=lambda: ["compose.yaml"])
    stack_start_args: list[str] = Field(default_factory=list)
    environments: dict[str, Environment] = Field(default_factory=dict)
    timeout_seconds: int = Field(default=300, ge=1, le=7200)
    root: Path = Field(default=Path("."), exclude=True)

    def environment(self, name: str) -> Environment:
        if name not in self.environments or not self.environments[name].enabled:
            raise IntegrationError(
                "environment_disabled", f"Umgebung {name!r} fehlt oder ist nicht freigeschaltet."
            )
        env = self.environments[name]
        if env.kind == "local" and any(
            urlparse(u).hostname not in {"localhost", "127.0.0.1", "::1"}
            for u in (env.jenkins_url, env.portal_url, env.manifest_url)
        ):
            raise IntegrationError(
                "local_endpoint_required", "Lokale Automatik darf nur Loopback-Adressen verwenden."
            )
        if env.git_write_back:
            raise IntegrationError(
                "runtime_incompatible", "Der PR-Workflow verlangt deaktiviertes Git-Rückschreiben."
            )
        if env.kind != "local" and env.repository_mode != "managed-git":
            raise IntegrationError("runtime_incompatible", "INT/PROD benötigen managed-git.")
        return env

    def fingerprint(self) -> str:
        files = [self.root / "config/rules.json", *sorted((self.root / "validation").glob("*"))]
        dependencies = []
        for directory in self.model_dirs:
            if not directory.startswith("https://"):
                dependencies.extend(sorted((self.root / directory).glob("*.ili")))
        for part in self.validator_command:
            if part.endswith(".jar") and (self.root / part).is_file():
                dependencies.append(self.root / part)
        return digest(
            {
                "settings": self.model_dump(mode="json"),
                "rules": {str(p.relative_to(self.root)): sha(p) for p in files if p.is_file()},
                "validation_dependencies": {str(p.resolve()): sha(p) for p in dependencies},
            }
        )


def load_settings(path: str | Path | None = None) -> Settings:
    config_path = Path(path or os.environ.get("DATENPORTAL_INTEGRATOR_CONFIG", "config/local.toml")).resolve()
    if not config_path.is_file():
        raise IntegrationError(
            "configuration_missing",
            f"Konfiguration fehlt: {config_path}",
            hint="config/local.example.toml nach config/local.toml kopieren und anpassen.",
        )
    with config_path.open("rb") as stream:
        values = tomllib.load(stream)
    root = Path(values.pop("root", config_path.parent.parent))
    if not root.is_absolute():
        root = (config_path.parent / root).resolve()
    settings = Settings.model_validate({**values, "root": root})
    for name in ("topics_repo", "stack_repo", "state_dir"):
        p = getattr(settings, name).expanduser()
        setattr(settings, name, p.resolve() if p.is_absolute() else (root / p).resolve())
    return settings
