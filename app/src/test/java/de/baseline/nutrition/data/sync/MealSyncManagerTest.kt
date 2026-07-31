package de.baseline.nutrition.data.sync

import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.network.ApiException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSyncManagerTest {
    @Test
    fun offlineCreateSurvivesManagerRestartAndSyncsExactlyOnce() = runTest {
        val store = InMemoryQueueStore()
        val remote = FakeRemote().apply { failure = IOException("offline") }
        val scheduler = FakeScheduler()
        val clock = FakeClock(1_000)
        val first = manager(store, remote, scheduler, clock)
        val local = first.enqueueSave(payload("offline-create"), null)

        val failed = first.sync(force = true)
        assertTrue(failed.hasRetryableWork)
        assertEquals(
            MealOperationStatus.FailedRetryable,
            first.uiState().byMealId[local.id]?.status,
        )

        val restarted = manager(store, remote, scheduler, clock)
        val cached = restarted.cachedMeals("2026-07-31")
        assertTrue(cached.available)
        assertEquals("offline-create", cached.meals.single().clientId)

        remote.failure = null
        restarted.sync(force = true)
        restarted.sync(force = true)

        assertEquals(1, remote.created.size)
        assertEquals("offline-create", restarted.cachedMeals("2026-07-31").meals.single().clientId)
        assertTrue(scheduler.users.contains("user-a"))
    }

    @Test
    fun retryAfterLostResponseUsesSameCreateKey() = runTest {
        val remote = FakeRemote().apply { loseCreateResponseOnce = true }
        val manager = manager(InMemoryQueueStore(), remote, FakeScheduler(), FakeClock(2_000))
        manager.enqueueSave(payload("lost-response"), null)

        manager.sync(force = true)
        assertEquals(1, remote.created.size)
        assertEquals(1, remote.createCalls)

        manager.sync(force = true)
        assertEquals(1, remote.created.size)
        assertEquals(2, remote.createCalls)
        assertEquals(0, manager.uiState().overview.pending)
    }

    @Test
    fun createEditAndDeleteBeforeFirstAttemptLeavesNoServerEntry() = runTest {
        val remote = FakeRemote()
        val manager = manager(InMemoryQueueStore(), remote, FakeScheduler(), FakeClock(3_000))
        val local = manager.enqueueSave(payload("local-chain"), null)
        manager.enqueueSave(payload("local-chain").copy(name = "Geändert"), local.id)
        manager.enqueueDelete(local.id)
        manager.sync(force = true)

        assertTrue(remote.created.isEmpty())
        assertTrue(manager.cachedMeals("2026-07-31").meals.isEmpty())
        assertTrue(manager.uiState().operations.isEmpty())
    }

    @Test
    fun conflictNeverOverwritesServerAndCanApplyLocalVersionExplicitly() = runTest {
        val remote = FakeRemote()
        val store = InMemoryQueueStore()
        val manager = manager(store, remote, FakeScheduler(), FakeClock(4_000))
        val server = payload("server-client")
            .copy(name = "Serverstand")
            .asMealDto("server-1", version = 2, updatedAt = 1_000)
        remote.created["server-client"] = server
        manager.cacheRemoteMeals("2026-07-31", listOf(server))
        manager.enqueueSave(
            server.asPayload().copy(name = "Lokale Änderung", version = 1),
            server.id,
        )

        manager.sync(force = true)

        val conflict = manager.uiState().operations.single()
        assertEquals(MealOperationStatus.Conflict, conflict.status)
        assertEquals("Serverstand", remote.created["server-client"]?.name)
        assertNotNull(conflict.serverMeal)

        manager.applyMine(conflict.id)

        assertEquals("Lokale Änderung", remote.created["server-client"]?.name)
        assertEquals(3, remote.created["server-client"]?.version)
        assertEquals(0, manager.uiState().overview.conflicts)
    }

    @Test
    fun backoffPermanentErrorsAuthAndAccountsStaySeparated() = runTest {
        val store = InMemoryQueueStore()
        val remote = FakeRemote().apply { failure = IOException("offline") }
        val clock = FakeClock(10_000)
        val user = MutableUser("user-a")
        val manager = MealSyncManager(
            store,
            remote,
            user::value,
            FakeScheduler(),
            clock,
        )
        manager.enqueueSave(payload("backoff"), null)
        manager.sync()
        val callsAfterFailure = remote.createCalls
        manager.sync()
        assertEquals(callsAfterFailure, remote.createCalls)
        clock.value += 30_000
        manager.sync()
        assertTrue(remote.createCalls > callsAfterFailure)

        user.current = "user-b"
        assertFalse(manager.cachedMeals("2026-07-31").available)
        assertTrue(manager.uiState().operations.isEmpty())

        user.current = "user-a"
        remote.failure = ApiException(422, "validation_error")
        manager.sync(force = true)
        assertEquals(1, manager.uiState().overview.failed)
        val callsAfterPermanent = remote.createCalls
        manager.sync(force = true)
        assertEquals(callsAfterPermanent, remote.createCalls)

        val permanent = manager.uiState().operations.single()
        manager.discard(permanent.id)
        manager.enqueueSave(payload("auth-pause"), null)
        remote.failure = ApiException(401, "invalid_session")
        val auth = manager.sync(force = true)
        assertTrue(auth.authRequired)
        assertTrue(manager.uiState().overview.authRequired)
    }

    @Test
    fun interruptedSyncingStateIsRecoveredAfterRestart() = runTest {
        val store = InMemoryQueueStore()
        val operation = QueuedMealOperation(
            id = "operation-1",
            userId = "user-a",
            resourceId = MealSyncManager.localMealId("restart"),
            idempotencyKey = "restart",
            type = MealOperationType.Create,
            payload = payload("restart"),
            status = MealOperationStatus.Syncing,
            createdAt = 1,
            updatedAt = 1,
        )
        store.write("user-a", MealSyncSnapshot(operations = listOf(operation)))
        val remote = FakeRemote()
        val manager = manager(store, remote, FakeScheduler(), FakeClock(20_000))

        manager.sync(force = true)

        assertEquals(1, remote.created.size)
        assertEquals(0, manager.uiState().overview.pending)
    }

    private fun manager(
        store: MealQueueStore,
        remote: FakeRemote,
        scheduler: MealSyncScheduler,
        clock: SyncTimeSource,
    ) = MealSyncManager(store, remote, { "user-a" }, scheduler, clock)

    private fun payload(clientId: String) = MealPayload(
        clientId = clientId,
        localDay = "2026-07-31",
        eatenAt = "2026-07-31T12:00:00+02:00",
        timezone = "Europe/Berlin",
        mealType = "lunch",
        name = "Mahlzeit",
        nutrients = listOf(
            NutrientDto(key = "energy", value = "500", unit = "kcal"),
        ),
    )
}

