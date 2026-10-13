# Datenportal-Themenintegrator

Der Agent unterstützt CSV-, XTF- und XLSX-Lieferungen, INTERLIS-Validierungsmodelle
und neue Organisationen. Er erstellt prüfbare Kandidaten und HTML-Berichte;
fachliche Änderungen und Publikationen benötigen deine Freigabe.

## Mit dem Betriebspaket starten

Für die Facharbeit verwendest du einen **quellcodefreien Betriebsordner**.
Der Integrator läuft im veröffentlichten Docker-Image. Du brauchst Docker mit
Compose, Git, Bash, Codex oder OpenCode sowie Themenrepo und Dev-Stack.
Ein Quellcheckout dieses Integrators oder Host-JDK ist nicht erforderlich.

Die [Schritt-für-Schritt-Anleitung](docs/anwenderhandbuch.md#installation-und-konfiguration)
führt vom Anlegen der Ordner bis zur ersten CSV:

1. Docker und Client vorbereiten.
2. Themenrepo und Dev-Stack unter `~/datenportal/` klonen oder vorhandene Checkouts verwenden.
3. `~/datenportal/datenportal-betrieb` erstellen und mit dem ausdrücklich geladenen `latest`-Image installieren.
4. Bei abweichenden Pfaden die Konfiguration anpassen.
5. Im Betriebsordner `./bin/datenportal-agent init` und `./bin/datenportal-agent doctor` ausführen.
6. **Den Betriebsordner** in Codex oder OpenCode öffnen.

Die Anleitung enthält alle kopierbaren Befehle, einschliesslich `mkdir`,
Docker-Installation und des macOS-Pfads zur mit OpenCode Desktop gelieferten CLI.
Voraussetzung ist eine erfolgreiche [Veröffentlichung des Betriebspakets](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/workflows/agent-image.yml).
Danach bleibt die installierte Version bis zum bewussten Update festgehalten.
Das gleich versionierte Paket wird auch als CI-Artefakt samt SHA-256 angeboten.

## Ersten Auftrag starten

Im Betriebsordner stehen **drei Skills und zwei MCP-Server** bereit:
`datenportal_integrator` führt fachliche Schritte aus; `datenportal_workspace`
übernimmt Eingangsdateien und zulässige Dateioperationen. Datenblatt- und
INTERLIS-MCP verwendet der Integrator intern. In OpenCode den Agenten
`datenportal` wählen.

| Auftrag | Skill |
|---|---|
| Themenintegration, CSV-Prüfung, Daten-/Metadatenlieferung | [datenportal-themenintegrator](skills/datenportal-themenintegrator/SKILL.md) |
| Eigenständiges Validierungsmodell | [datenportal-validierungsmodell](skills/datenportal-validierungsmodell/SKILL.md) |
| Eigenständige Organisation | [datenportal-organisation](skills/datenportal-organisation/SKILL.md) |

In Codex den Skill mit `$`, in OpenCode mit `@` auswählen. Für die erste CSV
[Eingangsordner erstellen und Datei bereitstellen](docs/anwenderhandbuch.md#erste-csv-bearbeiten).
Beispielauftrag nach Auswahl des Themenintegrations-Skills:

```text
Integriere eingang/Fahrzeugbestand.csv für mfk, Thema ch.so.fahrzeugbestand2025.
Verwende das vorhandene Datenblatt im Themenrepo, erstelle ein
INTERLIS-Validierungsmodell und liefere nach meinen Freigaben lokal.
Prüfe CSV, Modell, Portal und Downloads.
```

Datei, Organisation und Identifier anpassen. Modellierung innerhalb der
Themenintegration bleibt im selben Vorgang. Prüfe die Berichte und erteile die
angeforderten Freigaben. Die lokale Lieferung benötigt zusätzlich den
Publikationsstack und Lieferzugänge; `init` allein startet diesen Stack nicht.

## Weiterarbeiten und aktualisieren

Denselben Betriebsordner für weitere Themen und Sitzungen verwenden.
[Wiederaufnahme und Updates](docs/anwenderhandbuch.md#wiederaufnahme-und-updates)
erklärt vorhandene Vorgänge, Paketkonflikte und zusätzliche Mounts.
Bei Standardpfaden im Betriebsordner:

```sh
./bin/datenportal-agent --update-agent workspace update "$PWD"
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

Danach beide MCP-Verbindungen und bestehende Sitzungen neu starten.

## Entwicklung

Nur für Änderungen am Integrator selbst dessen Quellrepository klonen.
[Entwicklung einrichten](docs/entwicklerhandbuch.md#entwicklungscheckout-einrichten)
beschreibt den separaten Arbeitsbereich und die Build-/Testbefehle.

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Installation, vier Client-Startwege und fachliche Abläufe.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Architektur, Schnittstellen, Tests und Image-Veröffentlichung.
- [Abnahmestand](docs/abnahme.md): tatsächlich geprüfte Stände, simulierte Fälle und offene Nachweise.
