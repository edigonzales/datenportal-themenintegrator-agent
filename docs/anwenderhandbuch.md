# Anwenderhandbuch für das Java-Betriebspaket

## Installation und Konfiguration

Benötigt werden JDK 21 und für Repository-Änderungen Git. `gh`, Docker und Gradle sind für normale Facharbeit nicht erforderlich. Das JDK enthält den Compiler für fachliche Java-Konverter. JAVA_HOME muss auf das JDK zeigen; andernfalls wird Java im PATH verwendet.

Das vollständige Archiv wird im Maven-Repository `https://jars.interlis.guru/snapshots/` unter `ch.so.agi:datenportal-themenintegrator-agent:<version>:bin@tar.gz` veröffentlicht. Eine konkrete veröffentlichte Version herunterladen, deren Prüfsumme prüfen und in ein dauerhaftes Installationsverzeichnis entpacken. Das Archiv enthält Programme, Modelle, Skills und Betriebsressourcen, aber keine Implementierungsquellen. Die Versionsauswahl erfolgt ausdrücklich; es gibt kein automatisches Update auf „latest“.

```bash
export JAVA_HOME=/pfad/zum/jdk-21
/pfad/zum/paket/bin/datenportal-agent workspace install /absoluter/pfad/betrieb
cd /absoluter/pfad/betrieb
cp config/local.example.toml config/local.toml
```

`topics_repo` auf das fachliche Themenrepository setzen. `model_dirs` ergänzt bei Bedarf weitere Modellverzeichnisse. Die beiden MCPs werden aus dem Paket als stdio-Prozesse gestartet. Es gibt keine MCP-Image-, Port- oder Compose-Konfiguration. Für zusätzliche Eingangsordner `input_roots` auf oberster Ebene in `config/local.toml` setzen, zum Beispiel `input_roots = ["../lieferungen"]`. Relative Pfade beziehen sich auf den konfigurierten Betriebs-Root. Standardmässig ist dessen übergeordnetes Verzeichnis freigegeben. Nach Änderungen beide MCP-Verbindungen neu starten; ein erneutes `init` ist dafür nicht erforderlich. Die Pfadfreigabe beschränkt den Dateiimport im Java-Prozess und ist keine Betriebssystem-Sandbox.

```bash
bin/datenportal-agent init
bin/datenportal-agent doctor
```

`init` prüft JDK, Paketressourcen, beide MCPs sowie eine gültige und eine absichtlich ungültige synthetische CSV. Es richtet die eingeschränkten Clientzugänge ein. Synthetische Testfreigaben autorisieren keine fachlichen Änderungen. `doctor` trennt lokale Prüfbereitschaft von Publikationszugängen.

Den Betriebsordner im Client öffnen. Der Fachagent arbeitet ausschliesslich mit den drei Skills und den öffentlichen MCP-Werkzeugen. Implementierungsquellen, allgemeine Shell und Buildwerkzeuge bleiben ausserhalb seines Arbeitsbereichs.

## Zugänge und Publikation

GitHub-PRs werden direkt über die REST-API erstellt und geprüft. `GH_TOKEN` hat Vorrang vor `GITHUB_TOKEN`; ohne nichtleere Env-Variable wird der Credential Store des Workspace verwendet. Für das gewählte Repository werden Pull-Request-Schreibrechte und bei HTTPS-Push zusätzlich Contents-Schreibrechte benötigt. Git nutzt den vom Integrator gestarteten Credential-Helper; `gh auth login` ist nicht erforderlich. Tokens nicht in Konfigurationsdateien oder Chatnachrichten eintragen. Git-Autorenidentität wie üblich konfigurieren. Nur bei Verwendung von Env-Tokens muss der Client diese an den Integrator weitergeben.

GitHub-Token einmalig im bestehenden dateibasierten Store hinterlegen, ohne ihn
in der Shell-History zu speichern: Im Operator-Workspace folgenden Block in
**zsh** ausführen und den Token erst bei der verdeckten Abfrage eingeben:

```zsh
(
  read -rs 'github_token?GitHub-Token: ' || exit 1
  printf '\n'
  printf '%s' "$github_token" |
    bin/datenportal-agent credentials github set --token-stdin
)
```

