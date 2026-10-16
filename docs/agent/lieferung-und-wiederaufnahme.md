# Lieferung und Wiederaufnahme

Vor einer beauftragten Lieferung, einem Zielplan/PR oder bei Wiederaufnahme lesen. Eigenständige Modell- und Organisationsaufträge enden nach der beauftragten Übernahme beziehungsweise dem fachlichen PR; keine Datenlieferung hinzufügen.

**Lokaler Ablauf:** Nach den fachlichen Freigaben vorhandene Änderungen mit `apply_local_changes` übernehmen und die beauftragte lokale Lieferung mit `deliver(environment="local")` phasenweise fortsetzen. Eine fachliche Freigabe erweitert den Auftrag nicht automatisch um eine lokale Publikation. Für Profile mit `kind=local` ist keine zusätzliche Publikationsfreigabe vorgesehen; `approve(gate=publish:local)` wird gezielt abgelehnt. Der lokale Liefer- und Publikationstest umfasst die Lieferung über Jenkins und die Prüfung von Publikationsbericht, Portal-Reload, Metadaten und Downloads. Die Dateiübernahme allein ist kein erfolgreicher Liefernachweis.

**Repository-Abschluss:** Lokal abschliessen oder nach erfolgreichem lokalem Liefer- und Publikationstest einen beauftragten fachlichen PR erstellen. Zuerst `repository_plan(run_id, environment)` zeigen: Git-Repository, Remote, Zielbranch, Dateien und offene Voraussetzungen. Danach `prepare_pr` ohne `publish:<Profil>`; aktuelle fachliche Freigaben und bei Themen der aktuelle lokale Liefernachweis werden erneut geprüft. Das Profil wählt hierfür nur die konfigurierte Git-Zuordnung. Bereits erteilte PR-Aufträge nicht erneut bestätigen lassen. URL zeigen und in Codex als Artefakt anhängen. Ein Mensch mergt; anschliessend `sync_repository` ausführen, auch ohne weitere Lieferung. Eigenständige Modell-/Organisations-PRs benötigen keinen Themen-Liefernachweis. Kein PR für reine Datenlieferung ohne Git-Änderung.

**INT/PROD:** Nur bei beauftragter Anlieferung den konkreten `publication_plan` zeigen und nach ausdrücklichem OK `approve(gate=publish:<profil>)`. Beim Thema muss der aktuelle lokale Liefer- und Publikationstest erfolgreich sein. Ein erstellter oder gemergter PR ersetzt diese Freigabe nicht. Vor Seed/Lieferung Zielbranch/Head/Bytes prüfen. Der Abschluss mit PR und Synchronisierung löst keine INT-/PROD-Lieferung aus; diese kann weiterhin manuell erfolgen.

`status` statt zweitem Vorgang. Nur aktuelle Schema-2-Vorgänge verwenden. Bei `unsupported_state_version` den Altstand erhalten und vorhandene externe Läufe klären; keine automatische Migration oder neue Lieferung als Umgehung starten. Neue stdio-Verbindung oder verlorener Entwurf: bestätigten Snapshot automatisch rekonstruieren und frühere IDs eindeutig zuordnen lassen. Unbestätigte Operationen blockieren; `metadata(operation=restore)` stellt ausdrücklich den bestätigten Stand her. Den gezeigten unbestätigten Auftrag separat prüfen und bewusst neu beauftragen. Unveränderte Wiederaufnahme ersetzt keine Freigabe und entwertet vorhandene Exportbytes nicht.

Bei `submission_unknown` zuerst den vorhandenen Lauf anhand Vorgangskennung und Parametern aufklären; `reconcile`/`reconcile_seed` ordnen zu. Bis zur eindeutigen Zuordnung keine weiteren `deliver`-/`seed`-Aufrufe und kein `retry_delivery`. Fehlen Job-/Queue-Kennung oder andere Zuordnungsbelege, gezielt danach fragen und stoppen. Niemals blind erneut hochladen. `publication=accepted` mit Reload-/RDF-/Downloadfehler ist eine erfolgte Publikation: getrennt reparieren und mit `verify_delivery` erneut prüfen. Ohne Bericht kein sicherer Retry. `retry_delivery` nur bei eindeutig fehlgeschlagener, nicht publizierter Lieferung.

