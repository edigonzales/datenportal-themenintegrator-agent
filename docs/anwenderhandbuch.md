# Anwenderhandbuch

Dieses Handbuch richtet sich an Themenintegratoren. Der Agent erklärt Daten,
entwirft Metadaten und führt den technischen Ablauf aus. Du prüfst die
fachlichen Zwischenresultate und gibst die jeweilige Fassung frei.
Die technische Referenz steht im [Entwicklerhandbuch](entwicklerhandbuch.md).

## Einstieg

Für einen Vorgang brauchst du:

- lokale Originaldateien: CSV, optional ein vorhandenes Datenblatt als XTF/XML
  und ergänzende Metadaten als XLSX;
- die Jenkins-Organisation und einen stabilen Themenidentifier;
- bei einer CSV-Serienlieferung zusätzlich die Ausgabenbezeichnung, z.B. `2025`;
- einen eingerichteten Integrator, Java-Datenblatt-MCP und Zugang zum lokalen
  Jenkins für den Gesamttest.

Ein Dateipfad genügt. Bei Desktop-Anhängen muss das Harness die Originaldatei
lokal bereitstellen; unterstützte Anhangtypen hängen vom Harness ab.
Eine Chat-Vorschau ersetzt die Originaldatei nicht. Unklare Zuordnungen,
Kontakte oder fachliche Metadatenaussagen fragt der Agent nach.

## Einmalige Einrichtung

### Werkzeuge und Checkouts

Erforderlich sind Python 3.11 oder neuer, `uv`, Git, Docker mit Compose und
Java für ilivalidator. Der bestehende Datenblatt-MCP benötigt JDK 25. GRETL
verwendet die vorhandene Java-17-Laufzeit im Dev-Stack.
Für GitHub-PRs zusätzlich `gh` installieren und anmelden:

```sh
command -v uv
command -v git
command -v docker
java -version
docker compose version
command -v gh
gh --version
gh auth login
gh auth status
```

Standardmässig liegen diese Repositories nebeneinander:

```text
sources/
  datenportal-themenintegrator-agent/
  datenportal-themenrepo/
  datenportal-dev-stack/
  datenportal-datenblatt-editor/
```

Im Integrator-Repo ausführen:

```sh
uv sync --locked
cp -n config/local.example.toml config/local.toml
uv run python scripts/setup-tools.py
```

`cp -n` lässt eine vorhandene lokale Konfiguration stehen. Das Setup-Skript
lädt ilivalidator 1.15.0, prüft seine SHA-256-Prüfsumme und legt ihn unter
`.datenportal-integrator/tools/ilivalidator/` ab. Es benötigt Netzwerkzugriff.

### Ablage der Konfiguration

| Ort | Zweck |
| --- | --- |
| `config/local.toml` im Integrator | Ignorierte lokale Checkout-Pfade, Java-MCP-Adresse, Werkzeugpfade und Umgebungsprofile; Credential-Variablennamen |
| `config/local.example.toml` | Versionierte Vorlage ohne Zugangsdaten |
| `config/settings.schema.json` | Referenz für zulässige Konfigurationsfelder |
| `~/.codex/config.toml` | Benutzerlokaler MCP-Start für Codex CLI/Desktop |
| `~/.config/opencode/opencode.json` | Benutzerlokale OpenCode-Einstellungen |
| `.datenportal-integrator/runs/` | Ignorierte Originalkopien, Exporte, Vorschauen, Freigaben und Laufkennungen |
| `topics/<organisation>/<identifier>/` | Optionale Konverter, Tests und gespeicherte Lieferstrategie im Integrator |

Relative Checkout- und Laufzeitpfade beziehen sich auf `root`. Ein relatives
`root` bezieht sich auf den Ort der TOML-Datei. Die Beispielkonfiguration
verwendet `root = ".."`, also den Integrator-Repo-Ordner.
`--config` wählt eine andere Datei; alternativ ist
`DATENPORTAL_INTEGRATOR_CONFIG` möglich. Ohne beide wird
`config/local.toml` relativ zum Arbeitsverzeichnis gelesen.

Die [vollständige Vorlage](../config/local.example.toml) enthält unter anderem
diese lokalen Zuordnungen; die übrigen Einstellungen der Vorlage beibehalten:

```toml
root = ".."
topics_repo = "../datenportal-themenrepo"
stack_repo = "../datenportal-dev-stack"
datasheet_mcp_url = "http://127.0.0.1:8000/mcp"

[environments.local]
kind = "local"
enabled = true
jenkins_url = "http://localhost:8081/jenkins"
portal_url = "http://localhost:8081"
manifest_url = "http://localhost:8081/ch.so.daten/current.json"
repository_mode = "working-tree"
username_env = "DATENPORTAL_LOCAL_USER"
token_env = "DATENPORTAL_LOCAL_TOKEN"
git_write_back = false
```

