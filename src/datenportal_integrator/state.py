from __future__ import annotations

import json
import re
import shutil
import uuid
from contextlib import contextmanager
from pathlib import Path

from filelock import FileLock

from .common import IntegrationError, atomic_json, digest, now, sha


class Store:
    def __init__(self, root: Path):
        self.root = root

    def directory(self, run_id: str):
        if not re.fullmatch(r"[a-f0-9]{32}", run_id):
            raise IntegrationError("invalid_run_id", "Ungültige Vorgangs-ID.")
        path = self.root / run_id
        if not (path / "state.json").exists():
            raise IntegrationError("run_not_found", "Vorgang nicht gefunden.")
        return path

    def create(self, organization: str, identifier: str, issue: str | None, environment: str):
        for value in (organization, identifier):
            if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]*", value) or ".." in value:
                raise IntegrationError(
                    "invalid_identifier", "Organisation/Identifier enthält ungültige Zeichen."
                )
        rid = uuid.uuid4().hex
        path = self.root / rid
        path.mkdir(parents=True)
        data = {
            "schema_version": 1,
            "id": rid,
            "created": now(),
            "organization": organization,
            "identifier": identifier,
            "issue": issue,
            "source_environment": environment,
            "files": {},
            "changes": {},
            "approvals": {},
            "checks": {},
            "deliveries": {},
            "events": [],
            "provenance": [],
            "baseline": None,
        }
        atomic_json(path / "state.json", data)
        return data

    @contextmanager
    def edit(self, run_id):
        path = self.directory(run_id)
        with FileLock(str(path / ".lock"), timeout=1):
            data = json.loads((path / "state.json").read_text())
            try:
                yield data
            finally:
                atomic_json(path / "state.json", data)

    def attach(self, data, source: Path, role: str):
        source = source.resolve()
        if not source.is_file():
            raise IntegrationError("file_missing", "Eingabedatei fehlt.", path=str(source))
        content_sha = sha(source)
        dest = self.directory(data["id"]) / "files" / (content_sha + source.suffix.lower())
        dest.parent.mkdir(exist_ok=True)
        if not dest.exists():
            shutil.copyfile(source, dest)
        record = {"path": str(dest), "sha256": content_sha, "original_name": source.name}
        data["files"][role] = record
        data["events"].append({"at": now(), "action": "attach", "role": role, "sha256": content_sha})
        return record

    def file(self, data, role) -> Path | None:
        record = data["files"].get(role)
        if not record:
            return None
        path = Path(record["path"])
        if not path.is_file() or sha(path) != record["sha256"]:
            raise IntegrationError(
                "artifact_changed", "Gespeichertes Artefakt wurde verändert; erneut aufnehmen.", role=role
            )
        return path

    def fingerprint(self, data, gate: str, context: str, office_sha: str):
        roles = ["data"] if gate == "data" else sorted(data["files"])
        files = {}
        for role in roles:
            path = self.file(data, role)
            if path:
                files[role] = sha(path)
        return digest(
            {
                "gate": gate,
                "organization": data["organization"],
                "identifier": data["identifier"],
                "issue": data["issue"],
                "files": files,
                "context": context,
                "offices": office_sha if gate != "data" else None,
                "changes": data["changes"] if gate != "data" else {},
                "provenance": data["provenance"] if gate != "data" else [],
                "transform": data.get("transform"),
                "baseline": data.get("baseline"),
            }
        )

    @staticmethod
    def require(data, gate, fingerprint):
        approval = data["approvals"].get(gate)
        if not approval or approval["fingerprint"] != fingerprint:
            raise IntegrationError(
                "approval_required",
                "Menschliche Freigabe fehlt oder ist nach Änderungen ungültig.",
                gate=gate,
            )
