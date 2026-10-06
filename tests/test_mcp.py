import json
import sys

from conftest import ROOT
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from openpyxl import Workbook

from datenportal_integrator.reports import workbook_cells


async def test_real_stdio_transport_stops_before_human_approval(settings, tmp_path):
    config = tmp_path / "local.toml"
    config.write_text(f"""root = {json.dumps(str(ROOT))}
topics_repo = {json.dumps(str(settings.topics_repo))}
stack_repo = {json.dumps(str(settings.stack_repo))}
state_dir = {json.dumps(str(settings.state_dir))}
""")
    csv = tmp_path / "data.csv"
    csv.write_text("id;wert\n001;2.5\n")
    server = StdioServerParameters(
        command=sys.executable,
        args=["-m", "datenportal_integrator.cli", "--config", str(config), "serve"],
        cwd=str(ROOT),
    )
    async with stdio_client(server) as (reader, writer):
        async with ClientSession(reader, writer) as session:
            await session.initialize()
            tools = await session.list_tools()
            assert {"start", "office", "metadata", "approve", "deliver"} <= {t.name for t in tools.tools}
            result = await session.call_tool(
                "start", {"organization": "agi", "identifier": "ch.so.test", "data_path": str(csv)}
            )
            assert not result.isError
            run = json.loads(result.content[0].text)["id"]
            report = await session.call_tool("analyze", {"run_id": run})
            assert json.loads(report.content[0].text)["valid"]
            result = await session.call_tool(
                "metadata", {"run_id": run, "operation": "create_datasheet", "arguments": {"kind": "dataset"}}
            )
            assert result.isError
            assert "approval_required" in result.content[0].text


def test_xlsx_source_coordinates_formula_cache_and_paging(tmp_path):
    path = tmp_path / "source.xlsx"
    book = Workbook()
    sheet = book.active
    sheet.title = "Metadaten"
    sheet.append(["Titel", "Ein Titel"])
    sheet.append(["Formel", "=1+1"])
    book.save(path)
    assert workbook_cells(path)["sheets"][0]["name"] == "Metadaten"
    page = workbook_cells(path, "Metadaten", 0, 1)
    assert page["rows"][0][1]["cell"] == "B1" and page["next_offset"] == 1
    page = workbook_cells(path, "Metadaten", 1, 1)
    assert page["rows"][0][1]["missing_cached_result"]