`read -s` unterdrückt die Anzeige der Eingabe. Der Token wird über stdin
übergeben, nicht als Prozessargument oder Shell-Befehl. Die Variable verschwindet
beim Ende der Subshell. Keine Tokens direkt in Befehle oder Chatnachrichten
eintragen. Der CLI-Befehl liest stdin bis EOF; die Pipe beendet die Eingabe
automatisch. Alternativ übernimmt `credentials github set --from-env` ausdrücklich
die erste nichtleere Variable (`GH_TOKEN`, dann `GITHUB_TOKEN`).

Anschliessend ohne Ausgabe des Tokens prüfen:

```sh
bin/datenportal-agent credentials github status
```

Erwartet wird `"stored": true`. Ohne gesetzte Env-Tokens erscheint zusätzlich
`"credentials_source": "local_store"`. Sind `GH_TOKEN` oder `GITHUB_TOKEN` noch
gesetzt, haben sie weiterhin Vorrang vor dem gespeicherten Token.

Soll dieser Workspace dauerhaft ausschliesslich den Credential Store verwenden,
auf oberster Ebene von `config/local.toml` (vor den Tabellen wie `[datasheet]`)
folgende Einstellung ergänzen:

```toml
github_token_source = "store"
```

Danach `bin/datenportal-agent init` ausführen und die MCP-Verbindungen neu starten.
Die Einstellung bleibt bei erneutem `init` erhalten und gilt gemeinsam für REST
und Git-Helper, auch wenn der Client GitHub-Token-Variablen erbt. Fehlt der
Store-Eintrag oder ist er unlesbar, wird ein Fehler gemeldet; es gibt keinen
Rückgriff auf Env-Tokens. `credentials github status` zeigt zusätzlich
`"configured_source": "store"`. Ein ausdrücklicher Import mit `set --from-env`
bleibt möglich. Mit `github_token_source = "auto"` (Standard) gilt wieder
GH_TOKEN → GITHUB_TOKEN → Store.


`credentials github status` zeigt die aktive Quelle und mit `stored`, ob ein
Store-Eintrag existiert; bei unlesbarem Store erscheint stattdessen `store_error`.
Der Status prüft keine GitHub-Berechtigungen. Zum Rotieren erneut `set` verwenden,
zum Entfernen `credentials github remove`. Env-Tokens haben auch nach einer
Rotation Vorrang. Ein abgelehnter Token löst keinen Wechsel zur nächsten Quelle aus.

Der Store liegt unter `.datenportal-integrator/credentials/local.json` im Workspace
und ist durch Eigentümerprüfung und Rechte 0700/0600 geschützt, nicht verschlüsselt.
GitHub und Jenkins teilen sich den Store; ihre Einträge werden unabhängig verwaltet.

Jenkins-Profile stehen unter `[environments.<name>]`: `kind`, `enabled`, `jenkins_url`, `portal_url`, `manifest_url`, `username_env`, `token_env`, `repository_mode="managed-git"`, `git_write_back=false`, optional `git_remote` und `git_branch`. INT/PROD-Adressen werden nicht vorbelegt. Jenkins-Zugänge können auch mit `credentials set <profil> --username <user> --token-stdin` eingerichtet werden. `credentials status` zeigt keine Geheimnisse.

Die lokale CSV-Vorprüfung verwendet ilivalidator direkt. Jenkins prüft vor der Publikation eigenständig mit GRETL. Es gibt keinen Bundle-, Image- oder Bibliotheksabgleich zwischen beiden Prüfungen. Daten- und Modellprüfsummen binden Freigaben weiterhin an den gezeigten fachlichen Stand.

Nur für eine ausdrücklich gewünschte lokale Publikation oder `acceptance delivery` werden Docker und ein vorhandener Dev-Stack benötigt. `stack_repo` und ein lokales Loopback-Profil dafür gesondert konfigurieren. Der Integrator startet keine lokalen Container für CSV/XTF- oder Modellprüfungen.

## Wiederaufnahme und Updates

