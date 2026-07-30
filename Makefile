.PHONY: quality android-lint android-test android-build translations

quality:
	./scripts/quality-gate.sh

android-lint:
	./gradlew --no-daemon :app:lintDebug

android-test:
	./gradlew --no-daemon :app:testDebugUnitTest

android-build:
	./gradlew --no-daemon :app:assembleDebug :app:assembleInternalBeta

translations:
	./scripts/check-translations.sh

