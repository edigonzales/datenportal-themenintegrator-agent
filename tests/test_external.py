import json

import pytest
import respx
from conftest import approve_all

from datenportal_integrator.common import IntegrationError
from datenportal_integrator.jenkins import Jenkins, read_manifest
from datenportal_integrator.stack import Stack


@pytest.fixture
def auth(monkeypatch):
    monkeypatch.setenv("DATENPORTAL_JENKINS_USER", "test-user")
    monkeypatch.setenv("DATENPORTAL_JENKINS_TOKEN", "test-token")


@respx.mock
def test_only_404_allows_bootstrap(settings):
    route = respx.get(settings.environments["local"].manifest_url)
    route.respond(404)
    assert read_manifest(settings.environments["local"]) is None
    for status, body in [(403, "denied"), (500, "error"), (200, "not json"), (200, "{}")]:
        route.respond(status, text=body)
        with pytest.raises(IntegrationError):
            read_manifest(settings.environments["local"])


@respx.mock
def test_multipart_and_queue_identity(settings, auth, tmp_path):
    base = settings.environments["local"].jenkins_url
    respx.get(base + "/crumbIssuer/api/json").respond(
        200, json={"crumbRequestField": "Jenkins-Crumb", "crumb": "test-crumb"}
    )
    route = respx.post(base + "/gretl-datenportal/build").respond(
        302, headers={"Location": base + "/gretl-datenportal/run?job=generated-custom&queue=42"}
    )
    data = tmp_path / "a.csv"
    data.write_text("a\n1\n")
    with Jenkins(settings.environments["local"]) as j:
        result = j.submit("org", "ch.so.topic", "2025", data, None, "Themenintegrator run")
    assert result["job"] == "generated-custom" and result["queue"] == "42"
    body = route.calls[0].request.content
    assert b'name="SERIES_ID"' in body and b"2025" in body
    assert b'name="DATA_FILE"' in body
    assert b'name="METADATA_FILE"' not in body
    assert route.calls[0].request.headers["Jenkins-Crumb"] == "test-crumb"


@respx.mock
def test_no_credentials_sent_to_redirect(settings, auth):
    with Jenkins(settings.environments["local"]) as j:
        with pytest.raises(IntegrationError, match="Basisadresse"):
            j.get("https://another-host.example/secrets")
    assert not respx.calls


def test_stack_reuses_matching_instance(settings, monkeypatch):
    import datenportal_integrator.stack as module

    calls = []
    container = {
        "Mounts": [{"Destination": "/workspace/themenrepo", "Source": str(settings.topics_repo)}],
        "Config": {"Env": ["THEMEN_REPO_MODE=working-tree", "SECRET=never-return-this"]},
        "State": {"Running": True, "Health": {"Status": "healthy"}},
    }

    def execute(args, *a, **kw):
        calls.append(args)
        if args[:2] == ["docker", "inspect"]:
            return json.dumps([container])
        if args[:3] == ["docker", "ps", "-aq"]:
            return "abc\n"
        return json.dumps(
            [
                {"Service": s, "State": "running", "Health": "healthy"}
                for s in ("jenkins", "garage", "downloads")
            ]
        )

    monkeypatch.setattr(module, "command", execute)
    result = Stack(settings).ensure()
    assert result["reused"]
    assert "SECRET" not in json.dumps(result)
    assert not any("scripts/up.sh" in c for c in calls)
    container["Mounts"][0]["Source"] = "/other/checkout"
    with pytest.raises(IntegrationError, match="anderen Themenbestand"):
        Stack(settings).ensure()


def test_bootstrap_never_reinitializes_existing(settings, monkeypatch):
    import datenportal_integrator.stack as module

    monkeypatch.setattr(module, "read_manifest", lambda _: {"releaseId": "existing"})
    monkeypatch.setattr(Stack, "ensure_services", lambda *a: None)
    assert not Stack(settings).bootstrap(settings.environments["local"])["initialized"]


