import hashlib
import json
import sqlite3
from datetime import datetime, timedelta
from io import BytesIO
from pathlib import Path

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from PIL import Image

from baseline_api import main
from baseline_api.main import (
    app,
    apply_deletion_suppressions,
    db,
    iso,
    migrate,
    now,
    retry_deletion_jobs,
    uid,
)


def register(client: TestClient, username: str, code: str) -> str:
    migrate()
    with db() as connection:
        connection.execute(
            "INSERT INTO access_codes VALUES (?,?,?,?,NULL,NULL,NULL)",
            (
                uid(),
                hashlib.sha256(code.encode()).hexdigest(),
                iso(now()),
                iso(now() + timedelta(hours=1)),
            ),
        )
        connection.commit()
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


def auth(token: str, key: str | None = None) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}"}
    if key:
        headers["Idempotency-Key"] = key
    return headers


def photo() -> bytes:
    output = BytesIO()
    Image.new("RGB", (32, 32), "#7259d9").save(output, format="JPEG")
    return output.getvalue()


def upload(client: TestClient, token: str, key: str) -> dict:
    data = photo()
    created = client.post(
        "/v1/uploads",
        json={"media_type": "image/jpeg", "size_bytes": len(data)},
        headers=auth(token, key),
    )
    assert created.status_code == 201, created.text
    upload_id = created.json()["id"]
    assert (
        client.put(
            f"/v1/uploads/{upload_id}/content",
            content=data,
            headers=auth(token) | {"Content-Type": "image/jpeg"},
        ).status_code
        == 200
    )
    finalized = client.post(f"/v1/uploads/{upload_id}/finalize", headers=auth(token))
    assert finalized.status_code == 200, finalized.text
    return finalized.json()


def meal(client_id: str, attachment_id: str | None = None) -> dict:
    return {
        "client_id": client_id,
        "local_day": "2026-07-31",
        "eaten_at": "2026-07-31T12:00:00+02:00",
        "timezone": "Europe/Berlin",
        "meal_type": "lunch",
        "name": "Löschtest",
        "capture_method": "camera" if attachment_id else "manual",
        "attachment_id": attachment_id,
        "ingredients": [
            {
                "original_name": "Kartoffel",
                "amount": "200",
                "unit": "g",
                "nutrients": [
                    {
                        "key": "energy",
                        "value": "150",
                        "unit": "kcal",
                        "basis": "portion",
                    }
                ],
            }
        ],
    }


def test_migration_rolls_back_script_and_version_together(tmp_path, monkeypatch):
    database = tmp_path / "atomic-migration.db"
    monkeypatch.setattr(main.settings, "database", database)
    original_read_text = Path.read_text
    inject_failure = True

    def migration_text(path: Path, *args, **kwargs) -> str:
        content = original_read_text(path, *args, **kwargs)
        if inject_failure and path.name == "009_data_deletion.sql":
            return f"{content}\nTHIS IS NOT VALID SQL;"
        return content

    monkeypatch.setattr(Path, "read_text", migration_text)
    with pytest.raises(sqlite3.OperationalError):
        migrate()

    with sqlite3.connect(database) as connection:
        user_columns = {row[1] for row in connection.execute("PRAGMA table_info(users)").fetchall()}
        assert "deletion_requested_at" not in user_columns
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM schema_migrations WHERE version='009_data_deletion.sql'"
            ).fetchone()[0]
            == 0
        )

    inject_failure = False
    migrate()
    with sqlite3.connect(database) as connection:
        user_columns = {row[1] for row in connection.execute("PRAGMA table_info(users)").fetchall()}
        assert "deletion_requested_at" in user_columns
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM schema_migrations WHERE version='009_data_deletion.sql'"
            ).fetchone()[0]
            == 1
        )


