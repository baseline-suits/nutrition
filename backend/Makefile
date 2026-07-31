PYTHON ?= python
PYTEST = PYTHONPATH=. $(PYTHON) -m pytest

.PHONY: ci-install ci-format ci-lint ci-types ci-unit ci-api ci-database ci-migrations ci-openapi quality

ci-install:
	$(PYTHON) -m pip install -r requirements-dev.txt

ci-format:
	$(PYTHON) -m ruff format --check baseline_api tests scripts

ci-lint:
	$(PYTHON) -m ruff check baseline_api tests scripts

ci-types:
	$(PYTHON) -m mypy baseline_api

ci-unit:
	$(PYTEST) -q tests/test_foundation.py -k "expired_access_code or expired_session"

ci-api:
	$(PYTEST) -q tests/test_api.py

ci-database:
	$(PYTEST) -q tests/test_foundation.py

ci-migrations:
	PYTHONPATH=. $(PYTHON) scripts/check_migrations.py

ci-openapi:
	PYTHONPATH=. $(PYTHON) scripts/export_openapi.py artifacts/openapi.json
	diff -u openapi.snapshot.json artifacts/openapi.json

quality: ci-format ci-lint ci-types ci-unit ci-api ci-database ci-migrations ci-openapi
