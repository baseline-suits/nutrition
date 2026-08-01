package de.baseline.nutrition.data.sync

import de.baseline.nutrition.data.diary.DaySummaryDto
import de.baseline.nutrition.data.diary.DiaryTargets
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.network.ApiException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CachedMeals(val available: Boolean, val meals: List<MealDto>)

class MealSyncManager(
    private val store: MealQueueStore,
    private val remote: MealRemoteDataSource,
    private val currentUserId: () -> String?,
    private val scheduler: MealSyncScheduler,
    private val timeSource: SyncTimeSource = SyncTimeSource(System::currentTimeMillis),
) {
    private val mutex = Mutex()

    suspend fun cacheRemoteMeals(day: String, meals: List<MealDto>): List<MealDto> =
        mutex.withLock {
            val userId = requireUser()
            val snapshot = normalized(store.read(userId))
            val retained = snapshot.cachedMeals.filterNot { it.localDay == day }
            val updated = snapshot.copy(
                cachedMeals = boundedMeals(retained + meals),
                cachedDays = snapshot.cachedDays + day,
            )
            store.write(userId, updated)
            projectedMeals(updated, day)
        }

    suspend fun cachedMeals(day: String): CachedMeals = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        val hasLocal = activeOperations(snapshot).any { it.payload?.localDay == day }
        CachedMeals(
            available = day in snapshot.cachedDays || hasLocal,
            meals = projectedMeals(snapshot, day),
        )
    }

    suspend fun cacheSummary(summary: DaySummaryDto) = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        store.write(
            userId,
            snapshot.copy(
                daySummaries = (snapshot.daySummaries + (summary.localDay to summary))
                    .toList()
                    .takeLast(60)
                    .toMap(),
            ),
        )
    }

    suspend fun cachedSummary(day: String): DaySummaryDto? = mutex.withLock {
        val userId = requireUser()
        normalized(store.read(userId)).daySummaries[day]
    }

    suspend fun cacheTargets(targets: DiaryTargets) = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        store.write(userId, snapshot.copy(targets = targets))
    }

    suspend fun cachedTargets(): DiaryTargets? = mutex.withLock {
        normalized(store.read(requireUser())).targets
    }

    suspend fun cacheFavorites(favorites: List<FavoriteDto>) = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        store.write(
            userId,
            snapshot.copy(favorites = favorites.take(100), favoritesCached = true),
        )
    }

    suspend fun cachedFavorites(): Pair<Boolean, List<FavoriteDto>> = mutex.withLock {
        val snapshot = normalized(store.read(requireUser()))
        snapshot.favoritesCached to snapshot.favorites
    }

    suspend fun cacheRecent(meals: List<MealDto>) = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        val byId = (snapshot.cachedMeals + meals).associateBy(MealDto::id)
        store.write(userId, snapshot.copy(cachedMeals = boundedMeals(byId.values.toList())))
    }

    suspend fun recent(limit: Int, offset: Int): List<MealDto> = mutex.withLock {
        val snapshot = normalized(store.read(requireUser()))
        projectedMeals(snapshot)
            .sortedByDescending(MealDto::eatenAt)
            .drop(offset)
            .take(limit)
    }

    suspend fun meal(mealId: String): MealDto? = mutex.withLock {
        projectedMeals(normalized(store.read(requireUser()))).firstOrNull { it.id == mealId }
    }

    suspend fun mealByClientId(clientId: String): MealDto? = mutex.withLock {
        projectedMeals(normalized(store.read(requireUser())))
            .firstOrNull { it.clientId == clientId }
    }

    suspend fun enqueueSave(payload: MealPayload, mealId: String?): MealDto = mutex.withLock {
        val userId = requireUser()
        var snapshot = normalized(store.read(userId))
        val resourceId = mealId ?: localMealId(payload.clientId)
        val active = activeOperations(snapshot).filter { it.resourceId == resourceId }
        val create = active.firstOrNull { it.type == MealOperationType.Create }
        val terminal = active.lastOrNull {
            it.status == MealOperationStatus.FailedPermanent ||
                it.status == MealOperationStatus.Conflict
        }
        val timestamp = timeSource.nowMillis()
        if (terminal != null && terminal.type != MealOperationType.Delete) {
            val server = terminal.serverMeal
            val correctedPayload = if (server != null) {
                payload.copy(clientId = server.clientId, version = server.version)
            } else {
                payload
            }
            val correctedResource = server?.id ?: resourceId
            val correctedType = if (terminal.type == MealOperationType.Create && server == null) {
                MealOperationType.Create
            } else {
                MealOperationType.Update
            }
            val removed = dependentIds(snapshot.operations, terminal.id)
            val replacement = QueuedMealOperation(
                id = UUID.randomUUID().toString(),
                userId = userId,
                resourceId = correctedResource,
                idempotencyKey = if (correctedType == MealOperationType.Create) {
                    correctedPayload.clientId
                } else {
                    UUID.randomUUID().toString()
                },
                type = correctedType,
                payload = correctedPayload,
                createdAt = timestamp,
                updatedAt = timestamp,
                dependsOn = terminal.dependsOn,
            )
            snapshot = compact(
                snapshot.copy(
                    operations = snapshot.operations.filterNot { it.id in removed } + replacement,
                    cachedMeals = server?.let {
                        boundedMeals(snapshot.cachedMeals.filterNot { meal -> meal.id == it.id } + it)
                    } ?: snapshot.cachedMeals,
                ),
            )
            store.write(userId, snapshot)
            scheduler.schedule(userId)
            return@withLock correctedPayload.asMealDto(
                id = correctedResource,
                version = correctedPayload.version ?: 0,
                updatedAt = timestamp,
            )
        }
        val mergeable = when {
            create != null && create.attemptCount == 0 -> create
            else -> active.lastOrNull {
                it.type == MealOperationType.Update && it.attemptCount == 0
            }
        }
        val operations = if (mergeable != null) {
            snapshot.operations.map { operation ->
                if (operation.id == mergeable.id) {
                    operation.copy(
                        payload = payload,
                        status = MealOperationStatus.Pending,
                        updatedAt = timestamp,
                        nextAttemptAt = 0,
                        errorCode = null,
                        serverMeal = null,
                    )
                } else {
                    operation
                }
            }
        } else {
            ensureCapacity(snapshot)
            val type = if (mealId == null) MealOperationType.Create else MealOperationType.Update
            val dependency = active.lastOrNull()?.id
            snapshot.operations + QueuedMealOperation(
                id = UUID.randomUUID().toString(),
                userId = userId,
                resourceId = resourceId,
                idempotencyKey = if (type == MealOperationType.Create) {
                    payload.clientId
                } else {
                    UUID.randomUUID().toString()
                },
                type = type,
                payload = payload,
                createdAt = timestamp,
                updatedAt = timestamp,
                dependsOn = dependency,
            )
        }
        snapshot = compact(snapshot.copy(operations = operations))
        store.write(userId, snapshot)
        scheduler.schedule(userId)
        payload.asMealDto(
            id = resourceId,
            version = payload.version ?: 0,
            updatedAt = timestamp,
        )
    }

    suspend fun enqueueDelete(mealId: String) = mutex.withLock {
        val userId = requireUser()
        var snapshot = normalized(store.read(userId))
        val active = activeOperations(snapshot).filter { it.resourceId == mealId }
        if (active.any { it.type == MealOperationType.Delete }) return@withLock
        val create = active.firstOrNull { it.type == MealOperationType.Create }
        if (create != null && create.attemptCount == 0) {
            val removed = dependentIds(snapshot.operations, create.id)
            snapshot = snapshot.copy(
                operations = snapshot.operations.filterNot { it.id in removed },
                cachedMeals = snapshot.cachedMeals.filterNot { it.id == mealId },
            )
            store.write(userId, compact(snapshot))
            return@withLock
        }
        val removableUpdates = active.filter {
            it.type == MealOperationType.Update && it.attemptCount == 0
        }.map(QueuedMealOperation::id).toSet()
        val retained = snapshot.operations.filterNot { it.id in removableUpdates }
        val dependency = active.lastOrNull { it.id !in removableUpdates }?.id
        ensureCapacity(snapshot)
        val timestamp = timeSource.nowMillis()
        snapshot = snapshot.copy(
            operations = retained + QueuedMealOperation(
                id = UUID.randomUUID().toString(),
                userId = userId,
                resourceId = mealId,
                idempotencyKey = UUID.randomUUID().toString(),
                type = MealOperationType.Delete,
                createdAt = timestamp,
                updatedAt = timestamp,
                dependsOn = dependency,
            ),
        )
        store.write(userId, compact(snapshot))
        scheduler.schedule(userId)
    }

    suspend fun sync(force: Boolean = false): SyncRunResult = mutex.withLock {
        val userId = currentUserId() ?: return@withLock SyncRunResult(
            hasRetryableWork = false,
            authRequired = true,
        )
        var snapshot = normalized(store.read(userId))
        store.write(userId, snapshot)
        val attempted = mutableSetOf<String>()
        while (true) {
            val now = timeSource.nowMillis()
            val operation = snapshot.operations
                .sortedBy(QueuedMealOperation::createdAt)
                .firstOrNull { candidate ->
                    candidate.id !in attempted &&
                    candidate.status in retryableStatuses &&
                        (force || candidate.nextAttemptAt <= now) &&
                        dependencyReady(snapshot, candidate)
                } ?: break
            attempted += operation.id
            snapshot = replaceOperation(
                snapshot,
                operation.copy(
                    status = MealOperationStatus.Syncing,
                    updatedAt = now,
                    errorCode = null,
                ),
            )
            store.write(userId, snapshot)
            try {
                val response = when (operation.type) {
                    MealOperationType.Create -> remote.create(
                        requireNotNull(operation.payload),
                        operation.idempotencyKey,
                    )
                    MealOperationType.Update -> remote.update(
                        operation.resourceId,
                        requireNotNull(operation.payload),
                        operation.idempotencyKey,
                    )
                    MealOperationType.Delete -> {
                        remote.delete(operation.resourceId, operation.idempotencyKey)
                        null
                    }
                }
                snapshot = complete(snapshot, operation, response, now)
                store.write(userId, snapshot)
            } catch (error: Exception) {
                val attempts = operation.attemptCount + 1
                when {
                    error is ApiException && error.status == 401 -> {
                        snapshot = replaceOperation(
                            snapshot,
                            operation.copy(
                                status = MealOperationStatus.Pending,
                                attemptCount = attempts,
                                nextAttemptAt = 0,
                                errorCode = "auth_required",
                                updatedAt = now,
                            ),
                        )
                        store.write(userId, snapshot)
                        return@withLock SyncRunResult(
                            hasRetryableWork = true,
                            authRequired = true,
                        )
                    }
                    error is ApiException &&
                        error.status == 409 &&
                        error.code == "version_conflict" &&
                        operation.type == MealOperationType.Update -> {
                        val serverMeal = runCatching { remote.get(operation.resourceId) }.getOrNull()
                        snapshot = replaceOperation(
                            snapshot,
                            operation.copy(
                                status = MealOperationStatus.Conflict,
                                attemptCount = attempts,
                                errorCode = error.code,
                                serverMeal = serverMeal,
                                updatedAt = now,
                            ),
                        )
                    }
                    error is ApiException &&
                        error.status in 400..499 &&
                        error.status !in setOf(408, 429) -> {
                        snapshot = replaceOperation(
                            snapshot,
                            operation.copy(
                                status = MealOperationStatus.FailedPermanent,
                                attemptCount = attempts,
                                errorCode = error.code,
                                updatedAt = now,
                            ),
                        )
                    }
                    else -> {
                        snapshot = replaceOperation(
                            snapshot,
                            operation.copy(
                                status = MealOperationStatus.FailedRetryable,
                                attemptCount = attempts,
                                nextAttemptAt = now + backoffMillis(attempts),
                                errorCode = (error as? ApiException)?.code ?: "network_error",
                                updatedAt = now,
                            ),
                        )
                    }
                }
                store.write(userId, snapshot)
            }
        }
        val pending = snapshot.operations.any { it.status in retryableStatuses }
        SyncRunResult(hasRetryableWork = pending, authRequired = false)
    }

    suspend fun retry(operationId: String): SyncRunResult {
        mutex.withLock {
            val userId = requireUser()
            val snapshot = normalized(store.read(userId))
            val operation = snapshot.operations.firstOrNull { it.id == operationId } ?: return@withLock
            store.write(
                userId,
                replaceOperation(
                    snapshot,
                    operation.copy(
                        status = MealOperationStatus.Pending,
                        attemptCount = 0,
                        nextAttemptAt = 0,
                        errorCode = null,
                        serverMeal = null,
                        updatedAt = timeSource.nowMillis(),
                    ),
                ),
            )
        }
        return sync(force = true)
    }

    suspend fun discard(operationId: String) = mutex.withLock {
        val userId = requireUser()
        val snapshot = normalized(store.read(userId))
        val operation = snapshot.operations.firstOrNull { it.id == operationId } ?: return@withLock
        val removed = dependentIds(snapshot.operations, operation.id)
        val restored = operation.serverMeal?.let { server ->
            snapshot.cachedMeals.filterNot { it.id == server.id } + server
        } ?: snapshot.cachedMeals
        store.write(
            userId,
            compact(
                snapshot.copy(
                    operations = snapshot.operations.filterNot { it.id in removed },
                    cachedMeals = boundedMeals(restored),
                ),
            ),
        )
    }

    suspend fun keepServer(operationId: String) = discard(operationId)

    suspend fun applyMine(operationId: String): SyncRunResult {
        mutex.withLock {
            val userId = requireUser()
            val snapshot = normalized(store.read(userId))
            val conflict = snapshot.operations.firstOrNull {
                it.id == operationId && it.status == MealOperationStatus.Conflict
            } ?: return@withLock
            val server = conflict.serverMeal ?: return@withLock
            val payload = conflict.payload?.copy(
                clientId = server.clientId,
                version = server.version,
            ) ?: return@withLock
            val timestamp = timeSource.nowMillis()
            val replacement = conflict.copy(
                id = UUID.randomUUID().toString(),
                resourceId = server.id,
                idempotencyKey = UUID.randomUUID().toString(),
                payload = payload,
                status = MealOperationStatus.Pending,
                createdAt = timestamp,
                updatedAt = timestamp,
                attemptCount = 0,
                nextAttemptAt = 0,
                errorCode = null,
                dependsOn = null,
                serverMeal = null,
            )
            val removed = dependentIds(snapshot.operations, conflict.id)
            store.write(
                userId,
                snapshot.copy(
                    operations = snapshot.operations.filterNot { it.id in removed } + replacement,
                    cachedMeals = boundedMeals(
                        snapshot.cachedMeals.filterNot { it.id == server.id } + server,
                    ),
                ),
            )
            scheduler.schedule(userId)
        }
        return sync(force = true)
    }

    suspend fun uiState(): MealSyncUiState = mutex.withLock {
        val userId = currentUserId() ?: return@withLock MealSyncUiState()
        val snapshot = normalized(store.read(userId))
        val active = activeOperations(snapshot)
        val info = active
            .sortedBy(QueuedMealOperation::createdAt)
            .associate { operation ->
                operation.resourceId to MealSyncInfo(
                    operationId = operation.id,
                    status = operation.status,
                    attempts = operation.attemptCount,
                    errorCode = operation.errorCode,
                )
            }
        MealSyncUiState(
            byMealId = info,
            operations = active,
            overview = MealSyncOverview(
                pending = active.count { it.status in retryableStatuses },
                failed = active.count { it.status == MealOperationStatus.FailedPermanent },
                conflicts = active.count { it.status == MealOperationStatus.Conflict },
                lastSuccessAt = snapshot.lastSuccessAt,
                authRequired = active.any { it.errorCode == "auth_required" },
            ),
        )
    }

    suspend fun clearUserData(userId: String) = mutex.withLock {
        store.clear(userId)
    }

    private fun complete(
        snapshot: MealSyncSnapshot,
        operation: QueuedMealOperation,
        response: MealDto?,
        timestamp: Long,
    ): MealSyncSnapshot {
        var operations = snapshot.operations.map { candidate ->
            if (candidate.id == operation.id) {
                operation.copy(
                    resourceId = response?.id ?: operation.resourceId,
                    payload = null,
                    status = MealOperationStatus.Synced,
                    attemptCount = operation.attemptCount + 1,
                    nextAttemptAt = 0,
                    errorCode = null,
                    serverMeal = response,
                    updatedAt = timestamp,
                )
            } else {
                candidate
            }
        }
        if (response != null) {
            operations = operations.map { candidate ->
                if (candidate.dependsOn == operation.id) {
                    candidate.copy(
                        resourceId = response.id,
                        payload = candidate.payload?.copy(
                            clientId = response.clientId,
                            version = response.version,
                        ),
                        dependsOn = null,
                        status = MealOperationStatus.Pending,
                        nextAttemptAt = 0,
                        updatedAt = timestamp,
                    )
                } else {
                    candidate
                }
            }
        }
        val cached = if (operation.type == MealOperationType.Delete) {
            snapshot.cachedMeals.filterNot { it.id == operation.resourceId }
        } else if (response != null) {
            snapshot.cachedMeals.filterNot {
                it.id == operation.resourceId || it.id == response.id || it.clientId == response.clientId
            } + response
        } else {
            snapshot.cachedMeals
        }
        return compact(
            snapshot.copy(
                operations = operations,
                cachedMeals = boundedMeals(cached),
                cachedDays = response?.let { snapshot.cachedDays + it.localDay }
                    ?: snapshot.cachedDays,
                lastSuccessAt = timestamp,
            ),
        )
    }

    private fun projectedMeals(
        snapshot: MealSyncSnapshot,
        day: String? = null,
    ): List<MealDto> {
        val meals = snapshot.cachedMeals.associateBy(MealDto::id).toMutableMap()
        activeOperations(snapshot)
            .sortedBy(QueuedMealOperation::createdAt)
            .forEach { operation ->
                when (operation.type) {
                    MealOperationType.Create, MealOperationType.Update -> {
                        val payload = operation.payload ?: return@forEach
                        val baseVersion = meals[operation.resourceId]?.version ?: payload.version ?: 0
                        val photoDeleted = meals[operation.resourceId]?.photoDeleted ?: false
                        meals[operation.resourceId] = payload.asMealDto(
                            id = operation.resourceId,
                            version = baseVersion,
                            updatedAt = operation.updatedAt,
                            photoDeleted = photoDeleted,
                        )
                    }
                    MealOperationType.Delete -> meals.remove(operation.resourceId)
                }
            }
        return meals.values
            .asSequence()
            .filter { day == null || it.localDay == day }
            .sortedWith(compareBy(MealDto::eatenAt, MealDto::id))
            .toList()
    }

    private fun normalized(snapshot: MealSyncSnapshot): MealSyncSnapshot {
        val now = timeSource.nowMillis()
        return snapshot.copy(
            operations = snapshot.operations.map { operation ->
                if (operation.status == MealOperationStatus.Syncing) {
                    operation.copy(
                        status = MealOperationStatus.FailedRetryable,
                        nextAttemptAt = 0,
                        errorCode = "interrupted",
                        updatedAt = now,
                    )
                } else {
                    operation
                }
            },
        )
    }

    private fun compact(snapshot: MealSyncSnapshot): MealSyncSnapshot {
        val synced = snapshot.operations
            .filter { it.status == MealOperationStatus.Synced }
            .sortedByDescending(QueuedMealOperation::updatedAt)
            .take(20)
        val active = snapshot.operations.filterNot { it.status == MealOperationStatus.Synced }
        return snapshot.copy(operations = (active + synced).sortedBy(QueuedMealOperation::createdAt))
    }

    private fun activeOperations(snapshot: MealSyncSnapshot): List<QueuedMealOperation> =
        snapshot.operations.filterNot { it.status == MealOperationStatus.Synced }

    private fun replaceOperation(
        snapshot: MealSyncSnapshot,
        replacement: QueuedMealOperation,
    ): MealSyncSnapshot = snapshot.copy(
        operations = snapshot.operations.map {
            if (it.id == replacement.id) replacement else it
        },
    )

    private fun dependencyReady(
        snapshot: MealSyncSnapshot,
        operation: QueuedMealOperation,
    ): Boolean {
        val dependency = operation.dependsOn ?: return true
        return snapshot.operations.firstOrNull { it.id == dependency }?.status ==
            MealOperationStatus.Synced
    }

    private fun dependentIds(
        operations: List<QueuedMealOperation>,
        rootId: String,
    ): Set<String> {
        val result = mutableSetOf(rootId)
        var changed: Boolean
        do {
            changed = false
            operations.forEach { operation ->
                if (operation.dependsOn in result && result.add(operation.id)) changed = true
            }
        } while (changed)
        return result
    }

    private fun boundedMeals(meals: List<MealDto>): List<MealDto> =
        meals.associateBy(MealDto::id).values
            .sortedByDescending(MealDto::updatedAt)
            .take(200)

    private fun ensureCapacity(snapshot: MealSyncSnapshot) {
        if (activeOperations(snapshot).size >= 100) throw MealQueueFullException()
    }

    private fun requireUser(): String = requireNotNull(currentUserId()) { "authenticated_user" }

    private fun backoffMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        return (30_000L * (1L shl exponent)).coerceAtMost(6 * 60 * 60 * 1_000L)
    }

    companion object {
        fun localMealId(clientId: String) = "local:$clientId"

        private val retryableStatuses = setOf(
            MealOperationStatus.Pending,
            MealOperationStatus.FailedRetryable,
        )
    }
}

fun MealPayload.asMealDto(
    id: String,
    version: Int,
    updatedAt: Long,
    photoDeleted: Boolean = false,
): MealDto = MealDto(
    id = id,
    clientId = clientId,
    localDay = localDay,
    eatenAt = eatenAt,
    timezone = timezone,
    mealType = mealType,
    name = name,
    note = note,
    captureMethod = captureMethod,
    ingredients = ingredients,
    nutrients = nutrients,
    provenanceSource = provenanceSource,
    externalReference = externalReference,
    attachmentId = attachmentId,
    photoDeleted = photoDeleted,
    version = version,
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
)

fun MealDto.asPayload(clientId: String = this.clientId): MealPayload = MealPayload(
    clientId = clientId,
    localDay = localDay,
    eatenAt = eatenAt,
    timezone = timezone,
    mealType = mealType,
    name = name,
    note = note,
    captureMethod = captureMethod,
    ingredients = ingredients,
    nutrients = nutrients,
    provenanceSource = provenanceSource,
    externalReference = externalReference,
    attachmentId = attachmentId,
    version = version,
)
