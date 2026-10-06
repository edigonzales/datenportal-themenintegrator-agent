from pathlib import Path

import pytest
from conftest import FIXTURES, approve_all

from datenportal_integrator.common import IntegrationError, sha
from datenportal_integrator.workflow import Workflow


def test_approval_requires_successful_current_review(workflow, run):
    with pytest.raises(IntegrationError, match="Prüfung"):
        with workflow.store.edit(run) as state:
            fp = workflow.fingerprint(state, "data")
        workflow.approve(run, "data", fp, "OK")
    approve_all(workflow, run)
    assert workflow.status(run)["current_approvals"] == {"data": True, "metadata": True}


def test_changed_csv_invalidates_both_gates(workflow, run, tmp_path):
    approve_all(workflow, run)
    p = tmp_path / "new.csv"
    p.write_text("Jahrgang\n2000\n")
    workflow.attach(run, str(p), "data")
    assert workflow.status(run)["current_approvals"] == {"data": False, "metadata": False}
    with pytest.raises(IntegrationError, match="Freigabe"):
        workflow.deliver(run)


def test_changed_metadata_keeps_csv_gate(workflow, run, tmp_path):
    approve_all(workflow, run)
    p = tmp_path / "new.xtf"
    p.write_text((FIXTURES / "series.xtf").read_text().replace("Altersstruktur der Wohnbevölkerung", "Neu"))
    workflow.attach(run, str(p), "metadata")
    assert workflow.status(run)["current_approvals"] == {"data": True, "metadata": False}


def test_tampered_copy_cannot_be_approved(workflow, run):
    report = workflow.analyze(run)
    with workflow.store.edit(run) as state:
        Path(state["files"]["data"]["path"]).write_text("bad")
    with pytest.raises(IntegrationError, match="Artefakt"):
        workflow.approve(run, "data", report["fingerprint"], "OK")


def test_metadata_gate_needs_csv_gate(workflow, run):
    report = workflow.validate(run)
    assert report["valid"]
    with pytest.raises(IntegrationError, match="Freigabe"):
        workflow.approve(run, "metadata", report["fingerprint"], "OK")


def test_metadata_only(workflow):
    rid = workflow.start(
        "statistikdienst", "ch.so.bevoelkerung.altersstruktur", metadata_path=str(FIXTURES / "series.xtf")
    )["id"]
    assert workflow.analyze(rid)["skipped"]
    approve_all(workflow, rid)
    assert workflow.status(rid)["current_approvals"]["metadata"]


def test_series_requires_issue(workflow, tmp_path):
    data = tmp_path / "a.csv"
    data.write_text("Jahrgang\n2025\n")
    rid = workflow.start(
        "statistikdienst",
        "ch.so.bevoelkerung.altersstruktur",
        data_path=str(data),
        metadata_path=str(FIXTURES / "series.xtf"),
    )["id"]
    report = workflow.validate(rid)
    assert not report["valid"]
    assert "issue_required" in {e["code"] for e in report["errors"]}


def test_unknown_office(workflow, tmp_path):
    p = tmp_path / "unknown.xtf"
    p.write_text((FIXTURES / "series.xtf").read_text().replace("ch.so.afin", "ch.so.missing"))
    rid = workflow.start("statistikdienst", "ch.so.bevoelkerung.altersstruktur", metadata_path=str(p))["id"]
    assert "unknown_office" in {e["code"] for e in workflow.validate(rid)["errors"]}


def test_saved_state_survives_new_instance(workflow, run):
    approve_all(workflow, run)
    assert Workflow(workflow.settings).status(run)["current_approvals"]["metadata"]


def test_unexported_draft_cannot_validate_old_file(workflow, run):
    with workflow.store.edit(run) as state:
        state["draft"] = {"revision": 2}
        state["exported_revision"] = 1
    with pytest.raises(IntegrationError, match="exportieren"):
        workflow.validate(run)


