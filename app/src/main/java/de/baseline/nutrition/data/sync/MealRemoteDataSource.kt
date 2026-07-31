package de.baseline.nutrition.data.sync

import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.network.ApiClient

interface MealRemoteDataSource {
    suspend fun create(payload: MealPayload, idempotencyKey: String): MealDto
    suspend fun update(mealId: String, payload: MealPayload, idempotencyKey: String): MealDto
    suspend fun delete(mealId: String, idempotencyKey: String)
    suspend fun get(mealId: String): MealDto
}

class ApiMealRemoteDataSource(private val api: ApiClient) : MealRemoteDataSource {
    override suspend fun create(payload: MealPayload, idempotencyKey: String): MealDto =
        api.request(
            "/v1/meals",
            "POST",
            payload,
            authenticated = true,
            headers = mapOf("Idempotency-Key" to idempotencyKey),
        )

    override suspend fun update(
        mealId: String,
        payload: MealPayload,
        idempotencyKey: String,
    ): MealDto = api.request(
        "/v1/meals/$mealId",
        "PUT",
        payload,
        authenticated = true,
        headers = mapOf("Idempotency-Key" to idempotencyKey),
    )

    override suspend fun delete(mealId: String, idempotencyKey: String) {
        api.request<Unit, Unit>(
            "/v1/meals/$mealId",
            "DELETE",
            authenticated = true,
            headers = mapOf("Idempotency-Key" to idempotencyKey),
        )
    }

    override suspend fun get(mealId: String): MealDto =
        api.request<Unit, MealDto>("/v1/meals/$mealId", "GET", authenticated = true)
}
