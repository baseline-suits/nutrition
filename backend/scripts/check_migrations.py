import sqlite3
import tempfile
from pathlib import Path

from baseline_api import main


def applied_versions(database: Path) -> set[str]:
    with sqlite3.connect(database) as connection:
        return {row[0] for row in connection.execute("SELECT version FROM schema_migrations")}


def main_check() -> None:
    migrations = Path(__file__).parents[1] / "migrations"
    expected = {path.name for path in migrations.glob("*.sql")}
    with tempfile.TemporaryDirectory(prefix="baseline-migrations-") as directory:
        root = Path(directory)
        fresh = root / "fresh.db"
        main.settings.database = fresh
        main.migrate()
        assert applied_versions(fresh) == expected

        previous = root / "previous.db"
        ordered = sorted(migrations.glob("*.sql"))
        with sqlite3.connect(previous) as connection:
            for migration in ordered[:-1]:
                connection.executescript(migration.read_text(encoding="utf-8"))
            connection.execute(
                """CREATE TABLE schema_migrations (
                   version TEXT PRIMARY KEY,
                   applied_at TEXT NOT NULL
                )"""
            )
            connection.executemany(
                "INSERT INTO schema_migrations VALUES (?, ?)",
                [(migration.name, main.iso(main.now())) for migration in ordered[:-1]],
            )
            connection.commit()
        main.settings.database = previous
        main.migrate()
        assert applied_versions(previous) == expected


if __name__ == "__main__":
    main_check()