Neue Vorgänge verwenden Schema 3. Vorgänge aus der früheren Docker-Version werden nicht migriert: den alten Arbeitsbereich mit seiner bisherigen Version abschliessen und für neue Aufträge einen neuen Arbeitsbereich verwenden. Offene externe Uploads zuerst im alten Vorgang aufklären; niemals durch einen neuen Vorgang doppelt senden.

Innerhalb der neuen Version bleibt bei Wiederaufnahme die `run_id` erhalten. Bei einem unklaren PR-Erstellungsaufruf wird der bestehende GitHub-Stand abgefragt, kein zweiter PR blind erstellt. Rate-Limit-Berichte nennen die Wartezeit.

Für Updates ein neues vollständiges Paket in ein separates Installationsverzeichnis entpacken und dessen Launcher verwenden:

```bash
/pfad/zum/neuen-paket/bin/datenportal-agent workspace update /absoluter/pfad/betrieb
cd /absoluter/pfad/betrieb
bin/datenportal-agent init
```

Arbeitsdateien und lokale Konfiguration bleiben erhalten. Individuell veränderte verwaltete Dateien führen zu einem Konfliktbericht. Das alte Paket erst entfernen, wenn kein Arbeitsbereich es mehr verwendet. Alte Container und Volumes werden nicht automatisch gelöscht.

## Diagnose

`jdk_required` verlangt ein vollständiges JDK 21. `package_incomplete` verlangt ein vollständiges Paket; der Fachagent baut keine fehlenden Programme selbst. `configuration_upgrade_required` verweist auf die neue Konfigurationsvorlage. `unsupported_state_version` verlangt die bisherige Version für den alten Vorgang. `github_credentials_missing`, `github_access_denied` und `github_rate_limited` unterscheiden fehlende Zugänge, Berechtigungen und Wartezeiten.

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