def test_saved_submission_resumes_without_duplicate_upload(workflow, run, monkeypatch):
    import datenportal_integrator.workflow as module

    approve_all(workflow, run)
    counts = {"seed": 0, "submit": 0, "status": 0}

    class FakeStack:
        def __init__(self, *_):
            pass

        def ensure(self):
            return {"ready": True}

        def bootstrap(self, *_):
            return {"initialized": False}

    class FakeJenkins:
        def __init__(self, *_):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_):
            pass

        def seed(self):
            counts["seed"] += 1
            return {"queue_url": "q"}

        def seed_status(self, *_):
            return {"complete": True, "result": "SUCCESS"}

        def submit(self, *_):
            counts["submit"] += 1
            return {"job": "j", "queue": "9"}

        def status(self, *_):
            counts["status"] += 1
            return {"complete": True, "consoleUrl": "url"}

        def report(self, *_):
            return {"publication": "accepted", "reload": "succeeded"}

        def verify(self, *_):
            return {"verified": True, "visible": True, "portal_url": "http://localhost:8081/datasets/test"}

    monkeypatch.setattr(module, "Stack", FakeStack)
    monkeypatch.setattr(module, "Jenkins", FakeJenkins)
    assert workflow.deliver(run)["phase"] == "seeding"
    assert workflow.deliver(run)["phase"] == "running"
    assert workflow.deliver(run)["phase"] == "complete"
    assert workflow.deliver(run)["phase"] == "complete"
    assert counts == {"seed": 1, "submit": 1, "status": 1}


def test_unknown_submission_is_never_retried(workflow, run):
    approve_all(workflow, run)
    with workflow.store.edit(run) as state:
        state["deliveries"]["local"] = {"phase": "delivery_submitting"}
    with pytest.raises(IntegrationError, match="niemals blind"):
        workflow.deliver(run)
    with pytest.raises(IntegrationError, match="fehlgeschlagene"):
        workflow.retry_delivery(run, "local")


def test_accepted_publication_cannot_be_retried(workflow, run):
    approve_all(workflow, run)
    with workflow.store.edit(run) as state:
        state["deliveries"]["local"] = {
            "phase": "failed",
            "report": {"publication": "accepted", "reload": "failed"},
        }
    with pytest.raises(IntegrationError, match="nicht publizierte"):
        workflow.retry_delivery(run, "local")


def test_new_target_plan_invalidates_old_approval(workflow, run, monkeypatch):
    import datenportal_integrator.workflow as module

    approve_all(workflow, run)
    with workflow.store.edit(run) as state:
        state["deliveries"]["local"] = {
            "verified": True,
            "fingerprint": workflow.fingerprint(state, "metadata"),
        }
    monkeypatch.setattr(module, "accepted_sheet", lambda *_: ({"releaseId": "a"}, b"first"))
    plan = workflow.publication_plan(run, "local")
    workflow.approve(run, "publish:local", plan["fingerprint"], "TEST publish")
    monkeypatch.setattr(module, "accepted_sheet", lambda *_: ({"releaseId": "b"}, b"changed"))
    workflow.publication_plan(run, "local")
    assert not workflow.status(run)["current_approvals"]["publish:local"]


