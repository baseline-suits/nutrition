# Baseline Nutrition deployen

Diese Anleitung beschreibt den derzeit unterstützten manuellen Deploymentweg
für Baseline Nutrition: ein einzelner Backend-Host für die private Beta und
eine manuell gebaute Android-APK. Das Repository enthält aktuell keine
Docker-/Kubernetes-/Terraform-Konfiguration, keine automatische Server-
Provisionierung und keinen automatischen Play-Store-Upload.

Der Ablauf ist deshalb bewusst auf einen kleinen, kontrollierten
Single-Host-Betrieb ausgelegt. Für eine öffentliche Produktion müssten
mindestens Release-Signing, ein produktiver API-Endpunkt, ein belastbares
Backup-/Monitoring-Konzept und eine skalierbare Datenhaltung ergänzt werden.

## Architektur und Grenzen

~~~mermaid
flowchart LR
    A["Android APK"] -->|HTTPS| B["Reverse Proxy / TLS"]
    B --> C["Uvicorn / FastAPI<br/>127.0.0.1:8000"]
    C --> D["SQLite-Datenbank"]
    C --> E["Privater Objektspeicher<br/>Essensfotos"]
    C --> F["OpenAI-kompatibler Analyseanbieter"]
    C --> G["Open Food Facts"]
~~~

Wichtige Eigenschaften des aktuellen Stands:

- Das Backend nutzt SQLite und einen In-Memory-Rate-Limiter. Betreibe daher
  zunächst genau einen Uvicorn-Prozess auf einem Host.
- Datenbank und BASELINE_OBJECT_STORE müssen auf persistentem Speicher
  liegen und gemeinsam gesichert werden.
- Für den aktuellen Cloudflare-Tunnel-Betrieb lauscht Uvicorn auf
  0.0.0.0:8000. Der Port darf trotzdem nicht aus dem Internet erreichbar
  sein; die Firewall sollte den Zugriff auf das lokale Netz beziehungsweise
  den vorgesehenen Tunnelpfad begrenzen.
- internalBeta ist für die private Beta gedacht und wird derzeit mit der
  Debug-Signatur gebaut. Diese Variante ist kein Play-Store-Release.
