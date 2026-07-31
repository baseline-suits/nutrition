import hashlib
import sqlite3
from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from pathlib import Path

from fastapi.testclient import TestClient

from baseline_api import main
from baseline_api.main import app, db, iso, migrate, now, uid


def create_code(value: str, *, expired: bool = False) -> None:
    migrate()
    expires = now() - timedelta(minutes=1) if expired else now() + timedelta(hours=1)
    with db() as connection:
        connection.execute(
            "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
            (uid(), hashlib.sha256(value.encode()).hexdigest(), iso(now()), iso(expires)),
        )
        connection.commit()


def register(client: TestClient, username: str, code: str) -> dict:
    return client.post(
        "/v1/auth/register",
        json={
            "access_code": code,
            "username": username,
            "password": "a-secure-password",
            "locale": "de",
            "timezone": "Europe/Berlin",
        },
    )


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def complex_meal(client_id: str = "complex-1") -> dict:
    return {
        "client_id": client_id,
        "local_day": "2026-07-31",
        "eaten_at": "2026-07-31T12:00:00+02:00",
        "timezone": "Europe/Berlin",
        "meal_type": "lunch",
        "name": "Reis mit Gemüse",
        "capture_method": "barcode",
        "provenance_source": "open_food_facts",
        "external_reference": "off:4008400214507",
        "ingredients": [
            {
                "original_name": "Reis",
                "amount": "120.25",
                "unit": "g",
                "nutrients": [
                    {
                        "key": "energy",
                        "value": "156.325",
                        "unit": "kcal",
                        "basis": "portion",
                        "source": "open_food_facts",
                    },
                    {
                        "key": "protein",
                        "value": "3.125",
                        "unit": "g",
                        "basis": "portion",
                        "source": "open_food_facts",
                    },
                ],
            },
            {
                "original_name": "Gemüse",
                "amount": "100",
                "unit": "g",
                "nutrients": [
                    {
                        "key": "energy",
                        "value": "35.10",
                        "unit": "kcal",
                        "basis": "portion",
                        "source": "user",
                    }
                ],
            },
        ],
    }


def test_expired_access_code_and_parallel_consumption(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "auth.db")
    create_code("expired-code-value-123456", expired=True)
    with TestClient(app) as client:
        assert register(client, "expired", "expired-code-value-123456").status_code == 400

    create_code("parallel-code-value-123456")

    def attempt(username: str) -> int:
        with TestClient(app) as client:
            return register(client, username, "parallel-code-value-123456").status_code

    with ThreadPoolExecutor(max_workers=2) as executor:
        statuses = sorted(executor.map(attempt, ["parallel-a", "parallel-b"]))
    assert statuses == [201, 400]


def test_sessions_profile_and_logout_are_user_bound(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "sessions.db")
    create_code("session-first-code-123456")
    create_code("session-second-code-123456")
    with TestClient(app) as client:
        first = register(client, "session-first", "session-first-code-123456").json()["token"]
        second = register(client, "session-second", "session-second-code-123456").json()["token"]
        profile = {
            "locale": "de",
            "timezone": "Europe/Berlin",
            "target_kcal": "2100",
            "target_protein_g": "130",
            "target_carbs_g": "220",
            "target_fat_g": "70",
            "manual": True,
            "user_id": "attempted-horizontal-escalation",
        }
        assert client.put("/v1/profile", json=profile, headers=auth(first)).status_code == 200
        assert client.get("/v1/profile", headers=auth(second)).status_code == 404

        assert client.post("/v1/auth/logout", headers=auth(first)).status_code == 204
        assert client.get("/v1/auth/session", headers=auth(first)).status_code == 401
        assert client.get("/v1/auth/session", headers=auth(second)).status_code == 200

        third = client.post(
            "/v1/auth/login",
            json={"username": "session-second", "password": "a-secure-password"},
        ).json()["token"]
        assert client.post("/v1/auth/logout-all", headers=auth(second)).status_code == 204
        assert client.get("/v1/auth/session", headers=auth(second)).status_code == 401
        assert client.get("/v1/auth/session", headers=auth(third)).status_code == 401


