import hashlib
from datetime import timedelta

from fastapi.testclient import TestClient

from baseline_api.main import app, db, iso, migrate, now, uid


def create_code(value: str) -> None:
    migrate()
    with db() as connection:
        connection.execute(
            "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
            (
                uid(),
                hashlib.sha256(value.encode()).hexdigest(),
                iso(now()),
                iso(now() + timedelta(hours=1)),
            ),
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
        "ingredients": [
            {
                "original_name": "Joghurt",
                "amount": "200",
                "unit": "g",
                "nutrients": [
                    {"key": "energy", "value": "120.50", "unit": "kcal", "basis": "portion"},
                    {"key": "protein", "value": "10.25", "unit": "g", "basis": "portion"},
                ],
            }
        ],
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


def test_quick_manual_entry_and_private_food_isolation(tmp_path, monkeypatch):
    from baseline_api import main

    monkeypatch.setattr(main.settings, "database", tmp_path / "test.db")
    create_code("manual-first-code-123456")
    create_code("manual-second-code-123456")

    with TestClient(app) as client:
        first = register(client, "manual-first", "manual-first-code-123456")
        second = register(client, "manual-second", "manual-second-code-123456")
        first_headers = {"Authorization": f"Bearer {first}"}
        second_headers = {"Authorization": f"Bearer {second}"}
        quick = meal("quick-entry")
        quick["ingredients"] = []
        quick["nutrients"] = [
            {"key": "energy", "value": "450,5", "unit": "kcal", "basis": "portion"}
        ]
        created = client.post("/v1/meals", json=quick, headers=first_headers)
        assert created.status_code == 201, created.text
        assert created.json()["ingredients"] == []
        assert created.json()["nutrients"][0]["value"] == "450.5"

        template = {
            "name": "Haferflocken",
            "brand": "Privat",
            "default_amount": "100",
            "unit": "g",
            "basis": "100g",
            "nutrients": [{"key": "energy", "value": "370,5", "unit": "kcal", "basis": "100g"}],
        }
        food = client.post("/v1/private-foods", json=template, headers=first_headers)
        assert food.status_code == 201, food.text
        food_id = food.json()["id"]
        assert food.json()["nutrients"][0]["locked"] is True
        assert client.get(f"/v1/private-foods/{food_id}", headers=second_headers).status_code == 404
        assert client.get("/v1/private-foods", headers=second_headers).json() == []

        update = dict(template)
        update["version"] = food.json()["version"]
        update["nutrients"] = [{"key": "energy", "value": "380", "unit": "kcal", "basis": "100g"}]
        assert (
            client.put(
                f"/v1/private-foods/{food_id}", json=update, headers=first_headers
            ).status_code
            == 200
        )
        stored_meal = client.get(f"/v1/meals/{created.json()['id']}", headers=first_headers).json()
        assert stored_meal["nutrients"][0]["value"] == "450.5"
