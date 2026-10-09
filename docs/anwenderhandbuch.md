# Anwenderhandbuch

Der Integrator unterstützt drei Vorgänge: ein Thema integrieren (`topic`), ein INTERLIS-Modell erstellen (`model`) und eine Organisation anlegen (`organization`). Das Sprachmodell läuft in Codex oder OpenCode. Der Java-Kern prüft Dateien und kontrolliert Freigaben sowie externe Abläufe. Er benötigt keinen Modellzugang und betreibt keinen zusätzlichen Webserver.

## Installation und Konfiguration

Standard ist `bin/datenportal-agent`: Docker baut und startet den Java-Kern.
Benötigt werden eine Bash-Shell, lokales Docker mit Compose und die Checkouts von
Integrator, Themenrepo und Dev-Stack. Ein Host-JDK ist optional. macOS benötigt
aktiviertes Host-Networking in Docker Desktop ab 4.34; Linux einen lokalen Docker
Engine. `init` prüft Loopback tatsächlich über einen kurzlebigen Prüfcontainer.
Fehlende Docker-Einstellungen werden gemeldet, nicht automatisch geändert.

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

`init` ist gesperrt gegen parallele Aufrufe und protokolliert jeden Schritt in
`.datenportal-integrator/init.json`. Es legt eine fehlende Standardkonfiguration
an, prüft bestehende Checkouts, bereitet ilivalidator und beide Fach-MCPs sowie
GRETL vor, aktualisiert bekannte projektlokale MCP-Einträge und führt einen echten
synthetischen Funktionstest durch. Eine explizit gewählte fehlende Konfiguration
ist ein Fehler. Bestehende Konfigurationen bleiben erhalten. Fehlende Repos werden
nicht geklont. Ein Fehler kann nach seiner Behebung mit `init` fortgesetzt werden.

Der Funktionstest importiert/exportiert ein Datenblatt, validiert XTF, leitet ein
Modell ab und prüft gültige sowie absichtlich ungültige CSV mit GRETL. Er verwendet
eine private Themenkopie und eigene Testvorgänge ohne Publikationsprofile. Er
startet weder Seed noch Upload noch Publikationsstack. Testfreigaben sind explizit
synthetisch; sie gelten für keine Geschäftsdaten. Nachweise liegen unter
`.datenportal-integrator/smoke/`. Aktuelle Nachweise werden wiederverwendet.
`init --skip-smoke` meldet keine vollständige Bereitschaft;
`init --update-tools` aktualisiert bewusst die konfigurierten Werkzeugauswahlen.

`doctor` trennt Agent-Runtime/Mounts/Loopback, Fachwerkzeuge, aktuellen Smoke und
optionale Publikationszugänge. Ein fehlender Jenkins-Zugang verhindert reine
Modellierung nicht. Technische Bereitschaft ersetzt keine fachliche Freigabe.

Alle bisherigen CLI-Befehle funktionieren hinter dem Launcher. Zusätzliche
Eingabeordner müssen ausdrücklich eingebunden werden:

```sh
./bin/datenportal-agent --mount-ro '/pfad/mit leerzeichen/eingang' doctor
./bin/datenportal-agent --mount-rw /anderer/arbeitsordner --config /anderer/arbeitsordner/local.toml init
./bin/datenportal-agent gradle test jar spotlessCheck
./bin/datenportal-agent gradle integrationTest
```

Mounts verlangen existierende Verzeichnisse; Symlinks werden aufgelöst. Der
Checkout-Elternordner wird schreibbar unter identischem absolutem Pfad gemountet.
Berichte und Vorgänge bleiben hostseitig lesbar; auf Linux schreiben Build und
Agent mit der aufrufenden UID/GID. Remote-Docker-Kontexte sind nicht unterstützt.

`DATENPORTAL_FORWARD_ENV="DATENPORTAL_LOCAL_USER DATENPORTAL_LOCAL_TOKEN GH_TOKEN GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL"`
benennt weitergereichte Variablen. Keine Werte angeben. Für GitHub HTTPS und PRs
`GH_TOKEN` und Git-Autorenangaben in der aufrufenden Umgebung setzen; persönliche
Credential-Verzeichnisse werden nicht pauschal eingebunden. Die erzeugte
MCP-Konfiguration berücksichtigt die in den Umgebungsprofilen benannten Variablen.

`./bin/datenportal-agent codex` startet Codex auf dem Host und verbindet den
Docker-Launcher. `harness-config` gibt einen Vorschlag für benutzerlokale Einträge
aus; persönliche Konfigurationen werden nicht geändert. Bestehende fremde oder
mehrdeutige projektlokale Einträge führen zu einem Konflikthinweis. Sicherungen
liegen privat unter `.datenportal-integrator/harness-backups/`.

Für lokale Java-Entwicklung ausdrücklich `--runtime local` verwenden und JDK 25
über `JAVA_HOME` oder `DATENPORTAL_JAVA_COMMAND` wählen. Direkte Java-Befehle und
`setup-*` bleiben als erweiterte Schnittstelle verfügbar; die folgenden Beispiele
beschreiben auch diesen bisherigen Einstieg. GRETL verwendet ausschliesslich
Java 17 im Jenkins-Image. Ein Java-Symlink wird im Docker-Modus nicht verändert.

`config/local.toml` ist ignoriert. Relative Pfade beziehen sich auf `root`; `root` selbst bezieht sich auf die Konfigurationsdatei. Werkzeugpfade, Checkout-Pfade sowie MCP-Images und optionale HTTP-Adressen stehen hier. Die Auswahl erfolgt mit `--config`, alternativ `DATENPORTAL_INTEGRATOR_CONFIG`, sonst `config/local.toml` im aktuellen Arbeitsverzeichnis.

