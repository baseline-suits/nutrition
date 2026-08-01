# Datenlöschung und Backups

Die produktive Datenbank und der private Objektspeicher werden höchstens 30 Tage
in verschlüsselten Backups aufbewahrt. Eine selektive Entfernung aus bereits
geschriebenen Backups wird nicht versprochen. Gelöschte Daten dürfen nur für
eine technische Wiederherstellung innerhalb dieses Zeitraums vorhanden sein
und nicht in den normalen Betrieb zurückkehren.

Kontolöschungen erzeugen deshalb einen inhaltsfreien Tombstone mit Nutzer-ID,
Anforderungszeitpunkt, Abschlusszeitpunkt und Ablaufdatum. Tombstones werden
ab dem vollständigen Abschluss 45 Tage aufbewahrt. Vor einer Wiederherstellung müssen die nach dem
Sicherungszeitpunkt entstandenen Tombstones aus dem aktuellen Löschledger in
die wiederhergestellte Datenbank übernommen werden. Beim API-Start sperrt
`apply_deletion_suppressions()` solche Konten, widerruft ihre Sitzungen und
übernimmt sie erneut in den Löschworkflow.

Dateilöschungen laufen über persistente `deletion_jobs`. Ein Fehler im
Objektspeicher macht Foto oder Konto sofort unzugänglich, lässt den Auftrag aber
als `failed_retryable` bestehen. `make retry-deletions` wiederholt fällige
Schritte idempotent. Erfolgreiche Aufträge und abgelaufene Tombstones werden
nach ihrer Aufbewahrungsfrist entfernt.

Globale Open-Food-Facts-Caches enthalten keine privaten Kontoverknüpfungen und
werden durch Konto- oder Mahlzeitenlöschungen nicht entfernt.
