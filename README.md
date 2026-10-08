# Datenportal-Themenintegrator

Java-25-Workflow für Themenanlieferung, INTERLIS-Modellierung und die Anlage neuer Organisationen. CLI und stdio-MCP verwenden denselben ausführbaren JAR und speichern Prüfstände, menschliche Freigaben und externe Laufkennungen lokal.

Für den lokalen Gesamtstack reichen Integrator, Themenrepo und Dev-Stack als
Checkouts. `../datenportal-dev-stack/scripts/up.sh` ist der gemeinsame
vollständige Start: Secrets, Garage, erfolgreicher Seed, Erstpublikation und
Portal. Der Jenkins-Quellcheckout ist nur für lokale Image-Builds erforderlich.
Passende laufende Instanzen werden weiterverwendet.

Das lokale Konfigurationsbeispiel verwendet drei dauerhafte Compose-Dienste: Datenblatt-MCP und `interlis-mcp` über HTTP sowie einen eigenen GRETL-Prüfcontainer aus dem Jenkins-Image. Fehlende Dienste starten automatisch; passende laufende Instanzen und der Gradle-Daemon werden wiederverwendet. Fach-MCP-Checkouts und lokale Fach-MCP-Builds sind nicht erforderlich; lokal genügt JDK 25. Bestehende stdio-/ephemeral-Konfigurationen bleiben unterstützt. Fachliche Änderungen im Themenrepo werden als Kandidaten geprüft und erst nach Freigabe übernommen.

Die Setup-Befehle erzeugen eine ignorierte `compose.override.yaml` mit den ausgewählten Image-Identitäten, Ports und lokalen Mounts. Compose startet keine neueren Images. Dienste bleiben nach dem Ende des Integrators verfügbar; `docker compose down` hält sie an und erhält den Gradle-Cache. Jenkins und sein Home gehören weiterhin zum Dev-Stack.

## Einstieg

```sh
export JAVA_HOME="/pfad/zum/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew test jar
cp config/local.example.toml config/local.toml
# Pfade und Umgebungen in config/local.toml anpassen.
java -jar build/libs/datenportal-integrator.jar setup-java
java -jar build/libs/datenportal-integrator.jar setup-tools
java -jar build/libs/datenportal-integrator.jar setup-mcps
java -jar build/libs/datenportal-integrator.jar setup-gretl
# Optional vorab starten; ansonsten startet der erste Werkzeugaufruf bei Bedarf.
docker compose up -d
java -jar build/libs/datenportal-integrator.jar doctor
```

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Einrichtung, drei Vorgangsarten, Freigaben, Korrekturen und Wiederaufnahme.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Komponenten, Schnittstellen, Migration, Erweiterungen und Tests.
- [Abnahmestand](docs/abnahme.md): tatsächlich durchgeführte, simulierte und offene Prüfungen.
- [Gemeinsamer Skill](skills/datenportal-themenintegrator/SKILL.md): Agent-Regeln für Codex und OpenCode.

`java -jar build/libs/datenportal-integrator.jar schema <operation>` zeigt das verbindliche Argumentenschema. `call <operation> --args-file <datei.json>` verwendet dieselbe Operation wie MCP. `serve` startet den MCP über stdio.

Alte Vorgänge benötigen `migrate_run`; frühere Freigaben werden historisch übernommen. Python-Konverter werden ausdrücklich als migrationsbedürftig gemeldet.