def test_stage_does_not_modify_repo_and_detects_conflict(workflow, run, tmp_path):
    offices = workflow.settings.topics_repo / "shared/data/offices.xtf"
    old = sha(offices)
    candidate = tmp_path / "offices.xtf"
    candidate.write_text(offices.read_text().replace("Amt für Finanzen", "Finanzamt"))
    workflow.stage_change(run, "shared/data/offices.xtf", str(candidate))
    assert sha(offices) == old
    approve_all(workflow, run)
    offices.write_text(offices.read_text().replace("Amt für Finanzen", "Andere Änderung"))
    with pytest.raises(IntegrationError, match="zwischenzeitlich"):
        workflow.apply_local_changes(run)


def test_apply_changes_preserves_approval(workflow, run, tmp_path):
    p = tmp_path / "offices.xtf"
    p.write_text((FIXTURES / "offices.xtf").read_text().replace("Amt für Finanzen", "Finanzamt"))
    workflow.stage_change(run, "shared/data/offices.xtf", str(p))
    approve_all(workflow, run)
    workflow.apply_local_changes(run)
    assert workflow.status(run)["current_approvals"]["metadata"]


def test_outside_repo_changes_rejected(workflow, run):
    for relative in [
        "../escape.xtf",
        "shared/gradle/datenportal-publication.gradle",
        "unrelated/build.gradle",
    ]:
        with pytest.raises(IntegrationError):
            workflow.stage_change(run, relative, str(FIXTURES / "series.xtf"))


def test_html_escapes_untrusted_values(workflow, run, tmp_path):
    p = tmp_path / "html.xtf"
    p.write_text(
        (FIXTURES / "series.xtf")
        .read_text()
        .replace("Altersstruktur der Wohnbevölkerung", "&lt;script&gt;evil()&lt;/script&gt;")
    )
    workflow.attach(run, str(p), "metadata")
    report = workflow.validate(run)
    html = Path(report["review_path"]).read_text()
    assert "<script>evil()" not in html
    assert "Content-Security-Policy" in html


def test_provenance_invalidates_only_metadata(workflow, run):
    approve_all(workflow, run)
    workflow.provenance(run, [{"field": "title", "origin": "llm", "source": "Vorschlag"}])
    assert workflow.status(run)["current_approvals"] == {"data": True, "metadata": False}


def test_unexported_draft_cannot_reuse_existing_delivery_approval(workflow, run):
    approve_all(workflow, run)
    with workflow.store.edit(run) as state:
        state["draft"] = {"revision": 2}
        state["exported_revision"] = 1
    with pytest.raises(IntegrationError) as error:
        workflow.deliver(run)
    assert error.value.code == "draft_not_exported"


def test_running_delivery_checks_frozen_artifacts_after_new_attachment(workflow, run, tmp_path):
    from copy import deepcopy

    approve_all(workflow, run)
    with workflow.store.edit(run) as state:
        item = {
            "verification": {
                "sheet": deepcopy(state["checks"]["metadata"]["sheet"]),
                "data": deepcopy(state["files"]["data"]),
            }
        }
        old_path = state["files"]["data"]["path"]
    updated = tmp_path / "later.csv"
    updated.write_text("Jahrgang\n2030\n")
    workflow.attach(run, str(updated), "data")
    with workflow.store.edit(run) as state:
        _, data = workflow._verification_context(state, item)
    assert str(data) == old_path


def test_metadata_normalization_preserves_order_and_nonmanaged_content():
    from datenportal_integrator.xtf import business_fields

    a = {
        "issued": "2025-01-01",
        "attributes": [{"DatasetAttribute": {"name": "a"}}, {"DatasetAttribute": {"name": "b"}}],
    }
    b = {
        "issued": "2026-01-01",
        "modified": "2026-10-06",
        "attributes": {"DatasetAttribute": [{"name": "a"}, {"name": "b"}]},
    }
    assert business_fields(a) == business_fields(b)
    b["attributes"]["DatasetAttribute"].reverse()
    assert business_fields(a) != business_fields(b)