- Die release-Variante verwendet im Repository noch einen Platzhalter-
  Endpunkt (https://api.example.invalid/) und ist nicht als fertiger
  Produktions-Release konfiguriert.

## Voraussetzungen

### Backend-Host

- Linux-Host mit einem nicht privilegierten Service-Account
- Python 3.13 exakt. Mit den aktuell gepinnten Abhängigkeiten ist Python 3.14
  derzeit nicht kompatibel.
- DNS-Eintrag für den Beta- oder Produktionshost
- TLS-fähiger Reverse Proxy, zum Beispiel Caddy oder Nginx
- persistenter, regelmäßig gesicherter Speicher für SQLite und private Fotos
- ausgehende HTTPS-Verbindungen zum Analyseanbieter und zu Open Food Facts
- Firewall: öffentlich nur die Proxy-Ports 80/443; Port 8000 nur für den
  vorgesehenen lokalen/Tunnel-Zugriff freigeben

### Android-Build-Host

- JDK 17
- Android SDK 35 und Build Tools 35.0.0
- gesetztes ANDROID_HOME
- optional adb für Installation und Smoke-Tests auf einem Gerät oder
  Emulator

Auf Fedora 44 kann Python 3.13 parallel zum standardmäßigen Python 3.14
installiert werden:

~~~bash
sudo dnf install python3.13
/usr/bin/python3.13 --version
~~~

Danach für alle Baseline-Nutrition-Befehle explizit
/usr/bin/python3.13 beziehungsweise die daraus erstellte Venv verwenden.

Die vollständigen Projektvoraussetzungen stehen zusätzlich im
[README](README.md).

## 1. Backend installieren

Die folgenden Beispiele verwenden /opt/baseline-nutrition als Quellpfad und
/var/lib/baseline-nutrition als persistenten Datenpfad. Ersetze diese Pfade
nur, wenn du die systemd- und Backup-Konfiguration entsprechend anpasst.

Zuerst einen Service-Account und die Datenverzeichnisse anlegen. Falls der
Account bereits existiert, den useradd-Schritt überspringen:

~~~bash
sudo useradd --system \
  --home-dir /opt/baseline-nutrition \
  --shell /usr/sbin/nologin \
  baseline-nutrition

sudo install -d -o baseline-nutrition -g baseline-nutrition -m 0750 \
  /opt/baseline-nutrition \
  /var/lib/baseline-nutrition \
  /var/lib/baseline-nutrition/private_objects
~~~

Den Quellcode auf einen bekannten Commit oder Tag auschecken. Ein
unveränderlicher Commit ist für reproduzierbare Deployments besser als der
jeweils aktuelle Branch:

~~~bash
sudo git clone <REPOSITORY-URL> /opt/baseline-nutrition
sudo chown -R baseline-nutrition:baseline-nutrition /opt/baseline-nutrition

sudo -u baseline-nutrition git -C /opt/baseline-nutrition fetch --tags origin
sudo -u baseline-nutrition git -C /opt/baseline-nutrition checkout <COMMIT-ODER-TAG>
sudo -u baseline-nutrition git -C /opt/baseline-nutrition status --short
~~~

Virtuelle Umgebung erstellen und nur die Runtime-Abhängigkeiten installieren:

~~~bash
python313="$(command -v python3.13 || true)"
if [ -z "$python313" ]; then
  echo "Python 3.13 fehlt. Erst den System-Interpreter installieren."
  exit 1
fi
"$python313" --version
"$python313" -c 'import sys; assert sys.version_info[:2] == (3, 13), sys.version; print(sys.version)'

sudo -u baseline-nutrition "$python313" -m venv \
  /opt/baseline-nutrition/.venv
sudo -u baseline-nutrition /opt/baseline-nutrition/.venv/bin/python \
  -m pip install -r /opt/baseline-nutrition/backend/requirements.txt
~~~

Wichtig: Nicht einfach python verwenden, wenn dieser Befehl auf Python 3.14
zeigt. Eine bereits mit der falschen Version erzeugte Venv kann nicht durch
einen späteren pip-Aufruf umgestellt werden. Falls der Installationsversuch
bereits mit Python 3.14 gelaufen ist, den Service zunächst stoppen, die Venv
reversibel umbenennen und sie mit Python 3.13 neu erzeugen:

~~~bash
python313="$(command -v python3.13 || true)"
if [ -z "$python313" ]; then
  echo "Python 3.13 fehlt. Erst den System-Interpreter installieren."
  exit 1
fi

if sudo systemctl cat baseline-nutrition.service >/dev/null 2>&1; then
  sudo systemctl stop baseline-nutrition
fi

if [ -d /opt/baseline-nutrition/.venv ]; then
  venv_backup_suffix="$(date +%Y%m%d-%H%M%S)"
  sudo mv /opt/baseline-nutrition/.venv \
    "/opt/baseline-nutrition/.venv-python314-$venv_backup_suffix"
fi

sudo -u baseline-nutrition "$python313" -m venv \
  /opt/baseline-nutrition/.venv
sudo -u baseline-nutrition /opt/baseline-nutrition/.venv/bin/python \
  -c 'import sys; assert sys.version_info[:2] == (3, 13), sys.version; print(sys.version)'
sudo -u baseline-nutrition /opt/baseline-nutrition/.venv/bin/python \
  -m pip install -r /opt/baseline-nutrition/backend/requirements.txt
~~~

Die alte Venv enthält keine persistenten Anwendungsdaten und kann nach
erfolgreicher Prüfung später gelöscht werden. Wenn die Installation mit
Python 3.13 trotzdem versucht, pydantic-core aus dem Quellcode zu bauen,
zuerst Interpreter-Version und Plattform prüfen; für diesen Deploy sollte
kein Python-3.14-PyO3-Build erzwungen werden.

Für Tests und lokale Qualitätsprüfungen kann zusätzlich die
Entwicklungsdatei installiert werden:

~~~bash
sudo -u baseline-nutrition /opt/baseline-nutrition/.venv/bin/python \
  -m pip install -r /opt/baseline-nutrition/backend/requirements-dev.txt
~~~

## 2. Backend konfigurieren

Konfiguration und Geheimnisse gehören nicht ins Git-Repository. Die
systemd-Unit liest eine root-only-Datei:

~~~bash
sudo install -d -m 0750 /etc/baseline-nutrition
sudoedit /etc/baseline-nutrition/backend.env
sudo chmod 0600 /etc/baseline-nutrition/backend.env
~~~

Beispielinhalt:

~~~dotenv
BASELINE_DATABASE=/var/lib/baseline-nutrition/baseline.db
BASELINE_OBJECT_STORE=/var/lib/baseline-nutrition/private_objects

EXTERNAL_SERVICES_MODE=live
OPENAI_API_KEY=<SERVER-SEITIGES-GEHEIMNIS>
OPENAI_BASE_URL=https://api.openai.com
BASELINE_ANALYSIS_MODEL=<VERFUEGBARES-MODELL>

BASELINE_SESSION_HOURS=720
BASELINE_ANALYSIS_DAILY_LIMIT=100
BASELINE_ANALYSIS_WEEKLY_LIMIT=500
BASELINE_ANALYSIS_COST_ALERT_MICROS=50000000

BASELINE_OFF_BASE_URL=https://world.openfoodfacts.org
BASELINE_OFF_USER_AGENT="BaselineNutrition/0.1 (private beta)"
BASELINE_OFF_CACHE_HOURS=24
~~~

Die wichtigsten Einstellungen:

| Variable | Zweck |
| --- | --- |
| BASELINE_DATABASE | Absoluter Pfad zur SQLite-Datei. |
| BASELINE_OBJECT_STORE | Absoluter Pfad für private Essensfotos; niemals öffentlich ausliefern. |
| OPENAI_API_KEY | Einzige Stelle für den serverseitigen Analyse-Key; nie in die APK oder ins Git legen. |
| OPENAI_BASE_URL | OpenAI- oder kompatibler Analyseanbieter über HTTPS. |
| BASELINE_ANALYSIS_MODEL | Modellname, der beim konfigurierten Anbieter verfügbar ist. |
| BASELINE_SESSION_HOURS | Gültigkeitsdauer von Sessions. |
| BASELINE_ANALYSIS_DAILY_LIMIT / BASELINE_ANALYSIS_WEEKLY_LIMIT | Schutzlimits für Analysen. |
| BASELINE_ANALYSIS_COST_ALERT_MICROS | Kostenwarnschwelle in Mikroeinheiten gemäß Backend-Konfiguration. |
| EXTERNAL_SERVICES_MODE | Im Betrieb live; fake ist für Tests und CI gedacht. |
| BASELINE_OFF_* | Open-Food-Facts-Endpunkt, User-Agent und Cache-Dauer. |

Die Anwendung legt Tabellen beim Start über ihre Migrationen an. Migrationen
sind vorwärtsgerichtet. Vor jedem Update mit potenziellen Schemaänderungen
eine konsistente Sicherung von Datenbank und Objektspeicher erstellen; ein
Rollback erfolgt über die passende Sicherung beider Datenbestände, nicht über
eine erfundene Down-Migration.

## 3. Backend als systemd-Service starten

Eine versionierte Vorlage liegt in
deploy/baseline-nutrition.service. Die Beispielkonfiguration für die
Umgebungsvariablen liegt in deploy/backend.env.example. Auf dem Zielhost
können die Dateien so installiert werden:

~~~bash
sudo install -D -o root -g root -m 0644 \
  deploy/baseline-nutrition.service \
  /etc/systemd/system/baseline-nutrition.service
sudo install -d -o root -g root -m 0750 /etc/baseline-nutrition
sudo install -o root -g root -m 0600 \
  deploy/backend.env.example \
  /etc/baseline-nutrition/backend.env
~~~

Vor dem Start OPENAI_API_KEY und die übrigen umgebungsspezifischen Werte in
/etc/baseline-nutrition/backend.env prüfen. Die Unit selbst sieht so aus:

~~~ini
[Unit]
Description=Baseline Nutrition API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=baseline-nutrition
Group=baseline-nutrition
WorkingDirectory=/opt/baseline-nutrition/backend
EnvironmentFile=/etc/baseline-nutrition/backend.env
Environment=PYTHONPATH=/opt/baseline-nutrition/backend
ExecStart=/opt/baseline-nutrition/.venv/bin/uvicorn baseline_api.main:app --host 0.0.0.0 --port 8000 --proxy-headers --forwarded-allow-ips=127.0.0.1
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/baseline-nutrition

[Install]
WantedBy=multi-user.target
~~~

Aktivieren und prüfen:

~~~bash
sudo systemctl daemon-reload
sudo systemctl enable --now baseline-nutrition
sudo systemctl status baseline-nutrition --no-pager
sudo journalctl -u baseline-nutrition -n 100 --no-pager
~~~

Im Deployment kein --reload verwenden. Nach einem Code- oder
Konfigurationsupdate den Service gezielt neu starten:

~~~bash
sudo systemctl restart baseline-nutrition
sudo journalctl -u baseline-nutrition -n 100 --no-pager
~~~

## 4. HTTPS-Reverse-Proxy einrichten

Der Proxy muss DNS, TLS und die Weiterleitung an
http://127.0.0.1:8000 übernehmen. Beispiel für Caddy:

~~~caddyfile
beta.example.com {
    reverse_proxy 127.0.0.1:8000
}
~~~

Dafür müssen:

1. beta.example.com auf den Backend-Host zeigen,
2. die Ports 80 und 443 in der Firewall erreichbar sein,
3. Port 8000 von außen blockiert bleiben und
4. der Proxy die üblichen X-Forwarded-*-Header korrekt setzen.

Danach den Liveness-Endpunkt prüfen:

~~~bash
curl --fail --silent --show-error https://beta.example.com/health
~~~

Erwartete Antwort:

~~~json
{"status":"ok"}
~~~

/health prüft nur, ob die API antwortet. Für einen echten Smoke-Test
zusätzlich Anmeldung, Datenbankzugriff, Analyseanbieter, Foto-Upload und
Löschung prüfen.

## 5. Zugangscode für die private Beta erzeugen

Ein Zugangscode wird beim Erzeugen einmalig im Klartext ausgegeben. Ausgabe
deshalb nur über einen sicheren Admin-Kanal weitergeben:

~~~bash
sudo bash -c '
set -a
. /etc/baseline-nutrition/backend.env
set +a
cd /opt/baseline-nutrition/backend
exec runuser -u baseline-nutrition -- /opt/baseline-nutrition/.venv/bin/python \
  manage.py create-access-code --hours 72
'
~~~

Weitere Wartungsbefehle:

~~~bash
sudo bash -c '
set -a
. /etc/baseline-nutrition/backend.env
set +a
cd /opt/baseline-nutrition/backend
runuser -u baseline-nutrition -- /opt/baseline-nutrition/.venv/bin/python manage.py cleanup-uploads
runuser -u baseline-nutrition -- /opt/baseline-nutrition/.venv/bin/python manage.py retry-deletions
runuser -u baseline-nutrition -- /opt/baseline-nutrition/.venv/bin/python manage.py analysis-report --days 7
'
~~~

cleanup-uploads und retry-deletions sollten über einen Scheduler regelmäßig
laufen. Für den Anfang sind zum Beispiel stündliche Bereinigung und ein
Retry-Lauf alle 5–15 Minuten angemessen. Die Befehle müssen mit denselben
Umgebungsvariablen wie der Service ausgeführt werden.

Dafür liegen versionierte systemd-Vorlagen in deploy/:

- baseline-nutrition-cleanup.service und baseline-nutrition-cleanup.timer
- baseline-nutrition-retry-deletions.service und
  baseline-nutrition-retry-deletions.timer

Die Timer laufen als User baseline-nutrition und können nach der Installation
mit systemctl enable --now baseline-nutrition-cleanup.timer
baseline-nutrition-retry-deletions.timer aktiviert werden.

## 6. Datenbank und private Fotos sichern

SQLite-Datei und BASELINE_OBJECT_STORE bilden zusammen den Datenbestand.
Sie müssen in derselben Sicherungsstrategie enthalten sein. Backups:

- verschlüsselt und mit restriktiven Dateirechten speichern,
- auf einem anderen Speichersystem replizieren,
- regelmäßig testweise wiederherstellen,
- vor Migrationen und Releases erstellen,
- niemals über das öffentliche Web-Verzeichnis ausliefern.

Ein einfaches konsistentes Wartungs-Backup ist möglich, indem der
Einzelprozess kurz angehalten und der komplette Datenpfad archiviert wird:

~~~bash
sudo systemctl stop baseline-nutrition
sudo tar -C /var/lib -czf \
  /var/backups/baseline-nutrition-$(date +%Y%m%d-%H%M%S).tar.gz \
  baseline-nutrition
sudo systemctl start baseline-nutrition
~~~

Vor dem produktiven Einsatz eine Aufbewahrungs- und Verschlüsselungsregel
festlegen. Für die Wiederherstellung:

1. Service stoppen und sicherstellen, dass kein zweiter Prozess läuft.
2. Eine passende Sicherung von Datenbank und Objektspeicher auswählen.
3. Beide Datenbestände gemeinsam und mit den Eigentümern
   baseline-nutrition wiederherstellen.
4. Service starten, /health prüfen und einen fachlichen Smoke-Test
   durchführen.

## 7. Android-Debug-APK bauen

Für einen lokalen Emulator verwendet die Debug-Variante standardmäßig
http://10.0.2.2:8000/. Backend lokal starten:

~~~bash
make backend-run
~~~

Dann in einem zweiten Terminal im Repository:

~~~bash
export BASELINE_DEBUG_API_URL=http://10.0.2.2:8000/
./gradlew --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

Für ein physisches Android-Gerät ist adb reverse der einfachste lokale Weg,
ohne den Backend-Port im LAN zu öffnen:

~~~bash
adb reverse tcp:8000 tcp:8000
export BASELINE_DEBUG_API_URL=http://127.0.0.1:8000/
./gradlew --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

Die Debug-Konfiguration erlaubt lokales HTTP ausschließlich für diese
Entwicklungsvariante. Für eine Beta- oder Produktions-APK immer HTTPS
verwenden.

## 8. Private-Beta-APK bauen und verteilen

Vor dem Paketieren die lokalen Qualitätsprüfungen ausführen:

~~~bash
make translations
make security-scan
./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest
make quality
~~~

Die Beta-URL wird beim Gradle-Build in die internalBeta-Variante eingebaut.
Beispiel:

~~~bash
export BASELINE_BETA_API_URL=https://beta.example.com/
./gradlew --no-daemon :app:assembleInternalBeta
~~~

APK prüfen und für die Weitergabe mit Version und Commit verpacken:

~~~bash
ANDROID_HOME="$ANDROID_HOME" \
  ./scripts/check-beta-apk.sh \
  app/build/outputs/apk/internalBeta/app-internalBeta.apk

./scripts/package-beta-apk.sh
sha256sum artifacts/baseline-nutrition-*-beta-*.apk
~~~

Das Prüfskript lehnt unter anderem lokale Endpunkte und offensichtliche
Secrets in der APK ab. Den erzeugten APK-Artefaktpfad und den SHA-256-Hash
über einen sicheren Kanal an die Tester verteilen. Installation auf einem
bereits registrierten Gerät:

~~~bash
adb install -r app/build/outputs/apk/internalBeta/app-internalBeta.apk
~~~

internalBeta ist aktuell debug-signiert und sollte nur an den vorgesehenen
privaten Beta-Kreis verteilt werden. Für einen öffentlichen Release fehlen
noch mindestens ein verwalteter Release-Key, eine produktive URL ohne
Platzhalter, ein geprüfter Release-Build mit geeigneter Shrink-/Obfuscation-
Konfiguration sowie ein kontrollierter Distributionsprozess.

## 9. Update-Runbook

Für ein Backend-Update:

1. Ziel-Commit und Änderungsumfang prüfen.
2. Datenbank und private Fotos sichern.
3. Service stoppen, wenn die Sicherungsstrategie keine laufende SQLite-Datei
   unterstützt.
4. Commit auschecken und Runtime-Abhängigkeiten aktualisieren.
5. Service starten; dabei werden ausstehende Migrationen angewendet.
6. Logs, /health, Anmeldung und die kritischen Nutzerflüsse prüfen.
7. Erst danach eine neue Beta-APK mit genau dieser Backend-URL bauen und
   verteilen.

Beispiel für den Codewechsel:

~~~bash
sudo systemctl stop baseline-nutrition
sudo -u baseline-nutrition git -C /opt/baseline-nutrition fetch --tags origin
sudo -u baseline-nutrition git -C /opt/baseline-nutrition checkout <NEUER-COMMIT-ODER-TAG>
sudo -u baseline-nutrition /opt/baseline-nutrition/.venv/bin/python \
  -m pip install -r /opt/baseline-nutrition/backend/requirements.txt
sudo systemctl start baseline-nutrition
sudo systemctl status baseline-nutrition --no-pager
curl --fail --silent --show-error https://beta.example.com/health
~~~

Wenn ein Update nicht funktioniert, Service stoppen und die zugehörige
Sicherung von Datenbank und Objektspeicher wiederherstellen. Ein APK-Rollback
allein reicht bei inkompatiblen Datenbankmigrationen nicht aus.

## 10. CI und Artefakte

GitHub Actions prüft Pull Requests und Pushes unter anderem mit Android-Lint,
JVM-Unit-Tests, Android-Build/Beta-Sicherheitsprüfung, Übersetzungs- und
Secret-Checks sowie Backend-Tests. Instrumentierte Android-Tests laufen nur
für main oder manuell. Die CI veröffentlicht derzeit keine APK automatisch
im Play Store und deployt auch kein Backend.

Für den Branch-Schutz sollten die im Projekt dokumentierten Pflichtchecks
android und dependency-review verlangt werden. Ein erfolgreicher CI-Lauf
ist eine Qualitätsfreigabe für das manuelle Deployment, ersetzt aber nicht
den Smoke-Test gegen die konkrete Umgebung.

## 11. Minimaler Smoke-Test nach jedem Deploy

- [ ] Backend läuft als genau ein Prozess und startet nach einem Reboot.
- [ ] https://beta.example.com/health antwortet erfolgreich (Domain ersetzen).
- [ ] Port 8000 ist nicht aus dem Internet erreichbar; bei 0.0.0.0-Bind ist
      der Zugriff per Firewall auf den vorgesehenen Pfad begrenzt.
- [ ] Ein neuer Zugangscode kann erzeugt und einmalig verwendet werden.
- [ ] Zwei getrennte Benutzerkonten bleiben voneinander isoliert.
- [ ] Mahlzeit anlegen, bearbeiten und synchronisieren funktioniert.
- [ ] Foto-Upload, Analyse und Barcode-/Open-Food-Facts-Abfrage funktionieren.
- [ ] Offline-Queue und erneute Synchronisation funktionieren.
- [ ] Löschung und verzögerte Löschjobs funktionieren.
- [ ] Keine Secrets oder lokalen URLs befinden sich in der Beta-APK.
- [ ] Logs, Speicherbelegung und Backup-Ergebnis sind plausibel.

## Bekannte nächste Schritte für einen echten Produktionsbetrieb

Diese Punkte sind nicht Teil des aktuellen manuellen Beta-Deploys:

- Release-Signing und sichere Key-Verwaltung für Android
- produktiver API-Endpunkt in der release-Variante
- automatisierter, reproduzierbarer Backend-Deploy
- externe Datenbank beziehungsweise ein belastbarer SQLite-Betriebsplan
- zentralisiertes Monitoring, Alerting und Audit-Logging
- getestete Backup-Retention und Disaster-Recovery
- Rate-Limit- und Job-Zustandhaltung außerhalb des einzelnen Prozesses
- kontrollierte APK-/Play-Store-Distribution
