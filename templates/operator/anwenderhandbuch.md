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

### 2. Fachliche Repositories bereitstellen

Für eine neue Einrichtung im Terminal:

```sh
mkdir -p "$HOME/datenportal"
cd "$HOME/datenportal"
git clone https://github.com/sogis/datenportal-themenrepo.git
git clone https://codeberg.org/edigonzales/datenportal-dev-stack.git
```

Das Themenrepo enthält Datenblätter, Modelle und Lieferjobs. Der Dev-Stack enthält
Skripte für die lokale Testumgebung und muss bereits bei `init` vorhanden sein.
`init` klont keine Repositories. Hast du diese Checkouts bereits, überspringe die
Clone-Befehle und verwende ihre vorhandenen Pfade in Schritt 4. Nichts verschieben.
Separate Quellcheckouts der Fach-MCPs, von GRETL oder Jenkins sind nicht nötig.

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
└── datenportal-dev-stack/
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

Beispiel für bereits vorhandene Checkouts ausserhalb des gemeinsamen Elternordners:
Nach Eintragen ihrer tatsächlichen absoluten Pfade in `config/local.toml` die
Befehle aus Schritt 5 mit folgenden Optionen ausführen (Beispielpfade ersetzen):

```sh
./bin/datenportal-agent \
  --mount-rw /absolut/datenportal-themenrepo \
  --mount-rw /absolut/datenportal-dev-stack init
./bin/datenportal-agent \
  --mount-rw /absolut/datenportal-themenrepo \
  --mount-rw /absolut/datenportal-dev-stack doctor
```

Mount-Verzeichnisse müssen existieren. Bei jedem späteren `init` dieselben
benötigten Mount-Optionen angeben: Sie werden aus dem aktuellen Aufruf in die
Client-Einträge übernommen. `harness-config` zeigt nur einen Konfigurationsvorschlag;
auch für diese Diagnose die benötigten Optionen mitgeben.

### 5. Initialisieren und Ergebnis prüfen

Bei Standardpfaden:

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
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
./bin/datenportal-agent codex
```

Der Launcher startet die Host-CLI mit den vorgesehenen Betriebseinstellungen.

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

Ein Betriebsordner wird für mehrere Themen und Sitzungen verwendet. Erstelle
für die Eingänge im Terminal einen Ordner:

```sh
cd "$HOME/datenportal/datenportal-betrieb"
mkdir -p eingang
```

Kopiere deine CSV unverändert und mit ihrem Originalnamen dorthin, beispielsweise
mit dem Dateimanager. CSV-Namen dürfen nur ASCII-Buchstaben, Ziffern und
Unterstriche vor `.csv` enthalten. Eine erforderliche Umbenennung ausdrücklich
klären; nicht still umbenennen. Der Datei-MCP übernimmt die Datei anschliessend
in einen getrennten Eingang und prüft drei übereinstimmende SHA-256-Werte.

Wähle den Themenintegrations-Skill und passe Datei, Organisation und Identifier an:

```text
Integriere eingang/Fahrzeugbestand.csv für mfk, Thema ch.so.fahrzeugbestand2025.
Verwende das vorhandene Datenblatt im Themenrepo, erstelle ein
INTERLIS-Validierungsmodell und liefere nach meinen Freigaben lokal.
Prüfe CSV, Modell, Portal und Downloads.
```

Prüfe die angebotenen HTML-Berichte und beantworte die fachlichen Rückfragen.
CSV-, Modell-/Metadaten- und Publikationsfreigaben erfordern deine tatsächliche
Antwort zum jeweiligen Prüfstand. Beobachtete Werte sind noch keine bestätigten
Fachregeln. Für reine Modellierung wähle `datenportal-validierungsmodell`, für eine
eigenständige Organisationsanlage `datenportal-organisation`.

### Externe Dateien und Chat-Anhänge

Der Agent benötigt den zugänglichen Originalpfad eines Anhangs. Ein Chat-Vorschaubild
ersetzt die Originaldatei nicht. Liegt sie ausserhalb des eingebundenen Elternordners,
das betreffende Verzeichnis bereits bei `init` lesbar einbinden, beispielsweise:

```sh
./bin/datenportal-agent --mount-ro "$HOME/Downloads" init
```

Danach beide MCP-Verbindungen und die Sitzung neu starten. Benötigte zusätzliche
`--mount-rw`-Optionen ebenfalls angeben. Auch bei späterem `init` diese Optionen
beibehalten. Ist der Anhangpfad unbekannt, die Originaldatei unverändert unter
`eingang/` bereitstellen und den Pfad nennen.
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
Betriebsordner ausführen; gegebenenfalls die benötigten Mount-Optionen ergänzen:

```sh
./bin/datenportal-agent --update-agent workspace update "$PWD"
./bin/datenportal-agent init
./bin/datenportal-agent doctor
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
