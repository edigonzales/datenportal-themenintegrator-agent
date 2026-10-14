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

GitHub-PRs werden direkt über die REST-API erstellt und geprüft. `GH_TOKEN` hat Vorrang vor `GITHUB_TOKEN`. Für das gewählte Repository werden Pull-Request-Schreibrechte und bei HTTPS-Push zusätzlich Contents-Schreibrechte benötigt. Git nutzt den vom Integrator gestarteten Credential-Helper; `gh auth login` ist nicht erforderlich. Tokens nicht in Konfigurationsdateien oder Chatnachrichten eintragen. Git-Autorenidentität wie üblich konfigurieren. Der Client muss die benötigten Umgebungsvariablen an den Integrator weitergeben.

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
