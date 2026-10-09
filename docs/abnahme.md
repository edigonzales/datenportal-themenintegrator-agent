# Abnahmestand

## Docker-Anlieferung und gemeinsamer Credential-Store vom 9. Oktober 2026

Tatsächlich auf macOS mit Docker Desktop geprüft:

- `./bin/datenportal-agent gradle test jar spotlessCheck`: 146 Unit-/Funktionstests,
  keine Fehler und keine übersprungenen Tests. Log:
  `.datenportal-integrator/delivery-unit.log`.
- `./bin/datenportal-agent gradle integrationTest`: alle 22 Integrationstests
  erfolgreich, ohne übersprungene Tests. Log:
  `.datenportal-integrator/delivery-verification-final.log` dokumentiert auch den
  vollständigen Wiederholungslauf nach der CSRF-Korrektur. Ein echter dauerhaft
  laufender stdio-MCP erhielt Zugangsdaten erst nach seinem Start. Er verwendete
  sie beim nächsten Aufruf, meldete ein abgewiesenes Token und akzeptierte die
  erneute Hinterlegung ohne Neustart. Die HTTP-Gegenstelle dieses zusätzlichen
  Tests ist synthetisch; die Fachwerkzeuge der übrigen Tests sind tatsächlich
  konfigurierte Dienste.
- OpenCode Beta `0.0.0-beta-19723`, GUI: ausschliesslich privates synthetisches
  Git-Projekt und Vorgang `1b53b2a34e824cd4b91822b080ce509a`. Die GUI bestätigte
  `credentials_source=local_store`; nach erneuter MCP-Verbindung führte sie
  echte `datenportal_integrator.status`-Aufrufe aus. Keine GUI-Freigabe und keine
  GUI-Lieferung. Nachweis:
  `.datenportal-integrator/delivery-acceptance/73663f0a-b5c8-4be6-a397-edd8cc1d8b64/gui-evidence.json`,
  GUI-Sitzung `ses_eded40fa0ffeUCqZxSGHL0QX7o`.
  Der erste MCP-Start überschritt das Beta-Startzeitlimit. Die installierte Beta
  normalisiert das vorhandene numerische Timeout nur für Katalog/Ausführung,
  nicht für den Start. Nach abgeschlossenem Build bestand das erneute Verbinden.
- `init` bestand erneut mit dem abschliessenden JAR und tatsächlichen
  Fachwerkzeugen, ohne reale Jenkins-Zugangsdaten. `ready=true`, aktueller
  synthetischer Smoke: `.datenportal-integrator/delivery-init.json`.
  Der normale laufende Stack wurde von `doctor` im Container als kompatibel
  erkannt; reine Modellierung und fehlende Publikationszugänge sind getrennt.
- `./bin/datenportal-agent acceptance delivery` vollständig bestanden, ohne
  Host-JDK und ohne Jenkins-Zugangsdaten aus der Hostumgebung. Der Host startete
  das eigene Compose-Projekt; zwei echte Docker-stdio-MCPs wurden aus den
  erzeugten Codex-/OpenCode-Konfigurationen gestartet, bevor der private Store
  hinterlegt wurde. Beide Clients wirkten am synthetischen Vorgang mit.
  Gültige CSV: frühes `validateThemenCsv`, identische geprüfte Modellbytes,
  erfolgreicher Jenkins-Lauf, Manifest, Portal und Downloads bestätigt.
  Ungültige CSV über die echte Jenkins-Schnittstelle: numerische Verletzung bei
  `Jahr`, `validateThemenCsv FAILED`, kein `preparePublicationWorkspace` und
  unveränderter erfolgreicher Release. IDs und Startzeiten von Jenkins,
  Downloads-Gateway und Garage blieben gleich. Eigene Container, Volumes und
  Zugangsdaten wurden entfernt (`cleanup-result.json`: `cleaned=true`).
  Nachweise und Prüfsummen:
  `.datenportal-integrator/delivery-acceptance/91984e1c-56ce-4553-b1f9-b02cd7f8be47/`.
  `report.json`, `manifest.json` und beide bereinigten Konsole-Logs bleiben
  archiviert; der rohe eingebettete API-Konsolentext wird nicht übernommen.

Simulierte beziehungsweise isolierte Fehlerfälle: Desktop-Mountalias,
Linux ohne Aliasübersetzung, Leerzeichen/Symlinks, fremder oder fehlender
Themenmount, unveränderte Container-Wiederverwendung, CSRF-Session-Cookies, teilweise gesetzte
Zugangsdaten, geänderte Jenkins-Adresse, Dateirechte, Symlinkabwehr, konkurrierende
Store-Änderungen, stdin-/Umgebungsübernahme und bereinigte Bootstrap-Logs.
Die vorhandenen Harness-/Launcher-Tests prüfen weiterhin erhaltene fremde
Einstellungen und ausdrückliche Umgebungsweitergaben.

