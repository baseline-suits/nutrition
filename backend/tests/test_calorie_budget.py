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


def register(client: TestClient, username: str) -> tuple[str, str]:
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
    body = response.json()
    return body["token"], body["user_id"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def save_profile(
    client: TestClient,
    token: str,
    *,
    target: str = "2000",
    mode: str = "fixed",
    timezone: str = "Europe/Berlin",
) -> None:
    response = client.put(
        "/v1/profile",
        headers=auth(token),
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
            "calorie_budget_mode": mode,
        },
    )
    assert response.status_code == 200, response.text


def active_calorie_record(
    external_id: str,
    value: str,
    *,
    origin: str = "com.example.health",
    modified: str = "2026-07-15T10:00:00+02:00",
    start: str = "2026-07-15T08:00:00+02:00",
    end: str = "2026-07-15T09:00:00+02:00",
) -> dict:
    return {
        "operation": "upsert",
        "data_type": "active_calories",
        "external_record_id": external_id,
        "origin_package": origin,
        "origin_app_name": origin.rsplit(".", 1)[-1].title(),
        "last_modified_time": modified,
        "start_time": start,
        "end_time": end,
        "zone_id": "Europe/Berlin",
        "start_offset_seconds": 7200,
        "end_offset_seconds": 7200,
        "value": value,
        "unit": "kcal",
    }


def sync_active_calories(
    client: TestClient,
    token: str,
    request_id: str,
    records: list[dict],
    *,
    complete: bool = True,
) -> None:
    response = client.post(
        "/v1/health/batches",
        headers=auth(token),
        json={
            "schema_version": "health-sync/1.0",
            "request_id": request_id,
            "installation_id": "budget-test-installation",
            "sections": [
                {
                    "data_type": "active_calories",
                    "window_start": "2026-07-15T00:00:00+02:00",
                    "window_end": "2026-07-16T00:00:00+02:00",
                    "cursor": request_id,
                    "complete": complete,
                    "records": records,
                }
            ],
        },
    )
    assert response.status_code == 200, response.text


def current_budget(client: TestClient, token: str, day: str = "2026-07-15") -> dict:
    response = client.get(f"/v1/days/{day}/summary", headers=auth(token))
    assert response.status_code == 200, response.text
    budget = response.json()["targets"]
    assert budget is not None
    return budget


def test_fixed_and_dynamic_budget_rounding_cap_and_correction(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "calorie-budget.db")
    monkeypatch.setattr(main, "now", lambda: datetime(2026, 7, 15, 12, tzinfo=UTC))
    with TestClient(app) as client:
        token, _ = register(client, "budget-math")
        save_profile(client, token)
        sync_active_calories(
            client,
            token,
            "budget-activity-1",
            [active_calorie_record("active-1", "301")],
        )

        fixed = current_budget(client, token)
        assert fixed["budget_mode"] == "fixed"
        assert fixed["activity_status"] == "ready"
        assert fixed["activity_kcal"] == "301"
        assert fixed["activity_contribution_kcal"] == "0"
        assert fixed["target_kcal"] == "2000"

        dynamic_response = client.put(
            "/v1/calorie-budget/mode",
            headers=auth(token),
            json={"mode": "dynamic"},
        )
        assert dynamic_response.status_code == 200, dynamic_response.text
        dynamic = dynamic_response.json()
        assert dynamic["base_target_kcal"] == "2000"
        assert dynamic["activity_factor"] == "0.5"
        assert dynamic["activity_cap_kcal"] == "500"
        assert dynamic["activity_contribution_kcal"] == "150.5"
        assert dynamic["target_kcal"] == "2151"

        corrected = active_calorie_record(
            "active-1",
            "1400",
            modified="2026-07-15T11:00:00+02:00",
        )
        sync_active_calories(client, token, "budget-activity-2", [corrected])
        capped = current_budget(client, token)
        assert capped["activity_contribution_kcal"] == "500"
        assert capped["target_kcal"] == "2500"

        sync_active_calories(client, token, "budget-activity-3", [corrected])
        assert current_budget(client, token)["target_kcal"] == "2500"


def test_missing_conflicting_and_preferred_activity_are_transparent(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "calorie-context.db")
    monkeypatch.setattr(main, "now", lambda: datetime(2026, 7, 15, 12, tzinfo=UTC))
    with TestClient(app) as client:
        token, _ = register(client, "budget-context")
        save_profile(client, token, mode="dynamic")

        not_synced = current_budget(client, token)
        assert not_synced["activity_status"] == "not_synced"
        assert not_synced["target_kcal"] == "2000"

        sync_active_calories(client, token, "budget-empty", [])
        missing = current_budget(client, token)
        assert missing["activity_status"] == "missing"
        assert missing["activity_kcal"] is None
        assert missing["target_kcal"] == "2000"

        google = active_calorie_record(
            "google-active",
            "450.5",
            origin="com.google.android.apps.fitness",
        )
        samsung = active_calorie_record(
            "samsung-active",
            "380",
            origin="com.sec.android.app.shealth",
        )
        sync_active_calories(client, token, "budget-conflict", [google, samsung])
        conflicted = current_budget(client, token)
        assert conflicted["activity_status"] == "conflict"
        assert conflicted["activity_kcal"] is None
        assert conflicted["target_kcal"] == "2000"

        preference = client.put(
            "/v1/health/source-preferences/active_calories",
            headers=auth(token),
            json={"origin_package": "com.google.android.apps.fitness"},
        )
        assert preference.status_code == 200, preference.text
        preferred = current_budget(client, token)
        assert preferred["activity_status"] == "ready"
        assert preferred["activity_kcal"] == "450.5"
        assert preferred["activity_contribution_kcal"] == "225.25"
        assert preferred["target_kcal"] == "2225"

        removed = client.delete(
            "/v1/health/source-preferences/active_calories",
            headers=auth(token),
        )
        assert removed.status_code == 204
        assert current_budget(client, token)["activity_status"] == "conflict"


