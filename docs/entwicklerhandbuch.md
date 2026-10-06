# Entwicklerhandbuch

## Architektur und Verantwortlichkeiten

JDK 25, Gradle-Wrapper und ein ausführbares JAR bilden die Implementierung. `Main` stellt CLI und stdio-MCP bereit. Beide dispatchen über `Operations` in denselben `Workflow`. Das Sprachmodell und die fachliche Kommunikation bleiben im Harness. Es gibt keinen eigenen Modellclient und keinen zusätzlichen Integrator-Webserver.

| Komponente | Verantwortung |
|---|---|
| `Settings`, `Json`, `Problem`, `ProcessRunner` | TOML, Pfadgrenzen, Prüfsummen, atomare Speicherung, strukturierte Fehler und externe Prozesse |
| `Store` | Vorgänge, Dateiartefakte, Sperren, Ereignisse und explizite Migration |
| `Workflow` | Operationen, Prüfstände, Freigabeabhängigkeiten, Kandidaten und lokale Übernahme |
| `Csv`, `Xml`, `Validator`, `Reports` | Vollständige technische CSV-Prüfung, sicherer XML-Leser, externe ilivalidator-Aufrufe, XLSX/HTML |
| `McpClients` | Getrennte Adapter für Datenblatt-HTTP-MCP und INTERLIS-stdio/HTTP-MCP über das offizielle Java-MCP-SDK |
| `Models` | Typisierte flache Ableitung, Herkunft, High-Level-Nachweise, Modellreferenz und GRETL-Themenhook |
| `Organizations` | Office-Regeln aus kompiliertem Modell, Org-/Teamkandidaten und Gradle-Prüfung |
| `Converters`, `CsvConverter`, `ConverterMain` | Java-Konvertervertrag, Kompilierung, JUnit und separate Ausführungs-JVM |
| `Workspace`, `GretlRuntime` | Isolierte Arbeitskopie und Übernahme des vorhandenen Jenkins-Offline-Bundles |
| `Stack`, `Jenkins`, `Deliveries`, `Repository` | Vorhandene Runtime-Schnittstellen, persistierte Lieferphasen, Sichtbarkeitsprüfung und menschliches PR-Verfahren |
| `Setup` | Werkzeuginstallation, isolierter Datenblatt-MCP-Start und Harness-Konfiguration |

Alle Quellen liegen unter `src/main/java/ch/so/agi/integrator/`, JUnit unter `src/test/java/`. Fachliche Konverter liegen optional unter `topics/<organisation>/<identifier>/`. Regeln, Zusatzmodell und HTML-Vorlage bleiben im Integrator-Repo. Das Themenrepo ist die verbindliche Quelle für Organisationen, Dienststellen, Teams und Datenblätter; der Integrator pflegt keine zweite Kopie.

## Bestehende externe Schnittstellen

Keine Quellen von Dev-Stack, Fachdiensten, Jenkins-Plugin, GRETL oder Portal werden geändert. Änderungen an diesen Komponenten sind separate Aufträge. Relevante Grenzen werden als konkrete Fehler gemeldet.

