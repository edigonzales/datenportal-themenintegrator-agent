from __future__ import annotations

from copy import deepcopy
from pathlib import Path
from xml.etree import ElementTree as ET

from defusedxml.ElementTree import fromstring

from .common import IntegrationError

ILI = "http://www.interlis.ch/xtf/2.4/INTERLIS"
SHEET = "http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523"
BASE = "http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Base_20260529"


def local(tag):
    return tag.rsplit("}", 1)[-1]


def text(node, name, default=None):
    return next((c.text for c in node if local(c.tag) == name), default)


def structure(node):
    result = {}
    for child in node:
        key = local(child.tag)
        value = structure(child) if len(child) else child.text or ""
        if key in result:
            if not isinstance(result[key], list):
                result[key] = [result[key]]
            result[key].append(value)
        else:
            result[key] = value
    return result


def business_fields(fields):
    """Normalize IOX/ili2db collection wrappers, excluding GRETL-managed dates.

    shared/sql/publication/assemble.sql manages issued/modified in accepted
    sheets; every other field and collection order remains review-controlled.
    """
    if isinstance(fields, list):
        return [business_fields(value) for value in fields]
    if not isinstance(fields, dict):
        return fields
    result = {}
    for key, value in fields.items():
        if key in {"issued", "modified"}:
            continue
        if key in {"attributes", "issues"}:
            entries = value if isinstance(value, list) else [value]
            flattened = []
            for entry in entries:
                for tag, contents in entry.items():
                    for item in contents if isinstance(contents, list) else [contents]:
                        flattened.append({tag: business_fields(item)})
            result[key] = flattened
        elif key in {"keywords", "themes"}:
            result[key] = value if isinstance(value, list) else [value]
        else:
            result[key] = business_fields(value)
    return result


def parse(path: Path):
    try:
        return fromstring(path.read_bytes())
    except Exception as e:
        raise IntegrationError("invalid_xml", "XML kann nicht sicher gelesen werden.", file=str(path)) from e


def dataset_nodes(root):
    return [n for n in root.iter() if n.tag in {f"{{{SHEET}}}Dataset", f"{{{SHEET}}}DatasetSeries"}]


def describe(path: Path, identifier: str | None = None, issue_label: str | None = None):
    root = parse(path)
    nodes = dataset_nodes(root)
    if identifier:
        nodes = [n for n in nodes if text(n, "identifier") == identifier]
    if len(nodes) != 1:
        raise IntegrationError(
            "ambiguous_datasheet", "Genau ein passendes Datenblatt erforderlich.", matches=len(nodes)
        )
    node = nodes[0]
    issues = [n for n in node.iter() if n.tag == f"{{{SHEET}}}DatasetIssue"]
    selected = [n for n in issues if text(n, "issueLabel") == issue_label] if issue_label else []
    if issue_label and len(selected) != 1:
        raise IntegrationError(
            "unknown_issue",
            "Ausgabe muss im geprüften Datenblatt eindeutig vorhanden sein.",
            issue_label=issue_label,
        )

    def attributes(n):
        values = []
        for wrapper in n:
            if local(wrapper.tag) != "attributes":
                continue
            for a in wrapper:
                values.append(
                    {
                        "name": text(a, "name"),
                        "data_type": text(a, "dataType", ""),
                        "mandatory": text(a, "mandatory") == "true",
                        "description": text(a, "description"),
                        "unit": text(a, "unit"),
                        "code_list": text(a, "codeList"),
                    }
                )
        return values

    attrs = attributes(selected[0]) if selected else []
    attrs = attrs or attributes(node)
    return {
        "identifier": text(node, "identifier"),
        "kind": "series" if issues or local(node.tag) == "DatasetSeries" else "dataset",
        "title": text(node, "title"),
        "creator_ref": text(node, "creatorRef"),
        "publication_status": text(selected[0], "publicationStatus")
        if selected
        else text(node, "publicationStatus"),
        "attributes": attrs,
        "fields": structure(node),
        "issues": [
            {
                "identifier": text(n, "identifier"),
                "label": text(n, "issueLabel"),
                "current": text(n, "isCurrentIssue") == "true",
                "status": text(n, "publicationStatus"),
            }
            for n in issues
        ],
        "selected_issue": text(selected[0], "identifier") if selected else None,
    }


def select_datasheet(collection: bytes, identifier: str) -> bytes | None:
    root = fromstring(collection)
    found = [n for n in dataset_nodes(root) if text(n, "identifier") == identifier]
    if len(found) > 1:
        raise IntegrationError(
            "duplicate_identifier", "Mehrere Datenblätter mit gleichem Identifier im angenommenen Bestand."
        )
    if not found:
        return None
    selected = deepcopy(root)
    for parent in selected.iter():
        for child in list(parent):
            if (
                child.tag in {f"{{{SHEET}}}Dataset", f"{{{SHEET}}}DatasetSeries"}
                and text(child, "identifier") != identifier
            ):
                parent.remove(child)
    return ET.tostring(selected, encoding="utf-8", xml_declaration=True)


def check_offices(path: Path, creator: str):
    offices = [n for n in parse(path).iter() if local(n.tag) == "Office.Office"]
    ids = [text(n, "identifier") for n in offices]
    errors = []
    if len(ids) != len(set(ids)):
        errors.append({"code": "duplicate_office", "message": "Doppelte Dienststellen-Identifier."})
    if ids.count(creator) != 1:
        errors.append(
            {
                "code": "unknown_office",
                "message": "Datenherr fehlt oder ist nicht eindeutig im Office-Katalog.",
                "creator_ref": creator,
            }
        )
    return errors
