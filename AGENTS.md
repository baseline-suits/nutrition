# Repository Guidelines

## Project Structure & Module Organization

The native Android application lives in `app/`. Production Kotlin and Compose code is under `app/src/main/java/de/baseline/nutrition`, grouped into `core`, `data`, `domain`, and `ui`; Android resources are in `app/src/main/res`. JVM tests belong in `app/src/test`, while device and Compose navigation tests belong in `app/src/androidTest`.

The FastAPI service is in `backend/baseline_api`, database migrations are ordered SQL files in `backend/migrations`, and API tests are in `backend/tests`. Shared automation lives in `scripts/`, with quality-gate documentation in `docs/`.

## Build, Test, and Development Commands

- `make quality`: run the repository’s main local quality gate.
- `make android-build`: build debug and internal-beta APKs.
- `make android-lint`: run Android lint for the debug variant.
- `make android-test`: run JVM unit tests.
- `make translations`: verify German and Russian string completeness.
- `PYTHONPATH=backend pytest -q backend/tests`: run backend API tests.
- `make backend-run`: start FastAPI with reload at `127.0.0.1:8000`.
- `make access-code HOURS=72`: create a temporary registration code.

Use JDK 17, Android SDK 35, and an activated Python virtual environment with `backend/requirements-dev.txt`.

## Coding Style & Naming Conventions

Use four-space indentation in Kotlin and Python. Follow existing Kotlin conventions: `PascalCase` for classes, composables, and files; `camelCase` for functions and properties; package names remain lowercase. Python functions and modules use `snake_case`. Keep UI, domain logic, and data access in their existing layers. Add user-facing text to resource files rather than hard-coding it, and update all supported locales.

## Testing Guidelines

Use JUnit for JVM tests, AndroidX/Compose for instrumented tests, and pytest for the backend. Name Kotlin tests `*Test.kt` and Python tests `test_*.py`. Add focused tests beside the affected layer. Run the relevant targeted test during development and `make quality` before submitting substantial changes.

## Commit & Pull Request Guidelines

Recent commits use short, imperative summaries such as `Add diary UI and repository integration`. Keep each commit focused. Pull requests should explain the behavior change, link the relevant issue, list verification commands, and include screenshots for visible UI changes. Ensure required CI checks pass.

## Security & Configuration

Never commit tokens, access codes, API keys, databases, or local SDK paths. Configure API URLs through `BASELINE_DEBUG_API_URL` or `BASELINE_BETA_API_URL`; beta and production endpoints must use HTTPS.
