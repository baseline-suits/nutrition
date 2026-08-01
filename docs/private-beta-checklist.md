# Private-Beta-Abnahme

Stand: 2026-08-01  
Version: `0.1.0` / Debug- und interne-Beta-Build  
Repository: `Baseline Nutrition`

Dieses Protokoll ist das Release-Gate für ID-296. Es enthält nur echte Prüfergebnisse;
Demo- oder Seed-Daten werden nicht als Abnahmebeleg verwendet.

## Build und Konfiguration

| Prüfung | Ergebnis | Nachweis |
| --- | --- | --- |
| Frische Datenbankmigration | bestanden | `backend/scripts/check_migrations.py`, `backend/tests/test_foundation.py` |
| Backend- und Android-Quality-Gates | bestanden | `make quality` am 2026-08-01; Backend 48 API-/Security-Tests, Android-Lint und JVM-Tests grün |
| Interne Beta-APK reproduzierbar | bestanden | `app/build/outputs/apk/internalBeta/app-internalBeta.apk` |
| APK ohne lokale URL oder eingebettete Tokens | bestanden | `scripts/check-beta-apk.sh` |
| APK-Hash und Commit | bestanden | SHA-256 `d1453e6402a02525bb2721f1da76a471fefe338a57b2abec9f7cd4d6ffb6e07a`; Commit `cec4c4bb800ba0b388db02565d98ef160315d4d1` |
| Installations-/Updateanleitung | bestanden | Abschnitt „Interne Beta installieren oder aktualisieren“ in `README.md` |
| Rollback von Backend und Migrationen | bestanden | Migrationen sind vorwärtskompatibel; Rollback erfolgt über versioniertes Datenbank-Backup |

## Fachliche Kernstrecke

| Bereich | Ergebnis | Nachweis |
| --- | --- | --- |
| Registrierung, Login, Abmeldung und Sitzungswiderruf | bestanden | Backend-Auth-Tests und Galaxy-Geräteprüfung |
| Deutsch und Russisch im Onboarding | bestanden | Übersetzungsprüfung und Galaxy-Prüfung |
| Text-, Foto-, Barcode- und manuelle Erfassung | bestanden | fokussierte Android-/Backend-Tests |
| Review vor dem Speichern, Bearbeiten und Löschen | bestanden | Review-, Diary- und Löschtests |
| Offline-Wiederholung ohne Duplikate | bestanden | `MealSync*`-Tests und Backend-Idempotenztests |
| Health Connect: leer, teilweise, Konflikt und Synchronisierung | bestanden | Galaxy-Prüfung und Health-Connect-Integrationstests |
| Festes und dynamisches Kalorienbudget | bestanden | Galaxy-Prüfung und Budgettests |
| Nutzerisolation über Mahlzeiten, Fotos, Ziele und Health-Daten | bestanden | Zwei-Konto-Backendtests |
| Kontolöschung und aktive Sitzungen | bestanden | Datenlöschtests |

## Geräteabnahme

| Gerät | Ergebnis | Nachweis |
| --- | --- | --- |
| Galaxy A52 4G (SM-A525F), Android 16 / LineageOS 23.2 | bestanden | 51 Instrumentierungstests, 0 Fehler am 2026-08-01; drei opt-in Backend-Integrationen im Standardlauf übersprungen |
| Zweites reales Android-Gerät | blockiert | aktuell kein zweites Gerät per ADB verfügbar |

Die Beta wird erst freigegeben, wenn die zweite reale Geräteprüfung nachgeholt und
dieses Protokoll mit Build-Hash und Commit aktualisiert wurde.

## Bekannte Einschränkungen

- Open Food Facts und KI benötigen im Live-Betrieb erreichbare externe Dienste; lokale Pflichtprüfungen verwenden Fixtures.
- Health Connect bleibt optional; ohne Berechtigungen bleibt die App ein vollständiger Kalorientracker ohne Aktivitätsaufschlag.
- Die zweite reale Geräteabnahme ist vor der privaten Freigabe nachzuholen.

## Rollback

1. Beta-APK auf dem Testgerät deinstallieren oder auf die vorherige signierte interne Version zurücksetzen.
2. Backend auf den vorherigen Commit zurücksetzen.
3. Vor einer Migration ein Datenbank-Backup erstellen; Migrationen werden nicht rückwärts ausgeführt.
4. Bei einem fehlgeschlagenen Löschjob `make retry-deletions` ausführen und den Status prüfen.
