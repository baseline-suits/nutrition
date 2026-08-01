import hashlib
from datetime import timedelta
from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from baseline_api import main
from baseline_api.main import (
    app,
    cleanup_expired_uploads,
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
    assert response.status_code == 201
    return response.json()["token"]


def auth(token: str, key: str | None = None) -> dict[str, str]:
    result = {"Authorization": f"Bearer {token}"}
    if key:
        result["Idempotency-Key"] = key
    return result


def jpeg_with_metadata() -> bytes:
    image = Image.new("RGB", (64, 32), "#9274ff")
    exif = Image.Exif()
    exif[270] = "private metadata"
    output = BytesIO()
    image.save(output, format="JPEG", exif=exif)
    return output.getvalue()


def model_result() -> dict:
    return {
        "name": "Fotomahlzeit",
        "ingredients": [
            {
                "original_name": "sichtbare Zutat",
                "amount": "100",
                "unit": "g",
                "nutrients": [],
            }
        ],
        "warnings": [],
    }


def create_and_finalize(client: TestClient, token: str, data: bytes, key: str) -> dict:
    created = client.post(
        "/v1/uploads",
        json={"media_type": "image/jpeg", "size_bytes": len(data)},
        headers=auth(token, key),
    )
    assert created.status_code == 201, created.text
    upload_id = created.json()["id"]
    uploaded = client.put(
        f"/v1/uploads/{upload_id}/content",
        content=data,
        headers=auth(token) | {"Content-Type": "image/jpeg"},
    )
    assert uploaded.status_code == 200, uploaded.text
    finalized = client.post(f"/v1/uploads/{upload_id}/finalize", headers=auth(token))
    assert finalized.status_code == 200, finalized.text
    return finalized.json()


def test_private_upload_analysis_binding_and_deletion(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "uploads.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    provider_images: list[bytes | None] = []
    provider_texts: list[str | None] = []

    def provider(text, locale, meal_type, image_bytes=None, image_media_type=None):
        provider_texts.append(text)
        provider_images.append(image_bytes)
        return model_result(), {
            "prompt_tokens": 20,
            "completion_tokens": 20,
            "estimated_cost_micros": 100,
        }

    monkeypatch.setattr(main, "call_analysis_provider", provider)
    data = jpeg_with_metadata()
    with TestClient(app) as client:
        first = register(client, "photo-first", "photo-first-code-123456")
        second = register(client, "photo-second", "photo-second-code-123456")
        upload = create_and_finalize(client, first, data, "photo-upload-1")
        upload_id = upload["id"]

        repeated = client.post(f"/v1/uploads/{upload_id}/finalize", headers=auth(first))
        assert repeated.status_code == 200
        assert repeated.json() == upload
        assert client.get(f"/v1/uploads/{upload_id}", headers=auth(second)).status_code == 404
        assert (
            client.get(f"/v1/uploads/{upload_id}/content", headers=auth(second)).status_code == 404
        )
        downloaded = client.get(f"/v1/uploads/{upload_id}/content", headers=auth(first))
        assert downloaded.status_code == 200
        assert downloaded.headers["x-content-type-options"] == "nosniff"
        with Image.open(BytesIO(downloaded.content)) as stored:
            assert stored.format == "JPEG"
            assert not stored.getexif()

        analysis = client.post(
            "/v1/analysis",
            json={
                "attachment_id": upload_id,
                "text": "Kartoffeln, vermutlich ohne Sauce",
                "locale": "de",
                "meal_type": "lunch",
            },
            headers=auth(first, "photo-analysis-1"),
        )
        assert analysis.status_code == 201, analysis.text
        assert analysis.json()["attachment_id"] == upload_id
        assert provider_images and provider_images[0]
        assert provider_texts == ["Kartoffeln, vermutlich ohne Sauce"]

        meal = {
            "client_id": "photo-meal-1",
            "local_day": "2026-07-31",
            "eaten_at": "2026-07-31T12:00:00+02:00",
            "timezone": "Europe/Berlin",
            "meal_type": "lunch",
            "name": "Fotomahlzeit",
            "capture_method": "camera",
            "attachment_id": upload_id,
            "ingredients": [],
        }
        saved = client.post("/v1/meals", json=meal, headers=auth(first))
        assert saved.status_code == 201, saved.text
        assert saved.json()["attachment_id"] == upload_id

        assert client.delete(f"/v1/uploads/{upload_id}", headers=auth(first)).status_code == 204
        assert (
            client.get(f"/v1/uploads/{upload_id}/content", headers=auth(first)).status_code == 404
        )
        loaded = client.get(f"/v1/meals/{saved.json()['id']}", headers=auth(first))
        assert loaded.json()["attachment_id"] is None
        assert loaded.json()["photo_deleted"] is True

    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM photo_uploads WHERE id=?",
                (upload_id,),
            ).fetchone()[0]
            == 0
        )
        assert (
            connection.execute(
                "SELECT status FROM deletion_jobs WHERE kind='photo' AND target_id=?",
                (upload_id,),
            ).fetchone()["status"]
            == "completed"
        )


