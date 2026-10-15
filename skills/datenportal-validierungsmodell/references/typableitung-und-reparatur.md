# Typableitung und Reparatur

Vor der ersten Ableitung lesen; bei Fehlprüfungen den Reparaturabschnitt erneut heranziehen. Es gelten der vorhandene Vorgang, seine Datenbytes und bestätigten Fachregeln.

CSV und konkrete Datenblatt-XTF benötigen denselben Vertrag. Modellname, absolute AT-URI und ISO-Version klären. Standard für neue CSV-Validierungsmodelle: INTERLIS 2.4, Profil `SO-DATA`, Zweck `VALIDATION`, zunächst eine flache Klasse. Modellname endet auf `_YYYYMMDD`, passend zum gültigen VERSION-Datum: `SO_Messwerte_20261010`, `VERSION "2026-10-10"`, Themen-Datei `SO_Messwerte_20261010.ili`. Keine Kommentare, `iliDoc` oder beschreibenden Metaattribute in dieser `.ili`; Kontakte, Titel und Kurzbeschreibung sind dafür keine zusätzlichen Pflichtangaben. Beschreibungen und Einheiten im Datenblatt erhalten. Zulässige Profile sind ausschliesslich `CORE`, `SO-GIS` und `SO-DATA`. Der frühere Wert `SO` wird mit `model_profile` zurückgewiesen; nicht still ersetzen. Gespeichertes Profil vorhandener Vorgänge beibehalten; eine ausdrücklich beauftragte Umstellung erzeugt einen neuen Kandidaten und verlangt erneute Prüfung/Freigabe. Kommentare vorhandener Modelle mit dem typisierten Batch `REMOVE_COMMENTS` entfernen. Gemeinsames Serienmodell nur bei gleichen Verträgen.

`derive_model` verbindet bestätigte Datenblattangaben und gekennzeichnete Beobachtungen. Für numerische Spalten vollständige CSV-Statistik, Beschreibung und Einheit auswerten und unter `range_proposals.<spalte>` eine `type_spec` mit `baseType.kind=NUM_RANGE`, `min`, `max`, eine konkrete `reason` und `negative_values_possible` vorschlagen. Sinnvollen Spielraum für künftige Lieferungen begründen; keine pauschalen 64-Bit-Grenzen. Positive Stichproben allein belegen keine Nichtnegativität. Bei fehlender Fachgrundlage oder Widersprüchen gezielt nachfragen. Vorschläge sind Agentenschätzungen: niemals dafür `confirmations.confirmed=true` setzen. Vorhandene bestätigte Spezifikationen haben Vorrang. Beobachtungen begründen keine geschlossenen Codelisten, Schlüssel oder Pflichtigkeit. Zusätzliche Domains/Constraints nur mit bestätigter Semantik. Im Modellreview beobachtete und vorgeschlagene Grenzen, Genauigkeit und Begründung zusammen zeigen; die reguläre Modellfreigabe bestätigt den Kandidaten.

Der Adapter nutzt `authorIliModel` beziehungsweise `applyIliModelChanges`. Mitgelieferte Reviews/Compiler-/Constraint-Nachweise verwenden; nicht reflexartig weitere Low-Level-Prüfungen starten. Kandidaten mit Fehlern oder unvollständigen Proofs nicht übernehmen. Manuelle Reviewpunkte zeigen. Separat veränderte Quellen müssen neu geprüft werden.

Der Integrator liest bei `derive_model` die gesicherte Datenblattquelle und bereitet `.ili` und `dataset.gradle` vor; er eröffnet dabei keinen Datenblatt-Entwurf. `validate_model(scope="model")` prüft zunächst Modell und CSV mit der lokalen Java-Vorprüfung. Erst danach setzt der eigene Schritt `prepare_model_reference` die Modellreferenz über den Datenblatt-MCP und exportiert den Kandidaten. `validate_model(scope="integration")` prüft das gesamte Änderungspaket vor dem Review. Der vorbereitete GRETL-Task prüft später eigenständig in Jenkins und stoppt bei Fehlern vor `preparePublicationWorkspace`. Metadatenlieferungen überspringen die CSV-Task. Kein eigener CSV-zu-XTF-Umbau für diese Prüfung.

Der erzeugte Gradle-`Copy`-Task `stageThemenCsv` stellt endungslose Jenkins-Uploads bytegleich als `.csv` für den Validator bereit. Bei älteren erzeugten Taskdefinitionen ohne diesen Schritt die Modellableitung mit der bestehenden Identität erneut beauftragen, prüfen und die geänderte Task zur erneuten menschlichen Freigabe vorlegen. Keine bereits freigegebenen Repository-Dateien still nachbearbeiten.

