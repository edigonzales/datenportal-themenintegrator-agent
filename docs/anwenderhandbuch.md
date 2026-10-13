# Anwenderhandbuch

Der Integrator unterstützt drei Vorgänge: ein Thema integrieren (`topic`), ein INTERLIS-Modell erstellen (`model`) und eine Organisation anlegen (`organization`). Das Sprachmodell läuft in Codex oder OpenCode. Der Java-Kern prüft Dateien und kontrolliert Freigaben sowie externe Abläufe. Er benötigt keinen Modellzugang und betreibt keinen zusätzlichen Webserver.

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

## Mit dem Agenten arbeiten

### Quellcodefreies Betriebspaket

Die vollständige [Installation und Konfiguration](#installation-und-konfiguration)
steht am Anfang dieses Handbuchs. [Wiederaufnahme und Updates](#wiederaufnahme-und-updates)
beschreibt den laufenden Betrieb.

### Arbeitsbereich öffnen und Skill auswählen

Öffne nach der [Einrichtung](#installation-und-konfiguration) den Betriebsordner
als Projekt und prüfe seine zwei MCPs und drei Skills. Wähle den Skill nach dem
fachlichen Auftrag. Alle drei nutzen den Integrator und den Dateihelfer und sind
gemeinsam unter `.agents/skills/` verfügbar:

| Auftrag | Skill |
|---|---|
| Thema integrieren, CSV prüfen, Metadaten ändern oder Lieferung wiederaufnehmen | [datenportal-themenintegrator](../skills/datenportal-themenintegrator/SKILL.md) |
| Eigenständiges INTERLIS-Validierungsmodell ableiten | [datenportal-validierungsmodell](../skills/datenportal-validierungsmodell/SKILL.md) |
| Eigenständige Organisation mit Teams und Office anlegen | [datenportal-organisation](../skills/datenportal-organisation/SKILL.md) |

Bei Themenintegration mit ausdrücklich beauftragtem Modell bleibt der Hauptskill
verantwortlich und liest die Modellanleitung ergänzend. Derselbe `run_id` bleibt
erhalten. Ein fehlendes Office im Themenvorgang wird dort ergänzt; es löst keine
vollständige Organisationsanlage aus.

| Oberfläche | Skill ausdrücklich auswählen |
|---|---|
| OpenCode 2.x | `@datenportal-themenintegrator` im Prompt auswählen. |
| Codex | `$datenportal-themenintegrator` verwenden; in CLI und IDE ist auch die Auswahl über `/skills` möglich. |
| Ausgeschriebener Auftrag | „Verwende den Skill `datenportal-themenintegrator`.“ |

Die Aufrufweisen sind in der [OpenCode-2.x-Skill-Dokumentation](https://opencode.ai/v2/docs/skills/)
und der [Codex-Skill-Dokumentation](https://learn.chatgpt.com/docs/build-skills)
beschrieben. In Folgeantworten desselben Vorgangs musst du den Skill nicht
erneut auswählen. Wird er nicht gefunden, prüfe den geöffneten Arbeitsbereich
und die drei Skill-Verknüpfungen unter `.agents/skills/`. Für die anderen Skills
den jeweiligen Namen mit demselben `$`- beziehungsweise `@`-Präfix verwenden.

Hänge CSV-Daten und vorhandene XTF- oder XLSX-Metadaten an oder nenne zugängliche
absolute Dateipfade. Gib Organisation, Themenidentifier und bei Serien die
Ausgabe an; Organisationsaufträge benötigen keine Lieferdateien und keinen
Themenidentifier. Der Agent fragt fehlende Angaben nach. Chat-Anhänge übernimmt er
automatisch unverändert und vergleicht die Prüfsummen; du musst weder das
Kopieren noch die Prüfung zusätzlich beauftragen. Ist der Originalpfad nicht
zugänglich, fragt er danach. Die Übernahme ist noch keine fachliche Freigabe.

Vollständiger Startprompt für OpenCode:

```text
@datenportal-themenintegrator Integriere die angehängte CSV und die
XLSX-Metadaten für die Organisation statistikdienst, Thema
ch.so.bevoelkerung.altersstruktur, Ausgabe 2025. Erkläre zuerst die Daten
und zeige mir die CSV-Vorschau zur Freigabe. Bereite danach das Datenblatt
zur Prüfung vor. Ziel ist zunächst die lokale Lieferung. Frage fehlende
fachliche Angaben nach und halte an den menschlichen Freigaben an.
```

Derselbe Startprompt für Codex:

```text
$datenportal-themenintegrator Integriere die angehängte CSV und die
XLSX-Metadaten für die Organisation statistikdienst, Thema
ch.so.bevoelkerung.altersstruktur, Ausgabe 2025. Erkläre zuerst die Daten
und zeige mir die CSV-Vorschau zur Freigabe. Bereite danach das Datenblatt
zur Prüfung vor. Ziel ist zunächst die lokale Lieferung. Frage fehlende
fachliche Angaben nach und halte an den menschlichen Freigaben an.
```

Alle Themen, Zahlen und Dateinamen in den folgenden Szenarien sind Beispiele.
Ersetze sie durch deine Angaben; Platzhalter wie `<Vorgangs-ID>` stammen aus
deinem tatsächlichen Vorgang. Die Beispiele beschreiben unterstützte Abläufe,
keine durchgeführten Lieferungen. Den geprüften Betriebsstand hält die
[Abnahme](abnahme.md) getrennt fest.

### Ablauf und deine Entscheidungen

Die [Architekturskizze](entwicklerhandbuch.md#zusammenspiel-der-komponenten)
zeigt die beteiligten Komponenten. Das
[Sequenzdiagramm](entwicklerhandbuch.md#sequenz-einer-themenintegration)
zeigt ihre Zusammenarbeit bei einer Themenintegration einschliesslich Freigaben
und optionaler INT-/PROD-Publikation.

Du kannst einen vollständigen Ablauf oder einen einzelnen Schritt beauftragen.
Für ein Thema mit CSV und neuen oder geänderten Metadaten gilt:

```text
Du: Dateien und fachliche Angaben liefern
                  |
Agent: übernehmen, bei Bedarf konvertieren, CSV prüfen und erklären
                  |
Du: gezeigte CSV-Fassung freigeben
                  |
Agent: Datenblatt bearbeiten und prüfen
       optional: ausdrücklich beauftragtes INTERLIS-Modell ableiten
                  |
Du: Metadaten und gegebenenfalls Modell freigeben
                  |
Agent: Änderungen übernehmen, lokal liefern, Portal und Downloads prüfen
                  |
Du: lokal abschliessen ODER konkreten Plan für INT/PROD freigeben
                  |
Bei Repository-Änderungen: Agent erstellt PR -> Mensch mergt
                  |
Agent: Zielstand prüfen, Lieferung fortsetzen und Ergebnis kontrollieren
```

Bei reinen Metadatenlieferungen entfällt die CSV-Freigabe. Bei reinen
Datenlieferungen bleiben die bestehenden Metadaten erhalten. Eigenständige
Modell- und Organisationsvorgänge haben ihre jeweilige Freigabe und benötigen
keine Datenpublikation. Die automatische Anhangübernahme, ein Korrekturauftrag
oder ein allgemeines „Integriere das Thema“ ersetzen kein OK zum konkret
gezeigten Prüfstand. Kontakte, Benutzerkennungen und fachliche Regeln werden
bei fehlender Grundlage nachgefragt.

### HTML-Prüfberichte lesen

Die Vorschauen für CSV-Prüfung, Umwandlung, Metadaten, Modellableitung,
Modellprüfung und Organisation sind lokale HTML-Dateien. Öffne den vom Agenten
angegebenen Bericht im Browser oder Dateipanel. Die Darstellung benötigt weder
Internetzugriff noch JavaScript.

Beginne beim **Prüfstand**: Dort stehen das Ergebnis, Fehler, Warnungen und
vorhandene offene Fragen oder manuelle Prüfpunkte. „Nicht geprüft“ bezeichnet
fehlende Prüfergebnisse. Ein Modellkandidat benötigt noch die Gesamtprüfung;
auch „Prüfung bestanden“ ersetzt deine fachliche Freigabe nicht.

Unter **Fachliche Angaben** findest du je nach Phase Spalten und Beobachtungen,
Vorher-/Nachher-Ergebnisse, Datenblattattribute, Serienausgaben, Modellangaben
oder Organisationsdaten. SQL, Gradle-Tasks und INTERLIS-Modelle erscheinen in
eigenen Codeblöcken mit Zeilenumbrüchen und Syntaxfarben. Dabei bleibt der
Quelltext unverändert. Breite Tabellen und Codeblöcke lassen sich seitlich
scrollen.

**Vorbereitete Dateien und Änderungen** zeigt jede Datei einzeln. Klappe die
zeilenweisen Änderungen auf: `+` kennzeichnet hinzugefügte, `−` entfernte Zeilen.
Die vollständigen Kandidaten- und Bestandstexte bleiben ebenfalls zugänglich.
Diese Ansicht verwendet ausschliesslich den gespeicherten Vergleich und prüft
seine Prüfsummen. Bei unklaren älteren Vergleichsdaten erscheint ein Hinweis
zusammen mit dem vollständigen Originalvergleich. Sehr grosse Dateien werden
ohne aufwendigen Zeilenvergleich und Syntaxfarben angezeigt.

Unter **Technische Details** und **Vollständige Rohdaten** kannst du Nachweise,
Prüfsummen und zusätzliche Felder nachlesen. Eingebetteter JSON-Text wird, wenn
eindeutig lesbar, zusätzlich strukturiert dargestellt; der Originalwert bleibt
aufklappbar. Die Druckansicht umfasst auch die aufklappbaren Details.

Die neue Darstellung gilt für neu erzeugte Vorschauen. Bereits gespeicherte
HTML-Dateien bleiben erhalten. Ein Softwareupdate wiederholt keine fachlichen
Prüfungen und verändert keine Freigaben.

### 1. CSV verstehen und prüfen

**Ausgangssituation:** Du erhältst `bevoelkerung_2025.csv` und möchtest wissen,
ob die Lieferung zur vereinbarten Struktur passt.

**Agentenunterstützung:** Der Agent prüft alle Zeilen, erklärt Spalten und
Typvorschläge und zeigt fehlende Werte sowie Auffälligkeiten. Beispielsweise:

| Wert in der CSV | Fachliche Frage |
|---|---|
| Gemeindecode `0012` | Muss die führende Null erhalten bleiben? Dann ist ein Texttyp zu prüfen. |
| Leere Einwohnerzahl | Bedeutet sie „nicht erhoben“? Sie wird nicht automatisch zu `0`. |
| Einwohnerzahl `0` | Ist dies ein tatsächlicher Messwert? Er bleibt von fehlenden Werten unterscheidbar. |

**Ergebnis:** Eine HTML-Vorschau mit Beispielen und Prüfmeldungen.
**Deine Entscheidung:** Bedeutung unklarer Werte klären, Korrekturen beauftragen
oder die konkret gezeigte CSV-Fassung freigeben.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Prüfe die angehängte CSV für die Organisation `statistikdienst`, Thema `ch.so.bevoelkerung.altersstruktur`, Ausgabe `2025`. Erkläre Spalten, fehlende Werte und Auffälligkeiten. Zeige mir die Vorschau und halte vor der CSV-Freigabe an.

Technische Details: [CSV-Prüfung und Themenablauf](#durchgängiges-thema-csv-xlsx-und-xtf).

### 2. Lieferformat umwandeln

**Ausgangssituation:** Der Lieferant liefert eine Spalte je Jahr; benötigt
wird eine Zeile je Gemeinde und Jahr.

| Eingangsformat | Vereinbartes Zielformat |
|---|---|
| `Gemeinde;2024;2025` | `Gemeinde;Jahr;Einwohner` |
| `Beispielwil;1200;1215` | `Beispielwil;2024;1200` |
| | `Beispielwil;2025;1215` |

**Agentenunterstützung:** Zielstruktur klären, einen vorhandenen Java-Konverter
verwenden oder einen neuen mit Tests vorbereiten und die erzeugte CSV prüfen.
Die Originaldatei bleibt unverändert.

**Ergebnis:** Vorher/Nachher-Vergleich, geprüfte Ziel-CSV und bei wiederkehrender
Umwandlung ein Konverter. Alternativ entsteht eine lokale Formatvorgabe für
den Lieferanten; sie wird nicht ungefragt versendet.
**Deine Entscheidung:** Zielstruktur bestätigen und festlegen, ob der Lieferant
künftig passend liefert oder der Integrator jede Lieferung konvertiert.
Anschliessend die Ziel-CSV prüfen und freigeben.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Die angehängte CSV für `statistikdienst`, Thema `ch.so.bevoelkerung.altersstruktur`, enthält eine Spalte pro Jahr. Wir brauchen `Gemeinde;Jahr;Einwohner`. Bereite einen wiederkehrenden Konverter vor, der leere Werte erhält. Zeige Vorher/Nachher und die geprüfte Ziel-CSV zur Freigabe. Kläre die Zuordnung zu den Serienausgaben mit mir.

Technische Details: [Konverter und Serien](#serien-teillieferungen-und-konverter).

### 3. Datenblatt aus XLSX-Metadaten erstellen

**Ausgangssituation:** Zur CSV liegt eine Excel-Datei mit Titel, Beschreibung,
Einheiten und Zuständigkeit vor.

**Agentenunterstützung:** Der Agent übernimmt passende Angaben in das
Datenblatt, dokumentiert ihre Herkunft bis zu den Quellenzellen und fragt
fehlende Pflichtangaben nach. Eigene Formulierungsvorschläge bleiben erkennbar.
Excel-Formeln werden nicht ausgeführt; fehlende gespeicherte Formelresultate
werden als fehlend gemeldet und nicht als Null übernommen.

**Ergebnis:** Ein geprüftes Datenblatt als XTF und eine HTML-Vorschau mit
Quellen, Änderungen und Prüfmeldungen.
**Deine Entscheidung:** Fehlende Fachangaben ergänzen und nach der CSV-Freigabe
den konkreten Metadatenstand prüfen und freigeben.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Erstelle für die Organisation `statistikdienst`, Thema `ch.so.bevoelkerung.altersstruktur`, Ausgabe `2025`, ein Datenblatt aus der angehängten XLSX. Prüfe zuerst die angehängte CSV und zeige sie zur Freigabe. Verwende sie danach zur Beschreibung der Attribute. Kennzeichne eigene Textvorschläge, frage fehlende Fachangaben nach und zeige das Datenblatt zur Prüfung.

Technische Details: [XLSX, Herkunft und XTF](#durchgängiges-thema-csv-xlsx-und-xtf).

### 4. Nur Metadaten korrigieren

**Ausgangssituation:** Die Beschreibung der Erhebungsmethode ändert sich;
die vorhandenen Daten sollen erhalten bleiben.

**Agentenunterstützung:** Bestehenden Stand laden, Änderungen vorbereiten,
Datenblatt exportieren und validieren. Eine CSV wird dafür nicht benötigt.

**Ergebnis:** Geprüfte Metadaten mit einem Vergleich zum bisherigen Stand.
**Deine Entscheidung:** Die gezeigten Metadatenänderungen freigeben; eine
CSV-Freigabe entfällt.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Ändere für `statistikdienst`, Thema `ch.so.bevoelkerung.altersstruktur`, nur die Beschreibung der Erhebungsmethode gemäss angehängter XTF. Erhalte die vorhandenen Daten. Zeige die Änderungen zum bisherigen Stand und bereite eine reine Metadatenlieferung zur Freigabe vor.

Technische Details: [Teillieferungen](#serien-teillieferungen-und-konverter).

### 5. Daten oder neue Serienausgaben nachliefern

**Ausgangssituation:** Für eine vorhandene Ausgabe kommt eine korrigierte CSV,
oder eine Serie erhält die nächste Jahresausgabe.

**Agentenunterstützung:** Der Agent prüft die CSV gegen die bestehende
Vereinbarung zu Spalten, Reihenfolge und Werten. Bei einer reinen Datenlieferung
bleibt das Datenblatt erhalten. Bei einer neuen Ausgabe prüft er zusätzlich,
ob sie bereits im Datenblatt steht und welche Ausgabe als aktuell markiert ist.
Serienidentifier und Ausgabe wie `2026` bleiben getrennt.

**Ergebnis:** Geprüfte Datenlieferung und gegebenenfalls ein ergänztes Datenblatt.
**Deine Entscheidung:** CSV freigeben; nötige Ausgabenänderungen separat im
Metadatenreview bestätigen. Eine neue Jahresausgabe ist nicht automatisch eine
reine Datenlieferung.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Integriere für die Organisation `statistikdienst`, Serie `ch.so.bevoelkerung.altersstruktur`, die Ausgabe `2026` mit der angehängten CSV. Prüfe die bestehende Datenstruktur und ob die Ausgabe bereits im Datenblatt vorhanden ist. Bereite notwendige Ergänzungen zur Prüfung vor und kläre, welche Ausgabe aktuell sein soll. Ziel ist zunächst die lokale Lieferung nach den nötigen Freigaben.

Technische Details: [Serien und Teillieferungen](#serien-teillieferungen-und-konverter).

### 6. INTERLIS-Modell ausdrücklich beauftragen

**Ausgangssituation:** Die vereinbarte Datenstruktur soll mit einem
INTERLIS-Modell überprüfbar werden.

**Agentenunterstützung:** Auf ausdrücklichen Auftrag ein Modell aus CSV und
bestätigtem Datenblatt ableiten, Modellidentität und offene Fachregeln klären
und die CSV gegen das Modell prüfen lassen. Zur erfolgreichen Modellableitung
gehört automatisch auch die Vorbereitung von `dataset.gradle` mit Copy- und
Validierungstask im Themenordner. Standard sind INTERLIS 2.4,
Profil `SO-DATA` ohne Kommentare und zunächst eine flache Klasse. Beobachtete Werte von 120 bis
20'000 begründen keinen fachlichen Wertebereich; zufällige Eindeutigkeit
begründet keinen Schlüssel.

**Ergebnis:** INTERLIS-Modell (`.ili`), `dataset.gradle`, Prüfresultate und
aktualisierte Modellreferenz im Datenblatt werden gemeinsam vorbereitet.
Die erzeugte Taskdatei legt für CSV-Datenlieferungen diese Reihenfolge fest:

```text
stageThemenCsv (Copy)
  → validateThemenCsv (CsvValidator)
    → preparePublicationWorkspace
```

Der Copy-Task stellt Jenkins-Uploads ohne Dateiendung bytegleich als
`build/integrator-validation/input.csv` bereit. Der GRETL-CsvValidator prüft
diese Kopie gegen das Modell. Schlägt die Validierung fehl, stoppt der Ablauf
vor der Publikationsvorbereitung. Reine Metadatenlieferungen überspringen
die Copy- und CSV-Validierungstasks.

**Deine Entscheidung:** Modellname mit Datumssuffix, absolute URI, passendes
ISO-Versionsdatum sowie unbelegte Regeln klären. Numerische Vorschläge mit
Begründung und Spielraum für künftige Lieferungen im Modellreview prüfen. Beim Themenvorgang
Modell, `dataset.gradle`, Datenblatt und Prüfresultate gemeinsam prüfen und
die konkreten Modell- und Metadatenstände freigeben. Die Taskdatei gehört zur
Modellfreigabe.
Eigenständige Modellierung ist auch ohne Datenpublikation möglich.

**Beispielprompt für Modellierung im Themenvorgang:**

> Verwende den Skill `datenportal-themenintegrator`. Leite im Vorgang `<Vorgangs-ID>` aus der bestätigten CSV und dem Datenblatt ein INTERLIS-Modell ab. Frage die Modellidentität und fachlich nicht belegte Regeln nach. Zeige die dabei regulär vorbereiteten Dateien `.ili` und `dataset.gradle` mit dem Copy-Task `stageThemenCsv` und dem Validierungstask `validateThemenCsv` gemeinsam mit dem aktualisierten Datenblatt und den Prüfresultaten zur Freigabe.

**Beispielprompt für eigenständige Modellierung:**

> Verwende den Skill `datenportal-validierungsmodell`. Leite für die Organisation `statistikdienst`, Thema `ch.so.bevoelkerung.altersstruktur`, aus der angehängten CSV und XTF ein Validierungsmodell ab. Prüfe zuerst die CSV und warte auf meine Datenfreigabe. Kläre danach Modellidentität und offene Fachregeln, zeige Modell, Task und aktualisiertes Datenblatt zur Modellfreigabe. Publiziere keine Daten.

Der eigenständige Auftrag verwendet `workflow=model`; CSV, Datenfreigabe und
konkreter exportierter Metadatenstand sind weiterhin Voraussetzungen. Der
integrierte Auftrag bleibt dagegen bei `workflow=topic` und benötigt beide
Gates `metadata` und `model` für den gemeinsamen Review.

Technische Details: [INTERLIS-Modellierung](#interlis-modellierung).

### 7. Organisation oder Dienststelle vorbereiten

**Ausgangssituation:** Eine neue Fachstelle möchte künftig Themen publizieren.

**Agentenunterstützung:** Bestehende Dienststellen und Teams prüfen,
Organisationsdateien und Berechtigungszuordnungen vorbereiten und validieren.
Organisation und Dienststelle haben getrennte Kennungen. Kontakte und
Benutzerkennungen müssen tatsächlich vorliegen oder von dir bestätigt werden.

**Ergebnis:** Ein geprüfter Änderungsvorschlag mit Dateien und Berechtigungen.
**Deine Entscheidung:** Zuständigkeit, Lese-/Build-Teams und gegebenenfalls neue
Mitglieder bestätigen und den Organisationsstand freigeben. Eine Organisation
ohne Thema kann im Repository angelegt werden; ihr Jenkins-Job entsteht erst
mit dem ersten Thema. Es wird kein Dummy-Thema angelegt.

**Beispielprompt:**

> Verwende den Skill `datenportal-organisation`. Bereite die Organisation `mobilitaet` mit dem Titel „Fachstelle Mobilität“ vor. Prüfe zuerst vorhandene Dienststellen und Teams. Frage fehlende Kontakte und Berechtigungszuordnungen nach und zeige alle geplanten Änderungen zur Freigabe. Lege noch kein Thema an.

Für eine einzelne fehlende Dienststelle in einem bestehenden Themenvorgang
bleibt `datenportal-themenintegrator` zuständig und verwendet `office`.

Technische Details: [Organisation und Dienststelle](#neue-organisation-und-dienststelle).

### 8. Lokal liefern und INT oder PROD vorbereiten

**Ausgangssituation:** CSV und Metadaten sowie ein gegebenenfalls beauftragtes
Modell sind freigegeben; das Thema soll zunächst lokal geprüft werden.

**Agentenunterstützung:** Freigegebene Änderungen übernehmen, Lieferung
begleiten und Portalansicht, Metadaten und Downloads prüfen. Anschliessend kann
der Agent einen konkreten Publikationsplan für INT oder PROD zeigen. Dafür
müssen die jeweilige Umgebung und passende Zugänge eingerichtet sein.

**Ergebnis:** Bei Erfolg ein geprüfter lokaler Portal-Link; optional ein
Publikationsplan mit Ziel, Dateien und Repository-Änderungen.
**Deine Entscheidung:** Lokal abschliessen oder den konkreten Zielplan
ausdrücklich freigeben. Bei Repository-Änderungen erstellt der Agent einen PR,
den ein Mensch mergt. Erst nach Prüfung des Zielstands folgt die Lieferung.
Reine Datenlieferungen ohne Repository-Änderungen brauchen keinen PR.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Führe den freigegebenen Vorgang `<Vorgangs-ID>` lokal bis zur überprüften Portalansicht fort. Bereite danach den Publikationsplan für INT vor und zeige Ziel, Dateien und nötige Repository-Änderungen zur ausdrücklichen Freigabe.

Technische Details: [Lokale Lieferung und Zielpublikation](#durchgängiges-thema-csv-xlsx-und-xtf).

### 9. Unterbrochene Vorgänge wiederaufnehmen

**Ausgangssituation:** Die Sitzung wurde beendet oder Jenkins meldet einen
Timeout, dessen Ausgang unklar ist.

**Agentenunterstützung:** Den gespeicherten Vorgang und bestehende externe
Läufe prüfen und am bestätigten Stand fortsetzen. Bei unklarem Uploadstatus
zuerst aufklären, ob bereits geliefert wurde. Eine erfolgte Publikation mit
anschliessendem Reload- oder Downloadfehler wird getrennt nachgeprüft.

**Ergebnis:** Eine Übersicht über abgeschlossene und offene Schritte sowie
der nächste ausführbare Schritt oder eine konkrete Betreiberhandlung.
**Deine Entscheidung:** Offene Fachfragen beantworten und veränderte
Prüfstände erneut freigeben. Ein Timeout allein rechtfertigt keinen erneuten
Upload und keinen zweiten Vorgang am bisherigen Lauf vorbei.

**Beispielprompt:**

> Verwende den Skill `datenportal-themenintegrator`. Nimm den Vorgang `<Vorgangs-ID>` wieder auf. Kläre den bestehenden Jenkins-Lauf und den Publikationsstatus. Zeige, was abgeschlossen ist und was fehlt, und setze den Vorgang fort, soweit die vorhandenen Freigaben gelten. Lade bei unklarem Status nicht erneut hoch.

Technische Details: [Wiederaufnahme und Fehler](#wiederaufnahme-und-fehler).

### Beispieldialog: Korrektur und Freigabe

Der folgende Dialog ist eine Illustration, kein tatsächlicher Vorgang und
keine verwendbare Freigabe. In echten Vorgängen zählt deine tatsächliche
Antwort zum jeweils gezeigten Prüfstand.

| Sprecher | Beispiel |
|---|---|
| Agent | „Die CSV-Vorschau A schlägt den Gemeindecode als Zahl vor. Soll `0012` mit führenden Nullen erhalten bleiben?“ |
| Du | „Ja, behandle den Code als Text und erhalte die führenden Nullen.“ |
| Agent | „Ich habe die Typzuordnung korrigiert und erneut geprüft. Vorschau B zeigt `0012` unverändert als Text. Bitte prüfe diese Fassung.“ |
| Du | „Ich habe Vorschau B geprüft und gebe diese CSV-Fassung frei.“ |
| Agent | „Ich halte deine Freigabe für den gezeigten Prüfstand fest und bereite das Datenblatt zur nächsten Prüfung vor.“ |

Der Korrekturauftrag in der zweiten Zeile ist noch keine Freigabe. Eine
CSV-Freigabe umfasst weder das spätere Datenblatt noch eine INT-/PROD-Publikation.

## Betrieb und Updates

### Täglicher Start

Docker starten, den Integrator-Arbeitsbereich im Client öffnen und den MCP
verbinden. Ein erneutes `init` ist nicht vor jeder Sitzung nötig. Fehlende eigene
Fachdienste startet der Integrator bei Bedarf. Die Dienste bleiben nach dem
Schliessen des Chats verfügbar; Docker-Autostart auf dem Rechner richtet der
Agent nicht ein.

Mit `docker compose down` **im Betriebsordner** kannst du die eigenen
Fachdienste anhalten. Dabei bleibt der Gradle-Cache erhalten. Keine Volumes zur
normalen Fehlerbehebung löschen. Der Publikationsstack gehört zum separaten
Dev-Stack und wird dadurch nicht angehalten.

### Wiederaufnahme und Bereitschaft

`init` protokolliert seine Schritte in `.datenportal-integrator/init.json` und
verhindert parallele Initialisierungen. Nach Behebung eines Fehlers denselben
Befehl erneut ausführen. Eine ausdrücklich mit `--config` gewählte fehlende
Datei ist ein Fehler; nur die fehlende Standardkonfiguration wird angelegt.

Der Funktionstest prüft Datenblatt-Import/-Export, XTF, Modellableitung und
je eine gültige und absichtlich ungültige CSV mit GRETL. Er verwendet private
Themenkopien und ausschliesslich synthetische Freigaben ohne Publikationsprofile.
Nachweise liegen unter `.datenportal-integrator/smoke/`. Unveränderte gültige
Nachweise werden wiederverwendet; veränderte Grundlagen verlangen neue Prüfungen.
`init --skip-smoke` ist ein Entwicklungsweg und meldet keine volle Bereitschaft.

### Agent-Image aktualisieren

Im Betriebspaket Image und Paket gemeinsam über
[Wiederaufnahme und Updates](#wiederaufnahme-und-updates) aktualisieren. Ein
Git-Pull oder ein isoliertes Image-Update ersetzt die Paketaktualisierung nicht.
Entwicklungsstände beschreibt das
[Entwicklerhandbuch](entwicklerhandbuch.md#docker-launcher-und-initialisierung).

### Fachwerkzeuge aktualisieren

Die Image-Referenzen stehen unter `[datasheet].image`, `[interlis].image` und
`[gretl].image` in deiner lokalen Konfiguration. Einen geprüften neuen Pin aus der
aktuellen Vorlage beziehungsweise dem [Abnahmestand](abnahme.md) bewusst übernehmen.
Ein unveränderlicher Digest bezeichnet immer denselben Inhalt; `--update-tools`
macht daraus keine automatische Suche nach `latest`.

Bei allen folgenden `init`-Beispielen für Standardpfade die vollständigen
Mount-Optionen der eigenen Einrichtung verwenden; für externe Checkouts siehe
[Konfiguration prüfen](#4-konfiguration-prüfen).

Vor einem Werkzeugwechsel MCP-Verbindungen trennen und die **eigenen** Dienste
mit der bisherigen Compose-Konfiguration anhalten. Im Betriebsordner:

```sh
docker compose down
# Jetzt die gewünschten Image-Referenzen in config/local.toml ändern.
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten init --update-tools
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten doctor
```

`init` bereitet die ausgewählten Stände und die eigene Compose-Konfiguration
vor und prüft erneut. Anschliessend den Client verbinden. Betroffene fachliche
Kandidaten neu prüfen und erforderliche Freigaben erneuern. Eine abweichende
laufende Instanz wird nicht still ersetzt. Agent-Update und Werkzeugupdate sind
zwei getrennte Vorgänge.

### Mounts und Aufruf aus Codex oder OpenCode

Der Launcher bindet den gemeinsamen Elternordner des Betriebsordners und der fachlichen Checkouts schreibbar unter
dem gleichen absoluten Pfad ein. Der explizite Read-only-Mount des Pilotdatenordners
überlagert dessen schreibbaren Eltern-Mount. Eingangsdateien ausserhalb dieses Ordners
benötigen einen zusätzlichen Mount. Gib ihn bereits bei `init` an, damit
die erzeugten Client-Einträge ihn bei späteren Aufrufen behalten:

```sh
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten --mount-ro '/pfad/mit leerzeichen/eingang' init
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten --mount-ro '/pfad/mit leerzeichen/eingang' doctor
```

Für abweichende Themen-/Stack-Checkouts oder einen externen Arbeitsordner
`--mount-rw` verwenden. Die Konfiguration muss diese Pfade ebenfalls enthalten.
Mounts verlangen vorhandene Verzeichnisse; Symlinks werden aufgelöst. Übernommene
Chat-Anhänge liegen bereits im Betriebsordner und benötigen keinen Zusatzmount.

Die Konfigurationsauswahl erfolgt über `--config /absoluter/pfad/local.toml`,
alternativ `DATENPORTAL_INTEGRATOR_CONFIG`, sonst `config/local.toml` im
Betriebsordner. Eine externe Konfigurationsdatei muss ebenfalls erreichbar
sein. Zugangsdatenwerte gehören nicht in diese Datei.

`DATENPORTAL_FORWARD_ENV` benennt weitergereichte Umgebungsvariablen. `init`
speichert deren Namen in den Client-Einträgen. Werte müssen in der späteren
Startumgebung vorhanden sein; eine GUI erbt Shell-Variablen nicht automatisch.

### Vorhandene lokale Konfiguration umstellen

Es werden ausschliesslich die aktuellen Formate unterstützt. Die entfernten
Felder `datasheet_mcp_url`, `gretl_java_home`, `gretl_offline_jars` sowie
`[interlis].jar` und `java_command` führen zu `invalid_configuration`.
Vergleiche deine Datei mit `config/local.example.toml`, übernimm
bewusst die benötigten aktuellen Abschnitte und erhalte eigene Pfade,
Ports und Umgebungsprofile. Es gibt keine automatische Migration.

Für Codex ist ein Launcher-Eintrag erforderlich; OpenCode 2.x verwendet
`mcp.servers.datenportal_integrator`, `disabled` und ein Timeout-Objekt.
Alte Java-Startbefehle, direkte MCP-Einträge und numerische Timeouts führen zu
`harness_conflict`. Den gemeldeten Eintrag sichern und bewusst anhand von
`./bin/datenportal-agent harness-config` neu einrichten. Fremde Einträge nicht
löschen. Bei einem erkannten Konflikt verändert `init` keine der beiden
Client-Dateien. Danach `init` erneut ausführen.

Ein alter GRETL-Runtime-Nachweis wird ebenfalls nicht überschrieben: die in der
Fehlermeldung genannte Datei ausserhalb ihres Runtime-Verzeichnisses archivieren
und mit `init` neu einrichten. Vorgänge und fachliche Artefakte bleiben erhalten.
Alte Vorgangsschemata können mit diesem Agenten nicht fortgeführt werden; siehe
[Wiederaufnahme und Fehler](#wiederaufnahme-und-fehler).

### Lokale Publikation vorbereiten

Für CSV-Prüfung, Datenblattbearbeitung und Modellierung genügt die Einrichtung
oben. Erst die lokale Lieferung benötigt Jenkins-Zugangsdaten und den
Publikationsstack. Der Agent verwendet eine passende laufende Instanz oder
startet die vorhandenen Dev-Stack-Skripte im freigegebenen Lieferablauf.

Ein manueller vollständiger Start mit `../datenportal-dev-stack/scripts/up.sh`
ist ein gesonderter Betriebsweg: Er richtet Secrets und Garage ein und führt
Seed, Erstpublikation und Portalstart aus. Auf dem Host benötigt er OpenSSL und
Python 3. Das ist kein zusätzlicher Schritt der Integrator-Erstinstallation.
Beim Aufruf über den Java-Integrator stehen diese Werkzeuge im Agent-Image zur
Verfügung. `--infrastructure-only` beim manuellen Stackstart überspringt den
Bootstrap, benötigt aber weiterhin OpenSSL.

INT und PROD müssen mit tatsächlichen Adressen, Zielbranch und benannten
Zugangsvariablen bewusst eingerichtet werden. Es gibt dafür keine aktivierten
Standardprofile. Fachliche Freigaben und der menschliche PR-Merge gelten
unabhängig von der technischen Bereitschaft.

### GitHub-Anmeldung im Docker-Betrieb

`gh` ist bereits im Agent-Image installiert. Eine lokale `gh`-Installation oder
ein `gh auth login` auf dem Host ist für den Docker-Betrieb nicht erforderlich.
Für GitHub-HTTPS-Zugriffe und Pull Requests verwendet der Container `GH_TOKEN`;
der Launcher richtet `gh auth git-credential` als Git-Credential-Helper ein.
Das Token muss die für den Zielvorgang erforderlichen Repository-Rechte besitzen.
Siehe auch die [GitHub-CLI-Dokumentation zu Umgebungsvariablen](https://cli.github.com/manual/gh_help_environment).

Im Integrator-Root in Bash oder zsh ausführen. Die Token-Eingabe bleibt unsichtbar;
die Weitergabeliste wird um die GitHub- und Git-Variablen ergänzt, bestehende
Namen bleiben erhalten:

```sh
printf 'GitHub-Token: '
read -rs GH_TOKEN
printf '\nGit-Autorenname: '
read -r GIT_AUTHOR_NAME
printf 'Git-Autoren-E-Mail: '
read -r GIT_AUTHOR_EMAIL
export GH_TOKEN GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL
export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME"
export GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"
export DATENPORTAL_FORWARD_ENV="${DATENPORTAL_FORWARD_ENV:+$DATENPORTAL_FORWARD_ENV }GH_TOKEN GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL"

# Die Variablennamen auch in die projektlokale MCP-Konfiguration übernehmen.
./bin/datenportal-agent --mount-ro ../datenportal-pilot-daten init
```

Die Werte müssen beim späteren Start des Launchers beziehungsweise des
MCP-Clients ebenfalls in dessen Umgebung vorhanden sein. `init` speichert hier
nur die zusätzlichen Variablennamen. Nach Änderungen bestehende MCP-Verbindungen
neu starten. Ein aus dem Finder gestarteter Desktop erbt die Shell-Variablen
nicht automatisch; seine Startumgebung muss die Werte ausdrücklich bereitstellen.
Der lokale Jenkins-Credential-Store verwaltet keine GitHub-Tokens.

Nach erfolgreicher Einrichtung die Anmeldung im ausgewählten Agent-Image prüfen:

```sh
agent_image_id=$(cat .datenportal-integrator/launcher/image.id)
docker run --rm -e GH_TOKEN --entrypoint gh "$agent_image_id" \
  auth status --hostname github.com
```

Dieser Aufruf liest den Anmeldestatus und verwendet das Token ausschliesslich
aus der Umgebung. Ein erfolgreicher Check bestätigt noch keine Schreibberechtigung
für das Zielrepository. Eine bestehende Host-Anmeldung und persönliche
Git-Konfigurationen werden nicht automatisch in den Container übernommen;
persönliche Credential-Verzeichnisse werden nicht pauschal eingebunden.
Token-Werte weder in versionierte Dateien noch in Chatnachrichten schreiben.

### Jenkins-Zugangsdaten für Codex und OpenCode

Der gemeinsame Java-Kern kann lokale Zugangsdaten aus
`.datenportal-integrator/credentials/local.json` lesen. Damit muss eine GUI keine
Jenkins-Variablen von ihrem Startprozess erben. Der Store ist ignoriert und
unverschlüsselt; Verzeichnisrechte 0700 und Dateirechte 0600 schützen den Zugriff.
Symlinks, fremde Eigentümer und weitergehende Rechte werden abgewiesen.

Einmalig Benutzer und Token in einer Shell hinterlegen, ohne den Token in
Kommandoargumente oder den Chat zu schreiben:

```sh
printf 'Jenkins-Benutzer: '
read -r credential_user
printf 'Jenkins-Token: '
read -rs credential_token
printf '\n'
printf '%s' "$credential_token" | ./bin/datenportal-agent credentials set local --username "$credential_user" --token-stdin
unset credential_token credential_user
./bin/datenportal-agent credentials status local
```

Alternativ ausdrücklich aus der vorhandenen Umgebung übernehmen:

```sh
DATENPORTAL_FORWARD_ENV="DATENPORTAL_LOCAL_USER DATENPORTAL_LOCAL_TOKEN" \
  ./bin/datenportal-agent credentials set local --from-env
./bin/datenportal-agent credentials remove local
```

Bei abweichender Konfiguration gelten deren Variablennamen und `--config`.
Ein vollständiges Umgebungspaar hat Vorrang vor dem Store. Teilweise gesetzte
Werte sind ein Fehler; Benutzer und Token aus verschiedenen Quellen werden nicht
gemischt. Store-Einträge gelten nur für das gewählte lokale Profil und seine
Jenkins-Adresse. Eine geänderte Adresse erfordert eine neue Hinterlegung.
INT/PROD bleiben bei expliziten Umgebungsvariablen.

`doctor` zeigt technische Bereitschaft und Publikationszugänge getrennt.
Modellierung und `init` benötigen keine Jenkins-Zugangsdaten. Eine neue
Anlieferung prüft die Zugangsdaten vor Stack-Änderungen; fehlende oder abgewiesene
Zugänge erlauben keine Passwortversuche oder Container-Neuerstellung.

Nach einem JAR-Update den MCP einmal neu verbinden. Spätere Änderungen am Store
werden vom laufenden MCP ohne Neustart gelesen. Den vorhandenen Vorgang anhand
seiner ID fortsetzen; bei unbestätigtem Upload zunächst aufklären.

## Codex Desktop, Codex CLI und OpenCode

Die vier getrennten Startwege und die Kontrolle von MCPs und Skills stehen unter
[Betriebsordner im Client öffnen](#6-betriebsordner-im-client-öffnen).
`init` erzeugt beide vollständigen Client-Dateien inklusive Betriebsregeln.
Keine vereinfachten Einzel-MCP-Konfigurationen darüberkopieren oder dieselben
Server zusätzlich global registrieren. Zur Diagnose dient
`./bin/datenportal-agent harness-config`; benötigte Mounts dabei mitgeben.
Persönliche Einstellungen und Zugänge bleiben erhalten; fremde MCPs werden
im Betriebsprofil deaktiviert. Konflikte werden vor dem Schreiben gemeldet.
Sicherungen liegen unter `.datenportal-integrator/harness-backups/`.

Die tatsächlichen Desktop-/CLI-Prüfungen und offenen Fachdialog-Nachweise stehen
in [Abnahme](abnahme.md). Sie sind von der blossen Konfigurationsbereitschaft
zu unterscheiden.

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
./bin/datenportal-agent call start --args-file start.json
./bin/datenportal-agent call baseline --json '{"run_id":"VORGANGS_ID"}'
./bin/datenportal-agent call analyze --json '{"run_id":"VORGANGS_ID"}'
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
./bin/datenportal-agent call approve --args-file csv-ok.json
```

XLSX als `metadata_source` aufnehmen; `xlsx` ohne `sheet` zeigt die Blätter, mit Blattname paginierte Zellen und Koordinaten. Formeln werden nicht ausgeführt. Ein fehlender gespeicherter Formelwert ist keine Null.

```sh
./bin/datenportal-agent call attach --json '{"run_id":"VORGANGS_ID","path":"/eingang/metadaten.xlsx","role":"metadata_source"}'
./bin/datenportal-agent call xlsx --json '{"run_id":"VORGANGS_ID"}'
./bin/datenportal-agent call xlsx --json '{"run_id":"VORGANGS_ID","sheet":"Metadaten","offset":0,"limit":100}'
```

`metadata` delegiert an den bestehenden Datenblatt-MCP. Zuerst `describe_schema`, dann `import_xtf` oder `create_datasheet` mit `kind: dataset|series`. Attribute, Ausgaben und Pflichtwerte über die öffentlichen Fachwerkzeuge bearbeiten. Kontakte und Fachsemantik nicht erfinden. `provenance` speichert je Feld `field`, `origin` (`user`, `xlsx`, `accepted`, `llm`) und `source`; LLM-Vorschläge bleiben erkennbar.

```sh
./bin/datenportal-agent call metadata --json '{"run_id":"VORGANGS_ID","operation":"describe_schema"}'
./bin/datenportal-agent call metadata --json '{"run_id":"VORGANGS_ID","operation":"import_xtf"}'
./bin/datenportal-agent call metadata --json '{"run_id":"VORGANGS_ID","operation":"update_metadata","arguments":{"values":{"title":"Fachlich bestätigter Titel"}}}'
./bin/datenportal-agent call metadata --json '{"run_id":"VORGANGS_ID","operation":"export_xtf"}'
./bin/datenportal-agent call validate --json '{"run_id":"VORGANGS_ID"}'
```

`validate` prüft genau die Liefer-XTF mit ilivalidator, validiert `offices.xtf` separat und verwendet es als Referenzbestand für den EXISTENCE-Constraint gegen `Office.identifier`. CSV-Spalten, Reihenfolge, Typen und Pflichtwerte müssen mit dem Datenblatt zusammenpassen. Die lokale HTML-Datei zeigt Metadaten, Serienausgaben, Prüfmeldungen, Herkunft und Dateivergleiche.

Notwendige fachliche Repository-Dateien vor der Freigabe mit `stage_change` registrieren. Beispielargumente: `relative_path: statistikdienst/ch.so.bevoelkerung.altersstruktur/datasheet.xtf`, `source_path: /…/export.xtf`. Liefer-XTF und geplante Repository-XTF müssen dieselben Bytes haben.

**Zweiter menschlicher Stopp:** Datenblatt, Dienststellen- und Organisationsänderungen prüfen. Korrekturauftrag bedeutet weiter bearbeiten, exportieren, validieren und erneut zeigen. Das OK mit `gate: metadata` und der Prüfsumme aus `validate` protokollieren. Bei zusätzlichem Modell werden Datenblatt und Modell gemeinsam gezeigt und beide konkreten Gates bestätigt.

```sh
./bin/datenportal-agent call apply_local_changes --json '{"run_id":"VORGANGS_ID"}'
./bin/datenportal-agent call deliver --json '{"run_id":"VORGANGS_ID","environment":"local"}'
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
    "rule_profile": "SO-DATA"
  }
}
```

```sh
./bin/datenportal-agent call derive_model --args-file derive-model.json
./bin/datenportal-agent call validate_model --json '{"run_id":"VORGANGS_ID"}'
```

Standard für neue CSV-Validierungsmodelle: INTERLIS 2.4, Profil `SO-DATA`, Zweck `VALIDATION`. Fehlende Identität oder notwendige Fachsemantik erscheinen als `NEEDS_INPUT`. Das Modell enthält keine Kommentare; technische Kontakte, Titel und Beschreibungen sind keine zusätzlichen Modellpflichtangaben. Spaltennamen, Typen, Pflichtigkeit, Beschreibungen und Einheiten stammen aus dem Datenblatt. Führende Nullen benötigen eine bewusste Typentscheidung. Beobachtete Werte begründen weder geschlossene Codelisten noch Schlüssel oder Fachbereiche. Zusätzliche Domains, Einheiten und Constraints nur mit `identity.semantics_confirmed: true` und fachlicher Grundlage beauftragen. Bei TEXT die maximale Länge bestätigen, bei INTEGER/DECIMAL/NUMERIC einen konkreten Wertebereich und die Genauigkeit über bestätigte Spezifikationen oder begründete Agentenvorschläge vorlegen. Der bestehende Adapter akzeptiert DATETIME mit +01:00 nicht als INTERLIS.XMLDateTime; diese Grenze wird erklärt und blockiert eine ungeprüfte Übernahme. Spaltenbezogene bestätigte Typen können unter `confirmations.<spalte>.type_spec` angegeben werden; `confirmed: true` hält die Entscheidung fest.

Vorhandene Modelle werden mit einem typisierten `changes`-Batch über `applyIliModelChanges` bearbeitet. `allow_breaking` ist eine ausdrückliche Entscheidung über potenziell brechende Änderungen. Der Integrator übernimmt Compiler-, Review- und Constraint-Nachweise des Fach-MCP. Unvollständige Nachweise bleiben Kandidaten. Manuelle Reviewpunkte erscheinen im HTML.

Nach erfolgreicher Ableitung werden `.ili`, `dataset.gradle` und die Datenblatt-Modellreferenz zusammen vorbereitet. Die Referenz wird über den Datenblatt-MCP vor dem abschliessenden Export gesetzt. `validate_model` führt GRETL `CsvValidator` in einer isolierten Repository-Kopie aus. Derselbe Themen-Task läuft später vor `preparePublicationWorkspace`; Metadatenlieferungen überspringen ihn ausdrücklich. Es gibt dafür keine eigene CSV-zu-XTF-Konvertierung.

Beim eigenständigen Modellvorgang: Modellvorschau prüfen, `gate: model` bestätigen und lokal übernehmen oder über einen freigegebenen Zielplan als PR vorbereiten. Kein automatischer Datenupload. Beim Themenvorgang werden `model` und `metadata` für die gemeinsam gezeigten Prüfstände bestätigt.

### Wertebereiche, Reparaturen und Loglinks

Die verfügbaren Profile sind `CORE`, `SO-GIS` und `SO-DATA`. `SO-GIS` ist der
neue Name des bisherigen Profils `SO` mit unveränderten GIS-Regeln.
GIS-Aufträge verwenden `SO-GIS`; `SO` ist kein zulässiger Profilwert.
Ein gespeicherter Vorgang mit `SO` wird beim Lesen nicht umgeschrieben.

Für `SO-DATA` müssen Modellname, Datum und Themen-Dateiname zusammenpassen:
`SO_Messwerte_20261010`, `VERSION "2026-10-10"` und `SO_Messwerte_20261010.ili`.
Die AT-URI ist absolut. Datenblattbeschreibungen und Einheiten bleiben erhalten.
Bestehende Vorgänge behalten ihr Profil; eine ausdrücklich beauftragte Umstellung
mit `identity.rule_profile: "SO-DATA"` und gegebenenfalls `REMOVE_COMMENTS`
erzeugt einen neu zu prüfenden Kandidaten.

Der Agent schlägt numerische Grenzen anhand der vollständigen CSV-Statistik und
der fachlichen Beschreibung vor. Beispiel für eine moderne Jahresmessreihe:

```json
{
  "range_proposals": {
    "Jahr": {
      "type_spec": {"baseType": {"kind": "NUM_RANGE", "min": 1900, "max": 2100}},
      "reason": "Beobachtungsjahr der modernen Messreihe; Reserve für künftige Lieferungen bis 2100.",
      "negative_values_possible": false
    }
  }
}
```

Dieses Beispiel ist keine allgemeine Jahresregel. Positive Messwerte allein
belegen etwa bei Temperaturen keine Nichtnegativität. Ohne fachliche Grundlage
fragt der Agent nach. Im Modellreview stehen beobachtete Grenzen, Vorschlag,
Genauigkeit und Begründung. Vorschläge sind als Agentenschätzung gekennzeichnet;
die übliche Modellfreigabe bestätigt den gesamten Kandidaten.

Bei Fehlern erhältst du die Diagnose **im Chat und im HTML** mit anklickbarem
Logpfad. Beispiel mit fiktivem Pfad: „CSV-Wert liegt ausserhalb des Bereichs.
[Validator-Log][fiktiver-validator-log].
Korrekturversuch 1 von 3: Bereich anhand der Beschreibung erweitern.“ Vor jeder
Korrektur erklärt der Agent die Änderung. Nach der ersten Fehlprüfung sind
höchstens drei Reparaturversuche erlaubt, gemeinsam für Compiler und CSV-Prüfung.
Frühere Logs bleiben auch nach Erfolg und Wiederaufnahme erreichbar. Bei
`repair_limit_reached` braucht es einen ausdrücklichen menschlichen Neuauftrag;
`reset_model_repair` speichert dessen tatsächliche Aussage. Infrastrukturfehler
und ungeklärte Fachfragen verlangen Klärung. Die Originaldaten bleiben unverändert.

Für einen Reparaturaufruf übernimmt der Agent `model_repair.last_failure` aus
`status` als `repair_of` und ergänzt `repair_reason` in den vollständigen
`derive_model`-Auftrag. Diese Referenz ist eine Prüfkennung, kein Logpfad.
Beispiel für die zusätzlichen Felder:

```json
{"repair_of": "PRUEFKENNUNG_AUS_STATUS", "repair_reason": "Unbekannte Domain durch den fachlich bestätigten Zahlentyp ersetzen."}
```

Nach ausgeschöpfter Serie kann ausschliesslich ein tatsächlicher neuer Auftrag
mit `reset_model_repair` und den Feldern `run_id` und `human_statement` erfasst
werden. Der bisherige Verlauf bleibt erhalten; der Reset ist keine Modellfreigabe.

### Unterstützte Profile

Zulässig sind `CORE`, `SO-GIS` und `SO-DATA`. `SO-DATA` ist der Standard für
neue CSV-Validierungsmodelle. Der frühere Wert `SO` wird mit `model_profile`
abgewiesen; vorhandene Kandidaten werden nicht automatisch umgeschrieben.
Eine ausdrücklich beauftragte Profiländerung erzeugt einen neuen Kandidaten
mit erneuter Prüfung und Freigabe. Den geprüften Image-Stand nennt die
[Abnahme](abnahme.md); vorhandene Installationen wechseln nur durch ein
[ausdrückliches Update](#fachwerkzeuge-aktualisieren).

## Neue Organisation und Dienststelle

```sh
./bin/datenportal-agent call organization_schema --json '{}'
./bin/datenportal-agent call start --json '{"workflow":"organization","organization":"neueorganisation"}'
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
./bin/datenportal-agent call prepare_organization --args-file organization.json
./bin/datenportal-agent call validate_organization --json '{"run_id":"VORGANGS_ID"}'
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

## Wiederaufnahme und Fehler

Vorgangs-ID aufbewahren. `status` liest Artefakte, Entwürfe, Freigaben und externe Kennungen. Neue Eingaben nicht durch einen zweiten Vorgang am bestehenden Upload vorbei liefern.

```sh
./bin/datenportal-agent call status --json '{"run_id":"VORGANGS_ID"}'
```

Unterstützt sind aktuelle Schema-2-Vorgänge. Ältere Schemata und noch nicht
umgestellte Konverterstände werden mit `unsupported_state_version` abgewiesen,
ohne die gespeicherten Dateien zu ändern. Es gibt keinen Migrationsbefehl.
Unklare alte Uploads anhand ihrer vorhandenen externen Kennungen manuell klären;
ein neuer Vorgang oder Softwarewechsel erlaubt keinen erneuten Upload.

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
| `verification_snapshot_missing` | Gespeicherter Prüfsnapshot fehlt. Bestehende Lieferung manuell klären; kein Nachladen aus alten Berichten und kein neuer Upload. |
| `repository_conflict`, `pr_base_conflict`, `pr_changed` | Änderungen vergleichen und erneut prüfen; keine fremden Änderungen überschreiben. |
| `human_merge_required` | Menschlichen Merge abwarten; Agent mergt nicht. |
| `harness_conflict` bei `init` | Gemeldeten Konfigurationskonflikt klären, betroffene Einträge bewusst anhand der aktuellen Konfiguration einrichten und `init` wiederholen. Keine fachliche Freigabe erforderlich. |
| `unsupported_state_version` | Nicht unterstützten Altstand erhalten und bestehende externe Läufe klären; keine automatische Übernahme. |

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

Ohne `mode` oder mit `mode = "ephemeral"` startet ein neuer Container pro Prüfung mit `--no-daemon`; anschliessend wird er entfernt. Es gibt keinen automatischen Wechsel zwischen den Betriebswegen bei einem Fehler.

`setup-gretl` behält die ausgewählte Image-Version bei. Nach einer bewusst geänderten Image-Referenz oder mit `setup-gretl --update` wird ein neuer Stand aufgenommen; anschliessend betroffene Prüfungen und Freigaben erneuern. Vor dem lokalen Gesamttest vergleicht der Integrator Image und Bundle mit dem tatsächlich laufenden Jenkins. Bei `gretl_runtime_mismatch` anhalten, die Konfiguration abstimmen und erneut vorbereiten/prüfen; der Integrator ändert den Stack nicht automatisch.

GRETL verwendet ausschliesslich die Laufzeit im ausgewählten Jenkins-Image. Alte Konfigurationsfelder und alte Runtime-Nachweise werden abgewiesen; siehe [Konfiguration umstellen](#vorhandene-lokale-konfiguration-umstellen).

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

[fiktiver-validator-log]: /absoluter/vorgang/diagnostics/pruefkennung/artifact-csv.log