- Datenblatt-MCP: bestehendes HTTP-MCP, `describe_schema`, `import_xtf`, `read_datasheet`, `update_metadata`, Attribut-/Ausgabenwerkzeuge, Validierung und Export. Der Integrator erzeugt nur die Transferidentität eines leeren Imports mit gültigem OID-Präfix; fachliche Datenblätter und ihre Exporte verantwortet der Fach-MCP.
- INTERLIS-MCP: eigenes SDK-Client/Transport-Paar; standardmässig konfiguriertes JAR über stdio, optional Streamable HTTP. `authorIliModel` und `applyIliModelChanges` liefern Compiler-/Regel-/Constraint-Nachweise. Bei separat geändertem Quellstand `reviewIliModel`; unvollständige Constraint-Beweise verlangen weiterhin High-Level-Änderung/Prüfung.
- GRETL: vorhandenes Java-17-Skript, Repository-Wrapper und `shared/gradle/init.gradle`; Versionen aus `shared/gradle/gradle-build.properties`. `setup-gretl` kopiert das vorhandene Jenkins-Offline-JAR-Bundle in ignorierte Integrator-Dateien. Es wird nicht neu gebaut oder aktualisiert.
- Stack: Docker-/Compose-Status, Mount und `THEMEN_REPO_MODE`, vorhandenes `scripts/up.sh`, normale Starts vorhandener Services. Bei passender laufender Instanz keine neue Instanz. Unpassender Checkout/Modus stoppt.
- Jenkins: CSRF-Crumb, vorhandener Seed-Job, `gretl-datenportal/build` als Multipart, `gretl-datenportal/runStatus`, Queue-/Build-API und `report.json`. Authentisierung nur an die konfigurierte Jenkins-Basis; Redirects werden nicht verfolgt.
- Portal/Manifest: HTTP-Lesen von `current.json`, referenzierten Katalogen und Datenblättern, `catalog/published-catalog.xtf`, Themenseiten und Downloads. Nur HTTP 404 bezeichnet einen fehlenden Erstbestand. Die Metadatenprüfung ignoriert ausschliesslich die von GRETL verwalteten `issued`/`modified`-Werte und normalisiert Collections ohne die Reihenfolge der Attribute zu ändern.
- Git/`gh`: isolierter Clone des Zielbranches, exakte Kandidaten, Commit, Push und PR. Kein automatischer Merge. Nach menschlichem Merge Head, Zielbranch und exakte Blob-Bytes prüfen.