Bei **jeder Fehlprüfung** im Chat eine kurze Diagnose, einen anklickbaren absoluten Loglink aus `diagnostic_history.artifacts` und den Reparaturstand nennen, auch wenn das HTML dieselben Links enthält. Beispiel (Platzhalter durch tatsächlichen Pfad ersetzen): „ili2c: unbekannter Typ. [Diagnoseprotokoll][fiktiver-diagnosepfad]. Noch kein Korrekturversuch; maximal drei.“ Vor dem nächsten Versuch die konkrete Korrektur erläutern. `derive_model` mit `repair_of` (letzte `check_id`) und `repair_reason` aufrufen. Höchstens drei Korrekturversuche nach der ersten Fehlprüfung, gemeinsames Budget für ili2c und die lokale CSV-Validierung; `status` nach Wiederaufnahme lesen. Eingangs-CSV und bestätigte Fachregeln nicht still ändern. Bei Infrastruktur-/Zugangsfehlern oder ungeklärter Semantik klären statt wiederholt Modelle zu ändern. Nach Erfolg vorherige Fehlerlogs weiterhin verlinken. Nach drei erfolglosen Korrekturen verbleibende Ursache, bisherige Korrekturen und letzten Loglink nennen und stoppen. Nur ein tatsächlicher ausdrücklicher menschlicher Neuauftrag erlaubt `reset_model_repair(run_id, human_statement)`; keine Antwort erfinden. Neue Prüfungen ändern für sich allein keine Freigabe; Änderungen am Modell oder an Vorschlagsbegründungen brauchen einen neuen Review.

[fiktiver-diagnosepfad]: /absoluter/vorgang/diagnostics/pruefkennung/diagnostics.log

## Argumentdarstellung und technische Korrekturen

`operation_schema("derive_model")` beschreibt auch die verschachtelten Spaltenargumente.
Bestätigte Zahlenbereiche bleiben `confirmations`, auch nach einem technischen Fehler;
sie werden nicht nachträglich als unbestätigte `range_proposals` umdeklariert.

```json
{
  "confirmations": {
    "GDENR": {"confirmed": true, "type_spec": {"baseType": {"kind": "NUM_RANGE", "min": "1", "max": "9999"}}},
    "GDENAME": {"confirmed": true, "type_spec": {"baseType": {"kind": "TEXT", "length": 100}}},
    "FZART_CD": {"confirmed": true, "type_spec": {"baseType": {"kind": "TEXT", "length": 2}}}
  }
}
```

`confirmed=true` setzt eine tatsächliche menschliche Bestätigung voraus. Für eigene
Zahlenschätzungen stattdessen `range_proposals.<Spalte>` mit derselben `type_spec`,
`reason` und `negative_values_possible` verwenden. Dezimalgrenzen als Strings mit
gleicher Dezimalstellenzahl angeben, etwa `"0.00"` und `"99999.99"`; die Genauigkeit
liegt in diesen Grenzen. Kein separates `precision` nötig. Benannte Domains über
`type_spec.domainFqn` statt `baseType` referenzieren. Keine flachen `min`, `max`,
`max_length` oder zusätzlichen Felder erfinden.

Der Integrator normalisiert eindeutig gleichwertige Altformen (`NUMERIC` mit
vollständigen Grenzen, `maxLength`, dazu passende `precision`) ohne Änderung der
Fachwerte. Widersprüchliche Angaben werden abgewiesen. Bei `model_arguments_invalid`
oder `INVALID_SPEC` den genannten Argumentpfad korrigieren; dies ist noch keine
Compiler- oder CSV-Fehlprüfung. Eine technische Formatkorrektur braucht keine neue
Fachbestätigung. Fehlerhafte Eingaben ersetzen keinen bestehenden Modellkandidaten.

`reset_model_repair` setzt ausschliesslich das Budget einer Compiler-/CSV-Reparaturserie
zurück. Es löscht weder Modell noch bestätigte Fachregeln und behebt keine Argumentfehler.
Modellname, AT-URI und Versionsdatum müssen konsistent sein; einen Widerspruch mit
dem Benutzer klären, niemals das bestätigte Datum still ändern.

## Bestehendes Modell: Daten neu prüfen oder Fachregel ändern