Tatsächliche fehlgeschlagene Vorläufe werden nicht als erfolgreiche Negativtests
gewertet: zunächst eine falsche private Modellcache-Grenze, danach ein gelöschtes
Werkzeug-Image und eine zu wenig freie Docker-VM mit offline geschaltetem
Jenkins-Knoten. Der anschliessend versuchte private Jenkins-Home-Bind-Mount
scheiterte beim atomaren Schreiben unter VirtioFS (`Bad file descriptor`). Diese
Testanpassung wurde zurückgenommen; die Abnahme verwendet wieder das vorhandene
Jenkins-Volume-Konzept. Nach einem Abbruch verblieb eine leere Build-Sperre; sie
wurde erst nach Prüfung der wartenden Prozesse und fehlenden Build-Container
entfernt. Die isolierten Ressourcen dieser Vorläufe wurden bereinigt.
Ein weiterer Vorlauf zeigte den fehlenden administrativen Root-Build in der
minimalen Fixture; diese vorhandenen Build-Dateien werden nun mit übernommen.
Nach erfolgreicher Erstpublikation deckte die echte Abnahme ausserdem den fehlenden
Session-Cookie im bisherigen Java-CSRF-Ablauf auf. Der HTTP-Client wurde korrigiert
und die Cookie-/Crumb-Bindung mit einer isolierten HTTP-Gegenstelle regressionsgeprüft.
Der erste tatsächliche Datenjob erreichte danach `validateThemenCsv`, scheiterte
aber am endungslosen Jenkins-Dateiparameter. Die erzeugte Task verwendet jetzt
einen Gradle-`Copy`-Task und direkte Byteprüfung. Im nächsten Datenjob bestand
diese frühe Validierung; der nachgelagerte Publikationsvalidator fand das lokale
Modell noch nicht im Image-Modellverzeichnis. Die erzeugte Task ergänzt deshalb
den Themenordner für diesen vorhandenen Validator. Ein Regressionslauf zeigte,
dass minimale Prüfprojekte diesen optionalen Publikations-Task nicht besitzen;
die Ergänzung wird nur bei vorhandener Task angewandt. Fehlerlog:
`.datenportal-integrator/delivery-verification-optional-task-failed.log`.

Offen bleiben native Linux-Abnahme (insbesondere UID/GID und Socket-Zugriff)
und ein vollständiger fachlicher GUI-Dialog samt GUI-Publikation. Synthetische
Testfreigaben gelten nur in den privaten Fixtures. Die bestehenden MFK-Vorgänge
und vorhandenen fachlichen Arbeitsbaumänderungen wurden nicht fortgesetzt.
Es erfolgte keine INT-/PROD-Aktion und keine externe Quelländerung.

## Anhangübernahme im gemeinsamen Skill vom 9. Oktober 2026

Tatsächlich geprüft:

- Der vorhandene `.agents/skills`-Link führt auf die gleiche Skill-Datei für
  Codex und OpenCode. Der Skill-Creator-Validator besteht; `AGENTS.md` verweist
  verbindlich auf die Anhangregel. Der Java-MCP bleibt unverändert.
- OpenCode Beta `0.0.0-beta-19723`, GUI, vorhandener Build-Agent mit DeepSeek
  V4.1 Flash: Skill über die `@`-Auswahl geladen, zwei unterschiedliche
  `lieferung.csv` und ein synthetisches `datenblatt.xtf` über den nativen
  Dateidialog angehängt. Der Auftrag enthielt keine Kopier- oder Hash-Anleitung.
- Originaldateien lagen unter `/private/tmp`, ausserhalb des Agent-Docker-Mounts.
  OpenCode kopierte sie mit den Host-Dateiwerkzeugen automatisch unter
  `.datenportal-integrator/input/fixture/`, je Datei in einen eigenen UUID-Ordner.
  Originaldateinamen und Endungen blieben erhalten. Alle drei Hashes (Quelle
  vorher/nachher, Kopie) stimmten überein; unabhängig wurden Bytegleichheit und
  unveränderte Quellen geprüft. Die CSV-Fixtures verwenden Umlaute sowie
  unterschiedliche Zeilenenden (CRLF/LF).
- Ein erster Kopierversuch scheiterte am unter zsh reservierten Variablennamen
  `status`; der isolierte Testlauf wurde danach mit vollständigen Nachweisen
  wiederholt. Die Skill-Regel nennt nun eigene Arbeitsvariablen. Es wurde kein
  fachlicher Integrator-Vorgang gestartet und keine Freigabe oder Publikation
  ausgeführt.

Berichte: `.datenportal-integrator/attachment-acceptance/8a7e2289-4ea0-4f88-a3dc-5eeb5b07241d/`;
`sources.json` enthält die ursprünglichen Soll-Prüfsummen, `observed.json` die
unabhängige Byte-/Hash-Prüfung, `gui-session.json` den tatsächlichen GUI-Dialog
und seine Werkzeugaufrufe. GUI-Sitzung: `ses_edf4906cfffeWgcTx15VmGsQWw`.

Offen bleiben die separate Finder-Drag-and-drop-Abnahme, XLSX als GUI-Anhang und
der GUI-Fall ohne zugänglichen Originalpfad. Diese Fälle sind durch die Anweisung
abgedeckt, aber noch nicht praktisch abgenommen. Fachliche MFK-Abnahme und
Publikation bleiben eigene Vorgänge.

## Docker-Einstieg und gemeinsames init vom 9. Oktober 2026

Die folgenden Nachweise betreffen den neuen Docker-Launcher. Frühere Abnahmen
weiter unten bleiben historische Nachweise ihrer jeweiligen Quellstände.

