import hashlib
import json
from datetime import timedelta
from pathlib import Path

from fastapi.testclient import TestClient

from baseline_api import main
from baseline_api.main import app, db, iso, migrate, normalize_barcode, now, uid

FIXTURES = Path(__file__).parent / "fixtures"


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
    return response.json()["token"]


def auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def off_product(barcode: str = "4008400214504", energy: float = 370.5) -> dict:
    product = json.loads((FIXTURES / "off_product.json").read_text(encoding="utf-8"))
    product["code"] = barcode
    product["nutriments"]["energy-kcal_100g"] = energy
    return product


def test_barcode_lookup_is_validated_cached_and_source_locked(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "products.db")
    calls = []

    def provider(path, query=None):
        calls.append((path, query))
        return {"status": 1, "product": off_product()}

    monkeypatch.setattr(main, "call_off", provider)
    with TestClient(app) as client:
        first = register(client, "product-first", "product-first-code-123456")
        second = register(client, "product-second", "product-second-code-123456")
        found = client.get("/v1/products/barcode/4008400214504", headers=auth(first))
        cached = client.get("/v1/products/barcode/4008400214504", headers=auth(second))

    assert found.status_code == 200, found.text
    assert cached.json() == found.json()
    assert len(calls) == 1
    assert found.json()["basis"] == "100g"
    assert found.json()["schema_version"] == "off-product/1.0.0"
    assert found.json()["source"] == "open_food_facts"
    assert found.json()["language"] == "de"
    assert found.json()["country"] == "germany"
    assert found.json()["missing_core"] == []
    assert found.json()["image_url"].startswith("https://")
    assert all(item["source"] == "open_food_facts" for item in found.json()["nutrients"])
    assert all(item["locked"] is True for item in found.json()["nutrients"])


def test_product_search_skips_incomplete_records_and_preserves_leading_zero(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "search.db")

    def provider(path, query=None):
        assert path == "/cgi/search.pl"
        assert query["search_terms"] == "Hafer"
        return {
            "products": [
                off_product("012345678905"),
                {"code": "4008400214504", "product_name": "Ohne Nährwerte"},
            ]
        }

    monkeypatch.setattr(main, "call_off", provider)
    with TestClient(app) as client:
        token = register(client, "search-user", "search-code-value-123456")
        result = client.get("/v1/products/search?q=Hafer", headers=auth(token))

    assert result.status_code == 200
    assert [item["barcode"] for item in result.json()] == ["012345678905"]
    assert normalize_barcode("012345678905") == "012345678905"
    assert normalize_barcode("400 8400 21450-4") == "4008400214504"


def test_unknown_and_invalid_barcodes_do_not_create_products(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "unknown.db")

    def provider(path, query=None):
        return {"status": 0}

    monkeypatch.setattr(main, "call_off", provider)
    with TestClient(app) as client:
        token = register(client, "unknown-user", "unknown-code-value-123456")
        invalid = client.get("/v1/products/barcode/4008400214508", headers=auth(token))
        unknown = client.get("/v1/products/barcode/4008400214504", headers=auth(token))

    assert invalid.status_code == 422
    assert invalid.json()["detail"]["code"] == "invalid_barcode"
    assert unknown.status_code == 404
    assert unknown.json()["detail"]["code"] == "product_not_found"
    with db() as connection:
        assert connection.execute("SELECT COUNT(*) FROM product_cache").fetchone()[0] == 0


def test_mapper_keeps_partial_product_visible_without_invalid_energy(monkeypatch):
    product = off_product()
    product["nutriments"]["energy-kcal_unit"] = "kj"
    del product["nutriments"]["carbohydrates_100g"]

    mapped = main.map_off_product(product)

    assert {value.key for value in mapped.nutrients} == {"protein", "fat", "fiber"}
    assert mapped.missing_core == ["carbohydrates", "energy"]


def test_expired_cache_refresh_does_not_change_saved_meal(tmp_path, monkeypatch):
    monkeypatch.setattr(main.settings, "database", tmp_path / "history.db")
    calls = 0

    def provider(path, query=None):
        nonlocal calls
        calls += 1
        return {"status": 1, "product": off_product(energy=370.5 if calls == 1 else 410)}

    monkeypatch.setattr(main, "call_off", provider)
    with TestClient(app) as client:
        token = register(client, "history-user", "history-code-value-123456")
        headers = auth(token)
        product = client.get("/v1/products/barcode/4008400214504", headers=headers).json()
        meal = client.post(
            "/v1/meals",
            headers=headers,
            json={
                "client_id": uid(),
                "local_day": "2026-07-31",
                "eaten_at": "2026-07-31T12:00:00+02:00",
                "timezone": "Europe/Berlin",
                "meal_type": "lunch",
                "name": product["name"],
                "capture_method": "barcode",
                "provenance_source": "open_food_facts",
                "external_reference": f"off:{product['barcode']}",
                "ingredients": [
                    {
                        "original_name": product["name"],
                        "amount": "50",
                        "unit": "g",
                        "nutrients": [
                            {
                                **nutrient,
                                "value": str(float(nutrient["value"]) / 2),
                                "basis": "portion",
                            }
                            for nutrient in product["nutrients"]
                        ],
                    }
                ],
            },
        )
        assert meal.status_code == 201, meal.text
        saved_energy = next(
            value["value"]
            for value in meal.json()["ingredients"][0]["nutrients"]
            if value["key"] == "energy"
        )
        with db() as connection:
            connection.execute(
                "UPDATE product_cache SET expires_at=? WHERE barcode=?",
                (iso(now() - timedelta(seconds=1)), product["barcode"]),
            )
            connection.commit()
        refreshed = client.get("/v1/products/barcode/4008400214504", headers=headers)
        historical = client.get(f"/v1/meals/{meal.json()['id']}", headers=headers)

    assert refreshed.status_code == 200
    assert (
        next(value["value"] for value in refreshed.json()["nutrients"] if value["key"] == "energy")
        == "410"
    )
    assert (
        next(
            value["value"]
            for value in historical.json()["ingredients"][0]["nutrients"]
            if value["key"] == "energy"
        )
        == saved_energy
    )
