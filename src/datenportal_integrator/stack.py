from __future__ import annotations

import json

from .common import IntegrationError, command
from .jenkins import Jenkins, read_manifest


class Stack:
    def __init__(self, settings):
        self.settings = settings
        self.base = ["docker", "compose"]
        for filename in settings.compose_files:
            self.base.extend(["-f", filename])

    def compose(self, *args, timeout=120, input=None):
        return command(self.base + list(args), self.settings.stack_repo, timeout, input)

    def inspect(self):
        # Docker inspect output is deliberately never returned: it contains runtime secrets.
        # No Compose interpolation before up.sh has created its runtime .env.
        ids = command(
            [
                "docker",
                "ps",
                "-aq",
                "--filter",
                f"label=com.docker.compose.project.working_dir={self.settings.stack_repo}",
                "--filter",
                "label=com.docker.compose.service=jenkins",
            ]
        ).split()
        if not ids:
            return {"present": False, "compatible": True, "running": False}
        if len(ids) != 1:
            raise IntegrationError(
                "ambiguous_stack", "Mehrere Jenkins-Container im vorgesehenen Compose-Projekt."
            )
        container = json.loads(command(["docker", "inspect", ids[0]]))[0]
        mounts = [m for m in container["Mounts"] if m["Destination"] == "/workspace/themenrepo"]
        env = dict(v.split("=", 1) for v in container["Config"].get("Env", []) if "=" in v)
        from pathlib import Path

        compatible = len(mounts) == 1 and Path(mounts[0]["Source"]).resolve() == self.settings.topics_repo
        compatible &= env.get("THEMEN_REPO_MODE") == "working-tree"
        if not compatible:
            raise IntegrationError(
                "stack_mismatch",
                "Laufende/vorhandene Instanz verwendet einen anderen Themenbestand oder Modus.",
            )
        return {
            "present": True,
            "compatible": True,
            "running": container["State"]["Running"],
            "health": container["State"].get("Health", {}).get("Status", "unknown"),
        }

    def ensure(self):
        status = self.inspect()
        if not status["running"]:
            # The existing script generates only its normal ignored runtime files.
            command(
                ["bash", "scripts/up.sh", *self.settings.stack_start_args],
                self.settings.stack_repo,
                timeout=self.settings.timeout_seconds,
            )
        self.inspect()
        self.ensure_services({"garage", "jenkins", "downloads"})
        return {"ready": True, "reused": status["running"]}

    def ensure_services(self, required):
        states = self.compose("ps", "--all", "--format", "json")
        try:
            containers = json.loads(states)
            if isinstance(containers, dict):
                containers = [containers]
        except ValueError:
            containers = [json.loads(line) for line in states.splitlines() if line.strip()]
        present = {c.get("Service"): c for c in containers}
        missing = sorted(s for s in required if present.get(s, {}).get("State") != "running")
        if missing:
            # Use the documented Compose interface; preserve all existing containers.
            self.compose(
                "up",
                "-d",
                "--no-recreate",
                "--wait",
                "--wait-timeout",
                "120",
                *missing,
                timeout=self.settings.timeout_seconds,
            )
        bad = [
            c.get("Service")
            for c in containers
            if c.get("Service") in required
            and c.get("Service") not in missing
            and c.get("Health") in {"unhealthy", "starting"}
        ]
        if bad:
            raise IntegrationError("stack_unhealthy", "Stack ist noch nicht bereit.", services=bad)

    def bootstrap(self, env):
        if env.kind != "local":
            raise IntegrationError("local_only", "Automatische Erstinitialisierung ist nur lokal vorgesehen.")
        if read_manifest(env) is not None:
            self.ensure_services({"sodata"})
            return {"initialized": False, "reason": "existing_manifest"}
        with Jenkins(env) as jenkins:
            info = jenkins.get("api/json").json()
            if info.get("quietingDown"):
                raise IntegrationError(
                    "jenkins_paused", "Jenkins ist bereits administrativ pausiert; Zustand nicht übernehmen."
                )
            jenkins.post("quietDown")
            try:
                running = jenkins.get(
                    "computer/api/json?tree=computer[executors[currentExecutable[url]],oneOffExecutors[currentExecutable[url]]]"
                ).json()
                if any(
                    e.get("currentExecutable")
                    for c in running.get("computer", [])
                    for e in c.get("executors", []) + c.get("oneOffExecutors", [])
                ):
                    raise IntegrationError(
                        "builds_running",
                        "Laufende Builds abwarten; Erstinitialisierung noch nicht gestartet.",
                    )
                if read_manifest(env) is not None:
                    self.ensure_services({"sodata"})
                    return {"initialized": False, "reason": "manifest_appeared"}
                script = """set -euo pipefail
work=$(mktemp -d /var/jenkins_home/datenportal-integrator.XXXXXX)
tar -C /workspace/themenrepo --exclude=.git --exclude=.gradle --exclude=build --exclude=.DS_Store -cf - . | tar -C "$work" -xf -
cd "$work"
./shared/bin/gradlew-java17.sh --no-daemon -I "$work/shared/gradle/init.gradle" initializePublication -Ps3Publish=true -PgitWriteBack=false -PreloadPortal=false
"""
                self.compose(
                    "exec", "-T", "jenkins", "bash", "-s", timeout=self.settings.timeout_seconds, input=script
                )
            finally:
                jenkins.post("cancelQuietDown")
        manifest = read_manifest(env)
        if manifest is None:
            raise IntegrationError("bootstrap_incomplete", "Initialisierung lieferte kein lesbares Manifest.")
        self.ensure_services({"sodata"})
        return {"initialized": True, "release_id": manifest["releaseId"]}
