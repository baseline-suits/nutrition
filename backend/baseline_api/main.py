from __future__ import annotations

import hashlib
import json
import os
import secrets
import sqlite3
import threading
import uuid
from collections import defaultdict, deque
from contextlib import contextmanager
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Annotated, Literal

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request, status
from pydantic import BaseModel, Field, field_validator

UTC = timezone.utc
PASSWORDS = PasswordHasher()
CORE_NUTRIENTS = {"energy", "protein", "carbohydrates", "fat", "saturated_fat"}
NUTRIENT_KEYS = CORE_NUTRIENTS | {
    "fiber", "sugar", "salt", "sodium", "calcium", "iron", "magnesium",
    "potassium", "vitamin_a", "vitamin_b12", "vitamin_c", "vitamin_d",
}
SOURCES = {"user", "ai_estimate", "open_food_facts", "derived", "manual"}
CAPTURE_METHODS = {"description", "camera", "gallery", "barcode", "search", "favorite", "manual", "ai"}
MEAL_TYPES = {"breakfast", "lunch", "dinner", "snack", "other"}
UNITS = {"g", "kg", "mg", "µg", "ml", "l", "kcal", "kj", "piece", "portion"}
BASES = {"portion", "100g", "100ml"}


def now() -> datetime:
    return datetime.now(UTC)


def iso(value: datetime) -> str:
    return value.astimezone(UTC).isoformat()