Tatsächlich auf macOS mit Docker Desktop und aktiviertem Host-Networking geprüft:

- Runtime lokal aus festgelegten Basisimages gebaut; JDK 25, Docker CLI/Compose,
  Git und `gh` kommen aus dem Container. Ein sauberer Gradle-Build sowie
  `test jar spotlessCheck` bestehen über denselben Launcher mit 132
  Unit-/Funktionstests ohne übersprungene Tests.
- Alle 21 echten Integrationstests bestehen ohne übersprungene Tests über
  `./bin/datenportal-agent gradle integrationTest`: bestehende Fachabläufe,
  Datenblatt-Neustarts, GRETL-Prüfcontainer und dauerhafte Compose-Laufzeit.
  Der Timeout-Test erhielt 15 statt 3 Sekunden für den Start seines absichtlich
  30 Sekunden laufenden Tasks; der erste Durchlauf scheiterte an dieser zu kurzen
  Anlaufzeit. Der vollständige Wiederholungslauf bestand in 6 Minuten 42 Sekunden.
  Log: `.datenportal-integrator/docker-integration-final.log`.
- Zwei gleichzeitige CLI-Starts serialisieren ihre Builds und liefern reines
  JSON auf stdout. Der echte MCP-Client initialisiert und findet 28 Werkzeuge.
  EOF und TERM beenden die Agent-Container ohne zurückbleibende Instanzen.
  Dabei war `JAVA_HOME=/no-host-jdk` gesetzt. Bericht:
  `.datenportal-integrator/launcher-acceptance/report.json`.
- `init` besteht mit den tatsächlich konfigurierten Fachwerkzeugen: privater
  Datenblatt-Import/-Export, ilivalidator, INTERLIS-Ableitung mit Nachweisen,
  erfolgreicher GRETL-CSV-Task und gezielt abgewiesener ungültiger CSV.
  Die synthetischen Freigaben gelten ausschliesslich in der privaten Testkopie.
  Bericht: `.datenportal-integrator/init.json`; referenzierte Nachweise unter
  `.datenportal-integrator/smoke/`.
  Zwei abschliessende `init`-Aufrufe gegen den fertigen JAR-Stand melden beide
  `ready=true` und referenzieren denselben Smoke-Bericht. Protokolle:
  `.datenportal-integrator/init-final.log` und `.datenportal-integrator/init-repeat.log`.
- Eine zusätzliche kalte Einrichtung verwendet ein eigenes Compose-Projekt mit
  den Standardtransporten HTTP und dauerhaftem GRETL. Fehlende Standardkonfiguration
  wird erstellt, eine explizit fehlende Datei abgewiesen. Wiederholung erhält die
  Konfiguration bytegleich und verwendet denselben Smoke-Nachweis. `--skip-smoke`
  meldet anschliessend ausdrücklich keine vollständige Bereitschaft. Die privaten
  Dienste und Volumes wurden nach dem Test entfernt. Bericht:
  `.datenportal-integrator/init-acceptance/698aaacc-d8bd-44dd-a984-a7064927732f/acceptance.json`.
- Die isolierte Loopback-Prüfung aus dem Agent-Container besteht. Java verwendet
  für Docker Desktop ausdrücklich den IPv4-Stack. Das vorhandene lokale Portal
  antwortete aus dem Container mit HTTP 503: Netzwerkzugriff nachgewiesen,
  erfolgreiche Portal-Anwendungsprüfung damit noch offen.

Simulierte Fehlerfälle sind davon getrennt: Unit-Tests prüfen einen entfernten
Docker-Kontext, fehlende Mounts, Pfade mit Leerzeichen und Symlinks, unveränderte
JSON-Argumente, explizite Secret-Variablennamen, geschützte MCP-Konfigurationen
sowie verweigerte Smoke-Wiederverwendung bei geänderten Nachweisen. Ein gezielt
eingeschränkter Host-PATH prüft die Diagnose bei fehlendem Docker. Diese Tests
sind keine tatsächlichen Infrastruktur-Ausfälle.

Offen bleiben native Linux-Abnahme (UID/GID, Socket, Host-Networking), ein
erfolgreicher lesender Portal-Anwendungstest und menschliche Harness-Dialoge.
Die Linux-Container auf Docker Desktop ersetzen keine native Linux-Abnahme.
Es gab keine fachliche MFK-Übernahme und keine INT-/PROD-Publikation.

Stand: 6. Oktober 2026. Damaliger Durchlauf: 94 Unit-/Funktionstests und sechs echte Integrationstests erfolgreich. Technische Tests, Simulationen und tatsächliche menschliche Abnahmen sind getrennt aufgeführt. Testfreigaben autorisieren keine fachlichen Änderungen im echten Themenrepo und keine INT-/PROD-Publikation.

## Tatsächlich geprüft