Im Themenrepo sind keine Harness-Einstellungen nötig.

### PATH

`uv`, `gh`, Git, Java und Docker müssen für den Prozess erreichbar sein, der
den Integrator startet. Eine interaktive Shell kann einen anderen PATH als
Codex oder eine Desktop-App haben. Deshalb auch prüfen:

```sh
zsh -lc 'command -v uv && command -v gh'
```

Ist `gh` beispielsweise unter `$HOME/apps/gh/bin/gh` installiert und
`$HOME/.local/bin` bereits im PATH, lässt sich dort ein Link anlegen. Vorher
prüfen, ob am Ziel bereits eine Datei existiert:

```sh
ls -ld "$HOME/.local/bin"
ls -l "$HOME/.local/bin/gh"
```

Falls das Ziel fehlt:

```sh
ln -s "$HOME/apps/gh/bin/gh" "$HOME/.local/bin/gh"
zsh -lc 'command -v gh && gh --version'
```

Die Pfade sind Installationsbeispiele. In einer Desktop-MCP-Konfiguration
kann für `uv` ein absoluter Pfad nötig sein. `gh` muss zusätzlich im PATH des
MCP-Prozesses liegen, weil der Integrator es als Unterprozess aufruft.

### Zugangsdaten

Das Integrator-Profil enthält **Namen**, etwa `username_env` und `token_env`.
Die Werte kommen aus der Umgebung des CLI- oder MCP-Prozesses.
Eine `.env`-Datei wird vom Integrator **nicht automatisch geladen**.
Die `.env` des Dev-Stacks ist eine andere Konfiguration und wird nicht als
Credential-Datei des Integrators übernommen.

Der dokumentierte lokale Testzugang des Dev-Stacks ist Benutzer `admin` mit
Passwort `admin`. Das Feld `DATENPORTAL_LOCAL_TOKEN` kann für diesen lokalen
Test-Realm auch das Passwort enthalten. Für reguläre Jenkins-Zugänge ist ein
API-Token vorgesehen. GitHub-Anmeldung über `gh` und Jenkins-Anmeldung sind
voneinander unabhängig.

Für einen Terminalprozess in Zsh ohne Zugangsdaten in der Shell-History:

```sh
read -r 'DATENPORTAL_LOCAL_USER?Jenkins-Benutzer: '
read -rs 'DATENPORTAL_LOCAL_TOKEN?Jenkins-Token oder lokales Passwort: '
printf '\n'
export DATENPORTAL_LOCAL_USER DATENPORTAL_LOCAL_TOKEN
uv run datenportal-integrator doctor
```

Diese Werte gelten für dieses Terminal und die von dort gestarteten Prozesse.
Eine bereits laufende Desktop-App übernimmt sie dadurch nicht.
Für Desktop wird die benutzerlokale MCP-Konfiguration verwendet, siehe unten.
Die tatsächlichen Werte gehören in keine versionierte Repo-Datei, keinen
Bericht und keinen Chat. Platzhalter in den folgenden Beispielen lokal ersetzen.

### Java-Datenblatt-MCP und lokaler Stack

Der Integrator-MCP ist ein Python-Prozess über stdio. Er ruft separat den
bestehenden Java-MCP über HTTP auf. Beide müssen verfügbar sein.

Wenn kein passender Java-MCP läuft, in einem separaten Terminal im Integrator
starten und das Terminal geöffnet lassen:

```sh
uv run python scripts/start-datasheet.py --java-home /pfad/zu/jdk-25
```

Das Skript kopiert und baut die unveränderten Java-Quellen unter
`.datenportal-integrator/tools/datasheet-source/`. Falls das JAR bereits gebaut
ist, kann es direkt gestartet werden:

```sh
/pfad/zu/jdk-25/bin/java -jar \
  .datenportal-integrator/tools/datasheet-source/build/libs/datasheet-mcp.jar
```

Die Standardkonfiguration erwartet `http://127.0.0.1:8000/mcp`. Eine vorhandene
Instanz kann über `datasheet_mcp_url` verwendet werden. Den Server nicht ein
zweites Mal auf demselben Port starten. `doctor` prüft den Health-Endpunkt.

