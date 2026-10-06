import os
from pathlib import Path

import pytest
from conftest import FIXTURES

from datenportal_integrator.datasheet import DatasheetClient
from datenportal_integrator.validation import Validator


@pytest.mark.parametrize("fixture", ["dataset", "series"])
def test_real_ilivalidator_positive_and_unknown_office(settings, tmp_path, fixture):
    if not Path(settings.validator_command[-1]).exists():
        pytest.skip("Run scripts/setup-tools.py for real ilivalidator checks")
    valid = Validator(settings).validate(
        FIXTURES / f"{fixture}.xtf", FIXTURES / "offices.xtf", tmp_path / "valid"
    )
    assert valid["valid"], valid
    original = (FIXTURES / f"{fixture}.xtf").read_text()
    import re

    invalid = re.sub(r"(<(?:\w+:)?creatorRef>)[^<]+", r"\g<1>ch.so.unknown", original)
    p = tmp_path / "invalid.xtf"
    p.write_text(invalid)
    report = Validator(settings).validate(p, FIXTURES / "offices.xtf", tmp_path / "invalid")
    assert not report["valid"]
    assert "existence constraint" in (tmp_path / "invalid/datasheet.log").read_text().lower()


async def test_real_java_roundtrip_and_restore():
    if not os.getenv("TEST_DATASHEET_MCP"):
        pytest.skip("Set TEST_DATASHEET_MCP for live Java MCP test")
    client = DatasheetClient(os.environ["TEST_DATASHEET_MCP"])
    xml = (FIXTURES / "series.xtf").read_text()
    imported = await client.call("import_xtf", {"xml": xml})
    updated = await client.call(
        "update_metadata",
        {
            "draft_id": imported["draft_id"],
            "expected_revision": imported["revision"],
            "values": {"title": "Test & <Text>"},
        },
    )
    await client.call(
        "discard_datasheet", {"draft_id": updated["draft_id"], "expected_revision": updated["revision"]}
    )
    restored = await client.restore(updated, xml)
    assert restored["object_id"] == imported["object_id"]
    assert restored["basket_id"] == imported["basket_id"]
    assert restored["data"]["title"] == "Test & <Text>"
    assert restored["data"]["issues"][0]["identifier"] == imported["data"]["issues"][0]["identifier"]
    exported = await client.call(
        "export_xtf",
        {"draft_id": restored["draft_id"], "expected_revision": restored["revision"], "include_xml": True},
    )
    assert "Test &amp; &lt;Text&gt;" in exported["xml"]
    await client.call(
        "discard_datasheet", {"draft_id": restored["draft_id"], "expected_revision": restored["revision"]}
    )


async def test_new_topic_via_java_mcp_with_both_review_gates(settings, tmp_path):
    if not os.getenv("TEST_DATASHEET_MCP"):
        pytest.skip("Set TEST_DATASHEET_MCP for live Java MCP test")
    from datenportal_integrator.workflow import Workflow

    w = Workflow(settings.model_copy(update={"datasheet_mcp_url": os.environ["TEST_DATASHEET_MCP"]}))
    csv = tmp_path / "input.csv"
    csv.write_text("kennung;wert\n001;12.5\n002;0\n")
    rid = w.start("agi", "ch.so.integrator.test", data_path=str(csv))["id"]
    analysis = w.analyze(rid)
    w.approve(rid, "data", analysis["fingerprint"], "AUTOMATED TEST FIXTURE")
    await w.metadata(
        rid,
        "create_datasheet",
        {
            "kind": "dataset",
            "values": {
                "identifier": "ch.so.integrator.test",
                "title": "Synthetische Testdaten",
                "description": "Ausschliesslich automatisierte Abnahme; kein echtes Fachthema.",
                "creator_ref": "ch.so.agi",
                "contact_point": {"email": "mailto:test@example.org"},
                "access_level": "open",
                "publication_status": "published",
                "themes": ["Verwaltung"],
                "modified": "2026-10-05",
                "issued": "2026-10-05",
            },
        },
    )
    for name, kind in [("kennung", "TEXT"), ("wert", "DECIMAL")]:
        await w.metadata(
            rid,
            "upsert_attribute",
            {
                "values": {
                    "name": name,
                    "data_type": kind,
                    "mandatory": True,
                    "description": "Synthetischer Testwert",
                }
            },
        )
    exported = await w.metadata(rid, "export_xtf")
    w.stage_change(rid, "agi/ch.so.integrator.test/datenblatt.xtf", exported["artifact"]["path"])
    report = w.validate(rid)
    assert report["valid"], report
    w.approve(rid, "metadata", report["fingerprint"], "AUTOMATED TEST FIXTURE")
    w.apply_local_changes(rid)
    assert (settings.topics_repo / "agi/ch.so.integrator.test/datenblatt.xtf").read_bytes() == Path(
        exported["artifact"]["path"]
    ).read_bytes()
    assert w.status(rid)["current_approvals"]["metadata"]


@pytest.mark.parametrize("kind", ["dataset", "series"])
async def test_new_identity_survives_restore_before_first_export(kind):
    if not os.getenv("TEST_DATASHEET_MCP"):
        pytest.skip("Set TEST_DATASHEET_MCP for live Java MCP test")
    client = DatasheetClient(os.environ["TEST_DATASHEET_MCP"])
    draft, shell = await client.create(kind, {"identifier": "ch.so.identity.test"})
    assert draft["object_id"].startswith("o") and draft["basket_id"].startswith("b")
    await client.call(
        "discard_datasheet", {"draft_id": draft["draft_id"], "expected_revision": draft["revision"]}
    )
    restored = await client.restore(draft, shell)
    assert restored["object_id"] == draft["object_id"]
    assert restored["kind"] == kind
    assert restored["data"]["identifier"] == "ch.so.identity.test"
    await client.call(
        "discard_datasheet", {"draft_id": restored["draft_id"], "expected_revision": restored["revision"]}
    )
