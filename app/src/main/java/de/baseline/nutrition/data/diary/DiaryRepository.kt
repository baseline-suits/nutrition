package de.baseline.nutrition.data.diary

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.session.SecureSessionStore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
data class FavoriteDto(
    val id: String,
    @SerialName("original_meal_id") val originalMealId: String? = null,
    @SerialName("display_name") val displayName: String,
    val meal: MealPayload,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

@Serializable
data class FavoriteCreatePayload(
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class FavoriteUpdatePayload(
    @SerialName("display_name") val displayName: String,
    val meal: MealPayload? = null,
)

@Serializable
data class ReuseRequestPayload(
    @SerialName("client_id") val clientId: String,
    @SerialName("local_day") val localDay: String,
    @SerialName("eaten_at") val eatenAt: String,
    val timezone: String,
    @SerialName("meal_type") val mealType: String,
)

@Serializable
data class DaySummaryDto(
    @SerialName("local_day") val localDay: String,
    val totals: Map<String, String>,
    val available: List<String>,
    @SerialName("missing_core") val missingCore: List<String>,
    val coverage: Map<String, Int> = emptyMap(),
    @SerialName("meal_count") val mealCount: Int,
    val targets: DailyBudgetDto? = null,
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
data class DailyBudgetDto(
    val timezone: String,
    @SerialName("target_kcal") val energy: String,
    @SerialName("target_protein_g") val protein: String,
    @SerialName("target_carbs_g") val carbohydrates: String,
    @SerialName("target_fat_g") val fat: String,
    @SerialName("targets_manual") val manual: Boolean,
    @SerialName("weight_kg") val weightKg: String? = null,
    @SerialName("activity_level") val activityLevel: String? = null,
    @SerialName("calculation_version") val calculationVersion: String,
)

fun DailyBudgetDto.toDiaryTargets() = DiaryTargets(
    energy = energy,
    protein = protein,
    carbohydrates = carbohydrates,
    fat = fat,
    manual = if (manual) 1 else 0,
)

@Serializable
data class HistoryDayDto(
    @SerialName("local_day") val localDay: String,
    val status: String,
    val totals: Map<String, String>,
    val coverage: Map<String, Int>,
    @SerialName("meal_count") val mealCount: Int,
    val targets: DailyBudgetDto? = null,
)

@Serializable
data class HistoryAggregateDto(
    @SerialName("tracked_days") val trackedDays: Int,
    @SerialName("complete_days") val completeDays: Int,
    @SerialName("partial_days") val partialDays: Int,
    val averages: Map<String, String>,
    @SerialName("average_denominators") val averageDenominators: Map<String, Int>,
    @SerialName("target_averages") val targetAverages: Map<String, String>,
    @SerialName("target_denominators") val targetDenominators: Map<String, Int>,
    @SerialName("goal_percentages") val goalPercentages: Map<String, String>,
    @SerialName("goal_denominators") val goalDenominators: Map<String, Int>,
)

@Serializable
data class HistoryWeekDto(
    val start: String,
    val end: String,
    @SerialName("tracked_days") val trackedDays: Int,
    @SerialName("complete_days") val completeDays: Int,
    @SerialName("partial_days") val partialDays: Int,
    val averages: Map<String, String>,
    @SerialName("average_denominators") val averageDenominators: Map<String, Int>,
    @SerialName("target_averages") val targetAverages: Map<String, String>,
    @SerialName("target_denominators") val targetDenominators: Map<String, Int>,
    @SerialName("goal_percentages") val goalPercentages: Map<String, String>,
    @SerialName("goal_denominators") val goalDenominators: Map<String, Int>,
)

@Serializable
data class HistoryResponseDto(
    val start: String,
    val end: String,
    @SerialName("total_days") val totalDays: Int,
    val days: List<HistoryDayDto>,
    val summary: HistoryAggregateDto,
    val weeks: List<HistoryWeekDto>,
)

data class HistoryLoad(val data: HistoryResponseDto, val cached: Boolean)

interface HistoryDataSource {
    suspend fun history(days: Int): HistoryLoad
}

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

class DiaryRepository(
    private val api: ApiClient,
    private val sessionStore: SecureSessionStore? = null,
) : HistoryDataSource {
    private val historyCache = ConcurrentHashMap<String, HistoryResponseDto>()

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

    suspend fun favorites(query: String = "", sort: String = "recent"): List<FavoriteDto> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return api.request<Unit, List<FavoriteDto>>(
            "/v1/favorites?query=$encoded&sort=$sort",
            "GET",
            authenticated = true,
        )
    }

    suspend fun createFavorite(mealId: String, displayName: String): FavoriteDto =
        api.request(
            "/v1/meals/$mealId/favorite",
            "POST",
            FavoriteCreatePayload(displayName),
            authenticated = true,
        )

    suspend fun updateFavorite(
        id: String,
        displayName: String,
        meal: MealPayload? = null,
    ): FavoriteDto =
        api.request(
            "/v1/favorites/$id",
            "PUT",
            FavoriteUpdatePayload(displayName, meal),
            authenticated = true,
        )

    suspend fun deleteFavorite(id: String) {
        api.request<Unit, Unit>("/v1/favorites/$id", "DELETE", authenticated = true)
    }

    suspend fun recentMeals(limit: Int = 30, offset: Int = 0): List<MealDto> =
        api.request<Unit, List<MealDto>>(
            "/v1/recent-meals?limit=$limit&offset=$offset",
            "GET",
            authenticated = true,
        )

    suspend fun favoriteDraft(id: String, payload: ReuseRequestPayload): MealPayload =
        api.request("/v1/favorites/$id/draft", "POST", payload, authenticated = true)

    suspend fun recentDraft(id: String, payload: ReuseRequestPayload): MealPayload =
        api.request("/v1/meals/$id/draft", "POST", payload, authenticated = true)

    override suspend fun history(days: Int): HistoryLoad {
        require(days in 1..100)
        val userId = sessionStore?.readUserId()
        val cacheKey = userId?.let { "$it:$days" }
        return try {
            val response = api.request<Unit, HistoryResponseDto>(
                "/v1/nutrition/history?days=$days&limit=$days",
                "GET",
                authenticated = true,
            )
            if (cacheKey != null) historyCache[cacheKey] = response
            HistoryLoad(response, cached = false)
        } catch (error: Exception) {
            if (error is ApiException && error.status == 401) throw error
            val cached = cacheKey?.let(historyCache::get) ?: throw error
            HistoryLoad(cached, cached = true)
        }
    }
}