Der lokale Gesamttest erkennt den Stack, startet ihn bei Bedarf über dessen
vorhandenes Skript und initialisiert einen leeren Publikationsbestand erst
nach den fachlichen Freigaben. Das frühere Lesen des Ausgangsbestands mit
`baseline` benötigt bereits einen erreichbaren Manifest-Endpunkt. Falls der
Stack noch gar nicht läuft, kann er vor dem Fachablauf gestartet werden:

```sh
cd ../datenportal-dev-stack
bash scripts/up.sh
cd ../datenportal-themenintegrator-agent
```

Zusätzliche vorhandene Stack-Startoptionen müssen zur lokalen Konfiguration
passen. Bei einem anderen Themencheckout hält der Integrator an.
HTTP 404 für das Manifest bedeutet fehlenden Erstbestand; ein beschädigtes
oder nicht lesbares Manifest wird nicht ersetzt.

### Codex Desktop

Den Integrator-Repo-Ordner in Codex öffnen. Der Skill liegt unter
`skills/datenportal-themenintegrator/SKILL.md`; `.agents/skills/` verweist darauf,
und `AGENTS.md` nennt den Ablauf.

Für die benutzerlokale MCP-Konfiguration im Integrator ausführen:

```sh
uv run python scripts/harness-config.py
```

Die Ausgabe in `~/.codex/config.toml` einfügen bzw. mit einem vorhandenen
Eintrag `[mcp_servers.datenportal_integrator]` zusammenführen. Keine zweite
gleichnamige TOML-Tabelle anlegen. Das Skript gibt absolute Pfade aus und
ändert die persönliche Datei nicht selbst.
Unter diesem MCP-Eintrag können benutzerlokal die Werte ergänzt werden:

```toml
[mcp_servers.datenportal_integrator.env]
DATENPORTAL_LOCAL_USER = "<Jenkins-Benutzer>"
DATENPORTAL_LOCAL_TOKEN = "<Jenkins-Token oder lokales Passwort>"
```

Falls `uv` aus der App nicht gefunden wird, `command = "uv"` im ausgegebenen
Haupteintrag durch den mit `command -v uv` ermittelten absoluten Pfad ersetzen.
Nach Änderung Codex vollständig neu starten und eine Sitzung im Integrator
öffnen. Den Agenten bitten, die Verfügbarkeit der Werkzeuge `start`, `analyze`,
`metadata` und `deliver` zu prüfen.

