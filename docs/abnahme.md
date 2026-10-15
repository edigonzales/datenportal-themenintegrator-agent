# Abnahmestand

## Fachintegration vom 13. Oktober 2026

Die Nachprüfung auf Basis von `c6ddc97` verwendet die inzwischen vorhandene
`config/local.toml` und die tatsächlich konfigurierten Fachwerkzeuge.

### Tatsächlich geprüft

- Der erste vollständige Lauf hatte **26 Tests, 25 bestanden und einen Fehler**:
  `bothRealMcpAdaptersAndGretlEarlyValidation` fand keinen laufenden Jenkins im
  konfigurierten lokalen Stack. Die vorausgehenden MCP- und GRETL-Prüfungen
  dieses Tests waren erfolgreich.
- Die konfigurierte Jenkins-Instanz wurde mit dem vorhandenen Image gestartet.
  Ihr Themenrepo-Mount entspricht dem vorgesehenen Checkout; Image und
  GRETL-Bundle stimmen mit der vorbereiteten Prüflaufzeit überein.
  Der gezielte Jenkins-Regressionstest bestand anschliessend.
  Der für die Nachprüfung gestartete Jenkins-Dienst wurde danach wieder gestoppt.
- Der abschliessende vollständige Docker-Lauf
  `./bin/datenportal-agent gradle integrationTest` bestand mit
  **26 Integrationstests**, ohne Fehler oder Auslassungen.
- `./bin/datenportal-agent gradle test jar spotlessCheck` bestand ebenfalls:
  **194 Unit-/Funktionstests**, ohne Fehler oder Auslassungen.
- Es war keine Programmkorrektur erforderlich. Das Entwicklerhandbuch nennt
  jetzt die zusätzlich benötigte lokale Jenkins-Instanz und ihren Start im
  Standardstack. Dev-Stack und Themenrepository haben keine Arbeitskopieänderungen.

Nachweise: `.datenportal-integrator/integration-acceptance/20261013/` enthält
den Log des ersten vollständigen Laufs, den gezielten Jenkins-Nachweis sowie
JUnit-XML, Logs und Zusammenfassungen der abschliessenden Integrations- und
Regressionsprüfung.

### Synthetische Entscheidungen und offene Abnahmen

Freigaben und injizierte Modellfehler stammen ausschliesslich aus isolierten
Testfixtures; Compiler-, Datenblatt-, ilivalidator- und GRETL-Prüfungen wurden
tatsächlich ausgeführt. Geschäftsdaten wurden nicht publiziert.
Die am 12. Oktober fehlende Fachintegrationsprüfung ist damit nachgewiesen.
Eine vollständige synthetische Delivery-Abnahme mit `acceptance delivery`,
GUI-Dialoge mit tatsächlichen Fachfreigaben und eine INT-/PROD-Abnahme bleiben
separate Prüfungen.

## Drei fachliche Skills vom 12. Oktober 2026

Dieser Nachweis betrifft die Arbeitskopie auf Basis von `f97f66b`, noch ohne
Commit oder Veröffentlichung. Themenintegration, eigenständiges
Validierungsmodell und Organisationsanlage haben eigene Skills; gemeinsame
Verfahren werden bei Bedarf aus `docs/agent/` gelesen. CLI/MCP-Operationen,
fachliche Gates und Zustandsschema bleiben unverändert.

### Tatsächlich geprüft

- Docker: `./bin/datenportal-agent gradle test jar spotlessCheck` bestanden,
  **194 Unit-/Funktionstests**, keine Fehler oder Auslassungen.
- Alle drei Skills: YAML-Frontmatter, eindeutige zum Verzeichnis passende
  Namen, Beschreibungen, relative Skill-Verknüpfungen sowie rekursive lokale
  Markdown-Links und Anker geprüft. Die alten Hauptskill-Anker bleiben erhalten.
- Die kopierten Skills, Handbücher und Routing-Regeln der Abnahmeumgebung
  bleiben innerhalb ihres privaten Roots. Referenzen werden bytegleich kopiert;
  Änderungen an der Kopie verändern die Quellen nicht. Der Replay-Server weist
  unmarkierte Arbeitsbereiche und unmarkierte externe Antwortordner zurück.
- **20 ausgewählte Agentenszenarien** mit simulierten MCP-Antworten geprüft:
  Codex CLI **0.162.0-alpha.17.2** mit CLI-Standardmodell und OpenCode CLI
  **2.0.26** mit `deepseek/deepseek-flash`. Beide Clients entdecken und laden
  alle drei Skills; Haupt-, Modell- und Organisationsskill wurden automatisch
  sowie ausdrücklich ausgewählt. Vorhandene Zugangsdaten nutzt ausschliesslich
  der jeweilige Client; sie werden nicht in die Fixtures kopiert.

| Szenario | Beobachtetes Verhalten in beiden Clients |
|---|---|
| CSV prüfen | Bericht und CSV-Stopp; keine Freigabe, Modellierung oder Publikation |
| Nur Beschreibung ändern | Datenblattoperationen und Metadatenreview; keine CSV-Anforderung/-Prüfung |
| Eigenständiges Modell | Vorhandener Modellvorgang, Ableitung und Prüfung; Modellreview ohne Lieferung |
| Thema mit Modell | Derselbe Themenvorgang, gemeinsame Prüfung und beide Gates im Review |
| Organisation anlegen | Organisationsvorgang ohne CSV/Themenidentifier; Dateien und Berechtigungen zur Freigabe |
| Fehlende Dienststelle | `office` im Themenvorgang; keine Organisationsanlage |
| Unterbrochene Lieferung | `status`, fehlende Queue-Kennung erfragen und stoppen; kein Liefer-/Retry-Aufruf |
| Ungültiger CSV-Name | Stopp vor dem Integrator; keine Umbenennung oder Änderung |

Die zusätzlichen ausdrücklichen Modell- und Organisationsaufrufe ergeben
zusammen mit den acht Szenarien zehn ausgewählte Sitzungen pro Client.
Werkzeugaufrufe, geladene Referenzen, Schlussantworten und unveränderte
Eingangsbytes wurden anhand der Protokolle geprüft. Keine ausgewählte Sitzung
rief `approve`, lokale Übernahme, PR oder Publikation auf.

Nachweise: `.datenportal-integrator/skill-acceptance/5af252f6-c7fa-413a-87aa-bec5ef1a8889/`
mit `manifest.json`, `report.json`, Prompts, aufgezeichneten Antworten,
Eingangsprüfsummen und clientbezogenen Sitzungsprotokollen. Der Java-Testserver
`SkillReplay` läuft ohne Netzwerk; Antwortordner und Test-Classpath sind
schreibgeschützt eingebunden. Berichts- und Kandidatenartefakte entstehen erst
beim zugehörigen Replay-Aufruf.

### Simulierte Ergebnisse und fehlgeschlagene Vorläufe

Die MCP-Antworten, Prüfresultate und vorhandenen Datenfreigaben sind vorgegebene
synthetische Testdaten. Beobachtet wurden die Entscheidungen der Clients;
diese Sitzungen belegen keine ausgeführte ili2c-/GRETL-Prüfung und keine
tatsächliche menschliche Fachfreigabe. Automatische Werkzeugfreigaben waren
ausschliesslich in den isolierten Replay-Umgebungen aktiv.

Frühe Codex-Aufrufe ohne Projektvertrauen beziehungsweise Werkzeugfreigabe
erreichten den Replay-Server nicht. Die automatische Prüfung lehnte zudem
einen Lieferaufruf bei unklarem Upload und einen Export mit vermeintlichem
Original-Ausgabeziel ab. Die Wiederaufnahme wurde auf Status und belegte
Zuordnung begrenzt; Exportantworten verwenden separate Kandidatenpfade.

OpenCodes Standardmodell meldete `exo-free` als eingestellt. Gemma und Qwen
zeigten in Vorläufen falsche Argumente, direkte Dateibearbeitung oder nicht
beauftragte Operationen; diese Vorläufe sind nicht abgenommen. Kimi antwortete
mit HTTP 400. Bei einem frühen Testlauf wählte OpenCode wegen des geerbten
Arbeitsverzeichnisses den übergeordneten Checkout; die Tests setzen seither
Arbeitsverzeichnis und `PWD` konsistent und verwenden eigene Git-Roots.
Ein dabei erzeugtes Argumentartefakt wurde in den Nachweisordner verschoben.

Auch ein erster DeepSeek-Wiederaufnahmetest lief nach unzulässigen weiteren
Aufrufen in den Timeout. Die Kernregel für `submission_unknown` steht nun
zusätzlich unmittelbar im Hauptskill. Der abschliessende Test mit einem
konsistenten bereits freigegebenen Lieferstand stoppte nach `status` bei der
fehlenden Queue-Kennung; gleiches Verhalten wurde mit Codex erneut geprüft.
Die Modellanleitung nennt nach den Vorläufen das erforderliche `identity`-
Objekt und den Vorrang vorhandener Vorgangs-IDs ausdrücklich. Der externe
Skill-Creator-Validator konnte mangels PyYAML nicht starten; die entsprechenden
Frontmatter- und Verknüpfungsprüfungen übernimmt der bestandene Java-Test.

### Offen

