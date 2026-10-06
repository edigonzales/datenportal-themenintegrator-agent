from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .common import IntegrationError, command, inside, sha


def prepare_pr(settings, run, env, directory):
    if not run["changes"]:
        return {"required": False}
    if run.get("pull_request"):
        return run["pull_request"]
    clone = directory / "pr-checkout"
    remote = command(["git", "remote", "get-url", env.git_remote], settings.topics_repo).strip()
    if clone.exists():
        raise IntegrationError(
            "pr_checkout_exists",
            "PR-Arbeitskopie existiert bereits; ihren Zustand vor erneutem Push prüfen.",
            path=str(clone),
        )
    command(["git", "clone", "--single-branch", "--branch", env.git_branch, remote, str(clone)], timeout=300)
    branch = "codex/themenintegration-" + run["id"][:12]
    command(["git", "switch", "-c", branch], clone)
    for relative, record in run["changes"].items():
        target = inside(clone, relative)
        current = sha(target) if target.exists() else None
        if current not in {record["before_sha256"], record["sha256"]}:
            raise IntegrationError(
                "pr_base_conflict",
                "Zielbranch unterscheidet sich vom geprüften Ausgangsstand.",
                path=relative,
            )
        source = Path(record["path"])
        if sha(source) != record["sha256"]:
            raise IntegrationError("change_modified", "Vorbereitete Datei wurde verändert.")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source.read_bytes())
    command(["git", "add", "--", *run["changes"]], clone)
    command(["git", "diff", "--cached", "--check"], clone)
    if not command(["git", "diff", "--cached", "--name-only"], clone).strip():
        return {"required": False, "reason": "already_on_target"}
    command(["git", "commit", "-m", f"Themenintegration: {run['identifier']}"], clone)
    head = command(["git", "rev-parse", "HEAD"], clone).strip()
    command(["git", "push", "-u", "origin", branch], clone, timeout=300)
    body = directory / "pull-request-body.md"
    body.write_text(f"""Integriert das geprüfte Thema `{run["identifier"]}` für `{run["organization"]}`.

Validierung: CSV-/Metadatenabgleich, ilivalidator einschliesslich Office-Existenzprüfung und lokaler Jenkins-/Portaltest.

Prüfstand: `{run["approvals"]["metadata"]["fingerprint"]}`.

Nach menschlicher Übernahme setzt der Integrator Seed und Lieferung für die ausdrücklich freigegebene Umgebung fort. Dieser PR publiziert selbst keine Dateien.
""")
    url = command(
        [
            "gh",
            "pr",
            "create",
            "--base",
            env.git_branch,
            "--head",
            branch,
            "--title",
            f"Themenintegration: {run['identifier']}",
            "--body-file",
            str(body),
        ],
        clone,
    ).strip()
    return {
        "required": True,
        "url": url,
        "head": head,
        "branch": branch,
        "checkout": str(clone),
        "base": env.git_branch,
    }


def require_merged(settings, run, env):
    if not run["changes"]:
        return
    pr = run.get("pull_request")
    if not pr:
        raise IntegrationError(
            "pull_request_required",
            "Repository-Änderungen benötigen einen vorbereiteten und menschlich übernommenen PR.",
        )
    if not pr.get("required"):
        # Still verify the target files below, even if they were already present.
        clone = settings.topics_repo
        remote = env.git_remote
    else:
        clone = Path(pr["checkout"])
        remote = "origin"
        status = json.loads(
            command(
                ["gh", "pr", "view", pr["url"], "--json", "state,baseRefName,headRefOid,mergeCommit"], clone
            )
        )
        if status["state"] != "MERGED" or status["baseRefName"] != env.git_branch:
            raise IntegrationError(
                "human_merge_required",
                "PR muss durch einen Menschen auf den vorgesehenen Zielbranch übernommen werden.",
                url=pr["url"],
            )
        if status["headRefOid"] != pr["head"]:
            raise IntegrationError(
                "pr_changed", "PR wurde seit dem geprüften Stand verändert; erneut prüfen."
            )
    command(["git", "fetch", remote, env.git_branch], clone)
    for relative, change in run["changes"].items():
        # git show text encoding/newline normalization would invalidate exact-byte comparisons.
        import subprocess

        content = subprocess.run(
            ["git", "show", f"{remote}/{env.git_branch}:{relative}"],
            cwd=clone,
            capture_output=True,
            check=False,
        )
        if content.returncode or hashlib.sha256(content.stdout).hexdigest() != change["sha256"]:
            raise IntegrationError(
                "merged_content_changed",
                "Dateistand des Zielbranches entspricht nicht der Freigabe.",
                path=relative,
            )
