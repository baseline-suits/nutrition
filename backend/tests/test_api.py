import hashlib
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from baseline_api.main import app, db, iso, migrate, now, uid


def create_code(value: str) -> None:
    migrate()
    with db() as connection:
        connection.execute(
            "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
            (uid(), hashlib.sha256(value.encode()).hexdigest(), iso(now()), iso(now() + timedelta(hours=1))),
        )
        connection.commit()


def register(client: TestClient, username: str, code: str) -> str:
    response = client.post(
        "/v1/auth/register",
        json={
            "access_code": code,
            "username": username,
            "password": "a-secure-password",
            "locale": "de",
            "timezone": "Europe/Berlin",
        },
    )
    assert response.status_code == 201, response.text
    return response.json()["token"]


def meal(client_id: str = "phone-1") -> dict:
    return {
        "client_id": client_id,
        "local_day": "2026-07-31",
        "eaten_at": "2026-07-31T10:00:00+02:00",
        "timezone": "Europe/Berlin",
        "meal_type": "breakfast",
        "name": "Joghurt",
        "capture_method": "manual",
        "ingredients": [{
            "original_name": "Joghurt",
            "amount": "200",
            "unit": "g",
            "nutrients": [
                {"key": "energy", "value": "120.50", "unit": "kcal", "basis": "portion"},
                {"key": "protein", "value": "10.25", "unit": "g", "basis": "portion"},
            ],
        }],
    }


def test_auth_isolation_idempotency_and_summary(tmp_path, monkeypatch):
    from baseline_api import main
    monkeypatch.setattr(main.settings, "database", tmp_path / "test.db")
    create_code("first-code-value-123456")
    create_code("second-code-value-123456")

    with TestClient(app) as client:
        first = register(client, "first", "first-code-value-123456")
        second = register(client, "second", "second-code-value-123456")
        headers = {"Authorization": f"Bearer {first}", "Idempotency-Key": "stable-create"}
        created = client.post("/v1/meals", json=meal(), headers=headers)
        repeated = client.post("/v1/meals", json=meal(), headers=headers)
        assert created.status_code == 201
        assert repeated.json()["id"] == created.json()["id"]

        hidden = client.get(
            f"/v1/meals/{created.json()['id']}",
            headers={"Authorization": f"Bearer {second}"},
        )
        assert hidden.status_code == 404

        summary = client.get(
            "/v1/days/2026-07-31/summary",
            headers={"Authorization": f"Bearer {first}"},
        )
        assert summary.json()["totals"] == {"energy": "120.50", "protein": "10.25"}
        assert "fat" in summary.json()["missing_core"]

        stale = meal()
        stale["version"] = 99
        conflict = client.put(
            f"/v1/meals/{created.json()['id']}",
            json=stale,
            headers={"Authorization": f"Bearer {first}"},
        )
        assert conflict.status_code == 409


def test_access_code_is_one_time(tmp_path, monkeypatch):
    from baseline_api import main
    monkeypatch.setattr(main.settings, "database", tmp_path / "test.db")
    create_code("one-time-code-value-123456")
    with TestClient(app) as client:
        register(client, "first", "one-time-code-value-123456")
        response = client.post(
            "/v1/auth/register",
            json={
                "access_code": "one-time-code-value-123456",
                "username": "second",
                "password": "a-secure-password",
            },
        )
        assert response.status_code == 400