def test_partial_activity_is_visible_but_not_applied(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "calorie-partial.db")
    monkeypatch.setattr(main, "now", lambda: datetime(2026, 7, 15, 12, tzinfo=UTC))
    with TestClient(app) as client:
        token, _ = register(client, "budget-partial")
        save_profile(client, token, mode="dynamic")
        first = active_calorie_record("partial-active-1", "100")
        sync_active_calories(client, token, "budget-complete-before-partial", [first])
        assert current_budget(client, token)["target_kcal"] == "2050"

        second = active_calorie_record(
            "partial-active-2",
            "260",
            modified="2026-07-15T11:00:00+02:00",
            start="2026-07-15T09:00:00+02:00",
            end="2026-07-15T10:00:00+02:00",
        )
        sync_active_calories(
            client,
            token,
            "budget-partial-sync",
            [first, second],
            complete=False,
        )

        partial = current_budget(client, token)
        assert partial["activity_status"] == "partial"
        assert partial["activity_kcal"] == "360"
        assert partial["activity_contribution_kcal"] == "0"
        assert partial["target_kcal"] == "2000"

        sync_active_calories(
            client,
            token,
            "budget-complete-after-partial",
            [first, second],
        )
        completed = current_budget(client, token)
        assert completed["activity_status"] == "ready"
        assert completed["activity_contribution_kcal"] == "180"
        assert completed["target_kcal"] == "2180"


def test_historical_budget_retains_mode_base_and_timezone(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "calorie-history.db")
    clock = {"value": datetime(2026, 7, 15, 12, tzinfo=UTC)}
    monkeypatch.setattr(main, "now", lambda: clock["value"])
    with TestClient(app) as client:
        token, user_id = register(client, "budget-history")
        save_profile(client, token, mode="dynamic")
        sync_active_calories(
            client,
            token,
            "history-activity-1",
            [active_calorie_record("history-active", "400")],
        )
        assert current_budget(client, token)["target_kcal"] == "2200"

        clock["value"] = datetime(2026, 7, 16, 12, tzinfo=UTC)
        save_profile(client, token, target="2400", mode="fixed", timezone="UTC")
        correction = active_calorie_record(
            "history-active",
            "600",
            modified="2026-07-16T10:00:00+02:00",
        )
        sync_active_calories(client, token, "history-activity-2", [correction])

        with db() as connection:
            historical = connection.execute(
                "SELECT * FROM daily_budget_snapshots WHERE user_id=? AND local_day=?",
                (user_id, "2026-07-15"),
            ).fetchone()
            current = connection.execute(
                "SELECT * FROM daily_budget_snapshots WHERE user_id=? AND local_day=?",
                (user_id, "2026-07-16"),
            ).fetchone()
        assert historical is not None
        assert historical["timezone"] == "Europe/Berlin"
        assert historical["budget_mode"] == "dynamic"
        assert historical["base_target_kcal"] == "2000"
        assert historical["activity_kcal"] == "600"
        assert historical["target_kcal"] == "2300"
        assert current is not None
        assert current["timezone"] == "UTC"
        assert current["budget_mode"] == "fixed"
        assert current["base_target_kcal"] == "2400"
        assert current["target_kcal"] == "2400"


def test_budget_requires_profile_is_user_bound_and_is_deleted(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "calorie-isolation.db")
    monkeypatch.setattr(main, "now", lambda: datetime(2026, 7, 15, 12, tzinfo=UTC))
    with TestClient(app) as client:
        first_token, first_user_id = register(client, "budget-first")
        second_token, _ = register(client, "budget-second")
        save_profile(client, first_token, mode="dynamic")

        missing_profile = client.put(
            "/v1/calorie-budget/mode",
            headers=auth(second_token),
            json={"mode": "dynamic"},
        )
        assert missing_profile.status_code == 404
        second_summary = client.get(
            "/v1/days/2026-07-15/summary",
            headers=auth(second_token),
        )
        assert second_summary.status_code == 200
        assert second_summary.json()["targets"] is None

        deleted = client.request(
            "DELETE",
            "/v1/account",
            headers=auth(first_token),
            json={"password": "a-secure-password", "confirmation": "DELETE"},
        )
        assert deleted.status_code == 202, deleted.text
        with db() as connection:
            snapshots = connection.execute(
                "SELECT COUNT(*) FROM daily_budget_snapshots WHERE user_id=?",
                (first_user_id,),
            ).fetchone()[0]
        assert snapshots == 0
