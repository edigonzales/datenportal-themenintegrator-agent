from __future__ import annotations

import csv
import re
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path


def accepts(value: str, kind: str) -> bool:
    if not value:
        return True
    try:
        if kind == "TEXT":
            return True
        if kind == "INTEGER":
            return bool(re.fullmatch(r"[+-]?\d+", value)) and -(2**63) <= int(value) < 2**63
        if kind in {"DECIMAL", "NUMERIC"}:
            return (
                bool(re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?", value))
                and Decimal(value).is_finite()
            )
        if kind == "BOOLEAN":
            return value in {"true", "false"}
        if kind == "DATE":
            return bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", value)) and bool(date.fromisoformat(value))
        if kind == "DATETIME":
            return bool(
                re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?\+01:00", value)
            ) and datetime.fromisoformat(value).utcoffset() == timedelta(hours=1)
    except (ValueError, InvalidOperation):
        pass
    return False


def inspect_csv(path: Path, rules: dict, attributes: list[dict] | None = None) -> dict:
    errors, warnings = [], []
    total_errors = 0

    def error(code, message, **location):
        nonlocal total_errors
        total_errors += 1
        if len(errors) < 100:
            errors.append({"code": code, "message": message, **location})

    header, stats, rows = [], [], 0
    try:
        with path.open(encoding=rules["encoding"], newline="") as stream:
            reader = csv.reader(stream, delimiter=rules["delimiter"], strict=True)
            header = next(reader, [])
            if not header:
                error("empty_csv", "CSV enthält keine Kopfzeile.")
            if len(set(h.casefold() for h in header)) != len(header):
                error(
                    "duplicate_header",
                    "Spaltennamen sind ohne Beachtung der Grossschreibung nicht eindeutig.",
                )
            for i, name in enumerate(header):
                if not re.fullmatch(rules["header_pattern"], name):
                    error(
                        "invalid_header",
                        "Spaltenname entspricht nicht den Formatvorgaben.",
                        column=i + 1,
                        value=name,
                    )
                if name.casefold() in {"total", "gesamt", "summe"}:
                    warnings.append(
                        {
                            "code": "possible_total",
                            "message": "Möglicherweise redundante Totalspalte; fachlich prüfen.",
                            "column": name,
                        }
                    )
            if attributes is not None:
                if header != [a.get("name") for a in attributes]:
                    error(
                        "attribute_order", "Namen und Reihenfolge stimmen nicht mit dem Datenblatt überein."
                    )
                for a in attributes:
                    if a.get("data_type", "").upper() not in rules["supported_types"]:
                        error(
                            "unsupported_type",
                            "Nicht unterstützter Datentyp.",
                            column=a.get("name"),
                            value=a.get("data_type"),
                        )
            stats = [
                {
                    "name": n,
                    "missing": 0,
                    "examples": [],
                    "leading_zero": False,
                    "candidates": ["INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME"],
                }
                for n in header
            ]
            for row in reader:
                rows += 1
                if not row or all(not v.strip() for v in row):
                    error("empty_row", "Leerzeile ist nicht zulässig.", line=reader.line_num)
                    continue
                if len(row) != len(header):
                    error(
                        "row_width",
                        "Anzahl Zellen entspricht nicht der Kopfzeile.",
                        line=reader.line_num,
                        actual=len(row),
                    )
                    continue
                for i, value in enumerate(row):
                    st = stats[i]
                    if "\x00" in value:
                        error("nul", "NUL-Zeichen in CSV.", line=reader.line_num, column=header[i])
                    if not value:
                        st["missing"] += 1
                    else:
                        if len(st["examples"]) < 5 and value not in st["examples"]:
                            st["examples"].append(value[:160])
                        st["leading_zero"] |= bool(re.fullmatch(r"0\d+", value))
                        st["candidates"] = [t for t in st["candidates"] if accepts(value, t)]
                    if (
                        attributes is not None
                        and i < len(attributes)
                        and attributes[i].get("name") == header[i]
                    ):
                        a = attributes[i]
                        if a.get("mandatory") and not value.strip():
                            error(
                                "mandatory",
                                "Wert in Pflichtspalte fehlt.",
                                line=reader.line_num,
                                column=header[i],
                            )
                        elif value and not accepts(value, a.get("data_type", "").upper()):
                            error(
                                "datatype",
                                "Wert entspricht nicht dem deklarierten Typ/Format.",
                                line=reader.line_num,
                                column=header[i],
                                value=value[:160],
                            )
            if not rows:
                error("no_observations", "CSV enthält keine Beobachtungen.")
    except (UnicodeError, csv.Error) as e:
        error("csv_format", "CSV ist nicht gültiges UTF-8 oder fehlerhaft quotiert.", detail=str(e))
    for st in stats:
        st["suggested_type"] = (
            "TEXT" if st["leading_zero"] or st["missing"] == rows else next(iter(st["candidates"]), "TEXT")
        )
        st.pop("candidates")
        if st["leading_zero"]:
            warnings.append(
                {
                    "code": "leading_zero",
                    "message": "Führende Nullen vorhanden; Kennung als TEXT prüfen.",
                    "column": st["name"],
                }
            )
    return {
        "valid": not total_errors,
        "rows": rows,
        "header": header,
        "columns": stats,
        "errors": errors,
        "error_count": total_errors,
        "warnings": warnings,
        "rules_version": rules["version"],
        "source": rules["source"],
        "note": "Typvorschläge sind keine fachliche Bestätigung. Alle Datensätze wurden geprüft; maximal 100 Fehler angezeigt.",
    }
