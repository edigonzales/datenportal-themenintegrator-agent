import shutil
from pathlib import Path

import pytest
from conftest import ROOT

from datenportal_integrator.common import IntegrationError
from datenportal_integrator.workflow import Workflow


def test_transform_runs_tests_preserves_original_and_produces_supplier_spec(settings, tmp_path):
    root = tmp_path / "integrator"
    root.mkdir()
    shutil.copytree(ROOT / "config", root / "config", ignore=shutil.ignore_patterns("local.toml"))
    topic = root / "topics/org/ch.so.test"
    topic.mkdir(parents=True)
    script = topic / "convert.py"
    script.write_text("""import csv,sys
from pathlib import Path

def convert(source, target):
    with open(source, newline="", encoding="utf-8") as src, open(target,"w",newline="",encoding="utf-8") as dst:
        reader=csv.reader(src,delimiter=",")
        writer=csv.writer(dst,delimiter=";",lineterminator="\\n")
        for row in reader: writer.writerow(row)
if __name__=="__main__": convert(sys.argv[1],sys.argv[2])
""")
    tests = topic / "test_convert.py"
    tests.write_text("""from convert import convert

def test_values(tmp_path):
    source=tmp_path/"in.csv"; target=tmp_path/"out.csv"
    source.write_text("id,value\\n001,12.5\\n002,0\\n")
    convert(source,target)
    assert target.read_text()=="id;value\\n001;12.5\\n002;0\\n"
""")
    w = Workflow(settings.model_copy(update={"root": root}))
    source = tmp_path / "source.csv"
    source.write_text("id,value\n001,12.5\n002,0\n")
    before = source.read_bytes()
    rid = w.start("org", "ch.so.test", data_path=str(source))["id"]
    result = w.transform(
        rid,
        "org/ch.so.test/convert.py",
        "org/ch.so.test/test_convert.py",
        "supplier",
        "Bitte künftig Semikolon verwenden. Kennungen mit führenden Nullen erhalten.",
    )
    assert not result["before"]["valid"] and result["after"]["valid"]
    assert source.read_bytes() == before
    assert Path(result["supplier_instructions"]).exists()
    tests.write_text("def test_failure(): assert False")
    with pytest.raises(IntegrationError, match="Konvertertests"):
        w.transform(
            rid, "org/ch.so.test/convert.py", "org/ch.so.test/test_convert.py", "recurring", "Vorgabe"
        )
