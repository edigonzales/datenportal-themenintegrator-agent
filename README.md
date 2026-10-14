# Datenportal-Themenintegrator

Java-21-Integrator für geprüfte Datenportal-Themen, CSV/XTF/XLSX-Lieferungen, Validierungsmodelle und Organisationen. Facharbeit erfolgt im **separaten quellcodefreien Betriebspaket**, nicht in diesem Entwicklungsrepository.

## Betriebspaket

Das versionierte Maven-Artefakt `ch.so.agi:datenportal-themenintegrator-agent:<version>:bin@tar.gz` enthält Integrator, zwei MCP-Fat-JARs, ilivalidator, Modelle, Skills und Betriebsressourcen. Benötigt werden JDK 21 und für Repository-Änderungen Git. Docker, Gradle und `gh` sind keine Voraussetzungen für normale Facharbeit.

Nach Download und Entpacken:

```bash
/pfad/zum/paket/bin/datenportal-agent workspace install /absoluter/pfad/betrieb
cd /absoluter/pfad/betrieb
cp config/local.example.toml config/local.toml
# topics_repo und benötigte Publikationsprofile einrichten
bin/datenportal-agent init
bin/datenportal-agent doctor
```

Details: [Anwenderhandbuch](docs/anwenderhandbuch.md). Alte Docker-Arbeitsbereiche bleiben auf ihrer bisherigen Version; neue Vorgänge starten in neuen Java-Arbeitsbereichen.

Lokale CSV-Prüfung verwendet einen kleinen INTERLIS-Adapter. Jenkins prüft unabhängig mit GRETL. Pull Requests werden über GitHub REST erstellt und geprüft. Die beiden Fach-MCPs werden weiterhin auch als Dockerimages veröffentlicht, hier aber als Java-Prozesse verwendet. Das eigene Integrator-Image entfällt.

## Entwicklung

```bash
export JAVA_HOME=/pfad/zum/jdk-21
./gradlew test jar spotlessCheck
./gradlew javaDistribution -PdatasheetVersion=<publizierte-version> -PinterlisVersion=<publizierte-version>
bash bin/package-smoke build/java-distribution
```

[Entwicklerhandbuch](docs/entwicklerhandbuch.md), [Abnahmenachweise](docs/abnahme.md).

### Review compatibility

`validation/review-policy.json` versions the integrator's validation semantics.
Increment its revision when a code change alters CSV/model/metadata validation,
contract interpretation or approval safety rules. Do not increment it for UI,
packaging, logging or installation-path changes. Review fingerprints include
this policy, validation resources, models and executable validator contents;
they deliberately exclude the integrator JAR and ZIP packaging metadata.
Changes to external validator code are detected through their content hashes.
