# Entwicklerhandbuch

## Architektur

JDK 21 ist die gemeinsame Build- und Laufzeitbasis. Der Integrator enthält Workflow, Freigaben, Dateiprüfung, CSV-Adapter, GitHub-REST-Client und Jenkins-Steuerung. Datenblatt- und INTERLIS-MCP starten als eigene stdio-JVMs. XTF-Prüfung und CSV-Prüfung laufen in separaten JVMs, damit Logging und globale Validatorzustände weder MCP-stdout noch parallele Aufträge beeinflussen.

Installationspaket, fachlicher Arbeitsbereich und Entwicklungscheckout sind getrennt. AgentResources löst unveränderliche Ressourcen im Paket auf. WorkspaceFiles begrenzt fachliche Dateizugriffe. OperatorPolicy behält die eingeschränkten Clientrechte bei. Es wird keine Container-Sandbox behauptet; die Programme laufen als Prozesse des Betreibers.

MCP-Prozesse gehören ihrer Integrator-Verbindung, verwenden stderr für Diagnose und werden über EOF geordnet beendet. Ein unbestätigter fachlich verändernder Aufruf wird nicht automatisch wiederholt. Entwürfe werden über die bestehenden Wiederherstellungsmechanismen rekonstruiert.

## Entwicklung und Tests

```bash
export JAVA_HOME=/pfad/zum/jdk-21
./gradlew test jar spotlessCheck
./gradlew integrationTest -PintegrationConfig=/absoluter/pfad/neue-java-konfiguration.toml
```

`test` verwendet isolierte synthetische Fixtures. `integrationTest` benötigt konfigurierte echte Werkzeuge; lokale Jenkins-Prüfungen benötigen zusätzlich den bestehenden Dev-Stack. Keine realen fachlichen Freigaben für Testzwecke erzeugen. Tatsächliche, simulierte und offene Abnahmen in `docs/abnahme.md` unterscheiden.

Der CSV-Adapter erweitert ilivalidator um CsvReader: UTF-8, Header, Semikolon, doppelte Anführungszeichen. Lokal und in Jenkins werden keine Bibliotheks- oder Bundle-Hashes verglichen. Daten- und Modellidentität bleiben Teil der Freigaben. Organisationsprüfung validiert XTF, Teams und Berechtigungen; lokale Gradle-Ausführung entfällt.

## Paketbau und Veröffentlichung

Zuerst die MCP-Releases veröffentlichen. Beide MCP-Repositories publizieren ihre getesteten bootJar-Artefakte zusätzlich zu den bestehenden Multiarch-Images. Maven-Koordinaten: `ch.so.agi:datasheet-mcp:<version>:boot@jar` und `ch.so.agi:interlis-mcp:<version>:boot@jar`. `interlis-mcp-module` bleibt unabhängig davon eine Bibliothek.

```bash
./gradlew javaDistribution -PagentVersion=<version> -PdatasheetVersion=<version> -PinterlisVersion=<version>
bash bin/package-smoke build/java-distribution
./gradlew publishOperatorPublicationToOperatorSnapshotsRepository -PagentVersion=<version> -PdatasheetVersion=<version> -PinterlisVersion=<version>
```

Repository: `https://jars.interlis.guru/snapshots/`. Zugang über `MAVEN_USERNAME` und `MAVEN_PASSWORD`. Die GitHub-Actions-Pipeline `java-package.yml` prüft auf Linux und macOS; explizite Workflow-Dispatch-Eingaben wählen die veröffentlichten MCP-Versionen für ein Betriebspaket.

Die getesteten Standardversionen in `gradle.properties` sind Datenblatt-MCP
`0.2.3` und INTERLIS-MCP `0.0.580-1`. Die `-P`-Argumente überschreiben diese
Defaults bei einer gezielten Aktualisierung; Workflow-Dispatch bietet dieselben
Versionen als Vorgabe an.

Reine Dokumentationsänderungen unter `docs/**`, an `README.md`, `DEMO.md` sowie
an `templates/operator/README.md` und `templates/operator/anwenderhandbuch.md`
starten bei Push und Pull Request keinen Workflow. Gemischte Änderungen mit
Code, Konfiguration, Skills oder `AGENTS.md` werden weiterhin geprüft.
Paketbau und Publikation erfolgen ausschliesslich durch einen ausdrücklichen
manuellen Workflow-Start (`workflow_dispatch`), auch nach Dokumentationsänderungen.
Die Pfadfilter entsprechen der [GitHub-Actions-Syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpushpull_requestpull_request_targetpathspaths-ignore).

`distribution.json` dokumentiert Versionen und SHA-256 aller Paketdateien. Datenblatt-Modelle stammen aus dem eingebundenen bootJar. Das Paket enthält keine Quellcheckouts und baut beim Betreiber nichts. Fachliche Konverter werden weiterhin mit dem JDK kompiliert.

Nur für lokale Entwicklung kann `-PmcpArtifactDirectory=/pfad/zu/test-jars` anstelle veröffentlichter Versionen verwendet werden. Dort liegen `datasheet-mcp.jar` und `interlis-mcp.jar`. Solche Pakete sind ausdrücklich als Entwicklungsartefakte gekennzeichnet und von der Maven-Publikation ausgeschlossen.