def test_mime_spoofing_and_expired_upload_cleanup(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "invalid-uploads.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    payload = b"this is not an image"
    with TestClient(app) as client:
        token = register(client, "photo-invalid", "photo-invalid-code-123456")
        created = client.post(
            "/v1/uploads",
            json={"media_type": "image/jpeg", "size_bytes": len(payload)},
            headers=auth(token, "invalid-photo-1"),
        ).json()
        upload_id = created["id"]
        assert (
            client.put(
                f"/v1/uploads/{upload_id}/content",
                content=payload,
                headers=auth(token) | {"Content-Type": "image/jpeg"},
            ).status_code
            == 200
        )
        invalid = client.post(f"/v1/uploads/{upload_id}/finalize", headers=auth(token))
        assert invalid.status_code == 422
        assert invalid.json()["detail"]["code"] == "invalid_image"

        stale = client.post(
            "/v1/uploads",
            json={"media_type": "image/jpeg", "size_bytes": 20},
            headers=auth(token, "stale-photo-1"),
        ).json()
        with db() as connection:
            connection.execute(
                "UPDATE photo_uploads SET expires_at=? WHERE id=?",
                (iso(now() - timedelta(seconds=1)), stale["id"]),
            )
            connection.commit()

    assert cleanup_expired_uploads() == 2
    with db() as connection:
        assert connection.execute("SELECT COUNT(*) FROM photo_uploads").fetchone()[0] == 0
        jobs = {
            row["target_id"]: row["status"]
            for row in connection.execute(
                "SELECT target_id,status FROM deletion_jobs WHERE kind='photo'"
            )
        }
    assert jobs[upload_id] == "completed"
    assert jobs[stale["id"]] == "completed"


def test_expired_upload_cleanup_retries_failed_object_deletion(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "cleanup-retry.db")
    monkeypatch.setattr(main.settings, "object_store", tmp_path / "objects")
    with TestClient(app) as client:
        token = register(client, "photo-cleanup-retry", "photo-cleanup-retry-code")
        upload = client.post(
            "/v1/uploads",
            json={"media_type": "image/jpeg", "size_bytes": 20},
            headers=auth(token, "cleanup-retry-photo-1"),
        ).json()
        with db() as connection:
            connection.execute(
                "UPDATE photo_uploads SET expires_at=? WHERE id=?",
                (iso(now() - timedelta(seconds=1)), upload["id"]),
            )
            connection.commit()

    original_remove = main.remove_upload_files

    def fail_removal(_object_key):
        raise OSError("object store temporarily unavailable")

    monkeypatch.setattr(main, "remove_upload_files", fail_removal)
    assert cleanup_expired_uploads() == 1
    with db() as connection:
        assert (
            connection.execute(
                "SELECT status FROM photo_uploads WHERE id=?", (upload["id"],)
            ).fetchone()["status"]
            == "deleted"
        )
        assert (
            connection.execute(
                "SELECT status FROM deletion_jobs WHERE kind='photo' AND target_id=?",
                (upload["id"],),
            ).fetchone()["status"]
            == "failed_retryable"
        )

    monkeypatch.setattr(main, "remove_upload_files", original_remove)
    assert retry_deletion_jobs(force=True) == {"attempted": 1, "completed": 1}
    with db() as connection:
        assert (
            connection.execute(
                "SELECT COUNT(*) FROM photo_uploads WHERE id=?", (upload["id"],)
            ).fetchone()[0]
            == 0
        )
