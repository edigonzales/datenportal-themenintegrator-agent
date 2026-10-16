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
2. Verbindliche Quellenfolge: XLSX → Datenblatt → aktuelle exportierte XTF → Modell. XLSX dient ausschliesslich zur Datenblatterstellung; CSV-Beobachtungen und bestätigte Fachregeln ergänzen den XTF-Vertrag. Keine parallele Ableitung aus XLSX. Bei aktivem Vorgangsexport diesen direkt verwenden, weder `repository_datasheet` noch `attach(metadata)` erneut aufrufen. `status.metadata_source` nennt Quelle, Prüfsumme, Aktualität und nächsten Schritt. CSV und konkrete Datenblatt-XTF müssen denselben Vertrag haben. CSV mit `analyze` prüfen, HTML zeigen und am **CSV-Stopp** auf tatsächliche Freigabe für `gate=data` warten. Vorhandene aktuelle Freigaben wiederverwenden. Nur ohne aktive Vorgangsquelle bei vorhandenem Datenblatt im Themenrepo zuerst `repository_datasheet(run_id)` aufrufen. Die Operation erkennt Modell und Themenidentifier unabhängig vom Dateinamen, sichert die Originalbytes und liefert die fachlichen Felder. Bei mehreren Treffern einen aufgeführten `relative_path` auswählen. Kein `metadata(import_xtf)` oder Export zur Modellvorbereitung: das Datenblatt wird nur gelesen. `baseline` bleibt der publizierte Vergleichsstand und ersetzt diese Repository-Quelle nicht. Nur wenn ein Datenblatt fehlt oder fachlich geändert werden muss, [Metadaten und XLSX](../../docs/agent/metadaten-und-xlsx.md) lesen. `derive_model` benötigt CSV, Datenfreigabe und eine gesicherte Datenblattquelle; bereits bearbeitete Entwürfe müssen aktuell exportiert sein.
3. Vor der ersten Ableitung [Typableitung und Reparatur](references/typableitung-und-reparatur.md) mit `datenportal_workspace.read_file(path="skills/datenportal-validierungsmodell/references/typableitung-und-reparatur.md")` lesen. Auch `.agents/skills/…` und absolute Pfade im Operator-Workspace sind erlaubt; keine Pfadvarianten durchprobieren. Modellname, absolute AT-URI und ISO-Version klären. Standard: INTERLIS 2.4, `SO-DATA`, `VALIDATION`, eine flache Klasse. Gespeicherte Profile beibehalten. Beobachtete und vorgeschlagene Wertebereiche samt Begründung und Herkunft zeigen; fehlende Semantik gezielt klären.
4. Vor unbekannten Werkzeugaufrufen das verbindliche Schema lesen, bei zurückgehaltenen MCP-Werkzeugen zunächst deren Werkzeugsuche verwenden. `derive_model` erhält `run_id` und das Objekt `identity` mit `name`, `uri` und `version`; bestätigte Regeln und Schätzungen getrennt als `confirmations` beziehungsweise `range_proposals` übergeben. Keine flachen Argumentnamen wie `model_name` erfinden. Die Operation bereitet `.ili` und `dataset.gradle` vor und verändert das Datenblatt nicht. Zuerst `validate_model(run_id, scope="model")` ausführen; die Java-Vorprüfung prüft die CSV. Dieser Zwischenbericht ist keine Übernahme- oder Publikationsfreigabe. Erst nach erfolgreicher Prüfung `prepare_model_reference(run_id)` aufrufen: dieser eigene Schritt bereitet die Modellreferenz über den Datenblatt-MCP als Kandidat vor. Danach `validate_model(run_id, scope="integration")` für das vollständige Änderungspaket ausführen. Keine bereits freigegebenen Dateien still nachbearbeiten.
5. Argumentfehler (`model_arguments_invalid`, `INVALID_SPEC`) anhand des Feldpfads technisch korrigieren, ohne bestätigte Fachwerte zu ändern. Kein Reset und keine erneute Fachbestätigung für eine gleichwertige Darstellung. Bei jeder Compiler-/CSV-Fehlprüfung Diagnose, anklickbaren absoluten Loglink und Reparaturstand im Chat nennen. Vor erneutem Ableiten konkrete Korrektur erklären und die Referenz heranziehen. Höchstens drei Korrekturversuche nach der ersten Fehlprüfung, gemeinsam für Compiler und CSV-Validator; Infrastrukturfehler oder ungeklärte Semantik zuerst klären.

## Korrigierte CSV und Modelländerungen

Nur CSV geändert: im selben Vorgang verifiziert übernehmen, `attach(role=data)`, `analyze` und echte aktuelle Datenfreigabe; danach das vorhandene Modell mit `validate_model(scope="integration")` erneut prüfen. Fehlt die vorbereitete Modellreferenz noch, zuerst `validate_model(scope="model")`, `prepare_model_reference`, dann die Integrationsprüfung. Kein `derive_model`, kein `NOOP` und kein leerer Änderungsbatch zur Neubindung. Neue CSV entwertet abhängige Freigaben; erneute Prüfung ist keine Freigabe. Wertebereichs- oder UNIQUE-Verletzungen fachlich klären, nicht automatisch das Modell lockern.

