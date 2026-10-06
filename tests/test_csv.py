import json

import pytest
from conftest import ROOT

from datenportal_integrator.csvcheck import accepts, inspect_csv

RULES = json.loads((ROOT / "config/rules.json").read_text())


@pytest.mark.parametrize(
    "body,code",
    [
        ("a;A\n1;2\n", "duplicate_header"),
        ("2025;a\n1;2\n", "invalid_header"),
        ("a\n\n", "empty_row"),
        ("a;b\n1;2;3\n", "row_width"),
        ('a\n"unclosed\n', "csv_format"),
        ("a,b\n1,2\n", "invalid_header"),
        ("a\n", "no_observations"),
    ],
)
def test_format_rejections(tmp_path, body, code):
    p = tmp_path / "test.csv"
    p.write_text(body)
    report = inspect_csv(p, RULES)
    assert not report["valid"]
    assert code in {e["code"] for e in report["errors"]}


def test_quoting_zeroes_and_missing(tmp_path):
    p = tmp_path / "test.csv"
    p.write_text('kennung;text;wert\n001;"Französisch; Deutsch";2.5\n002;"Messstelle ""Nord""";\n')
    report = inspect_csv(p, RULES)
    assert report["valid"]
    assert report["columns"][0]["suggested_type"] == "TEXT"
    assert report["columns"][1]["examples"] == ["Französisch; Deutsch", 'Messstelle "Nord"']
    assert report["columns"][2]["missing"] == 1


def test_all_rows_and_capped_error_report(tmp_path):
    p = tmp_path / "test.csv"
    p.write_text("a;b\n" + "1;2\n" * 10000 + "x\n" * 110)
    report = inspect_csv(p, RULES)
    assert report["rows"] == 10110
    assert report["error_count"] == 110 and len(report["errors"]) == 100


@pytest.mark.parametrize(
    "kind,value,ok",
    [
        ("INTEGER", "2.5", False),
        ("INTEGER", str(2**63), False),
        ("DECIMAL", "1,2", False),
        ("DECIMAL", "1'000", False),
        ("DECIMAL", "NaN", False),
        ("BOOLEAN", "true", True),
        ("BOOLEAN", "yes", False),
        ("DATE", "2025-02-30", False),
        ("DATE", "2025-02-28", True),
        ("DATETIME", "2025-07-01T12:00:00+02:00", False),
        ("DATETIME", "2025-07-01T12:00:00+01:00", True),
    ],
)
def test_typed_values(kind, value, ok):
    assert accepts(value, kind) == ok


def test_attribute_order_mandatory_and_type(tmp_path):
    p = tmp_path / "test.csv"
    p.write_text("id;wert\n;x\n")
    attrs = [{"name": "id", "data_type": "TEXT", "mandatory": True}, {"name": "wert", "data_type": "DECIMAL"}]
    report = inspect_csv(p, RULES, attrs)
    assert {e["code"] for e in report["errors"]} == {"mandatory", "datatype"}
    assert not inspect_csv(p, RULES, attrs[::-1])["valid"]
