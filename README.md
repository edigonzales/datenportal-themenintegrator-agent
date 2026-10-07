# Datenportal-Themenintegrator

Java-25-Workflow für Themenanlieferung, INTERLIS-Modellierung und die Anlage neuer Organisationen. CLI und stdio-MCP verwenden denselben ausführbaren JAR und speichern Prüfstände, menschliche Freigaben und externe Laufkennungen lokal.

Der Integrator verwendet Datenblatt-MCP und `interlis-mcp` als veröffentlichte Docker-Images über stdio sowie GRETL, Jenkins und den Dev-Stack. Fach-MCP-Checkouts und lokale Fach-MCP-Builds sind nicht erforderlich. Deren Quellen bleiben unverändert. Fachliche Änderungen im Themenrepo werden als Kandidaten geprüft und erst nach Freigabe übernommen.

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
java -jar build/libs/datenportal-integrator.jar doctor
```

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Einrichtung, drei Vorgangsarten, Freigaben, Korrekturen und Wiederaufnahme.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Komponenten, Schnittstellen, Migration, Erweiterungen und Tests.
- [Abnahmestand](docs/abnahme.md): tatsächlich durchgeführte, simulierte und offene Prüfungen.
- [Gemeinsamer Skill](skills/datenportal-themenintegrator/SKILL.md): Agent-Regeln für Codex und OpenCode.

`java -jar build/libs/datenportal-integrator.jar schema <operation>` zeigt das verbindliche Argumentenschema. `call <operation> --args-file <datei.json>` verwendet dieselbe Operation wie MCP. `serve` startet den MCP über stdio.

Alte Vorgänge benötigen `migrate_run`; frühere Freigaben werden historisch übernommen. Python-Konverter werden ausdrücklich als migrationsbedürftig gemeldet.
