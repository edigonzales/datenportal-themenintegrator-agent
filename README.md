# Datenportal-Themenintegrator

Java-25-Workflow für Themenanlieferung, INTERLIS-Modellierung und die Anlage neuer Organisationen. CLI und stdio-MCP verwenden denselben ausführbaren JAR und speichern Prüfstände, menschliche Freigaben und externe Laufkennungen lokal.

Für den lokalen Gesamtstack reichen Integrator, Themenrepo und Dev-Stack als
Checkouts. `../datenportal-dev-stack/scripts/up.sh` ist der gemeinsame
vollständige Start: Secrets, Garage, erfolgreicher Seed, Erstpublikation und
Portal. Der Jenkins-Quellcheckout ist nur für lokale Image-Builds erforderlich.
Passende laufende Instanzen werden weiterverwendet.

Das lokale Konfigurationsbeispiel verwendet drei dauerhafte Compose-Dienste: Datenblatt-MCP und `interlis-mcp` über HTTP sowie einen eigenen GRETL-Prüfcontainer aus dem Jenkins-Image. Fehlende Dienste starten automatisch; passende laufende Instanzen und der Gradle-Daemon werden wiederverwendet. Fach-MCP-Checkouts und lokale Fach-MCP-Builds sind nicht erforderlich. Der Launcher baut und startet den Java-Kern standardmässig in Docker; ein Host-JDK 25 ist optional. Bestehende stdio-/ephemeral-Konfigurationen bleiben unterstützt. Fachliche Änderungen im Themenrepo werden als Kandidaten geprüft und erst nach Freigabe übernommen.

Die Setup-Befehle erzeugen eine ignorierte `compose.override.yaml` mit den ausgewählten Image-Identitäten, Ports und lokalen Mounts. Compose startet keine neueren Images. Dienste bleiben nach dem Ende des Integrators verfügbar; `docker compose down` hält sie an und erhält den Gradle-Cache. Jenkins und sein Home gehören weiterhin zum Dev-Stack.

## Einstieg

Docker mit Compose genügt für Build und Java-Betrieb. macOS benötigt Docker
Desktop ab 4.34 mit aktiviertem **Settings → Resources → Network → Enable host
networking**; Linux verwendet den lokalen Docker Engine. Der Launcher verwendet
Host-Networking und den lokalen Docker-Socket. Remote-Docker-Kontexte werden
wegen der lokalen Dateien abgewiesen.

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
./bin/datenportal-agent serve
./bin/datenportal-agent gradle test jar spotlessCheck
./bin/datenportal-agent gradle integrationTest
```

`init` erstellt nur eine fehlende Standardkonfiguration, bereitet die festgelegten
Werkzeuge vor und prüft echte Datenblatt-/Modell-/CSV-Aufrufe mit synthetischen
Daten in einer privaten Arbeitskopie. Der Publikationsstack wird dabei nicht
initialisiert. Wiederholung verwendet aktuelle Prüfnachweise; `--update-tools`
wählt Werkzeugstände bewusst neu, `--skip-smoke` lässt den Status unvollständig.

Dateien im gemeinsamen Checkout-Elternordner sind unter ihren absoluten Pfaden
verfügbar. Weitere Verzeichnisse vor dem Befehl mit `--mount-ro /eingang` oder
`--mount-rw /arbeitsordner` einbinden. `DATENPORTAL_FORWARD_ENV` nennt ausdrücklich
weitergereichte Zugangsdatenvariablen. Werte gehören weder in Argumente noch Git.
Lokales JDK 25 für Entwicklung: `./bin/datenportal-agent --runtime local doctor`.

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Einrichtung, drei Vorgangsarten, Freigaben, Korrekturen und Wiederaufnahme.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Komponenten, Schnittstellen, Migration, Erweiterungen und Tests.
- [Abnahmestand](docs/abnahme.md): tatsächlich durchgeführte, simulierte und offene Prüfungen.
- [Gemeinsamer Skill](skills/datenportal-themenintegrator/SKILL.md): Agent-Regeln für Codex und OpenCode.

`java -jar build/libs/datenportal-integrator.jar schema <operation>` zeigt das verbindliche Argumentenschema. `call <operation> --args-file <datei.json>` verwendet dieselbe Operation wie MCP. `serve` startet den MCP über stdio.

Alte Vorgänge benötigen `migrate_run`; frühere Freigaben werden historisch übernommen. Python-Konverter werden ausdrücklich als migrationsbedürftig gemeldet.