Der [GRETL-`CsvValidator`](https://github.com/edigonzales/gretl-next/blob/main/gretl-core/src/main/java/ch/so/agi/gretl/tasks/CsvValidator.java) verwendet ilivalidator mit dem bestehenden CSV-Reader-Adapter. Der Integrator führt keine eigene CSV-zu-XTF-Konvertierung ein.

## Vorgänge und gespeicherte Zustände

`state_dir/<32-stellige-hex-id>/state.json` enthält Schema-Version 2. `workflow` ist `topic`, `model` oder `organization`. Organisationsvorgänge haben keinen Themenidentifier und keine Datenlieferung. Weitere Felder:

| Feld | Inhalt |
|---|---|
| `files` | Inhaltsadressierte Original-/Bearbeitungsartefakte mit Pfad, SHA-256 und Originalname |
| `changes` | Kandidatenpfad, erwartete Ausgangs-SHA, Kandidaten-SHA und Vorher/Nachher-Inhalt |
| `draft`, `draft_base`, `exported_revision` | Letzter bestätigter Datenblatt-Snapshot, XML-Identität und exportierte Revision |
| `pending_metadata`, `pending_model` | Vor dem externen Aufruf persistierter, noch unbestätigter Auftrag |
| `checks`, `approvals` | Ergebnisse und menschliche Freigaben für konkrete Fingerprints |
| `baseline`, `provenance` | Angenommener Ausgangsstand und Herkunft fachlicher Angaben |
| `model`, `organization_spec`, `transform` | Ableitungsstand, bestätigte Organisationsdaten oder Konverternachweis |
| `deliveries`, `seeds` | Externe Queue-/Build-Kennungen und fortsetzbare Phasen |
| `publication_plans`, `pull_request` | Freigegebenes Ziel und PR-Stand |
| `events`, `delivery_history`, `historical_approvals` | Chronik, frühere Versuche und Migrationshistorie |

Dateiartefakte sind unveränderlich: beim Zugriff erneut SHA-256 prüfen. State wird mit exklusiver Dateisperre und atomarem Rename geschrieben; die temporäre Datei wird vor dem Rename synchronisiert. Auch ein fachlicher Fehler nach externem Start muss den Journalstand speichern. Unter demselben Vorgang keine konkurrierenden Änderungen ausführen.

Fingerprints beziehen Dateien, Vorgangszuordnung, relevante Einstellungen und Werkzeugstände, Regeln/Modelle, Shared-/Org-Konfiguration, Themenrezepte, Baseline, Transformation und bei fachlichen Gates Kandidaten/Herkunft ein. Das Daten-Gate bindet die CSV und deren Verarbeitung; Metadatenherkunft allein entwertet es nicht. Publish-Gates enthalten zusätzlich den konkreten Zielplan. Kandidatenbytes gelten auch nach identischer lokaler Übernahme; dadurch macht das Kopieren einer freigegebenen Änderung die Freigabe nicht selbst ungültig.

Gates: `data`, `metadata`, `model`, `organization`, `publish:<profil>`. Eine Freigabe benötigt einen aktuellen erfolgreichen Check und eine tatsächliche menschliche Aussage. Änderungen entwerten betroffene Freigaben durch Fingerprint-Vergleich. Ein nicht exportierter oder unbestätigter Datenblattentwurf blockiert fachliche Gates. Beim Themenvorgang mit Modell verlangen lokale Übernahme und Lieferung beide fachlichen Gates. Freigaben sind Workflow-Kontrollen und keine unabhängige menschliche Authentisierung.

### Lieferphasen

`seed_submitting → seeding → delivery_submitting → running → complete|failed`. Vor jedem möglicherweise mutierenden HTTP-Aufruf wird die unbestätigte Phase gesichert. Ein zweiter Aufruf mit unbekanntem Start liefert `submission_unknown`; er sendet keinen zweiten Upload. `reconcile`/`reconcile_seed` ordnen einen passenden bestehenden Lauf zu. Bei bestätigtem Start nur dessen Kennung weiter abfragen.

Beim Upload werden Datenblattinhalt und Datenartefakt für die spätere Prüfung eingefroren. Neu angehängte Dateien verändern den Prüfkontext eines bereits gestarteten Laufs nicht. `publication=accepted` und ein fehlgeschlagener Reload sind getrennte Zustände. `verify_delivery` prüft eine bestätigte Publikation erneut ohne Upload. Ein fehlender Publikationsbericht erlaubt keine sichere neue Lieferung. `retry_delivery` verlangt einen eindeutig fehlgeschlagenen, nicht publizierten Versuch und aktuelle Freigaben.

### Explizite Migration

`migrate_run(run_id, apply=false)` liefert Vorschau; `apply=true` schreibt zuerst eine Schema-1-Sicherung. Artefakte, Entwürfe, pending-Aufträge und externe Laufkennungen bleiben erhalten. Alte Freigaben werden historisch übernommen und neue Checks/Freigaben verlangt. Bereits laufende/abgeschlossene Lieferungen bleiben unter derselben Kennung. Python-Konverter werden markiert und nicht ausgeführt. Eine Migration ist kein Upload und keine automatische fachliche Freigabe.

## CLI und MCP

CLI-Grammatik:

```sh
java -jar build/libs/datenportal-integrator.jar [--config PATH] doctor
java -jar build/libs/datenportal-integrator.jar schema OPERATION
java -jar build/libs/datenportal-integrator.jar [--config PATH] call OPERATION --json '{}'
java -jar build/libs/datenportal-integrator.jar [--config PATH] call OPERATION --args-file args.json
java -jar build/libs/datenportal-integrator.jar [--config PATH] call OPERATION --args-file -
java -jar build/libs/datenportal-integrator.jar [--config PATH] serve
```

`--args-file -` liest stdin. Keine Shell-Interpolation von Nutzerwerten; externe Befehle verwenden Argumentlisten. CLI liefert UTF-8-JSON auf stdout. Fehler: `{ "error": "code", "message": "…", …details }`. Exit-Codes: 0 Erfolg, 2 `Problem`, 1 unerwarteter Fehler ohne Credential-Traceback. MCP verwendet dieselben JSON-Ergebnisse und bei Fehlern zusätzlich `isError=true`. Das SDK stellt Tools über stdio bereit; stdout bleibt dem Protokoll vorbehalten.

Das verbindliche Schema wird in `Operations.ALL` definiert; unbekannte Felder und falsche Argumenttypen werden abgewiesen. `schema OPERATION` zeigt exakt dieses Schema. Alle Operationen ausser `start` und `organization_schema` benötigen `run_id`. Ein Stern bezeichnet im folgenden weitere Pflichtargumente.

| Operation | Argumente neben `run_id` | Ergebnis / wichtige Fehler |
|---|---|---|
| `start` | `organization*`, `workflow=topic`, `identifier` (topic/model nötig), `issue`, `source_environment=local`, `data_path`, `metadata_path` | Vorgang mit `id`; `invalid_identifier`, `invalid_workflow` |
| `status` | – | State + `current_approvals`; `migration_required`, `artifact_changed` |
| `attach` | `path*`, `role*` | Artefakt; `invalid_role`, `file_missing` |
| `baseline` | – | Baseline und Verfügbarkeit; `manifest_invalid`, `ambiguous_repository_metadata` |
| `analyze` | – | Vollständiger CSV-Bericht, Fingerprint, HTML-Pfad; oder `skipped: metadata_only` |
| `provenance` | `entries*` | Feldherkunft; `provenance_invalid` |
| `xlsx` | `sheet`, `offset=0`, `limit=100` | Blätter oder Zellen mit Koordinaten/Formelcache; `invalid_page`, `invalid_xlsx` |
| `metadata` | `operation*`, `arguments={}` | Fach-MCP-Ergebnis/Exportartefakt; `approval_required`, `metadata_uncertain`, `draft_conflict`, `mcp_unavailable` |
| `validate` | – | XTF/Office/CSV/Modell-Bericht + Vorschau; `draft_not_exported`, `metadata_missing` |
| `approve` | `gate*`, `fingerprint*`, `human_statement*` | Freigabe; `stale_review`, `validation_required`, `approval_required`, `local_test_required` |
| `stage_change` | `relative_path*`, `source_path*` | Kandidat + Ausgangs-/Zielhash; `change_not_allowed`, `unsafe_path` |
| `apply_local_changes` | – | Übernommene Dateien, ggf. `repository_created`; `repository_conflict`, `approval_required` |
| `transform` | `converter*`, `tests*`, `policy*`, `instructions*` | Nachweise, Lieferantenvorgabe, Vorher/Nachher; `converter_tests_failed`, `converter_output_invalid`, `converter_migration_required` |
| `organization_schema` | `organization`, `values={}` | Office-Regeln aus Compiler, vorhandene Teams/Offices, fehlende Angaben |
| `prepare_organization` | `values*` | Kandidaten; `organization_exists`, `unknown_team`, `empty_team`, `users_unconfirmed`, `team_conflict`, Office-Fehler |
| `validate_organization` | – | Office-/YAML-/Gradle-Bericht + HTML; `organization_missing` |
| `derive_model` | `identity*`, `confirmations={}`, `changes`, `allow_breaking=false` | Ableitungsnachweise oder `NEEDS_INPUT`, Kandidat, Vorschau |
| `validate_model` | – | Compiler-/Reviewnachweise + echte GRETL-/XTF-Prüfung; `model_missing`, Kandidaten-/Vertragsfehler |
| `publication_plan` | `environment*` | Konkretes Ziel mit Fingerprint; `environment_disabled`, `runtime_incompatible` |
| `prepare_pr` | `environment*` | URL, Head, Branch, Checkout; `pr_base_conflict`, `pr_checkout_exists` |
| `deliver` | `environment=local` | Aktuelle Lieferphase; `wrong_workflow`, `submission_unknown`, `human_merge_required`, `target_changed` |
| `seed` | `environment=local` | Einmaliger Seed bzw. gespeicherte Phase; kein Datenupload |
| `reconcile` | `environment*`, `job*`, `queue*` | Zugeordnete Lieferung; `wrong_delivery`, `reconcile_not_needed` |
| `reconcile_seed` | `environment*`, `queue*` | Zugeordneter Seed; `wrong_seed`, `invalid_queue` |
| `verify_delivery` | `environment*` | Erneute Sichtbarkeitsprüfung; `publication_unconfirmed` |
| `retry_delivery` | `environment*` | `ready_for_new_attempt`; `retry_unsafe` |
| `migrate_run` | `apply=false` | Vorschau/Sicherung, Historie; `unsupported_state_version` |

Werkzeughelfer: `setup-java`, `setup-tools`, `setup-gretl`, `start-datasheet [--source PATH]`, `harness-config`, `codex [harness-argumente]`. Diese sind CLI-Helfer, keine zusätzlichen MCP-Fachoperationen.

## Modellierung und GRETL-Hook

Die erste Umsetzung erzeugt eine Topic mit einer flachen Klasse. Keine aus Beispielen erfundenen Schlüssel, Fachwertebereiche, geschlossenen Codelisten oder Geometrien. INTEGER hat den technischen CSV-Vertrag mit 64-Bit-Grenzen. Für konkrete DECIMAL-/NUMERIC-Domains werden Wertebereich und Genauigkeit erfragt; beobachtete Grenzen werden nicht automatisch übernommen. DATE verwendet INTERLIS.XMLDate. Die Standarddomain INTERLIS.XMLDateTime unterstützt im bestehenden Adapter den CSV-Offset +01:00 nicht; der Integrator meldet diese Grenze ohne Formatänderung. Fachliche Beschreibungen und belegte Einheiten werden übernommen. Zusätzliche Domains/Constraints brauchen eine bestätigte Spezifikation.

Die High-Level-Antwort wird vollständig gesichert. Compiler-Evidenz und automatisches SO-Review müssen erfolgreich sein; unvollständige Proofs bleiben Kandidaten. Manuelle Reviewpunkte bleiben für den Menschen sichtbar. Der Adapter berücksichtigt Compiler-Evidenz auch dann, wenn der Fach-MCP keinen separaten `compilerValid`-Boolean im Review liefert.

Nach erfolgreicher Ableitung setzt der Datenblatt-MCP die Modellreferenz, exportiert erneut, und diese konkrete XTF wird zusammen mit Modell und Task geprüft. Der Ableitungsvertrag bindet Attributsemantik und die Datenbytes; das abschliessende Gate bindet zusätzlich den konkreten finalen Export. Dadurch entsteht kein Zyklus aus „Modell hängt vom Export ab, Export hängt vom Modell ab“.

`dataset.gradle` wird ausschliesslich im bestehenden Themenordner vorbereitet. Ein markierter Integrator-Block kann ersetzt werden; fremder Hook-Inhalt bleibt erhalten, konkurrierende Taskdefinitionen verlangen Klärung. Wegen des eigenen Klassenpfads angewandter Gradle-Skripte wird `CsvValidator` über den bereits geladenen GRETL-Plugin-Classloader bezogen. Der Task ist eine Abhängigkeit von `preparePublicationWorkspace`, verwendet UTF-8, Header, Semikolon, doppelte Quotes und `failOnError=true`. Sein Log liegt ausserhalb des vom Publikationsablauf geleerten Verzeichnisses. Ohne CSV wird ausdrücklich übersprungen.

Frühe Prüfung und Jenkins prüfen dieselben Modellbytes und dieselbe Validator-Konfiguration. Das vorhandene Offline-Bundle wird in der Konfiguration/Freigabe über Inhaltsprüfsummen gebunden. Im Themen-Task kann der GRETL-Binärstand vor der Veröffentlichung gegen den frühen Stand geprüft werden. Bei abweichendem Runtime-Stand anhalten und Betreiber informieren.

## Organisationen

Office-Regeln werden aus dem tatsächlich konfigurierten Basismodell mit ili2c gelesen; `offices.xtf` bleibt die Referenz. Identifier, Name und Abkürzung müssen eindeutig sein. Pflichtigkeit, URI-Typ und Textlängen werden vor dem externen XTF-Validator geprüft.

Neue Organisationsdateien entsprechen dem Jenkins-Vertrag und binden `organisation-common.gradle` ein. Keine `defaultDataset`-Angabe ohne Thema. Bestehende Teams bleiben erhalten; neue Mitgliedschaften erfordern bestätigte Kennungen. YAML wird mit SafeConstructor und abgewiesenen doppelten Schlüsseln gelesen. Gradle läuft in einer isolierten Repository-Kopie. Ein erfolgreicher Org-Check erzeugt keinen fiktiven Jenkins-Job. Der Status erklärt die bestehende Plugin-Grenze für Organisationen ohne Thema.

Erlaubte Kandidaten: Office-/Teamkatalog, eigene Org-Dateien und im eigenen Themenordner XTF/XML, ILI und `dataset.gradle`. Keine Integrator-Konfigurationsdateien oder Shared-Gradle-Änderungen im Themenrepo.

## Konverter, Regeln und Umgebungen erweitern

Ein Konverterrezept nennt `class_name` und relative `sources`. Die Klasse implementiert `CsvConverter.convert(Path, Path)`. Konverter und JUnit-Tests werden mit `javac --release 25` kompiliert. JUnit läuft in einer separaten JVM mit `--fail-if-no-tests`; erst dann wird konvertiert. Anschliessend Eingabehash und CSV-Ausgabe prüfen. Fachliche Ergebniswerte müssen durch aussagekräftige JUnit-Tests belegt sein. Ein Konverter ist ausführbarer Code und hat die Rechte des Integrator-Prozesses; der Workflow ist keine Sandbox.

Regeln liegen unter `config/rules.json`; technische CSV-Regeln im Java-Leser und diese Konfiguration gemeinsam ändern. In `validation/` liegt das additive INTERLIS-Office-Prüfmodell samt ini; Basismodelle und Fach-MCPs nicht verändern. Neue fachliche Regeln brauchen positive und negative Datenfälle und eine verständliche Fehlermeldung.

Neue Umgebungsprofile unter `[environments.<name>]` in ignorierter TOML konfigurieren. Versionierte Beispiele enthalten nur Adressen, Branch, Seed-Job, Runtime-Modus und Credential-Variablennamen. `enabled=false` lässt ein Profil unbenutzbar. Lokale Profile verlangen Loopback-Adressen; INT/PROD verlangen `managed-git`, deaktiviertes Git-Rückschreiben, menschlichen Merge und ausdrücklich freigegebenen Zielplan. Das Hinzufügen eines Profils passt keine externe Runtime an. Der vom Benutzer gewählte Konfigurationsstand und Werkzeugstände werden durch die Freigaben gebunden.

## Entwicklung und Prüfungen

```sh
export JAVA_HOME="/pfad/zum/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test jar spotlessCheck
./gradlew spotlessApply
# Abhängigkeiten nur nach bewusster Änderung neu sperren:
./gradlew dependencies --write-locks
```

`gradle.lockfile` bindet Bibliotheksversionen; CLI/MCP kommen aus demselben JAR. Das offizielle Java-MCP-SDK ist bewusst ausserhalb der Fach-MCP-Prozesse geladen; deren eigene ili2c-/ilivalidator-Versionen werden nicht in denselben JVM-Prozess eingebettet.

Unit-/Funktionsprüfungen verwenden isolierte Repositories und deutlich bezeichnete Testfreigaben. Simulierte Fachantworten testen Gate- und Fehlerverhalten; sie beweisen keine tatsächliche Modellvalidität. Echte Integrationstests laufen separat:

```sh
java -jar build/libs/datenportal-integrator.jar setup-tools
java -jar build/libs/datenportal-integrator.jar setup-gretl
# Datenblatt-MCP muss laufen, INTERLIS-JAR muss vorhanden sein.
export GRADLE_JAVA_HOME_17="/pfad/zum/jdk-17"
./gradlew integrationTest
```

Die echten Tests prüfen stdio-MCP, beide Fachadapter, positive/negative XTF-/Office-Fälle und GRETL mit gültiger/ungültiger CSV. Ein negativer Fall muss am CSV-Task vor dem Publikations-Workspace stoppen. Tests dürfen weder echte Freigaben erfinden noch INT/PROD verwenden. Vollständige lokale Veröffentlichung und Dialogabnahme in beiden Harnesses erfordern tatsächliche menschliche Stopps und sind separat dokumentiert.

Vor/nach der Abnahme Quellstände der externen Komponenten und erlaubte Themenrepo-Änderungen kontrollieren. Versionierte Doku bleibt vollständig in diesem Repo, deutsch und Markdown. Konkrete Ergebnisse, simulierte Nachweise und offene Abnahmen stehen in [abnahme.md](abnahme.md); diese Datei bei tatsächlichen neuen Nachweisen aktualisieren.

## Bekannte Grenzen

V1 modelliert eine flache CSV. Geometrien, Beziehungen und zusätzliche Constraints benötigen bestätigte Semantik und explizite Fach-MCP-Aufträge; sie werden nicht automatisch aus Datenmustern erfunden. Serien mit unterschiedlichen Verträgen verlangen Klärung. Entwürfe des HTTP-Datenblatt-MCP sind nach Neustart zu restaurieren. Unklare Uploads und fehlende Berichte verlangen Aufklärung über vorhandene Laufkennungen.

Ein Agent mit vollem Dateizugriff kann lokale State-Dateien ändern; Freigaben stellen daher keine separate menschliche Identität sicher. HTML wird lokal mit escaped Werten und restriktiver CSP ohne JavaScript erzeugt. Noch offene externe Abnahmen dürfen nicht aus Unit-Test-Erfolgen abgeleitet werden.
