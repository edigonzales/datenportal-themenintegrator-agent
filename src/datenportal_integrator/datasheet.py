from __future__ import annotations

import json
from copy import deepcopy
from uuid import uuid4

import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

from .common import IntegrationError


class DatasheetClient:
    def __init__(self, url):
        self.url = url

    async def create(self, kind: str, values: dict | None = None) -> tuple[dict, str]:
        """Bootstrap transfer identity via import; Java owns all metadata and exports.

        The current Java create tool generates UUID OIDs that can start with a
        digit, which XTF rejects. Its public importer preserves valid OIDs.
        """
        tags = {"dataset": "Dataset", "series": "DatasetSeries"}
        if kind not in tags:
            raise IntegrationError("invalid_kind", "kind muss dataset oder series sein.")
        shell = (
            '<transfer xmlns="http://www.interlis.ch/xtf/2.4/INTERLIS" '
            'xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" '
            'xmlns:d="http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523">'
            "<headersection><models><model>SO_AGI_DataCatalog_Datasheet_20260523</model>"
            "<model>SO_AGI_DataCatalog_Base_20260529</model>"
            "</models><sender>datenportal-integrator</sender></headersection>"
            f'<datasection><d:Metadata i:bid="b{uuid4().hex}">'
            f'<d:{tags[kind]} i:tid="o{uuid4().hex}"/>'
            "</d:Metadata></datasection></transfer>"
        )
        current = await self.call("import_xtf", {"xml": shell})
        if values:
            current = await self.call(
                "update_metadata",
                {"draft_id": current["draft_id"], "expected_revision": current["revision"], "values": values},
            )
        return current, shell

    async def call(self, name: str, arguments: dict) -> dict:
        async with httpx.AsyncClient(timeout=120) as http:
            async with streamable_http_client(self.url, http_client=http) as (read, write, _):
                async with ClientSession(read, write) as session:
                    await session.initialize()
                    result = await session.call_tool(name, arguments)
        values = result.structuredContent
        if values is None:
            messages = [c.text for c in result.content if c.type == "text"]
            try:
                values = json.loads("\n".join(messages))
            except ValueError:
                values = {"message": "\n".join(messages)}
        if result.isError:
            raise IntegrationError(
                values.get("code", "datasheet_error"), "Datenblatt-MCP meldet einen Fehler.", response=values
            )
        return values

    async def restore(self, snapshot: dict, base_xml: str | None = None) -> dict:
        """Reconstruct via public MCP tools; no second XML writer. Keep TID/BID from base XTF."""
        target = deepcopy(snapshot["data"])
        if base_xml:
            current = await self.call("import_xtf", {"xml": base_xml})
        else:
            current, _ = await self.create(snapshot["kind"])

        async def change(name, args):
            nonlocal current
            current = await self.call(
                name, {"draft_id": current["draft_id"], "expected_revision": current["revision"], **args}
            )

        # Remove list entries explicitly; internal IDs are not stable across import.
        for issue in list(current["data"].get("issues", [])):
            await change("remove_issue", {"issue_id": issue["issue_id"]})
        for attribute in list(current["data"].get("attributes", [])):
            await change("remove_attribute", {"attribute_id": attribute["attribute_id"]})
        lists = {"attributes", "issues"}
        metadata = {k: None for k in current["data"] if k not in lists and k not in target}
        metadata.update({k: v for k, v in target.items() if k not in lists})
        # contact_point patches are partial; clear first to preserve explicit removals.
        if "contact_point" in metadata:
            await change("update_metadata", {"values": {"contact_point": None}})
        await change("update_metadata", {"values": metadata})
        for a in target.get("attributes", []):
            await change("upsert_attribute", {"values": {k: v for k, v in a.items() if k != "attribute_id"}})
        for issue in target.get("issues", []):
            await change(
                "upsert_issue",
                {"values": {k: v for k, v in issue.items() if k not in {"issue_id", "attributes"}}},
            )
            new_issue = next(
                i for i in current["data"]["issues"] if i.get("identifier") == issue.get("identifier")
            )
            for a in issue.get("attributes", []):
                await change(
                    "upsert_attribute",
                    {
                        "issue_id": new_issue["issue_id"],
                        "values": {k: v for k, v in a.items() if k != "attribute_id"},
                    },
                )
        return current
