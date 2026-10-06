# Entwicklerhandbuch

Dieses Handbuch beschreibt Version 0.1.0 und ihre Erweiterung. Einrichtung und
fachlicher Ablauf stehen im [Anwenderhandbuch](anwenderhandbuch.md).
[AGENTS.md](../AGENTS.md) setzt die Grenzen: technische Änderungen gehören in
den Integrator; fachliche Änderungen im Themenrepo werden vorbereitet und
freigegeben. Bestehende Komponenten werden über ihre Schnittstellen verwendet.

## Architektur und Zuständigkeiten

Das Harness verwendet den [gemeinsamen Skill](../skills/datenportal-themenintegrator/SKILL.md)
für Erklärung, Quellenbewertung und menschliche Rückfragen. Der Python-Kern
speichert Artefakte, prüft den Vertrag und kontrolliert Freigaben.
CLI und stdio-MCP rufen dieselben `Workflow`-Methoden auf.

| Komponente | Verantwortung |
| --- | --- |
| `workflow.py` | Vorgänge, Fachprüfungen, Staging, Freigaben und externe Phasen |
| `state.py`, `common.py` | Kopien, Prüfsummen, Locks, atomare Speicherung und strukturierte Fehler |
| `config.py`, `doctor.py` | Strikte lokale Konfiguration und Prüfung von Voraussetzungen |
| `csvcheck.py`, `xtf.py`, `validation.py` | Streaming-CSV-Prüfung, sicherer XML-Zugriff, ilivalidator und Offices |
| `datasheet.py` | Java-MCP-Client und Rekonstruktion gespeicherter Entwürfe |
| `reports.py`, `templates/` | HTML-Fachreview und XLSX-Zellen mit Herkunft |
| `stack.py` | Erkennung, Start und dokumentierte lokale Erstinitialisierung |
| `jenkins.py` | Seed, Multipart-Lieferung, Laufstatus und Ergebnisprüfung |
| `repository.py` | Isolierter Git-Checkout, GitHub-PR und Mergekontrolle |
| `cli.py`, `server.py` | JSON-CLI und FastMCP über stdio |

Der Integrator pflegt keine zweite Themen-/Office-Datenbank. Fachliche
Repository-Quelle ist `topics_repo`; der angenommene Laufzeitstand kommt aus
dem Manifest und seiner Datenblattsammlung. Originaldateien werden kopiert.

### Externe Schnittstellen

| Ziel | Verwendete Schnittstelle |
| --- | --- |
| Java-Datenblatt-MCP | Streamable HTTP unter `datasheet_mcp_url`; öffentliche Import-/Bearbeitungs-/Prüf-/Exporttools |
| ilivalidator 1.15.0 | Subprozess mit Originalmodellen, Zusatzmodell und `--refdata` |
| Docker/Dev-Stack | Container-Labels/Mounts; vorhandenes `scripts/up.sh` und Compose-Dateien |
| Jenkins | Crumb-Abfrage, bestehender Seed-Job, `gretl-datenportal/build`, `gretl-datenportal/runStatus`, Artefakt `report.json` |
| Publikationsbestand/Portal | Manifest, referenzierte XTF-Dateien, `/catalog/published-catalog.xtf`, Themen-/Ausgabenseiten und Downloads |
| GitHub | `git`, `gh pr create`, `gh pr view`; kein automatischer Merge |

Jenkins authentisiert über die im Profil benannten Umgebungsvariablen.
Authentisierte Requests folgen keinen beliebigen Redirects: Host, Protokoll
und Jenkins-Basispfad werden kontrolliert. S3-Credentials verbleiben im
bestehenden Stack/Jenkins. Der Integrator liest öffentliche Artefakte per HTTP.

## Konfiguration und Entwicklungssetup

Im Integrator-Repo:

```sh
uv sync --locked
cp -n config/local.example.toml config/local.toml
uv run python scripts/setup-tools.py
uv run datenportal-integrator --help
uv run datenportal-integrator doctor
```