def test_stale_authenticated_writes_are_rejected_after_deletion_starts(
    tmp_path,
    monkeypatch,
):
    monkeypatch.setattr(main.settings, "database", tmp_path / "stale-write.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    with TestClient(app) as client:
        token = register(client, "stale-write", "stale-write-code-123456")
        existing = client.post(
            "/v1/meals",
            json=meal("before-deletion"),
            headers=auth(token, "before-deletion"),
        ).json()
        stale_user = main.current_user(f"Bearer {token}")

        with db() as connection:
            connection.execute(
                "UPDATE users SET deletion_requested_at=? WHERE id=?",
                (iso(now()), stale_user.id),
            )
            connection.commit()

        profile = main.ProfileInput(
            locale="de",
            timezone="Europe/Berlin",
            target_kcal="2100",
            target_protein_g="130",
            target_carbs_g="220",
            target_fat_g="70",
            manual=True,
        )
        blocked_calls = (
            lambda: main.create_meal(
                main.MealInput.model_validate(meal("after-deletion")),
                stale_user,
                "after-deletion",
            ),
            lambda: main.save_profile(profile, stale_user),
            lambda: main.create_favorite(
                existing["id"], main.FavoriteCreate(display_name="Nicht speichern"), stale_user
            ),
        )
        for call in blocked_calls:
            with pytest.raises(HTTPException) as error:
                call()
            assert error.value.status_code == 401

    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM meals WHERE user_id=?", (stale_user.id,)
            ).fetchone()[0]
            == 1
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM profiles WHERE user_id=?", (stale_user.id,)
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM favorites WHERE user_id=?", (stale_user.id,)
            ).fetchone()[0]
            == 0
        )


