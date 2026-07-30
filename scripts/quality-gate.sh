#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

./scripts/check-translations.sh
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleInternalBeta
./scripts/check-beta-apk.sh app/build/outputs/apk/internalBeta/app-internalBeta.apk

