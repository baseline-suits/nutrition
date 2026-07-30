# Quality-Gate-Matrix

## Sofort aktive Pflichtprüfungen

| Gate | Lokal | CI | Blockiert bei Fehler |
| --- | --- | --- | --- |
| Deutsche/russische Ressourcen vollständig | `make translations` | `android / translations` | ja |
| Android-Lint | `make android-lint` | `android / lint` | ja |
| Android-Unit-Tests | `make android-test` | `android / unit-tests` | ja |
| Debug- und interne Beta-APK | `make android-build` | `android / build` | ja |
| Keine lokale Adresse oder offensichtliche Tokens in Beta-APK | `scripts/check-beta-apk.sh <apk>` | `android / beta-safety` | ja |
| Compose-Navigationstests | Android-Gerät/Emulator | `android-instrumented` auf Main oder manuell | ja für internen Beta-Stand |
| Abhängigkeitsprüfung | GitHub | `dependency-review` auf Pull Requests | ja ab mittlerem Schweregrad |

Die Branch-Protection für `main` muss im GitHub-Repository die Jobs `android` und `dependency-review` als erforderliche Checks konfigurieren. Repository-Einstellungen werden bewusst nicht aus der lokalen CI-Datei verändert.

## Artefakte

- Debug-/Beta-APK: 7 Tage
- Lint- und Unit-Testberichte: 14 Tage
- Instrumentierungsergebnisse: 14 Tage
- Backend-Test-, Migrations- und OpenAPI-Artefakte: nach Aktivierung 14 Tage

Die interne Beta-APK wird als `baseline-nutrition-<version>-beta-<commit>.apk` abgelegt. Ein Main-Build veröffentlicht nichts automatisch im Play Store.

## Backend-Vertrag

ID-306 ist in Linear durch ID-284 und ID-286 blockiert. Sobald `backend/pyproject.toml` vorhanden ist, aktiviert die CI automatisch den Backend-Job. Das Backend stellt dafür folgende reproduzierbare Make-Ziele bereit:

| Ziel | Erwartete Prüfung |
| --- | --- |
| `ci-install` | Lockfile-verifizierte Installation |
| `ci-format` | Formatierung |
| `ci-lint` | Linting |
| `ci-types` | Typprüfung |
| `ci-unit` | Unit-Tests ohne externe Produktivzugriffe |
| `ci-api` | API- und Nutzerisolations-Tests |
| `ci-database` | Integrationstests auf temporärem PostgreSQL |
| `ci-migrations` | leere Datenbank und Upgrade vom vorherigen Schema |
| `ci-openapi` | OpenAPI-Contract nach `artifacts/openapi.json` |

Die Tests verwenden ausschließlich Fixtures/Fakes für OpenAI/OpenRouter, Open Food Facts und Objektspeicher. CI-Secrets werden für Pull Requests aus Forks nicht bereitgestellt. Produktive KI-Evals gehören nicht zu den Pflichtchecks.

## Flaky Tests

Pflichttests werden nicht automatisch wiederholt. Ein instabiler Test bleibt rot, bis die Ursache behoben oder der Test mit dokumentierter Begründung aus dem Pflichtpfad genommen wurde.

