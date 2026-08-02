import hashlib
from datetime import UTC, datetime, timedelta

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
    assert response.status_code == 201
    body = response.json()
    return body["token"], body["user_id"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def health_record(
    external_id: str,
    *,
    data_type: str = "steps",
    origin: str = "com.example.health",
    start: str = "2026-07-15T08:00:00+02:00",
    end: str = "2026-07-15T09:00:00+02:00",
    value: str = "1000",
    unit: str = "count",
    modified: str = "2026-07-15T10:00:00+02:00",
    operation: str = "upsert",
    **extra,
) -> dict:
    record = {
        "operation": operation,
        "data_type": data_type,
        "external_record_id": external_id,
        "origin_package": origin,
        "origin_app_name": origin.rsplit(".", 1)[-1].title(),
        "last_modified_time": modified,
    }
    if operation == "upsert":
        record |= {
            "start_time": start,
            "end_time": end,
            "zone_id": "Europe/Berlin",
            "start_offset_seconds": 7200,
            "end_offset_seconds": 7200,
            "value": value,
            "unit": unit,
        }
    record.update(extra)
    return record


def health_section(
    records: list[dict],
    *,
    data_type: str = "steps",
    cursor: str = "cursor-july",
    start: str = "2026-07-01T00:00:00+02:00",
    end: str = "2026-07-31T00:00:00+02:00",
    complete: bool = True,
) -> dict:
    return {
        "data_type": data_type,
        "window_start": start,
        "window_end": end,
        "cursor": cursor,
        "complete": complete,
        "records": records,
    }


def health_batch(
    request_id: str,
    sections: list[dict],
    installation: str = "installation-a",
) -> dict:
    return {
        "schema_version": "health-sync/1.0",
        "request_id": request_id,
        "installation_id": installation,
        "sections": sections,
    }


def post_batch(
    client: TestClient,
    token: str,
    request_id: str,
    sections: list[dict],
    installation: str = "installation-a",
):
    return client.post(
        "/v1/health/batches",
        json=health_batch(request_id, sections, installation),
        headers=auth(token),
    )


def test_batch_idempotency_correction_and_stale_version(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "idempotency.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-idempotency")
        initial_record = health_record("steps-1")
        initial = health_batch(
            "health-request-1",
            [health_section([initial_record], cursor="cursor-1")],
        )

        created = client.post("/v1/health/batches", json=initial, headers=auth(token))
        assert created.status_code == 200, created.text
        assert created.json()["sections"][0]["results"][0]["status"] == "created"
        assert client.post("/v1/health/batches", json=initial, headers=auth(token)).json() == (
            created.json()
        )

        changed_request = initial | {"installation_id": "installation-b"}
        conflict = client.post("/v1/health/batches", json=changed_request, headers=auth(token))
        assert conflict.status_code == 409
        assert conflict.json()["detail"]["code"] == "idempotency_conflict"

        correction = health_record(
            "steps-1",
            value="1200",
            modified="2026-07-15T11:00:00+02:00",
        )
        corrected = post_batch(
            client,
            token,
            "health-request-2",
            [health_section([correction], cursor="cursor-2")],
        )
        assert corrected.status_code == 200
        assert corrected.json()["sections"][0]["results"][0]["status"] == "updated"

        stale = health_record(
            "steps-1",
            value="900",
            modified="2026-07-15T10:30:00+02:00",
        )
        stale_response = post_batch(
            client,
            token,
            "health-request-3",
            [health_section([stale], cursor="cursor-3")],
        )
        stale_result = stale_response.json()["sections"][0]["results"][0]
        assert stale_result == {
            "data_type": "steps",
            "external_record_id": "steps-1",
            "origin_package": "com.example.health",
            "status": "conflict",
            "code": "stale_record",
        }
        records = client.get(
            "/v1/health/records?data_type=steps&start=2026-07-15&end=2026-07-15",
            headers=auth(token),
        ).json()
        assert [(item["external_record_id"], item["value"]) for item in records] == [
            ("steps-1", "1200")
        ]
        aggregate = client.get(
            "/v1/health/aggregates?data_type=steps&start=2026-07-15&end=2026-07-15",
            headers=auth(token),
        ).json()[0]
        assert aggregate["status"] == "ready"
        assert aggregate["value"] == "1200"


def test_two_installations_reconciliation_and_explicit_tombstone(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "reconciliation.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-reconciliation")
        record = health_record("shared-record")
        for installation, request_id in (
            ("installation-a", "shared-request-a"),
            ("installation-b", "shared-request-b"),
        ):
            response = post_batch(
                client,
                token,
                request_id,
                [health_section([record], cursor=f"cursor-{installation}")],
                installation,
            )
            assert response.status_code == 200

        tombstone_while_shared = health_record(
            "shared-record",
            operation="delete",
            modified="2026-07-15T12:00:00+02:00",
        )
        shared_delete = post_batch(
            client,
            token,
            "shared-delete-a",
            [health_section([tombstone_while_shared], cursor="shared-delete")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert shared_delete["status"] == "deleted"
        stale_shared_restore = health_record(
            "shared-record",
            value="1100",
            modified="2026-07-15T11:00:00+02:00",
        )
        stale_shared = post_batch(
            client,
            token,
            "shared-stale-a",
            [health_section([stale_shared_restore], cursor="shared-stale")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert stale_shared["code"] == "newer_tombstone"
        newer_shared_restore = health_record(
            "shared-record",
            value="1200",
            modified="2026-07-15T12:30:00+02:00",
        )
        restored_shared = post_batch(
            client,
            token,
            "shared-newer-a",
            [health_section([newer_shared_restore], cursor="shared-newer")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert restored_shared["status"] == "updated"

        first_removed = post_batch(
            client,
            token,
            "empty-request-a",
            [health_section([], cursor="empty-a")],
            "installation-a",
        ).json()["sections"][0]
        assert first_removed["reconciled_deletions"] == 0
        assert (
            len(
                client.get(
                    "/v1/health/records?data_type=steps&start=2026-07-15&end=2026-07-15",
                    headers=auth(token),
                ).json()
            )
            == 1
        )

        second_removed = post_batch(
            client,
            token,
            "empty-request-b",
            [health_section([], cursor="empty-b")],
            "installation-b",
        ).json()["sections"][0]
        assert second_removed["reconciled_deletions"] == 1
        assert (
            client.get(
                "/v1/health/records?data_type=steps&start=2026-07-15&end=2026-07-15",
                headers=auth(token),
            ).json()
            == []
        )

        reconciliation_restore = health_record(
            "shared-record",
            value="1250",
            modified="2026-07-15T12:40:00+02:00",
        )
        restored = post_batch(
            client,
            token,
            "restore-request",
            [health_section([reconciliation_restore], cursor="restore")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert restored["status"] == "updated"

        tombstone = health_record(
            "shared-record",
            operation="delete",
            modified="2026-07-15T13:00:00+02:00",
        )
        deleted = post_batch(
            client,
            token,
            "delete-request",
            [health_section([tombstone], cursor="deleted")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert deleted["status"] == "deleted"

        stale_restore = health_record(
            "shared-record",
            value="1300",
            modified="2026-07-15T12:45:00+02:00",
        )
        rejected_restore = post_batch(
            client,
            token,
            "stale-restore-request",
            [health_section([stale_restore], cursor="stale-restore")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert rejected_restore["status"] == "conflict"
        assert rejected_restore["code"] == "newer_tombstone"

        newer_restore = health_record(
            "shared-record",
            value="1400",
            modified="2026-07-15T13:30:00+02:00",
        )
        accepted_restore = post_batch(
            client,
            token,
            "newer-restore-request",
            [health_section([newer_restore], cursor="newer-restore")],
            "installation-a",
        ).json()["sections"][0]["results"][0]
        assert accepted_restore["status"] == "updated"


def test_partial_failure_preserves_cursor_and_skips_reconciliation(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "partial.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-partial")
        original = health_record("original")
        assert (
            post_batch(
                client,
                token,
                "partial-initial",
                [health_section([original], cursor="cursor-before")],
            ).status_code
            == 200
        )

        accepted = health_record(
            "accepted",
            start="2026-07-16T08:00:00+02:00",
            end="2026-07-16T09:00:00+02:00",
            modified="2026-07-16T10:00:00+02:00",
        )
        invalid = health_record(
            "invalid",
            start="2026-07-16T10:00:00+02:00",
            end="2026-07-16T09:00:00+02:00",
            modified="2026-07-16T11:00:00+02:00",
        )
        partial = post_batch(
            client,
            token,
            "partial-failure",
            [health_section([accepted, invalid], cursor="cursor-must-not-advance")],
        )
        section = partial.json()["sections"][0]
        assert section["cursor_committed"] is False
        assert section["reconciled_deletions"] == 0
        assert [result["status"] for result in section["results"]] == [
            "created",
            "rejected",
        ]
        state = client.get("/v1/health/state", headers=auth(token)).json()
        assert state["cursors"][0]["cursor"] == "cursor-before"

        records = client.get(
            "/v1/health/records?data_type=steps&start=2026-07-01&end=2026-07-31",
            headers=auth(token),
        ).json()
        assert {record["external_record_id"] for record in records} == {"original", "accepted"}

        retried = post_batch(
            client,
            token,
            "partial-clean-retry",
            [health_section([accepted], cursor="cursor-after")],
        ).json()["sections"][0]
        assert retried["cursor_committed"] is True
        assert retried["reconciled_deletions"] == 1
        state = client.get("/v1/health/state", headers=auth(token)).json()
        assert state["cursors"][0]["cursor"] == "cursor-after"


def test_source_conflict_preference_and_reversible_aggregation(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "sources.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-sources")
        google = health_record(
            "google-calories",
            data_type="active_calories",
            origin="com.google.android.apps.fitness",
            value="450.5",
            unit="kcal",
        )
        samsung = health_record(
            "samsung-calories",
            data_type="active_calories",
            origin="com.sec.android.app.shealth",
            start="2026-07-15T08:30:00+02:00",
            end="2026-07-15T09:30:00+02:00",
            value="380",
            unit="kcal",
        )
        response = post_batch(
            client,
            token,
            "source-conflict",
            [
                health_section(
                    [google, samsung],
                    data_type="active_calories",
                    cursor="source-conflict",
                )
            ],
        )
        assert response.status_code == 200

        def aggregate() -> dict:
            result = client.get(
                "/v1/health/aggregates?data_type=active_calories&start=2026-07-15&end=2026-07-15",
                headers=auth(token),
            )
            assert result.status_code == 200
            return result.json()[0]

        conflicted = aggregate()
        assert conflicted["status"] == "conflict"
        assert conflicted["value"] is None
        assert all(source["selected"] is False for source in conflicted["sources"])

        preferred = client.put(
            "/v1/health/source-preferences/active_calories",
            json={"origin_package": "com.google.android.apps.fitness"},
            headers=auth(token),
        )
        assert preferred.status_code == 200
        selected = aggregate()
        assert selected["status"] == "ready"
        assert selected["value"] == "450.5"
        assert selected["selected_origin_package"] == "com.google.android.apps.fitness"

        assert (
            client.delete(
                "/v1/health/source-preferences/active_calories",
                headers=auth(token),
            ).status_code
            == 204
        )
        again_conflicted = aggregate()
        assert again_conflicted["status"] == "conflict"
        assert again_conflicted["value"] is None


def test_sleep_dst_weight_series_and_multiple_sections(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "timezones.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-timezones")
        sleep = health_record(
            "sleep-dst",
            data_type="sleep",
            start="2026-03-28T22:00:00+01:00",
            end="2026-03-29T08:00:00+02:00",
            value="32400",
            unit="s",
            modified="2026-03-29T08:30:00+02:00",
            start_offset_seconds=3600,
            end_offset_seconds=7200,
            segments=[
                {
                    "start_time": "2026-03-28T22:00:00+01:00",
                    "end_time": "2026-03-29T08:00:00+02:00",
                    "segment_type": 4,
                }
            ],
        )
        weight = health_record(
            "weight-1",
            data_type="weight",
            start="2026-03-29T09:00:00+02:00",
            end="2026-03-29T09:00:00+02:00",
            value="72.4",
            unit="kg",
            modified="2026-03-29T09:01:00+02:00",
        )
        sections = [
            health_section(
                [sleep],
                data_type="sleep",
                cursor="sleep-march",
                start="2026-03-01T00:00:00+01:00",
                end="2026-03-31T00:00:00+02:00",
            ),
            health_section(
                [weight],
                data_type="weight",
                cursor="weight-march",
                start="2026-03-01T00:00:00+01:00",
                end="2026-03-31T00:00:00+02:00",
            ),
        ]
        synced = post_batch(client, token, "timezones-request", sections)
        assert synced.status_code == 200, synced.text
        assert all(section["cursor_committed"] for section in synced.json()["sections"])

        aggregates = client.get(
            "/v1/health/aggregates?start=2026-03-29&end=2026-03-29",
            headers=auth(token),
        ).json()
        assert [(item["data_type"], item["status"], item["value"]) for item in aggregates] == [
            ("sleep", "ready", "32400"),
            ("weight", "series", None),
        ]
        sleep_records = client.get(
            "/v1/health/records?data_type=sleep&start=2026-03-29&end=2026-03-29",
            headers=auth(token),
        ).json()
        assert sleep_records[0]["local_day"] == "2026-03-29"
        assert sleep_records[0]["segments"][0]["segment_type"] == 4


def test_user_isolation_and_account_deletion_cascade(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "isolation.db")
    with TestClient(app) as client:
        first_token, first_user_id = register(client, "health-first")
        second_token, _ = register(client, "health-second")
        response = post_batch(
            client,
            first_token,
            "isolation-request",
            [health_section([health_record("private-record")])],
        )
        assert response.status_code == 200
        second_records = client.get(
            "/v1/health/records?data_type=steps&start=2026-07-01&end=2026-07-31",
            headers=auth(second_token),
        )
        assert second_records.status_code == 200
        assert second_records.json() == []
        unknown_source = client.put(
            "/v1/health/source-preferences/steps",
            json={"origin_package": "com.example.health"},
            headers=auth(second_token),
        )
        assert unknown_source.status_code == 404

        deleted = client.request(
            "DELETE",
            "/v1/account",
            json={"password": "a-secure-password", "confirmation": "DELETE"},
            headers=auth(first_token),
        )
        assert deleted.status_code == 202
        assert deleted.json()["status"] == "completed"
        with db() as connection:
            assert (
                connection.execute(
                    "SELECT COUNT(*) FROM health_records WHERE user_id=?", (first_user_id,)
                ).fetchone()[0]
                == 0
            )
            assert (
                connection.execute(
                    "SELECT COUNT(*) FROM health_sync_batches WHERE user_id=?", (first_user_id,)
                ).fetchone()[0]
                == 0
            )


def test_batch_limits_large_batch_and_monthly_windows(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "limits.db")
    with TestClient(app) as client:
        token, _ = register(client, "health-limits")
        base = datetime(2026, 7, 10, 8, tzinfo=UTC)
        weights = [
            health_record(
                f"weight-{index}",
                data_type="weight",
                start=(base + timedelta(minutes=index)).isoformat(),
                end=(base + timedelta(minutes=index)).isoformat(),
                value=str(70 + index / 1000),
                unit="kg",
                modified=(base + timedelta(minutes=index, seconds=1)).isoformat(),
                start_offset_seconds=0,
                end_offset_seconds=0,
            )
            for index in range(500)
        ]
        large = post_batch(
            client,
            token,
            "large-batch-request",
            [health_section(weights, data_type="weight", cursor="large-batch")],
        )
        assert large.status_code == 200, large.text
        assert len(large.json()["sections"][0]["results"]) == 500
        assert (
            len(
                client.get(
                    "/v1/health/records?data_type=weight&start=2026-07-10&end=2026-07-10&limit=500",
                    headers=auth(token),
                ).json()
            )
            == 500
        )

        too_large = post_batch(
            client,
            token,
            "too-large-request",
            [
                health_section(weights[:251], data_type="weight", cursor="part-a"),
                health_section(weights[250:], data_type="weight", cursor="part-b"),
            ],
        )
        assert too_large.status_code == 422
        assert too_large.json()["detail"]["code"] == "batch_too_large"

        june = health_section(
            [],
            cursor="june-complete",
            start="2026-06-01T00:00:00+02:00",
            end="2026-07-01T00:00:00+02:00",
        )
        july = health_section([], cursor="july-complete")
        monthly = post_batch(client, token, "monthly-windows", [june, july])
        assert monthly.status_code == 200
        assert all(section["cursor_committed"] for section in monthly.json()["sections"])
        state = client.get("/v1/health/state", headers=auth(token)).json()
        step_cursor = next(cursor for cursor in state["cursors"] if cursor["data_type"] == "steps")
        assert step_cursor["cursor"] == "july-complete"

        monkeypatch.setattr(main.health_sync_limit, "attempts", 1)
        main.health_sync_limit.entries.clear()
        allowed = post_batch(
            client,
            token,
            "rate-limit-one",
            [health_section([], cursor="rate-one", complete=False)],
        )
        assert allowed.status_code == 200
        limited = post_batch(
            client,
            token,
            "rate-limit-two",
            [health_section([], cursor="rate-two", complete=False)],
        )
        assert limited.status_code == 429
