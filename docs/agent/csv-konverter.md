# CSV-Konverter

Bei vorhandenen Konverterrezepten oder einer beauftragten Strukturänderung vor `transform` lesen. Eingangsdateien bleiben unverändert; eine Konvertierung ist keine CSV-Freigabe.

Vorhandene wiederkehrende Java-Konverterrezepte mit `transform` und ihren JUnit-Tests ausführen. Nur JSON-Rezepte mit Java-Quellen verwenden; andere Konverterformate werden zurückgewiesen und nicht ausgeführt. Keine neuen Python-Konverter, Launcher oder Tests hinzufügen.

Bei Strukturänderung zuerst den Zielvertrag klären. Java-Konverter unter `topics/<org>/<id>/` mit aussagekräftigen JUnit-Tests erstellen. Vertrag: `CsvConverter.convert(Path input, Path output)`; Eingabe unverändert, Ausgabe UTF-8 mit Semikolon. Tests sollen die fachliche Umformung und relevante Fälle wie leere Werte oder führende Nullen prüfen.

Lieferantenumstellung oder dauerhaften Konverter vereinbaren. Vorher/Nachher und Lieferantenvorgabe zeigen; Lieferantenhinweise nur lokal vorbereiten und nicht ungefragt versenden. Danach Ziel-CSV vollständig analysieren, HTML zeigen und die tatsächliche menschliche CSV-Freigabe abwarten.
