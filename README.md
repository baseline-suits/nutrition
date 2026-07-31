# Baseline Nutrition

Native Android-App für Baseline Nutrition.

Das Repository enthält außerdem die private, versionierte Backend-API für
Konten, Profile und Mahlzeiten.

## Voraussetzungen

- JDK 17
- Android SDK 35
- Android Build Tools 35.0.0
- Python 3.13

`ANDROID_HOME` muss auf das installierte Android SDK zeigen. Weitere globale Tools sind nicht erforderlich.

## Android lokal bauen

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
```

Die Debug-APK liegt anschließend unter `app/build/outputs/apk/debug/`. Installation auf einem verbundenen Gerät:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Die lokale API-Adresse ist ausschließlich in der Debugvariante als `http://10.0.2.2:8000/` voreingestellt. Sie kann ohne Quellcodeänderung gesetzt werden:

```bash
BASELINE_DEBUG_API_URL=http://192.168.1.10:8000/ ./gradlew :app:assembleDebug
```

Der interne Beta-Build verwendet standardmäßig eine HTTPS-Adresse und blockiert Klartextverkehr:

```bash
BASELINE_BETA_API_URL=https://beta.example.invalid/ ./gradlew :app:assembleInternalBeta
```

Die URLs sind Konfiguration, keine Geheimnisse. Tokens, Zugangscodes und API-Schlüssel dürfen weder als Gradle-Property noch als `BuildConfig`-Wert hinterlegt werden.

## Backend lokal starten

```bash
python -m venv .venv
.venv/bin/pip install -r backend/requirements-dev.txt
make access-code HOURS=72
make backend-run
```

Die API liegt unter `http://127.0.0.1:8000`, die OpenAPI-Dokumentation unter
`/docs`. Standardmäßig wird `backend/baseline.db` verwendet; für Tests und
Deployments kann `BASELINE_DATABASE` auf einen anderen Pfad gesetzt werden.
Der Zugangscode wird nur beim Erzeugen im Klartext ausgegeben.

Backendtests:

```bash
make backend-test
```

## Qualitätsprüfungen

Die lokal verfügbaren Befehle entsprechen den Pflichtschritten der CI:

```bash
./scripts/quality-gate.sh
./scripts/check-translations.sh
```

Eine Übersicht der Gates und Artefakte steht in [docs/quality-gates.md](docs/quality-gates.md).
