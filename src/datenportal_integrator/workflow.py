from __future__ import annotations

import difflib
import json
import shutil
import subprocess
import sys
from copy import deepcopy
from pathlib import Path

from .common import IntegrationError, atomic_json, digest, inside, now, sha
from .config import Settings
from .csvcheck import inspect_csv
from .datasheet import DatasheetClient
from .jenkins import Jenkins, accepted_sheet
from .reports import render, workbook_cells
from .stack import Stack
from .state import Store
from .validation import Validator
from .xtf import check_offices, describe


class Workflow:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.store = Store(settings.state_dir)
        self.datasheets = DatasheetClient(settings.datasheet_mcp_url)
        self.validator = Validator(settings)

    def start(
        self,
        organization: str,
        identifier: str,
        issue: str | None = None,
        source_environment: str = "local",
        data_path: str | None = None,
        metadata_path: str | None = None,
    ) -> dict:
        state = self.store.create(organization, identifier, issue, source_environment)
        with self.store.edit(state["id"]) as run:
            if data_path:
                self.store.attach(run, Path(data_path), "original_data")
                self.store.attach(run, Path(data_path), "data")
            if metadata_path:
                self.store.attach(run, Path(metadata_path), "metadata")
        return self.status(state["id"])

    def status(self, run_id: str) -> dict:
        with self.store.edit(run_id) as run:
            result = dict(run)
            recipe = (
                self.settings.root / "topics" / run["organization"] / run["identifier"] / "integration.json"
            )
            if recipe.is_file():
                result["topic_recipe"] = json.loads(recipe.read_text())
            result["current_approvals"] = {}
            for gate in run["approvals"]:
                try:
                    self.require(run, gate)
                    result["current_approvals"][gate] = True
                except IntegrationError:
                    result["current_approvals"][gate] = False
            return result

    def attach(self, run_id: str, path: str, role: str) -> dict:
        if role not in {"data", "metadata", "metadata_source"}:
            raise IntegrationError("invalid_role", "Erlaubt: data, metadata, metadata_source.")
        with self.store.edit(run_id) as run:
            result = self.store.attach(run, Path(path), role)
            if role == "data":
                self.store.attach(run, Path(path), "original_data")
            if role == "metadata":
                run.pop("draft", None)
                run.pop("draft_base", None)
            return result

    def provenance(self, run_id: str, entries: list[dict]) -> dict:
        for entry in entries:
            if (
                entry.get("origin") not in {"user", "xlsx", "accepted", "llm"}
                or not entry.get("field")
                or not entry.get("source")
            ):
                raise IntegrationError(
                    "provenance_invalid",
                    "Jede Angabe benötigt field, origin (user/xlsx/accepted/llm) und source.",
                )
        with self.store.edit(run_id) as run:
            run["provenance"] = entries
            return {"entries": entries}

    def xlsx(self, run_id: str, sheet: str | None = None, offset: int = 0, limit: int = 100) -> dict:
        if offset < 0 or not 1 <= limit <= 500:
            raise IntegrationError("invalid_page", "offset >= 0 und limit zwischen 1 und 500 erforderlich.")
        with self.store.edit(run_id) as run:
            path = self.store.file(run, "metadata_source")
            if path is None:
                raise IntegrationError("source_missing", "Zuerst XLSX als metadata_source aufnehmen.")
            return workbook_cells(path, sheet, offset, limit)

    def _repo_file(self, run, relative: str) -> Path:
        if relative in run["changes"]:
            record = run["changes"][relative]
            path = Path(record["path"])
            if not path.is_file() or sha(path) != record["sha256"]:
                raise IntegrationError(
                    "change_modified", "Vorbereitete Repository-Datei nachträglich verändert.", path=relative
                )
            return path
        return inside(self.settings.topics_repo, relative)

    def offices(self, run):
        return self._repo_file(run, "shared/data/offices.xtf")

    def fingerprint(self, run, gate):
        context_files = {}
        for p in (self.settings.topics_repo / "shared").rglob("*"):
            if (
                p.is_file()
                and "build" not in p.parts
                and ".gradle" not in p.parts
                and p.name != "offices.xtf"
            ):
                context_files[str(p.relative_to(self.settings.topics_repo))] = sha(p)
        for name in ("build.gradle", "settings.gradle", "gretl-datenportal-job.yaml"):
            relative = f"{run['organization']}/{name}"
            p = self._repo_file(run, relative)
            if p.exists():
                context_files[relative] = sha(p)
        topic = self.settings.root / "topics" / run["organization"] / run["identifier"]
        for p in sorted(topic.rglob("*")) if topic.exists() else []:
            if p.is_file() and "__pycache__" not in p.parts and ".pytest_cache" not in p.parts:
                context_files[str(p.relative_to(self.settings.root))] = sha(p)
        offices = self.offices(run)
        plan = (
            run.get("publication_plans", {}).get(gate.split(":", 1)[-1], {})
            if gate.startswith("publish:")
            else {}
        )
        context = digest(
            {
                "settings": self.settings.fingerprint(),
                "files": context_files,
                "publication": {k: v for k, v in plan.items() if k != "fingerprint"},
            }
        )
        return self.store.fingerprint(run, gate, context, sha(offices) if offices.exists() else "missing")

    def require(self, run, gate):
        if gate == "metadata" or gate.startswith("publish:"):
            if run.get("pending_metadata") or (
                run.get("draft") and run.get("exported_revision") != run["draft"]["revision"]
            ):
                raise IntegrationError(
                    "draft_not_exported", "Aktuellen Entwurf zuerst exportieren, prüfen und freigeben."
                )
        self.store.require(run, gate, self.fingerprint(run, gate))

    def baseline(self, run_id: str) -> dict:
        """Read the accepted collection; use repository metadata only when no accepted sheet exists."""
        with self.store.edit(run_id) as run:
            env = self.settings.environment(run["source_environment"])
            manifest, xml = accepted_sheet(env, run["identifier"])
            run["baseline"] = {
                "environment": run["source_environment"],
                "release_id": manifest["releaseId"] if manifest else None,
                "sheet_hash": digest(xml.decode()) if xml else None,
            }
            if xml:
                path = self.store.directory(run_id) / "accepted.xtf"
                path.write_bytes(xml)
                self.store.attach(run, path, "accepted_metadata")
            else:
                folder = inside(self.settings.topics_repo, f"{run['organization']}/{run['identifier']}")
                candidates = [p for p in folder.glob("*") if p.suffix.lower() in {".xtf", ".xml"}]
                if len(candidates) > 1:
                    raise IntegrationError(
                        "ambiguous_repository_metadata", "Mehr als ein Datenblatt im Themenordner."
                    )
                if candidates:
                    self.store.attach(run, candidates[0], "accepted_metadata")
            return {"baseline": run["baseline"], "available": "accepted_metadata" in run["files"]}

    def analyze(self, run_id: str) -> dict:
        with self.store.edit(run_id) as run:
            path = self.store.file(run, "data")
            if path is None:
                return {"skipped": True, "reason": "metadata_only"}
            rules = json.loads((self.settings.root / "config/rules.json").read_text())
            report = inspect_csv(path, rules)
            fingerprint = self.fingerprint(run, "data")
            report["fingerprint"] = fingerprint
            run["checks"]["data"] = report
            output = self.store.directory(run_id) / "data-review.html"
            render(
                self.settings.root,
                output,
                {
                    "title": "CSV prüfen",
                    "run_id": run_id,
                    "valid": report["valid"],
                    "fingerprint": fingerprint,
                    "csv": report,
                    "sheet": None,
                    "checks": report,
                    "changes": {},
                    "provenance": [],
                },
            )
            return {**report, "review_path": str(output)}

    def stage_change(self, run_id: str, relative_path: str, source_path: str) -> dict:
        with self.store.edit(run_id) as run:
            org, identifier = run["organization"], run["identifier"]
            destination = inside(self.settings.topics_repo, relative_path)
            allowed = relative_path == "shared/data/offices.xtf" or (
                destination.parent == self.settings.topics_repo / org / identifier
                and destination.suffix in {".xtf", ".xml"}
            )
            allowed |= relative_path in {
                f"{org}/build.gradle",
                f"{org}/settings.gradle",
                f"{org}/gretl-datenportal-job.yaml",
            }
            if not allowed:
                raise IntegrationError(
                    "change_out_of_scope",
                    "Nur Datenblatt-, Office- und Organisationsdateien sind vorgesehen.",
                )
            source = Path(source_path).resolve()
            if not source.is_file():
                raise IntegrationError("file_missing", "Änderungsdatei fehlt.")
            previous = destination.read_text() if destination.exists() else ""
            proposed = source.read_text()
            dest = self.store.directory(run_id) / "changes" / relative_path
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, dest)
            initial = (
                run["changes"]
                .get(relative_path, {})
                .get("before_sha256", sha(destination) if destination.exists() else None)
            )
            record = {
                "path": str(dest),
                "sha256": sha(dest),
                "before_sha256": initial,
                "diff": "".join(
                    difflib.unified_diff(
                        previous.splitlines(True),
                        proposed.splitlines(True),
                        fromfile=relative_path,
                        tofile=relative_path,
                    )
                ),
            }
            run["changes"][relative_path] = record
            return {"relative_path": relative_path, **record}

    def validate(self, run_id: str) -> dict:
        with self.store.edit(run_id) as run:
            if run.get("pending_metadata") or (
                run.get("draft") and run.get("exported_revision") != run["draft"]["revision"]
            ):
                raise IntegrationError("export_required", "Aktuellen Entwurf zuerst erfolgreich exportieren.")
            sheet_path = self.store.file(run, "metadata") or self.store.file(run, "accepted_metadata")
            if sheet_path is None:
                raise IntegrationError(
                    "metadata_missing", "Datenblatt fehlt; Ausgangsbestand laden oder via Java-MCP erstellen."
                )
            data_path = self.store.file(run, "data")
            sheet = describe(sheet_path, run["identifier"], run["issue"] if data_path else None)
            errors, warnings = [], []
            if sheet["kind"] == "series" and data_path and not run["issue"]:
                errors.append({"code": "issue_required", "message": "Ausgabenbezeichnung (SERIES_ID) fehlt."})
            if sheet["kind"] == "dataset" and run["issue"]:
                errors.append({"code": "unexpected_issue", "message": "Ausgabe nur bei Serien angeben."})
            if sheet["kind"] == "series" and sum(i["current"] for i in sheet["issues"]) != 1:
                errors.append(
                    {"code": "current_issue", "message": "Genau eine aktuelle Serienausgabe erforderlich."}
                )
            names = [a["name"] for a in sheet["attributes"]]
            if len(names) != len(set(n.casefold() for n in names if n)):
                errors.append({"code": "duplicate_attribute", "message": "Attributnamen nicht eindeutig."})
            for a in sheet["attributes"]:
                if not a["description"]:
                    warnings.append(
                        {
                            "code": "attribute_description",
                            "message": "Attributbeschreibung fehlt.",
                            "attribute": a["name"],
                        }
                    )
            offices = self.offices(run)
            errors += check_offices(offices, sheet["creator_ref"])
            ili = self.validator.validate(sheet_path, offices, self.store.directory(run_id) / "validation")
            for check in ili.get("checks", []):
                for message in check.get("messages", []):
                    (errors if message.startswith("Error:") else warnings).append(
                        {"code": "ilivalidator", "message": message, "check": check["name"]}
                    )
            csv = None
            if data_path:
                rules = json.loads((self.settings.root / "config/rules.json").read_text())
                if not sheet["attributes"]:
                    errors.append(
                        {
                            "code": "attributes_missing",
                            "message": "Für die Freigabe benötigt jede Datenspalte ein Attribut.",
                        }
                    )
                csv = inspect_csv(data_path, rules, sheet["attributes"])
                errors += csv["errors"]
                warnings += csv["warnings"]
            # A repository datasheet must match the file actually reviewed and delivered.
            for relative in run["changes"]:
                path = self._repo_file(run, relative)
                if relative.startswith(f"{run['organization']}/{run['identifier']}/") and sha(path) != sha(
                    sheet_path
                ):
                    errors.append(
                        {
                            "code": "repository_sheet_mismatch",
                            "message": "PR-Datenblatt und Lieferdatenblatt unterscheiden sich.",
                        }
                    )
            fingerprint = self.fingerprint(run, "metadata")
            report = {
                "valid": not errors and ili["valid"] and (csv is None or csv["valid"]),
                "errors": errors,
                "warnings": warnings,
                "ilivalidator": ili,
                "csv": csv,
                "sheet": sheet,
                "fingerprint": fingerprint,
            }
            run["checks"]["metadata"] = report
            output = self.store.directory(run_id) / "metadata-review.html"
            render(
                self.settings.root,
                output,
                {
                    "title": "Datenblatt prüfen",
                    "run_id": run_id,
                    "valid": report["valid"],
                    "fingerprint": fingerprint,
                    "csv": csv,
                    "sheet": sheet,
                    "checks": report,
                    "changes": run["changes"],
                    "provenance": run["provenance"],
                },
            )
            return {**report, "review_path": str(output)}

    def approve(self, run_id: str, gate: str, fingerprint: str, human_statement: str) -> dict:
        if gate not in {"data", "metadata"} and not gate.startswith("publish:"):
            raise IntegrationError(
                "invalid_gate", "Freigabe muss data, metadata oder publish:<Umgebung> sein."
            )
        if not human_statement.strip():
            raise IntegrationError(
                "human_statement_missing", "Die ausdrückliche menschliche Freigabe protokollieren."
            )
        with self.store.edit(run_id) as run:
            current = self.fingerprint(run, gate)
            if current != fingerprint:
                raise IntegrationError(
                    "stale_review", "Die angezeigte Fassung ist nicht mehr aktuell; erneut prüfen."
                )
            if gate in {"data", "metadata"}:
                check = run["checks"].get(gate, {})
                if not check.get("valid") or check.get("fingerprint") != current:
                    raise IntegrationError("validation_required", "Aktuelle erfolgreiche Prüfung fehlt.")
            if gate != "data" and self.store.file(run, "data"):
                self.require(run, "data")
            if gate.startswith("publish:"):
                self.require(run, "metadata")
                name = gate.split(":", 1)[1]
                self.settings.environment(name)
                plan = run.get("publication_plans", {}).get(name)
                if not plan or plan["fingerprint"] != current:
                    raise IntegrationError(
                        "publication_review_required", "Zuerst konkreten Publikationsplan anzeigen."
                    )
                local_ok = any(
                    d.get("verified")
                    and d.get("fingerprint") == self.fingerprint(run, "metadata")
                    and self.settings.environments[n].kind == "local"
                    for n, d in run["deliveries"].items()
                )
                if not local_ok:
                    raise IntegrationError(
                        "local_test_required", "Erfolgreicher lokaler Gesamttest für diese Fassung fehlt."
                    )
            record = {"at": now(), "fingerprint": current, "human_statement": human_statement}
            run["approvals"][gate] = record
            run["events"].append({"action": "approve", "gate": gate, **record})
            return {"approved": gate, **record}

    async def metadata(self, run_id: str, operation: str, arguments: dict | None = None) -> dict:
        arguments = arguments or {}
        allowed = {
            "describe_schema",
            "create_datasheet",
            "import_xtf",
            "read_datasheet",
            "update_metadata",
            "upsert_attribute",
            "remove_attribute",
            "upsert_issue",
            "remove_issue",
            "validate_datasheet",
            "export_xtf",
            "restore",
        }
        if operation not in allowed:
            raise IntegrationError("unknown_operation", "Nicht unterstützte Datenblattoperation.")
        if operation == "describe_schema":
            return await self.datasheets.call(operation, arguments)
        with self.store.edit(run_id) as run:
            if self.store.file(run, "data"):
                self.require(run, "data")
            if run.get("pending_metadata") and operation != "restore":
                raise IntegrationError(
                    "metadata_uncertain",
                    "Letzte MCP-Änderung nicht bestätigt; restore stellt den gesicherten Stand her.",
                    pending=run["pending_metadata"],
                )
            if operation == "restore":
                if "draft" not in run:
                    raise IntegrationError("draft_missing", "Kein gesicherter Entwurf vorhanden.")
                restored = await self.datasheets.restore(run["draft"], run.get("draft_base"))
                pending = run.pop("pending_metadata", None)
                run["draft"] = restored
                run["exported_revision"] = None
                return {
                    **restored,
                    "unconfirmed_operation": pending,
                    "note": "Gesicherter Stand wiederhergestellt; unbestätigte Operation anhand der aktuellen IDs erneut beauftragen.",
                }
            if operation in {"create_datasheet", "import_xtf"}:
                if operation == "import_xtf":
                    source = self.store.file(run, "metadata") or self.store.file(run, "accepted_metadata")
                    if source is None:
                        raise IntegrationError(
                            "metadata_missing", "Zuerst XTF-Datei aufnehmen oder Ausgangsbestand laden."
                        )
                    arguments = {"xml": source.read_text(encoding="utf-8-sig")}
                if operation == "create_datasheet":
                    result, base = await self.datasheets.create(
                        arguments.get("kind"), arguments.get("values")
                    )
                else:
                    result = await self.datasheets.call(operation, arguments)
                    base = arguments["xml"]
                run["draft"] = result
                run["draft_base"] = base
                run["exported_revision"] = result["revision"] if operation == "import_xtf" else None
                run.pop("pending_metadata", None)
                return result
            if "draft" not in run:
                raise IntegrationError("draft_missing", "Zuerst Datenblatt importieren oder erstellen.")
            current = await self.datasheets.call("read_datasheet", {"draft_id": run["draft"]["draft_id"]})
            if current["revision"] != run["draft"]["revision"]:
                raise IntegrationError(
                    "draft_conflict",
                    "Entwurf wurde ausserhalb des Vorgangs bearbeitet; restore oder bewusster Neuimport erforderlich.",
                )
            args = {**arguments, "draft_id": current["draft_id"]}
            if operation != "read_datasheet":
                args["expected_revision"] = current["revision"]
            if operation == "export_xtf":
                args["include_xml"] = True
            mutation = operation not in {"read_datasheet", "validate_datasheet", "export_xtf"}
            if mutation:
                run["pending_metadata"] = {"operation": operation, "arguments": arguments, "at": now()}
                atomic_json(self.store.directory(run_id) / "state.json", run)
            result = await self.datasheets.call(operation, args)
            if mutation:
                run["draft"] = result
                run.pop("pending_metadata", None)
                # An earlier export is no longer the current draft; cannot approve it accidentally.
                run["files"].pop("metadata", None)
                run["checks"].pop("metadata", None)
            if operation == "export_xtf":
                target = self.store.directory(run_id) / "export.xtf"
                target.write_text(result.pop("xml"), encoding="utf-8")
                record = self.store.attach(run, target, "metadata")
                run["draft_base"] = target.read_text()
                run["exported_revision"] = current["revision"]
                return {**result, "artifact": record}
            return result

    def transform(self, run_id: str, converter: str, tests: str, policy: str, instructions: str) -> dict:
        if policy not in {"supplier", "recurring"} or not instructions.strip():
            raise IntegrationError(
                "transform_policy", "supplier/recurring und Lieferantenbeschreibung erforderlich."
            )
        script = inside(self.settings.root / "topics", converter)
        test_dir = inside(self.settings.root / "topics", tests)
        with self.store.edit(run_id) as run:
            original = self.store.file(run, "original_data") or self.store.file(run, "data")
            topic_dir = self.settings.root / "topics" / run["organization"] / run["identifier"]
            if not script.is_relative_to(topic_dir.resolve()) or not test_dir.is_relative_to(
                topic_dir.resolve()
            ):
                raise IntegrationError(
                    "converter_scope", "Konverter und Tests müssen zum aktuellen Thema gehören."
                )
            if original is None:
                raise IntegrationError("data_missing", "Keine CSV zum Transformieren vorhanden.")
            directory = self.store.directory(run_id)
            log = directory / "transform-tests.log"
            with log.open("w") as stream:
                result = subprocess.run(
                    [sys.executable, "-m", "pytest", str(test_dir)],
                    cwd=self.settings.root,
                    stdout=stream,
                    stderr=subprocess.STDOUT,
                    timeout=self.settings.timeout_seconds,
                )
            if result.returncode:
                raise IntegrationError(
                    "transform_tests_failed", "Konvertertests fehlgeschlagen.", log=str(log)
                )
            output = directory / "transformed.csv"
            if output.exists():
                output.unlink()
            with (directory / "transform.log").open("w") as stream:
                result = subprocess.run(
                    [sys.executable, str(script), str(original), str(output)],
                    cwd=self.settings.root,
                    stdout=stream,
                    stderr=subprocess.STDOUT,
                    timeout=self.settings.timeout_seconds,
                )
            if result.returncode or not output.is_file():
                raise IntegrationError("transform_failed", "Konverter lieferte keine erfolgreiche Ausgabe.")
            rules = json.loads((self.settings.root / "config/rules.json").read_text())
            before, after = inspect_csv(original, rules), inspect_csv(output, rules)
            supplier = directory / "lieferanten-vorgabe.txt"
            supplier.write_text(
                instructions + "\n\nZielspalten: " + ";".join(after["header"]) + "\nUTF-8, Semikolon.\n"
            )
            run["transform"] = {
                "converter": converter,
                "converter_sha": sha(script),
                "policy": policy,
                "tests": tests,
                "instructions": instructions,
                "before": before,
                "after": after,
            }
            atomic_json(
                topic_dir / "integration.json",
                {"converter": converter, "tests": tests, "policy": policy, "instructions": instructions},
            )
            self.store.attach(run, output, "data")
            return {**run["transform"], "supplier_instructions": str(supplier), "tests_log": str(log)}

    def apply_local_changes(self, run_id: str) -> dict:
        with self.store.edit(run_id) as run:
            self.require(run, "metadata")
            # Check all files before changing any; do not overwrite concurrent or foreign edits.
            for relative, change in run["changes"].items():
                target = inside(self.settings.topics_repo, relative)
                actual = sha(target) if target.exists() else None
                if actual not in {change["before_sha256"], change["sha256"]}:
                    raise IntegrationError(
                        "repository_conflict", "Lokale Datei wurde zwischenzeitlich verändert.", path=relative
                    )
            for relative, change in run["changes"].items():
                target = inside(self.settings.topics_repo, relative)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(self._repo_file(run, relative), target)
            return {"applied": list(run["changes"])}

    def publication_plan(self, run_id: str, environment: str) -> dict:
        with self.store.edit(run_id) as run:
            self.require(run, "metadata")
            env = self.settings.environment(environment)
            manifest, xml = accepted_sheet(env, run["identifier"])
            plan = {
                "environment": environment,
                "kind": env.kind,
                "jenkins_url": env.jenkins_url,
                "portal_url": env.portal_url,
                "branch": env.git_branch,
                "files": {k: v["sha256"] for k, v in run["files"].items() if k in {"data", "metadata"}},
                "changes": list(run["changes"]),
                "issue": run["issue"],
                "target_sheet_hash": digest(xml.decode()) if xml else None,
                "target_release": manifest["releaseId"] if manifest else None,
            }
            run.setdefault("publication_plans", {})[environment] = plan
            plan["fingerprint"] = self.fingerprint(run, "publish:" + environment)
            return plan

    def deliver(self, run_id: str, environment: str = "local") -> dict:
        """Advance at most one external stage; repeated calls poll recorded runs, never resubmit."""
        with self.store.edit(run_id) as run:
            env = self.settings.environment(environment)
            previous = run["deliveries"].get(environment)
            if previous:
                return self._advance(run, environment, env)
            self.require(run, "metadata")
            if self.store.file(run, "data"):
                self.require(run, "data")
            if env.kind == "local":
                for relative, change in run["changes"].items():
                    target = inside(self.settings.topics_repo, relative)
                    if not target.is_file() or sha(target) != change["sha256"]:
                        raise IntegrationError(
                            "local_changes_missing",
                            "Freigegebene Änderungen zuerst lokal übernehmen.",
                            path=relative,
                        )
                Stack(self.settings).ensure()
                Stack(self.settings).bootstrap(env)
            else:
                self.require(run, "publish:" + environment)
                from .repository import require_merged

                require_merged(self.settings, run, env)
                self._check_target(run, environment, env)
            item = {"phase": "seed_submitting", "fingerprint": self.fingerprint(run, "metadata"), "at": now()}
            run["deliveries"][environment] = item
            atomic_json(self.store.directory(run_id) / "state.json", run)
            with Jenkins(env) as jenkins:
                item["seed"] = jenkins.seed()
                item["phase"] = "seeding"
            return item

    def _check_target(self, run, name, env):
        plan = run.get("publication_plans", {}).get(name)
        if not plan:
            raise IntegrationError("publication_review_required", "Publikationsplan fehlt.")
        _, xml = accepted_sheet(env, run["identifier"])
        if (digest(xml.decode()) if xml else None) != plan["target_sheet_hash"]:
            raise IntegrationError(
                "target_changed",
                "Metadaten des Themas haben sich im Ziel verändert. Erneut vergleichen und freigeben.",
            )

    def _advance(self, run, name, env):
        item = run["deliveries"][name]
        if item["phase"] in {"seed_submitting", "delivery_submitting"}:
            raise IntegrationError(
                "submission_unknown",
                "Start wurde nicht bestätigt. Queue/Lauf zuordnen; niemals blind wiederholen.",
                state=item,
            )
        if item["phase"] in {"complete", "failed"}:
            return item
        with Jenkins(env) as jenkins:
            if item["phase"] == "seeding":
                status = jenkins.seed_status(item["seed"])
                if not status["complete"]:
                    return item
                if status["result"] != "SUCCESS":
                    item.update(phase="failed", error="Seed fehlgeschlagen; Seed-Konsole prüfen.")
                    return item
                self.require(run, "metadata")
                if self.store.file(run, "data"):
                    self.require(run, "data")
                if env.kind != "local":
                    self.require(run, "publish:" + name)
                    from .repository import require_merged

                    require_merged(self.settings, run, env)
                    self._check_target(run, name, env)
                metadata = self.store.file(run, "metadata")
                data = self.store.file(run, "data")
                if data and metadata is None:
                    _, current_xml = accepted_sheet(env, run["identifier"])
                    baseline_hash = (run.get("baseline") or {}).get("sheet_hash")
                    if (digest(current_xml.decode()) if current_xml else None) != baseline_hash:
                        raise IntegrationError(
                            "target_changed",
                            "Wirksames Datenblatt hat sich geändert; Daten erneut dagegen prüfen.",
                        )
                item["phase"] = "delivery_submitting"
                item["verification"] = {
                    "sheet": deepcopy(run["checks"]["metadata"]["sheet"]),
                    "data": deepcopy(run["files"].get("data")),
                }
                atomic_json(self.store.directory(run["id"]) / "state.json", run)
                item["job"] = jenkins.submit(
                    run["organization"],
                    run["identifier"],
                    run["issue"],
                    data,
                    metadata,
                    f"Themenintegrator {run['id']}",
                )
                item["phase"] = "running"
                return item
            status = jenkins.status(item["job"])
            if not status.get("complete"):
                return item
            try:
                report = jenkins.report(status)
            except IntegrationError as error:
                if error.code != "report_missing":
                    raise
                item.update(
                    phase="failed",
                    error="Jenkins-Lauf ohne Publikationsbericht beendet; Konsole prüfen.",
                    build_url=status.get("consoleUrl"),
                    result=status.get("status"),
                )
                return item
            item["report"] = report
            sheet, data = self._verification_context(run, item)
            outcome = jenkins.verify(report, sheet, data)
            item.update(outcome)
            item["phase"] = "complete" if outcome.get("verified") else "failed"
            item["build_url"] = status.get("consoleUrl")
            return item

    def _verification_context(self, run, item):
        snapshot = item.get("verification")
        if snapshot is None:  # Compatibility with locally persisted v0.1 runs.
            return run["checks"]["metadata"]["sheet"], self.store.file(run, "data")
        frozen = {"files": {"data": snapshot["data"]}} if snapshot["data"] else {"files": {}}
        return snapshot["sheet"], self.store.file(frozen, "data")

    def reconcile(self, run_id: str, environment: str, job: str, queue: str) -> dict:
        """Associate a known queue after an ambiguous response, without sending a delivery."""
        with self.store.edit(run_id) as run:
            env = self.settings.environment(environment)
            item = run["deliveries"].get(environment, {})
            if item.get("phase") != "delivery_submitting":
                raise IntegrationError("reconcile_not_needed", "Keine unbestätigte Lieferung vorhanden.")
            candidate = {"job": job, "queue": queue}
            with Jenkins(env) as jenkins:
                status = jenkins.status(candidate)
            parameters = {p["name"]: p["value"] for p in status.get("parameters", [])}
            if (
                parameters.get("COMMENT") != f"Themenintegrator {run_id}"
                or parameters.get("DATASET") != run["identifier"]
            ):
                raise IntegrationError("wrong_run", "Jenkins-Lauf gehört nicht eindeutig zu diesem Vorgang.")
            item.update(phase="running", job=candidate)
            return item

    def prepare_pr(self, run_id: str, environment: str) -> dict:
        from .repository import prepare_pr

        with self.store.edit(run_id) as run:
            env = self.settings.environment(environment)
            self.require(run, "publish:" + environment)
            result = prepare_pr(self.settings, run, env, self.store.directory(run_id))
            run["pull_request"] = result
            return result

    def office(self, run_id: str, values: dict) -> dict:
        """Stage an Office entry using supplied facts, without changing the source catalogue."""
        from xml.etree import ElementTree as ET

        from .xtf import BASE, ILI, local, parse, text

        required = {"identifier", "name", "abbreviation", "email", "officeAtWeb", "phoneNumber"}
        allowed = required
        if (
            set(values) - allowed
            or not required <= set(values)
            or not all(isinstance(v, str) and v.strip() for v in values.values())
        ):
            raise IntegrationError(
                "office_fields",
                "Dienststelle benötigt identifier, name, abbreviation, phoneNumber, email und officeAtWeb.",
            )
        with self.store.edit(run_id) as run:
            tree = parse(self.offices(run))
            baskets = [n for n in tree.iter() if n.tag == f"{{{BASE}}}Office"]
            if len(baskets) != 1:
                raise IntegrationError("office_catalog_invalid", "Genau ein Office-Basket erforderlich.")
            nodes = [n for n in baskets[0] if text(n, "identifier") == values["identifier"]]
            if len(nodes) > 1:
                raise IntegrationError("office_ambiguous", "Dienststelle ist mehrfach vorhanden.")
            node = (
                nodes[0]
                if nodes
                else ET.SubElement(
                    baskets[0], f"{{{BASE}}}Office.Office", {f"{{{ILI}}}tid": values["identifier"]}
                )
            )
            for key, value in values.items():
                matches = [n for n in node if local(n.tag) == key]
                child = matches[0] if matches else ET.SubElement(node, f"{{{BASE}}}{key}")
                child.text = value
            path = self.store.directory(run_id) / "offices-proposed.xtf"
            path.write_bytes(ET.tostring(tree, encoding="utf-8", xml_declaration=True))
        return self.stage_change(run_id, "shared/data/offices.xtf", str(path))

    def verify_delivery(self, run_id: str, environment: str) -> dict:
        """Recheck an accepted publication after a separately repaired reload; never upload."""
        with self.store.edit(run_id) as run:
            item = run["deliveries"].get(environment, {})
            if item.get("report", {}).get("publication") != "accepted":
                raise IntegrationError(
                    "publication_unconfirmed", "Keine bestätigte Publikation zum Nachprüfen."
                )
            env = self.settings.environment(environment)
            with Jenkins(env) as jenkins:
                sheet, data = self._verification_context(run, item)
                result = jenkins.verify(item["report"], sheet, data)
            item.update(result)
            item["phase"] = "complete" if result.get("verified") else "failed"
            return item

    def retry_delivery(self, run_id: str, environment: str) -> dict:
        """Explicitly reopen a known failed, unpublished attempt after correction and fresh approvals."""
        with self.store.edit(run_id) as run:
            self.require(run, "metadata")
            item = run["deliveries"].get(environment, {})
            if item.get("phase") != "failed" or item.get("report", {}).get("publication") in {
                "accepted",
                "unknown",
            }:
                raise IntegrationError(
                    "retry_unsafe",
                    "Nur bekannt fehlgeschlagene, nicht publizierte Läufe können neu gestartet werden.",
                )
            if item.get("job") and not item.get("report"):
                raise IntegrationError(
                    "retry_unsafe", "Ohne Publikationsbericht ist der externe Zustand nicht eindeutig."
                )
            run.setdefault("delivery_history", []).append({"environment": environment, **item})
            del run["deliveries"][environment]
            return {"ready_for_new_attempt": True, "environment": environment}

    def reconcile_seed(self, run_id: str, environment: str, queue: str) -> dict:
        """Associate an operator-identified seed queue after an unconfirmed seed response."""
        if not queue.isdigit():
            raise IntegrationError("invalid_queue", "Numerische Jenkins-Queue-ID erforderlich.")
        with self.store.edit(run_id) as run:
            env = self.settings.environment(environment)
            item = run["deliveries"].get(environment, {})
            if item.get("phase") != "seed_submitting":
                raise IntegrationError("reconcile_not_needed", "Kein unbestätigter Seed vorhanden.")
            with Jenkins(env) as jenkins:
                url = jenkins.base + "/queue/item/" + queue + "/"
                queued = jenkins.get(url + "api/json").json()
                task = queued.get("task", {})
                if task.get("fullName", task.get("name")) != env.seed_job:
                    raise IntegrationError(
                        "wrong_seed", "Queue-Eintrag gehört nicht zum konfigurierten Seed-Job."
                    )
                item.update(phase="seeding", seed={"queue_url": url})
                return item