```toml
root = ".."
topics_repo = "../datenportal-themenrepo"
stack_repo = "../datenportal-dev-stack"
state_dir = ".datenportal-integrator/runs"
validator_command = ["java", "-jar", ".datenportal-integrator/tools/ilivalidator/ilivalidator-1.15.0.jar"]
model_dirs = [] # Originalmodelle kommen automatisch aus dem Datenblatt-Image

[gretl]
image = "sogis/datenportal-jenkins:0.1.0-3"
mode = "compose"

[datasheet]
transport = "http"
managed = true
url = "http://127.0.0.1:8000/mcp"
image = "sogis/datenportal-datenblatt-mcp@sha256:b391421578f8c4652fb7a64465187746a3057ab4413d17cfc20a56025b470da1"

[interlis]
transport = "http"
managed = true
url = "http://127.0.0.1:8080/mcp"
image = "sogis/interlis-mcp@sha256:b3ed1e738ccfe670f92ecebcbb014cf67aade5f0609e15be1f75948e15e899ff"
```

`stack_timeout_seconds` beträgt standardmässig 1800 Sekunden und gilt nur
für Stackstart, Bootstrap, Compose-Service-Starts und deren Bereitschaft. `timeout_seconds` bleibt mit
300 Sekunden der allgemeine Werkzeug-Default. Stacklogs liegen unter
`.datenportal-integrator/stack-logs/` und bleiben auch bei Fehler oder Timeout
erhalten. Nach einem Timeout den Containerlauf und den Bootstrap-Prüfstand
klären, bevor erneut gestartet wird.

Das vollständige Beispiel enthält auch das lokale Jenkins-Profil. `managed = true` verbindet einen Fach-MCP mit der lokalen Compose-Laufzeit. Beide Dienste verwenden HTTP auf `/mcp`; die Ports sind ausschliesslich an `127.0.0.1` gebunden. Andere freie Ports direkt in den beiden `url`-Werten wählen. Unter `[runtime]` kann `project = "datenportal-integrator"` einen eigenen Compose-Projektnamen wählen. Der öffentliche Werkzeugkatalog wird nach dem Start geprüft. Dateien werden weiterhin vom Integrator gelesen und als Text übertragen.

Für schon vorhandene HTTP-Dienste `managed = false` setzen oder das Feld weglassen. Sie werden nicht durch den Integrator gestartet. Ein `image` kann für beide Fach-MCPs die ausgewählte Version dokumentieren; beim Datenblatt liefert es zusätzlich die Originalmodelle. Ohne Datenblatt-Image müssen passende lokale `model_dirs` angegeben werden. Für stdio `transport = "stdio"` verwenden und `url` sowie `managed` entfernen: dann startet jede CLI-Verbindung wie bisher ihren eigenen Container über `docker run --rm -i`. Die frühere INTERLIS-JAR-Konfiguration und `datasheet_mcp_url` bleiben unterstützt.

Zugangsdatenwerte stehen weder in versionierter Konfiguration noch in Argumentdateien. Ein Umgebungsprofil nennt nur Variablennamen (`username_env`, `token_env`). Zum Setzen in einer Shell die Werte ohne Echo eingeben, beispielsweise unter zsh:

```sh
read -r 'DATENPORTAL_LOCAL_USER?Jenkins-Benutzer: '
read -rs 'DATENPORTAL_LOCAL_TOKEN?Jenkins-Token: '
export DATENPORTAL_LOCAL_USER DATENPORTAL_LOCAL_TOKEN
```

Ein aus dem Finder gestarteter Desktop erbt Shell-Variablen nicht automatisch. Dort die MCP-Umgebung benutzerlokal einrichten oder Codex aus der vorbereiteten Shell starten. Keine Zugangsdaten ins Themenrepo schreiben. `gh auth login` und `gh auth status` prüfen die GitHub-Anmeldung. `command -v java git gh docker codex opencode` prüft den PATH.

```sh
java -jar build/libs/datenportal-integrator.jar setup-java
java -jar build/libs/datenportal-integrator.jar setup-tools
java -jar build/libs/datenportal-integrator.jar setup-gretl
java -jar build/libs/datenportal-integrator.jar setup-mcps
```

`setup-tools` prüft ilivalidator 1.15.0 gegen eine feste SHA-256-Prüfsumme. `setup-mcps` bezieht fehlende Images, sichert ihre konkrete Identität und kopiert die beiden Originalmodelle aus `/app/models` eines gestoppten Datenblatt-Containers. Die ignorierte Auswahl liegt unter `.datenportal-integrator/tools/mcps/runtime.json`, die Modelle im nach Image-Stand getrennten Unterordner `models/`. Keine Modelle aus einem Editor-Checkout in `model_dirs` doppelt hinzufügen. `setup-gretl` sichert das GRETL-Image und dessen Bundle-Prüfsumme unter `.datenportal-integrator/tools/gretl-runtime/runtime.json`. Es startet einen kurzlebigen Prüfcontainer mit überschriebenem Einstiegspunkt, ohne Jenkins-Server oder laufenden Dev-Stack. JARs bleiben im Image.

Sind die ausgewählten Images vorbereitet, erzeugen die Setup-Befehle die ignorierte `compose.override.yaml` und Laufzeitdateien unter `.datenportal-integrator/tools/compose/`. Die Reihenfolge von `setup-gretl` und `setup-mcps` ist beliebig. Eine fremde Override-Datei wird nicht überschrieben. Die generierte Konfiguration enthält keine Zugangsdaten. Für normale Starts ist keine Neueinrichtung erforderlich.

