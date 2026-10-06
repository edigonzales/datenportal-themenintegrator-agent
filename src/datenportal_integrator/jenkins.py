from __future__ import annotations

import os
from contextlib import ExitStack
from pathlib import Path
from urllib.parse import parse_qs, quote, urljoin, urlparse

import httpx

from .common import IntegrationError
from .config import Environment
from .xtf import select_datasheet


def public_get(url: str) -> httpx.Response:
    response = httpx.get(url, timeout=30)
    if response.status_code not in {200, 404}:
        raise IntegrationError(
            "remote_read_failed",
            "Bestand nicht lesbar; kein leerer Erstbestand.",
            status=response.status_code,
            url=url,
        )
    return response


def read_manifest(env: Environment) -> dict | None:
    response = public_get(env.manifest_url)
    if response.status_code == 404:
        return None
    try:
        manifest = response.json()
        if not isinstance(manifest, dict) or not manifest.get("releaseId") or not manifest.get("datasheets"):
            raise ValueError()
        for key in ("datasheets", "catalog", "duckdb"):
            if manifest.get(key):
                artifact_url(env.manifest_url, manifest[key])
        return manifest
    except (ValueError, TypeError) as e:
        raise IntegrationError(
            "manifest_invalid", "Vorhandenes Manifest ist beschädigt; keine Initialisierung erlaubt."
        ) from e


def artifact_url(manifest_url: str, filename: str) -> str:
    if not isinstance(filename, str) or filename in {".", "..", ""} or any(c in filename for c in "/\\?#%"):
        raise IntegrationError("invalid_artifact", "Manifest enthält einen unzulässigen Artefaktnamen.")
    return urljoin(manifest_url, quote(filename))


def accepted_sheet(env, identifier):
    manifest = read_manifest(env)
    if manifest is None:
        return None, None
    response = public_get(artifact_url(env.manifest_url, manifest["datasheets"]))
    if response.status_code != 200:
        raise IntegrationError("accepted_sheet_missing", "Manifest verweist auf fehlende Datenblattsammlung.")
    return manifest, select_datasheet(response.content, identifier)


