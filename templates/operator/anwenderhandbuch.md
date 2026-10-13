# Anwenderhandbuch für das Betriebspaket

## Installation und Konfiguration

Das Paket enthält ausschliesslich Betriebsdateien. Das Docker-Image stellt Java,
Integrator und Fachwerkzeugadapter bereit. Die beiden fachlichen Checkouts für
Themenrepo und Dev-Stack werden über ihre vorhandenen Schnittstellen verwendet.

Betreiber installieren aus dem veröffentlichten Image in einen leeren, eingebundenen
Zielordner mit `workspace install <absoluter Zielordner>`. `workspace update` ersetzt
nur unveränderte Paketdateien. Änderungen, bestehende Konfiguration, Eingänge,
Zugangsdaten und Vorgänge werden nicht überschrieben oder automatisch verschoben.

Ein bewusstes Update startet der Betreiber im Betriebsordner mit:

```sh
./bin/datenportal-agent --update-agent workspace update "$PWD"
./bin/datenportal-agent init
```

Ohne Update bleibt das Paket an seine veröffentlichte Image-Version gebunden.

`bin/datenportal-agent init` erstellt fehlende Konfiguration, richtet Fachwerkzeuge
ein und schreibt die beiden Client-Konfigurationen. `doctor` prüft technische
Bereitschaft und Publikationszugänge getrennt. Eine Konfigurationsprüfung ersetzt
keinen tatsächlichen Client-Nachweis. Widersprüchliche Codex-Sandbox-Einstellungen
müssen vom Betreiber bewusst abgeglichen werden; keine uneingeschränkte Umgehung.

## Codex und OpenCode

Öffne den Betriebsordner als Projekt. In Codex muss die Projektkonfiguration als
vertrauenswürdig gelten. Die CLI kann mit `bin/datenportal-agent codex` starten.
In OpenCode wird der Fachagent `datenportal` ausgewählt. Nach Änderungen beide
MCP-Verbindungen und bestehende Sitzungen neu starten.

Die zwei registrierten Server sind `datenportal_integrator` und
`datenportal_workspace`. Beide stammen aus derselben Docker-Runtime.
Die drei Skills stehen unter `.agents/skills/`; Codex verwendet `$`, OpenCode `@`.
In OpenCode: Projekteinstellungen → Extensions → MCPs beziehungsweise Skills.
Bei der gebündelten OpenCode-CLI immer die Projekt-Location prüfen; `mcp list`
kann die Default-Location des Hintergrundservers anzeigen. Die Client-Regeln
wurden mit OpenCode 2.0.27 und Codex CLI 0.160.0 beziehungsweise Desktop-Engine
0.162.0-alpha.17.2 geprüft. Ein Konfigurationsfehler ist kein Anlass, die
Sperren abzuschalten.

Allgemeine Shell und Implementierungsquellen stehen im Fachbetrieb nicht zur
Verfügung. Benötigte Dateien über `datenportal_workspace.read_file` lesen und
konkrete Verzeichnisse mit `list_files` auflisten. Öffentliche Argumentverträge
liefert `datenportal_integrator.operation_schema`. Bei MCP-Ausfall nennt der Agent
die Diagnose; der administrative CLI-Fallback `schema`/`call` bleibt dem Betreiber.

## Fachliche Abläufe

- [Themenintegration](../skills/datenportal-themenintegrator/SKILL.md): CSV prüfen,
  vorhandenes Datenblatt verwenden, auf ausdrücklichen Auftrag modellieren und
  nach echten Freigaben liefern.
- [Validierungsmodell](../skills/datenportal-validierungsmodell/SKILL.md):
  eigenständiges Modell aus bestätigter CSV und Datenblatt, ohne Datenpublikation.
- [Organisation](../skills/datenportal-organisation/SKILL.md): bestätigte
  Organisation, Teams und Office vorbereiten; fehlendes Office im Thema bleibt dort.

[Eingangsdateien](agent/eingangsdateien.md) beschreibt den unveränderten
Anhangimport vor dem ersten Integrator-Aufruf. Liegt ein Original ausserhalb der
eingebundenen Pfade, muss der Betreiber dessen Verzeichnis mit `--mount-ro`
einbinden. Originaldateien werden nicht aus Vorschauen rekonstruiert.

CSV-, Metadaten-/Modell- und Publikationsfreigaben erfordern tatsächliche Antworten
zum gezeigten HTML-Prüfstand. Beobachtungen sind keine bestätigten Fachregeln.
Vorhandene Vorgänge und unklare externe Läufe werden mit ihren Kennungen fortgesetzt.
[Lieferung und Wiederaufnahme](agent/lieferung-und-wiederaufnahme.md)

## Java-Konverter

Bei einer beauftragten Umformung [CSV-Konverter](agent/csv-konverter.md) lesen.
Rezepte und Java-/JUnit-Dateien unter `topics/<org>/<id>/` mit `write_file`
vorbereiten. Zum Ändern bestehender Dateien den SHA-256 aus `read_file` übergeben.
Kompilieren, Testen und Umformen übernimmt das Integratorwerkzeug `transform`.
Keine Eingangsdateien, Datenblätter, freigegebenen Modelle oder Vorgangszustände
mit dem Dateiwerkzeug überschreiben.
