package de.baseline.nutrition.data.diary

import de.baseline.nutrition.data.network.ApiClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NutrientDto(
    val id: String? = null,
    val key: String,
    val value: String,
    val unit: String,
    val basis: String = "portion",
    val source: String = "user",
    val locked: Boolean = true,
    val accuracy: String? = "exact",
)

@Serializable
data class IngredientDto(
    val id: String? = null,
    @SerialName("original_name") val name: String,
    @SerialName("normalized_name") val normalizedName: String? = null,
    val preparation: String? = null,
    val amount: String,
    val unit: String,
    val nutrients: List<NutrientDto> = emptyList(),
)

@Serializable
data class MealPayload(
    @SerialName("client_id") val clientId: String,
    @SerialName("local_day") val localDay: String,
    @SerialName("eaten_at") val eatenAt: String,
    val timezone: String,
    @SerialName("meal_type") val mealType: String,
    val name: String,
    val note: String? = null,
    @SerialName("capture_method") val captureMethod: String = "manual",
    val ingredients: List<IngredientDto> = emptyList(),
    val nutrients: List<NutrientDto> = emptyList(),
    @SerialName("provenance_source") val provenanceSource: String? = "user",
    @SerialName("external_reference") val externalReference: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    val version: Int? = null,
)

@Serializable
data class MealDto(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("local_day") val localDay: String,
    @SerialName("eaten_at") val eatenAt: String,
    val timezone: String,
    @SerialName("meal_type") val mealType: String,
    val name: String,
    val note: String? = null,
    @SerialName("capture_method") val captureMethod: String,
    val ingredients: List<IngredientDto> = emptyList(),
    val nutrients: List<NutrientDto> = emptyList(),
    @SerialName("provenance_source") val provenanceSource: String? = null,
    @SerialName("external_reference") val externalReference: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    val version: Int,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class DaySummaryDto(
    @SerialName("local_day") val localDay: String,
    val totals: Map<String, String>,
    val available: List<String>,
    @SerialName("missing_core") val missingCore: List<String>,
    @SerialName("meal_count") val mealCount: Int,
)

@Serializable
data class DiaryTargets(
    @SerialName("target_kcal") val energy: String,
    @SerialName("target_protein_g") val protein: String,
    @SerialName("target_carbs_g") val carbohydrates: String,
    @SerialName("target_fat_g") val fat: String,
    @SerialName("targets_manual") val manual: Int = 0,
)

@Serializable
data class PrivateFoodPayload(
    val name: String,
    val brand: String? = null,
    @SerialName("default_amount") val defaultAmount: String,
    val unit: String,
    val basis: String,
    val nutrients: List<NutrientDto>,
    val version: Int? = null,
)

@Serializable
data class PrivateFoodDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    @SerialName("default_amount") val defaultAmount: String,
    val unit: String,
    val basis: String,
    val nutrients: List<NutrientDto>,
    val version: Int,
)

class DiaryRepository(private val api: ApiClient) {
    suspend fun meals(day: String): List<MealDto> =
        api.request<Unit, List<MealDto>>("/v1/days/$day/meals", "GET", authenticated = true)

    suspend fun summary(day: String): DaySummaryDto =
        api.request<Unit, DaySummaryDto>("/v1/days/$day/summary", "GET", authenticated = true)

    suspend fun targets(): DiaryTargets =
        api.request<Unit, DiaryTargets>("/v1/profile", "GET", authenticated = true)

    suspend fun save(payload: MealPayload, mealId: String? = null): MealDto =
        if (mealId == null) {
            api.request("/v1/meals", "POST", payload, authenticated = true)
        } else {
            api.request("/v1/meals/$mealId", "PUT", payload, authenticated = true)
        }

    suspend fun delete(mealId: String) {
        api.request<Unit, Unit>("/v1/meals/$mealId", "DELETE", authenticated = true)
    }

    suspend fun duplicate(mealId: String): MealDto = api.request(
        "/v1/meals/$mealId/duplicate", "POST", Unit, authenticated = true,
        headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
    )

    suspend fun privateFoods(query: String = ""): List<PrivateFoodDto> {
        val suffix = if (query.isBlank()) "" else "?query=" +
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return api.request<Unit, List<PrivateFoodDto>>("/v1/private-foods$suffix", "GET", authenticated = true)
    }

    suspend fun savePrivateFood(payload: PrivateFoodPayload, id: String? = null): PrivateFoodDto =
        if (id == null) api.request("/v1/private-foods", "POST", payload, authenticated = true)
        else api.request("/v1/private-foods/$id", "PUT", payload, authenticated = true)

    suspend fun duplicatePrivateFood(id: String): PrivateFoodDto =
        api.request("/v1/private-foods/$id/duplicate", "POST", Unit, authenticated = true)

    suspend fun deletePrivateFood(id: String) {
        api.request<Unit, Unit>("/v1/private-foods/$id", "DELETE", authenticated = true)
    }
}
