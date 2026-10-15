# Betrieb und Zugänge

Facharbeit über die öffentlichen Integrator-Werkzeuge und die drei Skills ausführen. Bei Einrichtungsfehlern die Betreiberhandlung aus der Diagnose nennen, keine Implementierungsquellen lesen oder Programme selbst bauen.

Das installierte Betriebspaket enthält Integrator, beide MCP-JARs, ilivalidator und Modelle. JDK 21 kommt vom Betreiber. `init` prüft die Programme und führt isolierte synthetische Prüfungen aus; `doctor` trennt technische Bereitschaft und Publikationszugänge. Keine Docker-Mounts, MCP-Ports oder GRETL-Einrichtung. Paketversionen ausschliesslich durch ein bewusstes `workspace update` wechseln.

GitHub verwendet REST und zuerst `GH_TOKEN`, dann `GITHUB_TOKEN`, andernfalls den Workspace-Credential-Store; Git bleibt für Repository-Operationen zuständig. `gh` wird nicht benötigt. GitHub-Zugänge durch den Betreiber mit `credentials github set --token-stdin` oder ausdrücklich mit `--from-env` hinterlegen lassen. `credentials github status` zeigt Quelle und gespeicherten Eintrag ohne Token; Rotation mit erneutem `set`, Entfernung mit `credentials github remove`. Jenkins-Zugänge über benannte Umgebungsvariablen oder `credentials set <profil> --username <user> --token-stdin` einrichten lassen. Niemals Tokens im Chat/MCP einsammeln oder aus Clientdateien kopieren. Fehlende Publikationszugänge verhindern keine Modellierung.

Die lokale CSV-Prüfung läuft direkt mit ilivalidator. Jenkins verwendet seinen GRETL-CsvValidator unabhängig davon. Ein lokaler Erfolg ersetzt keine Jenkins-Prüfung. XTF, Teams, Benutzer und Office-Zuordnung werden weiterhin geprüft. Organisationsprüfung startet kein Gradle.

Nur ausdrücklich gewünschte lokale Publikationsprüfungen benötigen Docker und den Dev-Stack. `acceptance delivery` ist synthetisch und autorisiert keine fachlichen Änderungen. Offene externe Laufkennungen zuerst aufklären; keine blinden Wiederholungen.

Alte Schema-2-Vorgänge mit der alten Version abschliessen. Neue Java-Arbeitsbereiche verwenden Schema 3; keine Übernahme alter Freigaben. Einrichtung und Updates stehen im [Anwenderhandbuch](../anwenderhandbuch.md).

## Prüfberichte und grosse Antworten

MCP-Antworten liefern kompakte Ergebnisse. `response_compacted: true` verweist mit
`full_result_path` auf das vollständige Ergebnis. Dieses mit
`datenportal_workspace.read_file(path=..., offset=0, limit=6000)` lesen und nur bei
Bedarf mit `next_offset` fortsetzen. `next_offset: null` bedeutet Dateiende.
Nicht dieselbe Prüfung wiederholen, um abgeschnittene Ausgaben zu lesen, und
keine OpenCode-internen `tool-output`-Dateien anfordern.

HTML-Prüfberichte mit `datenportal_workspace.open_report(path=...)` im lokalen
Standardbrowser anzeigen und den absoluten Berichtspfad als Markdown-Link im
Chat angeben. Das Öffnen ist keine fachliche Freigabe. Falls am MCP-Host kein
Browser verfügbar ist, den zurückgegebenen Pfad manuell öffnen. Ein gültiger
Modellbericht ersetzt keine separate Metadatenprüfung; deren Fehler (z.B.
fehlende Ausgabenbezeichnung) müssen vor einer Freigabe geklärt werden.

Eine nachträglich bestätigte Ausgabenbezeichnung wird mit
`set_issue(run_id, issue, human_statement)` am bestehenden Vorgang gesetzt.
Dies ändert keine Datenblattfelder und erteilt keine Freigabe. Bisherige
Prüfungen, Freigaben und Publikationspläne werden archiviert und entwertet;
Dateien und Entwürfe bleiben erhalten. Danach `analyze`, `validate` und bei
vorhandenem Modell `validate_model(scope=integration)` ausführen. Niemals einen
zweiten Vorgang anlegen, um eine fehlende Ausgabe nachzutragen.