```sh
# Im Integrator-Root nach der Einrichtung:
docker compose config --quiet
docker compose up -d
docker compose ps
# Alternativ inklusive MCP-Bereitschaftsprüfung:
java -jar build/libs/datenportal-integrator.jar runtime-up
```

Der erste benötigte MCP- oder GRETL-Aufruf startet fehlende Dienste selbst. Eine normale CLI-Operation oder das Ende des Integrator-MCP hält diese Dienste nicht an. Docker muss laufen und im PATH des Desktop-Prozesses verfügbar sein. Container verwenden `restart: unless-stopped`; Docker-Autostart auf dem Rechner wird nicht eingerichtet. `docker compose down` entfernt die eigenen Container und das Netzwerk, erhält aber den Gradle-Cache. Keine Volumes zur normalen Fehlerbehebung löschen.

Normale Aufrufe wechseln keine Image-Version. Für ein bewusstes Update zuerst die Dienste mit der vorhandenen Compose-Konfiguration anhalten, dann die Image-Referenz ändern beziehungsweise `setup-mcps --update` oder `setup-gretl --update` ausführen und erneut starten. GRETL erhält bei einem anderen Image einen eigenen Cache. Betroffene Prüfungen und Freigaben erneuern. Eine laufende abweichende Instanz wird diagnostiziert und nicht automatisch ersetzt. `start-datasheet` bleibt ein veralteter Hinweisbefehl.

Prüfen:

```sh
java -jar build/libs/datenportal-integrator.jar doctor
java -jar build/libs/datenportal-integrator.jar schema start
```

`doctor` prüft Pfade, Stack-Zuordnung, Image-/Modellcache, beide echten MCP-Werkzeugkataloge und die Java-17-/GRETL-Laufzeit im Prüfimage. Zusätzlich zeigt `doctor` sichere Dienst-/Image-Kennungen und den Gradle-Daemon-Status. Ein beendeter Leerlauf-Daemon ist bei funktionsfähiger Runtime kein Fehler. Fehler stehen im JSON-Ergebnis. Ein erfolgreicher Katalogtest ersetzt noch keine fachliche Modell- oder Publikationsabnahme.

## Codex Desktop, Codex CLI und OpenCode

Im Integrator-Arbeitsbereich liegen `.codex/config.toml`, `opencode.json` und der gemeinsame Skill unter `.agents/skills/`. Der gemeinsame MCP-Befehl ist `bin/datenportal-agent … serve`; `init` migriert bekannte Java-Einträge. Direkte Java-Aufrufe bleiben unterstützt. Für absolute, benutzerlokale Einträge:

```sh
java -jar build/libs/datenportal-integrator.jar harness-config
```

Die Ausgabe enthält den absoluten Launcherpfad und den tatsächlich gewählten Konfigurationspfad. Für Codex Desktop den `datenportal_integrator`-Eintrag in der benutzerlokalen `~/.codex/config.toml` aktualisieren und die MCP-Verbindung neu starten. Bereits vorhandene Zugangsdatenumgebung erhalten; die Ausgabe enthält keine Zugangsdatenwerte. Den Integrator-Ordner als Arbeitsbereich öffnen. Desktop kann HTML-Berichte im Dateipanel öffnen; im CLI reicht der lokale Dateilink oder ein Browser.

Für Codex CLI:

```sh
java -jar build/libs/datenportal-integrator.jar codex
# Alternativ, wenn die Projektkonfiguration geladen wird:
codex -C /pfad/datenportal-themenintegrator-agent
codex mcp list
```

Der Java-Helfer `codex` übergibt den MCP-Eintrag ausdrücklich als Konfigurationsoverride. Er verändert die persönliche Konfiguration nicht. Zusätzliche Codex-Argumente werden weitergereicht. Dies hilft auch bei Harness-Versionen, die Projektkonfigurationen noch nicht zuverlässig finden.

Für OpenCode:

```sh
cd /pfad/datenportal-themenintegrator-agent
opencode mcp list
opencode
```

OpenCode startet den Launcher aus `opencode.json`. Im optionalen lokalen Java-Betrieb legt `setup-java` einen ignorierten Symlink auf das tatsächlich verwendete JDK 25 an. `java` muss auf JDK 25 zeigen; bei Desktop-Prozessen gegebenenfalls den absoluten Befehl aus `harness-config` verwenden. Im Chat zum Beispiel: „Verwende den Themenintegrator-Skill. Integriere diese CSV und die XLSX-Metadaten lokal; erkläre zuerst die CSV und halte an den Freigaben an.“ Dateien anhängen oder absolute lokale Pfade nennen.

