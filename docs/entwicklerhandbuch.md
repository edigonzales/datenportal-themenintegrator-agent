# Entwicklerhandbuch

## Architektur und Verantwortlichkeiten

JDK 25, Gradle-Wrapper und ein ausführbares JAR bilden die Implementierung. `Main` stellt CLI und stdio-MCP bereit. Beide dispatchen über `Operations` in denselben `Workflow`. Das Sprachmodell und die fachliche Kommunikation bleiben im Harness. Es gibt keinen eigenen Modellclient und keinen zusätzlichen Integrator-Webserver.

| Komponente | Verantwortung |
|---|---|
| `Settings`, `Json`, `Problem`, `ProcessRunner` | TOML, Pfadgrenzen, Prüfsummen, atomare Speicherung, strukturierte Fehler und externe Prozesse |
| `Store` | Vorgänge, Dateiartefakte, Sperren, Ereignisse und explizite Migration |
| `Workflow` | Operationen, Prüfstände, Freigabeabhängigkeiten, Kandidaten und lokale Übernahme |
| `Csv`, `Xml`, `Validator`, `Reports` | Vollständige technische CSV-Prüfung, sicherer XML-Leser, externe ilivalidator-Aufrufe, XLSX/HTML |
| `McpClients` | Verzögert initialisierte, wiederverwendete SDK-Clients für beide Fach-MCPs über Docker-stdio oder HTTP |
| `Models` | Typisierte flache Ableitung, Herkunft, High-Level-Nachweise, Modellreferenz und GRETL-Themenhook |
| `Organizations` | Office-Regeln aus kompiliertem Modell, Org-/Teamkandidaten und Gradle-Prüfung |
| `Converters`, `CsvConverter`, `ConverterMain` | Java-Konvertervertrag, Kompilierung, JUnit und separate Ausführungs-JVM |
| `Workspace`, `GretlRuntime` | Isolierte Arbeitskopie, festgelegtes GRETL-Image und separate Prüfcontainer |
| `Stack`, `Jenkins`, `Deliveries`, `Repository` | Vorhandene Runtime-Schnittstellen, persistierte Lieferphasen, Sichtbarkeitsprüfung und menschliches PR-Verfahren |
| `DockerMcps`, `Setup` | Festgelegte Image-Auswahl, Originalmodellcache, eigene Container, Werkzeuge und Harness-Konfiguration |

Alle Quellen liegen unter `src/main/java/ch/so/agi/integrator/`, JUnit unter `src/test/java/`. Fachliche Konverter liegen optional unter `topics/<organisation>/<identifier>/`. Regeln, Zusatzmodell und HTML-Vorlage bleiben im Integrator-Repo. Das Themenrepo ist die verbindliche Quelle für Organisationen, Dienststellen, Teams und Datenblätter; der Integrator pflegt keine zweite Kopie.

## Bestehende externe Schnittstellen

Keine Quellen von Dev-Stack, Fachdiensten, Jenkins-Plugin, GRETL oder Portal werden geändert. Änderungen an diesen Komponenten sind separate Aufträge. Relevante Grenzen werden als konkrete Fehler gemeldet.

- Datenblatt-MCP: Docker-stdio oder Streamable HTTP, `describe_schema`, `import_xtf`, `read_datasheet`, `update_metadata`, Attribut-/Ausgabenwerkzeuge, Validierung und Export. Der Integrator erzeugt nur die Transferidentität eines leeren Imports mit gültigem OID-Präfix; fachliche Datenblätter und ihre Exporte verantwortet der Fach-MCP.
- INTERLIS-MCP: eigenes SDK-Client/Transport-Paar; im lokalen Beispiel als verwalteter HTTP-Compose-Dienst, weiterhin Docker-stdio, externes Streamable HTTP oder vorhandenes lokales JAR. `authorIliModel` und `applyIliModelChanges` liefern Compiler-/Regel-/Constraint-Nachweise. Bei separat geändertem Quellstand `reviewIliModel`; unvollständige Constraint-Beweise verlangen weiterhin High-Level-Änderung/Prüfung.
- GRETL: vorhandenes Java-17-Skript, Repository-Wrapper und `shared/gradle/init.gradle`; Versionen aus `shared/gradle/gradle-build.properties`. `setup-gretl [--update]` sichert ein veröffentlichtes Jenkins-Image und den darin vorhandenen Bundle-Stand. Java 17 und JARs werden ausschliesslich im Container verwendet.
- Stack: Docker-/Compose-Status, Mount und `THEMEN_REPO_MODE`, vollständiges
  `scripts/up.sh` und gemeinsames `scripts/bootstrap.sh [-f DATEI ...]`.
  Ein kalter Agentstart verwendet zunächst `up.sh --infrastructure-only`,
  damit der Image-/Bundle-Abgleich vor der gemeinsamen Erstpublikation erfolgt.
  Erstaufbau und Seed sind im Dev-Stack implementiert; der Java-Integrator
  enthält keine eigene Gradle-/Quiet-Down-Initialisierung. Bei vorhandenem
  Bestand prüft `--check-only` Manifest und Portal ohne Mutation.
  `stack_timeout_seconds` (Default 1800, Bereich 1–7200) ist vom allgemeinen
  Timeout getrennt; Stackdiagnosen bleiben unter dem lokalen State-Elternpfad
  in `stack-logs/`. Bei passender laufender Instanz keine neue Instanz.
  Unpassender Checkout/Modus und GRETL-Runtime stoppen weiterhin.
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
| `draft`, `draft_base`, `exported_revision` | Bestätigter Snapshot, XML-Identität und historischer Revisionsnachweis |
| `draft_session`, `draft_id_aliases`, `exported_content_sha256`, `exported_artifact_sha256` | Flüchtige Sitzung, transitive ID-Zuordnung und Exportbindung an Fachinhalt/Bytes |
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

