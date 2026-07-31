import hashlib
from datetime import timedelta

from fastapi.testclient import TestClient

from baseline_api import main
from baseline_api.main import app, db, iso, migrate, now, uid


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


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def meal(client_id: str, eaten_at: str = "2026-07-31T08:00:00+02:00") -> dict:
    return {
        "client_id": client_id,
        "local_day": "2026-07-31",
        "eaten_at": eaten_at,
        "timezone": "Europe/Berlin",
        "meal_type": "breakfast",
        "name": "Haferfrühstück",
        "capture_method": "barcode",
        "provenance_source": "open_food_facts",
        "external_reference": "off:4008400214504",
        "ingredients": [
            {
                "original_name": "Haferflocken",
                "amount": "100",
                "unit": "g",
                "nutrients": [
                    {
                        "key": "energy",
                        "value": "370.5",
                        "unit": "kcal",
                        "basis": "portion",
                        "source": "open_food_facts",
                        "locked": True,
                    },
                    {
                        "key": "fiber",
                        "value": "10.1",
                        "unit": "g",
                        "basis": "portion",
                        "source": "open_food_facts",
                        "locked": True,
                    },
                ],
            }
        ],
    }


def reuse_request(client_id: str, meal_type: str = "lunch") -> dict:
    return {
        "client_id": client_id,
        "local_day": "2026-08-01",
        "eaten_at": "2026-08-01T12:30:00+02:00",
        "timezone": "Europe/Berlin",
        "meal_type": meal_type,
    }


def test_favorite_is_independent_deep_copy_and_user_isolated(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "favorites.db")
    with TestClient(app) as client:
        first = register(client, "favorite-first", "favorite-first-code-123456")
        second = register(client, "favorite-second", "favorite-second-code-123456")
        first_headers = auth(first)
        second_headers = auth(second)

        original = client.post(
            "/v1/meals",
            json=meal("favorite-origin"),
            headers=first_headers,
        )
        assert original.status_code == 201, original.text
        favorite = client.post(
            f"/v1/meals/{original.json()['id']}/favorite",
            json={"display_name": "Morgenstandard"},
            headers=first_headers,
        )
        repeated = client.post(
            f"/v1/meals/{original.json()['id']}/favorite",
            json={"display_name": "Ignorierter Doppeltipp"},
            headers=first_headers,
        )
        assert favorite.status_code == 201, favorite.text
        assert repeated.json()["id"] == favorite.json()["id"]
        favorite_id = favorite.json()["id"]

        assert client.get("/v1/favorites", headers=second_headers).json() == []
        assert client.get("/v1/recent-meals", headers=second_headers).json() == []
        assert (
            client.post(
                f"/v1/favorites/{favorite_id}/draft",
                json=reuse_request("foreign-draft"),
                headers=second_headers,
            ).status_code
            == 404
        )

        renamed = client.put(
            f"/v1/favorites/{favorite_id}",
            json={"display_name": "Standardfrühstück"},
            headers=first_headers,
        )
        assert renamed.status_code == 200, renamed.text
        assert renamed.json()["display_name"] == "Standardfrühstück"

        draft = client.post(
            f"/v1/favorites/{favorite_id}/draft",
            json=reuse_request("favorite-reuse"),
            headers=first_headers,
        )
        assert draft.status_code == 200, draft.text
        assert draft.json()["client_id"] == "favorite-reuse"
        assert draft.json()["meal_type"] == "lunch"
        assert draft.json()["attachment_id"] is None
        assert "id" not in draft.json()["ingredients"][0]
        fiber = next(
            value
            for value in draft.json()["ingredients"][0]["nutrients"]
            if value["key"] == "fiber"
        )
        assert fiber["value"] == "10.1"
        assert fiber["source"] == "open_food_facts"
        assert fiber["locked"] is True

        saved = client.post("/v1/meals", json=draft.json(), headers=first_headers)
        retried = client.post("/v1/meals", json=draft.json(), headers=first_headers)
        assert saved.status_code == 201, saved.text
        assert retried.json()["id"] == saved.json()["id"]
        assert saved.json()["id"] != original.json()["id"]

        changed = saved.json()
        changed["ingredients"][0]["amount"] = "50"
        updated = client.put(
            f"/v1/meals/{saved.json()['id']}",
            json=changed,
            headers=first_headers,
        )
        assert updated.status_code == 200, updated.text
        assert updated.json()["ingredients"][0]["amount"] == "50"
        assert (
            client.get("/v1/favorites", headers=first_headers).json()[0]["meal"]["ingredients"][0][
                "amount"
            ]
            == "100"
        )
        assert (
            client.get(f"/v1/meals/{original.json()['id']}", headers=first_headers).json()[
                "ingredients"
            ][0]["amount"]
            == "100"
        )

        template = client.get("/v1/favorites", headers=first_headers).json()[0]
        template["meal"]["ingredients"][0]["amount"] = "75"
        template["meal"]["ingredients"][0]["nutrients"][0]["value"] = "277.875"
        template["meal"]["ingredients"][0]["nutrients"][1]["value"] = "7.575"
        edited_template = client.put(
            f"/v1/favorites/{favorite_id}",
            json={
                "display_name": template["display_name"],
                "meal": template["meal"],
            },
            headers=first_headers,
        )
        assert edited_template.status_code == 200, edited_template.text
        assert edited_template.json()["meal"]["ingredients"][0]["amount"] == "75"
        assert (
            client.get(f"/v1/meals/{original.json()['id']}", headers=first_headers).json()[
                "ingredients"
            ][0]["amount"]
            == "100"
        )

        assert (
            client.delete(f"/v1/meals/{original.json()['id']}", headers=first_headers).status_code
            == 204
        )
        after_delete = client.post(
            f"/v1/favorites/{favorite_id}/draft",
            json=reuse_request("favorite-after-delete"),
            headers=first_headers,
        )
        assert after_delete.status_code == 200, after_delete.text
        assert after_delete.json()["ingredients"][0]["amount"] == "75"

        assert (
            client.delete(f"/v1/favorites/{favorite_id}", headers=first_headers).status_code == 204
        )
        assert client.get("/v1/favorites", headers=first_headers).json() == []


def test_recent_meals_are_paginated_and_create_unsaved_draft(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "recent.db")
    with TestClient(app) as client:
        token = register(client, "recent-user", "recent-user-code-123456")
        headers = auth(token)
        early = client.post(
            "/v1/meals",
            json=meal("recent-early", "2026-07-31T08:00:00+02:00"),
            headers=headers,
        ).json()
        late_payload = meal("recent-late", "2026-07-31T20:00:00+02:00")
        late_payload["name"] = "Abendessen"
        late = client.post("/v1/meals", json=late_payload, headers=headers).json()

        first_page = client.get("/v1/recent-meals?limit=1&offset=0", headers=headers)
        second_page = client.get("/v1/recent-meals?limit=1&offset=1", headers=headers)
        assert [value["id"] for value in first_page.json()] == [late["id"]]
        assert [value["id"] for value in second_page.json()] == [early["id"]]

        draft = client.post(
            f"/v1/meals/{late['id']}/draft",
            json=reuse_request("recent-draft", "dinner"),
            headers=headers,
        )
        assert draft.status_code == 200, draft.text
        assert draft.json()["client_id"] == "recent-draft"
        assert draft.json()["name"] == "Abendessen"
        assert draft.json()["meal_type"] == "dinner"
