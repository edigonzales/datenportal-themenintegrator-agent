# Metadaten und XLSX

Vor Datenblattbearbeitung, XLSX-Auswertung, fehlendem Modelldatenblatt oder Office-Ergänzung lesen. Reine Metadatenänderungen benötigen keine CSV und keine Datenfreigabe. Reine Datenlieferungen behalten den vorhandenen Metadatenstand.

## Datenblatt bearbeiten

`baseline` liest den publizierten Vergleichsstand. `repository_datasheet(run_id)` findet dagegen das Datenblatt im Themenordner anhand des Modells `SO_AGI_DataCatalog_Datasheet_20260523` und des Themenidentifiers. Es liefert Pfad, Prüfsumme und fachliche Felder ohne bearbeitbaren Entwurf. Bei beauftragter CSV-Modellierung reicht diese Quelle: zum Modellskill wechseln, keinen Import/Export vorziehen. Erst `prepare_model_reference` bereitet nach erfolgreicher Modellprüfung eine Datenblattänderung vor. Die unterstützten Operationen stehen in `operation_schema("metadata")`; ihre Argumente in `operation_schema("metadata.<operation>")`. Entwurfs-ID und Revision verwaltet der Integrator. `describe_schema` ist ein Feldschema, kein Werkzeugkatalog. Für eine beauftragte Bearbeitung importiert `metadata(operation="import_xtf", arguments={})` die bereits gesicherte Quelle; keine XML-Zeichenkette übergeben. Vor unbekannten Feldänderungen `metadata(operation=describe_schema)` verwenden, danach Import/Erstellung und fachliche Bearbeitung ausschliesslich über den vorhandenen Datenblatt-MCP ausführen. JSON-/XTF-Dateien nicht als Ersatz für Datenblattoperationen direkt bearbeiten. Aktuelle Entwurfsrevisionen verwenden. Fehlende Fakten und Zuordnungen gezielt erfragen; Kontakte und Fachsemantik nicht erfinden.

XLSX als `metadata_source` aufnehmen. Mit `xlsx` Quellenkoordinaten und gespeicherte Formelresultate lesen; Formeln nicht ausführen. Fehlende Caches sind keine Nullwerte. Herkunft mit `provenance` markieren; LLM-Vorschläge ausdrücklich als solche kennzeichnen. Beschreibungen und belegte Einheiten im Datenblatt erhalten.

`metadata(operation=export_xtf)` prüft Identifier und unterstützte Attributtypen und sichert den aktuellen Stand als Kandidatenartefakt. Gleichzeitig registriert der Integrator genau einen Metadaten-Kandidaten im konfigurierten Themenrepo unter `<organisation>/<themenidentifier>/meta-<themenidentifier>.xtf`. Ein ausgewähltes Repository-Datenblatt behält seinen Zielpfad. Für einen ausdrücklich gewünschten Dateinamen `arguments={"repository_filename":"meta-beispiel.xtf"}` übergeben; keine Verzeichnisse angeben. Wiederholter Export aktualisiert denselben Kandidaten; ein neuer Zielname ersetzt die Vormerkung. Kein manuelles `stage_change` und keine Diagnose-Kopien für Metadaten. Erst nach tatsächlicher Freigabe kopiert `apply_local_changes` die geprüften Bytes ins Themenrepo. `validate` und HTML zeigen Export, Änderungen und Herkunft.

Verbindliche Quellenfolge: **XLSX → Datenblatt → aktuelle exportierte XTF → Validierungsmodell**. CSV-Beobachtungen und bestätigte Fachregeln ergänzen den XTF-Vertrag; XLSX-Angaben nicht parallel zur XTF für das Modell verwenden. Fachliche Korrekturen zuerst am Datenblatt vornehmen, dann erneut exportieren. Der Vorgangs-Identifier ist verbindlich; ein abweichender XLSX-Vorschlag ersetzt ihn nicht. `supported_data_types` im öffentlichen Schema nennt die verwendbaren Attributtypen.