Werkzeughelfer: `setup-java`, `setup-tools`, `setup-mcps [--update]`, `setup-gretl [--update]`, `harness-config`, `codex [harness-argumente]`. Diese sind CLI-Helfer, keine zusätzlichen MCP-Fachoperationen.

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
java -jar build/libs/datenportal-integrator.jar setup-mcps
java -jar build/libs/datenportal-integrator.jar setup-gretl
# Fach-MCP-Images müssen mit setup-mcps vorbereitet sein.
# Kein Host-Java-17 erforderlich; optional den Nichtgebrauch nachweisen:
export GRADLE_JAVA_HOME_17="/unavailable-java17"
./gradlew integrationTest
```

Die echten Tests prüfen stdio-MCP, beide Fachadapter, positive/negative XTF-/Office-Fälle und GRETL mit gültiger/ungültiger CSV. Ein negativer Fall muss am CSV-Task vor dem Publikations-Workspace stoppen. Tests dürfen weder echte Freigaben erfinden noch INT/PROD verwenden. Vollständige lokale Veröffentlichung und Dialogabnahme in beiden Harnesses erfordern tatsächliche menschliche Stopps und sind separat dokumentiert.

Vor/nach der Abnahme Quellstände der externen Komponenten und erlaubte Themenrepo-Änderungen kontrollieren. Versionierte Doku bleibt vollständig in diesem Repo, deutsch und Markdown. Konkrete Ergebnisse, simulierte Nachweise und offene Abnahmen stehen in [abnahme.md](abnahme.md); diese Datei bei tatsächlichen neuen Nachweisen aktualisieren.

## Bekannte Grenzen

V1 modelliert eine flache CSV. Geometrien, Beziehungen und zusätzliche Constraints benötigen bestätigte Semantik und explizite Fach-MCP-Aufträge; sie werden nicht automatisch aus Datenmustern erfunden. Serien mit unterschiedlichen Verträgen verlangen Klärung. Entwürfe werden nach Prozesswechsel oder eindeutigem Verlust aus bestätigten Snapshots restauriert; unbestätigte Änderungen bleiben ein ausdrücklicher Klärungsschritt. Unklare Uploads und fehlende Berichte verlangen Aufklärung über vorhandene Laufkennungen.

Ein Agent mit vollem Dateizugriff kann lokale State-Dateien ändern; Freigaben stellen daher keine separate menschliche Identität sicher. HTML wird lokal mit escaped Werten und restriktiver CSP ohne JavaScript erzeugt. Noch offene externe Abnahmen dürfen nicht aus Unit-Test-Erfolgen abgeleitet werden.

## Docker-Runtime und Wiederaufnahme

`setup-mcps` speichert unter `.datenportal-integrator/tools/mcps/runtime.json` Referenz, Registry-Digest, lokale Image-ID und Plattform je Dienst sowie Pfad/SHA-256 der Originalmodelle. Es kopiert ausschliesslich die beiden erwarteten `.ili` aus `/app/models`, kompiliert sie und stellt den Cache atomar bereit. Der gleiche Image-Stand wird mit `--pull=never` gestartet. Ein gewöhnlicher Aufruf zieht keine neuere Version. `--update` erlaubt ausdrücklich Pull und eine neue Auswahl. Der Konfigurationsfingerprint bindet diese Identitäten und die verifizierten Modelle.

`EofStdioTransport` implementiert den Transportvertrag des offiziellen SDK: UTF-8-JSON-RPC-Zeilen, serialisiertes Schreiben, begrenztes Lesen und Schliessen von stdin vor einem eventuellen TERM. Die SDK-Clients bleiben für Protokoll und Sitzungen zuständig. `ToolClient` besitzt zusätzlich `sessionKey`, `tools` und `close`; Test-Lambdas behalten ihre Standardimplementierungen. `Workflow` ist `AutoCloseable`. CLI, `doctor` und MCP-Shutdown schliessen die Clients. Docker-Kennungen mit Besitzerlabel und Prozess-ID werden ignoriert gespeichert. EOF erhält Gelegenheit zum regulären Shutdown; erst danach folgt eine auf den Besitzer begrenzte Bereinigung. stderr wird begrenzt in lokale Logs geschrieben. Es werden keine vollständigen Container-Inspektionen oder Credential-Umgebungen ausgegeben.

Vor einer Bearbeitung wird ein vorhandener Entwurf gelesen. Eine neue stdio-Sitzung oder ein eindeutiges `not_found` beim Entwurfslesen löst Rekonstruktion aus; Revisionskonflikte tun das nicht. Die Rekonstruktion prüft den fachlichen Inhaltsfingerprint inklusive Basket-/Objektidentität, ohne interne Attribut-/Ausgabe-IDs; fehlende und leere Attribut-/Ausgabenlisten sind fachlich gleichwertig. Alte IDs werden transitiv auf neue IDs abgebildet. Der Exportnachweis bindet Inhalt und gespeicherte Bytes statt der flüchtigen Serverrevision. Schema-2-Vorgänge erhalten die optionalen Felder ohne Versionswechsel; ein alter Export wird nur bei passendem Revisionsmarker und unverändertem Artefakt übernommen. `pending_metadata` verhindert jede automatische Wiederholung einer nicht bestätigten Mutation.

HTTP verwendet den SDK-Streamable-Transport mit Initialisierung, Sitzungsverwaltung und Sitzungsschluss. Der neue Datenblatt-MCP ist im HTTP-Profil nicht mehr STATELESS. Beide Transporte exportieren für den Integrator mit `include_xml=true`; der stdio-Vertrag benötigt keinen Download-Link.

JUnit simuliert Pull-/Cache-/Konfigurationsfehler und fremde Container. Die echten Integrationstests benutzen die konfigurierten veröffentlichten Images, nicht lokale Fach-JAR-Builds: getrennte CLI-Prozesse, Integrator-MCP, HTTP-Regression, unvollständige Entwürfe, Neustarts, historische IDs, Export/Freigabe und die vorhandenen Modell-/GRETL-Prüfungen. Testfreigaben bleiben auf isolierte Fixtures beschränkt. Aktuelle tatsächliche Ergebnisse stehen in [abnahme.md](abnahme.md).

## GRETL-Containeradapter

`[gretl].image` legt das Prüfimage fest; `mode = "compose"` aktiviert die dauerhafte Laufzeit, ohne `mode` gilt `ephemeral`. Ohne Abschnitt gilt `sogis/datenportal-jenkins:0.1.0-3`. `GretlRuntime` ersetzt den Host-Aufruf in `Workspace.gradle`. Image-Pinning und Besitzerkennungen verwenden die bestehende Docker-Mechanik. Die lokale Runtime-Datei hat Schema-Version 2: Referenz, Digest, Image-ID, Plattform, Java-17-/Bundle-/Cachepfade im Container und Bundle-SHA-256. Die Inventarliste wird wie im Themen-Task aus sortierten `Dateiname=SHA256`-Zeilen mit abschliessendem LF gebildet. Alte Runtime-Dateien werden gesichert; aktive lokale JDK-/JAR-Pfade werden nicht mehr ausgewertet oder an Container weitergereicht.

Der ephemeral-Prüfcontainer überschreibt den Image-Einstiegspunkt mit einem Warteprozess. Es gibt keinen Jenkins-Controller, keine Hostmounts und keine übernommenen Jenkins-Zugangsdaten. Arbeitskopie und Zusatzdateien werden nach `/tmp` kopiert und für `jenkins` vorbereitet. Der vorhandene Wrapper und das Init-Script laufen als dieser Benutzer. `-PdataFile` und zusätzliche `-I`/`--init-script`-Argumente werden ausdrücklich übersetzt; andere Argumente werden nicht pauschal ersetzt. Die Host-Arbeitskopie bleibt im Bericht erhalten.

Prüfergebnisse behalten `valid`, `returncode`, `diagnostics`, `log`, `log_sha256`, `workspace` und `messages`; Runtime-Identität, Prüfartefaktpfad und `reports_copied` kommen hinzu. Fehlende Gradle-Artefakte werden ausgewiesen; nach Fehler/Timeout wird vorhandenes Material eingesammelt; nur ephemeral-Container werden danach entfernt. Timeout-Protokolle bleiben lokal. Die Aufräumlogik entfernt nur eigene Container. Der lokale Lieferablauf vergleicht vor Bootstrap/Seed Image-ID und Bundle mit Jenkins; abweichende Stände ergeben `gretl_runtime_mismatch`. INT/PROD behalten ihre bestehenden Schnittstellen und die Bundle-Prüfung im Themen-Task.

Konfigurationsfingerprints binden den vorbereiteten GRETL-Stand. Die veralteten Felder `gretl_java_home` und `gretl_offline_jars` werden lediglich kompatibel gelesen und als Hinweise angezeigt; Änderungen an diesen unbenutzten Pfaden ändern den effektiven Prüfstand nicht. Schema-2-Vorgänge, externe Lieferkennungen und unbestätigte Operationen bleiben erhalten. Runtime-Wechsel verlangen neue Prüfungen/Freigaben und starten keine Lieferung.

## Dauerhafte Compose-Laufzeit

`ComposeRuntime` steuert ausschliesslich lokale Werkzeugdienste. `[datasheet]` und `[interlis]` verwenden `transport="http"`, `managed=true`, ein vorbereitetes Image und eine explizite Loopback-Portadresse mit `/mcp`. Bestehende HTTP-Konfigurationen ohne `managed` bleiben externe Dienste. `[runtime].project` wählt das Compose-Projekt (Default `datenportal-integrator`). Der Publikationsstack bleibt bei `Stack`; seine Konfiguration und Jenkins-Home werden nicht geteilt.

Die versionierte Compose-Datei und Bash-Laufzeitdateien werden als Ressourcen in den ausführbaren JAR aufgenommen. Die Setup-Helfer schreiben bei vollständiger Auswahl eine ignorierte Override-Datei für den manuellen Root-Aufruf und eine vollständige Compose-Konfiguration unter `.datenportal-integrator/tools/compose/`. Automatische Starts verwenden diese vollständige Konfiguration, feste lokale Image-IDs und `--pull never`. Nicht verwaltete Dienste erhalten ein inaktives Profil. Eigene HTTP-Ports werden ausschliesslich an IPv4-Loopback gebunden.

Vor dem Start und bei Wiederverwendung werden Projekt, Besitzer-Arbeitsbereich, tatsächliches Image, Portfreigabe und GRETL-Volume geprüft. Der Cache-Volumename enthält Projekt und Image-ID. Java 17 und Bundle werden weiterhin aus dem Container beschrieben und mit dem bestehenden Nachweis verglichen. Keine vollständigen Containerumgebungen werden ausgegeben. Der MCP-Handshake prüft den erwarteten Werkzeugkatalog; nur die nebenwirkungsfreie Initialisierung darf bei noch nicht bereitstehendem Server erneut versucht werden.

Der GRETL-Einstiegspunkt bereitet den eigenen Cache aus dem Image vor, übernimmt den kanonischen Wrapper read-only und startet `help --daemon` in einem minimalen Projekt als `jenkins`. Das persistente Volume erhält keine Jenkins- oder S3-Zugangsdaten. Neue Prüfungen kopieren den Snapshot und Eingaben in eindeutige Arbeitsverzeichnisse. `duration_ms` misst die Prüfung nach Bereitstellung des Containers, einschliesslich Kopieren und Taskausführung. `container_workspace` und `daemon_status` ergänzen bestehende Berichte; die fachlichen Ergebnisfelder und Runtime-Nachweise bleiben kompatibel.

`RuntimeGuard` schützt Lebenszyklus und Prüfungen zwischen Prozessen sowie Threads. Ein zusätzliches `flock` im Container hält die Build-Sperre auch bei Verlust des Hostprozesses. Der Task läuft mit einem containerseitigen Timeout; ein atomarer Exit-Nachweis und Tasklog bleiben im Volume erhalten. Vor der nächsten Prüfung wird die Sperre abgewartet. Ein fehlender beziehungsweise unklarer Nachweis verlangt einen bestätigten Neustart der eigenen GRETL-Runtime. Nach Timeout wird der Gradle-Daemon bei Bedarf gestoppt. Workflow-Shutdown beendet HTTP-Sitzungen, aber keine Compose-Dienste.

Bestehende Runtime-Dateien behalten Schema 2. Betriebsmodus, MCP-Verwaltung und Integrator-JAR sind bereits durch den Konfigurationsfingerprint gebunden; eine Umstellung verlangt neue Prüfungen/Freigaben. Freigegebene Snapshots, externe Laufkennungen und blockierte unbestätigte Mutationen werden nicht migriert oder gelöscht. `doctor` zeigt Dienstzustand und Daemon-Status; ein fehlender Leerlauf-Daemon allein macht die Runtime nicht ungültig.

Die echte `ComposeRuntimeIntegrationTest` verwendet ein eigenes Projekt, freie Ports, private Themenkopien und synthetische CSV. Sie darf ausschliesslich ihre selbst angelegten Testvolumes aufräumen. Die bestehenden echten Integrationstests bleiben die Regression für stdio, ephemeral-GRETL, Wiederaufnahme und den Jenkins-Taskvertrag.
