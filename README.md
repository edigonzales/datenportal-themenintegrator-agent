# datenportal-themenintegrator-agent

Themenintegration für Codex und OpenCode: CSV erklären und prüfen, Datenblätter
über den bestehenden Java-MCP bearbeiten, XTF und Office-Referenzen validieren,
Zwischenstände als HTML prüfen und über den vorhandenen Jenkins publizieren.
Der Workflow speichert Dateistände und menschliche Freigaben lokal.

## Dokumentation

- [Anwenderhandbuch](docs/anwenderhandbuch.md): Einrichtung, Zugangsdaten,
  vollständiger Probelauf, Freigaben, Serien, Teillieferungen und Fehlerbehandlung.
- [Entwicklerhandbuch](docs/entwicklerhandbuch.md): Architektur, Schnittstellen,
  Zustände, Erweiterungen, Tests und Abnahmestand.
- [Agent-Skill](skills/datenportal-themenintegrator/SKILL.md): Ablauf für den
  Agenten; [AGENTS.md](AGENTS.md) enthält die Entwicklungsregeln.

## Schnellstart

Voraussetzungen: Python 3.11+, `uv`, Java für ilivalidator, JDK 25 für den
Datenblatt-MCP, Docker Compose, Git und für GitHub-PRs die angemeldete CLI `gh`.
Schwester-Checkouts für Themenrepo, Dev-Stack und Datenblatt-Editor müssen
vorhanden oder in der lokalen Konfiguration entsprechend ersetzt sein.
Alle folgenden Befehle im Integrator-Repo ausführen:

```sh
uv sync --locked
cp -n config/local.example.toml config/local.toml
uv run python scripts/setup-tools.py
```

`config/local.toml` ist eine ignorierte lokale Datei;
[Vorlage](config/local.example.toml) und [Schema](config/settings.schema.json)
beschreiben die Optionen. Checkout-Pfade und Umgebungsadressen anpassen.
Das Integrator-Profil enthält **Namen von Credential-Variablen**, deren Werte
der startende Prozess übergibt. Die
[Einrichtungsanleitung](docs/anwenderhandbuch.md#zugangsdaten) erklärt die
unterschiedliche Ablage für CLI und Desktop.

Falls kein Java-Datenblatt-MCP läuft, in einem separaten Terminal starten und
`/pfad/zu/jdk-25` durch das eigene JDK-Verzeichnis ersetzen:

```sh
uv run python scripts/start-datasheet.py --java-home /pfad/zu/jdk-25
```

```sh
uv run datenportal-integrator doctor
```

Codex CLI mit expliziter MCP-Konfiguration starten:

```sh
uv run python scripts/codex-integrator.py
```

Für Codex Desktop den [benutzerlokalen MCP-Eintrag](docs/anwenderhandbuch.md#codex-desktop)
einrichten; für OpenCode im Integrator-Repo `opencode mcp list` und danach
`opencode` ausführen. Beide nutzen denselben Skill und Workflow-Kern.

Beispielauftrag mit angehängten lokalen Originaldateien:

> Verwende den Themenintegrator-Skill. Integriere diese CSV für die Organisation
> AGI als neues Thema. Frage fehlende Zuordnungen nach und halte nach der
> CSV-Erklärung zur Freigabe an.

## Grenzen und Abnahme

Die technische Implementierung und sämtliche Integrator-Konfiguration liegen
in diesem Repo. Dev-Stack, Java-MCP, Jenkins-Plugin, GRETL und Portal werden über
ihre bestehenden Schnittstellen verwendet. Das Themenrepo behält seine Struktur;
fachliche Änderungen werden vorbereitet und nach Freigabe übernommen.

Am **6. Oktober 2026** bestanden 63 Tests mit aktiviertem Java-MCP und echtem
ilivalidator. Ein gekennzeichneter lokaler Test der Pilotserie
`ch.so.bevoelkerung.altersstruktur`, Ausgabe `2025`, bestätigte Seed, Publikation,
Fachmetadaten, Reload und CSV-/XLSX-/Parquet-Downloads. Die
[lokale Ausgabe](http://localhost:8081/series/ch.so.bevoelkerung.altersstruktur/issues/ch.so.bevoelkerung.altersstruktur_2025)
ist erreichbar, solange der entsprechende Stack läuft.

Die dialogische Abnahme mit einem Menschen in beiden Harnesses, ein echter
GitHub-PR mit menschlichem Merge und INT-/PROD-Läufe stehen noch aus.
Details und die Unterscheidung zwischen Simulation und echten Läufen stehen im
[Abnahmeabschnitt](docs/entwicklerhandbuch.md#abnahmestand).