## GitHub und Fehlerbehandlung

GitHub verwendet Javas HTTP-Client, Bearer-Authentifizierung und keine Redirects. Tokenreihenfolge: GH_TOKEN, GITHUB_TOKEN, Workspace-Credential-Store. Die optionale Workspace-Einstellung `github_token_source="store"` überspringt beide Env-Quellen für REST und Git-Helper; Standard `auto` behält die Reihenfolge bei. `init` bewahrt die Einstellung in der lokalen Konfiguration. Leere Variablen werden übersprungen; gesetzte Env-Tokens umgehen den Store für die Authentifizierung. Der optionale `github`-Eintrag enthält `host: github.com` und `token`; Store-Version 1 und bestehende Jenkins-Einträge bleiben kompatibel. REST und Git verwenden `Credentials.resolveGithub()`. PR-Erstellung ist POST, Statusprüfung GET. Ein Java-Credential-Helper liefert Tokens für GitHub-HTTPS über das Git-Credential-Protokoll. Er erhält den absoluten Konfigurationspfad und liest den Store selbst; gespeicherte Tokens werden nicht in die Git-Prozessumgebung kopiert. Bei Authentifizierungsfehlern gibt es keinen Quellenwechsel. Geheimnisse stehen weder in Prozessargumenten noch in Rohlogs. SSH-Zugriff verwendet die normale Git-SSH-Konfiguration.

Vor PR-Erstellung wird die Zielidentität gespeichert. Nach einer unklaren Antwort wird ein passender vorhandener PR gesucht; kein erneuter POST ohne Klärung. Merge-Prüfung kontrolliert merged, Basisbranch, Head-Commit und danach die Dateiinhalte im Zielbranch. Rate-Limits melden Warte-/Reset-Zeit. Menschlicher Merge bleibt Voraussetzung.

## Versionen und Umstellung

Schema 3 beginnt neue Vorgänge. Schema 2 wird unverändert zurückgewiesen; Altvorgänge mit der alten Runtime abschliessen, insbesondere unklare Uploads dort aufklären. Keine automatische Konfigurations-, Freigabe- oder Vorgangsmigration. Alte Container und Volumes werden nicht automatisch gelöscht.

Die alten Befehle setup-java, setup-tools, setup-mcps, setup-gretl und runtime-up melden, dass vollständiges Paket und init zu verwenden sind. Docker-Launcheroptionen werden zurückgewiesen. Das Integrator-Image und seine Pipeline entfallen; Docker bleibt ausschliesslich beim vorhandenen Publikationsstack und dessen optionalen Tests.

### Repository-Synchronisierung

`sync_repository(run_id, environment)` verwendet dieselbe Merge-/Byteprüfung wie
die Lieferfortsetzung, aber ohne Jenkins. `RepositorySync` sperrt das gemeinsame
Git-Verzeichnis auch gegenüber `apply_local_changes`. Es speichert vor Eingriffen
ein atomisches Journal `datenportal-sync.json` mit Checkout-Identität, altem HEAD,
Zielcommit, Indexeinträgen und Dateisicherungen. Nur belegte, ungestagte
Vorgangsänderungen werden für ein Fast-forward gezielt zurückgeführt; fremde
Änderungen werden vor und nach dem Update inklusive Indexzustand verglichen.
Fehler vor dem Fast-forward werden bei unverändertem Zwischenstand zurückgesichert.
Nach Prozessabbruch bleiben Journal und Sicherungen zur Wiederaufnahme erhalten.
Keine automatische Rücksetzung eines bereits aktualisierten Branches.

`repository_sync` ist eine optionale Erweiterung von Schema 3. Bestehende Vorgänge
bleiben lesbar; ohne `applied.hashes` erfolgt keine Bereinigung lokaler Änderungen.
Neue PR-Datensätze binden zusätzlich Quellcheckout, Repository und Zielbranch.

### Verlustfreier numerischer Modellvertrag

`NUM_RANGE.min/max` werden im Integrator als Dezimalstrings gespeichert und auch
so an den INTERLIS-MCP gesendet. Der öffentliche MCP-Eingabevertrag signalisiert
`x-interlis-decimal-bounds: strings-v1`; ohne diese Fähigkeit blockiert der
Integrator die Modellgenerierung mit einer Aktualisierungsaufforderung.
Der MCP akzeptiert weiterhin JSON-Zahlen. Für fachlich relevante Nachkommastellen
sind Strings verbindlich: `"0.00"` und `"100.00"` müssen `0.00 .. 100.00` ergeben.

Der Integrator prüft vor Übernahme eines erfolgreichen Kandidaten anhand der
kompilierten Attribute sowohl Grenzwerte als auch Genauigkeit. Ein unveränderter
fehlerhafter Aufruf wird anhand von Argumenten und Komponentenprüfsumme gesperrt.
Ein Komponentenwechsel erlaubt eine erneute Prüfung, ersetzt aber keine Freigabe.
