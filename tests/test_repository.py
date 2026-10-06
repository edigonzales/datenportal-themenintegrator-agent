"""Exercise Git with temporary local remotes; only the GitHub API is simulated."""

import json

import pytest

from datenportal_integrator.common import IntegrationError, command, sha
from datenportal_integrator.repository import prepare_pr, require_merged


@pytest.fixture
def git_case(settings, tmp_path, monkeypatch):
    for key, value in {
        "GIT_AUTHOR_NAME": "Integrator Test",
        "GIT_COMMITTER_NAME": "Integrator Test",
        "GIT_AUTHOR_EMAIL": "test@example.org",
        "GIT_COMMITTER_EMAIL": "test@example.org",
    }.items():
        monkeypatch.setenv(key, value)
    repo = settings.topics_repo
    command(["git", "init", "-b", "main"], repo)
    command(["git", "add", "."], repo)
    command(["git", "commit", "-m", "Test fixture"], repo)
    remote = tmp_path / "remote.git"
    command(["git", "clone", "--bare", str(repo), str(remote)])
    command(["git", "remote", "add", "origin", str(remote)], repo)
    proposed = tmp_path / "sheet.xtf"
    proposed.write_text("synthetic proposed sheet\n")
    relative = "agi/ch.so.integrator.test/datenblatt.xtf"
    run = {
        "id": "a" * 32,
        "identifier": "ch.so.integrator.test",
        "organization": "agi",
        "changes": {relative: {"path": str(proposed), "sha256": sha(proposed), "before_sha256": None}},
        "approvals": {"metadata": {"fingerprint": "TEST FIXTURE"}},
    }
    env = settings.environments["local"]
    directory = tmp_path / "run"
    directory.mkdir()
    return run, env, directory


def test_pr_isolated_checkout_and_exact_merged_bytes(settings, git_case, monkeypatch):
    import datenportal_integrator.repository as module

    run, env, directory = git_case
    status = {"state": "OPEN", "baseRefName": "main", "headRefOid": None, "mergeCommit": None}
    gh_calls = []

    def execute(args, *a, **kw):
        if args[0] == "gh":
            gh_calls.append(args)
            return "https://github.com/example/test/pull/1" if args[2] == "create" else json.dumps(status)
        return command(args, *a, **kw)

    monkeypatch.setattr(module, "command", execute)
    run["pull_request"] = prepare_pr(settings, run, env, directory)
    pr = run["pull_request"]
    assert pr["branch"].startswith("codex/")
    assert "--body-file" in gh_calls[0]
    relative = next(iter(run["changes"]))
    assert not (settings.topics_repo / relative).exists()
    with pytest.raises(IntegrationError) as error:
        require_merged(settings, run, env)
    assert error.value.code == "human_merge_required"
    # Simulate the human merge in the disposable fixture remote.
    clone = pr["checkout"]
    command(["git", "push", "origin", "HEAD:main"], clone)
    status.update(state="MERGED", headRefOid=pr["head"])
    require_merged(settings, run, env)
    status["headRefOid"] = "changed"
    with pytest.raises(IntegrationError) as error:
        require_merged(settings, run, env)
    assert error.value.code == "pr_changed"
    status["headRefOid"] = pr["head"]
    from pathlib import Path

    (Path(clone) / relative).write_text("subsequent edit\n")
    command(["git", "add", "."], clone)
    command(["git", "commit", "-m", "Concurrent test edit"], clone)
    command(["git", "push", "origin", "HEAD:main"], clone)
    with pytest.raises(IntegrationError) as error:
        require_merged(settings, run, env)
    assert error.value.code == "merged_content_changed"
