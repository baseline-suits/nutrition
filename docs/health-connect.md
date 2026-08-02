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
- Rohwerte werden nur als verschlüsselter Pending-Batch gespeichert, bevor der
  Serveraufruf beginnt. Nach bestätigter vollständiger Verarbeitung wird dieser
  Batch entfernt; lokal bleiben nur Cursor und Berechtigungsmetadaten.
- Eine Abmeldung leert Aktivierungs-/Berechtigungsmetadaten zusammen mit der Sitzung.
  Ein noch unbestätigter Batch bleibt verschlüsselt und für andere Konten unzugänglich,
  damit dasselbe Konto ihn nach erneuter Anmeldung exakt wiederholen kann. „Verbindung
  trennen“ und Kontolöschung entfernen auch diesen Batch.

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
- Die lokale Leseschicht führt verschiedene Ursprungspakete nie still zusammen.

Die lokale Schicht liest ausschließlich Rohrecords und mischt sie nicht mit der
Health-Connect-Aggregat-API. So werden Roh- und Aggregatwerte nicht doppelt gezählt.

## Synchronisierung und Retry

- Ein Batch verwendet den Vertrag `health-sync/1.0`, enthält höchstens 500 Records
  und ist mit einer installationsstabilen ID sowie einer zufälligen Request-ID
  versehen.
- Der vollständige Request wird vor dem Netzaufruf verschlüsselt gespeichert. Ist
  das Ergebnis wegen eines Netzfehlers unbekannt, sendet die App exakt denselben
  Request mit derselben Request-ID erneut.
- Ein abgewiesenes Element verhindert Cursorfortschritt und Reconciliation für den
  Abschnitt. Gültige Elemente bleiben serverseitig verarbeitet; der vollständige
  Abschnitt erhält für einen erneuten Versuch eine neue Request-ID.
- Ein vollständiger leerer Abschnitt ist ein gültiger Abgleich und kann zuvor
  sichtbare, inzwischen entfernte Records reconciliieren.
- Serverseitig ist der fachliche Schlüssel Nutzer, Datentyp, Ursprungspaket und
  externe Record-ID. Eine Geräte- oder Installations-ID allein dedupliziert nicht.
- Jede Installation führt eine eigene Sichtung desselben Records. Fehlt ein Record
  in einem vollständig verarbeiteten Abschnitt, wird nur diese Sichtung deaktiviert.
  Der Record wird erst tombstoniert, wenn ihn keine Installation mehr aktiv sieht.
- Explizite Tombstones schützen anhand ihrer Health-Connect-Änderungszeit vor einer
  veralteten Wiederherstellung. Korrekturen mit neuerer Änderung ersetzen die vorige
  Version und berechnen betroffene Tage deterministisch neu.

## Serverseitige Quellenentscheidung

- Rohrecords bleiben von Tagesaggregaten getrennt und nennen Ursprung, Importzeit,
  letzte Änderung, Zeitzone und aktive Installationssichtungen.
- Überlappen mehrere Ursprungspakete am selben lokalen Tag, bleibt der kombinierte
  Wert im Status `conflict` leer. Nicht überlappende Quellen dürfen nach denselben
  Zeitregeln zusammengeführt werden.
- Eine bevorzugte Quelle pro Datentyp macht den gewählten Wert und die Auswahl im
  Aggregat sichtbar. Das Entfernen der Präferenz berechnet den ursprünglichen
  Konfliktstatus neu; keine Rohquelle wird dabei gelöscht.
- Gewicht bleibt auch serverseitig eine Messreihe. Schlaf wird dem Aufwachdatum
  zugeordnet; Schlaf- und Trainingsintervalle werden als Vereinigungsmenge berechnet.