def digest(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def uid() -> str:
    return str(uuid.uuid4())


class Settings:
    def __init__(self) -> None:
        default = Path(__file__).resolve().parents[1] / "baseline.db"
        self.database = Path(os.environ.get("BASELINE_DATABASE", default))
        self.session_hours = int(os.environ.get("BASELINE_SESSION_HOURS", "720"))


settings = Settings()


@contextmanager
def db():
    connection = sqlite3.connect(settings.database, timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    try:
        yield connection
    finally:
        connection.close()


def migrate() -> None:
    settings.database.parent.mkdir(parents=True, exist_ok=True)
    migration = Path(__file__).resolve().parents[1] / "migrations" / "001_initial.sql"
    with db() as connection:
        connection.executescript(migration.read_text(encoding="utf-8"))
        connection.commit()


class ApiError(BaseModel):
    code: str
    message: str
    field: str | None = None


def fail(http_status: int, code: str, message: str, field: str | None = None):
    raise HTTPException(http_status, ApiError(code=code, message=message, field=field).model_dump())


class SlidingWindow:
    def __init__(self, attempts: int = 10, window_seconds: int = 60):
        self.attempts = attempts
        self.window_seconds = window_seconds
        self.entries: dict[str, deque[float]] = defaultdict(deque)
        self.lock = threading.Lock()

    def check(self, key: str) -> None:
        timestamp = now().timestamp()
        with self.lock:
            bucket = self.entries[key]
            while bucket and bucket[0] < timestamp - self.window_seconds:
                bucket.popleft()
            if len(bucket) >= self.attempts:
                fail(429, "rate_limited", "Zu viele Versuche. Bitte später erneut versuchen.")
            bucket.append(timestamp)


auth_limit = SlidingWindow()


class Credentials(BaseModel):
    username: str = Field(min_length=3, max_length=80, pattern=r"^[^\s]+$")
    password: str = Field(min_length=10, max_length=256)


class Registration(Credentials):
    access_code: str = Field(min_length=16, max_length=256)
    locale: Literal["de", "ru"] = "de"
    timezone: str = Field(default="UTC", min_length=1, max_length=80)


class SessionResponse(BaseModel):
    token: str
    expires_at: datetime
    onboarding_complete: bool
    user_id: str


class UserContext(BaseModel):
    id: str
    username: str
    locale: str
    timezone: str
    onboarding_complete: bool
    session_id: str


def create_session(connection: sqlite3.Connection, user_id: str) -> SessionResponse:
    token = secrets.token_urlsafe(48)
    expires = now() + timedelta(hours=settings.session_hours)
    connection.execute(
        "INSERT INTO sessions VALUES (?, ?, ?, ?, ?, NULL)",
        (uid(), user_id, digest(token), iso(now()), iso(expires)),
    )
    user = connection.execute("SELECT onboarding_complete FROM users WHERE id = ?", (user_id,)).fetchone()
    return SessionResponse(
        token=token, expires_at=expires, onboarding_complete=bool(user["onboarding_complete"]), user_id=user_id
    )


def current_user(authorization: Annotated[str | None, Header()] = None) -> UserContext:
    if not authorization or not authorization.startswith("Bearer "):
        fail(401, "authentication_required", "Anmeldung erforderlich.")
    token = authorization[7:]
    with db() as connection:
        row = connection.execute(
            """SELECT u.*, s.id session_id, s.expires_at, s.revoked_at
               FROM sessions s JOIN users u ON u.id = s.user_id
               WHERE s.token_hash = ?""",
            (digest(token),),
        ).fetchone()
    if not row or row["revoked_at"] or datetime.fromisoformat(row["expires_at"]) <= now():
        fail(401, "invalid_session", "Die Sitzung ist abgelaufen oder wurde widerrufen.")
    return UserContext(
        id=row["id"], username=row["username"], locale=row["locale"], timezone=row["timezone"],
        onboarding_complete=bool(row["onboarding_complete"]), session_id=row["session_id"],
    )


class NutrientInput(BaseModel):
    key: str
    value: Decimal = Field(ge=0)
    unit: str
    basis: Literal["portion", "100g", "100ml"] = "portion"
    source: str = "user"
    locked: bool = False
    accuracy: Literal["exact", "estimated", "unknown"] | None = None

    @field_validator("key")
    @classmethod
    def known_key(cls, value: str) -> str:
        if value not in NUTRIENT_KEYS:
            raise ValueError("Unbekannter Nährstoffschlüssel")
        return value

    @field_validator("unit")
    @classmethod
    def known_unit(cls, value: str) -> str:
        if value.lower() not in UNITS:
            raise ValueError("Unbekannte Einheit")
        return value.lower()

    @field_validator("source")
    @classmethod
    def known_source(cls, value: str) -> str:
        if value not in SOURCES:
            raise ValueError("Unbekannte Quelle")
        return value


class IngredientInput(BaseModel):
    original_name: str = Field(min_length=1, max_length=200)
    normalized_name: str | None = Field(default=None, max_length=200)
    preparation: str | None = Field(default=None, max_length=200)
    amount: Decimal = Field(gt=0)
    unit: str
    nutrients: list[NutrientInput] = Field(default_factory=list, max_length=100)

    @field_validator("unit")
    @classmethod
    def known_unit(cls, value: str) -> str:
        if value.lower() not in UNITS:
            raise ValueError("Unbekannte Einheit")
        return value.lower()


class MealInput(BaseModel):
    client_id: str = Field(min_length=1, max_length=100)
    local_day: date
    eaten_at: datetime
    timezone: str = Field(min_length=1, max_length=80)
    meal_type: str
    name: str = Field(min_length=1, max_length=200)
    note: str | None = Field(default=None, max_length=2000)
    capture_method: str
    ingredients: list[IngredientInput] = Field(min_length=1, max_length=100)
    provenance_source: str | None = None
    external_reference: str | None = Field(default=None, max_length=500)

    @field_validator("meal_type")
    @classmethod
    def known_meal_type(cls, value: str) -> str:
        if value not in MEAL_TYPES:
            raise ValueError("Unbekannter Mahlzeitentyp")
        return value

    @field_validator("capture_method")
    @classmethod
    def known_capture_method(cls, value: str) -> str:
        if value not in CAPTURE_METHODS:
            raise ValueError("Unbekannte Erfassungsart")
        return value


class MealUpdate(MealInput):
    version: int = Field(ge=1)


class NutrientOutput(NutrientInput):
    id: str


class IngredientOutput(IngredientInput):
    id: str
    nutrients: list[NutrientOutput]


class MealOutput(MealInput):
    id: str
    version: int
    ingredients: list[IngredientOutput]
    created_at: datetime
    updated_at: datetime


class DaySummary(BaseModel):
    local_day: date
    totals: dict[str, str]
    available: list[str]
    missing_core: list[str]
    meal_count: int


class ProfileInput(BaseModel):
    locale: Literal["de", "ru"]
    timezone: str = Field(min_length=1, max_length=80)
    birth_date: date | None = None
    biological_input: Literal["female", "male"] | None = None
    height_cm: Decimal | None = Field(default=None, ge=80, le=260)
    weight_kg: Decimal | None = Field(default=None, ge=25, le=400)
    activity_level: Literal["inactive", "sometimes", "active", "very_active"] | None = None
    goal_direction: Literal["maintain", "deficit", "surplus"] | None = None
    target_kcal: Decimal = Field(ge=800, le=10000)
    target_protein_g: Decimal = Field(ge=0, le=1000)
    target_carbs_g: Decimal = Field(ge=0, le=1500)
    target_fat_g: Decimal = Field(ge=0, le=500)
    manual: bool
    calculation: dict | None = None


def snapshot(payload: MealInput) -> str:
    return payload.model_dump_json()


def insert_meal(connection: sqlite3.Connection, user_id: str, payload: MealInput, meal_id: str | None = None) -> str:
    meal_id = meal_id or uid()
    stamp = iso(now())
    connection.execute(
        """INSERT INTO meals (id,user_id,client_id,local_day,eaten_at,timezone,meal_type,name,note,
           capture_method,version,deleted_at,created_at,updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?,1,NULL,?,?)""",
        (meal_id, user_id, payload.client_id, str(payload.local_day), iso(payload.eaten_at), payload.timezone,
         payload.meal_type, payload.name, payload.note, payload.capture_method, stamp, stamp),
    )
    for position, ingredient in enumerate(payload.ingredients):
        ingredient_id = uid()
        connection.execute(
            "INSERT INTO ingredients VALUES (?,?,?,?,?,?,?,?)",
            (ingredient_id, meal_id, position, ingredient.original_name, ingredient.normalized_name,
             ingredient.preparation, str(ingredient.amount), ingredient.unit, ),
        )
        for nutrient in ingredient.nutrients:
            connection.execute(
                "INSERT INTO nutrient_values VALUES (?,?,?,?,?,?,?,?,?,?)",
                (uid(), meal_id, ingredient_id, nutrient.key, str(nutrient.value), nutrient.unit,
                 nutrient.basis, nutrient.source, int(nutrient.locked or nutrient.source == "user"), nutrient.accuracy),
            )
    if payload.provenance_source:
        if payload.provenance_source not in SOURCES:
            fail(422, "validation_error", "Unbekannte Herkunft.", "provenance_source")
        connection.execute(
            "INSERT INTO provenance VALUES (?,?,?,?,?)",
            (uid(), meal_id, payload.provenance_source, payload.external_reference, stamp),
        )
    connection.execute(
        "INSERT INTO meal_revisions VALUES (?,?,?,?,?)", (uid(), meal_id, 1, snapshot(payload), stamp)
    )
    return meal_id


def load_meal(connection: sqlite3.Connection, user_id: str, meal_id: str) -> MealOutput:
    meal = connection.execute(
        "SELECT * FROM meals WHERE id=? AND user_id=? AND deleted_at IS NULL", (meal_id, user_id)
    ).fetchone()
    if not meal:
        fail(404, "not_found", "Mahlzeit nicht gefunden.")
    ingredients = []
    rows = connection.execute(
        """SELECT i.*, n.id nutrient_id,n.nutrient_key,n.value nutrient_value,n.unit nutrient_unit,
           n.basis,n.source,n.locked,n.accuracy
           FROM ingredients i LEFT JOIN nutrient_values n ON n.ingredient_id=i.id
           WHERE i.meal_id=? ORDER BY i.position,n.nutrient_key""", (meal_id,)
    ).fetchall()
    grouped: dict[str, dict] = {}
    for row in rows:
        if row["id"] not in grouped:
            grouped[row["id"]] = {
                "id": row["id"], "original_name": row["original_name"],
                "normalized_name": row["normalized_name"], "preparation": row["preparation"],
                "amount": Decimal(row["amount"]), "unit": row["unit"], "nutrients": [],
            }
        if row["nutrient_id"]:
            grouped[row["id"]]["nutrients"].append(NutrientOutput(
                id=row["nutrient_id"], key=row["nutrient_key"], value=Decimal(row["nutrient_value"]),
                unit=row["nutrient_unit"], basis=row["basis"], source=row["source"],
                locked=bool(row["locked"]), accuracy=row["accuracy"],
            ))
    provenance = connection.execute(
        "SELECT source,external_reference FROM provenance WHERE meal_id=? ORDER BY created_at DESC LIMIT 1", (meal_id,)
    ).fetchone()
    return MealOutput(
        id=meal["id"], client_id=meal["client_id"], local_day=date.fromisoformat(meal["local_day"]),
        eaten_at=datetime.fromisoformat(meal["eaten_at"]), timezone=meal["timezone"], meal_type=meal["meal_type"],
        name=meal["name"], note=meal["note"], capture_method=meal["capture_method"],
        ingredients=[IngredientOutput(**value) for value in grouped.values()],
        provenance_source=provenance["source"] if provenance else None,
        external_reference=provenance["external_reference"] if provenance else None,
        version=meal["version"], created_at=datetime.fromisoformat(meal["created_at"]),
        updated_at=datetime.fromisoformat(meal["updated_at"]),
    )


app = FastAPI(title="Baseline Nutrition API", version="1.0.0")


@app.on_event("startup")
def startup() -> None:
    migrate()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/auth/register", response_model=SessionResponse, status_code=201)
def register(payload: Registration, request: Request):
    auth_limit.check(f"register:{request.client.host if request.client else 'unknown'}")
    with db() as connection:
        try:
            connection.execute("BEGIN IMMEDIATE")
            code = connection.execute(
                "SELECT * FROM access_codes WHERE code_hash=?", (digest(payload.access_code),)
            ).fetchone()
            if not code or code["consumed_at"] or code["revoked_at"] or datetime.fromisoformat(code["expires_at"]) <= now():
                fail(400, "invalid_access_code", "Der Zugangscode ist ungültig oder nicht mehr verwendbar.")
            user_id = uid()
            connection.execute(
                "INSERT INTO users VALUES (?,?,?,?,?,?,?)",
                (user_id, payload.username, PASSWORDS.hash(payload.password), payload.locale,
                 payload.timezone, 0, iso(now())),
            )
            changed = connection.execute(
                """UPDATE access_codes SET consumed_at=?,consumed_by=?
                   WHERE id=? AND consumed_at IS NULL AND revoked_at IS NULL""",
                (iso(now()), user_id, code["id"]),
            ).rowcount
            if changed != 1:
                fail(409, "access_code_consumed", "Der Zugangscode wurde bereits verwendet.")
            result = create_session(connection, user_id)
            connection.commit()
            return result
        except sqlite3.IntegrityError:
            connection.rollback()
            fail(409, "registration_conflict", "Registrierung nicht möglich.")


@app.post("/v1/auth/login", response_model=SessionResponse)
def login(payload: Credentials, request: Request):
    auth_limit.check(f"login:{request.client.host if request.client else 'unknown'}:{payload.username.lower()}")
    with db() as connection:
        user = connection.execute("SELECT * FROM users WHERE username=?", (payload.username,)).fetchone()
        valid = False
        try:
            PASSWORDS.verify(user["password_hash"] if user else PASSWORDS.hash("dummy-password-value"), payload.password)
            valid = user is not None
        except VerifyMismatchError:
            pass
        if not valid:
            fail(401, "invalid_credentials", "Benutzerkennung oder Passwort ist falsch.")
        result = create_session(connection, user["id"])
        connection.commit()
        return result


@app.get("/v1/auth/session")
def session(user: Annotated[UserContext, Depends(current_user)]):
    return user


@app.post("/v1/auth/logout", status_code=204)
def logout(user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        connection.execute("UPDATE sessions SET revoked_at=? WHERE id=?", (iso(now()), user.session_id))
        connection.commit()


@app.post("/v1/auth/logout-all", status_code=204)
def logout_all(user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        connection.execute("UPDATE sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", (iso(now()), user.id))
        connection.commit()


@app.put("/v1/profile")
def save_profile(payload: ProfileInput, user: Annotated[UserContext, Depends(current_user)]):
    stamp = iso(now())
    formula = None if payload.manual else "mifflin-st-jeor-v1"
    calculation = json.dumps(payload.calculation, separators=(",", ":")) if payload.calculation else None
    with db() as connection:
        connection.execute(
            """INSERT INTO profiles VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(user_id) DO UPDATE SET birth_date=excluded.birth_date,
               biological_input=excluded.biological_input,height_cm=excluded.height_cm,
               weight_kg=excluded.weight_kg,weight_measured_at=excluded.weight_measured_at,
               activity_level=excluded.activity_level,goal_direction=excluded.goal_direction,
               formula_version=excluded.formula_version,calculation_json=excluded.calculation_json,
               target_kcal=excluded.target_kcal,target_protein_g=excluded.target_protein_g,
               target_carbs_g=excluded.target_carbs_g,target_fat_g=excluded.target_fat_g,
               targets_manual=excluded.targets_manual,updated_at=excluded.updated_at""",
            (user.id, str(payload.birth_date) if payload.birth_date else None, payload.biological_input,
             str(payload.height_cm) if payload.height_cm else None, str(payload.weight_kg) if payload.weight_kg else None,
             stamp if payload.weight_kg else None, payload.activity_level, payload.goal_direction, formula, calculation,
             str(payload.target_kcal), str(payload.target_protein_g), str(payload.target_carbs_g),
             str(payload.target_fat_g), int(payload.manual), stamp),
        )
        connection.execute(
            "UPDATE users SET locale=?,timezone=?,onboarding_complete=1 WHERE id=?",
            (payload.locale, payload.timezone, user.id),
        )
        connection.commit()
    return {"onboarding_complete": True, "formula_version": formula, "updated_at": stamp}


@app.get("/v1/profile")
def get_profile(user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        profile = connection.execute(
            "SELECT p.*,u.locale,u.timezone FROM users u LEFT JOIN profiles p ON p.user_id=u.id WHERE u.id=?", (user.id,)
        ).fetchone()
    if not profile or not profile["target_kcal"]:
        fail(404, "not_found", "Profil nicht gefunden.")
    return dict(profile)


@app.post("/v1/meals", response_model=MealOutput, status_code=201)
def create_meal(payload: MealInput, user: Annotated[UserContext, Depends(current_user)],
                idempotency_key: Annotated[str | None, Header(alias="Idempotency-Key")] = None):
    client_id = idempotency_key or payload.client_id
    payload = payload.model_copy(update={"client_id": client_id})
    with db() as connection:
        existing = connection.execute(
            "SELECT id FROM meals WHERE user_id=? AND client_id=?", (user.id, client_id)
        ).fetchone()
        if existing:
            return load_meal(connection, user.id, existing["id"])
        try:
            connection.execute("BEGIN")
            meal_id = insert_meal(connection, user.id, payload)
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        return load_meal(connection, user.id, meal_id)


@app.get("/v1/meals/{meal_id}", response_model=MealOutput)
def get_meal(meal_id: str, user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        return load_meal(connection, user.id, meal_id)


@app.put("/v1/meals/{meal_id}", response_model=MealOutput)
def update_meal(meal_id: str, payload: MealUpdate, user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        existing = connection.execute(
            "SELECT * FROM meals WHERE id=? AND user_id=? AND deleted_at IS NULL", (meal_id, user.id)
        ).fetchone()
        if not existing:
            fail(404, "not_found", "Mahlzeit nicht gefunden.")
        if existing["version"] != payload.version:
            fail(409, "version_conflict", "Die Mahlzeit wurde zwischenzeitlich geändert.")
        connection.execute("BEGIN")
        connection.execute("DELETE FROM ingredients WHERE meal_id=?", (meal_id,))
        connection.execute("DELETE FROM provenance WHERE meal_id=?", (meal_id,))
        next_version = payload.version + 1
        stamp = iso(now())
        connection.execute(
            """UPDATE meals SET client_id=?,local_day=?,eaten_at=?,timezone=?,meal_type=?,name=?,
               note=?,capture_method=?,version=?,updated_at=? WHERE id=?""",
            (payload.client_id, str(payload.local_day), iso(payload.eaten_at), payload.timezone, payload.meal_type,
             payload.name, payload.note, payload.capture_method, next_version, stamp, meal_id),
        )
        for position, ingredient in enumerate(payload.ingredients):
            ingredient_id = uid()
            connection.execute(
                "INSERT INTO ingredients VALUES (?,?,?,?,?,?,?,?)",
                (ingredient_id, meal_id, position, ingredient.original_name, ingredient.normalized_name,
                 ingredient.preparation, str(ingredient.amount), ingredient.unit),
            )
            for nutrient in ingredient.nutrients:
                connection.execute(
                    "INSERT INTO nutrient_values VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (uid(), meal_id, ingredient_id, nutrient.key, str(nutrient.value), nutrient.unit,
                     nutrient.basis, "user", 1, nutrient.accuracy),
                )
        connection.execute(
            "INSERT INTO meal_revisions VALUES (?,?,?,?,?)",
            (uid(), meal_id, next_version, snapshot(payload), stamp),
        )
        connection.commit()
        return load_meal(connection, user.id, meal_id)


@app.delete("/v1/meals/{meal_id}", status_code=204)
def delete_meal(meal_id: str, user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        connection.execute(
            "UPDATE meals SET deleted_at=?,updated_at=? WHERE id=? AND user_id=? AND deleted_at IS NULL",
            (iso(now()), iso(now()), meal_id, user.id),
        )
        connection.commit()


@app.get("/v1/days/{local_day}/meals", response_model=list[MealOutput])
def list_meals(local_day: date, user: Annotated[UserContext, Depends(current_user)],
               limit: int = Query(50, ge=1, le=100), offset: int = Query(0, ge=0)):
    with db() as connection:
        rows = connection.execute(
            """WITH selected AS (
                   SELECT * FROM meals WHERE user_id=? AND local_day=? AND deleted_at IS NULL
                   ORDER BY eaten_at,meal_type,id LIMIT ? OFFSET ?
               )
               SELECT m.*,i.id ingredient_id,i.position,i.original_name,i.normalized_name,i.preparation,
                      i.amount,i.unit ingredient_unit,n.id nutrient_id,n.nutrient_key,
                      n.value nutrient_value,n.unit nutrient_unit,n.basis,n.source,n.locked,n.accuracy,
                      p.source provenance_source,p.external_reference
               FROM selected m
               JOIN ingredients i ON i.meal_id=m.id
               LEFT JOIN nutrient_values n ON n.ingredient_id=i.id
               LEFT JOIN provenance p ON p.id=(
                   SELECT id FROM provenance WHERE meal_id=m.id ORDER BY created_at DESC LIMIT 1
               )
               ORDER BY m.eaten_at,m.meal_type,m.id,i.position,n.nutrient_key""",
            (user.id, str(local_day), limit, offset),
        ).fetchall()
    meals: dict[str, dict] = {}
    for row in rows:
        entry = meals.setdefault(row["id"], {"row": row, "ingredients": {}})
        ingredient = entry["ingredients"].setdefault(row["ingredient_id"], {
            "id": row["ingredient_id"], "original_name": row["original_name"],
            "normalized_name": row["normalized_name"], "preparation": row["preparation"],
            "amount": Decimal(row["amount"]), "unit": row["ingredient_unit"], "nutrients": [],
        })
        if row["nutrient_id"]:
            ingredient["nutrients"].append(NutrientOutput(
                id=row["nutrient_id"], key=row["nutrient_key"], value=Decimal(row["nutrient_value"]),
                unit=row["nutrient_unit"], basis=row["basis"], source=row["source"],
                locked=bool(row["locked"]), accuracy=row["accuracy"],
            ))
    result = []
    for entry in meals.values():
        meal = entry["row"]
        result.append(MealOutput(
            id=meal["id"], client_id=meal["client_id"], local_day=date.fromisoformat(meal["local_day"]),
            eaten_at=datetime.fromisoformat(meal["eaten_at"]), timezone=meal["timezone"],
            meal_type=meal["meal_type"], name=meal["name"], note=meal["note"],
            capture_method=meal["capture_method"],
            ingredients=[IngredientOutput(**value) for value in entry["ingredients"].values()],
            provenance_source=meal["provenance_source"], external_reference=meal["external_reference"],
            version=meal["version"], created_at=datetime.fromisoformat(meal["created_at"]),
            updated_at=datetime.fromisoformat(meal["updated_at"]),
        ))
    return result


@app.get("/v1/days/{local_day}/summary", response_model=DaySummary)
def day_summary(local_day: date, user: Annotated[UserContext, Depends(current_user)]):
    with db() as connection:
        rows = connection.execute(
            """SELECT n.nutrient_key,n.value FROM nutrient_values n JOIN meals m ON m.id=n.meal_id
               WHERE m.user_id=? AND m.local_day=? AND m.deleted_at IS NULL AND n.basis='portion'""",
            (user.id, str(local_day)),
        ).fetchall()
        count = connection.execute(
            "SELECT COUNT(*) count FROM meals WHERE user_id=? AND local_day=? AND deleted_at IS NULL",
            (user.id, str(local_day)),
        ).fetchone()["count"]
    totals: dict[str, Decimal] = defaultdict(Decimal)
    for row in rows:
        totals[row["nutrient_key"]] += Decimal(row["value"])
    formatted = {key: str(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)) for key, value in totals.items()}
    available = sorted(formatted)
    return DaySummary(local_day=local_day, totals=formatted, available=available,
                      missing_core=sorted(CORE_NUTRIENTS - set(available)), meal_count=count)


@app.post("/v1/meals/{meal_id}/duplicate", response_model=MealOutput, status_code=201)
def duplicate_meal(meal_id: str, user: Annotated[UserContext, Depends(current_user)],
                   idempotency_key: Annotated[str, Header(alias="Idempotency-Key")]):
    with db() as connection:
        original = load_meal(connection, user.id, meal_id)
        existing = connection.execute(
            "SELECT id FROM meals WHERE user_id=? AND client_id=?", (user.id, idempotency_key)
        ).fetchone()
        if existing:
            return load_meal(connection, user.id, existing["id"])
        values = original.model_dump(exclude={"id", "version", "created_at", "updated_at"})
        values["client_id"] = idempotency_key
        payload = MealInput(**values)
        new_id = insert_meal(connection, user.id, payload)
        connection.commit()
        return load_meal(connection, user.id, new_id)