Beim damaligen Lauf wurde `./bin/datenportal-agent gradle integrationTest` ausgeführt:
**18 Tests, zwei bestanden, 16 fehlgeschlagen**. Ursache der Fehlprüfungen war
die fehlende `config/local.toml`, auf die beide Projekt-Clients verweisen.
Die Prüfung der tatsächlich konfigurierten Fachwerkzeuge wurde am
[13. Oktober nachgeholt](#fachintegration-vom-13-oktober-2026);
die vollständige Vorbereitung der bestehenden Delivery-Abnahme blieb in diesem
Umbau offen. Es wurde
keine historische Fixture-Konfiguration als aktuellen Betriebsstand ausgegeben
und keine neue Betriebskonfiguration erfunden.

Ein vollständiger GUI-Dialog mit tatsächlichen menschlichen Fachfreigaben
bleibt eine separate Abnahme. Die hier geprüften CLI-Sitzungen ersetzen weder
diesen Dialog noch eine INT-/PROD-Abnahme. Es wurden keine Geschäftsdaten
publiziert.

## Geprüfte Image-Defaults vom 12. Oktober 2026

Neue Installationen verwenden die unten nachgewiesenen Werkzeugstände:
Datenblatt-MCP 0.2.1, INTERLIS-MCP 0.0.575-1 und Jenkins/GRETL 0.1.0-6.
`config/local.example.toml`, `compose.yaml` und der GRETL-Fallback sind auf
unveränderliche Registry-Digests abgestimmt. Der statische GRETL-Runtime-Key
lautet `jenkins-0.1.0-6`; die erzeugte Compose-Konfiguration bindet weiterhin
die tatsächlich ausgewählten Image-IDs. Bestehende lokale Konfigurationen und
Schnittstellen bleiben erhalten. Die vier GRETL-Fixturekorrekturen des
vorangehenden Aktualisierungslaufs sind Bestandteil dieser Änderung.

### Tatsächlich geprüft

- Docker: `gradle test jar spotlessCheck` erfolgreich; **191 Unit-Tests**,
  keine Fehler oder Auslassungen. Der neue Konsistenztest vergleicht Vorlage,
  Compose und GRETL-Fallback und verlangt unveränderliche Referenzen.
- `gradle integrationTest` mit den tatsächlich konfigurierten Werkzeugen:
  **26 Integrationstests bestanden**, keine Fehler oder Auslassungen.
- ARM64-Kandidatenimage ohne Workspace-Mount und Netzwerk geprüft:
  CLI/Version, MCP-Initialisierung, eingebettete Vorlage mit konsistenten Pins,
  bytegleicher Erhalt einer bestehenden Konfiguration, HTML und Konverter.
- Isolierte Ersteinrichtung mit eigenen Ports und Compose-Projekt bestanden:
  bytegleiche Vorlagenübernahme mit den vorgesehenen Pins, gültiges Modell/CSV
  und zurückgewiesene ungültige CSV, `doctor`, beide generierten
  Codex-/OpenCode-Client-Verbindungen sowie unveränderte Wiederholung von `init`.
  Der Nachweis liegt unter
  `.datenportal-integrator/init-acceptance/72f7f346-0cce-443c-a3c3-9f0c701e4c59/acceptance.json`.

Die lokalen Nachweise liegen unter
`.datenportal-integrator/updates/2026-10-12T15-39-36-315Z-init-image-defaults/`.

### Veröffentlichung und lokale Übernahme

Die Änderung wurde auf `codex/init-image-defaults` vorbereitet und mit Commit
`e27c1c1754729315897e77553673b5ac47da0111` auf `main` übernommen.
Der unveränderte [Release-Workflow](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38273327631)
ist vollständig erfolgreich: Unit-/Formatprüfungen, AMD64- und ARM64-Smokes,
installierter Host-Loopback, Veröffentlichung und frische Prüfung beider
Registry-Plattformen sowie anschliessende Digest-Promotion auf `latest`.

- Integrator **0.1.15**, Registry-Digest
  `sha256:39865821eff1f47413786ab21137988391387c68afa3a53fb74bcc91db4a178a`.
  `latest` zeigt nachweislich auf denselben Multi-Plattform-Digest.
- Das veröffentlichte ARM64-Image wurde zusätzlich lokal ohne Workspace-Mount
  geprüft. OCI-Version und Quellcommit stimmen; die eingebettete Vorlage
  enthält exakt die vorgesehenen drei Werkzeug-Digests. Der erweiterte
  Container-Smoke einschliesslich Konfigurationserhalt ist bestanden.
- Lokale Übernahme mit `--update-agent --version`, `init` und `doctor`
  erfolgreich. Die aufgezeichnete Image-Auswahl entspricht dem Release-Digest;
  `initialization.ready=true`, `smoke_current=true`, beide Fach-MCPs und
  GRETL sind gültig.
- `config/local.toml` und der Credential-Store sind gegenüber der Sicherung
  bytegleich. Eine lesende Jenkins-Anfrage ist mit HTTP 200 authentifiziert.
  OpenCode wurde aus- und wieder eingeschaltet; die gebündelte CLI bestätigt
  `datenportal_integrator connected`. Der vorhandene Session-Entwurf blieb erhalten.

Alle technischen Abschlusskriterien dieser Änderung sind erfüllt.
Der Abschlussbericht liegt im oben genannten Nachweisverzeichnis als `report.json`.

### Simulierte Entscheidungen und offene Fachabnahme

Testfreigaben gelten ausschliesslich in isolierten synthetischen Fixtures.
Ein vollständiger fachlicher GUI-Dialog mit tatsächlichen menschlichen
Freigaben bleibt offen. Dieser technische Release publiziert keine Geschäftsdaten.

## Lokale Werkzeugaktualisierung vom 12. Oktober 2026

Der bestehende Arbeitsbereich wurde mit dem veröffentlichten Docker-Launcher
aktualisiert. Die ausgewählten Images sind über Registry-Digests festgehalten:

| Komponente | Image-Version | Registry-Digest |
|---|---|---|
| Integrator | 0.1.14 | `sha256:be4e5ac7bd5789a900b8d1de866445513df15a11d6489c665fd3e85f91641195` |
| Datenblatt-MCP | 0.2.1 | `sha256:b391421578f8c4652fb7a64465187746a3057ab4413d17cfc20a56025b470da1` |
| INTERLIS-MCP | 0.0.575-1 | `sha256:72e834f04e74aed92658676ea3a693e0300f09ef91bb8f91c1334f8d943c94de` |
| Jenkins/GRETL | 0.1.0-6 | `sha256:c3eb068ed8636e546ca5b5cd2e57428ab31d1d00480446e52ad570bace46d833` |

Die zugehörigen Veröffentlichungsprüfungen sind erfolgreich:
[Integrator](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38251793247),
[Datenblatt-MCP](https://github.com/sogis/datenportal-datenblatt-editor/actions/runs/37592187589),
[INTERLIS-MCP](https://github.com/edigonzales/interlis-mcp/actions/runs/38115566598) und
[Jenkins/GRETL](https://github.com/edigonzales/datenportal-jenkins-dev/actions/runs/37736792631).

### Tatsächlich geprüft

- `--update-agent --version` und `init --update-tools` erfolgreich. `doctor`
  bestätigt `initialization.ready=true`, `smoke_current=true`, beide Fach-MCPs,
  GRETL und Host-Loopback. Docker-stdio und die bestehende GRETL-Betriebsart
  wurden beibehalten.
- `test jar spotlessCheck integrationTest`: **190 Unit-Tests und 26
  Integrationstests bestanden**, keine Auslassungen. Der erste Integrationslauf
  scheiterte in fünf Fällen, weil die isolierten Fixtures den GRETL-Standard
  statt der tatsächlichen Konfiguration verwendeten. Vier Fixture-Zuordnungen
  wurden korrigiert; die vollständige Wiederholung ist erfolgreich.
- Modellableitung und Compilerprüfung, gültige und ungültige CSVs,
  Compiler-/CSV-Reparaturen sowie das gemeinsame Limit von drei Korrekturen
  geprüft. Die frühe GRETL-Prüfung und der vorhandene Jenkins verwenden
  dasselbe Image; dessen Validierungstask wurde ebenfalls ausgeführt.
- Vorhandene Zugangsdaten erhalten; eine lesende Jenkins-Anfrage bestätigt
  die Anmeldung. OpenCode Desktop 2.0.26 wurde neu verbunden;
  seine gebündelte CLI meldet `datenportal_integrator connected`.

Die lokalen Logs, Registry-/CI-Nachweise und Testberichte liegen unter
`.datenportal-integrator/updates/2026-10-12T14-54-30-658Z-e7fbdbd6/`.

### Simulierte Entscheidungen und offene Fachabnahme

Automatisierte Freigaben betreffen ausschliesslich isolierte synthetische
Testfixtures. Dieser Aktualisierungslauf publiziert keine Geschäftsdaten.
Der technische Verbindungstest ersetzt keinen vollständigen fachlichen
GUI-Dialog mit tatsächlichen menschlichen Freigaben. Bestehende fachliche
Vorgänge benötigen nach dem Werkzeugwechsel erneute Prüfungen und die
betroffenen Freigaben.

## Bereinigung und Docker-Einstieg vom 11. Oktober 2026

Dieser Abschnitt beschreibt den damaligen Stand. Alle darunter datierten
Abschnitte sind historische Nachweise; ihre damaligen Kompatibilitäts- und
Versionsangaben gelten nicht für diesen Stand.

Zulässig sind nur `CORE`, `SO-GIS` und `SO-DATA`. Der Alias `SO`, alte
Konfigurationsfelder, direkte INTERLIS-JAR-Starts, Vorgangsmigrationen,
Python-Konverterübernahmen und alte Client-Konfigurationsmigrationen sind entfernt.
Nicht unterstützte Dateien bleiben erhalten. Fehlende Liefer-Prüfsnapshots
sperren die Verifikation; alte Berichte lösen keine Rekonstruktion und keinen
neuen Upload aus. Aktuelle Client-Einstellungen, Freigaben, Reparaturgrenzen und
Diagnosehistorien bleiben unterstützt.

README und Anwenderhandbuch führen durch den veröffentlichten Docker-Betrieb:
drei vorhandene Checkouts, nur bei Abweichungen eigene Einstellungen, `init`,
`doctor`, Projektvertrauen und Skill-Auswahl. Einrichtung und Publikation sowie
Agent- und Werkzeugupdates sind getrennt erklärt. Neun Anwendungsszenarien,
Beispielprompts, HTML-Berichte und die vorhandenen Diagramme bleiben enthalten.
Schema, Vorlage und Java-Prüfung sind abgeglichen. Der echte Erststart deckte
einen Docker-Desktop-Fehler bei automatisch zugewiesenen Probeports auf;
`AgentRuntime` veröffentlicht dafür nun einen vorher ausgewählten freien Port.

### Veröffentlichter INTERLIS-MCP

- **0.0.574-1**, Quellrevision `2f202dd9926f9a0eb0b73a7a50bb73179d2cba3a`:
  [erfolgreiche Pipeline](https://github.com/edigonzales/interlis-mcp/actions/runs/38114821829).
  Anwendung, ARM64-/AMD64-Images und Veröffentlichung einschliesslich
  Image-Smokes bestanden. Registry-Digest
  `sha256:e9824d526528127705d800b798e8e6173dc2adb812451bf9096b349d7174b233`
  enthält beide Architekturen. Das lokal geladene ARM64-Image bestätigt Version
  und Quellrevision. Beispielkonfiguration und Compose verwenden diesen Digest.
- JDK 21: `check e2eTest bootJar` und
  `:interlis-mcp-module:publishMavenJavaPublicationToVerificationRepository`
  erfolgreich; **595 Unit-Tests und 24 MCP-End-to-End-Tests**, keine Auslassungen.
  Parser und tatsächliche Werkzeugschemas akzeptieren die drei aktuellen Profile
  und weisen `SO` zurück. Benchmark-, Workflow- und Maven-Verbraucherprüfungen
  sind eingeschlossen. Nachweis: `/tmp/interlis-cleanup-checks.log` und
  [MCP-Abnahmedokument](https://github.com/edigonzales/interlis-mcp/blob/main/docs/SO_DATA_ACCEPTANCE.md).

### Veröffentlichter Agent

Agent **0.1.12**, Quellrevision `59c04fde46d9d442bdea80e8cdfe7545d0479f09`,
ist veröffentlicht:
[erfolgreiche Image-Pipeline](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38116335646).
Unit-Tests und Formatierung, Builds und Image-Smokes für ARM64 und AMD64
(einschliesslich eingebettetem Java-Code), Netzwerkprüfung, Veröffentlichung
und `latest`-Promotion sind erfolgreich. Der unabhängig abgefragte Registry-Digest
`sha256:d625254bcf34ea44e192336b53c487618f2c225ab56e5b8f403aa7d5d9cb69c6`
enthält beide Plattformen; das heruntergeladene ARM64-Image bestätigt Version
und Quellrevision. Nachweise: `agent-ci.json`, `agent-ci-jobs.json`,
`agent-manifest.json`, `agent-image.txt` und `agent-pull.log` im Nachweisordner.

Dieser unveränderliche Release ist der Bezugspunkt der Abnahme. Spätere reine
Nachweiscommits ändern die geprüfte Implementierung nicht. Bestehende
Installationen wechseln erst mit dem ausdrücklichen Agent-Update; vorhandene
lokale Konfigurationen und Fachwerkzeug-Pins werden nicht still ersetzt.

### Tatsächliche Integrator-Prüfungen

Die Prüfung verwendet den veröffentlichten MCP-Digest oben, den gepinnten
Datenblatt-MCP, GRETL und einen privaten Jenkins-/Garage-Stack. Die lokalen
Nachweise liegen unter `.datenportal-integrator/cleanup-acceptance/`.

- `test jar spotlessCheck`: **190 Unit-Tests bestanden**, keine Auslassungen.
  Geprüft sind aktuelle/abgewiesene Konfigurationen, unveränderte Dateien bei
  Konflikten, erhaltene Client-Einstellungen, Sicherungen, Profile, gesperrte
  Altvorgänge, fehlende Liefer-Prüfsnapshots und Dokumentationsstruktur.
  `DocumentationTest` prüft lokale Links, Abschnittsanker, Codeblöcke,
  Skill-Metadaten und CLI-Argumente; keine Formulierungstests.
- Der erste Integrationslauf bestand 23 von 26 Fällen. Drei Testannahmen wurden
  korrigiert: alte Werkzeuganzahl, doppelter Datenblatt-Abschnitt und ein
  Neustarttest, der statt eines neuen STDIO-Prozesses dieselbe HTTP-Instanz nutzte.
  Nachweis: `integration-first.log` und `integration-first-results/`.
- Der anschliessende vollständige Lauf `integrationTest` mit eigener
  `-PintegrationConfig` besteht **alle 26 Tests ohne Auslassungen**. Darin sind
  echte MCP-/Validator-/GRETL-Prüfungen, Wiederaufnahme, Dienstneustarts,
  Credential-Rotation und die drei Reparaturszenarien enthalten. Nachweise:
  `integration.log` und `integration-results/`.
- Vollständige Docker-Ersteinrichtung mit dem aktuellen Entwicklungs-JAR und
  dem veröffentlichten MCP `0.0.574-1` bestanden: fehlende Standarddatei wird
  erstellt, abweichende Repository-Pfade/Ports/Mounts funktionieren, wiederholtes
  `init` erhält die Konfiguration und verwendet den aktuellen Smoke-Nachweis,
  `doctor` bestätigt Bereitschaft. Beide erzeugten Client-Konfigurationen
  verbinden sich tatsächlich und liefern 28 Werkzeuge. `--skip-smoke` meldet
  anschliessend ausdrücklich keine Bereitschaft. Nachweise: `cold-init-final.log`
  und `.datenportal-integrator/init-acceptance/6f2c3825-2e42-463b-ba19-791dc75e3fd8/acceptance.json`.
  Eigene Init-Testdienste und Volumes sind entfernt. Dieser Nachweis verwendet
  `docker-build`; die Veröffentlichung wird getrennt geprüft.
- Zusätzliche vollständige synthetische Lieferabnahme bestanden: positiver
  Jenkins-Lauf und Downloads verifiziert; fehlerhafte CSV stoppt im
  Validierungstask vor der Publikationsvorbereitung. Der bestehende Release
  bleibt unverändert. Nachweise: `delivery.log` und
  `.datenportal-integrator/delivery-acceptance/d3ce3fd7-43ac-4ce1-9dd5-cd2ab8c315e7/report.json`.
  Eigene Testcontainer, Volumes und Secrets sind entfernt (`delivery-cleanup.json`);
  die Berichte und Konsolen bleiben erhalten.

- Praxisprüfung mit dem veröffentlichten Agent **0.1.12** bestanden: normaler
  Docker-Launcher in einem weiteren privaten Arbeitsbereich, ohne lokale
  Java-Quellen, Gradle-Builddateien oder Agent-JAR und mit `JAVA_HOME=/no-host-jdk`.
  `init` startet die konfigurierten Dienste; wiederholtes `init` erhält die
  Konfiguration und verwendet denselben Smoke-Nachweis. `doctor` bestätigt
  `initialization.ready`, `smoke_current` und `host-loopback-verified`. Beide
  erzeugten Client-Konfigurationen verbinden sich tatsächlich mit Version
  `0.1.12`, liefern 28 Werkzeuge und beantworten `organization_schema`.
  Nachweise: `.datenportal-integrator/release-acceptance/0.1.12/agent/` mit
  `first-init.json`, `repeat-init.json`, `doctor.json`, `client-check.json` und
  `client-check.log`. Eigene Dienste/Volumes sind anschliessend entfernt.

### Simulierte Entscheidungen und Grenzen

Alle automatischen Freigaben und Testlieferungen gehören ausschliesslich zu
isolierten, als synthetisch gekennzeichneten Fixtures. Unit-Tests simulieren
externe Fehler und Konflikte; sie ersetzen keine tatsächliche Werkzeugprüfung.
Es wurden keine Geschäftsdaten verändert oder publiziert.

Ein vollständiger fachlicher GUI-Dialog mit tatsächlichen menschlichen
Freigaben bleibt eine gesonderte Abnahme. Die technische OpenCode-2.0.26-Prüfung
vom 10. Oktober bleibt gültiger damaliger Nachweis und wurde hier nicht als
neuer GUI-Test ausgegeben. Die im MCP-Abnahmedokument genannte wiederholte
Compilerprüfung von `INTERLIS.MissingSyntheticType` bleibt ebenfalls offen.

## SO-GIS, vervollständigte Dokumentation und veröffentlichte MCP-Pins vom 10. Oktober 2026

Dieser Abschnitt beschreibt den damaligen Stand vom 10. Oktober 2026. Die nachfolgenden Abschnitte
bleiben historische Nachweise ihrer jeweiligen Quellstände; damalige offene
Punkte sind nur dann weiterhin offen, wenn sie hier ausdrücklich genannt werden.

Das GIS-Profil heisst `SO-GIS`. `SO` bleibt kompatibler Eingabealias; neue
explizite Eingaben werden normalisiert, bereits gespeicherte Vorgänge beim Lesen
nicht umgeschrieben. `SO-DATA` bleibt der Standard für neue CSV-Validierungsmodelle.
Skill, Handbücher, API-Tabelle und MCP-Referenzen sind entsprechend nachgeführt,
einschliesslich `reset_model_repair`, `repair_of` und `REMOVE_COMMENTS`.

Die technische Init-Prüfung und die synthetische Lieferabnahme liefern jetzt den
begründeten numerischen Jahresbereich mit, den der Modellkern verlangt. Alle
Fachintegrationstests unterstützen eine eigene `-PintegrationConfig`; die
Credential-Prüfung verwendet privaten containerlokalen Speicher. Die
Compose-Abbruchprüfung liest den tatsächlich zurückgegebenen unveränderlichen
Logpfad; die eigenständige HTTP-Testinstanz bindet ausdrücklich an `0.0.0.0`
im Container, bei ausschliesslich lokal veröffentlichtem Hostport. Der Port wird
explizit reserviert und veröffentlicht: Docker-Desktop-Ports aus automatischer
Docker-Zuweisung waren nur vom Host, nicht vom Testcontainer erreichbar.
Der private Lieferlauncher setzt den absoluten Fixture-Konfigurationspfad zuletzt,
damit Harness-Argumente ihn auch mit vorangestellten Runtime-Optionen nicht ersetzen.

### Veröffentlichte und verifizierte Images

- INTERLIS-MCP **0.0.572-1**, Quellrevision
  `44ea8449b819aa1cb009388fd7650a85285a03d3`:
  [erfolgreiche Pipeline](https://github.com/edigonzales/interlis-mcp/actions/runs/38076945421).
  Application-, ARM64-, AMD64- und Publish-Job erfolgreich, einschliesslich
  Maven-Verbrauchertest und Image-Smokes. Registry-Digest
  `sha256:eb348cd3cc6da8fedf9da2dbf564f1c8ee0d3827fb0c8e9447b3b44f013d338b`
  enthält beide Plattformen; das heruntergeladene ARM64-Image weist dieselbe
  Quellrevision und Version aus. Compose, Beispielkonfiguration und Handbuch
  verwenden konsistent diesen Digest.
- Agent **0.1.9** ist als früherer Stand bereits veröffentlicht und enthält die
  OpenCode-2.x-Konfigurationskorrektur:
  [erfolgreiche Pipeline](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38075843152).
  Er enthält noch den früheren MCP-Pin und nicht die hier korrigierten
  synthetischen Bereichsangaben.
- Agent **0.1.10**, Quellrevision
  `09ddbc521a7e3924040a50a86e18dc955fa037fa`, ist der veröffentlichte
  korrigierte Stand mit dem oben genannten MCP-Pin:
  [Image-Pipeline](https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38078933475).
  Unit-Tests/Formatierung, Builds und Image-Smokes für ARM64 und AMD64,
  einschliesslich eingebettetem Java-Code, sowie Netzwerkprüfung bestanden.
  Registry-Digest
  `sha256:9907c0e8914f1e5b64e9b51bb6ef3f332c2d4b69e19b6657d6638299c47e3fe5`
  enthält beide Plattformen. Das lokal heruntergeladene ARM64-Image bestätigt
  Version und Quellrevision; `--version` liefert ohne Netzwerk `0.1.10`.
  Dieser unveränderliche Release ist der Bezugspunkt
  der Abnahme; spätere reine Dokumentations-Builds ändern diesen Nachweis nicht.

Bestehende Installationen wechseln nur ausdrücklich. Das Handbuch unterscheidet
Agent-Update, bewusste Änderung des lokalen MCP-Pins und `setup-mcps --update`;
eine vorhandene `config/local.toml` bleibt erhalten.

### Tatsächliche Prüfungen

- INTERLIS-MCP mit JDK 21: `test check e2eTest bootJar` und lokale
  Verification-Maven-Veröffentlichung erfolgreich; 595 JUnit-Tests und 24 echte
  MCP-End-to-End-Tests. STDIO prüft `SO-GIS` und den Alias `SO` durch das
  tatsächliche Werkzeugschema. Benchmark- und Workflow-Prüfungen ebenfalls grün.
- Vorlauf des Integrators: 189 Unit-Tests, JAR und Formatprüfung erfolgreich.
  Die echte `Smoke.run()`-Init-Prüfung und der Credential-STDIO-Test bestanden.
  Der erste vollständige Entwicklungslauf bestand 23 von 26 Integrationstests;
  drei veraltete Testannahmen wurden danach korrigiert. Dieser Vorlauf ersetzt
  nicht die gemeinsame Prüfung mit dem veröffentlichten MCP.

- Gemeinsame Integrator-Abnahme mit veröffentlichtem MCP `0.0.572-1`,
  Datenblatt-MCP, GRETL und eigenem synthetischem Jenkins: Gesamtlauf
  `test jar spotlessCheck integrationTest` mit 189 bestandenen Unit-Tests und
  25 von 26 bestandenen Integrationstests. Der einzige Fehler war die oben
  beschriebene automatische Portzuweisung im HTTP-Test. Nach deren Korrektur
  wurde dieser Integrationstest erfolgreich erneut ausgeführt, zusammen mit
  `test jar spotlessCheck`. Damit sind alle 26 Integrationsfälle nachgewiesen;
  es wird kein einzelner fehlerfreier Gesamtlauf behauptet.
  Nachweise: `.datenportal-integrator/sogis-release-checks.log`,
  `.datenportal-integrator/sogis-release-results/` und
  `.datenportal-integrator/sogis-http-final.log`.
- Die echte `Smoke.run()`-Init-Prüfung, Wiederaufnahme, Credential-Rotation,
  Containerabbruch, MCP-Neustarts und alle drei Reparaturszenarien sind darin
  eingeschlossen. Compilerfehler und CSV-Bereichsfehler wurden mit echten
  Werkzeugen repariert; drei erfolglose Korrekturen führen dauerhaft zu
  `repair_limit_reached`.
- Vollständiges `acceptance-run` erfolgreich, mit beiden erzeugten
  Codex-/OpenCode-MCP-Konfigurationen und dem privaten Credential-Store.
  Kommentarfreies `SO-DATA`-Modell, Datenblatt und `dataset.gradle` wurden
  gemeinsam geprüft. Die positive Lieferung ist verifiziert; der tatsächliche
  negative Jenkins-Lauf stoppt bei `validateThemenCsv` vor
  `preparePublicationWorkspace`, ohne den Release zu verändern. Vorhandene
  Fixture-Infrastruktur wurde weiterverwendet. Nachweise:
  `.datenportal-integrator/sogis-delivery-final.log` und
  `.datenportal-integrator/delivery-acceptance/5c6a64b0-9328-46c5-be78-1511546ca63c/report.json`
  samt positiver/negativer Jenkins-Konsole.

Alle Daten, automatischen Freigaben und Publikationen dieser Abnahme gehören
ausschliesslich zu ihrer isolierten synthetischen Fixture. Es wurden keine
Geschäftsdaten verändert oder publiziert. Eine synthetische MCP-Lieferabnahme
ist keine menschliche GUI-Freigabe.
Die eigenen Testcontainer, Volumes und Secrets wurden nach erfolgreicher
Abnahme mit `acceptance-cleanup` entfernt; Berichte bleiben erhalten.
Die OpenCode-Testverbindung wurde getrennt und das temporäre Projekt geschlossen.
Der abschliessende lokale Lauf `test jar spotlessCheck` bestand ebenfalls
(189 Tests; `.datenportal-integrator/sogis-commit-checks.log`). Abschnittsanker
und lokale Markdown-Links wurden zusätzlich geprüft.

### OpenCode 2.x tatsächlich geprüft

Die vorhandene Desktop-Installation ist `2.0.26`; ihre gebündelte CLI unter
`/Applications/OpenCode.app/Contents/Resources/opencode-cli` meldet ebenfalls
`2.0.26`. Die normale Shell-CLI ist separat weiterhin `1.18.22`.
Im isolierten Fixture-Projekt ist `@datenportal-themenintegrator` im Composer
und unter den Projekt-Skills sichtbar. Die erzeugte V2-Konfiguration wird unter
„More options → Edit project → Extensions → MCPs“ erkannt. Nach Verbindung
über die gebündelte CLI-API meldet `mcp list` tatsächlich `connected`; der
Desktop zeigt denselben Eintrag eingeschaltet. Es wurde kein fachlicher Auftrag
an ein Sprachmodell gesendet. Nachweis:
`.datenportal-integrator/sogis-opencode-evidence.json`.

### Gesonderte Grenzen

Ein vollständiger fachlicher Dialog mit tatsächlichen menschlichen Freigaben
in beiden GUIs bleibt eine gesonderte Abnahme. Der Verbindungstest und die
synthetischen Lieferfreigaben ersetzen diesen Nachweis nicht.
Die im MCP-Abnahmedokument beschriebene Compilerbeobachtung bei wiederholten
Prüfungen von `INTERLIS.MissingSyntheticType` ist weiterhin offen; die
modelllokalen Negativfälle dieser Reparaturabnahme belegen ihre Behebung nicht.

## SO-DATA, Wertebereiche und Reparaturen vom 10. Oktober 2026

Entwicklungsstand in Integrator und `../interlis-mcp`: neue CSV-Modelle verwenden
`SO-DATA`; CORE/SO bleiben verfügbar. Kommentarprüfung, Kommentarentfernung,
Datumsidentität, native Constraint-Namen, begründete numerische Vorschläge,
persistente Reparaturgrenzen und verifizierte Loglinks sind implementiert.
Die Regeln für reale Freigaben und Publikationen bleiben bestehen.

Tatsächlich lokal ausgeführt:

- Integrator: `./bin/datenportal-agent gradle test jar spotlessCheck`, 188 Tests
  bestanden, einschliesslich `DocumentationTest`; JAR und Formatprüfung erfolgreich.
  Nachweis: `.datenportal-integrator/sodata-unit.log`.
- INTERLIS-MCP mit JDK 21: `test check e2eTest bootJar` und
  `:interlis-mcp-module:publishMavenJavaPublicationToVerificationRepository` erfolgreich;
  595 JUnit-Tests und 24 MCP-End-to-End-Tests. Die vorhandenen Benchmark- und
  Workflow-Prüfungen wurden ebenfalls ausgeführt. Die Maven-Veröffentlichung hier
  bezeichnet ausschliesslich das lokale Verification-Repository.
- Beide Agent-Images für ARM64 und AMD64 lokal gebaut und mit
  `runtime/image-smoke.sh` geprüft, einschliesslich des eingebetteten Java-Codes.
  Nachweise: `.datenportal-integrator/sodata-image-arm64.log` und
  `.datenportal-integrator/sodata-image-amd64.log`.
- Neues MCP-JVM-Image `interlis-mcp-sodata:local` gebaut. Die gemeinsame Abnahme
  verwendet dieses Image mit JDK 21 an einem eigenen lokalen HTTP-Port,
  die vorhandenen Datenblatt-/GRETL-Images und eine eigene Testkonfiguration.
  Es wurden keine Geschäftsdaten geändert oder publiziert.
- Drei isolierte Reparaturszenarien mit tatsächlichem MCP-Compiler, Datenblatt-MCP,
  GRETL-CsvValidator und ilivalidator bestanden: Compilerfehler korrigieren,
  CSV-Bereichsfehler korrigieren und drei erfolglose Korrekturen mit anschliessendem
  `repair_limit_reached`. Nur die fehlerhafte Eingabespezifikation wird in der
  Testfixture injiziert; Werkzeugantworten und Diagnosen stammen aus den echten
  Werkzeugen. Die Prüfung kontrolliert auch die gespeicherten Artefaktprüfsummen.
  Nachweis: `.datenportal-integrator/sodata-repair-integration.log`; erhaltene
  Testvorgänge und `SYNTHETIC_ACCEPTANCE.json` unter `.datenportal-integrator/tmp/`.

Mit synthetischen Fixtures geprüft: erfolgreiche und fehlgeschlagene Ableitungen,
Reparaturgrenze und Wiederaufnahme, CSV-Reparatur, echte Schema-Serialisierung,
Kommentarzeichen in Strings, gültige Datumsangaben, erhaltene SO-Regeln,
Wertebereiche/Genauigkeit/Vorzeichen, Vorrang bestätigter Spezifikationen,
unveränderliche Diagnosekopien, blockierte fremde/manipulierte Loglinks und
weiter gültige Freigaben nach einer unveränderten Wiederholungsprüfung.
Automatische Freigabe- und Reset-Aussagen in diesen Tests gelten nur dort.

Die reguläre Gesamt-Integration ist nicht grün: 17 Tests, davon 16 fehlgeschlagen.
Für 15 Tests fehlt `config/local.toml`; ausserdem scheitert der separate
Credential-Integrationstest. Im isolierten
Fachwerkzeuglauf funktionieren Modellableitung, CSV-Validierung, XTF-Prüfung und
der Stopp vor Publikationsvorbereitung bei einer ungültigen CSV. Der anschliessende
Vergleich mit dem bereits laufenden Jenkins bleibt mangels Jenkins-Container offen.
Nachweise: `.datenportal-integrator/sodata-integration-default.log` und
`.datenportal-integrator/sodata-integration-isolated.log`. Dies ist keine
Publikations- oder Jenkins-Abnahme.

Registry-Veröffentlichung und neue Integrator-Pins sind **offen**. Ein lokaler
Image-Digest ist kein veröffentlichter Registry-Digest. Die bestehenden Pins
bleiben deshalb unverändert. Zuerst den geprüften MCP veröffentlichen, dann dessen
verifizierten Registry-Digest konsistent in Compose, Beispielkonfiguration und
Runtime-Defaults übernehmen und den Integrator-Build prüfen/veröffentlichen.
Erst das ausdrückliche Update wechselt bestehende Installationen. Der normale
Image-Betrieb der alten Pins enthält diese Erweiterung noch nicht.

## Image-Smoke-Korrektur vom 10. Oktober 2026

Der CI-Image-Smoke-Test scheiterte beim Kompilieren seines eingebetteten
Java-Programms: `runtime/image-smoke.sh` verwendete nach der Berichtsumstellung
noch die alte `Reports.render`-Signatur. Der Aufruf verwendet jetzt den
expliziten Berichtstyp `CSV` und eine synthetische Themenkennung. Die Prüfung
verlangt Phase, Kennung und Prüfergebnis im erzeugten HTML.

Tatsächlich lokal ausgeführt: Images mit `runtime/Dockerfile` für `linux/arm64`
und `linux/amd64` gebaut und jeweils `runtime/image-smoke.sh` erfolgreich
ausgeführt, ohne Workspace-Mounts und mit deaktiviertem Netzwerk in den
Testcontainern. Geprüft wurden unter anderem die Kompilierung des eingebetteten
Java-Programms, Ressourcenauflösung und HTML-Erzeugung, Konfigurationsanlage,
stdio-MCP-Initialisierung samt Version und Toolkatalog sowie die synthetische
CSV-Konvertierung. `bash -n runtime/image-smoke.sh` ist ebenfalls erfolgreich.
Nachweise: `.datenportal-integrator/html-reports-image-arm64.log` und
`.datenportal-integrator/html-reports-image-amd64.log`.

Die Daten und Verzeichnisse innerhalb der Container sind synthetische Fixtures.
Ein erneuter GitHub-Actions-Lauf, Registry-Veröffentlichung und der separate
Netzwerk-Probe-Schritt sind durch diese lokale Prüfung nicht nachgewiesen.

## HTML-Prüfberichte vom 9. Oktober 2026

Tatsächlich ausgeführt:

- `./bin/datenportal-agent gradle test jar spotlessCheck`: 178 Tests bestanden,
  keine Fehler oder übersprungenen Tests; JAR-Erstellung und Formatprüfung
  erfolgreich. Nachweis: `.datenportal-integrator/html-reports-unit.log` und
  JUnit-Berichte unter `build/test-results/test/`.
- Die sechs synthetischen HTML-Beispiele im Headless-Chromium
  151.0.7922.34 bei 1280 und 390 Pixeln Breite sowie mit Druckmedium
  geprüft: 18 erfolgreiche Phasen-/Ansichtsprüfungen, kein Überlaufen des
  Seitenrands, auch bei aufgeklappten Details. In der Druckansicht sind die
  sonst zugeklappten Bereiche sichtbar. Keine Netzwerkressourcen geladen.
- Screenshots aller sechs Phasen visuell kontrolliert; zusätzlich die schmale
  Metadatenansicht, die Druckdarstellung von Übersicht und SQL sowie den
  aufgeklappten Gradle-Dateivergleich betrachtet. HTML-Beispiele, Screenshots,
  erzeugte Druck-PDFs und `visual-check.json` liegen unter
  `build/reports/review-examples/`.

Isoliert und synthetisch geprüft: `ReportsTest` umfasst zehn Tests, darunter
alle sechs Berichtstypen mit erfolgreichen, fehlgeschlagenen, unvollständigen
und leeren Ergebnissen; zeichengetreue Code-Darstellung, strikte Erkennung
eingebetteten JSONs, HTML-Escaping, unveränderte Eingabedaten, neue/geänderte/
unveränderte Dateien, widersprüchliche oder mehrdeutige Vergleichsdaten und
grosse Inhalte. Zufällige Zeilenbeispiele mit festem Seed prüfen die
Rekonstruktion beider Dateiversionen aus dem Diff. `ReportExamples` erzeugt
ausschliesslich Darstellungsfixtures, keine Vorgänge oder Freigaben.

Nicht durchgeführt: erneute externe Integrations- oder Publikationsabnahmen,
Migration alter HTML-Dateien sowie Druckprüfung in anderen Browser-Engines
oder auf einem physischen Drucker. Die Darstellungsabnahme bestätigt keine
Fachwerkzeug- oder Publikationsfunktion. Historische Nachweise bleiben
unverändert bestehen.

## OpenCode-2.x-Konfiguration und Migration vom 9. Oktober 2026

Tatsächlich geprüft:

- `./bin/datenportal-agent gradle test jar spotlessCheck`: 168 Tests bestanden,
  keine Fehler oder übersprungenen Tests; einschliesslich zwölf neuer
  `HarnessTest`-Tests. Nachweis: `.datenportal-integrator/opencode-v2-unit.log`
  und JUnit-Berichte unter `build/test-results/test/`.
- OpenCode 2.0.26 aus `/Applications/OpenCode.app/Contents/Resources/opencode-cli`:
  tatsächliche Ausgabe von `harness-config` mit `--runtime docker-build` in
  einem isolierten Git-Projekt verwendet. Der echte OpenCode-Dienst meldete
  `datenportal_integrator` als `connected`. Eigene XDG-Verzeichnisse, eigener
  Port, minimale Testkonfiguration ohne Publikationsprofile; anschliessend den
  eigenen Dienst beendet. Keine Geschäftsvorgänge oder Freigaben ausgeführt.
  Nachweise: `.datenportal-integrator/opencode-v2-check/6cb99e18-641f-4402-b6e8-91968e9019c8/`,
  insbesondere `harness.json`, `opencode.json`, `mcp-persistent-result.json`
  und `evidence.json` mit Konfigurationsprüfsumme.

Isoliert mit synthetischen Konfigurationen geprüft: Erzeugung und Migration von
Java-/Launcher-Einträgen, Deaktivierung, individuelle Timeouts, Weitergabe von
Umgebungsvariablen, Erhalt fremder V2-Server, kompatible Doppeleinträge,
Konflikte und ungültige Feldtypen ohne Änderung beider Konfigurationsdateien,
Sicherungen und wiederholte Installation. Alle schreibenden Tests verwenden
temporäre Arbeitsbereiche. Der Konfigurationsleser der Lieferabnahme wurde
gegen die erzeugte V2-Datei geprüft; dies ist keine erneute Lieferabnahme.

Tatsächlich fehlgeschlagen beziehungsweise offen:

- `./bin/datenportal-agent gradle integrationTest` wurde ausgeführt und schlug
  fehl: 14 gemeldete Testfälle, davon einer erfolgreich und 13 fehlgeschlagen.
  Zwölf Fehler einschliesslich eines Klassen-Initialisierungsfehlers betreffen
  die fehlende `config/local.toml`; die Prüfung der tatsächlich konfigurierten
  Fachwerkzeuge bleibt damit offen. Der Credential-Store-Integrationstest
  erwartete `jenkins_authentication_failed`, erhielt jedoch
  `credential_store_permissions`. Nachweis:
  `.datenportal-integrator/opencode-v2-integration.log` und
  `build/test-results/integrationTest/`. Dieser Lauf gilt nicht als erfolgreiche
  Integrationsabnahme; die Ursachen wurden nicht durch Ersatzkonfigurationen
  oder Änderungen an den Credential-Regeln umgangen.
- Die Shell-CLI `opencode` meldet weiterhin 1.18.22. Für den obigen V2-Nachweis
  wurde ausdrücklich die mit der installierten Desktop-App gelieferte CLI
  verwendet; OpenCode wurde nicht installiert oder aktualisiert.
- Erste kurzlebige API-Abfragen lieferten noch keinen Verbindungsnachweis.
  Der erste eigene Hintergrunddienst kollidierte mit dem belegten Standardport;
  der erfolgreiche Test verwendete einen separaten Port und eine ausdrücklich
  gewählte Test-Location. Ein falsch kodierter Location-Parameter wurde zuvor
  mit HTTP 400 abgewiesen. Diese Vorversuche werden nicht als Erfolg gewertet.
- Ein vollständiger GUI-Fachdialog, eine erneute vollständige `init`-Prüfung
  mit Fachwerkzeugen und die Veröffentlichung eines korrigierten Agent-Images
  wurden nicht durchgeführt. Historische Beta-Nachweise bleiben unverändert.

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

## Gemeinsames Betriebspaket vom 13. Oktober 2026

Tatsächlich geprüft:

- `test jar spotlessCheck operatorWorkspaceArchive`: 210 Unit-Tests bestanden.
  Das reproduzierbare Archiv enthält drei vollständige Skills mit internen
  Verknüpfungen und Referenzen, Betriebsanweisungen, Anwenderhandbuch,
  Konfigurationsvorlagen und Launcher. Keine Java-Implementierungsquellen,
  Entwicklertests, Entwicklerhandbücher oder Gradle-Launcher.
  Die bestehenden Skill-Validatoren bestätigen alle drei Skills. Images für
  `linux/arm64` und `linux/amd64` wurden lokal gebaut; der quellcodefreie
  CLI-/MCP-/Paket-Smoke besteht auf beiden Architekturen. Ein AMD64-Vorlauf
  unter parallelen Image-Builds lief in die MCP-Startwartezeit; die abschliessende
  Einzelprüfung besteht. Es fand keine Registry-Publikation statt.
- Frische Installation, konfliktfreie Aktualisierung und Abbruch vor jeder
  Änderung bei individuellen Paketänderungen; Konfiguration, bestehende
  Eingänge, unbekannte lokale Dateien und Vorgangszustände bleiben erhalten.
- Beide echten Java-MCP-Prozesse aus einem installierten Arbeitsbereich:
  Anhangimport mit drei gleichen SHA-256, öffentliche `operation_schema`-Abfrage
  ohne neuen Vorgang, verweigerte Konfigurations-/Quellen-/Zustandszugriffe und
  Verzeichnis-/Symlink-Ausbrüche. Ein selbst geschriebener Java-Konverter und
  seine JUnit-Tests liefen im begrenzten Docker-Container erfolgreich; weder
  Docker-Socket noch Konfiguration oder Quellcode-Testdatei waren erreichbar.
  Öffentliche Integrator-Dateiargumente können den Datei-MCP ebenfalls nicht
  umgehen: direkter Originalpfad und Quellen als `start`/`attach`/`stage_change`
  sind gesperrt, verifizierte Eingangskopien erlaubt, nachträglich geänderte
  Kopien werden abgewiesen. Der abschliessende MCP-Integrationstest besteht.
- Ein vollständiger Lauf von `integrationTest` mit den tatsächlich
  konfigurierten Fachwerkzeugen bestand mit 27 Tests. Im späteren Vorlauf
  bestanden 26 von 27; der parallele GRETL-Test lief unter gleichzeitigen
  Image-Builds in die Runtime-Sperrwartezeit. Seine gezielte Wiederholung ohne
  Image-Builds bestand. Das ist kein Nachweis für unbegrenzte Parallelbelastung.
- Technische lokale Testlieferung aus dem installierten Betriebspaket mit
  tatsächlichen Datenblatt-/INTERLIS-/GRETL-Werkzeugen und privatem lokalen
  Stack bestanden: Modell erzeugt und geprüft, CSV im Lieferjob validiert,
  Portal/Manifest/Downloads bestätigt. Fehlerhafte CSV wird im Job abgewiesen;
  die bestehende Veröffentlichung bleibt unverändert. Automatisierte
  Freigaben betreffen ausschliesslich diese markierte synthetische Fixture.
  Der erste Vorlauf scheiterte vor jeder Lieferung an fehlenden Validator-
  Neben-JARs im isolierten Mount. Der Launcher mountet nun das konfigurierte
  ilivalidator-Bundle read-only; eine neue Fixture bestand vollständig.

Die vier Client-Nachweise sind getrennt zu beurteilen:

| Client | Tatsächlicher Nachweis | Grenze |
| --- | --- | --- |
| Codex CLI 0.160.0 | Effektive App-Server-Konfiguration: Shell, Unified Exec, JS-REPL, Apps und Remote-Plugins aus; fremde konfigurierte MCPs aus. Alle drei Projekt-Skills werden als aktiviert erkannt. Native Sandbox lässt die Betriebsanleitung zu und verweigert die Quellcode-Testdatei. | Kein vollständiger menschlicher Fachdialog. |
| Codex Desktop, Engine 0.162.0-alpha.17.2 | Mitgelieferte Engine separat geprüft: dieselben effektiven Einstellungen, alle drei aktivierten Projekt-Skills und bestandener positiver/negativer Sandbox-Test. | Desktop-GUI-Test offen: Computer Use verweigert den Zugriff auf `com.openai.codex`. Engine-Prüfung ersetzt diesen Nachweis nicht. |
| OpenCode Desktop 2.0.27 | Im isolierten Testprojekt zeigte die Oberfläche beide MCPs und alle drei Skills. Der zugehörige Server verweigerte Shell, Quellenlesen, Glob, Grep und Code Mode auch nach Wechsel von `datenportal` auf `build`; Anleitungen, Skills und Fach-MCPs blieben erlaubt. | Kein vollständiger menschlicher Fachdialog. |
| Gebündelte OpenCode-CLI 2.0.27 | Projektbezogene MCP-Abfrage und echte Permission-API prüfen dieselben Erlaubnisse/Sperren mit expliziter Projekt-Location. | `mcp list` allein verwendete in dieser Version die Server-Default-Location statt des CLI-Arbeitsordners. |

Alle Client- und Konverterproben verwenden ausdrücklich gekennzeichnete,
isolierte Testfixtures. Sie erteilen keine fachlichen Freigaben. Das temporäre
OpenCode-Testprojekt wurde geschlossen und die vorherige CSV-Sitzung wieder
angezeigt, ohne ihren Freigabestand zu verändern.

Eine zusätzliche Codex-MCP-Inventarabfrage ohne `threadId` zeigt Bootstrap-
Dienste, darunter `cua_repl`, und ist kein Nachweis der tatsächlich angebotenen
Werkzeuge einer Fachsitzung. Die vollständige Werkzeugliste einer neuen
Codex-Fachsitzung bleibt zusammen mit dem GUI-Nachweis zu prüfen; insbesondere
dürfen Desktop-injizierte Werkzeuge keine Datei- oder Shell-Umgehung bieten.

Die Codex-Konfiguration lehnt alte `sandbox_mode`-/`sandbox_workspace_write`-
Einstellungen in Projekt-, Benutzer- oder Elternkonfigurationen ab. MCPs erben
die Codex-Dateisandbox nicht; deshalb prüft der Datei-MCP seine Pfade selbst und
Konverter laufen zusätzlich in einem eigenen begrenzten Container. Grundlage:
[Codex-Berechtigungen](https://learn.chatgpt.com/docs/permissions),
[Konfigurationsreferenz](https://learn.chatgpt.com/docs/config-file/config-reference),
[OpenCode-Permissions](https://dev.opencode.ai/v2/docs/permissions/).

Der zunächst fehlgeschlagene Host-Loopback-Selbsttest wurde eingegrenzt:
derselbe installierte ARM64-Prozess besteht mit
`-Djava.net.preferIPv4Stack=true` und scheitert ohne diese Einstellung unter
Docker Desktop. Der reguläre Launcher setzte IPv4 bereits; der synthetische
Betriebslauncher und direkte Image-Starts taten dies nicht. Das Agent-Image
setzt die JVM-Einstellung nun selbst, der Fixture-Launcher ebenfalls. Der
Netzwerktest verlässt sich auf die Image-Vorgabe und mountet nur seine
synthetische Fixture und kompilierte Prüfklassen, keinen Entwicklungscheckout.
Er besteht mit den neu gebauten ARM64- und AMD64-Images. Die Gegenprobe mit
Bridge-Netzwerk wird weiterhin abgewiesen; die Prüfung wurde nicht gelockert.

Ein neues quellcodefreies Betriebspaket wurde installiert und `init` ohne
`--skip-smoke` ausgeführt: `ready=true`, `host-loopback-verified` und gültiger
Smoke-Bericht. Die Prüfung verwendet die tatsächlichen Fachwerkzeuge und
umfasst Modellableitung, Modell-/CSV-Positivprüfung sowie CSV-Negativprüfung.
Auch die synthetischen Eingänge des Init-Selbsttests durchlaufen jetzt den
verifizierten Anhangimport; die Pfadsperren bleiben wirksam. Die Fixture
enthält weder `src`, `build` noch `gradlew`. `doctor` bestätigt anschliessend
`ready=true` und `smoke_current=true`. Alle 210 Unit-Tests und die Formatprüfung
bestanden erneut. Es gibt keinen Host-JDK-Fallback. Der oben beschriebene
Codex-GUI-/Fachsitzungsnachweis bleibt separat offen.

Die Nachweise dieser Korrektur liegen als `loopback-*.log`, `loopback-init.json`
und `loopback-fixture.path` unter dem unten genannten lokalen Nachweisordner.

Lokale Nachweise liegen unter `.datenportal-integrator/operator-verification/`.
Das Paket und die Pipeline sind vorbereitet; diese Arbeit veröffentlicht kein
neues Image und kein CI-Artefakt. Das lokale Paket trägt `0.1.0-dev`; regulärer
Betrieb mit dem Paket-Launcher verlangt eine veröffentlichte Versionskennung.

## Betriebsdokumentation und versionierter Image-Smoke vom 13. Oktober 2026

Die README und beide Anwenderhandbücher führen jetzt vom Anlegen von
`~/datenportal/` über die Installation in den leeren Betriebsordner bis zu
Client-Start, CSV-Eingang und Paketupdate. Entwicklung hat einen separaten
Einstieg. Die Paketprüfung verwendet zusätzlich die vorhandene vollständige
Dokumentationsprüfung, einschliesslich Überschriftenankern im gebauten Paket.

Tatsächlich geprüft:

- `test jar spotlessCheck operatorWorkspaceArchive` besteht mit 210 Unit-Tests,
  darunter Repository- und Paket-Linkprüfungen. Die SHA-256-Prüfung des erzeugten
  Archivs besteht. Der abschliessende versionierte Image-Smoke besteht auf ARM64.
- Die mit OpenCode Desktop ausgelieferte macOS-CLI unter
  `/Applications/OpenCode.app/Contents/Resources/opencode-cli` meldet 2.0.27;
  ihre Hilfe bestätigt das Verzeichnisargument. Projekt-Extensions und die
  `mcp list`-Location-Grenze beruhen auf den oben dokumentierten Client-Prüfungen.
  Die aktuelle offizielle Codex-MCP-Dokumentation bestätigt die Voraussetzung
  einer vertrauenswürdigen Projektkonfiguration.
- Installation per `mkdir`, Docker-Bind-Mount, Host-UID/GID und
  `workspace install /workspace` in leere isolierte Testordner mit dem lokal
  gebauten Image `datenportal-operator:docs-verify` (Version 0.1.18) bestanden.
  Das Paket enthält keine Integrator-Quellen oder Gradle-Launcher.
- Standardpfade und externe fachliche Checkouts mit Leerzeichen wurden mit
  ausdrücklich synthetischen Pfadfixtures geprüft. Beide Verzeichnisse sind im
  Container lesbar; die Vorschläge für beide MCPs enthalten die benötigten
  Runtime-/Mount-Argumente. Wiederholtes bedingtes Kopieren der Konfigurationsvorlage
  erhält die bestehende Datei. Das ist kein neuer vollständiger `init`- oder
  Liefernachweis; der vollständige Init-Nachweis steht im vorherigen Abschnitt.
- Der Fehler aus CI-Lauf 38374183353 wurde lokal reproduziert: Der ursprüngliche
  Image-Smoke startet eine unversionierte Klasse im selben Java-Package wie
  `Main`. Deren zuerst definierte Package-Metadaten lassen `Main.version()` auf
  `0.1.0-dev` zurückfallen. Mit 0.1.18 führt dies zu
  `operator_package_version_mismatch`; lokale Dev-Versionen verdeckten den Fehler.
  Ein vorgeschalteter Launcher im Default-Package lädt jetzt zuerst `Main` aus
  dem versionierten JAR. Der korrigierte Smoke mit 0.1.18 besteht; die eigentliche
  Versionsprüfung und alle Betriebsregeln bleiben unverändert.

Veröffentlichung und Grenzen:

- Der tatsächliche Pull von `latest` lieferte beim Test noch 0.1.17.
  Der dokumentierte Installationsaufruf meldete dort `configuration_missing`,
  bevor ein Paket installiert wurde. CI-Lauf 38374183353 ist beim Image-Smoke
  fehlgeschlagen; die `latest`-Promotion wurde übersprungen. Die neue Anleitung
  setzt ausdrücklich eine erfolgreiche Paketveröffentlichung voraus.
- Die lokalen Pfadfixtures ersetzen weder neue GUI-Fachdialogtests noch eine
  Registry-Veröffentlichung. Es wurden keine Geschäftsdaten geliefert und keine
  fachlichen Freigaben erteilt. Dieser Auftrag enthält keinen Commit oder Push.

Lokale Nachweise: `.datenportal-integrator/documentation-verification/` für
Installations-/Pfadprüfungen und
`.datenportal-integrator/operator-verification/docs-*.log` für Builds,
Dokumentationsprüfungen und den reproduzierten beziehungsweise korrigierten
versionierten Image-Smoke.

## Dauerhafter Pilotdaten-Eingang (2026-10-13)

Entscheidung: Der vorhandene Pilotdatenordner wird gezielt read-only eingebunden.
Neue Installationen verwenden `../datenportal-pilot-daten` relativ zum
Betriebsordner. Ein zusätzlicher lokaler Import-MCP samt Laufzeit entfällt;
ein Home-Mount würde unnötig private Dateien und Entwicklungsquellen lesbar
machen. Die drei Prüfsummen bleiben erhalten, da ihr Wegfall keinen fehlenden
Containerzugriff behebt. CLI und MCP-Schnittstellen bleiben unverändert.

Tatsächlich geprüft:

- Im bestehenden Betriebspaket 0.1.20 unter
  `/Users/stefan/datenportal/datenportal-betrieb` zweimal vollständiges `init`
  mit den vorhandenen Themenrepo-/Dev-Stack-Mounts und
  `--mount-ro /Users/stefan/sources/datenportal-pilot-daten`: jeweils `ready=true`.
  `doctor` bestätigt `ready=true`, `smoke_current=true`, gültige Datenblatt-,
  INTERLIS- und GRETL-Werkzeuge sowie `host-loopback-verified`.
- Die erzeugten Startargumente beider MCPs in Codex und OpenCode enthalten nach
  erneuter Einrichtung den Read-only-Eingang und die beiden bisherigen
  Read-write-Checkouts. Das Beibehalten gilt bei erneuter Angabe aller Optionen;
  ein späteres `init` ohne diese Optionen ist keine Mount-Persistenzprüfung.
- Isolierte synthetische Fixtures mit dem veröffentlichten Image 0.1.20:
  Installation eines quellcodefreien Betriebspakets, Erzeugung der Client-Profile
  über `harness-config`, echter stdio-Aufruf von `import_attachment` für einen
  Geschwisterordner und einen externen absoluten Ordner mit Leerzeichen.
  Beide Importe liefern `verified=true`, drei identische SHA-256-Werte und
  unveränderte Originale/Kopien. Schreibversuche auf die Eingänge sind sowohl
  im Container als auch über `write_file` abgewiesen. Der Read-only-Mount des
  Geschwisterordners überlagert wirksam den schreibbaren Eltern-Mount.
- `test jar spotlessCheck operatorWorkspaceArchive` prüft die Dokumentation,
  Paketverweise und Überschriftenanker sowie die bestehenden Unit-Tests.
  Das erzeugte Paketarchiv wird zusätzlich gegen seine SHA-256-Datei geprüft.

Grenzen und Einführung:

- Dies sind Konfigurations- und MCP-/Container-Nachweise, keine neuen vollständigen
  Fachdialognachweise für Codex Desktop/CLI oder OpenCode Desktop/gebündelte CLI.
  Beide MCP-Verbindungen und bestehende Clientsitzungen müssen neu gestartet werden.
- Keine Geschäftsdateien verschoben oder geliefert, keine fachlichen Freigaben
  erteilt und keine bestehenden Vorgänge migriert. Der erste isolierte
  MCP-Testaufbau scheiterte wegen noch nicht erzeugter Client-Profile; nach deren
  Einrichtung über die vorhandene Schnittstelle bestehen die Tests.
- Die aktualisierte Dokumentation liegt im Repository und im neu gebauten Paket.
  Verwaltete Dateien des installierten Betriebspakets 0.1.20 wurden nicht manuell
  überschrieben. Die Dokumentation wird dort mit einer späteren regulären
  Veröffentlichung und Paketaktualisierung verfügbar; die Mount-Einrichtung ist
  bereits mit 0.1.20 wirksam. Kein Commit, Push oder Release in diesem Auftrag.

Lokale Nachweise: `.datenportal-integrator/pilot-verification/` (Testskripte,
Import- und Client-Ergebnisse), `.datenportal-integrator/pilot-doc-checks.log`
(Build) sowie `.datenportal-integrator/setup/pilot-*.json` und `pilot-*.log`
im eingerichteten Betriebsordner (`init`, erneutes `init`, `doctor`).

## Java-21-Umstellung vom 13. Oktober 2026

Implementiert ist die Trennung von unveränderlicher Java-Distribution,
quellcodefreiem Betriebsarbeitsbereich und Entwicklungscheckout. Integrator und
beide MCPs laufen mit JDK 21; lokale CSV-/XTF-Prüfung benötigt kein GRETL-Image.
Jenkins verwendet weiterhin sein eigenes GRETL. GitHub-PR-Operationen verwenden
REST; Git authentisiert HTTPS über den Java-Credential-Helper. Die MCP-Repositories
behalten ihre Docker-Pipelines und ergänzen ausführbare Maven-JARs.

Tatsächlich ausgeführt unter macOS mit JDK 21.0.7:

- 182 Integrator-Unit-Tests und `spotlessCheck` erfolgreich, darunter REST-Fehler,
  Rate-Limit, mehrdeutige PR-Antwort, Git-Credential-Protokoll sowie echte lokale
  CSV-Positiv-/Negativprüfungen. REST und Git-Zugänge verwenden isolierte lokale
  Testserver bzw. synthetische Zugangsdaten; kein echter PR wurde erstellt.
- Alle 16 Integrationstests mit echten MCP-JARs und konfigurierter lokaler
  Jenkins-Runtime erfolgreich. Ein vorheriger CLI-Test erreichte einmal sein
  Zeitlimit; Einzelwiederholung und anschliessender vollständiger Lauf bestanden.
- Paketinstallation in einen Pfad mit Leerzeichen, `init`, `doctor`, beide
  MCP-Starts, Modellableitung, CSV-/XTF-Prüfung, Paketupdate und erneutes `init`
  erfolgreich. Verwendet wurden ausdrücklich markierte lokale Entwicklungs-JARs.
- Datenblatt-MCP: `check bootJar`; INTERLIS-MCP: `test e2eTest bootJar`
  erfolgreich unter JDK 21. Für beide zusätzlich lokale Maven-Veröffentlichung,
  Rückdownload, Bytevergleich, MCP-Initialisierung, Werkzeugliste und EOF-Ende.

Offen sind die öffentlichen Maven-Veröffentlichungen, die neuen CI-Läufe auf
Linux und macOS sowie die erneuten Multiarch-Image-Pipelines. Die ergänzten
Publikationsschritte prüfen die veröffentlichten Artefakte durch Rückdownload;
die Integrator-Pipeline installiert und prüft auch das zurückgeladene Paket.
Benötigt werden `MAVEN_USERNAME` und `MAVEN_PASSWORD` in den publizierenden
Repositories. Es wurden keine realen Freigaben erteilt, keine INT-/PROD-Lieferungen
gestartet und keine Altvorgänge migriert. Bestehende externe Vorgänge bleiben mit
ihrer bisherigen Runtime aufzuklären.

Zusätzlich tatsächlich ausgeführte isolierte Lieferabnahme:

- Java-Betriebsarbeitsbereich mit zwei unabhängigen Integrator-stdio-Verbindungen
  entsprechend den Codex-/OpenCode-Konfigurationen; keine GUI-Dialogabnahme.
- Echte MCP-Modellableitung, lokale Validierung, synthetische Testfreigaben,
  Jenkins-Seed und positive Lieferung bis Portal, Manifest und geprüften
  CSV-/XLSX-/Parquet-Downloads erfolgreich.
- Ungültige CSV scheitert in Jenkins an GRETL-CsvValidator vor der Publikation;
  das Manifest des erfolgreichen Releases bleibt unverändert. Infrastruktur-
  Container werden während der Lieferung nicht ersetzt oder neu gestartet.
- Der Testlauncher wurde nach zwei Startfehlern (Arbeitsverzeichnis und doppelte
  Konfigurationsübergabe) korrigiert und durch einen Regressionstest abgesichert.
  Der anschliessende vollständige Lauf meldet `valid=true`; die eigenen
  Testcontainer und Testsecrets wurden danach erfolgreich bereinigt.

Nachweis: `.datenportal-integrator/delivery-acceptance/3ee3b60f-764f-41d9-ad14-aba83408c543/report.json`,
mit separat bereinigten positiven/negativen Jenkins-Konsolen und `cleanup.log`.
Dies ist ein real ausgeführter technischer Test mit synthetischen Daten und
markierten Testfreigaben, keine fachliche Lieferung und keine Benutzerabnahme.

Nachtrag zur öffentlichen CI und Publikation vom selben Tag:

- Integrator-Prüfungen auf Linux und macOS erfolgreich:
  https://github.com/edigonzales/datenportal-themenintegrator-agent/actions/runs/38429038191
- Datenblatt-MCP `0.2.3`: JAR-Rückdownload, Startprüfung und beide Dockerimages
  erfolgreich: https://github.com/sogis/datenportal-datenblatt-editor/actions/runs/38430221469
- INTERLIS-MCP `0.0.577-1`: JAR-Rückdownload, Modulprüfung und beide Dockerimages
  erfolgreich: https://github.com/edigonzales/interlis-mcp/actions/runs/38430219784
- Beide MCP-Pipelines prüfen nun ausdrücklich die Publikation gespeicherter
  JAR-Dateien. Ein deaktivierter Archivtask hatte zunächst nur die POM publiziert.
- Betriebspaket `0.2.2` nicht verwenden: Der erste Rückdownloadtest entdeckte
  fehlende Ausführungsrechte des Launchers im TAR. Das TAR setzt diese nun
  ausdrücklich; die CI prüft bereits vor dem Upload das entpackte Archiv.
  Der korrigierte Archivtest mit den oben veröffentlichten MCP-Versionen bestand
  lokal einschliesslich Installation, Validierung und Update. Die korrigierte
  öffentliche Ausgabe wird als neue Version über dieselbe Pipeline publiziert.

## 2026-10-13: Java-Eingangsverzeichnisse statt Mount-Anweisungen

Die aktive Eingangsdatei-Anleitung und beide Handbuchfassungen beschreiben den
Import durch den Java-Datei-MCP und gezielte `input_roots`. Persönliche Pfade und
Docker-Mount-Anweisungen wurden daraus entfernt. Der öffentliche Fehlercode für
nicht freigegebene Eingänge heisst jetzt `input_path_not_allowed`; die bisherigen
Pfadgrenzen, die Umgebungsvariable `DATENPORTAL_INPUT_ROOTS` und die drei
Prüfsummen bleiben erhalten. Historische Docker-Abnahmen oben bleiben unverändert.

Tatsächlich lokal geprüft: 183 Java-Tests, `jar`, `spotlessCheck` und der gezielte
`WorkspaceMcpIntegrationTest` erfolgreich. Der neue synthetische Test deckt
abgewiesene externe Pfade, absolute und relative Freigaben, einen Symlink-Ausbruch,
fehlende Quellen und unveränderte Bytes mit drei gleichen Prüfsummen ab. Bestehende
Installation-/Update-Tests prüfen den Erhalt lokaler Konfiguration und Vorgänge.
Die erzeugten Paketdokumente enthalten keine alten Mount-Aufforderungen.
Der Paket-Smoke-Test vergleicht nun die lokale Konfiguration vor und nach Update.

Zum Commit-Zeitpunkt noch offen: CI, Veröffentlichung, Download-Prüfung und Update
des bestehenden Betriebsordners mit Prüfung über das veröffentlichte Paket.
Diese Betriebsabnahme wird unter `.datenportal-integrator/setup/` im Betriebsordner
protokolliert. Es wird kein fachlicher Integrations- oder Publikationslauf ausgelöst.

## 2026-10-13: Repository-Datenblatt lesen, Modellreferenz separat vorbereiten

`repository_datasheet` erkennt Datenblattobjekte anhand des INTERLIS-Namespace
und Themenidentifiers, unabhängig vom Dateinamen. Die gesicherte Repository-Quelle
bleibt vom publizierten Vergleichsstand getrennt. Mehrdeutige Kandidaten,
unlesbares XML, externe Symlinks und konkurrierende Änderungen werden sichtbar
behandelt. Auch `.XML` und `.XTF` bleiben beim späteren Staging unter ihrem Namen.

`derive_model` eröffnet keinen Datenblatt-Entwurf mehr. `validate_model(scope=model)`
speichert eine Zwischenprüfung ohne Freigabewirkung. Erst
`prepare_model_reference` bereitet die Modellreferenz über den Fach-MCP vor;
der Default `validate_model(scope=integration)` prüft weiterhin das gesamte Paket.
Der technische Init-Smoke und die synthetische Lieferabnahme verwenden diese
Reihenfolge ebenfalls. `operation_schema(metadata.<operation>)` beschreibt den
Integrator-Vertrag anhand des öffentlichen Fach-MCP-Katalogs; intern verwaltete
Entwurfs-IDs und Revisionen werden nicht als Benutzerargumente ausgegeben.

Tatsächlich geprüft: 191 Unit-Tests, `jar`, `spotlessCheck` erfolgreich. 17
Integrationstests mit veröffentlichten MCP-JARs und ilivalidator erfolgreich,
einschliesslich getrennter Modellprüfung, Referenzvorbereitung, finaler Prüfung,
MCP-Neustarts und isoliertem GRETL-Task im vorhandenen Jenkins. Kein fachlicher
Upload oder Publikationslauf. Die anfänglichen Testkonfigurationsfehler wurden
behoben (Java-Konfiguration, expliziter Validator und vorhandener Dev-Stack-Pfad).

Separat isoliert geprüft: Kopien des Fahrzeugbestand-Datenblatts und der CSV,
Datenblatt umbenannt zu `beliebiger_Name.XML`. Inhaltliche Suche und CSV-Prüfung
waren erfolgreich; kein Datenblatt-Entwurf oder Fach-MCP-Import. Die Ableitung
lief bis `NEEDS_INPUT` für noch nicht festgelegte Wertebereiche/Textlängen.
Keine Fachsemantik wurde dafür erfunden; Originale blieben unverändert. Dieser
Test ist kein Nachweis eines abschliessend fachlich bestätigten MFK-Modells.
Der lokale Nachweis liegt in `build/datasheet-acceptance/mfk-source-report.json`.

Zum Commit-Zeitpunkt noch offen: CI-Publikation und Abnahme der veröffentlichten
Version im bestehenden Betriebsordner. Nachweise dafür werden dort unter
`.datenportal-integrator/setup/` gespeichert. Der bestehende fachliche Vorgang
wird dabei nur gelesen; seine nächsten Schritte bleiben beim Fachagenten.

## 2026-10-13: MCP-Verbindungen nach parallelen Antworten stabil halten

Der Stillstand in OpenCode wurde mit dem veröffentlichten Paket 0.2.8 reproduziert:
Bei 400 gleichzeitig ausstehenden Datei-Aufrufen kam nur eine Antwort zurück.
Der SDK-Fehler `Failed to enqueue message` beendete die Verarbeitung, während der
Hauptthread weiter auf stdin-EOF wartete. Der Prozess blieb deshalb sichtbar,
obwohl er keine weiteren Werkzeuge ausführte. Auslöser sind konkurrierende
Antworten im stdio-Transport des Java-MCP-SDK 2.0.1, kein Leerlauf-Zeitlimit.

Beide öffentlichen Server serialisieren jetzt das Einreihen ihrer Antworten;
die Werkzeuge selbst bleiben parallel ausführbar. Auch asynchrones Einreihen
wird vollständig abgewartet. Es gibt keinen automatischen Wiederholungsversuch
für fachliche Operationen. Ein fataler Transportabbruch beendet den Prozess auch
dann, wenn der Client stdin noch offen hält, und gibt wartende Sender frei.

Tatsächlich lokal geprüft: 193 Unit-Tests, Formatprüfung und der neue unabhängige
MCP-Prozesstest erfolgreich. Je Server wurden 1'200 gemischte erfolgreiche und
abgewiesene Aufrufe mit eindeutigen Antwortkennungen sowie anschliessende Pings
geprüft. Zusätzlich: EOF, fataler Protokollfehler ohne EOF, asynchroner
Enqueue-Fehler und Freigabe wartender Sender. Die Prozessregression läuft künftig
in der Linux- und macOS-CI. In einer isolierten OpenCode-Sitzung kamen alle sechs
parallelen Lese-/Schemaaufrufe einschliesslich erwarteter Fehler korrekt zurück.

Auch alle 19 Integrationstests mit den konfigurierten Fach-MCPs, ilivalidator
und dem isolierten Jenkins-Validierungstask sind erfolgreich.
Zum Commit-Zeitpunkt sind Veröffentlichung, Paketupdate und der längere
OpenCode-Verbindungstest noch offen.
Die Ergebnisse der installierten Version werden im Betriebsordner unter
`.datenportal-integrator/setup/mcp-stability-release-check.json` festgehalten.
Technische Tests führen keine fachlichen Freigaben, Lieferungen oder Publikation aus.

## Modellierungsargumente und Wiederaufnahme vom 13. Oktober 2026

### Tatsächlich geprüft

- Die OpenCode-Sitzung zum Fahrzeugbestand hatte echte menschliche Bestätigungen
  für die Zahlenbereiche und Textlängen, aber speicherte `NUMERIC` mit Grenzen
  und `TEXT.maxLength`. Der Fach-MCP lieferte `INVALID_SPEC`; anschliessend
  verhinderte der Integrator sogar die gleichwertige technische Korrektur.
- Der Integrator prüft die verschachtelten Spaltenargumente, normalisiert eindeutig
  gleichwertige Numerik-/Textdarstellungen und protokolliert Korrekturen alter
  gespeicherter Mappings. Abweichende bestätigte Werte bleiben gesperrt.
- Zurückgewiesene Spezifikationen ersetzen keinen Modellkandidaten; Argumentfehler
  werden getrennt von Compiler-/CSV-Prüfungen diagnostiziert und verbrauchen kein
  Reparaturbudget. Ein Budget-Reset löscht weiterhin keine Fachregeln.
- 198 Unit-/Funktionstests und 19 Integrationstests mit den tatsächlichen
  paketierten MCP-JARs bestanden; `spotlessCheck` ebenfalls.
- Installation, `init`, `doctor` und Update des erzeugten Pakets bestanden in
  einem temporären Betriebsordner; lokale Konfiguration blieb bytegleich.
- Eine isolierte Kopie von Fahrzeugbestand-CSV und Repository-Datenblatt wurde
  mit den Zahlenbereichen/Textlängen aus dem betroffenen Vorgang modelliert und
  erfolgreich gegen die CSV geprüft. `FZART_CD` bleibt `MANDATORY TEXT*2`.
  Es entstand kein Datenblattentwurf; Originaldateien blieben unverändert.

### Synthetische Entscheidungen und Auslieferungsnachweise

Die automatische CSV-Freigabe im Fahrzeugbestand-Test gilt ausschliesslich für
seinen ausdrücklich markierten temporären Testvorgang. Es erfolgte keine fachliche
Übernahme oder Publikation. Lokale Nachweise liegen unter
`build/model-argument-acceptance/` (Ableitung, Validierung, Zusammenfassung und
Paketprüfung). Veröffentlichungs-, Installations- und Wiederaufnahmenachweise
werden separat im Betriebsordner unter `.datenportal-integrator/setup/` mit dem
Präfix `model-arguments-` abgelegt. Der echte Vorgang behält seine tatsächlichen
menschlichen Freigaben; die separat bestätigte Modellidentität lautet
`SO_Fahrzeugbestand_20261013`, Version `2026-10-13`.

## 2026-10-14 – kompakte MCP-Ergebnisse und sichtbare Prüfberichte

Ursache in OpenCode: JSON-Textantworten enthielten vollständige HTML-Dateien,
Diffs und wiederholte Diagnostikhistorien. OpenCode kürzte eine lange JSON-Zeile
auf null sichtbare Zeilen; der Zugriff auf seine ausgelagerte interne Datei war
für den Fachagenten nicht erlaubt. Der aktive Vorgang hatte zusätzlich den
unabhängigen Fehler `issue_required` (fehlende Ausgabenbezeichnung).

Die MCP-Darstellung lagert grosse Ergebnisse unverändert in lesbare
Antwortdateien aus, hält Fehler und kompakte Prüfergebnisse sichtbar und liefert
mehrzeiligen JSON-Text. `read_file` bietet begrenzte Zeichenabschnitte samt
`next_offset`, auch für Berichte über 512 KiB. `open_report` öffnet vorhandene
Prüfberichte im lokalen Browser. Zustandsdateien und fachliche Freigaberegeln
bleiben unverändert; ausgelagerte Dateien gewähren keinen Zugriff auf Secrets
oder beliebige JSON-Dateien. Regressionen prüfen grosse Fehlerantworten,
verlustfreies abschnittweises Lesen, grosse/einzeilige Berichte und Pfadgrenzen.

Lokale Abnahme: 202 Java-Tests und 19 Integrationstests bestanden, darunter
STDIO-Paginierung eines 600-KiB-Berichts mit den tatsächlichen MCP-Servern.
Formatprüfung und synthetische Paketinstallation/init/doctor/update bestanden;
vorhandene lokale Konfiguration blieb beim Update unverändert. Der erste
Fachintegrationstestlauf verwendete den Betriebsordner ohne gesetzten Paketpfad
und scheiterte an fehlenden Werkzeug-JARs. Mit dem expliziten Testpaket als
`DATENPORTAL_AGENT_HOME` bestanden alle 19 Tests ohne Auslassung.

## 2026-10-14 – Ausgabe im bestehenden Vorgang setzen

`set_issue` ergänzt die fehlende öffentliche Operation für bestätigte
Ausgabenbezeichnungen. Sie erhält Vorgangskennung, Dateien, Modell, Entwurf und
Historie. Geänderte Ausgaben archivieren und entwerten Prüfungen/Freigaben/Pläne;
identische Wiederholungen sind wirkungslos. Ungültige Argumente, unklare
MCP-Mutationen sowie bereits übernommene/extern eingereichte Vorgänge werden ohne
Teiländerung abgewiesen. Rückwechsel auf eine frühere Ausgabe reaktivieren keine
alte Freigabe. Fehlermeldung und Skill verweisen direkt auf `set_issue`, nicht
auf einen erneuten `start`. Isolierte Tests decken Zustands- und Artefakterhalt,
Idempotenz, Rückwechsel, Sperren und den tatsächlichen STDIO-Aufruf ab.

Lokale Prüfung: 204 Java-Tests, Formatprüfung und Paketinstallation/update mit
init/doctor bestanden. 18 Fachintegrationstests bestanden im ersten Lauf; der
STDIO-Workspace-Test wurde wegen unterschiedlicher Versionen von Test-JAR und
Workspace-Manifest abgewiesen. Mit einheitlichem `agentVersion=0.2.17-dev`
bestand auch dieser Test einschliesslich `set_issue` und anschliessendem Status
im selben Vorgang. Es wurden keine fachlichen Freigaben oder Publikationen
zur Testabnahme ausgelöst.

## 2026-10-14: Ein vorhandener GRETL-CSV-Validator

Der Integrator konfiguriert ausschliesslich `validateDeliveredCsv` mit Semikolon,
Anführungszeichen, UTF-8, Kopfzeile und Themenmodellverzeichnis. Der zusätzliche
Validator, Copy-Task sowie die Gradle-Byte-/Modell-Hash-Prüfungen entfallen.
Java-Vorprüfung und Freigabefingerprints bleiben bestehen. Ein unveränderter
historischer Integrator-Block wird bei `validate_model` als neuer Kandidat
ersetzt; Modell und Datenblatt bleiben unverändert, neue Freigaben sind nötig.

Tatsächlich geprüft: 206 Java-Tests, Formatprüfung und 19 Integrationstests mit
den konfigurierten MCP-JARs und dem vorhandenen Jenkins-GRETL bestanden.
Die isolierte GRETL-Fixture verwendet zwei Spalten, Semikolon und den Textwert
`01`. Der echte Publikationstask akzeptiert die gültige CSV, stoppt beim
ungültigen Zahlenwert vor `prepareCsvConversion` und wird bei Metadatenlieferung
übersprungen. Für diesen gezielten Test wird nur das Ergebnis von
`mergeDatasheets` als synthetische Fixture bereitgestellt; es gibt keinen Upload.
Paketinstallation, beide MCPs, init, doctor und Update mit unveränderter lokaler
Konfiguration wurden in einem temporären Betriebsordner geprüft.

Zusätzlich berücksichtigt der Paket-Build die Version als Task-Eingabe, damit
inkrementelle Builds keine veraltete Workspace-Versionsangabe übernehmen.
Frühere Testversuche mit veralteten lokalen Pfaden und einer falschen
Dateiberechtigung der synthetischen Container-Fixture wurden korrigiert; der
abschliessende vollständige Integrationstestlauf war erfolgreich.

Der tatsächliche mfk-Lauf (Build 3, Queue 590) wurde nur abgefragt und als
fehlgeschlagen/nicht publiziert erfasst. Eine erneute fachliche Lieferung sowie
Portal-/Download-Abnahme nach der Korrektur bleiben bis zur neuen Freigabe offen.

## 2026-10-14: Paketpfadunabhängige Freigaben

Freigabefingerprints verwenden logische Ressourcenpfade und Inhalte der
Prüfregeln, Modelle und Validatoren. Paketpfad, Integrator-JAR und technische
Laufzeiteinstellungen sind keine fachlichen Prüfeingaben. Änderungen an der
Prüfsemantik erfordern eine neue Revision in `validation/review-policy.json`.

Tatsächlich geprüft: 213 Java-Tests und Formatprüfung bestanden; 19
Integrationstests mit den konfigurierten Werkzeugen bestanden. Paketinstallation,
init, doctor, beide MCPs und Update mit Konfigurationserhalt bestanden.
Eine isolierte Speicherkopie des bestehenden mfk-Vorgangs wurde vom bekannten
Format aus Paket 0.2.20 überführt: acht Fingerprint-Felder wurden ersetzt,
alle drei Freigaben und die abgeschlossene Lieferung blieben aktuell.
Die Migration erzeugt keine Freigaben, erhält Aussagen/Zeitpunkte und
protokolliert die bisherigen Freigaben. Veränderte Fachinhalte oder Prüfregeln
werden nicht durch die Migration freigegeben. Folgeupdates benötigen nach
vollständiger Migration kein altes Paket mehr.

Veröffentlichung und Aktualisierung des tatsächlichen Betriebsordners stehen
zum Zeitpunkt dieses Eintrags noch aus; diese Prüfung löst keine erneute
Publikation aus. Die inzwischen separat freigegebene lokale mfk-Publikation
(Build 4) bleibt vom Update unberührt.

## Lokale Repository-Synchronisierung nach PR-Merge (2026-10-14)

Implementiert: `sync_repository(run_id, environment)` sowie Synchronisierung vor
neuen Seed-/Lieferungsstarts mit PR; lesender Status mit `repository_sync`.
Repositoryweite Sperre, dauerhaftes Wiederaufnahmejournal, gezielte Rücksicherung
und Fast-forward ohne pauschales Reset, Clean oder Stash.

**Automatisierte, isolierte Prüfung:** temporäre Git-Repositories und synthetische
GitHub-/Jenkins-Antworten. Geprüft werden normaler Merge, Squash und Rebase,
Übernahmenachweise, neue Dateien, fremde staged/unstaged/untracked Änderungen,
Konflikte, abweichender Branch/HEAD, veränderte Zielbytes, Fehler-Rücksicherung,
Prozessabbrüche vor/nach Fast-forward, Wiederholung, Sperren, blockierte neue
externe Starts und weiter mögliche Abfrage bestehender Seeds. Hinzu kommen
ignorierte Pfadkollisionen, versteckte Indexänderungen, ungewöhnliche Dateinamen
und leere Dateien. Diese Tests stellen keine realen fachlichen Freigaben dar.

**Noch offen:** Abnahme mit einem realen, menschlich gemergten PR im installierten
Betriebspaket. Im Rahmen der Implementierung wurden keine realen Vorgänge
synchronisiert, keine PRs gemergt und keine Checkouts gelöscht.

## 2026-10-15: Modelländerungsvertrag und CSV-Neuprüfung

Implementiert: `operation_schema(derive_model)` und
`operation_schema(derive_model.changes)` beziehen den vollständigen Änderungsvertrag
lesend aus dem konfigurierten INTERLIS-MCP. Referenzen werden im Integrator-Schema
vollständig erhalten. CLI und MCP liefern denselben Vertrag. Fehlende oder nicht
auflösbare Verträge führen zu `model_contract_unavailable`.

`validate_model` prüft neue, freigegebene CSV ohne Modelländerungsbatch. Die
ursprüngliche Ableitungsquelle bleibt erhalten; nur erfolgreiche Gesamtprüfungen
aktualisieren `validated_data_sha256`. Aktuelle CSV-Beobachtungen und ursprüngliche
Ableitungsbeobachtungen sind im HTML getrennt. Bei fachlichen Änderungen werden
gespeicherte Bereichsvorschläge bei unverändertem Vertrag wiederverwendet, ohne
sie zu menschlichen Bestätigungen aufzuwerten.

Tatsächlich ausgeführte technische Prüfungen:

- JDK 21: `bin/datenportal-agent gradle test jar spotlessCheck` erfolgreich;
  233 Tests, keine Fehler und keine übersprungenen Tests.
- `ModelRevalidationIntegrationTest` mit expliziter isolierter Konfiguration
  `build/model-contract-runtime/integration.toml` und den MCP-/Validator-JARs aus
  Betriebspaket 0.2.24, INTERLIS-MCP 0.0.577-1: echter Schemaabruf und Vergleich
  von CLI/MCP, echte Modellableitung, Datenblatt-Referenz, CSV-Neuprüfung ohne
  Authoring-Aufruf, anschliessende UNIQUE-Ergänzung ohne erneut übermittelte
  Zahlenvorschläge, positive CSV-Prüfung sowie negative Prüfungen für doppelte
  Schlüssel und Wertebereichsverletzung erfolgreich.
- Regressionen für alte Vorgänge ohne neue Prüffelder, Wiederaufnahme, stabile
  Fingerprints, fehlende Datenfreigabe, geänderten Vertrag und unvollständige
  Modellnachweise sind enthalten.

Simuliert: Alle Geschäftsdatensätze, Zustimmung zu potenziell brechenden
Änderungen und Freigaben stammen ausschliesslich aus isolierten, ausdrücklich
gekennzeichneten Testfixtures. Unit-Tests verwenden teilweise MCP-/Prozessdoubles;
der genannte Integrationstest verwendet echte MCPs und Validatoren.

Offen/nicht Bestandteil dieser Abnahme: Installation eines neuen Betriebspakets,
Fortsetzung der laufenden OpenCode-Session und lokale/INT/PROD-Publikation.
Bestehende Geschäftsvorgänge und andere Komponenten wurden nicht verändert.

## 2026-10-15: GitHub-Token im Workspace-Credential-Store

Tatsächlich ausgeführte technische Prüfungen:

- JDK-21-Build über `bin/datenportal-agent gradle test jar spotlessCheck`
  erfolgreich: 237 Tests, keine Fehler oder übersprungenen Tests.
- CLI und Store: stdin-Eingabe, expliziter Env-Import, Rotation, Entfernen,
  Status ohne Token, Eingabegrenzen, Priorität GH_TOKEN vor GITHUB_TOKEN vor
  Store sowie defekte und unsichere Stores geprüft. Gesetzte Env-Tokens umgehen
  defekte Stores bei der Authentifizierung.
- Bestehende Jenkins-Einträge bleiben erhalten, auch bei parallelen Schreibzugriffen.
- Echter Git-Prozess mit Java-Credential-Helper: alle drei Tokenquellen,
  abweichendes Arbeitsverzeichnis und Konfigurationspfad mit Leerzeichen geprüft.
  Fremde Hosts erhalten keine Tokens; gespeicherte Tokens stehen nicht in der
  vom Integrator erzeugten Git-Prozessumgebung oder im Helper-Aufruf.
- REST gegen lokalen HTTP-Testserver: gespeicherter Token im Authorization-Header,
  keine Redirects und kein automatischer Quellenwechsel bei HTTP 401.
- `git diff --check` erfolgreich.

Simuliert: Sämtliche Tokens und GitHub-Antworten stammen aus isolierten
Testfixtures. Es wurden keine echten GitHub-Schreibzugriffe ausgeführt und
keine persönlichen Zugangsdaten importiert.

Offen: Installation eines neuen Betriebspakets im Operator-Workspace und
End-to-End-Abnahme mit echten GitHub-Zugängen. INT/PROD-Publikation ist nicht
Bestandteil dieser Änderung.

## 2026-10-15: Dauerhafte GitHub-Store-Auswahl

Tatsächlich geprüft: `bin/datenportal-agent gradle test jar spotlessCheck`
mit JDK 21 erfolgreich, 238 Tests ohne Fehler oder übersprungene Tests.
Die optionale Einstellung `github_token_source="store"` bleibt nach wiederholter
Client-Konfiguration erhalten und ignoriert beide GitHub-Env-Variablen.
Standard `auto` behält die bisherige Quellenpriorität bei.

Lokal wurde Paket 0.2.28 mit Datenblatt-MCP 0.2.3 und INTERLIS-MCP 0.0.577-1
gebaut und im bestehenden Operator-Workspace installiert. Die Store-Auswahl
wurde in dessen lokaler Konfiguration aktiviert. Der frühere `env -u`-Workaround
wurde entfernt. Zwei anschliessende `init`-Durchläufe meldeten `ready=true`;
der Credential-Status meldete danach `configured_source=store` und
`credentials_source=local_store`. Beide OpenCode-MCP-Prozesse wurden neu gestartet
und ihre Paketpfade auf 0.2.28 geprüft. Tokenwerte wurden nicht ausgegeben.

Simuliert: Regressionstests verwenden synthetische Tokens; die lokale
Initialisierungsprüfung verwendet synthetische Fachdaten. Offen: echte
GitHub-Authentifizierung und Publikation; kein GitHub-Schreibzugriff durchgeführt.

## Eindeutiger lokaler Lieferablauf (2026-10-15)

`approve` lehnt zusätzliche Publikationsfreigaben für Profile mit `kind=local`
mit `local_publication_approval_not_required` ab. Die lokale Lieferung bleibt an
fachliche Freigaben und die Übernahme vorhandener Änderungen gebunden. Für
INT/PROD bleibt der aktuelle lokale Liefernachweis erforderlich; die Meldung
nennt nun den „lokalen Liefer- und Publikationstest“. MCP-Beschreibungen, Skill
und Betriebsanleitungen erklären dieselbe Reihenfolge.

Tatsächlich ausgeführt: `./bin/datenportal-agent gradle test jar spotlessCheck`
(zusammen mit `spotlessApply`), erfolgreich mit 241 Tests. `git diff --check`
ist ebenfalls erfolgreich.

Simuliert: neue isolierte Regressionstests für lokale Profilnamen, Ablehnung ohne
neuen Freigabeeintrag, fehlende/veraltete/unverifizierte und aktuelle lokale
Liefernachweise bei INT/PROD sowie lokale Voraussetzungen vor und nach der
Dateiübernahme. Der bestehende HTTP-Fixture-Test bestätigt die lokale Lieferung
ohne `publish:local`. Sämtliche Freigaben und externen Dienste dieser Tests sind
synthetisch.

Nicht ausgeführt: reale lokale Testpublikation, INT-/PROD-Lieferung und erneute
Fachdialog-Abnahme. Vorhandene fachliche Vorgänge und historische Freigaben wurden
nicht migriert.

### Übernahme ins installierte Betriebspaket

Lokal gebaut und separat installiert als `0.2.28-local-delivery-flow` unter
`/Users/stefan/datenportal/pakete/datenportal-themenintegrator-agent-0.2.28-local-delivery-flow`,
mit unveränderten Fachwerkzeug-Versionen Datenblatt-MCP 0.2.3 und INTERLIS-MCP
0.0.577-1. Dies ist ein lokaler Build, kein veröffentlichtes Release.

Tatsächlich bestanden: isolierter Paket-Smoke (Installation, beide MCPs,
Validierung und Update), `workspace update` für
`/Users/stefan/datenportal/datenportal-betrieb`, danach `init` und `doctor` mit
`initialization.ready=true` und `smoke_current=true`. Konfiguration und alle 21
vorhandenen Vorgangsdateien sind laut SHA-256-Vergleich unverändert. Die
installierten Anleitungen stimmen bytegleich mit den Quellen überein; das
öffentliche `approve`-Schema enthält die neue lokale Freigaberegel.

Nachweise liegen unter `build/operator-update-*`. Bestehende MCP-Verbindungen
wurden nicht beendet; sie müssen neu verbunden werden, um das neue JAR zu laden.
Keine fachliche Lieferung oder Publikation ausgelöst.

## Repository-Abschluss nach lokalem Liefernachweis (2026-10-15)

Implementiert: `repository_plan` zeigt lesend das konfigurierte Git-Ziel und
fehlende Voraussetzungen. `prepare_pr` verlangt fachliche Freigaben und bei
Themenänderungen den aktuellen verifizierten lokalen Liefernachweis, aber keine
Publikationsfreigabe. INT-/PROD-Seed und -Anlieferung behalten ihre eigenen
Publikationsfreigaben. Bestehende Profilparameter bleiben kompatibel.

Tatsächlich geprüft:

- Vollständige Java-Testsuite, JAR-Build und `spotlessCheck` erfolgreich
  (`./bin/datenportal-agent gradle test jar spotlessCheck`; Abschlusslauf zusätzlich
  mit `spotlessApply`).
- Die gezielten Java-Tests `RepositoryTest`, `RepositoryPlanTest` und
  `PublicationApprovalTest` sind erfolgreich.
- Der Repository-Plan lässt Inhalt und Änderungszeit der Vorgangsdatei unverändert;
  fehlende Freigaben und Liefernachweise werden angezeigt. Zugangsdaten in einer
  Remote-Adresse werden ohne Offenlegung abgewiesen.
- Fehlende fachliche Freigaben und fehlende, fehlgeschlagene oder veraltete lokale
  Nachweise blockieren den PR. Nach Datenänderungen genügt eine erneute fachliche
  Freigabe ohne erneuten lokalen Liefernachweis nicht.

Isoliert simuliert:

- Lokale Dateiübernahme, Fortsetzung eines gespeicherten Seed-Laufs, HTTP-Upload
  und Publikationsprüfung gegen einen Loopback-Jenkins-/Portal-Testserver.
- Anschliessend PR ohne `publish:*`, Git-Push ausschliesslich in ein temporäres
  Bare-Repository, simulierte GitHub-Antworten und menschliche Übernahme als Merge,
  Squash oder Rebase; danach echte lokale Git-Synchronisierung.
- Wiederholte PR-Vorbereitung verwendet denselben PR. Fehlende Publikationsfreigabe
  blockiert weiterhin externe Starts trotz gemergtem PR. Modell-/Organisations-
  voraussetzungen werden mit synthetischen fachlichen Freigaben geprüft.

Offen: reale Ende-zu-Ende-Abnahme mit lokalem Docker/Jenkins, echtem GitHub-PR und
menschlichem Merge. Keine reale INT-/PROD-Anlieferung und keine produktiven
Freigaben oder Pushes wurden durch diese Implementierungsprüfung ausgeführt.

## XMLDate-Nachweise und Dateistände (2026-10-15)

Implementiert in Integrator und INTERLIS-MCP: XMLDate-Wertepaare aus dem
Metamodell, vollständige Prüfung unveränderter Modellbytes mit `verifyIliModel`,
Abweisung doppelter Constraint-Namen und gezielte Nachweiserneuerung mit
`validate_model(refresh_evidence=true)`. Modell und Task werden gemeinsam
vorgemerkt. Unvollständige Kandidaten können weder über `stage_change` noch durch
Freigabe als erfolgreiches Paket übernommen werden.

Prüfstände enthalten Inhaltsprüfsummen, Parameter und Komponentenidentitäten.
Berichte, CSV-Prüfeingaben und Prüfprotokolle bleiben als einzelne historische
Stände erhalten. Abweichende Dateien, Komponenten und Repository-Konflikte
blockieren die Verwendung alter Ergebnisse. Tatsächliche Datenfreigaben können
beim Paketwechsel nur bei unveränderter CSV, identischen CSV-Prüfregeln und
belegter menschlicher Antwort erhalten bleiben; dies erzeugt keine neue Freigabe.

Die isolierte eBau-Regression verwendet synthetische CSV-Daten und ausdrücklich
synthetische Datenfreigaben, aber echte MCPs und ilivalidator. Sie rekonstruiert
einen unvollständigen Kandidaten mit fehlender Taskbindung, erneuert dessen
Nachweise ohne Modelländerung, bereitet die Modellreferenz vor und prüft die
Integration. Eine CSV mit doppeltem Monat muss am UNIQUE scheitern. Fehler bei
Paketvorbereitung und Eingabeänderungen während einer Prüfung werden zusätzlich
in isolierten Java-Tests injiziert.

Docker-/Jenkins-Abnahme bleibt optional und wird in der expliziten
Integrationskonfiguration mit `docker_acceptance=true` zugeschaltet. Ohne diese
Option wird dieser Abschnitt als übersprungen ausgewiesen. Ein tatsächlicher
Prozessabbruch durch Betriebssystem/Crash und eine produktive Veröffentlichung
sind durch diese Tests nicht abgenommen.

Lokales Entwicklungspaket: `0.2.28-local-proof-recovery`
(`developmentArtifacts=true`, kein veröffentlichter Release). JDK 21.0.7.
Vollständige INTERLIS-MCP-Tests: 598 erfolgreich. Integrator `test jar
spotlessCheck`: 253 Tests erfolgreich; anschliessend `distributionFiles` mit den
zuvor gebauten MCP-Artefakten.

SHA-256 der lokalen Paketkomponenten:

| Komponente | SHA-256 |
| --- | --- |
| Integrator | `477dbfefa6f30231b4c8c1ad55071fd7d67a4083078c8f16bf9db019fb9326ea` |
| INTERLIS-MCP | `c79bb6c689f1324eda504507f00d4cd52789c71e2eed1809d13612d7ab2c8684` |
| Datenblatt-MCP (unverändert) | `cb4c0dd0c4d4f188f9cc5373edeeddd0a9a152d4cfadae4cc04ea723e032c99a` |
| ilivalidator | `8c984abed5841421ae1008bf3d671f1d1164f1fead93a64b2d82ad251e0d4569` |

Explizite isolierte Integrationskonfiguration:
`build/proof-integration.toml`, Themenrepository-Kopie unter
`build/proof-fixture-repo`, beide MCPs und Validator aus `build/java-distribution`.
Die anfänglichen Läufe deckten eine fehlende Schema-Normalisierung des neuen
MCP-Tools, eine falsche Testannahme zu `skipped` und unvollständige
Testkonfiguration auf; diese Fehler wurden vor der Operator-Aktualisierung behoben.

Abschluss der tatsächlichen lokalen Abnahme:

- `integrationTest` mit obiger expliziter Konfiguration erfolgreich: 21 Tests,
  davon 20 bestanden und der optionale Docker-/Jenkins-Test übersprungen.
- Lokales Paket unter
  `/Users/stefan/datenportal/pakete/datenportal-themenintegrator-agent-0.2.28-local-proof-recovery`
  bereitgestellt; Arbeitsbereich über öffentliches `workspace update` aktualisiert.
- Bestehenden Vorgang `02d53671ddc44c6ebf55c0f394789705` ausschliesslich über
  öffentliche Operationen fortgesetzt: `validate_model(scope=model,
  refresh_evidence=true)`, `prepare_model_reference`, `validate_model(scope=integration)`
  und `validate`. Beide Reviews sind gültig, Modellintegration `approvable=true`.
- Modell mit genau einem UNIQUE bytegleich:
  `35daa444705c394ed5a81958e27d85691357b5a7f03a7c05ef22c4a884d5be77`.
  Modellnachweis `VERIFIED`, CSV-Prüfung tatsächlich ausgeführt und erfolgreich.
- Tatsächliche Datenfreigabe vom 2026-10-15 08:54:10 UTC erhalten; keine neue
  Daten-, Modell- oder Metadatenfreigabe erzeugt. Kein direkter Eingriff in
  gespeicherte Zustandsdateien, keine Übernahme ins Themenrepository und keine
  Veröffentlichung.
- Unveränderlicher Modellreview: `report-history/862a5dff698cae116d56be5e5d97fa2783400fb3eb5ee5a8f1f885993fed6553-model-review.html`.
  Metadatenreview: `report-history/60ce94816bbbeb54f6564c191d7a548ed818f0db069646402e1c2019b38b687e-metadata-review.html`
  (jeweils im bestehenden Vorgangsordner).

Bereits laufende OpenCode-MCP-Verbindungen müssen neu verbunden werden, um das
neue Paket und das ergänzte Operationsschema zu laden. Der Vorgang selbst bleibt
dabei derselbe; keine erneute Ableitung und keine Entfernung der Fachregel nötig.

## Eigenständige Office-Pflege (15.10.2026)

Implementiert ist der eigenständige Vorgang `workflow=office` ohne Organisation,
Thema, Teams oder Lieferdateien. `office_schema` liest nur Office-Regeln und
Bestand. `office` unterscheidet ausdrücklich `mode=create` und `mode=update`;
Korrekturen behalten den Identifier. `validate` erstellt den Office-Review,
`approve(gate=office)` bindet eine tatsächliche menschliche Antwort an diesen
Prüfstand. Nur `shared/data/offices.xtf` kann übernommen werden. Neue
Themen-/Organisationsdateien, Seeds und Lieferungen sind im Office-Vorgang gesperrt.

**Tatsächlich geprüft:** Java-21-Build und Unit-/Regressionstests sowie der
gezielte Integrationstest `OfficeMcpIntegrationTest` mit echtem stdio-MCP,
echtem INTERLIS-Validator und isoliertem quellfreiem Operator-Arbeitsbereich.
Abschliessend bestanden 265 Unit-/Regressionstests und die zwei ausgewählten
Integrationstests ohne Fehler oder Auslassungen; `jar` und `spotlessCheck`
waren ebenfalls erfolgreich. Die lokale Git-Prüfung enthält auch eine
Prozessunterbrechung während der Synchronisierung mit anschliessender Wiederaufnahme.
Der BFS-Fall aus der OpenCode-Session wird dabei ohne Organisation oder Thema
vorbereitet, geprüft und übernommen. `WorkspaceMcpIntegrationTest` prüft
zusätzlich die vorhandenen MCP-/Dateigrenzen. Integrationstests laufen mit
expliziter neuer Konfiguration:
`./bin/datenportal-agent gradle integrationTest -PintegrationConfig=build/office-integration.toml --tests '*OfficeMcpIntegrationTest' --tests '*WorkspaceMcpIntegrationTest'`.
Die lokale Testkonfiguration enthält den absoluten Entwicklungsroot,
`topics_repo="build/office-integration-repo"`,
`state_dir="build/office-integration-runs"`,
`model_dirs=["tests/fixtures/models"]` und
`[datasheet]` mit `transport="stdio"`. Der Office-Test erzeugt seine eigenen
temporären Fachdateien und verwendet standardmässig
`build/validator/ilivalidator.jar` (mit `gradle validatorJar` erstellbar).

**Isoliert simuliert:** Menschliche Freigaben und GitHub-Antworten sind explizit
synthetische Testdaten. `OfficeRepositoryTest` verwendet echte lokale Git-Repositories
und einen lokalen Bare-Remote für PR-Kandidat, wiederholte Vorbereitung,
simulierten menschlichen Merge und anschliessende Synchronisierung. Es erfolgt
kein Netzwerkzugriff auf GitHub und kein Jenkins-Aufruf. Unit-Tests simulieren
Validator-Erfolge/-Fehler und prüfen unter anderem veraltete Freigaben,
Kandidaten-/Modelländerungen, konkurrierende Katalogänderungen und unerlaubte
Dateien beziehungsweise Operationen.

**Offen / nicht durchgeführt:** Kein Release oder Update des realen Betriebspakets,
keine Änderung an der aktiven OpenCode-Session oder am realen Office-Katalog.
Keine echten PRs, menschlichen Fachfreigaben oder INT-/PROD-Publikationen.
Die übrigen externen Fachintegrationstests und die optionale Docker-Publikationsabnahme
wurden für diese Office-Erweiterung nicht erneut ausgeführt.

## Veröffentlichtes INTERLIS-MCP im lokalen Paket (15.10.2026)

Build-Defaults: INTERLIS-MCP `0.0.579-1`, Datenblatt-MCP `0.2.3`.
Beide ausführbaren JARs werden über Maven bezogen; das lokale Agentenpaket
`0.2.28-local-office-interlis579` hat `developmentArtifacts=false`.
Der veröffentlichte SHA-256 von INTERLIS-MCP stimmt mit dem Paket überein:
`c70705ee4c436a6b9c7ec4aeb97273d2783023c43f73ecbe8ae77c2732b4df3b`.

Tatsächlich geprüft: `test jar spotlessCheck javaDistribution` erfolgreich;
Paket-Smoke mit Installation, beiden MCPs, positiver/negativer Validierung und
Workspace-Update erfolgreich. INTERLIS-MCP meldet im echten stdio-Handshake
`0.0.579-1`. Alle Paket-Smoke-Fachdateien und Freigaben sind isolierte Testdaten.
Die vollständige Integrationstestsuite gegen das neue Paket bestand ebenfalls
(mit expliziter Konfiguration `build/proof-integration.toml` und
`DATENPORTAL_AGENT_HOME=build/java-distribution`). Der optionale Docker-/Jenkins-Test
bleibt ausdrücklich übersprungen; Modellnachweiserneuerung und Office-MCP wurden
mit den tatsächlich veröffentlichten Laufzeitkomponenten geprüft.

Lokale Installation abgeschlossen unter
`/Users/stefan/datenportal/pakete/datenportal-themenintegrator-agent-0.2.28-local-office-interlis579`.
Der Betriebsordner `/Users/stefan/datenportal/datenportal-betrieb` wurde mit
`workspace update` aktualisiert und mit `init` erneut geprüft. Seine lokale
Konfiguration blieb bytegleich; vor dem Update wurde eine private Sicherung
angelegt. `doctor` bestätigt beide MCPs und den aktuellen Initialisierungsnachweis.

OpenCode wurde über `reload` neu verbunden; `mcp list` meldet beide Server als
`connected`. Ein tatsächlicher OpenCode-Lesetest mit dem bereits verwendeten
DeepSeek-Modell rief erfolgreich die Schemas für `start`, `office` und
`derive_model.changes` ab. Letzteres liest den Vertrag des INTERLIS-MCP.
Der vorherige Versuch mit dem CLI-Standardmodell scheiterte am OpenRouter-
Guthabenlimit (HTTP 402); die MCP-Anbindung war nicht die Ursache. Keine Provider-
oder Guthabeneinstellungen geändert. Die beiden technischen Testchats verändern
keine Fachvorgänge; keine neue fachliche Freigabe oder Veröffentlichung ausgelöst.

Abschlusszahlen: 265 Unit-/Regressionstests erfolgreich; vollständige
Integrationstestsuite 22 Tests, davon 21 erfolgreich und eine optionale
Docker-/Jenkins-Abnahme übersprungen. Agentenänderungen auf `main` gepusht
(Implementierungscommit `d1d8a78`). Das Agentenpaket ist lokal gebaut und
installiert, nicht als neuer Maven-Agentenrelease veröffentlicht.

## Maven-Release 0.2.32 und Neuinstallation (15.10.2026)

Tatsächlich veröffentlicht: `ch.so.agi:datenportal-themenintegrator-agent:0.2.32:bin@tar.gz`
auf `https://jars.interlis.guru/snapshots/`, aus Commit `b18cd07`.
[Release-Workflow #32](https://github.com/sogis/datenportal-themenintegrator-agent/actions/runs/38734034790)
ist erfolgreich: Tests, JAR und Formatprüfung unter Linux und macOS, MCP-Transporttests,
Paketinstallation, Maven-Publikation, erneuter Download mit Bytevergleich und
nochmalige Paketinstallation. Enthalten sind INTERLIS-MCP `0.0.579-1` und
Datenblatt-MCP `0.2.3`; `developmentArtifacts=false`.

Zusätzlich lokal unter macOS/JDK 21 tatsächlich geprüft: Das veröffentlichte
Archiv wurde direkt von Maven nach `build/maven-release-032/agent.tar.gz`
heruntergeladen und mit `bin/package-smoke` frisch in einem isolierten Workspace
installiert. Beide MCP-Verbindungen, positive und negative Validierung, `init`,
`doctor` und Workspace-Update erfolgreich. `doctor` meldet `ready=true` und
`smoke_current=true`; der echte INTERLIS-MCP-Handshake meldet `0.0.579-1`.
Archiv-SHA-256: `c0eb9ca3890b9510c40c50477afd379912af7bcc2766b85c0c024dbac4d5e09c`.
Lokale technische Nachweise liegen unter `build/maven-release-032/`.

Die Installationsprüfung verwendet ausschliesslich synthetische Fachdateien.
Keine realen Office-Daten, Fachvorgänge, Freigaben oder Publikationen verändert.
Die bestehende Betriebsinstallation bleibt bei dem zuvor geprüften lokalen Paket;
dieser Nachweis betrifft ausdrücklich die Neuinstallation des Maven-Releases.

## Lokale Betriebsinstallation auf Maven 0.2.32 (15.10.2026)

Auf ausdrücklichen Auftrag wurde auch `/Users/stefan/datenportal/datenportal-betrieb`
mit `workspace update` auf das veröffentlichte Paket `0.2.32` aktualisiert.
Paketpfad: `/Users/stefan/datenportal/pakete/datenportal-themenintegrator-agent-0.2.32`.
Vorher vollständige private Workspace-Sicherung; `config/local.toml` blieb bytegleich.
`init` und `doctor` erfolgreich, Java 21 und Laufzeitpfade gültig,
Initialisierungsnachweis `ready=true`, `smoke_current=true`, beide MCPs gültig.
Das installierte Maven-Paket enthält INTERLIS-MCP `0.0.579-1`.

OpenCode-Konfiguration neu geladen. Tatsächlich geprüft: `mcp list` meldet
`datenportal_integrator` und `datenportal_workspace` als `connected`.
Ein separater technischer OpenCode-Lesetest mit `deepseek/deepseek-flash#high`
rief `operation_schema` für `start`, `office` und `derive_model.changes`
erfolgreich auf (jeweils `completed`). Der letzte Abruf lieferte den vollständigen
INTERLIS-Änderungsvertrag über den eingebundenen INTERLIS-MCP.
Keine fachlichen Schreiboperationen oder Freigaben im Test.
Nachweise: `build/maven-release-032/operator-{update,init,doctor}.json`
und `build/maven-release-032/opencode-read-test.jsonl`.

## Verbindlicher XTF-Vertrag und verwalteter Metadaten-Export (15.10.2026)

Der Export prüft Themen-Identifier und unterstützte Attributtypen und verwaltet
exakt einen Repository-Kandidaten. Zielordner stammen aus Konfiguration und
Vorgang; neue Dateien heissen standardmässig `meta-<identifier>.xtf`, ausgewählte
Bestandsdateien behalten ihren Pfad. `repository_filename` erlaubt einen expliziten
Dateinamen. Wiederholte Exporte und Umbenennungen ersetzen die Vormerkung.
Die tatsächliche Übernahme bleibt hinter den bestehenden fachlichen Freigaben
und prüft die Zielbytes nach dem Kopieren. Mehrdeutige Altvormerkungen blockieren
Export, Prüfung und Übernahme mit einer konkreten Diagnose.

Die aktuelle exportierte XTF ist die Modellquelle. XLSX liefert ausschliesslich
Ausgangsinformationen zur Datenblatterstellung; CSV-Beobachtungen und bestätigte
Fachregeln ergänzen den Vertrag. Identisches `attach(metadata)` erhält den
Entwurf, eine andere Quelle bei aktivem Entwurf wird zurückgewiesen. Status und
Exportantwort nennen Quelle, Prüfsumme, Aktualität, Repository-Pfad und nächsten
Schritt. Skills untersagen wiederholtes Pfadraten und Diagnose-Kopien.

**Synthetische Regressionen:** Exportwiederholung, Zielnamenwechsel,
Identifier-Konflikt und Korrektur, frühe Typdiagnose, idempotentes Anhängen,
Quellenkonflikt, mehrdeutige Altvormerkungen, Repository-Konflikte und
freigabegebundene Übernahme werden in isolierten temporären Repositories geprüft.
Ein widersprüchlicher XLSX-Quellenmarker beeinflusst den Modellauftrag nicht;
ein nicht exportierter geänderter Entwurf blockiert die erneute Modellableitung.
Alle dortigen Freigaben sind ausdrücklich synthetische Testantworten.

**Umgebungsdiagnose:** Der erste Integrationstestlauf mit der historischen
Office-Testkonfiguration scheiterte an fehlenden Paket-JARs und Testrepo.
Temporäre Runtime-Verknüpfungen im Entwicklungsroot führten ausserdem zu
doppelten Office-Modellquellen in den Unit-Fixtures. Diese Umgebungsfehler sind
kein erfolgreicher Fachnachweis; für die abschliessende Prüfung werden
Paketlaufzeit und isolierte Unit-Fixtures getrennt verwendet.

**Offen:** Reale fachliche Abnahme des Hotellerie-Themas sowie Lieferung,
Portal und Downloads. Die aktive OpenCode-Session und ihre Freigaben wurden
nicht verändert. Keine Installation oder Veröffentlichung des neuen Pakets
im Betriebsworkspace durch diesen Entwicklungsauftrag.

**Abschliessend tatsächlich ausgeführt:** `./bin/datenportal-agent gradle
spotlessApply test jar spotlessCheck javaDistribution` erfolgreich: 274 Unit-/
Regressionstests, keine Fehler oder Auslassungen. Das vollständige Entwicklungspaket
liegt unter `build/distributions/datenportal-themenintegrator-agent-0.1.0-dev-bin.tar.gz`.
Die Integration lief anschliessend erfolgreich mit
`DATENPORTAL_AGENT_HOME=$PWD/build/java-distribution ./bin/datenportal-agent gradle
integrationTest -PintegrationConfig=build/xtf-contract-integration/integration.toml`:
21 Tests bestanden, 1 optionaler Docker-/Jenkins-Abnahmetest übersprungen, keine Fehler.
Die temporären Runtime-Verknüpfungen im Entwicklungsroot wurden entfernt.
Die explizite Testkonfiguration nutzt ausschliesslich synthetisches Testrepo und
Testzustand unter `build/xtf-contract-integration`; Fach-MCPs und Validator stammen
aus dem neu gebauten Paket. Kein Betriebsworkspace wurde aktualisiert.

## Lokales Update auf XTF-Vertragspaket (15.10.2026)

Auf ausdrücklichen Auftrag wurde Implementierungscommit `3964e20` nach
`origin/main` gepusht und das Paket `0.2.33-local-xtf-contract` gebaut.
Der isolierte Paket-Smoke mit Neuinstallation, beiden MCPs, positiver/negativer
Validierung und Workspace-Update war erfolgreich.

Vor dem Betriebsupdate wurde der vollständige Workspace privat unter
`build/operator-backups/before-xtf-contract-3964e20/workspace` gesichert.
`workspace update` installiert das Paket aus
`/Users/stefan/datenportal/pakete/datenportal-themenintegrator-agent-0.2.33-local-xtf-contract`
in `/Users/stefan/datenportal/datenportal-betrieb`.
Alle 16 erfassten Konfigurations-/Fachzustandsdateien sind danach SHA-256-identisch.
Keine Fachfreigaben wurden übertragen: Legacy-Migration `already_current`,
CSV-Freigabemigration `csv_rules_changed_or_unknown`.

`init` meldet `ready=true`; `doctor` bestätigt `ready=true`, `smoke_current=true`
und beide Fach-MCPs als gültig. OpenCode wurde mit `reload` neu verbunden;
`mcp list` bestätigt Integrator und Workspace-MCP als `connected`.
Die beiden laufenden MCP-JVMs des OpenCode-Servers verwenden nachweislich
`0.2.33-local-xtf-contract`. Keine Fachsession fortgesetzt und keine Lieferung
oder Publikation ausgelöst. Technische Nachweise liegen unter
`build/xtf-contract-install/` und `build/xtf-contract-package-smoke.log`.

## Dezimalgenauigkeit bis zum kompilierten Modell (2026-10-15)

Implementiert: Dezimalstrings im MCP-Vertrag und unveränderte Übergabe durch den
Integrator; Rendering ohne Entfernen der Endnullen; frühe Prüfung gleicher
Genauigkeit; Soll/Ist-Vergleich numerischer Attribute anhand der kompilierten
Modellstruktur vor Kandidatenübernahme und bei erneuter Validierung.
Bestätigte Bereiche werden zusätzlich gegen CSV-Beobachtungen geprüft. Ein
unveränderter technischer Generatorfehler wird ohne erneuten MCP-Aufruf blockiert.

Tatsächlich lokal geprüft (synthetische, isolierte Daten):
- Integrator: 280 Java-Unit-Tests erfolgreich; JAR und `spotlessCheck` erfolgreich.
- INTERLIS-MCP: vollständige Java-Tests erfolgreich; anschließend erweiterter
  Tool-Vertragstest (26 Tests), `check` und 24 End-to-End-Tests erfolgreich.
- Tatsächlicher stdio-Rundlauf für ein/zwei Nachkommastellen, Nullgrenzen, negative
  Bereiche, Ganzzahlen und große Dezimalgrenzen; typisierte Änderung geprüft.
- CSV-Adapter akzeptiert `3.4`, `3.40` und `0.00` gegen das erzeugte Modell.
- Verfälschte Generatorantwort wird vor Übernahme abgelehnt; identische Wiederholung
  führt zu keinem weiteren Generatoraufruf. Abweichende Nullgrenze bleibt geschützt.

Die ersten Läufe enthielten zwei MCP-Testfehler mit inzwischen gelöschten
Temporärpfaden sowie Integrator-Fingerprintfehler bei gleichzeitigem Paketbau.
Isolierte und anschließend vollständige Wiederholungen bestanden. Die zusätzliche
XTF-Testdatei war als Datenfixture ungeeignet; die fachlich relevante Prüfung
verwendet den integrierten CSV-Adapter. Der bisherige synthetische CSV-Reparaturfall
prüft nun die bewusst frühere Abweisung des verfälschten Generatormodells.

Vollständiger Integrationstest mit expliziter Konfiguration
`build/xtf-contract-integration/integration.toml`: 23 Tests, 22 bestanden, ein
optionaler Docker-/Publikationsfall übersprungen. Die Prüfung verwendete das
isolierte Paket und keine Betriebs-Konfiguration.

Lokale Auslieferung auf ausdrücklichen Wunsch mit beiden lokalen Builds:
`0.2.34-local-decimal-contract`, INTERLIS-MCP aus Commit `fcf4e9f`
(`0.0.LOCALBUILD`, SHA-256
`bd050dadc0a77e09df498d69b1caf927313f383b24add500c70c9fc9104f6f80`).
Paket-Smoke, Installation, `init` und `doctor` erfolgreich; OpenCode neu geladen,
Integrator und Workspace-MCP verbunden. Die gestarteten Java-Prozesse verwenden
das neue Paket. Konfiguration plus 16 Vorgangsdateien: 17/17 SHA-256 unverändert.
Die automatische CSV-Freigabemigration des Updaters wurde anhand des gesicherten
Originalzustands vollständig zurückgenommen, damit keine bestehende Freigabe auf
den neuen Prüfstand übertragen wird. Die ursprünglichen Vorgangsbytes sind erhalten.

Die zusätzliche Feldzuordnung bei MCP-Präzisionsfehlern und das Zurücknehmen eines
nur vorgemerkten Aufrufs bei veralteter Komponente wurden gezielt nachgetestet.
Der öffentliche Build-Default verwendet die bereits veröffentlichte Version
`0.0.580-1` mit dem Dezimalstring-Vertrag; das lokale Paket enthält zusätzlich die
präzisere Attributdiagnose aus `fcf4e9f`. Die CI-Veröffentlichung `0.0.581-1` läuft
separat weiter und war keine Voraussetzung für diese lokale Installation. Keine reale Fachfreigabe oder Themenrepo-Übernahme durchgeführt.

## Serienvertrag und Wiederherstellung (2026-10-15)

Implementiert ausschließlich im Integrator: getrennte Themen-/Ausgabe-Identifier,
interne IDs und Bezeichnungen; Vorprüfung von Ausgabeoperationen; Schutz vor
erneuter Erstellung/Import; atomare Übernahme von Erstellungsbasis und Snapshot;
geprüfte Wiederherstellungspunkte; gezielte Rekonstruktion des belegbaren
Altfehlers mit ursprünglicher Basket-/Objektidentität. Abweichungen nennen
Identitäts- und Inhaltsfelder; unveränderte fehlgeschlagene Wiederherstellungen
führen vor weiteren MCP-Mutationen zu einem Blocker. Ein neuer Integrator- oder
Fach-MCP-Implementierungsstand erlaubt eine erneute Prüfung.

Tatsächlich durchgeführt: 287 Java-Tests erfolgreich, JAR-Bau und Formatprüfung
erfolgreich. Isolierte Tests unterscheiden eindeutige Ablehnung und unklaren
Transportabbruch; bestätigte Checkpoints bleiben erhalten. Manipulierte
Checkpoints und unzureichende Altfall-Evidenz werden abgewiesen. Absichtlich
veränderte Restore-Inhalte/Identitäten werden nicht übernommen; ein erneuter
identischer Versuch ruft den MCP nicht auf.

Der reale MCP-Regressionsfall erstellt eine Serie, legt eine Ausgabe ohne interne
ID an, aktualisiert sie über Identifier sowie interne ID und prüft genau eine
Ausgabe. Er reproduziert die überschriebenen Basisidentitäten und das Scheitern
des bisherigen Restore-Wegs isoliert. Der reparierte Restore erhält alle
fachlichen Inhalte und ursprünglichen Identitäten; Export und weiterer
MCP-Neustart sind Bestandteil der Prüfung. Keine Geschäftssession wird als
Testfixture verwendet.

Offene fachliche Abnahme: ausdrückliches `restore` des realen Vorgangs
`11ff1aa4f8b7463f9fbe06f503e72698` und dessen fachliche Fortsetzung.
Keine Freigabe, Repository-Übernahme oder Publikation wird hierfür simuliert.
Die optionale Docker-/Publikationsabnahme gehört nicht zu diesem lokalen Update.

Vollständige Integration mit expliziter Konfiguration
`build/xtf-contract-integration/integration.toml` und isoliertem Paket erfolgreich:
24 Tests, davon 23 erfolgreich und ein optionaler Docker-/Publikationsfall
übersprungen. Der Serienfall läuft gegen den realen Datenblatt-MCP, inklusive
Prozessneustarts. Paketbau mit den lokalen MCP-Artefakten; keine Änderung am
Datenblatt-MCP erforderlich.

Paket `0.2.35-local-series-recovery` gebaut und synthetischer Paket-Smoke
bestanden: Installation, beide MCPs, positive/negative Validierung, Update und
explizites `--preserve-run-state` mit byteidentischer Zustandsfixture.
Der öffentliche Updatepfad kann damit ohne Freigabemigration verwendet werden.
