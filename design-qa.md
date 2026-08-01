# ID-308 Design-QA

## Quellen und Zustand

- Referenzen: die zehn verbindlichen Screens aus `docs/assets/`, jeweils einzeln geöffnet.
- Implementierung: native Compose-App im Dark Mode auf dem Galaxy A52 4G (SM-A525F, Android 16 / LineageOS 23.2).
- Verglichen wurden jeweils derselbe Bildschirm und derselbe Interaktionszustand; keine Referenz wurde gesammelt als Kontaktbogen betrachtet.

## Ergebnis

| Bereich | Sichtprüfung | Verhalten |
| --- | --- | --- |
| Übersicht/Dashboard | bestanden | Tagesbudget, Makrofortschritt, leerer Zustand und primäre Aktion sind klar lesbar |
| Tagebuch | bestanden | Datumswechsel, Mahlzeitengruppen, Summen und leerer Zustand funktionieren |
| Mahlzeit hinzufügen | bestanden | Beschreibung, Foto, Import, Barcode, manuell und Wiederverwendung führen in echte Flows |
| Mahlzeit analysieren | bestanden | Lade-, Fehler- und Review-Zustände bleiben nachvollziehbar |
| Barcode | bestanden | Scanner-, Suche-, unbekannt- und Fehlerzustände sind erreichbar |
| Mahlzeit prüfen | bestanden | Zutaten, Mengen und Nährwerte bleiben korrigierbar vor dem Speichern |
| Verlauf/Statistiken | bestanden | Kalender, historische Tage und Wochenübersichten sind navigierbar |
| Favoriten/zuletzt gegessen | bestanden | Wiederverwendung erzeugt einen neuen Entwurf ohne das Original zu ändern |
| Kalorienbudget | bestanden | Fest/dynamisch, echte Aktivitätsdaten und fehlende Daten werden transparent dargestellt |
| Einstellungen | bestanden | Sprache, Ziele, Health Connect, Sync, Datenschutz und Konto sind gebündelt |

## Plattform-QA

- Zurück-Navigation, Prozesswiederherstellung, Rotation, Querformat und kleine Breite geprüft.
- Tastatur und Systemleisten geprüft.
- Deutsch und Russisch geprüft; die App wurde anschließend wieder auf Deutsch gesetzt.
- Instrumentierte Android-Tests: 51 Tests, 0 Fehler, 3 bewusst übersprungene opt-in-Backend-Integrationstests im Standardlauf.
- Opt-in-Backend-Integrationstests: 3/3 auf dem Galaxy bestanden.
- Android-Lint und JVM-Tests bestanden.

Temporäre Vergleichsscreenshots werden nach der Abnahme entfernt; sie sind kein
Produktartefakt und enthalten keine Demo-Daten.

final result: passed
