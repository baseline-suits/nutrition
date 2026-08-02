.PHONY: quality android-lint android-test android-build translations security-scan backend-test backend-run access-code retry-deletions

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

security-scan:
	./scripts/check-secrets.sh

backend-test:
	$(MAKE) -C backend quality

backend-run:
	cd backend && python -m uvicorn baseline_api.main:app --reload

access-code:
	cd backend && python manage.py create-access-code --hours $${HOURS:-72}

retry-deletions:
	cd backend && python manage.py retry-deletions