def test_expired_session_and_argon2_storage(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "expiry.db")
    create_code("expiry-code-value-123456")
    with TestClient(app) as client:
        token = register(client, "expiry-user", "expiry-code-value-123456").json()["token"]
        with db() as connection:
            password_hash = connection.execute(
                "SELECT password_hash FROM users WHERE username='expiry-user'"
            ).fetchone()["password_hash"]
            assert password_hash.startswith("$argon2")
            connection.execute(
                "UPDATE sessions SET expires_at=? WHERE token_hash=?",
                (iso(now() - timedelta(seconds=1)), hashlib.sha256(token.encode()).hexdigest()),
            )
            connection.commit()
        assert client.get("/v1/auth/session", headers=auth(token)).status_code == 401


def test_complex_roundtrip_bulk_day_and_delete(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "meals.db")
    create_code("meal-code-value-123456")
    with TestClient(app) as client:
        token = register(client, "meal-user", "meal-code-value-123456").json()["token"]
        headers = auth(token) | {"Idempotency-Key": "stable-complex"}
        created = client.post("/v1/meals", json=complex_meal(), headers=headers)
        assert created.status_code == 201, created.text
        body = created.json()
        assert body["totals"] == {"energy": "191.43", "protein": "3.13"}
        assert body["ingredients"][0]["nutrients"][0]["locked"] is True
        assert body["ingredients"][1]["nutrients"][0]["locked"] is True
        assert "iron" not in body["totals"]

        for index in range(1, 50):
            payload = complex_meal(f"bulk-{index}")
            payload["name"] = f"Mahlzeit {index}"
            response = client.post("/v1/meals", json=payload, headers=auth(token))
            assert response.status_code == 201
        listed = client.get(
            "/v1/days/2026-07-31/meals?limit=50", headers=auth(token)
        )
        assert listed.status_code == 200
        assert len(listed.json()) == 50

        summary = client.get("/v1/days/2026-07-31/summary", headers=auth(token)).json()
        assert summary["meal_count"] == 50
        assert summary["coverage"]["energy"] == 50
        assert "iron" not in summary["available"]

        meal_id = body["id"]
        assert client.delete(f"/v1/meals/{meal_id}", headers=auth(token)).status_code == 204
        assert client.delete(f"/v1/meals/{meal_id}", headers=auth(token)).status_code == 204
        assert client.get(f"/v1/meals/{meal_id}", headers=auth(token)).status_code == 404


def test_migrations_apply_to_empty_and_previous_schema(tmp_path, monkeypatch):
    empty = tmp_path / "empty.db"
    monkeypatch.setattr(main.settings, "database", empty)
    migrate()
    with sqlite3.connect(empty) as connection:
        versions = {
            row[0] for row in connection.execute("SELECT version FROM schema_migrations")
        }
    assert versions == {"001_initial.sql", "002_private_foods.sql"}

    previous = tmp_path / "previous.db"
    with sqlite3.connect(previous) as connection:
        connection.executescript(
            (Path(__file__).parents[1] / "migrations" / "001_initial.sql").read_text()
        )
        connection.execute(
            "CREATE TABLE schema_migrations (version TEXT PRIMARY KEY, applied_at TEXT NOT NULL)"
        )
        connection.execute(
            "INSERT INTO schema_migrations VALUES (?, ?)", ("001_initial.sql", iso(now()))
        )
        connection.commit()
    monkeypatch.setattr(main.settings, "database", previous)
    migrate()
    with sqlite3.connect(previous) as connection:
        assert connection.execute(
            "SELECT COUNT(*) FROM private_foods"
        ).fetchone()[0] == 0
        assert connection.execute(
            "SELECT COUNT(*) FROM schema_migrations"
        ).fetchone()[0] == 2
