from __future__ import annotations

import argparse
import asyncio
import inspect
import json
import sys
from pathlib import Path

from .common import IntegrationError
from .config import load_settings
from .workflow import Workflow

OPERATIONS = [
    "office",
    "reconcile_seed",
    "retry_delivery",
    "verify_delivery",
    "start",
    "status",
    "attach",
    "baseline",
    "analyze",
    "provenance",
    "xlsx",
    "metadata",
    "validate",
    "approve",
    "stage_change",
    "apply_local_changes",
    "transform",
    "publication_plan",
    "prepare_pr",
    "deliver",
    "reconcile",
]


def main():
    parser = argparse.ArgumentParser(description="Datenportal-Themenintegrator — alle Ergebnisse als JSON")
    parser.add_argument(
        "--config", help="Lokale TOML-Konfiguration (alternativ DATENPORTAL_INTEGRATOR_CONFIG)"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("serve", help="MCP-Server über stdio starten")
    commands.add_parser("doctor", help="Konfiguration und lokale Voraussetzungen prüfen")
    schema = commands.add_parser("schema", help="Argumente einer Operation anzeigen")
    schema.add_argument("operation", choices=OPERATIONS)
    call = commands.add_parser("call", help="Dieselben Operationen wie MCP aufrufen")
    call.add_argument("operation", choices=OPERATIONS)
    group = call.add_mutually_exclusive_group()
    group.add_argument("--json", default="{}", help="JSON-Argumente")
    group.add_argument("--args-file", type=Path, help="JSON-Datei; '-' liest stdin")
    args = parser.parse_args()
    if args.command == "schema":
        method = getattr(Workflow, args.operation)
        print(
            json.dumps(
                {
                    "operation": args.operation,
                    "signature": str(inspect.signature(method)),
                    "description": method.__doc__,
                },
                ensure_ascii=False,
            )
        )
        return
    try:
        settings = load_settings(args.config)
        if args.command == "serve":
            from .server import server

            server(settings).run(transport="stdio")
            return
        if args.command == "doctor":
            from .doctor import doctor

            result = doctor(settings)
        else:
            if args.args_file:
                raw = sys.stdin.read() if str(args.args_file) == "-" else args.args_file.read_text()
            else:
                raw = args.json
            parameters = json.loads(raw)
            method = getattr(Workflow(settings), args.operation)
            result = method(**parameters)
            if inspect.isawaitable(result):
                result = asyncio.run(result)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except IntegrationError as error:
        print(json.dumps(error.as_dict(), ensure_ascii=False, indent=2))
        sys.exit(2)
    except Exception as error:
        # Never dump request objects, environments or credential-bearing tracebacks.
        print(
            json.dumps(
                {
                    "error": "operation_failed",
                    "message": "Operation konnte nicht abgeschlossen werden.",
                    "exception": type(error).__name__,
                }
            )
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
