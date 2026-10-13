---
name: datenportal-validierungsmodell
description: Leite auf ausdrücklichen Auftrag ein INTERLIS-Validierungsmodell für Datenportal-CSV aus Datenblatt, Datenbeobachtungen und bestätigten Fachregeln ab; prüfe und repariere Modell, Modellreferenz und Validierungstask. Eigenständig oder innerhalb einer Themenintegration; nicht für allgemeine GIS-Modellierung.
---

Verwende denselben `datenportal_integrator`-MCP und seine vorhandenen Modelloperationen. Argumentverträge mit `operation_schema` klären; Implementierungsquellen nicht lesen oder durchsuchen. Referenzen und Fachdateien mit `datenportal_workspace.read_file` lesen, Anhänge mit `import_attachment` verifiziert übernehmen. Der CLI-Fallback `schema`/`call` bleibt im Betriebspaket eine Betreiberhandlung. Bei Einrichtungs-, Runtime- oder Zugangsproblemen [Betrieb und Zugänge](../../docs/agent/betrieb-und-zugaenge.md) lesen. Fehlende Publikationszugänge verhindern keine Modellierung.

## Grenzen und Vorgang

Bei mitgelieferter Vorgangs-ID vor weiteren Fachoperationen `status` lesen und
den dort gespeicherten Workflow weiterverwenden. `start` nur verwenden, wenn
kein passender Vorgang besteht; die Skillauswahl erzeugt keinen neuen Vorgang.

Nur ausdrücklich beauftragte Modellierung ausführen. Original-CSV und bestätigte Fachregeln nicht still ändern. Beobachtungen und Agentenschätzungen sind keine bestätigte Semantik; dafür niemals `confirmations.confirmed=true` setzen. Freigaben nur aus tatsächlichen menschlichen Antworten zum konkreten Prüfstand festhalten. Unbestätigte externe Läufe zuerst aufklären, nicht erneut hochladen. Andere Komponenten nur über vorhandene Schnittstellen verwenden, deren Quellen nicht bearbeiten.

- Neuer eigenständiger Auftrag: `start(workflow=model)` mit Organisation und Themenidentifier; keine Datenpublikation hinzufügen.
- Modellierung innerhalb einer Themenintegration: vorhandenen `run_id` mit `workflow=topic` weiterverwenden, kein zweites `start`. Anschliessend zum gemeinsamen Review des [Themenintegrators](../datenportal-themenintegrator/SKILL.md#thema-integrieren) zurückkehren; beide Gates `metadata` und `model` bleiben erforderlich.
- Organisationsvorgänge können keine CSV modellieren.

## Vorbereiten und ableiten

1. Vor jeder CSV-Weitergabe und vor der Übernahme von Chat-Anhängen [Eingangsdateien](../../docs/agent/eingangsdateien.md) lesen und anwenden. Originale unverändert übernehmen, nur verifizierte Kopiepfade verwenden; ungültige CSV-Namen vor dem Integrator stoppen.
2. CSV und konkrete Datenblatt-XTF müssen denselben Vertrag haben. CSV mit `analyze` prüfen, HTML zeigen und am **CSV-Stopp** auf tatsächliche Freigabe für `gate=data` warten. Vorhandene aktuelle Freigaben wiederverwenden. Bei fehlendem Datenblatt oder Export [Metadaten und XLSX](../../docs/agent/metadaten-und-xlsx.md) zur Vorbereitung lesen. `derive_model` benötigt CSV, Datenfreigabe und exportierten Metadatenstand; diese Voraussetzungen nicht umgehen.
3. Vor der ersten Ableitung [Typableitung und Reparatur](references/typableitung-und-reparatur.md) lesen. Modellname, absolute AT-URI und ISO-Version klären. Standard: INTERLIS 2.4, `SO-DATA`, `VALIDATION`, eine flache Klasse. Gespeicherte Profile beibehalten. Beobachtete und vorgeschlagene Wertebereiche samt Begründung und Herkunft zeigen; fehlende Semantik gezielt klären.
4. Vor unbekannten Werkzeugaufrufen das verbindliche Schema lesen, bei zurückgehaltenen MCP-Werkzeugen zunächst deren Werkzeugsuche verwenden. `derive_model` erhält `run_id` und das Objekt `identity` mit `name`, `uri` und `version`; bestätigte Regeln und Schätzungen getrennt als `confirmations` beziehungsweise `range_proposals` übergeben. Keine flachen Argumentnamen wie `model_name` erfinden. Die Operation bereitet `.ili`, Modellreferenz im Datenblatt und `dataset.gradle` vor. Aktuelle Metadaten exportieren und `validate_model` mit vorhandenen Nachweisen und GRETL-CsvValidator verwenden. Keine bereits freigegebenen Dateien still nachbearbeiten.
5. Bei jeder Fehlprüfung Diagnose, anklickbaren absoluten Loglink und Reparaturstand im Chat nennen. Vor erneutem Ableiten konkrete Korrektur erklären und die Referenz heranziehen. Höchstens drei Korrekturversuche nach der ersten Fehlprüfung, gemeinsam für Compiler und CSV-Validator; Infrastrukturfehler oder ungeklärte Semantik zuerst klären.

## Review und Abschluss

HTML mit Modell, Task, aktualisiertem Datenblatt, Prüfresultaten und manuellen Reviewpunkten zeigen. **Modell-Stopp:** echte Antwort zum Kandidaten abwarten; Korrekturaufträge sind keine Freigaben.

Eigenständiger Vorgang: `approve(gate=model)` mit angezeigtem Fingerprint und tatsächlicher Aussage, dann nur beauftragte lokale Übernahme oder fachlichen PR vorbereiten. Vor Zielplan/PR oder Wiederaufnahme [Lieferung und Wiederaufnahme](../../docs/agent/lieferung-und-wiederaufnahme.md) lesen. Keine Datenlieferung starten.

Beim Themenvorgang den gemeinsamen Review und die beauftragte Fortsetzung dem Hauptablauf überlassen; eine separate Modellprüfung ersetzt keine Metadatenfreigabe.