Eine korrigierte CSV verlangt bei unverändertem Datenblattvertrag keine neue
Modellableitung. Nach Übernahme, Analyse und echter Datenfreigabe
`validate_model(scope="integration")` aufrufen. Fehlt die Modellreferenz, zuvor
`scope="model"` und `prepare_model_reference` ausführen. Die ursprüngliche
Ableitungsquelle bleibt `source_data_sha256`; `validated_data_sha256` benennt die
zuletzt insgesamt erfolgreich geprüfte CSV. Fehlprüfungen ersetzen diese Bindung
nicht. Der Bericht zeigt die aktuelle CSV-Prüfsumme und aktuelle Beobachtungen
getrennt von den ursprünglichen Ableitungsbeobachtungen. Neue Prüfungen erteilen
keine Freigabe; die regulären Modell-/Metadatenstopps bleiben bestehen.

Bei echten Modelländerungen zuerst `operation_schema("derive_model.changes")`
lesen. Es liefert das vollständige `derive_model`-Argumentobjekt mit dem vom
konfigurierten INTERLIS-MCP übernommenen `changes`-Vertrag und allen referenzierten
Definitionen. Keine Schemaerkundung durch `{}`, `NOOP` oder unvollständige
Änderungsaufrufe. Gespeicherte Zahlenvorschläge werden bei unverändertem Vertrag
wiederverwendet und an den aktuellen Beobachtungen geprüft. Sie bleiben
`agent_estimate`, nicht menschlich bestätigte Regeln.

Beispiel für das Argument `changes` nach ausdrücklich bestätigter BFS-Eindeutigkeit
(Modell-/Klassenname an den vorhandenen Kandidaten anpassen):

```json
[
  {
    "operation": "ADD_CONSTRAINT",
    "addConstraint": {
      "containerFqn": "SO_Bevoelkerung_Gemeinde_Alter_20261015.Data.Observation",
      "constraint": {
        "kind": "UNIQUE",
        "name": "UniqueBfsNr",
        "scope": "GLOBAL",
        "keyPaths": ["BFS_Nr"]
      }
    }
  }
]
```

Diese Darstellung wurde gegen den öffentlichen Vertrag von INTERLIS-MCP
0.0.577-1 und mit einem entsprechenden echten Änderungsaufruf im isolierten Test
geprüft. Der aktuell konfigurierte Vertrag bleibt verbindlich. Ein neues UNIQUE
kann zuvor gültige Daten zurückweisen; `allow_breaking=true` im Integrator-Aufruf
setzt einen ausdrücklichen menschlichen Auftrag auch für diese Auswirkung voraus.

## Unvollständige Nachweise und Dateistände

`PROOF_INCOMPLETE` ist keine Aufforderung, eine bestätigte Fachregel zu entfernen.
Den betroffenen Constraint und dessen `reasonCode` aus `constraintProofs` nennen,
einen absoluten Diagnoselink zeigen und die technische Ursache klären. Eine Antwort
„mit UNIQUE weiterfahren“ erhält den Auftrag und die Regel; sie ersetzt keinen
Nachweis und ist kein Override für `approve` oder `prepare_model_reference`.
Keine weiteren Auswahlfragen zur Entfernung stellen, wenn die Regel bestätigt ist.

Nach einer tatsächlichen Komponentenreparatur im selben Vorgang
`validate_model(run_id, scope="model", refresh_evidence=true)` verwenden. Diese
Operation prüft die vorhandenen Modellbytes über `verifyIliModel`, ohne zusätzliche
Constraints einzufügen. Bei Erfolg folgen `prepare_model_reference` und
`validate_model(scope="integration")`, dann der neue menschliche Modellreview.
Ohne geänderte Prüfkomponente oder geklärte Ursache keine unveränderten Versuche
wiederholen. `derive_model` ist für fachliche Änderungen, nicht zum Neubinden
von Task-Prüfsummen. Modell und Task nicht mit `stage_change` nachregistrieren.

`status.check_states` unterscheidet aktuelle und veraltete Prüfungen. Das Manifest
bindet Daten, Datenblatt, Modell, Task, Repository-Abhängigkeiten, Prüfparameter
und Komponenten an einen Stand. Gleicher Inhalt an anderem Pfad erzeugt keine
neue Dateiversion. Eine neue CSV entwertet abhängige Freigaben; alte erfolgreiche
Berichte bleiben Historie, sind aber kein Nachweis für neue Dateien. Fehlende
Manifeste alter Modellprüfungen verlangen erneute Prüfung. Bestehende echte
CSV-Freigaben werden nur bei weiterhin passendem Fingerprint wiederverwendet.
Repository-Konflikte gezielt klären, keine fremden Änderungen überschreiben.
