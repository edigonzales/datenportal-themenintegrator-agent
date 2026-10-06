from __future__ import annotations

import subprocess
from pathlib import Path

from .common import IntegrationError, sha
from .config import Settings


class Validator:
    def __init__(self, settings: Settings):
        self.settings = settings

    def validate(self, xtf: Path, offices: Path, output: Path):
        output.mkdir(parents=True, exist_ok=True)
        model_dirs = [
            str((self.settings.root / p).resolve()) if not p.startswith("https://") else p
            for p in self.settings.model_dirs
        ]
        model_dirs.append(str(self.settings.root / "validation"))
        base = self.settings.validator_command + ["--modeldir", ";".join(model_dirs)]
        results = []
        invocations = [
            ("offices", base + [str(offices)]),
            (
                "datasheet",
                base
                + [
                    "--config",
                    str(self.settings.root / "validation/office-check.ini"),
                    "--allObjectsAccessible",
                    "--refdata",
                    str(offices),
                    str(xtf),
                ],
            ),
        ]
        for name, args in invocations:
            log = output / f"{name}.log"
            try:
                with log.open("w") as stream:
                    result = subprocess.run(
                        args,
                        cwd=self.settings.root,
                        stdout=stream,
                        stderr=subprocess.STDOUT,
                        timeout=self.settings.timeout_seconds,
                    )
            except (OSError, subprocess.TimeoutExpired) as e:
                raise IntegrationError(
                    "validator_unavailable",
                    "ilivalidator nicht ausführbar oder Zeitlimit erreicht.",
                    log=str(log),
                ) from e
            results.append(
                {
                    "name": name,
                    "valid": result.returncode == 0,
                    "returncode": result.returncode,
                    "log": str(log),
                    "log_sha256": sha(log),
                    "messages": [
                        line
                        for line in log.read_text(errors="replace").splitlines()
                        if line.startswith(("Error:", "Warning:"))
                    ][:100],
                }
            )
        return {
            "valid": all(r["valid"] for r in results),
            "checks": results,
            "xtf_sha256": sha(xtf),
            "offices_sha256": sha(offices),
        }