`status.metadata_source` und die Exportantwort nennen aktive XTF, Prüfsumme, Aktualität, Repository-Ziel und nächsten Schritt. Ein aktiver Export braucht weder `repository_datasheet` noch erneutes `attach`. Identisches Anhängen erhält den Entwurf; eine andere Quelle bei bestehendem Entwurf wird abgelehnt. Bei `datasheet_identifier_mismatch` die Kennung korrigieren; bei `unsupported_type` den genannten Attributtyp anhand des Schemas klären. Bei unverändert wiederkehrendem Fehler Blocker mit Diagnose melden, keine alternativen Pfade ausprobieren. Mehrdeutige Altvormerkungen müssen gezielt durch den Betreiber bereinigt werden; keine state.json-Bearbeitung durch Geschäftsagenten.

## Dienststelle ergänzen

Für alleinige Ergänzungen oder ausdrücklich beauftragte Korrekturen in
`shared/data/offices.xtf` den eigenständigen Office-Ablauf im
[Themenintegrator](../../skills/datenportal-themenintegrator/SKILL.md#office-katalog-eigenständig-pflegen)
verwenden: `office_schema`, `start(workflow=office)`, `office(mode=create|update)`,
`validate`, HTML-Review und `approve(gate=office)`. Keine Organisation und keine
Teams erforderlich. Ein vollständiger Korrekturdatensatz enthält die bestätigten
Änderungen und alle übrigen unveränderten Bestandswerte. Identische Angaben sind
wirkungslos; der Identifier bleibt bestehen.


Für ein fehlendes Office im Themenvorgang zuerst `organization_schema` lesen und vorhandene Offices prüfen. Mit `office` bestätigte Pflichtangaben ergänzen; Organisation und Office-Identifier unterscheiden. Diese Ergänzung bleibt beim vorhandenen `run_id` und wird gemeinsam mit den Themenänderungen geprüft.

Ein Auftrag zur vollständigen neuen Organisation verwendet dagegen [datenportal-organisation](../../skills/datenportal-organisation/SKILL.md). Dafür bestätigte Kontakte und Berechtigungszuordnungen klären; fehlende Offices allein sind keine solche Beauftragung.

## Serien und Ausgaben

Der Themenidentifier bezeichnet die Serie. Der fachliche Ausgabe-Identifier
(`values.identifier`), die Bezeichnung (`values.issue_label`, etwa `2025`) und
die interne, vom MCP zurückgelieferte `issue_id` sind verschiedene Angaben.
Vorgangs-`issue` bezeichnet die Ausgabe für Lieferung und Auswahl; daraus wird
kein fachlicher Identifier abgeleitet. Fehlende Fachangaben gezielt erfragen.

Neue Ausgabe mit `metadata(operation=upsert_issue, arguments={values: …})`
ohne `issue_id` anlegen. Für erneutes Upsert denselben bestätigten fachlichen
Identifier verwenden; dadurch entsteht keine zweite Ausgabe. Bestehende
Ausgaben alternativ über die zurückgelieferte interne `issue_id` bearbeiten.
`update_metadata(issue_id=…)` prüft den Identifier dieser Ausgabe, nicht den
Themenidentifier. Vorhandene Identifier bleiben erhalten. Bei unbekannter
interner ID die vorhandenen Ausgaben lesen; `issue_id="2025"` ist keine
Abkürzung für eine neue Ausgabe. `issue_label` und `is_current_issue` gehören
zu `upsert_issue`, nicht auf die Serienebene.

`create_datasheet` und `import_xtf` sind nur ohne bestehenden Entwurf zulässig.
Bei vorhandenem Entwurf lesen, bearbeiten oder ausdrücklich `restore` verwenden.
Keine zweite Erstellung als Fehlerumgehung versuchen.