class Jenkins:
    def __init__(self, env: Environment):
        self.env = env
        user, token = os.getenv(env.username_env), os.getenv(env.token_env)
        if not user or not token:
            raise IntegrationError(
                "credentials_missing",
                "Jenkins-Zugangsdaten fehlen in der Umgebung.",
                variables=[env.username_env, env.token_env],
            )
        self.http = httpx.Client(auth=(user, token), timeout=60, follow_redirects=False)
        self.base = env.jenkins_url.rstrip("/")

    def close(self):
        self.http.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

    def trusted_url(self, location: str) -> str:
        target = urljoin(self.base + "/", location)
        base, parsed = urlparse(self.base), urlparse(target)
        if (base.scheme, base.netloc) != (parsed.scheme, parsed.netloc) or not parsed.path.startswith(
            base.path + "/"
        ):
            raise IntegrationError(
                "jenkins_url_mismatch", "Jenkins verweist auf eine andere Basisadresse; Konfiguration prüfen."
            )
        return target

    def get(self, path):
        response = self.http.get(self.trusted_url(path))
        if response.status_code != 200:
            raise IntegrationError(
                "jenkins_read_failed", "Jenkins-Antwort ist nicht lesbar.", status=response.status_code
            )
        return response

    def post(self, path, **kwargs):
        headers = {}
        crumb = self.http.get(self.base + "/crumbIssuer/api/json")
        if crumb.status_code == 200:
            c = crumb.json()
            headers[c["crumbRequestField"]] = c["crumb"]
        elif crumb.status_code != 404:
            raise IntegrationError(
                "jenkins_auth",
                "Jenkins-Anmeldung oder CSRF-Abfrage fehlgeschlagen.",
                status=crumb.status_code,
            )
        response = self.http.post(self.trusted_url(path), headers=headers, **kwargs)
        if response.status_code not in {200, 201, 202, 302, 303}:
            raise IntegrationError(
                "jenkins_submit_failed",
                "Jenkins hat den Auftrag nicht bestätigt. Status vor erneutem Start klären.",
                status=response.status_code,
            )
        return response

    def seed(self):
        name = self.env.seed_job
        url = "job/" + "/job/".join(quote(p, safe="") for p in name.split("/")) + "/build"
        response = self.post(url)
        location = response.headers.get("Location")
        if not location:
            raise IntegrationError("submission_unknown", "Seed gestartet, aber keine Queue-Kennung erhalten.")
        return {"queue_url": self.trusted_url(location)}

    def seed_status(self, item):
        if not item.get("build_url"):
            q = self.get(item["queue_url"].rstrip("/") + "/api/json").json()
            if q.get("cancelled"):
                raise IntegrationError("seed_cancelled", "Seed wurde abgebrochen.")
            if not q.get("executable"):
                return {"complete": False}
            item["build_url"] = self.trusted_url(q["executable"]["url"])
        result = self.get(item["build_url"].rstrip("/") + "/api/json").json()
        return {"complete": not result.get("building", False), "result": result.get("result")}

    def submit(self, organization, identifier, issue, data: Path | None, metadata: Path | None, comment):
        if not data and not metadata:
            raise IntegrationError("empty_delivery", "Mindestens eine Lieferdatei ist erforderlich.")
        fields = {
            "ORGANISATION": organization,
            "DATASET": identifier,
            "SERIES_ID": issue if data and issue else "",
            "PUBLICATION_MODE": "delivery",
            "RELOAD_PORTAL": str(self.env.reload_portal).lower(),
            "COMMENT": comment,
        }
        with ExitStack() as stack:
            files = {}
            for key, path in (("DATA_FILE", data), ("METADATA_FILE", metadata)):
                if path:
                    files[key] = (path.name, stack.enter_context(path.open("rb")), "application/octet-stream")
            response = self.post("gretl-datenportal/build", data=fields, files=files)
        location = response.headers.get("Location")
        if not location:
            raise IntegrationError(
                "submission_unknown", "Lieferung möglicherweise gestartet; keine Laufkennung erhalten."
            )
        location = self.trusted_url(location)
        query = parse_qs(urlparse(location).query)
        if not query.get("job") or not query.get("queue"):
            raise IntegrationError(
                "submission_unknown", "Jenkins-Rückleitung enthält keine eindeutige Queue-Kennung."
            )
        return {"job": query["job"][0], "queue": query["queue"][0], "details_url": location}

    def status(self, item):
        query = {k: item[k] for k in ("job", "queue", "build") if item.get(k)}
        response = self.http.get(self.base + "/gretl-datenportal/runStatus", params=query)
        response.raise_for_status()
        result = response.json()
        if result.get("buildNumber"):
            item["build"] = str(result["buildNumber"])
        return result

    def report(self, status):
        artifacts = [a for a in status.get("artifacts", []) if a["fileName"] == "report.json"]
        if len(artifacts) != 1:
            raise IntegrationError("report_missing", "Kein eindeutiger Publikationsbericht im Jenkins-Lauf.")
        return self.get(artifacts[0]["url"]).json()

    def verify(self, report, sheet, source_data: Path | None):
        result = {
            "publication": report.get("publication"),
            "reload": report.get("reload"),
            "opendata": report.get("opendata"),
            "visible": False,
        }
        if report.get("publication") != "accepted":
            result["message"] = "Lieferung ist nicht als publiziert bestätigt; Bericht prüfen."
            return result
        manifest = read_manifest(self.env)
        if not manifest or manifest["releaseId"] != report.get("releaseId"):
            result["message"] = "Manifest entspricht nicht diesem Lauf; keine erneute Lieferung starten."
            return result
        # Exact catalog equality establishes that the portal loaded this release.
        expected = public_get(artifact_url(self.env.manifest_url, manifest["catalog"]))
        actual = public_get(self.env.portal_url + "/catalog/published-catalog.xtf")
        if expected.status_code != 200 or actual.status_code != 200 or expected.content != actual.content:
            result["message"] = (
                "Publiziert, aber Portal hat den Katalog noch nicht geladen. Reload separat klären; nicht erneut liefern."
            )
            return result
        result["reload_observed"] = True
        if (report.get("opendata") or {}).get("status") != "accepted":
            result["message"] = "Katalog publiziert; RDF-Übernahme nicht bestätigt. Bericht prüfen."
            return result
        import hashlib

        from defusedxml.ElementTree import fromstring

        from .xtf import business_fields, dataset_nodes, local, structure, text

        accepted = public_get(artifact_url(self.env.manifest_url, manifest["datasheets"]))
        roots = (
            [
                n
                for n in dataset_nodes(fromstring(accepted.content))
                if text(n, "identifier") == sheet["identifier"]
            ]
            if accepted.status_code == 200
            else []
        )
        if len(roots) != 1 or business_fields(structure(roots[0])) != business_fields(sheet["fields"]):
            result["message"] = (
                "Angenommene Metadaten entsprechen nicht dem freigegebenen Fachinhalt. Nicht erneut liefern; Bestand und Lauf prüfen."
            )
            return result
        result["metadata_verified"] = True
        result["managed_dates"] = {key: text(roots[0], key) for key in ("issued", "modified")}

        target_identifier = sheet.get("selected_issue") or sheet["identifier"]
        nodes = [
            n
            for n in fromstring(actual.content).iter()
            if local(n.tag) in {"Dataset", "DatasetSeries", "DatasetIssue"}
            and text(n, "identifier") == target_identifier
        ]
        if sheet["publication_status"] != "published":
            result["verified"] = not nodes
            result["message"] = (
                "Bestand übernommen; Thema ist entsprechend seinem Status nicht öffentlich."
                if not nodes
                else "Thema ist trotz nicht öffentlichem Status noch im Katalog."
            )
            return result
        if len(nodes) != 1:
            result["message"] = "Thema/Ausgabe fehlt oder ist mehrdeutig im publizierten Katalog."
            return result
        distributions = [n for n in nodes[0].iter() if local(n.tag) == "Distribution"]
        result["downloads"] = []
        for distribution in distributions:
            fmt, url = text(distribution, "format"), text(distribution, "downloadURL")
            if not url:
                continue
            parsed = urlparse(url)
            if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password:
                raise IntegrationError("unsafe_download", "Katalog enthält unzulässige Downloadadresse.")
            h, size = hashlib.sha256(), 0
            with httpx.stream("GET", url, timeout=60) as response:
                response.raise_for_status()
                for chunk in response.iter_bytes():
                    h.update(chunk)
                    size += len(chunk)
            result["downloads"].append({"format": fmt, "url": url, "bytes": size, "sha256": h.hexdigest()})
        if source_data:
            from .common import sha

            formats = {d["format"]: d for d in result["downloads"]}
            if not {"csv", "xlsx", "parquet"} <= formats.keys() or formats["csv"]["sha256"] != sha(
                source_data
            ):
                result["message"] = (
                    "Publizierte Downloads fehlen oder CSV entspricht nicht der gelieferten Datei."
                )
                return result
        suffix = "/datasets/" + quote(sheet["identifier"], safe="")
        if sheet["kind"] == "series":
            suffix = "/series/" + quote(sheet["identifier"], safe="")
            if sheet.get("selected_issue"):
                suffix += "/issues/" + quote(sheet["selected_issue"], safe="")
        result["portal_url"] = self.env.portal_url + suffix
        page = public_get(result["portal_url"])
        if sheet["publication_status"] != "published":
            result["message"] = (
                "Bestand übernommen; Metadatenstatus sieht keine öffentliche Sichtbarkeit vor."
            )
            result["verified"] = True
            return result
        result["visible"] = page.status_code == 200
        result["verified"] = result["visible"]
        result["message"] = (
            "Publikation, Portalstand und Downloads bestätigt."
            if result["verified"]
            else "Thema noch nicht im Portal sichtbar."
        )
        return result
