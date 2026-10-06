import shutil
from pathlib import Path

import pytest

from datenportal_integrator.config import Environment, Settings
from datenportal_integrator.workflow import Workflow

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "tests/fixtures"


@pytest.fixture
def settings(tmp_path):
    repo = tmp_path / "topics-repo"
    (repo / "shared/data").mkdir(parents=True)
    shutil.copy(FIXTURES / "offices.xtf", repo / "shared/data/offices.xtf")
    return Settings(
        root=ROOT,
        topics_repo=repo,
        stack_repo=tmp_path / "stack",
        state_dir=tmp_path / "runs",
        validator_command=[
            "java",
            "-jar",
            str(ROOT / ".datenportal-integrator/tools/ilivalidator/ilivalidator-1.15.0.jar"),
        ],
        model_dirs=[str(FIXTURES / "models")],
        environments={
            "local": Environment(
                kind="local",
                enabled=True,
                repository_mode="working-tree",
                jenkins_url="http://localhost:8081/jenkins",
                portal_url="http://localhost:8081",
                manifest_url="http://localhost:8081/ch.so.daten/current.json",
            )
        },
    )


@pytest.fixture
def workflow(settings, monkeypatch):
    w = Workflow(settings)
    monkeypatch.setattr(w.validator, "validate", lambda *args: {"valid": True, "checks": []})
    return w


@pytest.fixture
def run(workflow, tmp_path):
    data = tmp_path / "data.csv"
    data.write_text("Jahrgang\n1920\n2025\n")
    return workflow.start(
        "statistikdienst",
        "ch.so.bevoelkerung.altersstruktur",
        "2025",
        data_path=str(data),
        metadata_path=str(FIXTURES / "series.xtf"),
    )["id"]


def approve_all(w, rid):
    result = w.analyze(rid)
    if not result.get("skipped"):
        w.approve(rid, "data", result["fingerprint"], "TEST FIXTURE: CSV freigegeben")
    result = w.validate(rid)
    assert result["valid"], result
    w.approve(rid, "metadata", result["fingerprint"], "TEST FIXTURE: Metadaten freigegeben")