Bei Schnittstellen- oder Runtime-Grenzen konkrete Betreiberhandlung erklären; keine Komponentenänderung als Umgehung vornehmen. Tests und Abnahme immer als tatsächlich, simuliert oder offen kennzeichnen; Testfreigaben dürfen keine reale Lieferung autorisieren.

## Lokales Repository nach Merge synchronisieren

Nach menschlichem Merge `sync_repository(run_id, environment)` aufrufen, auch wenn
keine weitere Veröffentlichung vorgesehen ist. Der Aufruf prüft den Merge und die
freigegebenen Zielbytes und aktualisiert ausschliesslich das konfigurierte
`topics_repo` per Fast-forward. Er benötigt keine Jenkins-Verbindung und startet
keine Lieferung. `status` zeigt `repository_sync`, löst selbst aber kein Update aus.
Vor neuen Seed-/Lieferungsstarts mit vorhandenem PR erfolgt dieselbe Prüfung und
Synchronisierung automatisch. Bereits gestartete externe Läufe bleiben abfragbar.

Nachweislich mit `apply_local_changes` übernommene Vorgangsdateien werden gezielt
bereinigt. Andere lokale Änderungen und deren Staging-Zustand bleiben erhalten,
sofern sie das Update nicht überschneiden. Fehlende Übernahmenachweise, eigene
Staging-Änderungen, abweichende Dateien, falscher Branch oder divergierende lokale
Commits blockieren die Fortsetzung. Nicht mit Reset, Clean, Stash oder erneuter
Lieferung umgehen: betroffene Pfade und Git-Zustand durch den Betreiber klären.

Eine unterbrochene Synchronisierung mit demselben Vorgang und Profil erneut
aufrufen. Das Repository-Journal und seine Sicherungen erhalten; bei unbekannten
zwischenzeitlichen Änderungen erfolgt keine automatische Überschreibung. Ein
anderer Vorgang kann während einer offenen Synchronisierung keine lokalen
Änderungen übernehmen. Es werden weder alte Checkouts noch PR-Arbeitskopien gelöscht.

## Aktuelle Dateien und historische Prüfungen

Prüfungen sind an Inhaltsprüfsummen und Prüfkomponenten gebunden. `status` zeigt
unter `check_states` den aktuellen beziehungsweise veralteten Status sowie das
Manifest des letzten erfolgreichen Prüfstands. Alte Ergebnisse bleiben in
`check_history` und HTML-Snapshots unter `report-history` erhalten. Sie dürfen
nicht als aktuelle Freigaben verwendet werden. Pfadwechsel ohne Inhaltsänderung
ändern die Dateiversion nicht; Änderungen während einer Prüfung machen deren
Ergebnis unbrauchbar für die Freigabe.

Ein unvollständiger Modellkandidat ist vom vorbereiteten Übernahmepaket getrennt.
Das Fehlen eines neuen Pakets ist keine nachträgliche Taskänderung. Nach einem
Proof-Engine-Update vorhandene Modellbytes mit
`validate_model(scope="model", refresh_evidence=true)` erneut prüfen. Keine
zusätzlichen Constraints einfügen und keine vorgemerkten Dateien manuell austauschen.
Erst anschliessend Modellreferenz und Integrationsreview vorbereiten. Externe
Repository-Änderungen müssen geklärt werden; sie werden nicht überschrieben.

## Eigenständige Office-Pflege

Office-Vorgänge übernehmen ausschliesslich `shared/data/offices.xtf`. Nach
`validate`, gezeigtem HTML-Bericht und tatsächlicher Freigabe `gate=office`
ist die beauftragte lokale Übernahme oder PR-Erstellung möglich. Ein lokaler
Datenliefernachweis und Publikationsfreigaben sind hierfür nicht erforderlich.
`repository_plan`, `prepare_pr` und nach menschlichem Merge `sync_repository`
verwenden; keinen Seed und keine Lieferung starten. Ein bereits vorbereiteter
PR wird fortgesetzt; weitere Office-Änderungen benötigen einen neuen Vorgang.

## Bestätigter Wiederherstellungspunkt

