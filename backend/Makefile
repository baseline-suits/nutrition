PYTHON ?= python
PYTEST = PYTHONPATH=. $(PYTHON) -m pytest

.PHONY: ci-install ci-format ci-lint ci-types ci-unit ci-api ci-database ci-migrations ci-openapi ci-eval eval-live quality

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
	$(PYTEST) -q tests/test_api.py tests/test_analysis.py tests/test_uploads.py

ci-database:
	$(PYTEST) -q tests/test_foundation.py

ci-migrations:
	PYTHONPATH=. $(PYTHON) scripts/check_migrations.py

ci-openapi:
	PYTHONPATH=. $(PYTHON) scripts/export_openapi.py artifacts/openapi.json
	diff -u openapi.snapshot.json artifacts/openapi.json

ci-eval:
	$(PYTEST) -q tests/test_evals.py
	PYTHONPATH=. $(PYTHON) scripts/run_evals.py --output-dir artifacts/eval

eval-live:
	EXTERNAL_SERVICES_MODE=live PYTHONPATH=. $(PYTHON) scripts/run_evals.py --live --output-dir artifacts/eval-live

quality: ci-format ci-lint ci-types ci-unit ci-api ci-database ci-migrations ci-openapi ci-eval