Python 3.11+ ist erforderlich. `uv.lock` bindet Python-Abhängigkeiten;
`setup-tools.py` bindet den Validator-Download durch feste SHA-256-Prüfsumme.
Der Java-MCP wird aus einer isolierten Kopie mit JDK 25 gestartet, siehe
[Startanleitung](anwenderhandbuch.md#java-datenblatt-mcp-und-lokaler-stack).
`model_dirs` zeigt zur Laufzeit auf vorhandene Modelle. Die Tests verwenden
einen abgegrenzten Snapshot unter `tests/fixtures/models/`.

Pydantic-Konfigurationsmodelle verbieten unbekannte Felder. Bei Änderungen
das versionierte JSON-Schema auf denselben Stand bringen; der Start
regeneriert es nicht automatisch.

### Settings

| Feld | Verhalten/Default |
| --- | --- |
| `topics_repo`, `stack_repo` | Erforderliche lokale Checkout-Pfade |
| `root` | Basis für relative Pfade; relatives `root` ab TOML-Datei; ohne Angabe deren Eltern-Elternordner |
| `state_dir` | `.datenportal-integrator/runs` relativ zu `root` |
| `datasheet_mcp_url` | `http://127.0.0.1:8000/mcp` |
| `validator_command` | Befehl als Argumentliste; Vorlage zeigt auf provisioniertes JAR |
| `model_dirs` | Liste lokaler Modellverzeichnisse oder HTTPS-Modellrepositories |
| `compose_files` | Standard `['compose.yaml']`, ab Stack-Arbeitsverzeichnis |
| `stack_start_args` | Argumentliste für vorhandenes `scripts/up.sh` |
| `timeout_seconds` | 300, Bereich 1 bis 7200; insbesondere Subprozessgrenze |
| `environments` | Profilnamen für `source_environment`/`environment` |

Konfigurationswahl: `--config`, danach `DATENPORTAL_INTEGRATOR_CONFIG`, danach
`config/local.toml` ab aktuellem Arbeitsverzeichnis. Die MCP-Launcher verwenden
absolute Repo-/Konfigurationspfade. `root` wird aus `model_dump` ausgeschlossen.

### Umgebungsprofile

| Feld | Verhalten/Default |
| --- | --- |
| `kind` | `local`, `int` oder `prod` |
| `jenkins_url`, `portal_url`, `manifest_url` | Erforderliche HTTP(S)-Adressen ohne eingebettete Credentials |
| `enabled` | `false`; explizit aktivieren |
| `seed_job` | `gretl-datenportal-seed` |
| `username_env`, `token_env` | `DATENPORTAL_JENKINS_USER`, `DATENPORTAL_JENKINS_TOKEN`; lokale Vorlage verwendet eigene Variablen |
| `git_remote`, `git_branch` | `origin`, `main` |
| `repository_mode` | `managed-git`; lokale Vorlage verwendet `working-tree` |
| `git_write_back` | `false`; `true` blockiert den PR-Workflow |
| `reload_portal` | `true`; als Jenkins-Parameter weitergegeben |

Lokale Automatik akzeptiert Loopback-Adressen. INT/PROD verlangen `managed-git`
und deaktiviertes Git-Rückschreiben. Das Profil bestätigt den Betreiberzustand;
es ist keine Remote-Inspektion oder automatische Umstellung von Jenkins.
Neue Profile vor der Abnahme konfigurieren: Settings-Änderungen können
vorhandene Freigaben entwerten.

Credentials kommen aus der Prozessumgebung. Benutzerlokale Harness-Dateien
dürfen die Werte beim MCP-Start setzen; versionierte Dateien enthalten sie
nicht. Der Kern lädt weder `.env` noch `~/.codex/config.toml` selbst.
`doctor` prüft Tool-Erreichbarkeit, Office-Datei, Java-Health und Stack-Zuordnung,
aber keine Jenkins-Anmeldung, vollständige XTF-Validierung oder PR-Berechtigung.

## Persistenz, Prüfsummen und Freigaben

### Lokaler Vorgang

`start` erzeugt eine UUID als 32 kleingeschriebene Hexzeichen.
`Store.directory` akzeptiert dieses Format und einen vorhandenen `state.json`.
Das lokale Dateiformat ist `schema_version: 1`. Es gibt keine allgemeine
Versionsmigration oder automatische Vorgangsbereinigung.

```text
state_dir/<ID>/
  state.json
  .lock
  files/<sha256>.<endung>
  changes/<pfad-im-themenrepo>
  accepted.xtf
  export.xtf
  data-review.html
  metadata-review.html
  validation/offices.log
  validation/datasheet.log
  transformed.csv
  transform-tests.log
  transform.log
  lieferanten-vorgabe.txt
  pr-checkout/
  pull-request-body.md
```

Optionale Dateien entstehen beim jeweiligen Schritt. `files/` enthält
prüfsummengebundene Kopien; `export.xtf` und `transformed.csv` sind Arbeitsstände,
deren gültige Versionen zusätzlich unter `files/` gesichert werden.
Die State-Pfade sind lokal und teilweise absolut. Beim Umzug Artefakt-/PR-Pfade
nicht still umschreiben; für Wiederaufnahme die Vorgangsdateien erhalten.

| State-Feld | Inhalt |
| --- | --- |
| `organization`, `identifier`, `issue`, `source_environment` | Fachliche Zuordnung und Quellumgebung |
| `files` | Rollen `original_data`, `data`, `metadata`, `metadata_source`, `accepted_metadata`; je Pfad, SHA-256 und ursprünglicher Dateiname |
| `baseline` | Quellumgebung, Release-ID, Digest der ausgewählten angenommenen XTF; ggf. Repository-Fallback ohne angenommenen Sheet-Hash |
| `changes` | Vorbereitete Dateien, SHA-256, Ausgangs-SHA und Textdiff |
| `checks` | CSV-/Metadatenberichte mit Prüfstand |
| `approvals`, `events`, `provenance` | Freigabetext/Zeitpunkt, Ereignisse und Quellenliste |
| `draft`, `draft_base`, `exported_revision`, `pending_metadata` | Java-Snapshot, Importbasis, Exportrevision und unbestätigte Operation |
| `transform` | Konverter/Testpfade, Konverterhash, Policy, Strukturvergleich und Vorgabe |
| `publication_plans`, `pull_request` | Zielpläne und PR-Identität/Checkout |
| `deliveries`, `delivery_history` | Externe Zustände je Umgebung und archivierte Fehlversuche |

`Store.edit` verwendet FileLock mit einer Sekunde Wartezeit und speichert im
`finally` atomar über temporäre Datei, `fsync` und Rename. Auch ein Fehler
bewahrt bereits gespeicherte unbestätigte Operationen. Das ist keine Transaktion
über mehrere Dateien oder externe Dienste. Vor lokalem Kopieren werden alle
Ausgangshashes geprüft; ein Dateisystemfehler während der Kopien kann dennoch
eine Teilübernahme hinterlassen.

### Fingerprints und Gates

Datei-SHA-256 bezeichnet Bytes. `digest` bindet JSON mit sortierten Schlüsseln;
`baseline.sheet_hash` ist der Digest des ausgewählten XML-Strings, kein direkter
Datei-SHA.

| Gate | Voraussetzung und Bindung |
| --- | --- |
| `data` | Erfolgreiches aktuelles `analyze`; Datenbytes, Zuordnung, Transformations-/Baseline-Stand und Konfigurationskontext |
| `metadata` | Aktuelles gültiges `validate`, ggf. Daten-OK; alle Rollen, Quellen, Office-Stand und vorbereitete Änderungen |
| `publish:<name>` | Metadata-OK, aktueller Zielplan und erfolgreicher lokaler Test derselben Fassung |

Zum Kontext gehören Settings, Regeln, Zusatzprüfungen, lokale `.ili`-Dateien,
Validator-JAR, relevante `shared/`-Dateien im Themenrepo, Organisationsdateien
und Konverter-/Rezeptdateien des Themas. Office und vorbereitete Dateien werden
gegen tatsächliche Hashes kontrolliert. Remote-Modellrepository-Inhalte werden
nicht lokal gehasht; für reproduzierbare Freigaben lokale feste Modelle verwenden.
Reine Metadatenrollen-/Quellenänderungen erhalten normalerweise das Daten-OK;
Settings-, Baseline- oder Organisationsänderungen können auch dieses entwerten.

`approve` verlangt einen nicht leeren tatsächlichen menschlichen Text und
aktuellen Fingerprint. Der Skill stellt die Stopps sicher; der Dateistore
authentisiert die Identität des Schreibenden nicht unabhängig. Kein Agent darf
aus dem allgemeinen Auftrag ein OK ableiten. Entwurfsänderungen blockieren
Metadaten-/Publikationsfreigaben, bis ein aktueller Export vorliegt.
Historische Freigaben bleiben gespeichert; `status.current_approvals` bewertet
ihre aktuelle Gültigkeit.

### Java-Neustart und unbestätigte Änderungen

Pro Java-Tool-Aufruf öffnet der Adapter eine Streamable-HTTP-MCP-Session.
Java-Entwürfe sind serverseitig flüchtig. Der bestätigte Snapshot wird lokal
gesichert. `restore` importiert `draft_base`, entfernt Listen über Java-Tools,
setzt die gesicherten Fachwerte und rekonstruiert Attribute/Ausgaben.
Objekt-/Basket-IDs bleiben erhalten; interne Attribut-/Ausgaben-IDs ändern
sich. Anschliessend ist ein neuer Export erforderlich.

Vor gewöhnlichen bearbeitenden Tools wird `pending_metadata` gespeichert.
Unbestätigte Antworten blockieren weitere Bearbeitung; `restore` gibt den
Auftrag separat zurück, statt ihn automatisch erneut zu spielen.
Initiales Erstellen/Importieren nutzt diese Pending-Journalisierung nicht;
ein abgebrochener Initialaufruf kann einen nicht zugeordneten Java-Entwurf
hinterlassen. Der bestätigte lokale Stand bleibt die Wiederaufnahmequelle.

Die bestehende Java-Version erzeugt beim direkten `create_datasheet` UUID-OIDs,
die mit führender Ziffer von XTF abgelehnt werden können. Der Integrator erstellt
eine leere Transferhülle mit `b`-/`o`-präfigierten IDs und importiert sie über
das bestehende Tool. Alle Fachwerte, Änderungen und der gültige Export bleiben
Aufgabe des Java-MCP. Die Hülle ist selbst kein gültiges Datenblatt.

## CLI- und MCP-Referenz

Ausgaben der CLI sind JSON. Globale Optionen stehen vor dem Unterkommando:

```sh
uv run datenportal-integrator --config config/local.toml doctor
uv run datenportal-integrator schema start
uv run datenportal-integrator call start --args-file .datenportal-integrator/eingang.json
uv run datenportal-integrator call status --json '{"run_id":"<ID>"}'
```

`--args-file -` liest stdin; alternativ `--json`, standardmässig `{}`.
Exitcodes: `0` bei erfolgreichem Aufruf, `2` bei `IntegrationError`, `1` bei
sonstigem Fehler; Parserfehler ebenfalls `2`. Ein Prüfaufruf kann mit Exitcode
0 trotzdem `valid=false` liefern. Auch bei `doctor` ist Exitcode 0 allein kein
Abnahmebeleg.

`schema <operation>` liefert Python-Signatur und Beschreibung, kein vollständiges
JSON-Schema der Fachmetadaten. MCP veröffentlicht Tool-Eingabeschemas.
Java-Feldregeln kommen aus `metadata(operation="describe_schema")`.
FastMCP registriert `cli.OPERATIONS`; sync-Methoden laufen im Thread,
async-Metadatenaufrufe direkt. Erwartete Fehler sind Toolfehler mit
`isError=true` und JSON-Code, sonstige Exceptions reduzierte Meldungen ohne
Request-/Credential-Details.

### Vorgang, Quellen und Prüfung

Parameter ohne angezeigten Default sind erforderlich. `run_id` ist die
vollständige zurückgegebene ID.

| Operation | Argumente | Ergebnis |
| --- | --- | --- |
| `start` | `organization`, `identifier`; `issue=null`, `source_environment="local"`, `data_path=null`, `metadata_path=null` | State mit `id`, Kopien und `current_approvals`; keine automatische Baseline |
| `status` | `run_id` | State, gültige Freigaben, ggf. `topic_recipe` |
| `attach` | `run_id`, `path`, `role` (`data`, `metadata`, `metadata_source`) | Artefakt `path`, `sha256`, `original_name`; bei `data` neue Originalrolle, bei `metadata` Entwurf zurücksetzen |
| `baseline` | `run_id` | `baseline`, `available`; angenommener Bestand, sonst eindeutige Repo-XTF |
| `provenance` | `run_id`, `entries` | Ersetzt Quellenliste; Eintrag mit `field`, `origin` (`user`, `xlsx`, `accepted`, `llm`), `source` |
| `xlsx` | `run_id`; `sheet=null`, `offset=0`, `limit=100` | Blattliste oder Zellen mit Koordinate/Wert/Formel/Cachehinweis; `next_offset` |
| `analyze` | `run_id` | CSV-Bericht, `valid`, `fingerprint`, `review_path`; ohne CSV `skipped=true` |
| `validate` | `run_id` | `valid`, Fehler/Hinweise, Validatorprotokolle, CSV-Abgleich, `sheet`, `fingerprint`, `review_path` |
| `approve` | `run_id`, `gate`, `fingerprint`, `human_statement` | `approved`, Zeitpunkt, Text und Prüfstand |

Organisation/Identifier beginnen mit ASCII-Buchstaben; weitere Zeichen sind
Buchstaben, Ziffern, `_`, `.`, `-`, aber keine `..`.
`xlsx` verlangt `offset >= 0`, `1 <= limit <= 500`. Es führt keine Formeln oder
externen Links aus; fehlende gecachte Formelwerte bleiben sichtbar.

### Datenblattbearbeitung

`metadata(run_id, operation, arguments=null)` kapselt:

| `operation` | `arguments` | Ergebnis/Verhalten |
| --- | --- | --- |
| `describe_schema` | Optional `kind` aus Java-Feldkatalog | Schema, auch vor CSV-OK |
| `create_datasheet` | `kind` (`dataset`, `series`), optional `values` | Java-Snapshot und gesicherte Importhülle |
| `import_xtf` | Keine eigenen XML-Argumente | Importiert Rolle `metadata`, sonst `accepted_metadata` |
| `read_datasheet` | Keine | Java-Snapshot, kontrolliert Entwurfsrevision |
| `update_metadata` | `values`, optional `issue_id` | Fachpatch am Root oder der Ausgabe |
| `upsert_attribute` | `values`, optional `attribute_id`, `issue_id` | Anlegen/Ändern, genaue Java-Namenssuche ohne ID |
| `remove_attribute` | `attribute_id`, optional `issue_id` | Snapshot nach Entfernung |
| `upsert_issue` | `values`, optional `issue_id` | Anlegen/Ändern, Identifier-Suche ohne ID |
| `remove_issue` | `issue_id` | Snapshot nach Entfernung |
| `validate_datasheet` | Keine | Java-Prüfbericht, `valid` und Meldungen |
| `export_xtf` | Keine erforderlich | Java-Exportresultat ohne XML-Inlinefeld, zusätzlich lokales `artifact` |
| `restore` | Keine | Snapshot, `unconfirmed_operation`, Hinweis auf neue interne IDs |

Der Adapter ergänzt `draft_id`, `expected_revision` und beim Export
`include_xml=true`. Diese Werte nicht aus früheren Antworten übernehmen.
Der Export übernimmt Java-XML-Bytes in eine lokale Datei; der temporäre
Downloadlink ist für Wiederaufnahme nicht erforderlich.
Direkte Java-Tools wie `discard_datasheet` sind im Integrator nicht freigegeben.
Bei vorhandener CSV verlangen Entwurfsoperationen ausser `describe_schema`
ein gültiges Daten-OK.

Beispiel eines Attributs als Argumente der Integrator-Operation:

```json
{
  "run_id": "<ID>",
  "operation": "upsert_attribute",
  "arguments": {
    "values": {
      "name": "kennung",
      "data_type": "TEXT",
      "description": "Fachlich bestaetigte Kennung",
      "mandatory": true
    }
  }
}
```

Java-Feldnamen sind z.B. `creator_ref`, `contact_point`, `data_type`;
XML-Namen folgen dem Modell. Attribute/Ausgaben über eigene Tools bearbeiten.
`null` in einem Metadatenpatch löscht einen Wert; optionale Toolargumente
weglassen statt `null` senden. Kontaktpatches sind partiell, andere Struktur-
und Listenänderungen richten sich nach `describe_schema`. Unvollständige
Entwürfe sind möglich; erst gültige Exporte können freigegeben werden.

### Transformation und fachliches Staging

| Operation | Argumente | Ergebnis |
| --- | --- | --- |
| `transform` | `run_id`, `converter`, `tests`, `policy`, `instructions` | Testlog, Vorher/Nachher, Lieferantenvorgabe, neue `data`-Rolle und Themenrezept |
| `office` | `run_id`, `values` mit genau `identifier`, `name`, `abbreviation`, `email`, `officeAtWeb`, `phoneNumber` | Vorbereiteter Katalog via `stage_change`; nicht leere Stringwerte |
| `stage_change` | `run_id`, `relative_path`, `source_path` | Kopie unter `changes/`, Zielpfad, SHA, Ausgangs-SHA und Diff |
| `apply_local_changes` | `run_id` | `applied`-Liste; nach Metadata-OK und Konfliktprüfung |

Erlaubte Ziele: `shared/data/offices.xtf`, XTF/XML unmittelbar unter
`<organization>/<identifier>/` sowie `build.gradle`, `settings.gradle`,
`gretl-datenportal-job.yaml` der Organisation. Andere Ziele:
`change_out_of_scope`. Thema-XTF muss bytegleich zur Liefer-XTF sein;
bestehende Dateinamen beibehalten. Das Staging prüft nicht vollständig den
Inhalt ausführbarer Gradle-/Jenkins-Dateien; lokale Seed-/Gesamttests sind nötig.

### Publikation und Fortsetzung

| Operation | Argumente | Ergebnis |
| --- | --- | --- |
| `publication_plan` | `run_id`, `environment` | URLs/Branch, Lieferhashes, Änderungen, Ausgabe, Ziel-Sheet-Hash/Release und Fingerprint |
| `prepare_pr` | `run_id`, `environment` | `required`; ggf. PR-URL, Head, Branch, Checkout, Base; verlangt Publish-OK |
| `deliver` | `run_id`; `environment="local"` | Gespeicherter Phasenstand; startet oder verfolgt bestehenden Lauf |
| `reconcile` | `run_id`, `environment`, `job`, `queue` | Zuordnung unbestätigten Uploads; kontrolliert `COMMENT`/`DATASET` |
| `reconcile_seed` | `run_id`, `environment`, `queue` | Zuordnung unbestätigten Seeds; numerische Queue-ID und Seed-Taskname |
| `verify_delivery` | `run_id`, `environment` | Bestätigte Publikation nachprüfen, ohne Upload oder Reload |
| `retry_delivery` | `run_id`, `environment` | `ready_for_new_attempt`; geeigneten Fehllauf archivieren, für `deliver` öffnen |

`retry_delivery` verlangt Metadata-OK. `deliver` kontrolliert zusätzlich
Datenfreigabe sowie bei INT/PROD Publish-Freigabe und Merge. Unbestätigte,
publizierte oder hinsichtlich Publikation unbekannte Läufe können nicht
über `retry_delivery` neu gestartet werden.

### Fehlervertrag

Ein erwarteter CLI-Fehler:

```json
{
  "error": "approval_required",
  "message": "Menschliche Freigabe fehlt oder ist nach Aenderungen ungueltig.",
  "details": {"gate": "metadata"}
}
```

Codes dienen der Steuerung; Nachrichten können sich ändern. Java-Toolfehler
übernehmen den Servercode und seine Antwort unter `details.response`.
Validierungsprobleme stehen oft in einem Report mit `valid=false`.

| Bereich | Relevante Codes |
| --- | --- |
| Konfiguration/Tools | `configuration_missing`, `environment_disabled`, `local_endpoint_required`, `runtime_incompatible`, `command_unavailable`, `command_failed`, `validator_unavailable`, `credentials_missing` |
| Dateien/Zuordnung | `file_missing`, `unsafe_path`, `invalid_identifier`, `invalid_run_id`, `run_not_found`, `artifact_changed`, `change_modified`, `invalid_role` |
| Quellen/Datenblatt | `source_missing`, `invalid_page`, `provenance_invalid`, `metadata_missing`, `invalid_xml`, `ambiguous_datasheet`, `ambiguous_repository_metadata`, `unknown_issue`, `duplicate_identifier` |
| Freigaben | `approval_required`, `stale_review`, `validation_required`, `invalid_gate`, `human_statement_missing`, `export_required`, `draft_not_exported`, `local_test_required`, `publication_review_required` |
| Entwurf | `unknown_operation`, `invalid_kind`, `draft_missing`, `draft_conflict`, `metadata_uncertain`; weitere Java-Codes |
| Umbau/Staging | `transform_policy`, `converter_scope`, `data_missing`, `transform_tests_failed`, `transform_failed`, `change_out_of_scope`, `repository_conflict`, `local_changes_missing`, `office_fields`, `office_catalog_invalid`, `office_ambiguous` |
| Stack/Bestand | `stack_mismatch`, `ambiguous_stack`, `stack_unhealthy`, `local_only`, `manifest_invalid`, `invalid_artifact`, `remote_read_failed`, `accepted_sheet_missing`, `jenkins_paused`, `builds_running`, `bootstrap_incomplete` |
| Jenkins/Fortsetzung | `jenkins_url_mismatch`, `jenkins_auth`, `jenkins_read_failed`, `jenkins_submit_failed`, `empty_delivery`, `seed_cancelled`, `submission_unknown`, `report_missing`, `reconcile_not_needed`, `wrong_run`, `invalid_queue`, `wrong_seed`, `publication_unconfirmed`, `retry_unsafe`, `unsafe_download` |
| Git/Zielbestand | `pull_request_required`, `human_merge_required`, `pr_checkout_exists`, `pr_base_conflict`, `pr_changed`, `merged_content_changed`, `target_changed` |

`unknown_office`, `issue_required`, `unexpected_issue`, `current_issue`,
`attributes_missing`, CSV-Format-/Typfehler und `repository_sheet_mismatch`
sind Reportcodes. Nicht jeder Fehler führt zu einem CLI-Fehlerexitcode.
Für allgemeine Exceptions sind Details reduziert; Validator-/Transformationslogs
und Jenkins-Konsole separat prüfen.

## CSV und XTF

`config/rules.json` bindet die
[Formatvorgaben](https://sogis.github.io/datenportal-dokumentation/datenpublikation/main/#datenformat).
CSV wird mit `csv.reader(strict=True)` vollständig gescannt. Ausgabe maximal
100 Fehler und fünf Beispiele je Spalte; die Prüfung ist keine Stichprobe.

- UTF-8 mit/ohne BOM, Semikolon, nicht leere Kopfzeile, mindestens eine
  Beobachtung, keine leeren Beobachtungszeilen, konsistente Spaltenzahl.
- Spaltennamen beginnen mit ASCII-Buchstaben, danach Buchstaben/Ziffern/`_`;
  Eindeutigkeit ohne Gross-/Kleinschreibung. NUL-Zeichen in Zellen werden abgelehnt.
- TEXT beliebig, INTEGER als vorzeichenbehaftete 64-Bit-Ganzzahl,
  DECIMAL/NUMERIC als endliche Zahl mit Dezimalpunkt, auch Exponentenform.
- BOOLEAN genau `true`/`false`, DATE gültiges ISO-Datum, DATETIME ISO-Zeit mit
  Sekunden, optional Sekundenbruchteilen und Offset `+01:00`.
- Bei Datenblattabgleich Namen/Reihenfolge exakt, Typen unterstützt,
  Pflichtwerte nicht leer. Führende Nullen beeinflussen den Typvorschlag;
  der Vertrag kommt aus dem freigegebenen Datenblatt.

Totals und fehlende Attributbeschreibungen sind Hinweise. Fachliche Richtigkeit,
Kennungen, Nachweisgrenzen, Einheiten und Rundungen benötigen menschliche Prüfung.
Es erfolgt keine automatische Umkodierung oder fachliche Datenergänzung.

XML wird mit `defusedxml` gelesen. Die Freigabe validiert Office-Katalog und
konkrete XTF separat mit ilivalidator. `validation/office-check.ini` aktiviert
`Datenportal_Integrator_Checks_20261005`; die VIEW verwendet eine EXISTENCE
CONSTRAINT auf `creatorRef` gegen `Office.Office.identifier`.
`--refdata offices.xtf` und `--allObjectsAccessible` binden den Referenzbestand.
Die Originalmodelle werden nicht erweitert. Python-Prüfungen erkennen
unbekannte/doppelte Offices, falsche Ausgaben, Attribute und Liefer-/Repo-Abweichungen.

HTML entsteht aus dem vorgesehenen lokalen Artefakt und Report mit
Jinja-Autoescaping und ohne ausführbare Scripts. Quellen/Diffs werden als Text
gerendert, Typannahmen als Vorschläge. XLSX wird mit `openpyxl` read-only gelesen.

## Externe Phasen und Idempotenz

### Stack und Erstbestand

Jenkins-Container werden über Compose-Working-Dir-/Service-Labels gesucht.
Mountquelle `/workspace/themenrepo` muss `topics_repo` entsprechen und
`THEMEN_REPO_MODE=working-tree` sein. Docker-Inspect-Rohdaten werden nicht
ausgegeben. Auch eine gestoppte inkompatible Instanz blockiert. Bei fehlendem
Stack verwendet `ensure` das vorhandene Startskript. Fehlende Kernservices
werden mit Compose `--no-recreate --wait` gestartet; nicht bereite Services
werden gemeldet.

Nur HTTP 404 des Manifests erlaubt lokale Erstinitialisierung.
Jenkins wird über `quietDown` vorübergehend angehalten; laufende Builds oder
ein bereits administrativ pausierter Jenkins blockieren. Im Jenkins-Volume
wird eine temporäre Themenkopie angelegt und die bestehende Gradle-Task
`initializePublication` mit `s3Publish=true`, `gitWriteBack=false` und
`reloadPortal=false` ausgeführt. `cancelQuietDown` steht im Cleanup. Danach
wird das Portal mit vorhandenem Compose gestartet. Kein Volume wird gelöscht.

### Jenkins-Lieferung

| Gespeicherte Phase | Nächster Fortschritt |
| --- | --- |
| Noch kein Eintrag | Freigaben/Ziel prüfen, lokal Stack bereitstellen; `seed_submitting` vor Seed-POST sichern |
| `seed_submitting` | Antwort unbestätigt; Queue zuordnen, keine automatische Wiederholung |
| `seeding` | Dieselben Queue-/Builddaten abfragen; nach Erfolg Freigaben/Ziel erneut prüfen, Upload vorbereiten |
| `delivery_submitting` | Antwort unbestätigt; bestehenden Job/Queue zuordnen |
| `running` | Bestehenden Lauf abfragen, Bericht lesen und Publikation prüfen |
| `complete` oder `failed` | Ergebnis zurückgeben; separate Nachprüfung oder zulässiger neuer Versuch |

Vor Upload wird `verification` mit Fachsheet und Datenartefakt eingefroren.
Spätere Aufnahmen ersetzen nicht die Prüfung des gesendeten Pakets.
Alte v0.1-Läufe ohne Snapshot verwenden den gespeicherten Prüfbericht und die
aktuelle Datenrolle; sie nicht während der Nachprüfung ändern.

Jenkins erhält `ORGANISATION`, `DATASET`, `SERIES_ID`,
`PUBLICATION_MODE=delivery`, `RELOAD_PORTAL`, `COMMENT=Themenintegrator <ID>`.
`DATA_FILE`/`METADATA_FILE` werden nur bei vorhandener Lieferrolle gesendet.
Bei reiner Datenlieferung muss die angenommene XTF vor Upload dem Baseline-Digest
entsprechen. `SERIES_ID` bedeutet Ausgabenbezeichnung und wird nur bei CSV gesetzt.

Seed speichert `queue_url`, später `build_url`. Lieferung speichert `job`,
`queue`, `details_url`, später `build`. Der Plugin-Jobname wird übernommen.
Es besteht kein allgemeines Exactly-once-Protokoll; unbestätigte Starts
erfordern Aufklärung. `reconcile` prüft Vorgangskennung/`DATASET`, aber keine
vollständigen zusätzlichen Parameter oder hochgeladenen Dateihashes.

### Publikationskontrolle

`phase`, `publication`, `reload`, `verified` haben unterschiedliche Bedeutung.
Eine bestätigte Publikation bleibt publiziert, auch bei Reload-/Prüffehler.
Kontrolliert werden:

1. Bericht bestätigt `publication=accepted` und aktuelle Manifest-Release-ID.
2. Portal-Katalog ist bytegleich zum Katalog der Release.
3. Bericht bestätigt RDF-/Opendata-Übernahme.
4. Angenommene Fachmetadaten entsprechen dem geprüften Datenblatt. XML-Wrapper
   werden normalisiert; GRETL-verwaltete `issued`/`modified` vom Vergleich
   ausgenommen und die Rootdaten separat ausgegeben.
5. Öffentliche Dataset-/Serienausgabe ist eindeutig und Downloads sind lesbar.
   Bei CSV-Lieferung existieren CSV/XLSX/Parquet und CSV-SHA stimmt.
6. Portal-Seite ist erreichbar. Bei nicht öffentlichem Status wird stattdessen
   Abwesenheit im publizierten Katalog geprüft.

Attributreihenfolge bleibt relevant. XLSX/Parquet werden auf Download und
Hashbildung geprüft, nicht zusätzlich zeilenweise mit CSV verglichen.
HTTP 200 ersetzt keinen vollständigen UI-Test. Eine neuere Publikation kann
die Release-gebundene historische Nachprüfung blockieren.
`verify_delivery` löst selbst keinen Reload aus; Betreiber beheben ihn über
die vorhandene Umgebung.

### PR-Verfahren

`prepare_pr` braucht Publish-Freigabe nach lokalem Test. Es klont den Remote/
Zielbranch unter `pr-checkout`, erstellt `codex/themenintegration-<ID-Praefix>`
und übernimmt nur vorbereitete Dateien. Abweichender Ausgangsstand blockiert.
Sind die Dateien bereits am Ziel, `required=false`; sonst Commit, Push und
`gh pr create --body-file`. Der Kern gibt die PR-URL zurück; das Harness hängt
sie in Codex gegebenenfalls mit `attach_artifact` an.

Vor Remote-Seed/Upload kontrolliert `require_merged` Zustand `MERGED`, Base,
ursprünglichen PR-Head und bytegleichen Stand des aktuellen Zielbranches.
Es authentisiert den Menschen hinter dem Merge nicht unabhängig; der Skill
verlangt menschliche Übernahme. Ein vorhandener PR-Checkout ohne gespeichertes
Ergebnis erfordert manuelle Prüfung. Pro Vorgang wird ein PR-Ergebnis gesichert;
mehrere unterschiedliche INT-/PROD-Zielbranches sind kein allgemein unterstützter
automatischer PR-Promotionsablauf.

## Erweiterungen

### Konverter

Unter `topics/<organization>/<identifier>/` Konverter und Tests anlegen.
Vertrag: `python converter.py INPUT.csv OUTPUT.csv`, Exitcode 0 bei Erfolg.
Python-Laufzeit ist die des Integrators. Beide Pfade müssen zum Thema gehören
und sind bei `transform` relativ zu `topics/`:

```json
{
  "run_id": "<ID>",
  "converter": "agi/ch.so.beispiel/convert.py",
  "tests": "agi/ch.so.beispiel/test_convert.py",
  "policy": "supplier",
  "instructions": "Zielspalten kennung;wert, UTF-8, Semikolon; fuehrende Nullen erhalten."
}
```

Tests prüfen Werteerhaltung, führende Nullen, fehlende Werte, Grenzfälle und
fachlichen Umbau. Der Kern ruft pytest und danach den Konverter auf, scannt
Vorher/Nachher und sichert die Ausgabe. Fehlgeschlagene Tests blockieren.
Ein erfolgreicher Prozess ersetzt `analyze` und menschliches OK nicht.

`integration.json` speichert `converter`, `tests`, `policy`, `instructions`.
`status.topic_recipe` macht es dem Skill zugänglich. `recurring` löst durch
den Skill Vorverarbeitung bei Folgelieferung aus; `supplier` dokumentiert die
Umstellung. `start`/`deliver` führen den Konverter nicht eigenständig aus.
Konverter sind vertrauenswürdiger lokaler Python-Code, kein Sandbox-Plugin.

### Regeln, Profile und neue Operationen

Bei Formatänderungen `config/rules.json` und gezielt `csvcheck.py` anpassen,
Regelversion erhöhen und positive/negative Beispiele prüfen. Das Feld
`datetime_offset` wird derzeit nicht dynamisch von `accepts` gelesen:
Offsetlogik und Tests müssen gemeinsam angepasst werden. Neue JSON-Typnamen
allein implementieren keine Parser.

Zusatzprüfungen bleiben in `validation/`; Originalmodelle/Java-MCP nicht
erweitern. Neue Umgebung in lokaler TOML konfigurieren, Betreiberzustand
prüfen und Credentials als Variablen referenzieren.
Eine neue Workflow-Operation benötigt typisierte öffentliche Methode,
Registrierung in `cli.OPERATIONS`, Gate-/Persistenzlogik und gezielte Tests.
CLI/MCP teilen den Kern; keine zweite Ablaufimplementierung schaffen.

## Tests und Abnahme

### Automatisierte Prüfungen

```sh
uv sync --locked
uv run pytest -q
uv run ruff check .
git diff --check
```

| Tests | Abgedeckte Bereiche |
| --- | --- |
| `test_csv.py` | Formate, vollständiger Scan, Fehlerbegrenzung, Typen, Reihenfolge/Pflichtwerte |
| `test_workflow.py` | Freigaben, Änderungen, Wiederaufnahme, Teillieferungen, Serien/Offices, Staging, HTML-Escaping, eingefrorene Lieferung |
| `test_transform.py` | Tatsächliche Konvertertests, Wert-/Originalerhaltung, Lieferantenvorgabe und Testfehler |
| `test_external.py` | HTTP-/Stack-Doubles: Multipart/Crumb, Identität, kein Duplikatupload, Manifestfehler, leerer/vorhandener/teilweiser Stack, Reload/Downloads |
| `test_repository.py` | Echte temporäre lokale Git-Remotes, simulierte GitHub-API; PR-Stand, offener Merge, veränderte Zielbytes |
| `test_mcp.py` | Echter stdio-Client mit Freigabestopp; XLSX-Zellen/Formelcache |
| `test_validation_live.py` | Echter Validator; optional echter Java-MCP für Import, neue Datenblätter, IDs und Wiederherstellung |

Für echte Validator-Tests `scripts/setup-tools.py` ausführen. Ohne das JAR
werden diese Tests explizit übersprungen. Für echte Java-Tests einen Testserver
wie im Anwenderhandbuch starten, dann:

```sh
TEST_DATASHEET_MCP=http://127.0.0.1:8000/mcp uv run pytest -q
```

Der normale pytest-Lauf publiziert keine realen Jenkins-Daten und erstellt
keinen realen GitHub-PR. Java-Tests legen flüchtige Entwürfe im angegebenen
Server an und verwenden ein temporäres Themenrepo. Testfreigaben sind als
Fixtures gekennzeichnet und dürfen nur isolierte/local Tests autorisieren.

Fixture-XTF stammen aus dem Editor-Testbestand; Office-/Modellfixtures sind
Testsnapshots. Laufzeit liest den tatsächlichen Office-Katalog.
Ein realer lokaler Publikationstest ist ein separater bewusster Vorgang und
verändert Stack-Laufzeitdaten. Vor/nach ihm Git-Status und Dateistände der
Schwester-Repositories vergleichen; vorhandene Nutzeränderungen erhalten.

### Abnahmestand

Stand **6. Oktober 2026**. Historische erfolgreiche Tests sind von einer
momentan laufenden lokalen Umgebung zu unterscheiden.

| Nachweis | Status und Reichweite |
| --- | --- |
| Gesamte Testsuite mit Java-MCP/ilivalidator | 63 Tests bestanden bei technischer Implementierungsabnahme; Ruff bestanden |
| Neuer synthetischer Fachablauf | Echter Java-MCP erstellt Dataset, Attribute und Export; echter Validator, beide gekennzeichneten Testfreigaben und Übernahme in temporäres Themenrepo geprüft |
| Dataset/Serie und Offices | Echte positive/negative Validatorläufe, unbekannte Office-Referenzen abgelehnt; neue IDs/Rekonstruktion für Dataset und Serie geprüft |
| Bestehender lokaler Stack | Pilotserie `ch.so.bevoelkerung.altersstruktur`, Ausgabe `2025`, Seed, Lieferung, Reload, Metadaten und drei Downloads erfolgreich |
| Leerer/teilweiser Stack | Isolierte Simulation inkl. Portalstart nach Initialisierung; kein realer Bestand für diese Prüfung gelöscht |
| Codex/OpenCode | OpenCode-MCP verbunden, Codex-Launcher-Eintrag erkannt, echter stdio-Client mit Stopp vor Datenfreigabe; vollständiger Mensch-Dialog in beiden Harnesses noch offen |
| GitHub | Temporäre Git-Remotes und simuliertes `gh` geprüft; echter GitHub-PR und menschlicher Merge noch offen. `gh` inzwischen lokal erreichbar und angemeldet. |
| INT/PROD | Profile, Betreiberabgleich und reale Veröffentlichung offen; keine produktive Abnahme behauptet |
| Schutz bestehender Repos | Dev-Stack/Editor bei Implementierungsabnahme Git-sauber; ausschliesslich vorgefundene eBau-Nutzeränderung im Themenrepo erhalten |

Bei der anschliessenden Dokumentationsprüfung bestanden 59 Tests; vier
Java-MCP-Tests wurden mangels aktiviertem Testserver übersprungen. Die echten
Validator-Tests liefen. Zusätzlich wurden 22 lokale Dokumentationslinks,
31 Shell-Blöcke, fünf JSON- und zwei TOML-Blöcke sowie 23 CLI-JSON-Aufrufe
gegen die Signaturen geprüft. Die Referenz deckt alle 21 öffentlichen
Workflow-Operationen ab. OpenCode-MCP-Verbindung und Codex-Launcher-Eintrag
wurden erneut geprüft; Ruff und `git diff --check` bestanden.

Kennungen des tatsächlichen lokalen Pilottests:

- Vorgang: `a8fb924222964b5c9338cd61bbbd9675`
- Seed-Queue 39, Seed-Build 918; Liefer-Queue 40, Liefer-Build 14
- Release: `257a31dc-7634-4129-9391-4a5562f99b04`
- CSV-SHA-256: `ce7210a21ed4484650403765b4c5bdab91a116289ad57d1053874b5ffa679491`
- [Lokale Ausgabe](http://localhost:8081/series/ch.so.bevoelkerung.altersstruktur/issues/ch.so.bevoelkerung.altersstruktur_2025)

Dies sind Nachweise eines gekennzeichneten lokalen Tests, keine Vorlage für
reale Freigaben oder dauerhafte Zusage der Erreichbarkeit. Artefakte/Protokolle
liegen lokal im konfigurierten `state_dir`.

### Bekannte Grenzen und nächste Abnahmen

- Menschliche Freigaben/Merges werden im Ablauf kontrolliert, nicht unabhängig
  gegenüber einem voll berechtigten Agenten authentisiert.
- Kein Live-Gesamttest eines neuen Fachthemas in beiden Harnesses, keine
  produktive PR-/INT-/PROD-Abnahme; weitere MCP-Harnesses sind nicht abgenommen.
- Unterbrochene PRs und verschwundene Seed-Queues benötigen manuelle Aufklärung;
  keine allgemeine Remote-Reconciliation oder Duplikatbereinigung.
- Keine allgemeine State-Migration, Mehrrechnerablage oder automatische Bereinigung.
- Keine unabhängige fachliche Richtigkeitsprüfung, Formelberechnung,
  Office-Faktenrecherche oder automatische Lieferantenkommunikation.
- Desktop-Anhänge und Discovery bleiben Harness-/Versionsabhängigkeiten;
  CLI mit Originalpfaden ist der gemeinsame Fallback.
- Organisationen brauchen bestehende Team-/Berechtigungskonventionen;
  Staging prüft Dateipfade, nicht vollständig die ausführbaren Konfigurationsinhalte.

Bei Codeänderungen Testsuite, diese Referenz und die Beispiele im
[Anwenderhandbuch](anwenderhandbuch.md) aktuell halten.
Zurück zum [Repository-Einstieg](../README.md).
