import hashlib
from datetime import date, datetime, time, timedelta

from fastapi.testclient import TestClient

from baseline_api import main
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


def register(client: TestClient, username: str, code: str) -> tuple[str, str]:
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
    body = response.json()
    return body["token"], body["user_id"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def save_profile(
    client: TestClient,
    headers: dict[str, str],
    target: str,
    timezone: str = "Europe/Berlin",
) -> None:
    response = client.put(
        "/v1/profile",
        headers=headers,
        json={
            "locale": "de",
            "timezone": timezone,
            "weight_kg": "80",
            "activity_level": "active",
            "target_kcal": target,
            "target_protein_g": "120",
            "target_carbs_g": "220",
            "target_fat_g": "70",
            "manual": True,
        },
    )
    assert response.status_code == 200, response.text


def meal_payload(
    day: date,
    client_id: str,
    energy: str,
    *,
    timezone: str = "Europe/Berlin",
    include_fat: bool = True,
) -> dict:
    offset = "+00:00" if timezone == "UTC" else "+02:00"
    nutrients = [
        {"key": "energy", "value": energy, "unit": "kcal", "basis": "portion"},
        {"key": "protein", "value": "30", "unit": "g", "basis": "portion"},
        {"key": "carbohydrates", "value": "60", "unit": "g", "basis": "portion"},
    ]
    if include_fat:
        nutrients.append({"key": "fat", "value": "20", "unit": "g", "basis": "portion"})
    return {
        "client_id": client_id,
        "local_day": str(day),
        "eaten_at": f"{day}T12:00:00{offset}",
        "timezone": timezone,
        "meal_type": "lunch",
        "name": "Testmahlzeit",
        "capture_method": "manual",
        "nutrients": nutrients,
    }


def test_history_uses_recorded_days_and_historical_targets(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "history.db")
    create_code("history-code-value-123456")
    with TestClient(app) as client:
        token, _ = register(client, "history-user", "history-code-value-123456")
        headers = auth(token)
        save_profile(client, headers, "2000")
        first = client.post(
            "/v1/meals",
            headers=headers,
            json=meal_payload(date(2026, 7, 1), "history-1", "400"),
        )
        assert first.status_code == 201, first.text

        save_profile(client, headers, "2400", timezone="UTC")
        second_payload = meal_payload(
            date(2026, 7, 3),
            "history-2",
            "600",
            timezone="UTC",
        )
        second = client.post("/v1/meals", headers=headers, json=second_payload)
        assert second.status_code == 201, second.text

        response = client.get(
            "/v1/nutrition/history?days=7&end=2026-07-07",
            headers=headers,
        )
        assert response.status_code == 200, response.text
        body = response.json()
        by_day = {entry["local_day"]: entry for entry in body["days"]}
        assert body["summary"]["tracked_days"] == 2
        assert body["summary"]["averages"]["energy"] == "500.00"
        assert body["summary"]["average_denominators"]["energy"] == 2
        assert by_day["2026-07-02"]["status"] == "none"
        assert "energy" not in by_day["2026-07-02"]["totals"]
        assert by_day["2026-07-01"]["targets"]["target_kcal"] == "2000"
        assert by_day["2026-07-01"]["targets"]["timezone"] == "Europe/Berlin"
        assert by_day["2026-07-03"]["targets"]["target_kcal"] == "2400"
        assert by_day["2026-07-03"]["targets"]["timezone"] == "UTC"

        day_summary = client.get("/v1/days/2026-07-01/summary", headers=headers).json()
        assert day_summary["targets"]["target_kcal"] == "2000"

        changed = dict(second_payload)
        changed["version"] = second.json()["version"]
        changed["nutrients"] = [
            dict(nutrient, value="800") if nutrient["key"] == "energy" else nutrient
            for nutrient in second_payload["nutrients"]
        ]
        assert (
            client.put(
                f"/v1/meals/{second.json()['id']}",
                headers=headers,
                json=changed,
            ).status_code
            == 200
        )
        updated = client.get(
            "/v1/nutrition/history?days=7&end=2026-07-07",
            headers=headers,
        ).json()
        assert updated["summary"]["averages"]["energy"] == "600.00"

        assert client.delete(f"/v1/meals/{first.json()['id']}", headers=headers).status_code == 204
        after_delete = client.get(
            "/v1/nutrition/history?days=7&end=2026-07-07",
            headers=headers,
        ).json()
        assert after_delete["summary"]["tracked_days"] == 1
        assert after_delete["summary"]["averages"]["energy"] == "800.00"


def test_history_exposes_partial_coverage_and_real_denominators(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "coverage.db")
    create_code("coverage-code-value-123456")
    with TestClient(app) as client:
        token, _ = register(client, "coverage-user", "coverage-code-value-123456")
        headers = auth(token)
        save_profile(client, headers, "2000")
        for index in range(4):
            response = client.post(
                "/v1/meals",
                headers=headers,
                json=meal_payload(
                    date(2026, 7, 1) + timedelta(days=index),
                    f"coverage-{index}",
                    str(400 + index * 100),
                    include_fat=index != 3,
                ),
            )
            assert response.status_code == 201, response.text

        body = client.get(
            "/v1/nutrition/history?days=7&end=2026-07-07",
            headers=headers,
        ).json()
        summary = body["summary"]
        assert summary["tracked_days"] == 4
        assert summary["complete_days"] == 3
        assert summary["partial_days"] == 1
        assert summary["average_denominators"]["energy"] == 4
        assert summary["average_denominators"]["fat"] == 3
        assert summary["goal_denominators"]["fat"] == 3
        assert body["weeks"][0]["tracked_days"] == 4


def test_year_range_is_paginated_and_user_bound(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "year.db")
    create_code("year-first-code-123456")
    create_code("year-second-code-123456")
    with TestClient(app) as client:
        first_token, first_user = register(client, "year-first", "year-first-code-123456")
        second_token, _ = register(client, "year-second", "year-second-code-123456")
        first_headers = auth(first_token)
        save_profile(client, first_headers, "2000")
        with db() as connection:
            start = date(2025, 7, 9)
            stamp = iso(now())
            for index in range(365):
                local_day = start + timedelta(days=index)
                meal_id = uid()
                eaten_at = datetime.combine(local_day, time(12), tzinfo=main.UTC)
                connection.execute(
                    """INSERT INTO meals
                       (id,user_id,client_id,local_day,eaten_at,timezone,meal_type,name,note,
                        capture_method,version,deleted_at,created_at,updated_at)
                       VALUES (?,?,?,?,?,?,?,?,?,?,1,NULL,?,?)""",
                    (
                        meal_id,
                        first_user,
                        f"year-{index}",
                        str(local_day),
                        iso(eaten_at),
                        "UTC",
                        "lunch",
                        "Archiv",
                        None,
                        "manual",
                        stamp,
                        stamp,
                    ),
                )
                connection.execute(
                    "INSERT INTO nutrient_values VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (
                        uid(),
                        meal_id,
                        None,
                        "energy",
                        "500",
                        "kcal",
                        "portion",
                        "user",
                        1,
                        "exact",
                    ),
                )
            connection.commit()

        first_page = client.get(
            "/v1/nutrition/history?days=365&end=2026-07-08&limit=100",
            headers=first_headers,
        ).json()
        assert first_page["total_days"] == 365
        assert len(first_page["days"]) == 100
        assert first_page["has_more"] is True
        assert first_page["summary"]["tracked_days"] == 365

        last_page = client.get(
            "/v1/nutrition/history?days=365&end=2026-07-08&limit=100&offset=300",
            headers=first_headers,
        ).json()
        assert len(last_page["days"]) == 65
        assert last_page["has_more"] is False

        isolated = client.get(
            "/v1/nutrition/history?days=365&end=2026-07-08&limit=100",
            headers=auth(second_token),
        ).json()
        assert isolated["summary"]["tracked_days"] == 0
