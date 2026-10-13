# Datenportal-Betriebspaket

Öffne diesen Ordner in **Codex Desktop/CLI** oder **OpenCode Desktop/CLI**.
Das Paket enthält die drei Skills und ihre Anleitungen. Die Implementierung läuft
im veröffentlichten Docker-Image; ein Entwicklungscheckout oder Host-JDK ist nicht
erforderlich.

Einrichtung und Prüfung sind Betreiberaufgaben:

```sh
./bin/datenportal-agent init
./bin/datenportal-agent doctor
```

Prüfe die fachlichen Repository- und Stackpfade in `config/local.toml`.
`init` legt die fehlende Datei aus der Vorlage an und erhält bestehende Einstellungen.
Zugangsdaten gehören in Umgebungsvariablen oder den Credential-Store.

Beide Clients verwenden `datenportal_integrator` und `datenportal_workspace`.
Der Datei-MCP übernimmt Anhänge unverändert und prüft ihre SHA-256-Prüfsummen.
Datenblatt- und INTERLIS-MCP werden intern vom Integrator verwendet.

In Codex `$datenportal-themenintegrator`, in OpenCode
`@datenportal-themenintegrator` auswählen. Beispiel:

```text
Integriere die angehängte CSV für <Organisation>, Thema <Identifier>.
Verwende das vorhandene Datenblatt im Themenrepo und leite ein
INTERLIS-Validierungsmodell ab. Liefere nach meinen Freigaben lokal
und prüfe CSV, Modell, Portal und Downloads.
```

[Anwenderhandbuch](docs/anwenderhandbuch.md)
