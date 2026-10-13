# Datenportal-Betriebspaket

**Diesen Ordner in Codex oder OpenCode öffnen.** Er enthält drei Skills,
Anleitungen und Einstellungen; Java und Integrator laufen im Docker-Image.
Das Integrator-Quellrepository und ein Host-JDK sind für den Betrieb nicht nötig.

Neu hier? Die vollständige [Installation und Konfiguration](docs/anwenderhandbuch.md#installation-und-konfiguration)
zeigt auch das Erstellen dieses Ordners: Voraussetzungen, fachliche Repositories,
`mkdir`, Installation aus `latest`, Konfiguration, `init` und `doctor`.
Bereits vorhandene Checkouts werden weiterverwendet. Alle Terminalbefehle führt
der Betreiber aus; der Fachagent verwendet seine MCP-Werkzeuge.

Bei eingerichteten Standardpfaden im Betriebsordner:

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

Erwartet werden `ready=true`, `smoke_current=true` und fehlerfreie Werkzeugdiagnosen.
[Client öffnen](docs/anwenderhandbuch.md#6-betriebsordner-im-client-öffnen) beschreibt
Codex Desktop/CLI und OpenCode Desktop samt gebündelter macOS-CLI getrennt.
In OpenCode den Agenten `datenportal` auswählen. Beide Clients verwenden
`datenportal_integrator` und `datenportal_workspace`. Datenblatt- und INTERLIS-MCP
bleiben intern; drei Skills stehen unter `.agents/skills/` zur Auswahl.

Für [die erste CSV](docs/anwenderhandbuch.md#erste-csv-bearbeiten) den Eingangsordner
erstellen, die Originaldatei unverändert bereitstellen und den Themenintegrations-Skill
wählen: in Codex `$datenportal-themenintegrator`, in OpenCode `@datenportal-themenintegrator`.
Datei, Organisation und Identifier in diesem Beispiel anpassen:

```text
Integriere eingang/Fahrzeugbestand.csv für mfk, Thema ch.so.fahrzeugbestand2025.
Verwende das vorhandene Datenblatt im Themenrepo, erstelle ein
INTERLIS-Validierungsmodell und liefere nach meinen Freigaben lokal.
Prüfe CSV, Modell, Portal und Downloads.
```

Die lokale Lieferung braucht zusätzlich Jenkins, Portal und Lieferzugänge.
[Wiederaufnahme und Updates](docs/anwenderhandbuch.md#wiederaufnahme-und-updates)
erklärt gespeicherte Vorgänge, Versionsbindung und den Umgang mit Paketkonflikten.