def test_migration_physically_removes_legacy_soft_deleted_meals(tmp_path, monkeypatch):
    database = tmp_path / "legacy-soft-delete.db"
    object_store = tmp_path / "objects"
    monkeypatch.setattr(main.settings, "database", database)
    monkeypatch.setattr(main.settings, "object_store", object_store)
    migrations = Path(main.__file__).resolve().parents[1] / "migrations"
    with sqlite3.connect(database) as connection:
        connection.execute(
            """CREATE TABLE schema_migrations (
               version TEXT PRIMARY KEY,
               applied_at TEXT NOT NULL
               )"""
        )
        for migration in sorted(migrations.glob("*.sql")):
            if migration.name == "009_data_deletion.sql":
                continue
            connection.executescript(migration.read_text(encoding="utf-8"))
            connection.execute(
                "INSERT INTO schema_migrations VALUES (?,?)",
                (migration.name, iso(now())),
            )
        stamp = iso(now())
        connection.execute(
            "INSERT INTO users VALUES (?,?,?,?,?,?,?)",
            ("legacy-user", "legacy-user", "unused", "de", "Europe/Berlin", 1, stamp),
        )
        connection.execute(
            """INSERT INTO meals (
               id,user_id,client_id,local_day,eaten_at,timezone,meal_type,name,note,
               capture_method,version,deleted_at,created_at,updated_at
               ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                "legacy-meal",
                "legacy-user",
                "legacy-client",
                "2026-07-31",
                "2026-07-31T12:00:00+02:00",
                "Europe/Berlin",
                "lunch",
                "Legacy-Mahlzeit",
                None,
                "camera",
                1,
                stamp,
                stamp,
                stamp,
            ),
        )
        connection.execute(
            """INSERT INTO meals (
               id,user_id,client_id,local_day,eaten_at,timezone,meal_type,name,note,
               capture_method,version,deleted_at,created_at,updated_at
               ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                "legacy-photo-meal",
                "legacy-user",
                "legacy-photo-client",
                "2026-07-31",
                "2026-07-31T13:00:00+02:00",
                "Europe/Berlin",
                "lunch",
                "Mahlzeit mit gelöschtem Foto",
                None,
                "camera",
                1,
                None,
                stamp,
                stamp,
            ),
        )
        connection.execute(
            "INSERT INTO ingredients VALUES (?,?,?,?,?,?,?,?)",
            ("legacy-ingredient", "legacy-meal", 0, "Kartoffel", None, None, "200", "g"),
        )
        connection.execute(
            "INSERT INTO nutrient_values VALUES (?,?,?,?,?,?,?,?,?,?)",
            (
                "legacy-nutrient",
                "legacy-meal",
                "legacy-ingredient",
                "energy",
                "150",
                "kcal",
                "portion",
                "user",
                1,
                "exact",
            ),
        )
        connection.execute(
            "INSERT INTO provenance VALUES (?,?,?,?,?)",
            ("legacy-provenance", "legacy-meal", "ai_estimate", "analysis:legacy-analysis", stamp),
        )
        connection.execute(
            """INSERT INTO photo_uploads (
               id,user_id,idempotency_key,object_key,status,declared_media_type,
               stored_media_type,declared_size,size_bytes,width,height,expires_at,
               retained_at,deleted_at,created_at,updated_at
               ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                "legacy-upload",
                "legacy-user",
                "legacy-upload-key",
                "ab/legacy.jpg",
                "analysis_attached",
                "image/jpeg",
                "image/jpeg",
                10,
                10,
                10,
                10,
                iso(now() + timedelta(hours=1)),
                stamp,
                None,
                stamp,
                stamp,
            ),
        )
        connection.execute(
            "INSERT INTO attachments VALUES (?,?,?,?,?,?)",
            (
                "legacy-attachment",
                "legacy-meal",
                "ab/legacy.jpg",
                "image/jpeg",
                stamp,
                "legacy-upload",
            ),
        )
        for upload_id, idempotency_key, object_key in (
            ("legacy-deleted-attached", "legacy-deleted-attached-key", "ab/deleted-attached.jpg"),
            ("legacy-deleted-orphan", "legacy-deleted-orphan-key", "ab/deleted-orphan.jpg"),
        ):
            connection.execute(
                """INSERT INTO photo_uploads (
                   id,user_id,idempotency_key,object_key,status,declared_media_type,
                   stored_media_type,declared_size,size_bytes,width,height,expires_at,
                   retained_at,deleted_at,created_at,updated_at
                   ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (
                    upload_id,
                    "legacy-user",
                    idempotency_key,
                    object_key,
                    "deleted",
                    "image/jpeg",
                    "image/jpeg",
                    10,
                    10,
                    10,
                    10,
                    iso(now() + timedelta(hours=1)),
                    None,
                    stamp,
                    stamp,
                    stamp,
                ),
            )
        connection.execute(
            "INSERT INTO attachments VALUES (?,?,?,?,?,?)",
            (
                "legacy-deleted-attachment",
                "legacy-photo-meal",
                "ab/deleted-attached.jpg",
                "image/jpeg",
                stamp,
                "legacy-deleted-attached",
            ),
        )
        connection.execute(
            """INSERT INTO analysis_requests (
               id,user_id,idempotency_key,input_hash,input_kind,status,model_name,
               prompt_version,schema_version,response_json,input_chars,created_at,
               updated_at,attachment_id
               ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            (
                "legacy-analysis",
                "legacy-user",
                "legacy-analysis-key",
                "hash",
                "photo",
                "completed",
                "model",
                "prompt",
                "schema",
                json.dumps({"attachment_id": "legacy-upload"}),
                0,
                stamp,
                stamp,
                "legacy-upload",
            ),
        )
        legacy_favorite = meal("legacy-client", "legacy-upload")
        legacy_favorite["external_reference"] = "analysis:legacy-analysis"
        connection.execute(
            "INSERT INTO favorites VALUES (?,?,?,?,?,?,?,NULL)",
            (
                "legacy-favorite",
                "legacy-user",
                "legacy-meal",
                "Legacy-Vorlage",
                json.dumps(legacy_favorite),
                stamp,
                stamp,
            ),
        )
        connection.execute(
            "INSERT INTO meal_mutations VALUES (?,?,?,?,?,?,?,?)",
            (
                "legacy-mutation",
                "legacy-user",
                "legacy-key",
                "create",
                "legacy-client",
                "hash",
                json.dumps(legacy_favorite),
                stamp,
            ),
        )
        connection.commit()
    object_path = object_store / "ab" / "legacy.jpg"
    object_path.parent.mkdir(parents=True)
    object_path.write_bytes(b"legacy")
    attached_deleted_path = object_store / "ab" / "deleted-attached.jpg"
    attached_deleted_path.write_bytes(b"legacy deleted attachment")
    orphan_deleted_path = object_store / "ab" / "deleted-orphan.jpg"
    orphan_deleted_path.write_bytes(b"legacy deleted orphan")

    migrate()

    with db() as connection:
        assert connection.execute("SELECT COUNT(*) FROM meals").fetchone()[0] == 1
        assert connection.execute("SELECT COUNT(*) FROM ingredients").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM nutrient_values").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM analysis_requests").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM meal_mutations").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM attachments").fetchone()[0] == 0
        assert connection.execute(
            "SELECT photo_deleted_at FROM meals WHERE id='legacy-photo-meal'"
        ).fetchone()["photo_deleted_at"]
        favorite = connection.execute(
            "SELECT original_meal_id,snapshot_json FROM favorites"
        ).fetchone()
        assert favorite["original_meal_id"] is None
        assert json.loads(favorite["snapshot_json"])["external_reference"] is None
        assert (
            connection.execute(
                "SELECT status FROM deletion_jobs WHERE target_id='legacy-meal'"
            ).fetchone()["status"]
            == "pending"
        )
        assert (
            connection.execute(
                """SELECT COUNT(*) FROM deletion_jobs
                   WHERE kind='photo'
                     AND target_id IN ('legacy-deleted-attached','legacy-deleted-orphan')
                     AND status='pending'"""
            ).fetchone()[0]
            == 2
        )
    assert object_path.exists()
    assert attached_deleted_path.exists()
    assert orphan_deleted_path.exists()

    assert retry_deletion_jobs(force=True) == {"attempted": 3, "completed": 3}
    assert not object_path.exists()
    assert not attached_deleted_path.exists()
    assert not orphan_deleted_path.exists()
    with db() as connection:
        assert connection.execute("SELECT COUNT(*) FROM photo_uploads").fetchone()[0] == 0


def test_meal_delete_is_physical_idempotent_and_keeps_independent_favorite(
    tmp_path,
    monkeypatch,
):
    monkeypatch.setattr(main.settings, "database", tmp_path / "meal-deletion.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")

    def provider(*_args, **_kwargs):
        return {
            "name": "Löschtest",
            "ingredients": [
                {
                    "original_name": "Kartoffel",
                    "amount": "200",
                    "unit": "g",
                    "nutrients": [],
                }
            ],
            "warnings": [],
        }, {}

    monkeypatch.setattr(main, "call_analysis_provider", provider)
    with TestClient(app) as client:
        first = register(client, "delete-meal-first", "delete-meal-first-code-123456")
        second = register(client, "delete-meal-second", "delete-meal-second-code-123456")
        attached = upload(client, first, "delete-meal-photo")
        upload_id = attached["id"]
        analysis = client.post(
            "/v1/analysis",
            json={
                "attachment_id": upload_id,
                "locale": "de",
                "meal_type": "lunch",
            },
            headers=auth(first, "delete-meal-analysis"),
        ).json()
        payload = meal("delete-meal-client", upload_id)
        payload["provenance_source"] = "ai_estimate"
        payload["external_reference"] = f"analysis:{analysis['id']}"
        created = client.post(
            "/v1/meals",
            json=payload,
            headers=auth(first, "delete-meal-create"),
        ).json()
        favorite = client.post(
            f"/v1/meals/{created['id']}/favorite",
            json={"display_name": "Bleibende Vorlage"},
            headers=auth(first),
        )
        assert favorite.status_code == 201
        with db() as connection:
            legacy_snapshot = favorite.json()["meal"]
            legacy_snapshot["external_reference"] = f"analysis:{analysis['id']}"
            connection.execute(
                "UPDATE favorites SET snapshot_json=? WHERE id=?",
                (json.dumps(legacy_snapshot), favorite.json()["id"]),
            )
            connection.commit()
        other = client.post(
            "/v1/meals",
            json=meal("other-user-meal"),
            headers=auth(second),
        ).json()

        assert (
            client.delete(
                f"/v1/meals/{created['id']}",
                headers=auth(second, "foreign-delete-key"),
            ).status_code
            == 204
        )
        assert client.get(f"/v1/meals/{created['id']}", headers=auth(first)).status_code == 200
        delete_headers = auth(first, "delete-meal-stable-key")
        assert (
            client.delete(f"/v1/meals/{created['id']}", headers=delete_headers).status_code == 204
        )
        assert (
            client.delete(f"/v1/meals/{created['id']}", headers=delete_headers).status_code == 204
        )
        assert client.get(f"/v1/meals/{created['id']}", headers=auth(first)).status_code == 404
        assert client.get(f"/v1/meals/{other['id']}", headers=auth(second)).status_code == 200
        assert client.get("/v1/days/2026-07-31/meals", headers=auth(first)).json() == []
        assert (
            client.get("/v1/days/2026-07-31/summary", headers=auth(first)).json()["meal_count"] == 0
        )
        remaining_favorite = client.get("/v1/favorites", headers=auth(first)).json()
        assert remaining_favorite[0]["original_meal_id"] is None
        assert remaining_favorite[0]["meal"]["name"] == "Löschtest"
        assert remaining_favorite[0]["meal"]["external_reference"] is None
        assert (
            client.get(f"/v1/uploads/{upload_id}/content", headers=auth(first)).status_code == 404
        )

    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM meals WHERE id=?",
                (created["id"],),
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM analysis_requests WHERE id=?",
                (analysis["id"],),
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                """SELECT COUNT(*) FROM meal_mutations
               WHERE user_id=(SELECT id FROM users WHERE username='delete-meal-first')
                 AND response_json IS NOT NULL"""
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM photo_uploads WHERE id=?",
                (upload_id,),
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT status FROM deletion_jobs WHERE kind='meal' AND target_id=?",
                (created["id"],),
            ).fetchone()["status"]
            == "completed"
        )


def test_photo_delete_preserves_meal_and_removes_every_access_path(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "photo-deletion.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")

    def provider(*_args, **_kwargs):
        return {
            "name": "Löschtest",
            "ingredients": [
                {
                    "original_name": "Kartoffel",
                    "amount": "200",
                    "unit": "g",
                    "nutrients": [],
                }
            ],
            "warnings": [],
        }, {}

    monkeypatch.setattr(main, "call_analysis_provider", provider)
    with TestClient(app) as client:
        first = register(client, "delete-photo-first", "delete-photo-first-code-123456")
        second = register(client, "delete-photo-second", "delete-photo-second-code-123456")
        attached = upload(client, first, "delete-photo-upload")
        upload_id = attached["id"]
        analysis_payload = {
            "attachment_id": upload_id,
            "locale": "de",
            "meal_type": "lunch",
        }
        analysis_headers = auth(first, "delete-photo-analysis")
        assert (
            client.post(
                "/v1/analysis",
                json=analysis_payload,
                headers=analysis_headers,
            ).status_code
            == 201
        )
        created = client.post(
            "/v1/meals",
            json=meal("delete-photo-meal", upload_id),
            headers=auth(first),
        ).json()

        assert client.delete(f"/v1/uploads/{upload_id}", headers=auth(second)).status_code == 204
        assert (
            client.get(f"/v1/uploads/{upload_id}/content", headers=auth(first)).status_code == 200
        )
        assert client.delete(f"/v1/uploads/{upload_id}", headers=auth(first)).status_code == 204
        assert client.delete(f"/v1/uploads/{upload_id}", headers=auth(first)).status_code == 204
        assert client.get(f"/v1/uploads/{upload_id}", headers=auth(first)).status_code == 404
        assert (
            client.get(f"/v1/uploads/{upload_id}/content", headers=auth(first)).status_code == 404
        )
        repeated_analysis = client.post(
            "/v1/analysis",
            json=analysis_payload,
            headers=analysis_headers,
        )
        assert repeated_analysis.status_code == 404
        loaded = client.get(f"/v1/meals/{created['id']}", headers=auth(first)).json()
        assert loaded["attachment_id"] is None
        assert loaded["photo_deleted"] is True
        assert loaded["name"] == "Löschtest"

    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM photo_uploads WHERE id=?",
                (upload_id,),
            ).fetchone()[0]
            == 0
        )
        assert connection.execute("SELECT COUNT(*) FROM analysis_requests").fetchone()[0] == 0


def test_inflight_finalize_removes_file_after_account_deletion_starts(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "inflight-deletion.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    data = photo()
    with TestClient(app) as client:
        token = register(client, "delete-inflight", "delete-inflight-code-123456")
        created = client.post(
            "/v1/uploads",
            json={"media_type": "image/jpeg", "size_bytes": len(data)},
            headers=auth(token, "delete-inflight-upload"),
        ).json()
        upload_id = created["id"]
        assert (
            client.put(
                f"/v1/uploads/{upload_id}/content",
                content=data,
                headers=auth(token) | {"Content-Type": "image/jpeg"},
            ).status_code
            == 200
        )
        with db() as connection:
            upload_row = connection.execute(
                "SELECT object_key,user_id FROM photo_uploads WHERE id=?",
                (upload_id,),
            ).fetchone()

        original_finalize = main.finalize_upload_file

        def finalize_during_deletion(row):
            result = original_finalize(row)
            with db() as connection:
                connection.execute(
                    "UPDATE users SET deletion_requested_at=? WHERE id=?",
                    (iso(now()), upload_row["user_id"]),
                )
                connection.commit()
            return result

        monkeypatch.setattr(main, "finalize_upload_file", finalize_during_deletion)
        finalized = client.post(f"/v1/uploads/{upload_id}/finalize", headers=auth(token))
        assert finalized.status_code == 401
        assert not main.object_path(upload_row["object_key"]).exists()


def test_inflight_analysis_cannot_persist_after_account_deletion_starts(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "inflight-analysis.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    with TestClient(app) as client:
        token = register(client, "delete-analysis", "delete-analysis-code-123456")
        with db() as connection:
            user_id = connection.execute(
                "SELECT id FROM users WHERE username='delete-analysis'"
            ).fetchone()["id"]

        def provider(*_args, **_kwargs):
            with db() as connection:
                connection.execute(
                    "UPDATE users SET deletion_requested_at=? WHERE id=?",
                    (iso(now()), user_id),
                )
                connection.commit()
            return {
                "name": "Darf nicht gespeichert werden",
                "ingredients": [
                    {
                        "original_name": "Kartoffel",
                        "amount": "200",
                        "unit": "g",
                        "nutrients": [],
                    }
                ],
                "warnings": [],
            }, {}

        monkeypatch.setattr(main, "call_analysis_provider", provider)
        response = client.post(
            "/v1/analysis",
            json={"text": "Kartoffeln", "locale": "de", "meal_type": "lunch"},
            headers=auth(token, "delete-inflight-analysis"),
        )
        assert response.status_code == 401

    with db() as connection:
        assert connection.execute("SELECT COUNT(*) FROM analysis_requests").fetchone()[0] == 0


def test_account_delete_revokes_login_retries_storage_and_suppresses_restore(
    tmp_path,
    monkeypatch,
):
    monkeypatch.setattr(main.settings, "database", tmp_path / "account-deletion.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    with TestClient(app) as client:
        first = register(client, "delete-account-first", "delete-account-first-code-123456")
        second = register(client, "delete-account-second", "delete-account-second-code-123456")
        attached = upload(client, first, "delete-account-upload")
        client.post(
            "/v1/meals",
            json=meal("delete-account-meal", attached["id"]),
            headers=auth(first),
        )
        with db() as connection:
            first_user = connection.execute(
                "SELECT * FROM users WHERE username='delete-account-first'"
            ).fetchone()
            first_user_values = tuple(first_user)
            connection.execute(
                """INSERT INTO product_cache VALUES (?,?,?,?)""",
                ("4008400214504", "{}", iso(now()), iso(now() + timedelta(hours=1))),
            )
            connection.commit()

        wrong = client.request(
            "DELETE",
            "/v1/account",
            json={"password": "wrong-password-value", "confirmation": "DELETE"},
            headers=auth(first),
        )
        assert wrong.status_code == 403
        assert client.get("/v1/auth/session", headers=auth(first)).status_code == 200

        original_remove = main.remove_upload_files

        def unavailable(_object_key: str) -> None:
            raise OSError("object store unavailable")

        monkeypatch.setattr(main, "remove_upload_files", unavailable)
        deleted = client.request(
            "DELETE",
            "/v1/account",
            json={"password": "a-secure-password", "confirmation": "DELETE"},
            headers=auth(first),
        )
        assert deleted.status_code == 202
        assert deleted.json()["status"] == "accepted"
        assert client.get("/v1/auth/session", headers=auth(first)).status_code == 401
        assert (
            client.post(
                "/v1/auth/login",
                json={
                    "username": "delete-account-first",
                    "password": "a-secure-password",
                },
            ).status_code
            == 401
        )
        assert client.get("/v1/auth/session", headers=auth(second)).status_code == 200
        assert (
            client.post(
                "/v1/analysis",
                json={"text": "darf nicht laufen", "locale": "de", "meal_type": "lunch"},
                headers=auth(first, "deleted-user-analysis"),
            ).status_code
            == 401
        )

    with db() as connection:
        pending = connection.execute(
            "SELECT * FROM deletion_jobs WHERE id=?",
            (deleted.json()["deletion_id"],),
        ).fetchone()
        assert pending["status"] == "failed_retryable"
        assert connection.execute(
            """SELECT deletion_requested_at FROM users
               WHERE username='delete-account-first'"""
        ).fetchone()["deletion_requested_at"]

    monkeypatch.setattr(main, "remove_upload_files", original_remove)
    assert retry_deletion_jobs(force=True) == {"attempted": 1, "completed": 1}
    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM users WHERE username='delete-account-first'"
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM users WHERE username='delete-account-second'"
            ).fetchone()[0]
            == 1
        )
        assert connection.execute("SELECT COUNT(*) FROM product_cache").fetchone()[0] == 1
        tombstone = connection.execute(
            """SELECT completed_at,retain_until FROM account_deletion_tombstones
               WHERE user_id=?""",
            (first_user["id"],),
        ).fetchone()
        assert tombstone["completed_at"]
        assert datetime.fromisoformat(tombstone["retain_until"]) - datetime.fromisoformat(
            tombstone["completed_at"]
        ) >= timedelta(days=44)

        restored = list(first_user_values)
        restored[-1] = None
        connection.execute(
            "INSERT INTO users VALUES (?,?,?,?,?,?,?,?)",
            restored,
        )
        connection.commit()

    assert apply_deletion_suppressions() == 1
    assert retry_deletion_jobs(force=True) == {"attempted": 1, "completed": 1}
    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM users WHERE id=?",
                (first_user["id"],),
            ).fetchone()[0]
            == 0
        )
