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
- Backend-Test-, Migrations- und OpenAPI-Artefakte: 14 Tage

Die interne Beta-APK wird als `baseline-nutrition-<version>-beta-<commit>.apk` abgelegt. Ein Main-Build veröffentlicht nichts automatisch im Play Store.

## Backend-Vertrag

Der Backend-Job ist verbindlich aktiv. Das Backend stellt dafür folgende
reproduzierbare Make-Ziele bereit:

| Ziel | Erwartete Prüfung |
| --- | --- |
| `ci-install` | Installation ausschließlich aus vollständig gepinnten Abhängigkeiten |
| `ci-format` | Formatierung |
| `ci-lint` | Linting |
| `ci-types` | Typprüfung |
| `ci-unit` | Unit-Tests ohne externe Produktivzugriffe |
| `ci-api` | API- und Nutzerisolations-Tests |
| `ci-database` | Integrationstests auf einer frischen temporären SQLite-Datenbank |
| `ci-migrations` | leere Datenbank und Upgrade vom vorherigen Schema |
| `ci-openapi` | OpenAPI-Contract nach `artifacts/openapi.json` |
| `ci-eval` | Kostenlose, versionierte KI-Fixture-Evaluation nach `artifacts/eval/` |

Die Tests verwenden ausschließlich Fixtures/Fakes für OpenAI/OpenRouter, Open Food Facts und Objektspeicher. CI-Secrets werden für Pull Requests aus Forks nicht bereitgestellt. Produktive KI-Evals gehören nicht zu den Pflichtchecks.

Eine echte Modell-Evaluation wird vor Änderungen an Modell, Prompt oder Schema
bewusst über den manuellen Workflow `Meal model evaluation` gestartet. Sie
verwendet ausschließlich synthetische Repository-Testdaten, legt den Bericht
30 Tage als Artefakt ab und läuft nie automatisch in Pull Requests.

Die API speichert pro Analyse ausschließlich technische Metadaten wie
Modell-/Prompt-/Schemaversion, Status, Latenz, Token-/Kostenschätzung und
Fehlerkategorie. Vollständige Eingabetexte und Bilder werden nicht in
Standardlogs oder Telemetrietabellen kopiert. `python backend/manage.py
analysis-report --days 7` liefert Erfolgsrate, p50/p95, Kosten und einen
auffälligen `health`-Status. Pro Nutzer gelten serverseitige Tages- und
Wochenlimits; temporäre Fotos laufen nach spätestens 24 Stunden ab, sofern sie
nicht bewusst an eine Mahlzeit gebunden wurden.

## Flaky Tests

Pflichttests werden nicht automatisch wiederholt. Ein instabiler Test bleibt rot, bis die Ursache behoben oder der Test mit dokumentierter Begründung aus dem Pflichtpfad genommen wurde.
