import hashlib
from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from baseline_api import main
from baseline_api.main import app, db, iso, migrate, uid


def create_code(value: str) -> None:
    migrate()
    with db() as connection:
        connection.execute(
            "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
            (
                uid(),
                hashlib.sha256(value.encode()).hexdigest(),
                iso(main.now()),
                iso(main.now() + timedelta(hours=1)),
            ),
        )
        connection.commit()


def register(client: TestClient, username: str) -> str:
    code = f"{username}-access-code-123456"
    create_code(code)
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


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def profile_payload(target: str = "2000", expected: str | None = None) -> dict:
    payload = {
        "locale": "de",
        "timezone": "Europe/Berlin",
        "weight_kg": "80",
        "activity_level": "active",
        "target_kcal": target,
        "target_protein_g": "120",
        "target_carbs_g": "220",
        "target_fat_g": "70",
        "manual": True,
        "calorie_budget_mode": "fixed",
    }
    if expected is not None:
        payload["expected_updated_at"] = expected
    return payload


def test_locale_setting_is_confirmed_user_bound_and_validated(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "settings-locale.db")
    monkeypatch.setattr(main, "now", lambda: datetime(2026, 8, 1, 12, tzinfo=UTC))
    with TestClient(app) as client:
        first = register(client, "settings-first")
        second = register(client, "settings-second")

        changed = client.put(
            "/v1/settings/locale",
            headers=auth(first),
            json={"locale": "ru"},
        )
        assert changed.status_code == 200, changed.text
        assert changed.json()["locale"] == "ru"
        assert client.get("/v1/auth/session", headers=auth(first)).json()["locale"] == "ru"
        assert client.get("/v1/auth/session", headers=auth(second)).json()["locale"] == "de"

        invalid = client.put(
            "/v1/settings/locale",
            headers=auth(first),
            json={"locale": "en"},
        )
        assert invalid.status_code == 422
        assert client.put("/v1/settings/locale", json={"locale": "de"}).status_code == 401


def test_profile_expected_update_detects_cross_device_conflict(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "settings-profile.db")
    clock = {"value": datetime(2026, 8, 1, 12, tzinfo=UTC)}
    monkeypatch.setattr(main, "now", lambda: clock["value"])
    with TestClient(app) as client:
        token = register(client, "settings-profile")
        first = client.put("/v1/profile", headers=auth(token), json=profile_payload())
        assert first.status_code == 200, first.text
        initial = client.get("/v1/profile", headers=auth(token)).json()
        initial_updated_at = initial["updated_at"]

        clock["value"] = datetime(2026, 8, 1, 12, 1, tzinfo=UTC)
        updated = client.put(
            "/v1/profile",
            headers=auth(token),
            json=profile_payload("2100", initial_updated_at),
        )
        assert updated.status_code == 200, updated.text

        clock["value"] = datetime(2026, 8, 1, 12, 2, tzinfo=UTC)
        stale = client.put(
            "/v1/profile",
            headers=auth(token),
            json=profile_payload("2200", initial_updated_at),
        )
        assert stale.status_code == 409
        assert stale.json()["detail"]["code"] == "profile_conflict"
        confirmed = client.get("/v1/profile", headers=auth(token)).json()
        assert confirmed["target_kcal"] == "2100"

        naive = profile_payload("2300", "2026-08-01T12:01:00")
        assert client.put("/v1/profile", headers=auth(token), json=naive).status_code == 422
