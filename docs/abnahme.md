# Abnahmestand

Stand: 6. Oktober 2026. Aktueller Durchlauf: 94 Unit-/Funktionstests und sechs echte Integrationstests erfolgreich. Technische Tests, Simulationen und tatsächliche menschliche Abnahmen sind getrennt aufgeführt. Testfreigaben autorisieren keine fachlichen Änderungen im echten Themenrepo und keine INT-/PROD-Publikation.

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