Erstellung und Import werden separat vorbereitet. Erst nach vollständigem Erfolg
speichert der Integrator Basis, Snapshot, Identitäten und Prüfsumme gemeinsam.
Eindeutige fachliche Ablehnung hinterlässt keinen `pending_metadata`-Auftrag;
ein Transportabbruch mit unbekanntem Ausgang blockiert weitere Bearbeitung.
`restore` verwendet ausschließlich den letzten bestätigten Stand und zeigt den
unbestätigten Auftrag separat, ohne ihn auszuführen.

Der Vergleich unterscheidet `identity_fields` (Basket-/Objektidentität) und
`content_fields` (fachliche Feldpfade). Neue interne Entwurfs-/Kind-IDs werden
nach erfolgreichem Vergleich zugeordnet. `draft_restore_blocked` verhindert
weitere MCP-Mutationen bei identischem fehlgeschlagenem Wiederherstellungsversuch.
Ursache beziehungsweise Komponente korrigieren; keine alternativen Pfade oder
Diagnosedatenblätter erzeugen und keine Zustandsdateien manuell bearbeiten.

Für den Altfehler einer überschriebenen Erstellungsbasis kann `restore` eine
leere Basis mit den ursprünglichen Identitäten aus dem bestätigten Snapshot
rekonstruieren. Das ist nur bei nachgewiesener Integrator-Erstellungsbasis,
passendem unbestätigtem Erstellungsauftrag und ohne importierte/exportierte
Quelle zulässig. Anschließend gelten dieselben vollständigen Inhalts- und
Identitätsprüfungen. Fehlende Evidenz führt zu
`draft_recovery_evidence_missing`, nicht zu einer gelockerten Prüfung.
Freigaben und Repository-Dateien werden durch Wiederherstellung nicht verändert.

## Manuell repariert und geseedet

Ein manueller Seed ist ein gültiger Wiederaufnahmefall im bestehenden Vorgang.
Mit belegter Build-URL `adopt_seed(run_id, environment, build_url)` verwenden.
Die Operation prüft Jenkins-Basis, konfigurierten Seed-Job und Ergebnis; sie
startet weder Seed noch Upload. Laufende Seeds werden durch `seed` oder die
anschliessenden `deliver`-Aufrufe weiter abgefragt. Nur `SUCCESS` erlaubt die
Lieferung. Frühere Versuche bleiben in der Historie. Fachliche Freigaben und
INT-/PROD-Publikationsfreigaben gelten weiterhin unverändert.

Ohne belegte manuelle Build-URL darf innerhalb des bestehenden Lieferauftrags
nach einem eindeutig fehlgeschlagenen Seed `retry_delivery` und danach `deliver`
einen eigenen Seed starten. Bei unbestätigtem Start zuerst aufklären. Kein
zweiter Vorgang und keine erfundene Queue-ID. Buildnummer und Queue-ID sind
verschiedene Kennungen; eine Build-URL wird niemals in eine Queue-ID umgedeutet.

`status.external_runs`, Aufrufergebnisse (`action`, `reference`, `next_operation`)
und Fehler (`external_state`) trennen Seed und Datenlieferung:

- `seed_submitting`: eigener Seed-Start unbestätigt; `reconcile_seed` verwenden.
- `seeding`: den gespeicherten Seed weiter abfragen.
- `delivery_submitting`: Datenupload unbestätigt; nur den Datenlieferlauf mit
  `reconcile` zuordnen. Auch ein 404 in dieser Phase ist kein Seed-Fehler.
  Seed-Kennungen helfen hier nicht; `adopt_seed` und blinder Retry sind gesperrt.

Bei `local_changes_missing` fehlt eine geprüfte Datei, bei
`local_changes_modified` weicht ihr Inhalt ab. Nach manueller Reparatur niemals
automatisch `apply_local_changes` zum Wiederherstellen alter Bytes aufrufen.
Korrekturen zunächst als neue Kandidaten im vorhandenen Fachablauf vorbereiten:
Metadaten über den Datenblatt-MCP, verwaltetes Modell und Task gemeinsam über
`derive_model`, sonst erlaubte Fachdateien über `stage_change`. Die Vorbereitung
kann die aktuellen Repo-Bytes als neuen Kandidaten übernehmen, wenn sie exakt
dem Kandidaten entsprechen. Danach die betroffenen Prüfungen und menschlichen
Freigaben erneuern. Veränderte oder entfernte bereits übernommene Dateien werden
nicht ungeprüft überschrieben. Nach geändertem Prüfstand den passenden manuellen
Seed erneut zuordnen; die frühere Zuordnung ist kein Nachweis für neue Bytes.