private class InMemoryQueueStore : MealQueueStore {
    private val values = mutableMapOf<String, MealSyncSnapshot>()

    override fun read(userId: String): MealSyncSnapshot =
        values[userId] ?: MealSyncSnapshot()

    override fun write(userId: String, snapshot: MealSyncSnapshot) {
        values[userId] = snapshot
    }

    override fun clear(userId: String) {
        values.remove(userId)
    }
}

private class FakeScheduler : MealSyncScheduler {
    val users = mutableListOf<String>()
    override fun schedule(userId: String) {
        users += userId
    }
}

private class FakeClock(var value: Long) : SyncTimeSource {
    override fun nowMillis(): Long = value
}

private class MutableUser(var current: String?) {
    fun value(): String? = current
}

private class FakeRemote : MealRemoteDataSource {
    val created = mutableMapOf<String, MealDto>()
    private val updateResponses = mutableMapOf<String, MealDto>()
    private val deleteResponses = mutableSetOf<String>()
    var failure: Exception? = null
    var loseCreateResponseOnce = false
    var createCalls = 0

    override suspend fun create(payload: MealPayload, idempotencyKey: String): MealDto {
        createCalls += 1
        val existing = created[idempotencyKey]
        if (existing != null) return existing
        failure?.let { throw it }
        val result = payload.copy(clientId = idempotencyKey)
            .asMealDto("server-${created.size + 1}", version = 1, updatedAt = 1_000)
        created[idempotencyKey] = result
        if (loseCreateResponseOnce) {
            loseCreateResponseOnce = false
            throw IOException("response lost")
        }
        return result
    }

    override suspend fun update(
        mealId: String,
        payload: MealPayload,
        idempotencyKey: String,
    ): MealDto {
        updateResponses[idempotencyKey]?.let { return it }
        failure?.let { throw it }
        val current = created.values.firstOrNull { it.id == mealId }
            ?: throw ApiException(404, "not_found")
        if (payload.version != current.version) throw ApiException(409, "version_conflict")
        val result = payload.asMealDto(mealId, current.version + 1, updatedAt = 2_000)
        created[current.clientId] = result
        updateResponses[idempotencyKey] = result
        return result
    }

    override suspend fun delete(mealId: String, idempotencyKey: String) {
        if (!deleteResponses.add(idempotencyKey)) return
        failure?.let { throw it }
        created.entries.removeAll { it.value.id == mealId }
    }

    override suspend fun get(mealId: String): MealDto =
        created.values.firstOrNull { it.id == mealId }
            ?: throw ApiException(404, "not_found")
}