Echte Modelländerung: vor dem Batch `operation_schema("derive_model.changes")` lesen. Die Auskunft enthält den vollständigen Integrator-Vertrag inklusive verschachtelter Definitionen. Unbekannte Felder nicht durch ausführende Probeaufrufe erraten; bei nicht verfügbarem Vertrag die Zugangs-/Versionsdiagnose klären. Bei unverändertem Datenblattvertrag übernimmt der Integrator gespeicherte Zahlenvorschläge und Bestätigungen; Ohne Modellabnahme bleiben Schätzungen unbestätigt. Die explizite Modellabnahme bestätigt enthaltene Zahlenbereiche und Dezimalgenauigkeiten; unveränderte Regeln bleiben bei gleichem Datenblattvertrag bestätigt. Neue Eindeutigkeit ist nur ein Vorschlag, bis ein Mensch die Fachregel bestätigt. `allow_breaking=true` nur bei ausdrücklich beauftragter Zulassung potenziell brechender Änderungen.

## Review und Abschluss


HTML mit Modell, Task, aktualisiertem Datenblatt, Prüfresultaten und manuellen Reviewpunkten zeigen. **Modell-Stopp:** echte Antwort zum Kandidaten abwarten; Korrekturaufträge sind keine Freigaben.

Eigenständiger Vorgang: `approve(gate=model)` mit angezeigtem Fingerprint und tatsächlicher Aussage, dann nur beauftragte lokale Übernahme oder fachlichen PR vorbereiten. Vor Zielplan/PR oder Wiederaufnahme [Lieferung und Wiederaufnahme](../../docs/agent/lieferung-und-wiederaufnahme.md) lesen. Keine Datenlieferung starten.

Beim Themenvorgang den gemeinsamen Review und die beauftragte Fortsetzung dem Hauptablauf überlassen; eine separate Modellprüfung ersetzt keine Metadatenfreigabe.

Prüfberichte dem Menschen mit `datenportal_workspace.open_report(path=...)`
zeigen und als absoluten Dateilink im Chat anbieten. Grosse MCP-Ergebnisse
verweisen mit `full_result_path` auf die vollständige Ausgabe; diese bei Bedarf
über `read_file` und `next_offset` abschnittweise lesen. Keine erneute Prüfung
zum Lesen auslösen und keine internen OpenCode-Ausgabedateien anfordern.

Vor einem beauftragten fachlichen PR `repository_plan(run_id, environment)` zeigen: konkretes Git-Repository, Remote, Zielbranch und offene Voraussetzungen. Das Profil wählt die Git-Zuordnung; `prepare_pr` benötigt keine Publikationsfreigabe. Themenvorgänge mit Repository-Änderungen benötigen aktuelle fachliche Freigaben und einen erfolgreichen lokalen Liefernachweis; eigenständige Modell-/Organisationsvorgänge nur ihre fachlichen Freigaben. Bereits erteilte PR-Aufträge nicht erneut bestätigen lassen. Lokal testen, PR erstellen und nach menschlichem Merge synchronisieren ist ein vollständiger Abschluss; INT/PROD werden dabei nicht angeliefert.

Nach menschlichem PR-Merge beim Fortsetzen `sync_repository(run_id, environment)`
aufrufen, auch für einen Abschluss ohne Veröffentlichung. Der Aufruf prüft Merge
und Zielbytes und synchronisiert das lokale Themenrepository. `status` bleibt
lesend. Bei `repository_sync` mit Phase `blocked` die Betreiberdiagnose zeigen und
keine neue Veröffentlichung starten; keine lokalen Änderungen mit Reset/Clean
verwerfen. Unterbrochene Synchronisierung mit demselben Vorgang wiederaufnehmen.

Bei `PROOF_INCOMPLETE` bestätigte Regeln erhalten und technische Ursache mit dem
konkreten Constraint-Nachweis benennen. Zustimmung ist kein technischer Override.
Keine wiederholten ADD_CONSTRAINT-Aufrufe oder manuellen stage_change-Reparaturen.
Nach Komponentenupdate `validate_model(scope="model", refresh_evidence=true)`
verwenden; erst bei Erfolg Referenz vorbereiten und Integration prüfen. Aktuelle
und historische Dateistände anhand `status.check_states` und Manifest unterscheiden;
Details stehen in der Referenz „Typableitung und Reparatur“.

Bei unverändert wiederkehrendem Fehler die konkrete Diagnose als Blocker melden. Keine Dateinamenvarianten oder Diagnose-Kopien erzeugen. Identifier- und Typfehler am Datenblatt anhand des öffentlichen Schemas korrigieren, danach exportieren.

### Modellabnahme und numerische Fachregeln

Die ausdrückliche Abnahme des gesamten vorgelegten Modells bestätigt auch seine
Zahlenbereiche und Dezimalgenauigkeiten. Keine zusätzliche Einzelabnahme verlangen.
Vor der Abnahme dürfen Agentenvorschläge nicht als menschliche Bestätigung erfasst
werden. Danach nennt `numeric_rule_confirmations` den effektiven Status und den
Freigabenachweis; `range_proposal.origin=agent_estimate` dokumentiert weiterhin die
Herkunft. Historisches `range_proposal.confirmed=false` ist kein aktueller Freigabestatus.
Unveränderte Regeln bleiben innerhalb des Vorgangs bei gleichem Datenblattvertrag
bestätigt. Ein geänderter Modellstand benötigt trotzdem seine reguläre Freigabe.
Widersprechende Grenzen oder Genauigkeiten fachlich klären, nicht automatisch lockern.
Bestehende aktuelle Modellfreigaben werden beim nächsten schreibenden Schritt
nachvollziehbar ergänzt; aus veralteten Freigaben wird keine Bestätigung abgeleitet.
Im Abschluss beispielsweise schreiben: «Die Zahlenbereiche und Genauigkeiten wurden
vom Agenten vorgeschlagen und mit der Modellabnahme bestätigt.»
