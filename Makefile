.PHONY: quality android-lint android-test android-build translations backend-run access-code

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

backend-run:
	cd backend && python -m uvicorn baseline_api.main:app --reload

access-code:
	cd backend && python manage.py create-access-code --hours $${HOURS:-72}