- Java 25 und Gradle: ausführbarer JAR, CLI und stdio-MCP mit gemeinsamem Kern.
- Echter Integrator-MCP über stdio: Werkzeugliste und Stopp vor fehlender menschlicher Freigabe.
- Beide echten Fach-MCPs: Datenblatt über HTTP, INTERLIS über sein vorhandenes JAR/stdio. High-Level-Ableitung mit Compiler, SO-Review und automatischen Constraint-Nachweisen.
- ilivalidator 1.15.0: gültige Dataset-/Serien-XTF sowie negative Fälle mit unbekannter Dienststelle. Office-Katalog separat validiert und als Referenzbestand verwendet.
- GRETL: dieselbe konkrete Themen-Task und dieselben Modellbytes früh in einer isolierten Host-Kopie sowie im bestehenden Jenkins-Container. Gültige CSV akzeptiert, ungültige CSV am `validateThemenCsv` vor `preparePublicationWorkspace` gestoppt. Jenkins-Offline-Bundle unverändert übernommen und über Prüfsumme gebunden.
- Typfälle: INTEGER, bestätigter DECIMAL-Bereich, BOOLEAN, DATE, TEXT mit bestätigter Länge und führenden Nullen. DATETIME-Grenze separat negativ geprüft, siehe unten.
- Eigenständige leere Organisation: tatsächlich konfigurierte Office-Regeln, Office-ilivalidator, vorhandene Teamreferenzen und echte Gradle-Konfiguration in einer isolierten Repository-Kopie. Kein Thema/defaultDataset/Job erfunden.
- Tatsächlicher Neustart eines zusätzlich isoliert gestarteten Datenblatt-MCP: bestätigten Snapshot über öffentliche Werkzeuge restauriert, Inhalt und Transferidentität erhalten.
- Vorhandener früher Pilotvorgang `a8fb924222964b5c9338cd61bbbd9675`: explizite Migration mit Vorschau und Sicherung, frühere Freigaben historisch, externe Kennungen erhalten. Fehlenden historischen Prüfkontext aus den über SHA-256 benannten, unveränderten Lieferdateien und dem bestätigten Jenkins-Bericht wiederhergestellt. Keine neue Lieferung.
- Java prüfte die bereits erfolgte Pilotpublikation erneut lesend: Queue 40 / Build 14, Release `257a31dc-7634-4129-9391-4a5562f99b04`, Metadaten, Reload und Downloads bestätigt. [Lokale Pilotpublikation](http://localhost:8081/series/ch.so.bevoelkerung.altersstruktur/issues/ch.so.bevoelkerung.altersstruktur_2025).
- Quellstandsprüfung: Git-Index und Arbeitsbaumstatus von Dev-Stack, Datenblatt-Editor, interlis-mcp und Themenrepo entsprechen dem Ausgangsstand. Keine fachlichen Änderungen im echten Themenrepo übernommen.
- Codex-MCP-Eintrag auf Java umgestellt; Zugangsdatenumgebung erhalten. OpenCode hat die Java-stdio-Verbindung tatsächlich als verbunden angezeigt.

## Docker-MCP-Abnahme vom 7. Oktober 2026

Die folgenden Nachweise ergänzen die ursprüngliche Java-Abnahme; die dort verwendeten lokalen Fach-JARs sind kein Bestandteil der neuen Standardeinrichtung.

- Veröffentlichtes Datenblatt-Image: `sogis/datenportal-datenblatt-mcp@sha256:b391421578f8c4652fb7a64465187746a3057ab4413d17cfc20a56025b470da1`, Server 0.2.0. Veröffentlichtes INTERLIS-Image: `sogis/interlis-mcp@sha256:b3ed1e738ccfe670f92ecebcbb014cf67aade5f0609e15be1f75948e15e899ff`, Server 0.0.570-1. Tatsächlich lokal auf linux/arm64 verwendet. Keine lokalen Fach-MCP-Builds.
- `setup-mcps` kopierte beide Originalmodelle aus `/app/models` des festgelegten Images, kompilierte sie und speicherte Image-Identitäten sowie Prüfsummen im ignorierten Cache. `doctor` initialisierte beide echten stdio-Verbindungen und bestätigte die benötigten Werkzeugkataloge.
- Echte Datenblatt-Tests: Bearbeitung über getrennte CLI-Prozesse und den Integrator-MCP, automatische Snapshot-Rekonstruktion, unvollständige Entwürfe, historische Attribut-/Ausgabe-IDs über mehrere Neustarts, Änderung und Löschung, kompatible Schema-2-Exportnachweise und unveränderte Exportbytes/Freigaben. Unbestätigte Aufträge bleiben bis zur ausdrücklichen Wiederherstellung blockiert.
- Der neue SDK-Transport prüfte 16 parallele Antworten mit jeweils über 64 KiB und beendete den veröffentlichten Datenblatt-Container regulär bei EOF mit Exit-Code 0. HTTP-Regression verwendete das veröffentlichte Image mit Streamable-Sitzung und XML-/Linkexport.
- Die vorhandenen echten Office-/XTF-, Modell- und GRETL-Prüfungen liefen ebenfalls mit den Docker-Fachadaptern. Der Dev-Stack wurde über sein vorhandenes Startskript gestartet; die gleiche CSV-Task wurde früh lokal und im bestehenden Jenkins-Runtime geprüft, ohne Seed, Upload oder neue Publikation.
- 105 Unit-/Funktionstests und 11 echte Integrationstests bestanden mit `./gradlew test jar integrationTest spotlessCheck`, JDK 25 und bestehender Java-17-GRETL-Umgebung. Unit-Tests simulieren unter anderem Registryfehler, explizite Image-Updates, Modellcache-Manipulationen, Konfigurationskonflikte, fremde/live Container und einen nicht erreichbaren Docker-Daemon. Die simulierten Docker-Fehler sind keine tatsächlichen Registry-Ausfälle.
- OpenCode meldete den Integrator-MCP tatsächlich als verbunden. Der echte SDK-Test prüfte den neuen Integrator-stdio-Prozess samt Datenblattaufrufen; ein vollständiger menschlicher Dialog in Codex Desktop/CLI und OpenCode bleibt separat offen. Laufende Harness-Verbindungen müssen nach dem JAR-Wechsel neu gestartet werden.
- Quellstände und Git-Arbeitsbäume von Themenrepo, Dev-Stack, Datenblatt-Editor und interlis-mcp entsprechen vor und nach den Tests exakt dem Ausgangsstand. Fachliche Übernahmen und Testfreigaben fanden ausschliesslich in isolierten Testrepositories statt.

Prüfberichte: `build/reports/tests/test/` und `build/reports/tests/integrationTest/`. Lokales Ausführungsprotokoll: `.datenportal-integrator/docker-verification.log`. Die lokale Konfiguration wurde auf die oben genannten Digests umgestellt; die vorherige Konfiguration ist ignoriert gesichert. Es wurde keine fachliche menschliche Freigabe ergänzt und keine INT-/PROD-Lieferung ausgeführt.

## GRETL-Container-Abnahme vom 7. Oktober 2026

Dieser Stand ersetzt die Host-Ausführung der GRETL-Vorprüfungen aus den oben dokumentierten früheren Abnahmen.

- Verwendetes Prüfimage: `sogis/datenportal-jenkins:0.1.0-3`, festgelegt auf `sha256:ecb915bf07fc87b47c8abf37d5e18a89be0c49f79be59a38c1ea5a47c7e75b56`, linux/arm64. Die im Image ermittelte Bundle-Prüfsumme bleibt `dd585744c3fd3909103086a48af5204f32ab53491ab887a9680cddf63cbf0fc7` und entspricht der tatsächlichen lokalen Jenkins-Runtime.
- `setup-gretl` ermittelte Java 17, Gradle-Cache und Bundle ausschliesslich in einem separaten Container. Der normale Image-Einstiegspunkt wurde überschrieben; es wurde kein Jenkins-Controller gestartet und kein JAR-Bundle auf den Rechner kopiert. Der vorherige Runtime-Nachweis wurde ignoriert gesichert.
- Echte CSV-/Modellprüfungen und Organisationsprüfungen liefen im separaten Container. Gültige CSV wurde akzeptiert; ungültige CSV stoppte vor `preparePublicationWorkspace`. Dieselbe Task wurde im laufenden lokalen Jenkins ausgeführt; Image- und Bundle-Identität wurden verglichen. Keine neue Lieferung, kein Seed und keine INT-/PROD-Aktion.
- Der zusätzliche echte Test verwendete einen nicht vorhandenen Stack-Pfad, nicht vorhandene Host-Java-17-/JAR-Pfade und ein frisches lokales Runtime-Verzeichnis. Einrichtung und Gradle-Prüfung bestanden ohne Stack-Zugriff und ohne lokalen JAR-Cache. Der tatsächlich vorhandene Dev-Stack wurde für diesen Nachweis nicht gestoppt.
- 111 Unit-/Funktionstests und 12 echte Integrationstests bestanden ohne übersprungene Tests mit `./gradlew test jar integrationTest spotlessCheck`. Der Prozess verwendete JDK 25 und `GRADLE_JAVA_HOME_17=/no-host-java17`; die GRETL-Laufzeit kam aus dem Image.
- Unit-Tests simulierten Image-Updates, Bundle-/Image-Abweichungen, explizite Pfadübersetzung mit Leerzeichen, ungültige Runtime-Nachweise, Taskfehler und Timeout-Aufräumen einschliesslich Berichtserfassung. Simulierte Abweichungen sind keine tatsächlich geänderte Jenkins-Installation.
- Beide MCP-Adapter, XTF-/Office-Validierung, Wiederaufnahme, Freigaben und die bestehenden GRETL-Typprüfungen blieben Bestandteil der echten Tests. Laufprotokoll: `.datenportal-integrator/gretl-verification.log`; Berichte unter `build/reports/tests/`.
- Quellstände, Arbeitsbaumstatus und Diff-Prüfsummen von Themenrepo, Dev-Stack, Fach-MCP-Repos und Jenkins-Quellrepo entsprechen dem Ausgangsstand. Die bereits vorhandene Änderung an `datenportal-jenkins-dev/bin/test-image-duckdb.sh` wurde nicht verändert.

README, beide Handbücher und Skill beschreiben den Containerbetrieb. Die lokale Konfiguration wurde auf `[gretl].image` umgestellt; alte Host-Einstellungen sind nicht mehr aktiv. Neue technische Prüfungen erteilen keine menschlichen Freigaben. Die unten dokumentierten Dialog-/Publikationsabnahmen bleiben offen.

## Gemeinsamer Stack-Bootstrap vom 8. Oktober 2026

- 117 Unit-/Funktionstests sowie 12 echte Integrationstests ohne übersprungene
  Tests erfolgreich; ausführbarer JAR und Spotless-Prüfung bestanden.
- Java-Fixtures prüfen Delegation an `scripts/bootstrap.sh`, lesende Prüfung
  vorhandener Publikationen, Wiederverwendung passender Instanzen, erhaltene
  Mount-/Modusgrenzen, separate Stack-Timeouts und persistente Fehlerlogs.
  Ein kalter Agentstart bereitet zunächst die Infrastruktur vor, sodass der
  GRETL-Image-/Bundle-Abgleich vor der gemeinsamen Erstpublikation erfolgt.
- Die echten lokalen Starttests fanden in eigenen Compose-Projekten statt:
  lokale Themenquelle und Registry-only, Erstpublikation, erneuter Seed,
  erhaltener Release/Secrets, Portal und Smoke-Tests. Eine getrennte
  Containerprüfung bestätigte die Sperre nach Abbruch des Host-Clients.
- Der [gemeinsame Prüfbericht](https://github.com/sogis/datenportal-dokumentation-betrieb/blob/main/pruefprotokolle/2026-10-08-automatischer-stackstart.md)
  trennt diese tatsächlichen Durchläufe von simulierten Fehlerfällen.
  Es wurde keine neue fachliche Lieferung, menschliche Freigabe oder
  INT-/PROD-Publikation im echten Themenrepo vorgenommen.

## Dauerhafte Compose-Laufzeit vom 8. Oktober 2026

- 123 Unit-/Funktionstests erfolgreich, ohne übersprungene Tests. Ausführbarer
  JAR und `spotlessCheck` erfolgreich; Bash-Laufzeitdateien bestehen `bash -n`.
- Die zwölf bestehenden echten Integrationstests bestanden im vollständigen
  Regressionslauf: stdio-MCPs, XTF/Office, Typprüfungen, Wiederaufnahme und
  gleicher GRETL-Task im vorhandenen Jenkins. Keine neue fachliche Lieferung.
- Alle neun neuen Compose-Integrationstests bestanden im abschliessenden
  eigenen Prüflauf. Sie verwenden eigene Projekte, freie Loopback-Ports,
  private Themenkopien und ausschliesslich synthetische Testdaten.
- Tatsächlich geprüft: automatischer Kaltstart beider HTTP-MCPs und GRETL,
  erwartete Werkzeugkataloge, manuelles `docker compose up -d`, gerenderte
  Konfiguration, Compose-Build ohne lokale Image-Builddefinitionen und Status.
- Tatsächlich geprüft: derselbe Gradle-Daemon bei zwei CSV-Prüfungen, getrennte
  Arbeitskopien, parallele gültige/ungültige CSV und Organisationsprüfung.
  Im letzten Messlauf: erste CSV-Prüfung 6473 ms, Folgeprüfung 1236 ms,
  ephemeral-Vergleich 9043 ms. Die Messung beginnt nach Containerbereitstellung
  und enthält Kopieren und Taskausführung; sie ist ein lokaler Einzelvergleich,
  keine allgemeine Leistungszusage.
- Tatsächlich geprüft: containerseitiger Timeout mit Diagnose, verlorener
  Hostprozess bei laufendem Task, nächste erfolgreiche Prüfung, GRETL-Neustart
  samt Warm-up, bewusst gestoppter Leerlauf-Daemon und Datenblatt-Wiederaufnahme
  nach MCP-Neustart. Die isolierten Container und selbst angelegten Testvolumes
  wurden entfernt.
- Ein absichtlich unvollständiger Abschlussnachweis im Testvolume löst einen
  Neustart ausschliesslich der eigenen GRETL-Runtime aus. Eine manipulierte
  erwartete Bundle-Prüfsumme stoppt vor der Taskausführung. Dies sind explizite
  Fehlerfixtures, keine beschädigte echte Jenkins-Installation.
- Ein tatsächlich belegter IPv4-Loopback-Port verhindert den Containerstart
  mit erhaltenem Diagnoselog. Unit-Fixtures prüfen ausserdem fremde Images,
  Arbeitsbereiche und Caches, nicht verfügbares Docker, Konfigurationsgrenzen
  und konkurrierende Sperren.
- Eingabestand: Themenrepo `b877712`, Dev-Stack `64ce441`; GRETL-Image
  `sogis/datenportal-jenkins:0.1.0-3` mit dem oben dokumentierten Image-/Bundle-
  Stand. Die beiden MCPs verwenden die Digests aus dem Konfigurationsbeispiel.
  Alle GRETL-Läufe nutzen Java 17 aus dem Image; der Hostprozess verwendet
  JDK 25 und `GRADLE_JAVA_HOME_17=/no-host-java17`.
- Änderungen betreffen ausschliesslich den Integrator. Schwester-Repositories
  und die vorhandenen Änderungen an `test-image-duckdb.sh` beziehungsweise
  `ibx.png` bleiben erhalten. `git diff --check` erfolgreich, einschliesslich
  zusätzlicher Prüfung der neuen Dateien.

Der bestehende Dev-Stack blieb während der Prüfung verfügbar. Docker-Daemon-
und Rechnerneustart wurden nicht ausgelöst; Containerneustart und die deklarierte
Restart-Policy sind geprüft. Die bestehende lokale Konfiguration wird nicht
automatisch migriert. Vorhandene Freigaben bleiben Aufzeichnungen; geänderte
Runtime-/Konfigurationsfingerprints verlangen erneut Prüfung und Freigabe.
Keine neuen menschlichen Freigaben oder INT-/PROD-Aktionen wurden vorgenommen.

## Automatisiert mit Simulationen geprüft

JUnit prüft zusätzlich fehlerhafte CSV, vollständige Scans und begrenzte Fehlerlisten, Datentypen/Pflichtwerte, Freigaben nach Änderungen, Dateimanipulationen, Serien, Teillieferungen, Konverter/JUnit-Fehler, XLSX-Formelcache, XML-/HTML-Behandlung, Kandidaten und unvollständige Proofs, Office-/Teamfehler und Repository-Konflikte.

Jenkins-HTTP-Fixtures belegen Multipart-Vertrag, Wiederaufnahme ohne zweiten Upload, eingefrorene Artefakte, unbestätigte Starts, Publikation mit Reloadfehlern, spätere erneute Sichtbarkeitsprüfung und abweichende Metadaten. Temporäre echte Git-Repositories mit simulierter `gh`-Antwort prüfen isolierte PR-Erstellung, menschlichen Merge-Stopp und veränderte Heads/Zielbytes. Dies ist keine Abnahme eines echten GitHub-PRs.

Die genauen Testergebnisse stehen nach Ausführung unter `build/reports/tests/test/` und `build/reports/tests/integrationTest/`. `./gradlew test` verwendet Fixtures; `./gradlew integrationTest` verwendet die echten, lokal konfigurierten Fachdienste und Werkzeuge. Keine Tests senden eine INT-/PROD-Lieferung.

## Konkrete Schnittstellengrenze

Der veröffentlichte CSV-Vertrag verlangt DATETIME mit `+01:00`. Der vorhandene GRETL-/ilivalidator-Adapter akzeptiert dieses Format für die Standarddomain `INTERLIS.XMLDateTime` nicht. Der echte negative Test belegt dies mit einer technisch gültigen CSV.

Der Integrator meldet diese Grenze als Rückfrage beziehungsweise `datetime_validator_limit`. Er ändert weder den Offset noch den Datentyp still zu TEXT. Für eine solche Modellierung ist eine belegte passende Domain oder ein separater Auftrag zur Erweiterung einer bestehenden Komponente erforderlich. INTEGER, BOOLEAN, DATE und bestätigte Text-/Zahlendomains sind davon nicht betroffen.

SO verlangt für TEXT eine bestätigte maximale Länge; ein konkreter INTERLIS-Zahlentyp benötigt bestätigte Grenzen und Genauigkeit. Beobachtete Werte werden als Vorschläge angezeigt, nicht automatisch zu Fachregeln erhoben.

## Noch offen

- Vollständiger Dialog in Codex Desktop/CLI und OpenCode mit tatsächlichem menschlichem CSV-OK, gemeinsamer Datenblatt-/Modellfreigabe und sichtbarer neuer lokaler Publikation.
- Eigenständige Organisationsabnahme mit tatsächlicher menschlicher Prüfung der Dateien und Berechtigungen im gewünschten fachlichen Repo.
- Menschliche Übernahme eines echten fachlichen PRs und fortgesetzter INT-/PROD-Lauf mit passenden Zugängen und bestehenden Runtime-Profilen.

Die frühere lokale Pilotlieferung verwendete ausdrücklich gekennzeichnete automatisierte Testfreigaben. Die lesende Java-Nachprüfung und technische Harness-Verbindung ersetzen keine der hier genannten menschlichen Abnahmen.

Der erste menschliche CSV-Prüfstand ist vorbereitet: Vorgang `34b7934e694540ed9fc2dbf99c1c97b5`, Altersstruktur 2025, 106 Zeilen und sechs INTEGER-Spalten. `Total` ist als mögliche Redundanz zur bewussten Entscheidung markiert. Zum Zeitpunkt dieses Standes ist keine menschliche Freigabe protokolliert; Modellidentität und technischer Kontakt sind ebenfalls noch zu bestätigen. Die Dateien und die HTML-Vorschau liegen im ignorierten Arbeitsverzeichnis.

## Agent-Run-Image und Release-Pipeline vom 9. Oktober 2026

Tatsächlich geprüft:

- `./bin/datenportal-agent gradle test jar spotlessCheck`: 154 Unit-Tests ohne
  Fehler und erfolgreiche Formatprüfung. Shell-Syntax, Workflow-YAML und
  `git diff --check` ebenfalls geprüft. Unit-Tests laufen auf dem Linux-Dateisystem
  im Container; hostseitige temporäre Pfade bleiben auf Integrationstests beschränkt.
- Fertiges Image lokal für `linux/amd64` und `linux/arm64` gebaut.
  `runtime/image-smoke.sh` besteht auf beiden Plattformen ohne Workspace-,
  Quellcode-, Cache- oder Host-JAR-Mount: CLI-Version und Hilfe, echte MCP-
  Initialisierung und Werkzeugliste, übereinstimmende MCP-Version,
  Konfigurationserzeugung, HTML-Bericht sowie Kompilierung und Ausführung eines
  synthetischen Java-Konverters bei unveränderter Eingabe. Das ist keine
  Veröffentlichung auf Docker Hub.
- Ein echter Probecontainer startet den Netzwerkserver aus dem eingebauten JAR
  ohne Host-JAR-Mount; sein veröffentlichter Port antwortet vom Host aus.
  Der Zugriff aus einem weiteren Container mit `--network host` scheitert in
  dieser Docker-Desktop-Umgebung. Der vollständige Host-Loopback-Selbsttest ist
  deshalb nicht bestanden; die Probecontainer wurden entfernt.

Simuliert und isoliert geprüft:

- Erstes Laden und Festhalten des Release-Images, Start ohne erneutes Pull/Build,
  Wiederherstellung per Digest, erfolgreicher expliziter Update sowie Erhalt der
  bisherigen Auswahl bei Pull-/Startfehlern. Docker-Aufrufe sind Test-Doubles.
- Registry-Neuveröffentlichung, Wiederholung mit passendem Commit, Ablehnung
  eines abweichenden Commits, Registry-Verbindungsfehler und Verhinderung eines
  Rücksprungs von `latest`. Diese Tests verwenden ausschliesslich synthetische
  Registry-Antworten, keine Zugangsdaten und keine tatsächlichen Uploads.

Fehlgeschlagene Vorläufe und offene Abnahme:

- Die ersten Unit-Testläufe scheiterten bei Eigentumsprüfungen des bestehenden
  Credential-Stores auf dem Docker-Desktop-Bind-Mount. Derselbe Testbestand
  bestand auf dem internen Linux-Dateisystem. Die Sicherheitsprüfungen des Stores
  wurden nicht abgeschwächt; nur die temporären Unit-Testpfade wurden getrennt.
- Ein erster Smoke mit sofortigem stdin-EOF lieferte sporadisch keine MCP-Antwort.
  Der abschliessende Smoke verwendet einen echten MCP-Client mit Initialisierung,
  Werkzeugabfrage und geordnetem Schliessen.
- `./bin/datenportal-agent gradle integrationTest` wurde ausgeführt: 14 Tests,
  davon 13 fehlgeschlagen. Für zwölf fehlt `config/local.toml`; ein Credential-
  Integrationstest scheiterte an den Eigentumsprüfungen auf dem Bind-Mount.
  Die vollständige MCP-/GRETL-/Init-Abnahme mit tatsächlich konfigurierten
  Fachwerkzeugen bleibt offen. Es wurde keine lokale Fachkonfiguration erfunden.
- Die Abfrage der GitHub-Actions-Secrets erhielt mit dem verfügbaren gh-Aufruf
  HTTP 401. `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, Schreibrecht auf das
  Docker-Hub-Repo und die erste CI-Publikation sind nicht bestätigt.
  Der erste veröffentlichte Versions-Tag, sein Plattformmanifest und das erneute
  Laden aus Docker Hub bleiben zu prüfen. Die Pipeline enthält diese Prüfungen.
- Keine reale Fachfreigabe, INT-/PROD-Publikation oder Änderung an externen
  Quellrepositories wurde vorgenommen.

Lokale technische Nachweise liegen unter
`.datenportal-integrator/image-release-abnahme/`; sie sind keine Registry-
oder Fachabnahme. Die reproduzierbaren Prüfskripte liegen unter `runtime/`.

### Korrektur der Registry-Prüfung nach der ersten Veröffentlichung

Der erste CI-Run hat `0.1.1` erfolgreich unter dem Manifest-Digest
`sha256:3c274b4c40b84d2b36a600bb5573056ff5c75a6f11cc45e94d7373c7375fa9e4`
veröffentlicht. Der anschliessende Prüfschritt scheiterte mit Exit 125, weil die
Docker-CLI des Runners `docker image inspect --platform` nicht unterstützt.
Die davon abhängige `latest`-Promotion konnte in diesem Run nicht erfolgen.

Die korrigierte Prüfung löst jeden Plattform-Manifest-Digest aus dem Index auf
und verwendet ihn für Pull, Inspect und Smoke. Sie benötigt das nicht verfügbare
Inspect-Flag nicht und prüft trotzdem jede Architektur unabhängig.

Tatsächlich bestanden: `test jar spotlessCheck` mit 156 Unit-Tests sowie die
reine Registry-Verifikation des bereits vorhandenen `0.1.1` für AMD64 und ARM64.
Version und Commit-Labels stimmen mit `3e7aee9ec2299d08e6c06c87484d70ac2a06d149`
überein; CLI, MCP, Ressourcen und Konverter-Smokes bestehen auf beiden Plattformen.
Es wurde dabei weder neu gebaut noch hochgeladen noch `latest` verändert.
Nachweis: `.datenportal-integrator/image-release-abnahme/registry-0.1.1-verify.log`.

Simuliert geprüft: Eine CLI, die `image inspect --platform` ablehnt, besteht die
korrigierten Abläufe; abweichende ARM-Metadaten werden auch auf einem anderen
Host erkannt. Der zusätzliche Modus `verify` führt keine Publikation aus.
Die erfolgreiche CI-Ausführung der Korrektur und die anschliessende
`latest`-Promotion bleiben bis zum neuen Workflow-Ergebnis offen.
