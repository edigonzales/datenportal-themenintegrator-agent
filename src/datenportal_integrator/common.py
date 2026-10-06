from __future__ import annotations

import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class IntegrationError(Exception):
    def __init__(self, code: str, message: str, **details: Any):
        super().__init__(message)
        self.code, self.message, self.details = code, message, details

    def as_dict(self):
        return {"error": self.code, "message": self.message, "details": self.details}


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def atomic_json(path: Path, data: Any):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(".tmp")
    with temp.open("w", encoding="utf-8") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.flush()
        os.fsync(stream.fileno())
    temp.replace(path)


def command(args: list[str], cwd: Path | None = None, timeout: int = 120, input: str | None = None):
    try:
        result = subprocess.run(args, cwd=cwd, input=input, text=True, capture_output=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise IntegrationError(
            "command_unavailable", f"Befehl nicht abgeschlossen: {args[0]}", reason=type(e).__name__
        ) from e
    if result.returncode:
        # Callers decide which diagnostics can safely be displayed (commands may log credentials).
        raise IntegrationError(
            "command_failed", f"Befehl fehlgeschlagen: {args[0]}", returncode=result.returncode
        )
    return result.stdout


def inside(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()):
        raise IntegrationError("unsafe_path", "Pfad liegt ausserhalb des vorgesehenen Verzeichnisses.")
    return path
