# Health-Connect-Leseregeln

Baseline Nutrition fordert ausschließlich die fünf einzelnen Leserechte für Schritte,
Schlaf, aktive Kalorien, Training und Gewicht an. Eine Freigabe aktiviert nur den
betroffenen Datentyp. Ein lokal deaktivierter Typ wird nicht gelesen; „Verbindung
trennen“ widerruft alle Health-Connect-Rechte. Die App bleibt ohne Health Connect ein
vollständiger Kalorientracker.

## Abfrage und Aufbewahrung

- Ein sichtbarer Nutzerimpuls liest höchstens 30 Tage; die Einstellungsprüfung nutzt
  sieben Tage.
- Seiten enthalten höchstens 200 Records. Wiederholte Seitentoken und mehr als 100
  Seiten brechen als unvollständiger Lauf ab; daraus wird kein Teilwert gebildet.
- Records werden während einer Abfrage anhand Ursprungspaket und externer Record-ID
  dedupliziert. Bei einer Korrektur gilt nur die jüngste `lastModifiedTime`.
- Rohwerte werden für ID-291 nicht persistent gespeichert. Die UI hält lediglich
  Anzahl, Quellenanzahl und Leer-/Fehlerstatus, solange die Ansicht aktiv ist.
- Abmeldung und Kontolöschung leeren die verschlüsselt und nutzergebunden gespeicherten
  Aktivierungs-/Berechtigungsmetadaten zusammen mit der Sitzung.

## Zeit- und Aggregationsregeln

- UTC-Start/-Ende, Record-Zonenoffsets, Abfrage-Zone, externe ID, Ursprungspaket,
  App-Name und letzte Änderung bleiben im internen Modell erhalten.
- Schlaf wird dem lokalen Tag des Sitzungsendes (Aufwachdatum) zugeordnet. Sitzungen
  und ihre Segmente bleiben einzeln erhalten. Überlappende Intervalle werden bei der
  Dauer nur einmal gezählt.
- Trainingsdauer wird je Ursprung und Tag als Vereinigungsmenge der Intervalle
  berechnet. Trainingseinträge und Trainingsart bleiben separat erhalten.
- Schritte und aktive Kalorien werden nur je Ursprung und Tag summiert, wenn sich die
  gelieferten Intervalle nicht überlappen. Bei Überlappung bleiben die Rohrecords
  unterscheidbar und der Summenwert bleibt absichtlich leer.
- Gewicht bleibt eine Folge einzelner Messungen und wird in dieser Schicht nicht
  gemittelt.
- Verschiedene Ursprungspakete werden nie still zusammengeführt oder priorisiert.

Die lokale Schicht liest ausschließlich Rohrecords und mischt sie nicht mit der
Health-Connect-Aggregat-API. So werden Roh- und Aggregatwerte nicht doppelt gezählt.
Backend-Synchronisierung und eine explizite Quellenentscheidung gehören zu ID-295.
