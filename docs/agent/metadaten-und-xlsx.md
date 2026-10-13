# Metadaten und XLSX

Vor Datenblattbearbeitung, XLSX-Auswertung, fehlendem Modelldatenblatt oder Office-Ergänzung lesen. Reine Metadatenänderungen benötigen keine CSV und keine Datenfreigabe. Reine Datenlieferungen behalten den vorhandenen Metadatenstand.

## Datenblatt bearbeiten

`baseline` liest den publizierten Vergleichsstand. `repository_datasheet(run_id)` findet dagegen das Datenblatt im Themenordner anhand des Modells `SO_AGI_DataCatalog_Datasheet_20260523` und des Themenidentifiers. Es liefert Pfad, Prüfsumme und fachliche Felder ohne bearbeitbaren Entwurf. Bei beauftragter CSV-Modellierung reicht diese Quelle: zum Modellskill wechseln, keinen Import/Export vorziehen. Erst `prepare_model_reference` bereitet nach erfolgreicher Modellprüfung eine Datenblattänderung vor. Die unterstützten Operationen stehen in `operation_schema("metadata")`; ihre Argumente in `operation_schema("metadata.<operation>")`. Entwurfs-ID und Revision verwaltet der Integrator. `describe_schema` ist ein Feldschema, kein Werkzeugkatalog. Für eine beauftragte Bearbeitung importiert `metadata(operation="import_xtf", arguments={})` die bereits gesicherte Quelle; keine XML-Zeichenkette übergeben. Vor unbekannten Feldänderungen `metadata(operation=describe_schema)` verwenden, danach Import/Erstellung und fachliche Bearbeitung ausschliesslich über den vorhandenen Datenblatt-MCP ausführen. JSON-/XTF-Dateien nicht als Ersatz für Datenblattoperationen direkt bearbeiten. Aktuelle Entwurfsrevisionen verwenden. Fehlende Fakten und Zuordnungen gezielt erfragen; Kontakte und Fachsemantik nicht erfinden.

XLSX als `metadata_source` aufnehmen. Mit `xlsx` Quellenkoordinaten und gespeicherte Formelresultate lesen; Formeln nicht ausführen. Fehlende Caches sind keine Nullwerte. Herkunft mit `provenance` markieren; LLM-Vorschläge ausdrücklich als solche kennzeichnen. Beschreibungen und belegte Einheiten im Datenblatt erhalten.

`metadata(operation=export_xtf)` sichert genau den vorgesehenen Stand als separates Kandidatenartefakt; keine Eingangsdatei als Ausgabeziel wählen. Geplante Repository-XTF vor dem Review mit `stage_change` registrieren. `validate` und HTML zeigen den konkreten Export, die Änderungen und ihre Herkunft. Erst nach tatsächlicher Freigabe übernehmen. Bei Korrektur erneut exportieren und prüfen.

## Dienststelle ergänzen

Für ein fehlendes Office im Themenvorgang zuerst `organization_schema` lesen und vorhandene Offices prüfen. Mit `office` bestätigte Pflichtangaben ergänzen; Organisation und Office-Identifier unterscheiden. Diese Ergänzung bleibt beim vorhandenen `run_id` und wird gemeinsam mit den Themenänderungen geprüft.

Ein Auftrag zur vollständigen neuen Organisation verwendet dagegen [datenportal-organisation](../../skills/datenportal-organisation/SKILL.md). Dafür bestätigte Kontakte und Berechtigungszuordnungen klären; fehlende Offices allein sind keine solche Beauftragung.
