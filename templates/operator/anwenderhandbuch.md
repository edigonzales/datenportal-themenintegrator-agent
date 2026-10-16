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

Die lokale CSV-Vorprüfung verwendet ilivalidator direkt. Jenkins prüft vor der Publikation eigenständig mit dem bestehenden GRETL-Task `validateDeliveredCsv`. Der Themenblock konfiguriert Semikolon, Anführungszeichen, UTF-8, Kopfzeile und das Modellverzeichnis. Es gibt keinen zusätzlichen CSV-Validator- oder Copy-Task und keine zusätzliche CSV-Byte- oder Modell-Hash-Prüfung im Gradle-Block. Es gibt keinen Bundle-, Image- oder Bibliotheksabgleich zwischen beiden Prüfungen. Daten- und Modellprüfsummen binden Freigaben weiterhin an den gezeigten fachlichen Stand.

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

## Fachliche Arbeit

Die drei Skills führen durch Themenintegration, eigenständige Modellierung und Organisationsanlage. Öffentliche Argumente mit `operation_schema` erfragen. Chat-Anhänge mit `datenportal_workspace.import_attachment` unverändert übernehmen; nur drei gleiche SHA-256-Prüfsummen bestätigen die Kopie.

Lokale CSV-Konverter sind fachliche Java-Arbeitsprodukte. Sie werden durch den Integrator mit JDK 21 kompiliert und getestet; keine Integrator-Quellen bearbeiten. CSV-/Modell-/Metadatenänderungen prüfen und nur tatsächliche menschliche Freigaben protokollieren.

Bei bestehenden Modellvorgängen ersetzt `validate_model` einen unveränderten alten Integrator-Taskblock durch diese Konfiguration als Kandidat. Modell und Datenblatt bleiben unverändert. Anschliessend die vollständigen Prüfberichte aktualisieren und den geänderten Stand freigeben lassen; es erfolgt keine automatische Übernahme oder Lieferung.

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

## Freigaben bei Paketupdates

Freigaben beziehen sich auf die fachlichen Dateien und den Inhalt der Prüfregeln,
Modelle und Validatoren. Paketversion, Installationspfad, JAR-Zeitstempel,
Integrator-Oberfläche, Beispieldokumentation und Timeouts entwerten sie nicht.
Änderungen an Daten, Modell, Metadaten, Prüfregeln oder Validator-Inhalten erfordern
weiterhin die passende erneute Prüfung und menschliche Freigabe.

`workspace update` überführt bisher aktuelle Freigaben aus Version 0.2.20 einmalig
auf das inhaltsbasierte Format. Dafür muss das bisherige Paket noch vorhanden
sein, sein alter Fingerprint zum Vorgang passen und sein Prüfinhalt mit dem neuen
Paket übereinstimmen. Es werden keine fehlenden oder veralteten Freigaben erzeugt.
Aussage und Zeitpunkt der menschlichen Freigabe sowie historische Fingerprints
bleiben protokolliert. Der Updatebericht nennt nicht überführbare Vorgänge;
fehlende Belege oder geänderte Regeln erfordern eine neue Prüfung/Freigabe.

## Nach dem PR-Merge

Mit `sync_repository(run_id, environment)` den Merge prüfen und das konfigurierte
Themenrepository synchronisieren; auch beim Abschluss ohne weitere Lieferung.
Vor neuen Seed-/Lieferungsstarts mit vorhandenem PR erfolgt dies automatisch.
`status` bleibt lesend und zeigt `repository_sync`. Bei Konflikten stoppt die
Fortsetzung; lokale Änderungen nicht pauschal verwerfen. Einen unterbrochenen
Aufruf mit derselben Vorgangs-ID wiederaufnehmen und das Journal erhalten.

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

## Datenblatt, Modellquelle und Repository-Ziel

XLSX → Datenblatt → aktuelle exportierte XTF → Modell. CSV-Beobachtungen und bestätigte Fachregeln ergänzen den XTF-Vertrag. `metadata(export_xtf)` prüft Identifier und Datentypen und merkt die XTF automatisch unter `<organisation>/<identifier>/meta-<identifier>.xtf` im konfigurierten Themenrepo vor. Bei ausgewähltem Repository-Datenblatt bleibt der Pfad erhalten; einen gewünschten Dateinamen über `arguments.repository_filename` angeben. Wiederholte Exporte ersetzen den Kandidaten. Kein manuelles Registrieren, erneutes Anhängen oder Suchen des aktuellen Exports. `status.metadata_source` zeigt Quelle und nächsten Schritt. Erst nach Freigabe mit `apply_local_changes` übernehmen. Unveränderte Fehler als konkrete Blocker melden; keine Diagnose-Kopien oder Pfadvarianten ausprobieren.

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

### Paketwechsel ohne Vorgangsmigration

`/pfad/zum/neuen-paket/bin/datenportal-agent workspace update /absoluter/pfad/betrieb --preserve-run-state`
aktualisiert ausschließlich die verwalteten Betriebsdateien und den Paketverweis.
Die automatischen Freigabemigrationen werden dabei ausgelassen; sämtliche
Vorgangszustände bleiben unverändert. Vor einem Neuladen der MCP-Verbindungen
laufende Aufrufe auslaufen lassen. Ein Paketwechsel führt weder `restore` aus
noch repariert oder genehmigt er fachliche Vorgänge. Serienausgaben und die
Diagnose gesperrter Wiederherstellungen sind in den ausgelieferten Referenzen
zu Metadaten und Wiederaufnahme beschrieben.

### Manueller Seed nach Repository-Reparatur

Ein erfolgreicher manueller Seed kann mit
`adopt_seed(run_id, environment, build_url)` im bestehenden Vorgang übernommen
werden. Die vollständige Build-URL genügt; keine Queue-ID aus der Buildnummer
ableiten. `deliver` fragt einen laufenden Seed ab und liefert erst nach Erfolg
und den weiterhin nötigen Freigaben. Ohne zugeordneten manuellen Seed startet
`deliver` einen eigenen Seed. Unbestätigte Starts müssen zuerst geklärt werden.

`delivery_submitting` bezeichnet einen Datenupload, auch wenn dieser mit 404
endet. Dann den Datenlieferlauf klären, keinen Seed zuordnen oder blind erneut
liefern. `status.external_runs` zeigt Aktion, Referenz und nächsten Schritt.
Manuell veränderte oder entfernte Dateien nicht mit alten Kandidaten
überschreiben; Korrekturen neu vorbereiten, prüfen und gegebenenfalls freigeben.

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
