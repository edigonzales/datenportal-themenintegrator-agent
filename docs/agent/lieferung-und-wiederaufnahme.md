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