@respx.mock
def test_series_downloads_and_repaired_reload(settings, auth, tmp_path):
    env = settings.environments["local"]
    manifest = {"releaseId": "test", "datasheets": "sheets.xtf", "catalog": "catalog.xtf"}
    url = env.portal_url + "/series/ch.so.test/issues/ch.so.test_2025"
    catalog = b"""<transfer><DatasetSeries><identifier>ch.so.test</identifier><issues><DatasetIssue><identifier>ch.so.test_2025</identifier><distributions>
    <Distribution><format>csv</format><downloadURL>http://localhost:8081/files/data.csv</downloadURL></Distribution>
    <Distribution><format>xlsx</format><downloadURL>http://localhost:8081/files/data.xlsx</downloadURL></Distribution>
    <Distribution><format>parquet</format><downloadURL>http://localhost:8081/files/data.parquet</downloadURL></Distribution>
    </distributions></DatasetIssue></issues></DatasetSeries></transfer>"""
    csv = tmp_path / "data.csv"
    csv.write_text("a\n1\n")
    respx.get(env.manifest_url).respond(200, json=manifest)
    accepted = respx.get("http://localhost:8081/ch.so.daten/sheets.xtf").respond(
        200,
        text='<transfer xmlns:d="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523"><d:DatasetSeries><d:identifier>ch.so.test</d:identifier><d:modified>2026-10-06</d:modified></d:DatasetSeries></transfer>',
    )
    respx.get("http://localhost:8081/ch.so.daten/catalog.xtf").respond(200, content=catalog)
    active = respx.get(env.portal_url + "/catalog/published-catalog.xtf").respond(
        200, content=b"old catalogue"
    )
    respx.get(url).respond(200, text="<h1>Series issue</h1>")
    for fmt in ["csv", "xlsx", "parquet"]:
        respx.get("http://localhost:8081/files/data." + fmt).respond(
            200, content=csv.read_bytes() if fmt == "csv" else b"test data"
        )
    report = {
        "publication": "accepted",
        "reload": "failed",
        "releaseId": "test",
        "opendata": {"status": "accepted"},
    }
    sheet = {
        "identifier": "ch.so.test",
        "selected_issue": "ch.so.test_2025",
        "kind": "series",
        "publication_status": "published",
        "fields": {"identifier": "ch.so.test", "modified": "2025-01-01"},
    }
    with Jenkins(env) as j:
        assert not j.verify(report, sheet, csv).get("verified")
        active.respond(200, content=catalog)
        result = j.verify(report, sheet, csv)
        accepted.respond(
            200,
            text='<transfer xmlns:d="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523"><d:DatasetSeries><d:identifier>ch.so.test</d:identifier><d:title>Unapproved change</d:title></d:DatasetSeries></transfer>',
        )
        drift = j.verify(report, sheet, csv)
        assert not drift.get("verified")
        assert "Fachinhalt" in drift["message"]
    assert result["verified"] and result["portal_url"] == url
    assert len(result["downloads"]) == 3
    assert not any(c.request.method == "POST" for c in respx.calls)


def test_local_label_cannot_enable_remote_publication(settings):
    settings.environments["local"].jenkins_url = "https://prod.example.org/jenkins"
    with pytest.raises(IntegrationError, match="Loopback"):
        settings.environment("local")


def test_empty_stack_initialization_starts_portal_only_after_manifest(settings, monkeypatch, auth):
    from unittest.mock import Mock

    import datenportal_integrator.stack as module

    manifests = iter([None, None, {"releaseId": "first"}])
    monkeypatch.setattr(module, "read_manifest", lambda _: next(manifests))
    calls = []

    class FakeJenkins:
        def __init__(self, *_):
            pass

        def __enter__(self):
            return self

        def __exit__(self, *_):
            pass

        def post(self, path):
            calls.append(path)

        def get(self, path):
            return Mock(json=lambda: {"quietingDown": False} if path == "api/json" else {"computer": []})

    monkeypatch.setattr(module, "Jenkins", FakeJenkins)
    stack = Stack(settings)
    monkeypatch.setattr(stack, "compose", lambda *a, **kw: calls.append((a, kw)))
    monkeypatch.setattr(stack, "ensure_services", lambda services: calls.append(services))
    assert stack.bootstrap(settings.environments["local"])["initialized"]
    assert calls[0] == "quietDown"
    assert "initializePublication" in calls[1][1]["input"]
    assert calls[-2:] == ["cancelQuietDown", {"sodata"}]


def test_partial_stack_starts_missing_services_without_recreating(settings, monkeypatch):
    stack = Stack(settings)
    calls = []

    def compose(*args, **kw):
        calls.append(args)
        return json.dumps([{"Service": "jenkins", "State": "running", "Health": "healthy"}])

    monkeypatch.setattr(stack, "compose", compose)
    stack.ensure_services({"jenkins", "downloads"})
    assert calls[-1] == ("up", "-d", "--no-recreate", "--wait", "--wait-timeout", "120", "downloads")


def test_starting_stack_does_not_report_ready(settings, monkeypatch):
    stack = Stack(settings)
    monkeypatch.setattr(
        stack,
        "compose",
        lambda *a: json.dumps([{"Service": "jenkins", "State": "running", "Health": "starting"}]),
    )
    with pytest.raises(IntegrationError) as error:
        stack.ensure_services({"jenkins"})
    assert error.value.code == "stack_unhealthy"
