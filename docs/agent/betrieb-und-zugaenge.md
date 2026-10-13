# Betrieb und Zugänge

Facharbeit über die öffentlichen Integrator-Werkzeuge und die drei Skills ausführen. Bei Einrichtungsfehlern die Betreiberhandlung aus der Diagnose nennen, keine Implementierungsquellen lesen oder Programme selbst bauen.

Das installierte Betriebspaket enthält Integrator, beide MCP-JARs, ilivalidator und Modelle. JDK 21 kommt vom Betreiber. `init` prüft die Programme und führt isolierte synthetische Prüfungen aus; `doctor` trennt technische Bereitschaft und Publikationszugänge. Keine Docker-Mounts, MCP-Ports oder GRETL-Einrichtung. Paketversionen ausschliesslich durch ein bewusstes `workspace update` wechseln.

GitHub verwendet REST und `GH_TOKEN` beziehungsweise `GITHUB_TOKEN`; Git bleibt für Repository-Operationen zuständig. `gh` wird nicht benötigt. Jenkins-Zugänge über benannte Umgebungsvariablen oder `credentials set <profil> --username <user> --token-stdin` einrichten lassen. Niemals Tokens im Chat/MCP einsammeln oder aus Clientdateien kopieren. Fehlende Publikationszugänge verhindern keine Modellierung.

Die lokale CSV-Prüfung läuft direkt mit ilivalidator. Jenkins verwendet seinen GRETL-CsvValidator unabhängig davon. Ein lokaler Erfolg ersetzt keine Jenkins-Prüfung. XTF, Teams, Benutzer und Office-Zuordnung werden weiterhin geprüft. Organisationsprüfung startet kein Gradle.

Nur ausdrücklich gewünschte lokale Publikationsprüfungen benötigen Docker und den Dev-Stack. `acceptance delivery` ist synthetisch und autorisiert keine fachlichen Änderungen. Offene externe Laufkennungen zuerst aufklären; keine blinden Wiederholungen.

Alte Schema-2-Vorgänge mit der alten Version abschliessen. Neue Java-Arbeitsbereiche verwenden Schema 3; keine Übernahme alter Freigaben. Einrichtung und Updates stehen im [Anwenderhandbuch](../anwenderhandbuch.md).