In OpenCode Beta das Integrator-Repository als Projekt öffnen und den Skill mit
`@datenportal-themenintegrator` auswählen. Die Anhangübernahme erfolgt automatisch
nach der Regel [Chat-Anhänge übernehmen](../skills/datenportal-themenintegrator/SKILL.md#chat-anhänge-übernehmen);
Kopieren und Prüfsummenvergleich müssen nicht im Prompt wiederholt werden.
Wenn die GUI keinen zugänglichen Originalpfad übermittelt, fragt der Agent gezielt danach.

Die aktuelle technische und menschliche Abnahme ist in [abnahme.md](abnahme.md) dokumentiert. Ein erfolgreicher MCP-Handshake ersetzt keinen vollständigen menschlichen Dialogtest.

## Durchgängiges Thema: CSV, XLSX und XTF

Die folgenden JSON-Dateien sind Beispiele. Pfade, fachliche Angaben, Vorgangs-ID und Prüfsummen durch die tatsächlichen Werte ersetzen. `schema <operation>` zeigt Pflichtargumente und erlaubte Typen. `call` gibt JSON aus; Exit-Code 2 bezeichnet einen fachlichen Fehler, Exit-Code 1 einen unerwarteten technischen Fehler.

`start.json`:

```json
{
  "workflow": "topic",
  "organization": "statistikdienst",
  "identifier": "ch.so.bevoelkerung.altersstruktur",
  "issue": "2025",
  "data_path": "/eingang/altersstruktur.csv",
  "metadata_path": "/eingang/altersstruktur.xtf"
}
```

```sh
java -jar build/libs/datenportal-integrator.jar call start --args-file start.json
java -jar build/libs/datenportal-integrator.jar call baseline --json '{"run_id":"VORGANGS_ID"}'
java -jar build/libs/datenportal-integrator.jar call analyze --json '{"run_id":"VORGANGS_ID"}'
```

Originaldateien bleiben unverändert. `baseline` liest den angenommenen Katalog; fehlt dort das Thema, wird ein eindeutiges Repository-Datenblatt berücksichtigt. Beschädigte oder unlesbare Bestände werden nicht als leer behandelt. Die CSV-Prüfung liest alle Zeilen, kontrolliert UTF-8, Semikolon, Quotierung, Spaltennamen und Zeilenbreiten. Die Vorschau zeigt Beispiele, fehlende Werte und Typvorschläge. Führende Nullen, Totalspalten oder fragliche Werte brauchen eine fachliche Entscheidung.

Bei nötigem Umbau einen Java-Konverter und JUnit-Tests vorbereiten. Pro Thema entscheiden: Lieferant stellt künftig um (`supplier`) oder der Integrator konvertiert jede Anlieferung (`recurring`). Die Vorgabe an den Lieferanten wird als lokale Datei erzeugt; sie wird nicht automatisch verschickt. Nach `transform` die neue CSV erneut mit `analyze` prüfen.

**Erster menschlicher Stopp:** CSV-Erklärung und Zielstruktur ansehen. Bei Korrekturen neu bearbeiten und prüfen. Erst das tatsächliche OK für die angezeigte Fassung protokollieren:

```json
{
  "run_id": "VORGANGS_ID",
  "gate": "data",
  "fingerprint": "PRUEFSUMME_AUS_ANALYZE",
  "human_statement": "Tatsächliche Antwort des Themenintegrators"
}
```

```sh
java -jar build/libs/datenportal-integrator.jar call approve --args-file csv-ok.json
```

XLSX als `metadata_source` aufnehmen; `xlsx` ohne `sheet` zeigt die Blätter, mit Blattname paginierte Zellen und Koordinaten. Formeln werden nicht ausgeführt. Ein fehlender gespeicherter Formelwert ist keine Null.

```sh
java -jar build/libs/datenportal-integrator.jar call attach --json '{"run_id":"VORGANGS_ID","path":"/eingang/metadaten.xlsx","role":"metadata_source"}'
java -jar build/libs/datenportal-integrator.jar call xlsx --json '{"run_id":"VORGANGS_ID"}'
java -jar build/libs/datenportal-integrator.jar call xlsx --json '{"run_id":"VORGANGS_ID","sheet":"Metadaten","offset":0,"limit":100}'
```

`metadata` delegiert an den bestehenden Datenblatt-MCP. Zuerst `describe_schema`, dann `import_xtf` oder `create_datasheet` mit `kind: dataset|series`. Attribute, Ausgaben und Pflichtwerte über die öffentlichen Fachwerkzeuge bearbeiten. Kontakte und Fachsemantik nicht erfinden. `provenance` speichert je Feld `field`, `origin` (`user`, `xlsx`, `accepted`, `llm`) und `source`; LLM-Vorschläge bleiben erkennbar.

```sh
java -jar build/libs/datenportal-integrator.jar call metadata --json '{"run_id":"VORGANGS_ID","operation":"describe_schema"}'
java -jar build/libs/datenportal-integrator.jar call metadata --json '{"run_id":"VORGANGS_ID","operation":"import_xtf"}'
java -jar build/libs/datenportal-integrator.jar call metadata --json '{"run_id":"VORGANGS_ID","operation":"update_metadata","arguments":{"values":{"title":"Fachlich bestätigter Titel"}}}'
java -jar build/libs/datenportal-integrator.jar call metadata --json '{"run_id":"VORGANGS_ID","operation":"export_xtf"}'
java -jar build/libs/datenportal-integrator.jar call validate --json '{"run_id":"VORGANGS_ID"}'
```

`validate` prüft genau die Liefer-XTF mit ilivalidator, validiert `offices.xtf` separat und verwendet es als Referenzbestand für den EXISTENCE-Constraint gegen `Office.identifier`. CSV-Spalten, Reihenfolge, Typen und Pflichtwerte müssen mit dem Datenblatt zusammenpassen. Die lokale HTML-Datei zeigt Metadaten, Serienausgaben, Prüfmeldungen, Herkunft und Dateivergleiche.

Notwendige fachliche Repository-Dateien vor der Freigabe mit `stage_change` registrieren. Beispielargumente: `relative_path: statistikdienst/ch.so.bevoelkerung.altersstruktur/datasheet.xtf`, `source_path: /…/export.xtf`. Liefer-XTF und geplante Repository-XTF müssen dieselben Bytes haben.

**Zweiter menschlicher Stopp:** Datenblatt, Dienststellen- und Organisationsänderungen prüfen. Korrekturauftrag bedeutet weiter bearbeiten, exportieren, validieren und erneut zeigen. Das OK mit `gate: metadata` und der Prüfsumme aus `validate` protokollieren. Bei zusätzlichem Modell werden Datenblatt und Modell gemeinsam gezeigt und beide konkreten Gates bestätigt.

```sh
java -jar build/libs/datenportal-integrator.jar call apply_local_changes --json '{"run_id":"VORGANGS_ID"}'
java -jar build/libs/datenportal-integrator.jar call deliver --json '{"run_id":"VORGANGS_ID","environment":"local"}'
```

`deliver` führt je Aufruf höchstens eine externe Phase weiter. Eine passende
laufende Stack-Instanz wird verwendet; andernfalls führt der Integrator das
Startskript `scripts/up.sh --infrastructure-only` des Dev-Stacks aus.
Vor Publikation vergleicht er die tatsächliche Jenkins-Image-/Bundle-Identität
mit seiner festgelegten GRETL-Runtime. Für einen noch fehlenden
lokalen Publikationsstand delegiert er an `scripts/bootstrap.sh` mit den
konfigurierten Compose-Dateien. Der gemeinsame Helfer übernimmt erfolgreichen
Seed, pausierten administrativen Erstaufbau und Portalstart. Bei vorhandenem
Bestand prüft `--check-only` lesend, ohne Seed oder Container-Neuerstellung.
Ein vorhandenes oder beschädigtes Manifest wird niemals durch einen Erstaufbau ersetzt. Anschliessend Seed, Lieferung, Publikationsbericht, Reload, Metadaten und Downloads prüfen. Erneute Aufrufe fragen gespeicherte Läufe ab. Bei Erfolg erscheint der konkrete `portal_url`.

**Dritter menschlicher Stopp:** lokal belassen oder INT/PROD wählen. `publication_plan` zeigt Zieladressen, Branch, Dateien und Ausgangsstand. Erst nach ausdrücklichem OK `gate: publish:int` beziehungsweise `publish:prod` freigeben. `prepare_pr` erstellt notwendige fachliche Änderungen in einer isolierten Git-Arbeitskopie. Ein Mensch übernimmt den PR; danach überprüft der Integrator Merge und exakte Zielbytes. Es folgt `deliver` in der freigegebenen Umgebung. Reine Datenlieferungen ohne Repository-Änderung brauchen keinen PR.

INT/PROD sind nur mit passenden Zugängen und bestehendem `managed-git`-Betrieb ohne Git-Rückschreiben nutzbar. Der Integrator stellt bestehende Runtime-Konfigurationen nicht um.

## INTERLIS-Modellierung

Modellierung ausdrücklich beauftragen. Sie ist optional im Themenvorgang oder mit `start.workflow: model` eigenständig möglich. Benötigt werden die geprüfte CSV und die konkrete Datenblatt-XTF. Die erste Version bildet eine flache CSV auf eine Klasse ab. Ein gemeinsames Serienmodell verlangt denselben Vertrag für alle Ausgaben.

`derive-model.json`:

```json
{
  "run_id": "VORGANGS_ID",
  "identity": {
    "name": "SO_Beispiel_20261006",
    "uri": "https://example.org/models",
    "version": "2026-10-06",
    "technical_contact": "modellverantwortlicher@example.org",
    "title": "Bestätigter Modelltitel",
    "short_description": "Bestätigter Zweck und Inhalt"
  }
}
```

```sh
java -jar build/libs/datenportal-integrator.jar call derive_model --args-file derive-model.json
java -jar build/libs/datenportal-integrator.jar call validate_model --json '{"run_id":"VORGANGS_ID"}'
```

Standard: INTERLIS 2.4, Profil `SO`, Zweck `VALIDATION`. Fehlende Identität, Beschreibungen oder Semantik erscheinen als `NEEDS_INPUT`. Spaltennamen, Typen, Pflichtigkeit, Beschreibungen und Einheiten stammen aus dem Datenblatt. Führende Nullen benötigen eine bewusste Typentscheidung. Beobachtete Werte begründen weder geschlossene Codelisten noch Schlüssel oder Fachbereiche. Zusätzliche Domains, Einheiten und Constraints nur mit `identity.semantics_confirmed: true` und fachlicher Grundlage beauftragen. Bei TEXT die maximale Länge bestätigen, bei DECIMAL/NUMERIC einen konkreten Wertebereich und die Genauigkeit. Der bestehende Adapter akzeptiert DATETIME mit +01:00 nicht als INTERLIS.XMLDateTime; diese Grenze wird erklärt und blockiert eine ungeprüfte Übernahme. Spaltenbezogene bestätigte Typen können unter `confirmations.<spalte>.type_spec` angegeben werden; `confirmed: true` hält die Entscheidung fest.

Vorhandene Modelle werden mit einem typisierten `changes`-Batch über `applyIliModelChanges` bearbeitet. `allow_breaking` ist eine ausdrückliche Entscheidung über potenziell brechende Änderungen. Der Integrator übernimmt Compiler-, Review- und Constraint-Nachweise des Fach-MCP. Unvollständige Nachweise bleiben Kandidaten. Manuelle Reviewpunkte erscheinen im HTML.

Nach erfolgreicher Ableitung werden `.ili`, `dataset.gradle` und die Datenblatt-Modellreferenz zusammen vorbereitet. Die Referenz wird über den Datenblatt-MCP vor dem abschliessenden Export gesetzt. `validate_model` führt GRETL `CsvValidator` in einer isolierten Repository-Kopie aus. Derselbe Themen-Task läuft später vor `preparePublicationWorkspace`; Metadatenlieferungen überspringen ihn ausdrücklich. Es gibt dafür keine eigene CSV-zu-XTF-Konvertierung.

Beim eigenständigen Modellvorgang: Modellvorschau prüfen, `gate: model` bestätigen und lokal übernehmen oder über einen freigegebenen Zielplan als PR vorbereiten. Kein automatischer Datenupload. Beim Themenvorgang werden `model` und `metadata` für die gemeinsam gezeigten Prüfstände bestätigt.

## Neue Organisation und Dienststelle

```sh
java -jar build/libs/datenportal-integrator.jar call organization_schema --json '{}'
java -jar build/libs/datenportal-integrator.jar call start --json '{"workflow":"organization","organization":"neueorganisation"}'
```

Die Organisation benötigt keinen Themenidentifier. `organization_schema` zeigt echte Office-Regeln, vorhandene Dienststellen und Teams sowie fehlende Angaben. Organisation und Office sind unterschiedliche Kennungen. Vorhandene Dienststelle über `office_identifier` verwenden oder ein vollständiges `office`-Objekt liefern.

```json
{
  "run_id": "VORGANGS_ID",
  "values": {
    "title": "Bestätigter Organisationstitel",
    "read_teams": ["datenportal-read"],
    "build_teams": ["bereits-vorhandenes-build-team"],
    "office_identifier": "ch.so.afu"
  }
}
```

```sh
java -jar build/libs/datenportal-integrator.jar call prepare_organization --args-file organization.json
java -jar build/libs/datenportal-integrator.jar call validate_organization --json '{"run_id":"VORGANGS_ID"}'
```

Neue Dienststellen benötigen `identifier`, `name`, `abbreviation`, `phoneNumber`, `email` und `officeAtWeb`. URI-Felder müssen absolute URIs enthalten, beispielsweise `mailto:…` und `https://…`. Längen und Eindeutigkeit werden geprüft. Fehlende Kontakte nachfragen.

Neue Teams unter `values.new_teams` mit `{ "teamname": { "users": ["bestaetigter-benutzer"] } }` vorbereiten; dieselben bestätigten Kennungen unter `values.confirmed_users` nennen. Leere, unbekannte oder widersprüchliche Teams werden abgewiesen. Bestehende Mitgliedschaften bleiben erhalten.

Der Kandidat enthält Organisationsordner, `gretl-datenportal-job.yaml`, `settings.gradle`, `build.gradle`, gegebenenfalls Office-Katalog und Teamkatalog. `validate_organization` prüft Office-XTF, YAML, Berechtigungen und die Gradle-Konfiguration. HTML-Dateien und Dateivergleiche zeigen die Änderungen.

**Organisations-Stopp:** alle Dateien und Berechtigungszuordnungen prüfen und `gate: organization` bestätigen. Danach `apply_local_changes` oder Zielplan/PR. Eine Organisation ohne Thema wird als `repository_created` abgeschlossen. Das bestehende Jenkins-Plugin erzeugt ihren Job erst mit dem ersten Thema; Dummy-Themen werden nicht angelegt. Ein optionaler `seed` verwendet die vorhandene Schnittstelle und gespeicherte Kennungen, publiziert aber keine Daten.

## Serien, Teillieferungen und Konverter

Bei Serien bleiben Serienidentifier, Ausgabeidentifier und Ausgabenbezeichnung getrennt. `start.issue` und Jenkins `SERIES_ID` bezeichnen die Ausgabe, beispielsweise `2025`. Die Ausgabe muss eindeutig im Datenblatt stehen; genau eine Ausgabe ist aktuell. Für Datenlieferungen ist die Ausgabenbezeichnung erforderlich.

Bei reinen Datenlieferungen `metadata_path` weglassen und `baseline` laden. CSV gegen den bestehenden Vertrag prüfen und freigeben; das vorhandene Datenblatt bleibt erhalten. Ändert es sich im Ziel, hält der Integrator an. Bei reinen Metadatenlieferungen `data_path` weglassen; CSV-Gate und GRETL-CSV-Task entfallen. Vorhandene Daten werden nach dem bestehenden Liefervertrag erhalten.

Java-Konverter liegen unter `topics/<organisation>/<identifier>/`. Rezeptdatei:

```json
{"class_name":"MeinKonverter","sources":["MeinKonverter.java"]}
```

Die Klasse implementiert `ch.so.agi.integrator.CsvConverter` mit `convert(Path input, Path output)`. UTF-8-/Semikolon-Ausgabe an den übergebenen Pfad schreiben, Eingabe unverändert lassen. JUnit-Tests unter einem Themen-Unterordner prüfen echte Beispiele, Randfälle und erwartete Werte. `transform` bekommt die relativen Rezept- und Testpfade ab `topics/`, dazu `policy` und `instructions`. Erst kompilieren, dann JUnit ausführen, danach in separater JVM konvertieren und die Ausgabe technisch prüfen. Fehlgeschlagene Tests stoppen die Transformation.

## Wiederaufnahme, Migration und Fehler

Vorgangs-ID aufbewahren. `status` liest Artefakte, Entwürfe, Freigaben und externe Kennungen. Neue Eingaben nicht durch einen zweiten Vorgang am bestehenden Upload vorbei liefern.

```sh
java -jar build/libs/datenportal-integrator.jar call migrate_run --json '{"run_id":"ALTE_ID"}'
java -jar build/libs/datenportal-integrator.jar call migrate_run --json '{"run_id":"ALTE_ID","apply":true}'
java -jar build/libs/datenportal-integrator.jar call status --json '{"run_id":"ALTE_ID"}'
```

Die erste Migration ist nur Vorschau. Die zweite schreibt eine Sicherung und übernimmt Schema 1 nach Schema 2. Originale, Exporte, Java-Snapshots, unbestätigte Metadatenoperationen und externe Laufkennungen bleiben erhalten. Alte Freigaben sind historisch; vor neuen Lieferungen erneut prüfen. Bereits gestartete Lieferungen werden abgefragt. Python-Rezepte müssen nach Java portiert werden und werden nicht ausgeführt.

Bei einer neuen CLI-Verbindung oder einem eindeutig fehlenden Entwurf rekonstruiert der Integrator automatisch den letzten bestätigten Snapshot. Frühere Attribut-/Ausgabe-IDs werden eindeutig übersetzt. Die Transferidentität, vorhandene Exportdatei und deren Freigaben bleiben bei unverändertem Inhalt und Prüfstand erhalten. Tatsächliche Änderungen benötigen wieder Export, Validierung und Freigabe.

Eine unbestätigte letzte Änderung blockiert weitere Bearbeitung mit `metadata_uncertain`. `metadata` mit `operation: restore` stellt ausdrücklich den bestätigten Stand her und zeigt den unbestätigten Auftrag. Erst nach Prüfung erneut beauftragen; der Integrator wiederholt ihn nicht automatisch.

| Meldung | Vorgehen |
|---|---|
| `approval_required`, `stale_review` | Aktuellen Stand erneut prüfen und echten menschlichen Entscheid einholen. |
| `draft_not_exported`, `metadata_uncertain` | Gesicherten Entwurf wiederherstellen oder aktuellen Entwurf exportieren; dann validieren. |
| `artifact_changed`, `change_modified` | Veränderung aufklären und bewusst neu aufnehmen; Freigaben nicht manuell reparieren. |
| `unknown_office`, `office_fields`, `unknown_team`, `users_unconfirmed` | Fehlende fachliche Angaben oder bestätigte Benutzerkennungen erfragen. |
| `model_candidate`, `NEEDS_INPUT`, `PROOF_INCOMPLETE` | Fach-MCP-Diagnosen und offene Fachfragen bearbeiten; Kandidat nicht übernehmen. |
| `stack_mismatch` | Laufende Instanz und vorgesehenen Themencheckout klären; keine stille Umkonfiguration. |
| `stack_command_failed`, `command_timeout` | Den gemeldeten Stacklog und den Bootstrap-Prüfstand im Dev-Stack prüfen; unklare Publikationsversuche nicht erneut starten. |
| `manifest_invalid`, `remote_read_failed` | Bestand beziehungsweise Erreichbarkeit reparieren; nicht neu initialisieren. |
| `submission_unknown` | Vorhandene Queue/Laufkennung anhand Vorgangsparameter aufklären, dann `reconcile` oder `reconcile_seed`. Kein erneuter Upload. |
| `publication=accepted`, Prüfung fehlgeschlagen | Publikation ist erfolgt. Reload, RDF, Metadaten oder Downloads separat klären; `verify_delivery` prüft erneut ohne Upload. |
| `report_missing` | Jenkins-Konsole und bestehenden Lauf prüfen; fehlender Bericht erlaubt keinen sicheren Retry. |
| `repository_conflict`, `pr_base_conflict`, `pr_changed` | Änderungen vergleichen und erneut prüfen; keine fremden Änderungen überschreiben. |
| `human_merge_required` | Menschlichen Merge abwarten; Agent mergt nicht. |
| `converter_migration_required` | Python-Rezept nach Java mit JUnit portieren. |

`retry_delivery` ist nur für eindeutig fehlgeschlagene, nicht publizierte Versuche vorgesehen. Bei unklarer Publikation wird es abgewiesen. Freigaben sichern den Arbeitsablauf ab; sie sind keine unabhängige menschliche Authentisierung gegenüber einem Agenten mit vollständigem Dateizugriff.

### Docker-MCP-Fehler

| Fehler | Vorgehen |
|---|---|
| `mcp_setup_required` | Konfigurierte Images mit `setup-mcps` vorbereiten. |
| `mcp_image_missing` / `mcp_pull_failed` | Docker, Image-Referenz und gegebenenfalls `docker login` prüfen; anschliessend `setup-mcps`. Kein lokaler Build-Fallback. |
| `mcp_models_changed` | Modellcache wurde verändert oder fehlt; `setup-mcps` kopiert ihn erneut aus dem festgelegten Image. Danach erneut prüfen. |
| `invalid_configuration` bei `model_dirs` | Doppelte Originalmodelle entfernen; der Docker-Betrieb verwendet den Image-Cache. |
| `draft_restore_mismatch` | Rekonstruktion weicht ab; Vorgang erhalten und Ursache klären. Keine Änderung oder Lieferung fortsetzen. |
| `mcp_unavailable` | Docker und den lokalen Fehlerlog unter `.datenportal-integrator/tools/mcps/logs/` prüfen. Unbestätigte Änderungen separat aufklären. |

Der stdio-Export liefert keine Browser-Downloadadresse. Der Integrator speichert das validierte XML lokal und zeigt seine Vorschau. HTTP bleibt für Browser-Downloads verfügbar. Eigene stdio-Container werden beim Beenden entfernt; bei einem abgebrochenen Integratorprozess räumt die nächste Docker-MCP-Verbindung seine gespeicherten, nicht mehr zu einem lebenden Prozess gehörenden Containerkennungen auf. Fremde Container werden nicht entfernt.

## GRETL-Vorprüfungen im Container

Mit `[gretl].mode = "compose"` verwenden CSV-Modellprüfung und Organisationsprüfung einen eigenen dauerhaften Container aus dem festgelegten Jenkins-Image. Java 17, GRETL und die ursprünglichen Gradle-Abhängigkeiten kommen aus dem Image. Der eigene beschreibbare Cache gehört zu genau dieser Image-Identität. Nach jedem Containerstart läuft ein nebenwirkungsfreies `help` in einem minimalen Projekt mit dem bestehenden Themenrepo-Wrapper. Das Themenrepo ist dafür read-only eingebunden; dessen fachlicher Build wird beim Warm-up nicht ausgeführt.

Jede Prüfung erhält eine neue beschreibbare Themenkopie und eigene Eingabepfade. Gradle läuft als `jenkins` mit `--daemon`, ohne S3-Publikation, Git-Rückschreiben oder Reload. Prüfungen sind pro Runtime serialisiert. Der Daemon wird wiederverwendet, solange Gradle ihn aktiv hält; nach Inaktivität oder einem Abbruch darf er neu starten. Die aktuelle Jenkins-Instanz und ihr Home werden für Vorprüfungen nicht verwendet. Die eigentliche Anlieferung bleibt ein Jenkins-Job.

Ohne `mode` oder mit `mode = "ephemeral"` bleibt das bisherige Verhalten erhalten: ein neuer Container pro Prüfung, `--no-daemon`, anschliessende Entfernung. Es gibt keinen automatischen Wechsel zwischen den Betriebswegen bei einem Fehler.

`setup-gretl` behält die ausgewählte Image-Version bei. Nach einer bewusst geänderten Image-Referenz oder mit `setup-gretl --update` wird ein neuer Stand aufgenommen; anschliessend betroffene Prüfungen und Freigaben erneuern. Vor dem lokalen Gesamttest vergleicht der Integrator Image und Bundle mit dem tatsächlich laufenden Jenkins. Bei `gretl_runtime_mismatch` anhalten, die Konfiguration abstimmen und erneut vorbereiten/prüfen; der Integrator ändert den Stack nicht automatisch.

Alte Felder `gretl_java_home` und `gretl_offline_jars` werden noch gelesen, aber ignoriert und von `doctor` als veraltet gemeldet. Alte Runtime-Nachweise verlangen `setup-gretl`; sie werden vor der Übernahme gesichert. Ein vorhandener alter JAR-Cache wird nicht mehr benutzt und kann nach erfolgreicher Umstellung entfernt werden. Es gibt keinen lokalen GRETL-Fallback.

Prüfberichte enthalten Runtime-Identität, Exit-Code, lokalen Konsollog und einen Ordner für Gradle-Prüfartefakte. Im Compose-Modus bleiben Container und Diagnosearbeitskopien erhalten. Logs, Abschlussnachweise und Prüfartefakte liegen zusätzlich unter `/var/lib/integrator/jobs/` im eigenen Volume. Ein Timeout beendet den Task im Container; bei nicht bestätigtem Abbruch wird ausschliesslich die eigene GRETL-Runtime unter der Sperre neu gestartet. Auch nach Verlust des Hostprozesses wartet die nächste Prüfung auf den Abschluss oder klärt ihn durch diesen Neustart. Im ephemeral-Modus werden Container wie bisher entfernt. `gretl_setup_required` verlangt die Einrichtung; `gretl_image_missing` weist auf ein fehlendes festgelegtes Image hin. Normale Prüfungen beziehen keine neuen Images.

### Compose-Diagnose

| Beobachtung | Vorgehen |
|---|---|
| `runtime_start_failed` | Genannten Startlog unter `.datenportal-integrator/tools/compose/` prüfen; Docker, freien Port und `docker compose logs datasheet interlis gretl` prüfen. |
| `runtime_mismatch` | Arbeitsbereich, Image-Identität, Portfreigabe und eigenen GRETL-Cache abgleichen. Fremde Instanzen nicht ersetzen. |
| `runtime_tools_missing` / `runtime_start_timeout` | MCP-Profil, Serverlogs und erwarteten Werkzeugkatalog prüfen; keine Mutation automatisch wiederholen. |
| `runtime_configuration_conflict` | Eigene `compose.override.yaml` sichern und deren Verwendung klären; der Integrator überschreibt sie nicht. |
| `runtime_locked` | Laufende Prüfung beziehungsweise Container-Abschlussnachweis prüfen. Ein getrennter Host kann einen noch laufenden Task hinterlassen. |
| GRETL-Warm-up schlägt fehl | `docker compose logs gretl` und `/var/lib/integrator/warmup.log` lesen; Wrapper, Java 17, Image und Cache prüfen. |

Die erzeugte Compose-Konfiguration wird nach Änderungen mit den Setup-Befehlen erneuert. Eine vorhandene lokale Konfiguration wird nicht automatisch auf den neuen Standard umgestellt.

### Vorhandene lokale Konfiguration umstellen

Die bestehende `config/local.toml` erhalten und gezielt ergänzen: unter `[gretl]`
`mode = "compose"` setzen; bei beiden Fach-MCPs `transport = "http"`,
`managed = true` und die gewünschten lokalen `/mcp`-Adressen mit freiem Port
eintragen. Vorhandene Image-Referenzen, Pfade und Umgebungsprofile beibehalten.
Ein altes `datasheet_mcp_url` entfernen, wenn stattdessen `[datasheet]` verwendet
wird. Anschliessend `setup-mcps`, `setup-gretl` und `doctor` ausführen. Die
Setup-Befehle wechseln ohne `--update` keine bestehende Image-Auswahl.
Geänderte Konfiguration und Integrator-JAR verlangen erneut die betroffenen
Prüfungen und Freigaben.