Die versionierte `.codex/config.toml` ist ein Projekteinstieg. Ihre automatische
Discovery hängt von Version und Projektvertrauen ab; in der getesteten
Codex-CLI 0.160.0 war der explizite Launcher erforderlich. Die benutzerlokale
Einrichtung entspricht der [offiziellen MCP-Dokumentation](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

Die Werte unter MCP-`env` gelten für diesen MCP-Prozess. Ein unabhängig
gestartetes `uv run datenportal-integrator doctor` liest sie nicht aus der
Codex-Konfiguration. Für die direkte CLI sind weiterhin Terminalvariablen nötig.
Die tatsächliche Jenkins-Anmeldung wird erst beim Zugriff geprüft; `doctor`
zeigt nur, ob die Credential-Variablen befüllt sind.

### Codex CLI

Nach Einrichtung der Terminalvariablen im Integrator-Repo:

```sh
uv run python scripts/codex-integrator.py mcp list --json
uv run python scripts/codex-integrator.py
```

Der Launcher übergibt den stdio-MCP explizit mit absoluten Integrator-Pfaden und
leitet die vorgesehenen Credential-Variablen weiter. Er schreibt keine
persönliche Codex-Konfiguration. `mcp list` bestätigt den Konfigurationseintrag;
die Werkzeugverfügbarkeit in der laufenden Sitzung zusätzlich prüfen.

Falls der MCP-Einstieg nicht verfügbar ist, kann der Agent dieselben Operationen
über die CLI nutzen:

```sh
uv run datenportal-integrator schema start
uv run datenportal-integrator call status --json '{"run_id":"<ID>"}'
```

`<ID>` ist durch die zurückgegebene Vorgangs-ID zu ersetzen. Für eine echte
Integration den Ausführungsmodus des Harness verwenden.

### OpenCode

Im Integrator-Repo liegt `opencode.json` mit dem lokalen stdio-MCP. Die
Terminalvariablen wie oben setzen, dann:

```sh
opencode mcp list
opencode
```

Die Verbindung `datenportal_integrator` sollte als verbunden erscheinen.
OpenCode unterstützt die gemeinsame `.agents/skills/`-Ablage;
im Auftrag den Skill `datenportal-themenintegrator` ausdrücklich nennen.
Referenzen: [MCP-Konfiguration](https://opencode.ai/docs/mcp-servers/),
[Skill-Ablage](https://opencode.ai/docs/skills/).

Für eine Desktop-Instanz oder Verwendung ausserhalb des Integrator-Repos den
absoluten MCP-Block erzeugen:

```sh
uv run python scripts/harness-config.py opencode
```

Diesen Block mit `~/.config/opencode/opencode.json` zusammenführen. Im
benutzerlokalen Eintrag kann das folgende Objekt ergänzt werden; bei Desktop
ohne Terminalvariablen die Platzhalter lokal durch die Werte ersetzen:

```json
{
  "environment": {
    "DATENPORTAL_LOCAL_USER": "<Jenkins-Benutzer>",
    "DATENPORTAL_LOCAL_TOKEN": "<Jenkins-Token oder lokales Passwort>"
  }
}
```

Das Objekt gehört innerhalb von `mcp.datenportal_integrator`, nicht an die
JSON-Wurzel. Die [OpenCode-Konfigurationen](https://opencode.ai/docs/config/)
werden zusammengeführt; Projektfelder können benutzerlokale Felder übersteuern.
`command` bei Bedarf mit absolutem `uv`-Pfad beginnen. Danach OpenCode neu
starten und Werkzeugzugriff im gewählten Projekt prüfen. Die Desktop-Abnahme
ist noch offen; siehe [Abnahmestand](entwicklerhandbuch.md#abnahmestand).

## Vollständiger Fachablauf

### Dateien aufnehmen und zuordnen

Als durchgängiges Beispiel dient die vorhandene Pilotserie
`ch.so.bevoelkerung.altersstruktur` der Jenkins-Organisation `statistikdienst`,
Ausgabe `2025`. Die fachliche Dienststelle ist eine eigene Zuordnung und muss
mit dem Datenblatt übereinstimmen. Im optionalen Pilotdaten-Checkout liegen
CSV und XTF unter `../datenportal-pilot-daten/ch.so.bevoelkerung.altersstruktur/`.
Eine ergänzende XLSX muss aus der tatsächlichen Anlieferung kommen.

Beispielauftrag im Chat, mit deinen Originaldateien oder deren Pfaden:

> Verwende den Themenintegrator-Skill. Integriere die CSV für `statistikdienst`,
> Serie `ch.so.bevoelkerung.altersstruktur`, Ausgabe `2025`, zuerst lokal.
> Vergleiche die mitgelieferte XTF mit dem angenommenen Bestand. Nutze die
> ergänzende XLSX als Metadatenquelle und kennzeichne eigene Vorschläge.
> Erkläre mir zuerst die CSV und halte zur Freigabe an.

Der Agent führt `start` aus und nennt die Vorgangs-ID. Er nimmt die XLSX mit
`attach(role="metadata_source")` auf und lädt mit `baseline` den angenommenen
Metadatenstand aus der Quellumgebung. Quelle und vorgesehene Änderung werden
fachlich verglichen. Bei neuen Themen kann ein eindeutiges Repository-Datenblatt
als Ausgangspunkt dienen.

### Stopp 1: CSV prüfen

`analyze` liest alle Beobachtungen. Du erhältst Spaltenreihenfolge, Beispiele,
fehlende Werte, vermutete Typen, technische Fehler und fachliche Hinweise.
`review_path` verweist auf `data-review.html` im lokalen Vorgang.

Die Prüfregeln orientieren sich an den
[Datenformatvorgaben](https://sogis.github.io/datenportal-dokumentation/datenpublikation/main/#datenformat):
UTF-8, Semikolon, eindeutige Spaltennamen, konsistente Zeilenbreite und
unterstützte Datentypen. Bei führenden Nullen wird TEXT vorgeschlagen; der
fachliche Typ muss bestätigt werden. Empfehlungen wie der Hinweis auf eine
Totalspalte benötigen deine Beurteilung.
Die implementierten Regeln stehen im
[Entwicklerhandbuch](entwicklerhandbuch.md#csv-und-xtf).

Du kannst beispielsweise antworten:

> Die Struktur und die fachliche Erklärung passen. Ich gebe diese CSV-Fassung frei.

Erst dann protokolliert der Agent `approve` mit Gate `data` und dem aktuellen
Prüfstand. Bei einer Korrektur wie „Totalspalte entfernen und Kennung als TEXT
behalten“ bereitet er einen getesteten Konverter und eine neue Vorschau vor.
Danach folgt erneut deine Prüfung.

### Metadaten bearbeiten

Der Agent verwendet `metadata` als Adapter zum bestehenden Java-MCP. Eine
vorhandene XTF wird importiert; neue Datenblätter werden dort als Dataset oder
Serie aufgebaut. Feldregeln kommen aus `describe_schema`. XLSX-Zellen werden
mit Blattname und Zelladresse gelesen. Formelresultate müssen im Workbook
gespeichert sein; der Integrator berechnet Formeln nicht neu.

`provenance` kennzeichnet die Herkunft: Nutzerangabe, XLSX, angenommener
Bestand oder LLM-Vorschlag. Themen, Kontakte, Einheiten und Beschreibungen
werden nicht als gesicherte Fakten ausgegeben, wenn dafür eine Quelle fehlt.
Attribute müssen zu Namen, Reihenfolge, Typen und Pflichtwerten der CSV passen.

Korrekturen werden im Java-Entwurf ausgeführt. Anschliessend exportiert der
Agent die aktuelle XTF. Notwendige Themenrepo-Änderungen werden mit
`stage_change` vorbereitet: Datenblatt, Office-Katalog und gegebenenfalls die
vorgesehenen Organisationsdateien. Liefer-XTF und Repository-XTF müssen
denselben Inhalt haben.

### Stopp 2: Datenblatt und fachliche Repo-Änderungen

`validate` prüft die konkrete XTF und `offices.xtf` mit ilivalidator, die
Office-Existenz und den CSV-/Metadatenvertrag. `metadata-review.html` zeigt
das vollständige Datenblatt, Attribute, Serienausgaben, Quellen, Hinweise und
vorgesehene Repository-Änderungen.

Prüfe Titel, Beschreibung, Datenherr, Kontakt, Zeitbezug, Nachführung,
Ausgaben, Attribute und Annahmen. Ein möglicher Korrekturauftrag:

> Ändere die Beschreibung gemäss Blatt „Metadaten“, Zelle B4, und ergänze die
> Einheit der Spalte „Wert“. Zeige mir danach das Datenblatt erneut.

Nach Export und neuer Validierung kannst du die konkrete Fassung freigeben.
Der Agent protokolliert Gate `metadata`. Technische Fehler blockieren die
Freigabe. Geänderte Dateien oder relevante Konfiguration entwerten die
betroffenen Freigaben; die neue Vorschau ist verbindlich.

### Lokaler Gesamttest

Nach Freigabe übernimmt `apply_local_changes` die vorbereiteten fachlichen
Änderungen. `deliver(environment="local")` nutzt die passende laufende
Stack-Instanz oder startet sie. Ein leerer Bestand wird nach dem vorhandenen
administrativen Verfahren initialisiert; ein vorhandenes Manifest bleibt
erhalten. Danach folgen Jenkins-Seed, Anlieferung und Ergebnisprüfung.

Weitere `deliver`-Aufrufe verfolgen denselben gespeicherten Lauf. Du erhältst
schliesslich den Portal-Link und das Ergebnis für Publikation, angenommenen
Fachinhalt, Portal-Katalog und Downloads. Eine Freigabe belegt noch keine
erfolgreiche externe Publikation; massgeblich sind Laufbericht und Nachprüfung.

### Stopp 3: Lokal beenden oder INT/PROD wählen

Nach erfolgreichem lokalem Test kannst du lokal beenden oder eine konkrete
Zielumgebung wählen. `publication_plan` zeigt URLs, Branch, Dateistände,
Ausgabe und fachliche Änderungen. Deine ausdrückliche Zustimmung wird als
`publish:<umgebung>` gespeichert.

Sind Repo-Änderungen nötig, erstellt `prepare_pr` einen GitHub-PR.
Du oder ein anderer Mensch übernimmt ihn. Nach deiner Fortsetzung kontrolliert
der Agent den Merge und die übernommenen Bytes, seedet und liefert an die
freigegebene Umgebung. Datenlieferungen ohne Repo-Änderung brauchen keinen PR.

INT-/PROD-Profile sind standardmässig nicht eingerichtet. Reale Adressen,
Credential-Variablen, Branch und ein bestehender kompatibler `managed-git`-Betrieb
mit deaktiviertem Git-Rückschreiben sind Voraussetzung. Der Integrator ändert
diese Betreiberkonfiguration nicht.

## Direkte CLI: derselbe Ablauf

Alle Beispiele im Integrator-Repo ausführen. Platzhalter `<ID>`, `<FP_DATA>`,
`<FP_METADATA>` und Pfade durch die tatsächlichen Ausgaben ersetzen. Die
`approve`-Beispiele erst nach deiner Prüfung ausführen; sie sind kein Skript
für automatische Freigaben.

Speichere z.B. `.datenportal-integrator/eingang.json` mit deinen Dateipfaden:

```json
{
  "organization": "statistikdienst",
  "identifier": "ch.so.bevoelkerung.altersstruktur",
  "issue": "2025",
  "source_environment": "local",
  "data_path": "/absoluter/pfad/so_bevo_altersstruktur_2025.csv",
  "metadata_path": "/absoluter/pfad/meta-ch.so.bevoelkerung.altersstruktur.xtf"
}
```

Aufnehmen, Quellen lesen und Daten prüfen:

```sh
uv run datenportal-integrator call start --args-file .datenportal-integrator/eingang.json
uv run datenportal-integrator call attach --json '{"run_id":"<ID>","path":"/absoluter/pfad/metadaten.xlsx","role":"metadata_source"}'
uv run datenportal-integrator call baseline --json '{"run_id":"<ID>"}'
uv run datenportal-integrator call xlsx --json '{"run_id":"<ID>"}'
uv run datenportal-integrator call xlsx --json '{"run_id":"<ID>","sheet":"Metadaten","offset":0,"limit":100}'
uv run datenportal-integrator call analyze --json '{"run_id":"<ID>"}'
```

Wenn keine XLSX vorliegt, `attach`/`xlsx` weglassen. Nach CSV-Prüfung den
Fingerprint aus `analyze` verwenden:

```sh
uv run datenportal-integrator call approve --json '{"run_id":"<ID>","gate":"data","fingerprint":"<FP_DATA>","human_statement":"Ich gebe die angezeigte CSV-Fassung frei."}'
uv run datenportal-integrator call metadata --json '{"run_id":"<ID>","operation":"describe_schema"}'
uv run datenportal-integrator call metadata --json '{"run_id":"<ID>","operation":"import_xtf"}'
```

Metadaten nach fachlicher Entscheidung ändern und Herkunft protokollieren.
`provenance` ersetzt die komplette Quellenliste; weitere bestehende Einträge
beibehalten. Das Beispiel setzt eine Beschreibung aus B4 voraus:

```sh
uv run datenportal-integrator call metadata --json '{"run_id":"<ID>","operation":"update_metadata","arguments":{"values":{"description":"<gepruefter Inhalt aus B4>"}}}'
uv run datenportal-integrator call provenance --json '{"run_id":"<ID>","entries":[{"field":"description","origin":"xlsx","source":"metadaten.xlsx / Metadaten!B4; fachlich geprueft"}]}'
uv run datenportal-integrator call metadata --json '{"run_id":"<ID>","operation":"export_xtf"}'
```

Bei einer Repo-Metadatenänderung die zurückgegebene `artifact.path` vorbereiten.
Für ein bestehendes Thema dessen bestehenden Dateinamen verwenden; der folgende
Name entspricht dem Beispielthema:

```sh
uv run datenportal-integrator call stage_change --json '{"run_id":"<ID>","relative_path":"statistikdienst/ch.so.bevoelkerung.altersstruktur/meta-ch.so.bevoelkerung.altersstruktur.xtf","source_path":"<artifact.path aus export_xtf>"}'
uv run datenportal-integrator call validate --json '{"run_id":"<ID>"}'
```

HTML unter `review_path` öffnen. Nach erfolgreicher fachlicher Prüfung den
aktuellen Fingerprint aus `validate` verwenden:

```sh
uv run datenportal-integrator call approve --json '{"run_id":"<ID>","gate":"metadata","fingerprint":"<FP_METADATA>","human_statement":"Ich gebe das angezeigte Datenblatt und die vorbereiteten fachlichen Repo-Aenderungen frei."}'
uv run datenportal-integrator call apply_local_changes --json '{"run_id":"<ID>"}'
uv run datenportal-integrator call deliver --json '{"run_id":"<ID>","environment":"local"}'
```

`deliver` nach Prüfung des Zwischenstands erneut ausführen, bis `phase` den
Wert `complete` oder `failed` hat. Ein Aufruf wartet nicht auf alle externen
Phasen. Bei Fehlern zuerst den bestehenden Vorgang aufklären. Wenn relevante
Konfiguration die CSV-Freigabe entwertet hat, vor der Metadatenfreigabe
`analyze` und Stopp 1 wiederholen.

## Varianten

### Serien

| Begriff | Beispiel | Verwendung |
| --- | --- | --- |
| Serienidentifier | `ch.so.bevoelkerung.altersstruktur` | `start.identifier`, Jenkins `DATASET` |
| Ausgabenbezeichnung | `2025` | `start.issue`, Jenkins `SERIES_ID` bei CSV |
| Identifier der Ausgabe | `ch.so.bevoelkerung.altersstruktur_2025` | Metadaten und Portal-Link |

Die Ausgabenbezeichnung wird nicht aus dem Serienidentifier abgeleitet.
Die Ausgabe muss eindeutig im freigegebenen Datenblatt vorhanden sein.
Ein Serien-Datenblatt benötigt genau eine aktuelle Ausgabe. Fehlende
Zuordnungen werden nachgefragt; neue Ausgaben entstehen nicht allein aus dem
Dateinamen. Attribute können auf der Serie oder der Ausgabe liegen.

### Nur Daten oder nur Metadaten

| Lieferung | Vorgehen |
| --- | --- |
| Nur CSV | `metadata_path` weglassen; `baseline` lädt das angenommene Datenblatt. CSV und dieses Datenblatt prüfen und freigeben. Ohne Bearbeitung kein `export_xtf` und keine Metadaten-Datei hochladen. |
| Nur XTF/Metadaten | `data_path` und gewöhnlich `issue` weglassen. CSV-Stopp entfällt. Datenblatt importieren/bearbeiten, exportieren, validieren und freigeben. Bei Serien die vollständige Serie mit allen Ausgaben erhalten. |
| CSV und Metadaten | Beide Dateien bzw. neuen Entwurf aufnehmen; beide Freigabestopps durchlaufen. |

Bei reiner Datenlieferung wird vor dem Upload geprüft, ob der angenommene
Metadatenstand noch derselbe ist. Ein Repository-Datenblatt allein ersetzt
keinen fehlenden angenommenen Bestand für eine solche Lieferung.
Bei `target_changed` Bestand erneut vergleichen und Daten neu prüfen.
Die Erhaltung der nicht gelieferten Bestandteile erfolgt über den bestehenden
Jenkins-/GRETL-Liefervertrag.

### Dienststellen und neue Organisationen

Eine Dienststelle wird über `creator_ref` und `Office.identifier` zugeordnet.
Sie ist von der Jenkins-Organisation unabhängig. Für eine fehlende Dienststelle
benötigt der Agent tatsächliche Werte für `identifier`, `name`, `abbreviation`,
`email`, `officeAtWeb` und `phoneNumber`. `office` bereitet die Änderung des
vorhandenen Katalogs `shared/data/offices.xtf` vor. Dieser und die XTF werden
vor der Metadatenfreigabe validiert und als Änderung angezeigt.

Eine neue Jenkins-Organisation benötigt die bestehenden Organisationsdateien
und passende Berechtigungs-/Teamkonventionen. Der Agent klärt diese mit dir und
bereitet die vorgesehenen Dateien vor. Neue Berechtigungen in gemeinsamen
technischen Komponenten sind kein automatischer Bestandteil dieses Ablaufs.

### Lieferantenumstellung und dauerhafter Konverter

Ein Umbau benötigt einen Python-Konverter mit Tests im Integrator unter
`topics/<organisation>/<identifier>/`. `transform` führt zuerst die Tests und
dann den Konverter aus, zeigt Vorher/Nachher und erzeugt
`lieferanten-vorgabe.txt`. Deine Entscheidung wird pro Thema gespeichert:

- `supplier`: Der Lieferant stellt seine künftige Struktur um; der Konverter
  dient dem aktuellen Umbau. Der Agent erstellt die Strukturvorgabe.
- `recurring`: Zukünftige Dateien werden im Integrator vor der normalen
  Jenkins-Anlieferung konvertiert. Der Skill verwendet das gespeicherte Rezept.

Der Lieferant erhält nichts automatisch. Konverterausgaben werden erneut
analysiert und freigegeben. `recurring` ist keine Terminplanung; es wird bei
der nächsten beauftragten Lieferung durch den Agenten angestossen.

## Wiederaufnahme und Fehlerbehandlung

Die Vorgangs-ID aufbewahren. Beim Fortsetzen `status` lesen:

```sh
uv run datenportal-integrator call status --json '{"run_id":"<ID>"}'
```

`current_approvals` zeigt die gültigen Freigaben. Gespeicherte Dateien unter
`files/` und `changes/` nicht manuell bearbeiten. Änderungen über Aufnahme oder
Java-MCP durchführen und erneut prüfen. Freigaben sichern den Ablauf, sind
aber keine unabhängige Authentisierung gegenüber einem Agenten mit Dateizugriff.

| Meldung oder Situation | Nächster Schritt |
| --- | --- |
| `configuration_missing`, `credentials_missing` | Konfigurationspfad und Umgebung des tatsächlich startenden Prozesses prüfen. |
| `datasheet_mcp: false` bei `doctor` | Java-MCP starten; URL, Port und Health-Endpunkt prüfen. |
| `approval_required`, `stale_review`, `validation_required` | Aktuelle Vorschau erzeugen, Fehler beheben und konkrete Fassung erneut freigeben. |
| `export_required`, `draft_not_exported` | Aktuellen Java-Entwurf exportieren und mit `validate` prüfen. |
| `unknown_office`, `issue_required`, `unknown_issue` | Dienststelle bzw. Serienzuordnung klären und Datenblatt/Katalog ergänzen. |
| `metadata_uncertain` oder Java-Neustart | Gesicherten Stand mit `metadata(operation="restore")` rekonstruieren, neue interne IDs verwenden, unbestätigte Änderung bewusst erneut ausführen. |
| `draft_conflict` | Externe Bearbeitung prüfen; bewusster Neuimport oder Wiederherstellung. |
| `stack_mismatch`, `ambiguous_stack` | Checkout/Instanz durch Betreiber klären; keine laufende Instanz still umstellen. |
| `stack_unhealthy`, `builds_running`, `jenkins_paused` | Health/Builds bzw. administrativen Zustand prüfen und später denselben Vorgang fortsetzen. |
| `manifest_invalid`, `remote_read_failed` | Bestand und Zugriff durch Betreiber klären; keine Neuinitialisierung auslösen. |
| `repository_conflict`, `pr_base_conflict`, `target_changed` | Fremde Änderungen erhalten, Ausgangsstand vergleichen und neue Fassung prüfen. |
| `human_merge_required`, `pr_changed`, `merged_content_changed` | Menschlichen Merge bzw. geänderten PR-/Zielbranchstand prüfen. |
| `submission_unknown` | Bestehende Jenkins-Queue/Lauf zuordnen; nicht erneut hochladen. |
| `phase: failed` | Laufbericht, `message`/`error`, `build_url` und Prüfprotokolle lesen; Publikationsstatus gesondert betrachten. |

Nach Java-Neustart, anschliessend wieder exportieren und prüfen:

```sh
uv run datenportal-integrator call metadata --json '{"run_id":"<ID>","operation":"restore"}'
```

Bei einem unbestätigten Upload den Lauf anhand Vorgangskennung (`COMMENT`)
und Themenidentifier in Jenkins klären. Mit echten Job-/Queuekennungen zuordnen:

```sh
uv run datenportal-integrator call reconcile --json '{"run_id":"<ID>","environment":"local","job":"<Jobname aus Jenkins>","queue":"<Queue-ID>"}'
uv run datenportal-integrator call reconcile_seed --json '{"run_id":"<ID>","environment":"local","queue":"<numerische Seed-Queue-ID>"}'
```

Der zweite Aufruf ist nur für einen unbestätigten Seed erforderlich. Ist dessen
Queue-Eintrag verschwunden, kann diese Schnittstelle ihn nicht mehr zuordnen;
der Betreiber muss den Laufzustand klären.

`publication: accepted` bedeutet erfolgte Publikation, auch bei Reloadfehler.
Nach einer separat ausgeführten Reloadreparatur nur nachprüfen:

```sh
uv run datenportal-integrator call verify_delivery --json '{"run_id":"<ID>","environment":"local"}'
```

Das löst weder Reload noch neuen Upload aus. Bei inzwischen neuerer Manifest-
Release zuerst den Bestand klären. Auch fehlende RDF-Übernahme oder abweichende
Metadaten können nach erfolgter Publikation die Gesamtprüfung scheitern lassen.

`retry_delivery` ist für einen bekannten, nicht publizierten Fehllauf nach
Korrektur und aktuellen Freigaben vorgesehen. Es bereitet einen neuen Versuch
vor; erst anschliessendes `deliver` startet ihn.

## Abnahme und Referenzen

Ein technischer Test mit gekennzeichneten Testfreigaben ersetzt keinen
fachlichen Durchlauf mit einem Menschen. Testumfang, tatsächlich ausgeführte
Läufe und offene Abnahmen stehen im
[Entwicklerhandbuch](entwicklerhandbuch.md#abnahmestand).
Die [Agent-Anleitung](../skills/datenportal-themenintegrator/SKILL.md) beschreibt
Rückfragen und Stopps. Zurück zum [Repository-Einstieg](../README.md).
