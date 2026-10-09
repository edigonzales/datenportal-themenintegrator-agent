# Abnahmestand

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
