# Lieferung und Wiederaufnahme

Vor einer beauftragten Lieferung, einem Zielplan/PR oder bei Wiederaufnahme lesen. Eigenständige Modell- und Organisationsaufträge enden nach der beauftragten Übernahme beziehungsweise dem fachlichen PR; keine Datenlieferung hinzufügen.

**Ziel-Stopp:** lokale Beendigung, INT oder PROD anbieten. `publication_plan` zeigt den konkreten Stand. Nur nach ausdrücklichem OK `approve(gate=publish:<profil>)`. `prepare_pr` bei Repository-Änderungen; URL zeigen und in Codex, falls verfügbar, als Artefakt anhängen. Ein Mensch mergt. Nach Fortsetzung Zielbranch/Head/Bytes durch den Kern prüfen und erst dann Seed/Lieferung ausführen. Kein PR für reine Datenlieferung ohne Git-Änderung.

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