## Angenommene Lieferung korrigieren

`publication=accepted` bestätigt die Annahme, nicht die öffentliche Sichtbarkeit.
`verified`, `visibility_check` und `publication_status` getrennt lesen. Ein
Draft kann im Katalog stehen und dennoch öffentlich nicht erreichbar sein.
Bei offener Nachprüfung den bestehenden Lauf mit `verify_delivery` prüfen;
kein erneuter Upload zur Behebung eines Reload-/Sichtbarkeitsfehlers.

Sind tatsächlich geänderte Daten oder Metadaten neu geprüft und freigegeben,
`prepare_followup_delivery(run_id, environment)` im bestehenden Vorgang aufrufen.
Die Operation archiviert die eindeutig angenommene abgeschlossene Lieferung
und bereitet eine neue Generation vor, ohne Seed/Upload. Danach für INT/PROD
einen neuen `publication_plan` zeigen und ausdrücklich freigeben lassen;
lokal genügt weiterhin die aktuelle fachliche Freigabe. Mit `deliver` fortsetzen.
Ein passender zugeordneter Seed wird verwendet, sonst ein eigener gestartet.

Identische Vorbereitungen sind wirkungslos. Unveränderte Lieferinhalte, laufende
oder unbestätigte Starts erlauben keine Folgelieferung. `retry_delivery` bleibt
für eindeutig fehlgeschlagene, nicht angenommene Einreichungen reserviert.
`current_candidate_delivered=false` zeigt noch nicht gelieferte Korrekturen;
die alte Nachprüfung bleibt an ihren eingefrorenen Stand gebunden. Bei grossen
Antworten enthält `diagnostics` die wesentlichen Zustände und nächsten Schritte;
nicht wiederholt Megabyte-Ausgaben lesen oder terminale Lieferungen starten.

## Neue Repository-Dateien und unterbrochene PR-Vorbereitung

Vor `prepare_pr` mit demselben Vorgang `repository_plan` lesen. Der Plan prüft
nun den aktuellen Remote-Zielcommit in einer temporären Arbeitskopie und nennt
`target_commit`, `file_comparisons`, `blockers` und `next_operation`. Er verändert
weder den Vorgang noch das lokale Themenrepository. Bei nicht erreichbarem
Remote ist die Bereitschaft nicht bestätigt.

`addition` bedeutet: Die geprüfte Datei fehlt in der belegten Git-Basis und im
aktuellen Zielbranch. Auch eine lokal bereits vorhandene, unveränderte Datei
kann eine solche Ergänzung sein. Das ist kein zu bereinigender Branchkonflikt.
`modification` bezeichnet eine Änderung gegenüber der belegten Git-Basis;
`already_on_target` bereits übernommene Kandidatenbytes. Der lokale Ausgangsstand
und die Git-Basis sind unterschiedliche Nachweise.

Bei `pr_base_conflict` die ausgegebenen Basis-, Ziel- und Kandidatenprüfsummen
sowie `required_action` beachten. Tatsächliche Zielbranchänderungen fachlich
abgleichen, betroffene Kandidaten erneut prüfen und gegebenenfalls freigeben.
Bei `pr_basis_unknown` fehlt eine belegbare Git-Basis; nicht durch wiederholtes
Staging oder eine neue Lieferung umgehen. `local_changes_missing` und
`local_changes_modified` unterscheiden entfernte und manuell geänderte Dateien.
Keine automatische Wiederherstellung durch Kopieren, Reset, Clean oder Stash.

Nach einem Abbruch `repository_plan` und bei Bereitschaft `prepare_pr` im selben
Vorgang verwenden. Eigene bekannte Vorbereitungsschritte werden wiederaufgenommen;
eindeutig zuordenbare alte Teil-Checkouts bleiben als Archiv erhalten. Bei
`pr_checkout_conflict` fremde Dateien oder Commits erhalten und die konkrete
Diagnose klären. Bei `github_pr_ambiguous` wird ausschliesslich der bereits
angestossene PR gesucht; kein weiterer Erstellungsversuch ohne Klärung.

Ein PR-Fehler ändert weder die Annahme der lokalen Lieferung noch deren Seed.
Keine neue Datenlieferung als Reparatur der PR-Vorbereitung starten.
