# Datenportal-Themenintegrator

Der Agent unterstützt die Integration von CSV-, XTF- und XLSX-Lieferungen,
INTERLIS-Validierungsmodelle und neue Organisationen. Er erstellt prüfbare
Kandidaten und HTML-Berichte; fachliche Änderungen und Publikationen benötigen
deine Freigabe.

## Mit Docker starten

Du brauchst lokales Docker mit Compose, Git, Bash und einen eingerichteten
Client: Codex oder OpenCode 2.x stable. Auf macOS in Docker Desktop ab 4.34
**Settings → Resources → Network → Enable host networking** aktivieren und
**Apply and restart** wählen. Unter Linux einen lokalen Docker Engine verwenden.

Die drei Checkouts liegen im Standardfall nebeneinander:

```sh
mkdir datenportal-arbeitsbereich
cd datenportal-arbeitsbereich
git clone https://github.com/edigonzales/datenportal-themenintegrator-agent.git
git clone https://github.com/sogis/datenportal-themenrepo.git
git clone https://codeberg.org/edigonzales/datenportal-dev-stack.git
cd datenportal-themenintegrator-agent
```

Bestehende Checkouts können weiterverwendet werden. Bei abweichenden Pfaden,
belegten Ports 8000/8080 oder zusätzlichen Eingabeordnern zuerst
[die betreffenden Einstellungen](docs/anwenderhandbuch.md#was-muss-ich-konfigurieren)
anpassen. Bei Standardpfaden und freien Ports ist keine manuelle
Konfigurationsdatei nötig.

| Du stellst bereit | Der Agent übernimmt | Erst später erforderlich |
|---|---|---|
| Docker, Client und die drei Checkouts | Agent-Image laden und festhalten | GitHub-Zugang und Git-Identität für Schreibzugriffe |
| Abweichende Pfade und zusätzliche Mounts | Fehlende Standardkonfiguration anlegen | Jenkins-Zugang für lokale Lieferung |
| Freie Ports; bei mehreren Arbeitsbereichen eigener Projektname | Fachwerkzeuge, Originalmodelle und Prüfumgebung vorbereiten | INT-/PROD-Adressen und Zugänge |
| Gewünschte lokale Einstellungen | Eigene Dienste starten, MCP-Einträge erstellen und synthetisch prüfen | Publikationsstack und fachliche Freigaben |

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

`init` klont keine Repositories und publiziert keine Geschäftsdaten. Für den
Erststart sind weder Host-Java/Gradle noch Fach-MCP-Quellcheckouts oder
Jenkins-Zugangsdaten nötig. Der Dev-Stack-Checkout muss vorhanden sein; seine
Publikationsumgebung wird erst für eine spätere Lieferung benötigt.

Bei Erfolg meldet `init` `ready: true`. Prüfe bei `doctor`
`initialization.ready`, die beiden MCP-Verbindungen und GRETL auf Fehler;
der Exit-Code allein bestätigt keine Bereitschaft.
[Die vollständige Einrichtung](docs/anwenderhandbuch.md#installation-und-konfiguration)
erklärt die Ausgaben und trennt eigene Vorbereitung von automatischen Schritten.

## Ersten Auftrag starten

Für fachliche Aufträge das **quellcodefreie Betriebspaket** verwenden. Es enthält
dieselben drei Skills für Codex Desktop/CLI und OpenCode Desktop/CLI, eigene
Betriebsanweisungen und zwei MCP-Einträge: `datenportal_integrator` und den
begrenzten Dateihelfer `datenportal_workspace`. Allgemeine Shell und
Implementierungsquellen stehen dort nicht zur Verfügung.

Das Paket wird mit jeder neuen Image-Version als CI-Artefakt samt SHA-256
bereitgestellt und liegt im Image unter `/opt/datenportal-agent/workspace`.
Betreiber installieren mit `workspace install <absoluter Zielordner>` aus dem
Image oder entpacken das zugehörige Archiv. Danach im Betriebsordner `init` und
`doctor` ausführen. [Installation und Updates](docs/anwenderhandbuch.md#quellcodefreies-betriebspaket).

Den Betriebsordner als Projekt in Codex oder OpenCode öffnen und die von
`init` eingerichtete MCP-Verbindung prüfen. In Codex muss das Projekt als
vertrauenswürdig gelten. Für Codex CLI: `./bin/datenportal-agent codex`.

Skill auswählen und einen konkreten Auftrag formulieren, beispielsweise:

```text
$datenportal-themenintegrator
Prüfe die angehängte CSV für die Organisation agi. Erkläre Spalten,
fehlende Werte und mögliche Typfehler. Zeige mir den HTML-Prüfbericht
und warte danach auf meine fachliche Entscheidung.
```

In OpenCode beginnt derselbe Prompt mit `@datenportal-themenintegrator`.
Die drei Skills verwenden denselben Integrator-MCP und werden gemeinsam mit
diesem Checkout unter `.agents/skills/` bereitgestellt:

| Auftrag | Skill und Beispiel |
|---|---|
| Thema, CSV-Prüfung oder Metadatenänderung | [datenportal-themenintegrator](skills/datenportal-themenintegrator/SKILL.md): „Prüfe diese CSV und warte nach dem Bericht.“ |
| Eigenständiges Validierungsmodell | [datenportal-validierungsmodell](skills/datenportal-validierungsmodell/SKILL.md): „Leite für diese CSV und ihr Datenblatt ein Modell ab; publiziere nichts.“ |
| Eigenständige Organisation | [datenportal-organisation](skills/datenportal-organisation/SKILL.md): „Bereite eine Organisation mit diesen Teams und dieser Dienststelle vor; lege kein Thema an.“ |

In Codex den jeweiligen Namen mit `$`, in OpenCode mit `@` auswählen.
Bei Themenintegration mit ausdrücklich beauftragtem Modell lädt der Hauptskill
die Modellanleitung hinzu und verwendet denselben Vorgang weiter.
[Neun Anwendungsszenarien](docs/anwenderhandbuch.md#mit-dem-agenten-arbeiten)
zeigen erwartete Ergebnisse, menschliche Entscheidungen und kopierbare Prompts.
Neue CSV-Validierungsmodelle verwenden das kommentarfreie Profil `SO-DATA`;
weitere Profile sind `CORE` und `SO-GIS`.

## Weiterarbeiten und aktualisieren

Normale Starts verwenden das gespeicherte Agent-Image. Ein Update erfolgt
bewusst; lokale Konfiguration und Fachwerkzeug-Pins bleiben erhalten:

```sh
./bin/datenportal-agent --update-agent --version
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

Danach bestehende Client-Verbindungen neu starten. Für die Bereinigung alter
Konfigurationen und gesonderte Werkzeugupdates siehe
[Betrieb und Updates](docs/anwenderhandbuch.md#betrieb-und-updates).

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Einrichtung, Anwendungsszenarien, Berichte und Publikation.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Architektur, Schnittstellen, Entwicklungsmodi, Tests und Image-Veröffentlichung.
- [Abnahmestand](docs/abnahme.md): tatsächlich geprüfte Revisionen und Images, simulierte Fälle und offene Nachweise.

`--runtime docker-build`, `--runtime local`, Docker-stdio und GRETL `ephemeral`
bleiben für Entwicklung und isolierte Tests verfügbar. Ihre Verwendung steht
im Entwicklerhandbuch. Der Standardbetrieb benötigt keinen lokalen Agent-Build.
