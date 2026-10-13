# Anwenderhandbuch für das Betriebspaket

## Installation und Konfiguration

Für den Fachbetrieb öffnest du den **Betriebsordner** in Codex oder OpenCode.
Er enthält drei Skills, Anleitungen, Konfiguration und Arbeitsdaten. Java und
Integrator laufen im Docker-Image. Ein Integrator-Quellcheckout und Host-JDK
sind dafür nicht erforderlich. Alle Shell-Befehle dieser Anleitung führst du
selbst im Terminal aus; im Fachdialog arbeitet der Agent über seine MCP-Werkzeuge.

### 1. Voraussetzungen prüfen

Benötigt werden lokales Docker mit Compose, Git, Bash und ein eingerichteter
Codex- oder OpenCode-Client. Anmeldung und Modellzugang richtest du im Client ein.
Auf macOS in Docker Desktop ab 4.34 **Settings → Resources → Network → Enable
host networking** aktivieren und **Apply and restart** wählen. Unter Linux einen
lokalen Docker Engine verwenden. Remote-Docker-Kontexte sind nicht unterstützt.
Siehe [Docker Host Networking](https://docs.docker.com/engine/network/drivers/host/).

Für den Download brauchst du Zugriff auf die Git-Hosts, Docker Hub und
`downloads.interlis.ch`; für private Repositories die entsprechenden Git-Rechte.
Voraussetzung ist eine erfolgreiche Veröffentlichung des Betriebspakets:
[Image-Pipeline](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/workflows/agent-image.yml).
Ein fehlgeschlagener Lauf aktualisiert `latest` nicht. Meldet `workspace install`
einen unbekannten Befehl oder verlangt bereits vor der Installation eine lokale
Konfiguration (`configuration_missing`), zuerst Image-Version und Veröffentlichung
prüfen; kein Entwicklungscheckout als Installationsersatz erforderlich.

### 2. Fachliche Repositories und Eingangsordner bereitstellen

Für eine neue Einrichtung im Terminal:

```sh
mkdir -p "$HOME/datenportal"
cd "$HOME/datenportal"
git clone https://github.com/sogis/datenportal-themenrepo.git
git clone https://codeberg.org/edigonzales/datenportal-dev-stack.git
mkdir -p datenportal-pilot-daten
```

Das Themenrepo enthält Datenblätter, Modelle und Lieferjobs. Der Dev-Stack enthält
Skripte für die lokale Testumgebung und muss bereits bei `init` vorhanden sein.
`init` klont keine Repositories. Hast du diese Checkouts bereits, überspringe die
Clone-Befehle und verwende ihre vorhandenen Pfade in Schritt 4. Nichts verschieben.
Separate Quellcheckouts der Fach-MCPs, von GRETL oder Jenkins sind nicht nötig.
Der Pilotdatenordner enthält die Originaldateien. Verwende auch hier einen
vorhandenen Ordner weiter; kein erneutes Klonen, Verschieben oder Kopieren nötig.

### 3. Betriebsordner erstellen und Paket installieren

Die folgenden Befehle installieren in einen **leeren** Ordner. Ein bereits
installierter Ordner wird später mit `workspace update` aktualisiert.

```sh
betrieb="$HOME/datenportal/datenportal-betrieb"
mkdir -p "$betrieb"
agent_image='sogis/datenportal-themenintegrator-agent:latest'
docker pull "$agent_image"
docker run --rm --network none --user "$(id -u):$(id -g)" \
  --mount "type=bind,source=$betrieb,target=/workspace" \
  "$agent_image" workspace install /workspace
cd "$betrieb"
```

`/workspace` bezeichnet hier nur das Installationsziel im Container. Auf deinem
Rechner liegen die Dateien unter `$HOME/datenportal/datenportal-betrieb`.
Der Installationsschritt benötigt keinen Docker-Socket im Container.

```text
~/datenportal/
├── datenportal-betrieb/       ← im Client öffnen
├── datenportal-themenrepo/
├── datenportal-dev-stack/
└── datenportal-pilot-daten/  ← Originaldateien, im Container read-only
```

`latest` dient dem Erstbezug. Das Paket enthält seine Versionskennung in
`workspace-version`; der Launcher verwendet anschliessend diese veröffentlichte
Version und hält die konkrete Image-Identität fest. Normale Starts wechseln
nicht automatisch auf ein neueres Image. Alternativ kann das gleich versionierte
CI-Archiv samt Prüfsummendatei heruntergeladen, mit `shasum -a 256 -c DATEI.sha256`
geprüft und in einen leeren Betriebsordner entpackt werden.

### 4. Konfiguration prüfen

Bei der gezeigten Ordnerstruktur und freien Ports 8000/8080 direkt zu Schritt 5
gehen. `init` erstellt die fehlende `config/local.toml` aus der Vorlage.
Nur bei Abweichungen zuerst die Vorlage kopieren, ohne eine bestehende Datei zu
überschreiben:

```sh
if [ ! -e config/local.toml ]; then
  cp config/local.example.toml config/local.toml
fi
```

Öffne danach `config/local.toml` in einem Texteditor. Zugangsdaten gehören in
Umgebungsvariablen oder den Credential-Store, nicht in diese Datei.

| Einstellung | Wann anpassen? |
|---|---|
| `topics_repo`, `stack_repo` | Vorhandene Checkouts liegen anders. Absolute Pfade sind erlaubt; relative Pfade beziehen sich auf `root`, bei `root = ".."` auf den Betriebsordner. |
| `[datasheet].url`, `[interlis].url` | Ports 8000/8080 sind belegt; freie lokale Ports mit Pfad `/mcp` wählen. |
| `[runtime].project` | Mehrere Betriebsordner parallel: eigenen Projektnamen und eigene MCP-Ports wählen. |
| `--mount-rw` | Themenrepo oder Stack liegen ausserhalb von `~/datenportal`; zusätzlich zum Konfigurationseintrag ihre Verzeichnisse einbinden. |

Beispiel für vorhandene Checkouts und Pilotdaten unter `~/sources/`, wie in
Stefans Einrichtung: Nach Eintragen der Themenrepo- und Stack-Pfade in
`config/local.toml` die Befehle aus Schritt 5 so ausführen:

```sh
./bin/datenportal-agent \
  --mount-rw "$HOME/sources/datenportal-themenrepo" \
  --mount-rw "$HOME/sources/datenportal-dev-stack" \
  --mount-ro "$HOME/sources/datenportal-pilot-daten" init
./bin/datenportal-agent \
  --mount-rw "$HOME/sources/datenportal-themenrepo" \
  --mount-rw "$HOME/sources/datenportal-dev-stack" \
  --mount-ro "$HOME/sources/datenportal-pilot-daten" doctor
```

Mount-Verzeichnisse müssen existieren. Bei jedem späteren `init` dieselben
benötigten Mount-Optionen angeben: Sie werden aus dem aktuellen Aufruf in die
Client-Einträge übernommen. `harness-config` zeigt nur einen Konfigurationsvorschlag;
auch für diese Diagnose die benötigten Optionen mitgeben.

### 5. Initialisieren und Ergebnis prüfen

Bei Standardpfaden:

```sh
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten init
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten doctor
```

`init` richtet Fachwerkzeuge ein, erstellt `.codex/config.toml` und `opencode.json`
und führt einen isolierten synthetischen Selbsttest durch. Es erstellt keine
fachlichen Freigaben und führt keine Geschäftsdatenlieferung aus.

Erwartet wird bei `init` **`ready: true`**. Bei `doctor` insbesondere prüfen:

```json
{
  "agent_runtime": {"mounts_valid": true, "network": "host-loopback-verified"},
  "datasheet_mcp": {"valid": true},
  "interlis_mcp": {"valid": true},
  "initialization": {"ready": true, "smoke_current": true}
}
```

Auch `gretl_runtime` muss fehlerfrei sein. Der Exit-Code allein reicht nicht;
ein inzwischen ausgefallener Dienst kann trotz gespeichertem erfolgreichen
Selbsttest einen aktuellen Fehler melden. Bei Fehlern den genannten Bericht
prüfen, Ursache beheben und `init` wiederholen. `--skip-smoke` bestätigt keine
vollständige Bereitschaft. Widersprüchliche Client-Einstellungen gezielt abgleichen,
keine Zugriffssperren abschalten.

**Lokale Lieferung benötigt zusätzlich Jenkins, Portal und Lieferzugänge.**
`init` startet nur die Fachwerkzeugdienste, nicht den Publikationsstack.
Der Integrator verwendet diesen später über die Dev-Stack-Schnittstellen.
Fehlende Publikationszugänge verhindern reine CSV-Prüfung oder Modellierung nicht.
Die Einzelheiten stehen unter [Betrieb und Zugänge](agent/betrieb-und-zugaenge.md).

### 6. Betriebsordner im Client öffnen

Öffne immer `~/datenportal/datenportal-betrieb`, nicht den gemeinsamen
Elternordner oder den Integrator-Quellcheckout. Nach Änderungen an Client-Einträgen
MCP-Verbindungen und bestehende Sitzungen neu starten.

**Codex Desktop:** Betriebsordner als Projekt öffnen und seine
Projektkonfiguration als vertrauenswürdig bestätigen. Projektlokale MCPs gelten
nur in vertrauenswürdigen Projekten; siehe
[offizielle OpenAI-Dokumentation](https://learn.chatgpt.com/docs/extend/mcp).

**Codex CLI:** Bei bereits installierter und angemeldeter Codex-CLI:

```sh
cd "$HOME/datenportal/datenportal-betrieb"
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten codex
```

Der Launcher startet die Host-CLI mit den vorgesehenen Betriebseinstellungen.
Bei vorhandenen Checkouts unter `~/sources/` dieselben drei Mount-Optionen wie
beim dortigen `init` verwenden und lediglich `init` durch `codex` ersetzen.

**OpenCode Desktop:** Betriebsordner als Projekt öffnen, Agent **`datenportal`**
auswählen. Im Projektmenü **More options → Edit project → Extensions** die
Bereiche **MCPs** und **Skills** prüfen (geprüft mit Desktop 2.0.27).

**Mit OpenCode Desktop gelieferte CLI, macOS:** Bei Installation unter
`/Applications/OpenCode.app` im Terminal:

```sh
cd "$HOME/datenportal/datenportal-betrieb"
"/Applications/OpenCode.app/Contents/Resources/opencode-cli" --version
"/Applications/OpenCode.app/Contents/Resources/opencode-cli" .
```

Keine zusätzliche globale OpenCode-CLI voraussetzen. Bei einem anderen
Installationsort den App-Pfad anpassen. In Version 2.0.27 kann `mcp list` die
Default-Location des Hintergrundservers statt des aktuellen Projekts anzeigen.
Deshalb Projektpfad und projektspezifische Extensions prüfen; eine leere Liste
allein belegt keinen fehlenden MCP-Eintrag.

Erwartet werden diese **zwei MCP-Server**:

| Server | Aufgabe |
|---|---|
| `datenportal_integrator` | Prüfen, modellieren, Vorgänge bearbeiten und liefern |
| `datenportal_workspace` | Anhänge übernehmen und zulässige Arbeitsdateien lesen oder schreiben |

Datenblatt- und INTERLIS-MCP werden intern verwaltet; nicht zusätzlich registrieren.
Die **drei Skills** unter `.agents/skills/` sind `datenportal-themenintegrator`,
`datenportal-validierungsmodell` und `datenportal-organisation`. In Codex mit `$`,
in OpenCode mit `@` auswählen. Modellierung innerhalb einer Themenintegration
nutzt denselben Vorgang. Ein funktionierender MCP-Handshake ersetzt keinen
vollständig geprüften Fachdialog; Desktop-/CLI-Nachweise sind getrennt zu bewerten.
Im Betriebsprofil sind allgemeine Shell und Zugriffe auf Implementierungsquellen
gesperrt. Der Agent verwendet öffentliche Werkzeugschemas, Skills und Prüfberichte.
Bei fehlenden Informationen fragt er nach; bei Einrichtungsfehlern den Betreiber
einbeziehen und die Sperren beibehalten.

## Erste CSV bearbeiten

Ein Betriebsordner wird für mehrere Themen und Sitzungen verwendet. Bei der
Standardinstallation liegen die Originaldateien im Geschwisterordner
`../datenportal-pilot-daten`. Falls noch nicht vorhanden:

```sh
cd "$HOME/datenportal/datenportal-betrieb"
mkdir -p ../datenportal-pilot-daten
```

Stelle neue CSV-Dateien unverändert und mit ihrem Originalnamen dort bereit,
beispielsweise mit dem Dateimanager. Bereits vorhandene Dateien im eingerichteten
Pilotdatenordner direkt verwenden. In Stefans Einrichtung ist dies
`/Users/stefan/sources/datenportal-pilot-daten`; nichts verschieben.
CSV-Namen dürfen nur ASCII-Buchstaben, Ziffern und Unterstriche vor `.csv`
enthalten. Eine erforderliche Umbenennung ausdrücklich klären.

Wähle den Themenintegrations-Skill und passe Datei, Organisation und Identifier an:

```text
Integriere ../datenportal-pilot-daten/Fahrzeugbestand.csv für mfk,
Thema ch.so.fahrzeugbestand2025. Verwende das vorhandene Datenblatt im
Themenrepo, erstelle ein INTERLIS-Validierungsmodell und liefere nach
meinen Freigaben lokal. Prüfe CSV, Modell, Portal und Downloads.
```

Bei einem externen Eingang den vollständigen Originalpfad nennen, zum Beispiel
`/Users/stefan/sources/datenportal-pilot-daten/ch.so.fahrzeugbestand/Fahrzeugbestand_SO_251231.csv`.
Der Agent löst relative Angaben gegen den Betriebsordner auf und übergibt den
absoluten Originalpfad an `datenportal_workspace.import_attachment`. Dieses
Werkzeug erstellt eine getrennte Eingangskopie und prüft drei übereinstimmende
SHA-256-Werte. Nur die verifizierte Kopie wird an den Integrator übergeben.

Prüfe die angebotenen HTML-Berichte und beantworte die fachlichen Rückfragen.
CSV-, Modell-/Metadaten- und Publikationsfreigaben erfordern deine tatsächliche
Antwort zum jeweiligen Prüfstand. Beobachtete Werte sind noch keine bestätigten
Fachregeln. Für reine Modellierung wähle `datenportal-validierungsmodell`, für eine
eigenständige Organisationsanlage `datenportal-organisation`.

### Warum ein dauerhaft eingebundener Pilotdatenordner?

Ein einmal eingerichteter Eingang vermeidet Mount-Anpassungen für jede Datei.
Beide Clients speichern den Read-only-Mount in ihren MCP-Startargumenten; normale
neue Sitzungen brauchen keine erneute Einrichtung. Bei einem späteren `init`
alle Mount-Optionen erneut angeben: Ein Aufruf ohne sie kann die gespeicherten
Mounts entfernen. `doctor` allein speichert keine Client-Konfiguration.

Die vorhandene Docker-/MCP-Lösung genügt dafür; ein zusätzlicher lokaler
Import-MCP mit eigener Installation und Laufzeit entfällt. Das gesamte Home
wird nicht eingebunden, weil dies unnötig private Dateien und Entwicklungsquellen
zugänglich machen würde. Read-only schützt Originale vor Änderungen über diesen
Mount, auch beim bereits schreibbar eingebundenen Elternordner. Die drei
Prüfsummen sichern zusätzlich die unveränderte Übernahme ab. Ihr Entfernen würde
den fehlenden Dateizugriff im Container nicht beheben.

### Externe Dateien und Chat-Anhänge

Ein Chat-Anhang garantiert keinen Zugriff auf die tatsächlichen Dateibytes im
Container. Der Agent benötigt den zugänglichen Originalpfad; Chattext oder eine
Vorschau dürfen die Originaldatei nicht ersetzen.

Dateien ausserhalb der freigegebenen Eingänge unverändert im Pilotdatenordner
bereitstellen und dessen Dateipfad nennen. Alternativ einen weiteren gezielten
Read-only-Mount einrichten. Bei Standardpfaden beispielsweise:

```sh
./bin/datenportal-agent \
  --mount-ro ../datenportal-pilot-daten --mount-ro "$HOME/Downloads" init
./bin/datenportal-agent \
  --mount-ro ../datenportal-pilot-daten --mount-ro "$HOME/Downloads" doctor
```

Bei externen Checkouts auch deren beide `--mount-rw`-Optionen und den tatsächlich
verwendeten Pilotdatenpfad angeben. Danach beide MCP-Verbindungen und die Sitzung
neu starten. Auch bei späterem `init` alle Optionen beibehalten.
[Eingangsdateien](agent/eingangsdateien.md) beschreibt die Übernahme genau.

## Wiederaufnahme und Updates

Sitzungen dürfen geschlossen werden; Vorgänge und Eingänge bleiben im
Betriebsordner gespeichert. Zur Wiederaufnahme vorhandene `run_id` nennen und
keinen zweiten Vorgang für denselben Auftrag starten. Bei einem unklaren Upload
zuerst den bestehenden externen Lauf klären.

Vorgänge aus einem früheren Entwicklungsordner werden nicht automatisch
übernommen. Den alten Arbeitsbereich erhalten und eine Übernahme mit allen
referenzierten Ressourcen und Pfaden gezielt prüfen. Nur `state_dir` umzusetzen
oder einzelne Zustandsdateien zu kopieren ist keine vollständige Migration.
[Lieferung und Wiederaufnahme](agent/lieferung-und-wiederaufnahme.md)

Vor Updates aktive Arbeit abschliessen und MCP-Verbindungen trennen. Im
Betriebsordner bei Standardpfaden ausführen:

```sh
./bin/datenportal-agent --update-agent workspace update "$PWD"
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten init
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten doctor
```

Für Stefans bestehende Ordnerstruktur lautet der vollständige Ablauf:

```sh
./bin/datenportal-agent --update-agent workspace update "$PWD"
./bin/datenportal-agent \
  --mount-rw "$HOME/sources/datenportal-themenrepo" \
  --mount-rw "$HOME/sources/datenportal-dev-stack" \
  --mount-ro "$HOME/sources/datenportal-pilot-daten" init
./bin/datenportal-agent \
  --mount-rw "$HOME/sources/datenportal-themenrepo" \
  --mount-rw "$HOME/sources/datenportal-dev-stack" \
  --mount-ro "$HOME/sources/datenportal-pilot-daten" doctor
```

Das Update ersetzt nur unveränderte Paketdateien. Bei individuellen Änderungen
wird ein Konfliktbericht ausgegeben; Änderungen bewusst abgleichen und erneut
aktualisieren. Vor erfolgreicher Paketaktualisierung keine Fachsitzung starten:
Image und Paket müssen dieselbe Version besitzen. Eigene Konfiguration,
Zugangsdaten, Eingänge und Vorgänge werden nicht automatisch verschoben oder
überschrieben. Fachwerkzeug-Pins sind gesondert zu aktualisieren. Danach beide
MCP-Verbindungen und die Sitzung neu starten.

## Java-Konverter

Bei beauftragten Umformungen [CSV-Konverter](agent/csv-konverter.md) verwenden.
Rezepte und Java-/JUnit-Dateien unter `topics/<org>/<id>/` vorbereiten;
Kompilieren und Testen übernimmt das Integratorwerkzeug `transform`.
