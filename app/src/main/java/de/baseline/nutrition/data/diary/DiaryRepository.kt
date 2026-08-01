package de.baseline.nutrition.data.diary

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.data.sync.MealSyncManager
import de.baseline.nutrition.data.sync.MealSyncUiState
import de.baseline.nutrition.data.sync.SyncRunResult
import de.baseline.nutrition.data.sync.asPayload
import java.math.BigDecimal
import java.math.RoundingMode
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
    @SerialName("photo_deleted") val photoDeleted: Boolean = false,
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
    private val mealSyncManager: MealSyncManager? = null,
) : HistoryDataSource {
    private val historyCache = ConcurrentHashMap<String, HistoryResponseDto>()

    suspend fun meals(day: String): List<MealDto> {
        val manager = mealSyncManager
            ?: return api.request<Unit, List<MealDto>>(
                "/v1/days/$day/meals",
                "GET",
                authenticated = true,
            )
        return try {
            val remote = api.request<Unit, List<MealDto>>(
                "/v1/days/$day/meals",
                "GET",
                authenticated = true,
            )
            manager.cacheRemoteMeals(day, remote)
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            val cached = manager.cachedMeals(day)
            if (!cached.available) throw error
            cached.meals
        }
    }

    suspend fun summary(day: String): DaySummaryDto {
        val manager = mealSyncManager
            ?: return api.request<Unit, DaySummaryDto>(
                "/v1/days/$day/summary",
                "GET",
                authenticated = true,
            )
        return try {
            api.request<Unit, DaySummaryDto>(
                "/v1/days/$day/summary",
                "GET",
                authenticated = true,
            ).also { manager.cacheSummary(it) }
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            val cachedMeals = manager.cachedMeals(day)
            if (!cachedMeals.available) throw error
            localSummary(day, cachedMeals.meals, manager.cachedSummary(day))
        }
    }

    suspend fun targets(): DiaryTargets {
        val manager = mealSyncManager
            ?: return api.request<Unit, DiaryTargets>(
                "/v1/profile",
                "GET",
                authenticated = true,
            )
        return try {
            api.request<Unit, DiaryTargets>("/v1/profile", "GET", authenticated = true)
                .also { manager.cacheTargets(it) }
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            manager.cachedTargets() ?: throw error
        }
    }

    suspend fun save(payload: MealPayload, mealId: String? = null): MealDto {
        val manager = mealSyncManager
        if (manager == null) {
            val key = if (mealId == null) payload.clientId else UUID.randomUUID().toString()
            return if (mealId == null) {
                api.request(
                    "/v1/meals",
                    "POST",
                    payload,
                    authenticated = true,
                    headers = mapOf("Idempotency-Key" to key),
                )
            } else {
                api.request(
                    "/v1/meals/$mealId",
                    "PUT",
                    payload,
                    authenticated = true,
                    headers = mapOf("Idempotency-Key" to key),
                )
            }
        }
        val local = manager.enqueueSave(payload, mealId)
        manager.sync(force = true)
        return manager.mealByClientId(payload.clientId) ?: local
    }

    suspend fun delete(mealId: String) {
        val manager = mealSyncManager
        if (manager == null) {
            api.request<Unit, Unit>(
                "/v1/meals/$mealId",
                "DELETE",
                authenticated = true,
                headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
            )
            return
        }
        manager.enqueueDelete(mealId)
        manager.sync(force = true)
    }

    suspend fun deletePhoto(attachmentId: String) {
        api.request<Unit, Unit>(
            "/v1/uploads/$attachmentId",
            "DELETE",
            authenticated = true,
        )
    }

    suspend fun duplicate(mealId: String): MealDto {
        val manager = mealSyncManager
            ?: return api.request(
                "/v1/meals/$mealId/duplicate",
                "POST",
                Unit,
                authenticated = true,
                headers = mapOf("Idempotency-Key" to UUID.randomUUID().toString()),
            )
        val original = manager.meal(mealId)
            ?: api.request<Unit, MealDto>("/v1/meals/$mealId", "GET", authenticated = true)
        return save(
            original.asPayload(UUID.randomUUID().toString()).copy(
                attachmentId = null,
                version = null,
            ),
        )
    }

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
        val manager = mealSyncManager
            ?: return api.request<Unit, List<FavoriteDto>>(
                "/v1/favorites?query=$encoded&sort=$sort",
                "GET",
                authenticated = true,
            )
        return try {
            api.request<Unit, List<FavoriteDto>>(
                "/v1/favorites?query=$encoded&sort=$sort",
                "GET",
                authenticated = true,
            ).also { manager.cacheFavorites(it) }
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            val (available, cached) = manager.cachedFavorites()
            if (!available) throw error
            cached.filter {
                query.isBlank() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.meal.name.contains(query, ignoreCase = true)
            }.let { values ->
                if (sort == "name") values.sortedBy { it.displayName.lowercase() }
                else values.sortedByDescending { it.lastUsedAt ?: it.updatedAt }
            }
        }
    }

    suspend fun createFavorite(mealId: String, displayName: String): FavoriteDto {
        val favorite: FavoriteDto = api.request(
            "/v1/meals/$mealId/favorite",
            "POST",
            FavoriteCreatePayload(displayName),
            authenticated = true,
        )
        mealSyncManager?.let { manager ->
            val cached = manager.cachedFavorites().second
            manager.cacheFavorites(cached.filterNot { it.id == favorite.id } + favorite)
        }
        return favorite
    }

    suspend fun updateFavorite(
        id: String,
        displayName: String,
        meal: MealPayload? = null,
    ): FavoriteDto {
        val favorite: FavoriteDto = api.request(
            "/v1/favorites/$id",
            "PUT",
            FavoriteUpdatePayload(displayName, meal),
            authenticated = true,
        )
        mealSyncManager?.let { manager ->
            val cached = manager.cachedFavorites().second
            manager.cacheFavorites(cached.filterNot { it.id == favorite.id } + favorite)
        }
        return favorite
    }

    suspend fun deleteFavorite(id: String) {
        api.request<Unit, Unit>("/v1/favorites/$id", "DELETE", authenticated = true)
        mealSyncManager?.let { manager ->
            manager.cacheFavorites(manager.cachedFavorites().second.filterNot { it.id == id })
        }
    }

    suspend fun recentMeals(limit: Int = 30, offset: Int = 0): List<MealDto> {
        val manager = mealSyncManager
            ?: return api.request<Unit, List<MealDto>>(
                "/v1/recent-meals?limit=$limit&offset=$offset",
                "GET",
                authenticated = true,
            )
        return try {
            api.request<Unit, List<MealDto>>(
                "/v1/recent-meals?limit=$limit&offset=$offset",
                "GET",
                authenticated = true,
            ).also { manager.cacheRecent(it) }
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            manager.recent(limit, offset)
        }
    }

    suspend fun favoriteDraft(id: String, payload: ReuseRequestPayload): MealPayload {
        return try {
            api.request("/v1/favorites/$id/draft", "POST", payload, authenticated = true)
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            val favorite = mealSyncManager?.cachedFavorites()?.second
                ?.firstOrNull { it.id == id } ?: throw error
            favorite.meal.reused(payload)
        }
    }

    suspend fun recentDraft(id: String, payload: ReuseRequestPayload): MealPayload {
        return try {
            api.request("/v1/meals/$id/draft", "POST", payload, authenticated = true)
        } catch (error: Exception) {
            if (!canUseCache(error)) throw error
            val meal = mealSyncManager?.meal(id) ?: throw error
            meal.asPayload().reused(payload)
        }
    }

    suspend fun syncPending(): SyncRunResult =
        mealSyncManager?.sync() ?: SyncRunResult(false, false)

    suspend fun syncNow(): SyncRunResult =
        mealSyncManager?.sync(force = true) ?: SyncRunResult(false, false)

    suspend fun syncUiState(): MealSyncUiState =
        mealSyncManager?.uiState() ?: MealSyncUiState()

    suspend fun retrySync(operationId: String): SyncRunResult =
        mealSyncManager?.retry(operationId) ?: SyncRunResult(false, false)

    suspend fun discardSync(operationId: String) {
        mealSyncManager?.discard(operationId)
    }

    suspend fun keepServer(operationId: String) {
        mealSyncManager?.keepServer(operationId)
    }

    suspend fun applyMine(operationId: String): SyncRunResult =
        mealSyncManager?.applyMine(operationId) ?: SyncRunResult(false, false)

    suspend fun clearLocalData(userId: String) {
        mealSyncManager?.clearUserData(userId)
        historyCache.keys.removeAll { it.startsWith("$userId:") }
    }

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

    private fun canUseCache(error: Exception): Boolean =
        error !is ApiException || error.status != 401

    private fun localSummary(
        day: String,
        meals: List<MealDto>,
        cached: DaySummaryDto?,
    ): DaySummaryDto {
        val totals = linkedMapOf<String, BigDecimal>()
        val coverage = linkedMapOf<String, MutableSet<String>>()
        meals.forEach { meal ->
            (meal.nutrients + meal.ingredients.flatMap { it.nutrients })
                .filter { it.basis == "portion" }
                .forEach { nutrient ->
                    val value = nutrient.value.toBigDecimalOrNull() ?: return@forEach
                    totals[nutrient.key] = (totals[nutrient.key] ?: BigDecimal.ZERO) + value
                    coverage.getOrPut(nutrient.key, ::mutableSetOf).add(meal.id)
                }
        }
        val formatted = totals.mapValues { (_, value) ->
            value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
        val core = setOf("energy", "protein", "carbohydrates", "fat", "saturated_fat")
        return DaySummaryDto(
            localDay = day,
            totals = formatted,
            available = formatted.keys.sorted(),
            missingCore = (core - formatted.keys).sorted(),
            coverage = coverage.mapValues { it.value.size },
            mealCount = meals.size,
            targets = cached?.targets,
        )
    }
}

private fun MealPayload.reused(request: ReuseRequestPayload): MealPayload = copy(
    clientId = request.clientId,
    localDay = request.localDay,
    eatenAt = request.eatenAt,
    timezone = request.timezone,
    mealType = request.mealType,
    attachmentId = null,
    version = null,
)
