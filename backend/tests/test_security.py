import hashlib
import json
from datetime import timedelta
from pathlib import Path

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from baseline_api import main
from baseline_api.main import app, db, iso, migrate, now, uid

FIXTURE = Path(__file__).parent / "fixtures" / "off_product.json"


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


def test_rate_limiter_bounds_untrusted_keys():
    limiter = main.SlidingWindow(attempts=10, max_keys=2)
    limiter.check("first")
    limiter.check("second")
    limiter.check("third")

    assert len(limiter.entries) == 2
    assert "first" not in limiter.entries
    assert set(limiter.entries) == {"second", "third"}


def test_session_header_is_bounded_and_security_headers_are_present(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "security.db")
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.headers["x-content-type-options"] == "nosniff"
        assert response.headers["x-frame-options"] == "DENY"
        assert response.headers["referrer-policy"] == "no-referrer"

        huge_token = "a" * (main.MAX_SESSION_TOKEN_LENGTH + 1)
        invalid = client.get("/v1/auth/session", headers={"Authorization": f"Bearer {huge_token}"})

    assert invalid.status_code == 401
    assert invalid.json()["detail"]["code"] == "invalid_session"


def test_open_food_facts_image_url_cannot_point_to_arbitrary_host():
    product = json.loads(FIXTURE.read_text(encoding="utf-8"))
    product["image_front_small_url"] = "https://attacker.example/image.jpg"

    mapped = main.map_off_product(product)

    assert mapped.image_url is None


def test_login_rate_limit_key_does_not_store_raw_username(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "login.db")
    with TestClient(app) as client:
        register(client, "security-user", "security-code-value-123456")
        response = client.post(
            "/v1/auth/login",
            json={"username": "security-user", "password": "wrong-password"},
        )

    assert response.status_code == 401
    assert all("security-user" not in key for key in main.auth_limit.entries)


def test_rate_limiter_rejects_excessive_attempts():
    limiter = main.SlidingWindow(attempts=1)
    limiter.check("same-client")

    with pytest.raises(HTTPException) as error:
        limiter.check("same-client")

    assert error.value.status_code == 429
