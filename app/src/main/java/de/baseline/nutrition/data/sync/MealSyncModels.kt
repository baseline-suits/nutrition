package de.baseline.nutrition.data.sync

import de.baseline.nutrition.data.diary.DaySummaryDto
import de.baseline.nutrition.data.diary.DiaryTargets
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MealOperationType {
    @SerialName("create")
    Create,

    @SerialName("update")
    Update,

    @SerialName("delete")
    Delete,
}

@Serializable
enum class MealOperationStatus {
    @SerialName("pending")
    Pending,

    @SerialName("syncing")
    Syncing,

    @SerialName("failed_retryable")
    FailedRetryable,

    @SerialName("failed_permanent")
    FailedPermanent,

    @SerialName("conflict")
    Conflict,

    @SerialName("synced")
    Synced,
}

@Serializable
data class QueuedMealOperation(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("resource_id") val resourceId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    val type: MealOperationType,
    val payload: MealPayload? = null,
    val status: MealOperationStatus = MealOperationStatus.Pending,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("attempt_count") val attemptCount: Int = 0,
    @SerialName("next_attempt_at") val nextAttemptAt: Long = 0,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("depends_on") val dependsOn: String? = null,
    @SerialName("server_meal") val serverMeal: MealDto? = null,
)

@Serializable
data class MealSyncSnapshot(
    val operations: List<QueuedMealOperation> = emptyList(),
    @SerialName("cached_meals") val cachedMeals: List<MealDto> = emptyList(),
    @SerialName("cached_days") val cachedDays: Set<String> = emptySet(),
    @SerialName("day_summaries") val daySummaries: Map<String, DaySummaryDto> = emptyMap(),
    val targets: DiaryTargets? = null,
    val favorites: List<FavoriteDto> = emptyList(),
    @SerialName("favorites_cached") val favoritesCached: Boolean = false,
    @SerialName("last_success_at") val lastSuccessAt: Long? = null,
)

data class MealSyncInfo(
    val operationId: String,
    val status: MealOperationStatus,
    val attempts: Int,
    val errorCode: String? = null,
)

data class MealSyncOverview(
    val pending: Int = 0,
    val failed: Int = 0,
    val conflicts: Int = 0,
    val lastSuccessAt: Long? = null,
    val authRequired: Boolean = false,
)

data class MealSyncUiState(
    val byMealId: Map<String, MealSyncInfo> = emptyMap(),
    val operations: List<QueuedMealOperation> = emptyList(),
    val overview: MealSyncOverview = MealSyncOverview(),
)

data class SyncRunResult(
    val hasRetryableWork: Boolean,
    val authRequired: Boolean,
)

class MealQueueFullException : IllegalStateException("meal_queue_full")

interface MealQueueStore {
    fun read(userId: String): MealSyncSnapshot
    fun write(userId: String, snapshot: MealSyncSnapshot)
    fun clear(userId: String)
}

interface MealSyncScheduler {
    fun schedule(userId: String)
}

fun interface SyncTimeSource {
    fun nowMillis(): Long
}