Das [Entwicklerhandbuch](entwicklerhandbuch.md#architektur) beschreibt Komponenten und Verantwortlichkeiten. Fachliche Freigaben und optionale INT-/PROD-Publikation bleiben getrennte Schritte.

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
gehört automatisch auch die Vorbereitung von `dataset.gradle` mit Konfiguration des bestehenden CSV-Validierungstasks im Themenordner. Standard sind INTERLIS 2.4,
Profil `SO-DATA` ohne Kommentare und zunächst eine flache Klasse. Beobachtete Werte von 120 bis
20'000 begründen keinen fachlichen Wertebereich; zufällige Eindeutigkeit
begründet keinen Schlüssel.

**Ergebnis:** INTERLIS-Modell (`.ili`), `dataset.gradle`, Prüfresultate und
aktualisierte Modellreferenz im Datenblatt werden gemeinsam vorbereitet.
Der konfigurierte Validator bleibt in der bestehenden Publikationsreihenfolge:

```text
mergeDatasheets
  → validateDeliveredCsv (CsvValidator)
    → prepareCsvConversion
```

Der vorhandene GRETL-CsvValidator prüft die Lieferdatei gegen das Modell mit
Semikolon, Anführungszeichen, UTF-8 und Kopfzeile. Schlägt die Validierung fehl,
stoppt der Ablauf vor Konvertierung und Publikation. Reine Metadatenlieferungen
überspringen die CSV-Validierung. Die unabhängige Java-Vorprüfung bleibt bestehen.

**Deine Entscheidung:** Modellname mit Datumssuffix, absolute URI, passendes
ISO-Versionsdatum sowie unbelegte Regeln klären. Numerische Vorschläge mit
Begründung und Spielraum für künftige Lieferungen im Modellreview prüfen. Beim Themenvorgang
Modell, `dataset.gradle`, Datenblatt und Prüfresultate gemeinsam prüfen und
die konkreten Modell- und Metadatenstände freigeben. Die Taskdatei gehört zur
Modellfreigabe.
Eigenständige Modellierung ist auch ohne Datenpublikation möglich.

**Beispielprompt für Modellierung im Themenvorgang:**

> Verwende den Skill `datenportal-themenintegrator`. Leite im Vorgang `<Vorgangs-ID>` aus der bestätigten CSV und dem Datenblatt ein INTERLIS-Modell ab. Frage die Modellidentität und fachlich nicht belegte Regeln nach. Zeige die dabei regulär vorbereiteten Dateien `.ili` und `dataset.gradle` mit der Konfiguration des vorhandenen Validierungstasks `validateDeliveredCsv` gemeinsam mit dem aktualisierten Datenblatt und den Prüfresultaten zur Freigabe.

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

Metadaten-XTF werden beim `metadata(export_xtf)` automatisch im konfigurierten Themenordner vorgemerkt; optional `arguments.repository_filename` angeben. Neue Datenblätter verwenden `meta-<identifier>.xtf`, ausgewählte Bestandsdateien behalten ihren Pfad. Andere fachliche Dateien weiterhin mit `stage_change` registrieren. Erst nach fachlicher Freigabe übernimmt `apply_local_changes` die geprüften Bytes.

**Zweiter menschlicher Stopp:** Datenblatt, Dienststellen- und Organisationsänderungen prüfen. Korrekturauftrag bedeutet weiter bearbeiten, exportieren, validieren und erneut zeigen. Das OK mit `gate: metadata` und der Prüfsumme aus `validate` protokollieren. Bei zusätzlichem Modell werden Datenblatt und Modell gemeinsam gezeigt und beide konkreten Gates bestätigt.

```sh
./bin/datenportal-agent call apply_local_changes --json '{"run_id":"VORGANGS_ID"}'
./bin/datenportal-agent call deliver --json '{"run_id":"VORGANGS_ID","environment":"local"}'
```

`deliver` führt je Aufruf höchstens eine externe Phase weiter. Eine passende
laufende Stack-Instanz wird verwendet; andernfalls führt der Integrator das
Startskript `scripts/up.sh --infrastructure-only` des Dev-Stacks aus.
Für einen noch fehlenden
lokalen Publikationsstand delegiert er an `scripts/bootstrap.sh` mit den
konfigurierten Compose-Dateien. Der gemeinsame Helfer übernimmt erfolgreichen
Seed, pausierten administrativen Erstaufbau und Portalstart. Bei vorhandenem
Bestand prüft `--check-only` lesend, ohne Seed oder Container-Neuerstellung.
Ein vorhandenes oder beschädigtes Manifest wird niemals durch einen Erstaufbau ersetzt. Anschliessend Seed, Lieferung, Publikationsbericht, Reload, Metadaten und Downloads prüfen. Erneute Aufrufe fragen gespeicherte Läufe ab. Bei Erfolg erscheint der konkrete `portal_url`.

**Abschluss wählen:** lokal abschliessen, lokal testen und anschliessend einen fachlichen PR erstellen oder ausdrücklich INT/PROD beliefern. Der PR benötigt keine Publikationsfreigabe. `repository_plan` zeigt zuvor Git-Repository, Remote, Zielbranch, Änderungen und offene Voraussetzungen. Ein Mensch übernimmt den PR; danach prüft `sync_repository` Merge und Zielbytes und aktualisiert das lokale Themenrepo. Reine Datenlieferungen ohne Repository-Änderung brauchen keinen PR. INT/PROD benötigen für eine beauftragte Anlieferung zusätzlich den konkreten `publication_plan` und eine echte Freigabe `publish:<Profil>`.

INT/PROD sind nur mit passenden Zugängen und bestehendem `managed-git`-Betrieb ohne Git-Rückschreiben nutzbar. Der Integrator stellt bestehende Runtime-Konfigurationen nicht um.

## Lokaler Lieferablauf und Publikationsfreigaben

Nach den fachlichen Freigaben für Daten, Metadaten und gegebenenfalls Modell
zuerst vorhandene Änderungen mit `apply_local_changes` lokal übernehmen. Nur bei
beauftragter lokaler Lieferung anschliessend `deliver(environment="local")`
phasenweise fortsetzen. Eine fachliche Freigabe erweitert den Auftrag nicht
automatisch um eine lokale Publikation.

Für Profile mit `kind=local` ist keine zusätzliche Publikationsfreigabe vorgesehen.
`approve(gate="publish:local")` wird mit
`local_publication_approval_not_required` abgelehnt; dies gilt auch für anders
benannte lokale Profile. Der lokale Liefer- und Publikationstest umfasst die
Lieferung über Jenkins und die Prüfung von Publikationsbericht, Portal-Reload,
Metadaten und Downloads. Die Dateiübernahme allein erfüllt diesen Nachweis nicht.

Danach kann der lokal geprüfte Stand ins echte Themenrepository übernommen werden:

1. `repository_plan(run_id, environment="local")` zeigt das Git-Ziel und offene Voraussetzungen, ohne Jenkins-/Portal-Zugriff, Arbeitskopie oder Push. Zugangsdaten in Remote-Adressen sind unzulässig und werden nicht ausgegeben.
2. Die beauftragte PR-Erstellung mit `prepare_pr(run_id, environment="local")` prüft alle fachlichen Freigaben und den aktuellen erfolgreichen lokalen Liefernachweis erneut, pusht einen separaten Branch und erstellt den PR. Weder `publish:local` noch eine INT-/PROD-Freigabe ist dafür erforderlich.
3. Ein Mensch mergt. Anschliessend `sync_repository(run_id, environment="local")` ausführen. Dies startet keine Anlieferung. INT und PROD können weiterhin manuell beliefert werden.

Bei diesen drei Repository-Operationen bestimmt das bestehende Profil Git-Remote
(Standard `origin`) und Zielbranch (Standard `main`). Lieferumgebung und Git-Ziel
werden getrennt angezeigt; es gibt keine neue Git-Konfiguration. Ein bereits
erteilter PR-Auftrag benötigt keine erneute Bestätigung. Fachliche Freigaben
allein erteilen jedoch keinen neuen Push- oder Lieferauftrag.

Eigenständige Modell- und Organisations-PRs benötigen ihre fachlichen Freigaben,
aber keinen Themen-Liefernachweis. Ohne Repository-Änderungen ist kein PR nötig.

Nur für eine ausdrücklich beauftragte INT-/PROD-Anlieferung den konkreten
`publication_plan` zeigen und nach menschlichem OK `approve(gate="publish:<Profil>")`
protokollieren. Beim Themenvorgang setzt dies weiterhin einen erfolgreichen
lokalen Liefer- und Publikationstest für den aktuellen Stand voraus. Ein
vorhandener oder gemergter PR ersetzt diese Publikationsfreigabe nicht.

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

Nach erfolgreicher Ableitung werden `.ili`, `dataset.gradle` und die Datenblatt-Modellreferenz zusammen vorbereitet. Die Referenz wird über den Datenblatt-MCP vor dem abschliessenden Export gesetzt. `validate_model` prüft die CSV mit dem lokalen INTERLIS-Adapter. Derselbe Themen-Task läuft später vor `preparePublicationWorkspace`; Metadatenlieferungen überspringen ihn ausdrücklich. Es gibt dafür keine eigene CSV-zu-XTF-Konvertierung.

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
mit erneuter Prüfung und Freigabe. Den geprüften Paketstand nennt die
[Abnahme](abnahme.md); vorhandene Installationen wechseln nur durch ein
[ausdrückliches Update](#wiederaufnahme-und-updates).

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

Der Kandidat enthält Organisationsordner, `gretl-datenportal-job.yaml`, `settings.gradle`, `build.gradle`, gegebenenfalls Office-Katalog und Teamkatalog. `validate_organization` prüft Office-XTF, YAML und Berechtigungen. HTML-Dateien und Dateivergleiche zeigen die Änderungen.

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

### Freigaben und Paketupdates

Freigaben sind an fachliche Inhalte und relevante Prüfregeln gebunden, nicht an
Paketpfad oder Integrator-JAR. Ein unveränderter Prüfstand bleibt bei einem
Paketupdate gültig. `workspace update` kann noch aktuelle Fingerprints aus 0.2.20
bei identischem Prüfinhalt kontrolliert überführen; alte Freigaben und ihre
menschlichen Aussagen bleiben im Audit erhalten. Das alte Paket bis zum
abgeschlossenen Update behalten. Geänderte Regeln oder nicht belegbare Altstände
werden nicht automatisch freigegeben.

### Themenrepository nach einem PR-Merge aktualisieren

Nach dem menschlichen Merge synchronisiert der Integrator beim Fortsetzen vor
neuen externen Starts das konfigurierte Themenrepository. Für einen Abschluss ohne
Publikation steht derselbe Schritt separat bereit:

```sh
./bin/datenportal-agent call sync_repository --json '{"run_id":"VORGANGS_ID","environment":"local"}'
```

Das Profil muss dem vorbereiteten PR entsprechen. Dieser Aufruf startet weder
Jenkins noch einen Upload. `status` bleibt lesend und zeigt unter `repository_sync`
den Abschluss oder einen Blockierungsgrund. Ein Merge auf GitHub allein verändert
den lokalen Checkout weiterhin nicht. Details zu Konflikten und Wiederaufnahme:
[Lieferung und Wiederaufnahme](agent/lieferung-und-wiederaufnahme.md#lokales-repository-nach-merge-synchronisieren).

## Modelländerungsvertrag und korrigierte CSV

`operation_schema("derive_model")` und `operation_schema("derive_model.changes")`
liefern den vollständigen Änderungsvertrag des konfigurierten INTERLIS-MCP,
einschliesslich verschachtelter Typen und referenzierter Definitionen. Für Betreiber
ist derselbe Vertrag über `bin/datenportal-agent schema derive_model.changes`
abrufbar. Diese Auskünfte benötigen keine Vorgangs-ID und ändern kein Modell.
Ist der Vertrag nicht verfügbar, wird dies als `model_contract_unavailable`
gemeldet; ausführende Probeaufrufe sind kein Ersatz.

Bei korrigierter CSV: verifiziert übernehmen, im bestehenden Vorgang als `data`
anhängen, analysieren, tatsächliche Datenfreigabe einholen und das unveränderte
Modell mit `validate_model(scope="integration")` erneut prüfen. Ohne vorbereitete
Modellreferenz zunächst `scope="model"`, `prepare_model_reference`, danach
`scope="integration"`. Kein künstlicher Änderungsbatch und keine Neuschätzung der
Modellgrenzen. Abweichender Datenblattvertrag, ungültige Modellnachweise und echte
CSV-Regelverletzungen bleiben Sperren.

`source_data_sha256` dokumentiert weiterhin die Ableitungsquelle.
`validated_data_sha256` im Modellstand nennt die zuletzt erfolgreich geprüfte CSV;
`data_sha256` im Prüfbericht nennt auch bei Fehlern die aktuell geprüfte Eingabe.
Der Review trennt aktuelle von ursprünglichen Ableitungsbeobachtungen. Alte
Vorgänge benötigen keine Migration. Neue CSV entwertet abhängige Freigaben;
eine erfolgreiche Neuprüfung ersetzt keine menschliche Freigabe.

Bei fachlicher Modelländerung zuerst das Änderungsschema lesen. Gespeicherte
Zahlenvorschläge und bestätigte Typangaben werden bei unverändertem Vertrag
wiederverwendet, Ohne Modellabnahme bleiben Schätzungen unbestätigt. Die explizite Modellabnahme bestätigt enthaltene Zahlenbereiche und Dezimalgenauigkeiten; unveränderte Regeln bleiben bei gleichem Datenblattvertrag bestätigt. Eine neu beobachtete Eindeutigkeit
darf vorgeschlagen, aber erst nach menschlicher Bestätigung als UNIQUE eingebaut
werden. Das vollständige Beispiel steht in der Modellskill-Referenz
`skills/datenportal-validierungsmodell/references/typableitung-und-reparatur.md`.

### Numerische Genauigkeit im Validierungsmodell

Grenzen als Dezimalstrings angeben: `"0.0"` / `"100.0"` bedeutet eine,
`"0.00"` / `"100.00"` zwei Nachkommastellen. `0 .. 100` ist ganzzahlig.
Beide Grenzen müssen dieselbe Genauigkeit haben; die Nullgrenze bleibt erhalten.
CSV-Werte zeigen die mindestens erforderliche Genauigkeit, ersetzen aber keine
bestätigte Fachregel. Widersprüche werden als offene Fachfrage zurückgegeben.

Bei `model_numeric_contract_violation` wurde eine Abweichung zwischen gewünschtem
Bereich und kompiliertem Modell festgestellt. Keine alternativen Grenzen oder
Dateipfade versuchen; die MCP-Komponente muss korrigiert werden. Bei
`model_component_upgrade_required` das Betriebspaket mit aktuellem INTERLIS-MCP
installieren. Paketwechsel übertragen keine fachlichen Freigaben auf neue Prüfstände.

### Manuelle Seed-Übernahme

`adopt_seed(run_id, environment, build_url)` übernimmt einen manuell gestarteten
Build des konfigurierten Seed-Jobs ohne neuen Seed oder Upload. Laufende Builds
werden weiter abgefragt; nur `SUCCESS` erfüllt die Seed-Voraussetzung. Danach
`deliver` fortsetzen. Ohne manuell zugeordneten Seed startet der normale Ablauf
einen eigenen. Historie, fachliche Freigaben und INT-/PROD-Freigaben bleiben
wirksam. Nach verändertem Prüfstand erneut prüfen und passend zuordnen.

`status.external_runs` und Fehlerfeld `external_state` unterscheiden Seed und
Datenlieferung. `delivery_submitting` benötigt die Aufklärung des Datenuploads
mit `reconcile`, keine Seed-Kennung. Buildnummern sind keine Queue-IDs.
Manuelle Repo-Korrekturen bleiben erhalten: fehlende und veränderte Dateien
werden getrennt gemeldet und müssen als neue Kandidaten geprüft werden.
Siehe [Wiederaufnahme](agent/lieferung-und-wiederaufnahme.md#manuell-repariert-und-geseedet).

### Modellabnahme und numerische Fachregeln

Die ausdrückliche Abnahme des gesamten vorgelegten Modells bestätigt auch seine
Zahlenbereiche und Dezimalgenauigkeiten. Keine zusätzliche Einzelabnahme verlangen.
Vor der Abnahme dürfen Agentenvorschläge nicht als menschliche Bestätigung erfasst
werden. Danach nennt `numeric_rule_confirmations` den effektiven Status und den
Freigabenachweis; `range_proposal.origin=agent_estimate` dokumentiert weiterhin die
Herkunft. Historisches `range_proposal.confirmed=false` ist kein aktueller Freigabestatus.
Unveränderte Regeln bleiben innerhalb des Vorgangs bei gleichem Datenblattvertrag
bestätigt. Ein geänderter Modellstand benötigt trotzdem seine reguläre Freigabe.
Widersprechende Grenzen oder Genauigkeiten fachlich klären, nicht automatisch lockern.
Bestehende aktuelle Modellfreigaben werden beim nächsten schreibenden Schritt
nachvollziehbar ergänzt; aus veralteten Freigaben wird keine Bestätigung abgeleitet.
Im Abschluss beispielsweise schreiben: «Die Zahlenbereiche und Genauigkeiten wurden
vom Agenten vorgeschlagen und mit der Modellabnahme bestätigt.»

### Bestätigter CSV-Name beim Import

Bei einem ungültigen CSV-Namen schlägt der Agent einen Namen mit ASCII-Buchstaben,
Ziffern und Unterstrichen vor. Nach deiner Zustimmung erstellt er selbst die
bytegleiche Eingangskopie mit `datenportal_workspace.import_attachment`:
`target_name` enthält den bestätigten Namen inklusive `.csv`, `human_statement`
die tatsächliche Zustimmung. Bereits erteilte Zustimmung wird nicht erneut erfragt.
Die Originaldatei bleibt unverändert. Ohne Zustimmung wird nicht umbenannt.
Die drei SHA-256-Prüfungen bleiben verbindlich; diese Zustimmung erteilt keine
CSV-, Modell-, Metadaten- oder Publikationsfreigabe. Die Erweiterung gilt nur für
CSV-Dateien, nicht für XTF/XLSX und nicht für Verzeichnispfade.
