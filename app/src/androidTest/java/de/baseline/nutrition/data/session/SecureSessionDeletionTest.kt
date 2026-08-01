package de.baseline.nutrition.data.session

import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.auth.HttpAuthRepository
import de.baseline.nutrition.data.health.EncryptedHealthSyncStore
import de.baseline.nutrition.data.health.HealthSyncSnapshot
import de.baseline.nutrition.data.health.StoredHealthCursor
import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.sync.EncryptedMealQueueStore
import de.baseline.nutrition.data.sync.MealSyncScheduler
import de.baseline.nutrition.data.sync.MealSyncSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSessionDeletionTest {
    @Test
    fun accountDeletionMarkerSurvivesAmbiguousUnauthorizedResponse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        store.clear()
        store.writeSession("session-token", "deleting-user")
        store.markAccountDeletion("deleting-user")

        store.clearPreservingAccountDeletion()

        assertNull(store.readToken())
        assertNull(store.readUserId())
        assertEquals("deleting-user", store.readAccountDeletionUserId())
        store.clear()
    }

    @Test
    fun logoutStopsUserJobsAndClearsAllPrivateLocalData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val userId = "logout-cleanup-user"
        val session = SecureSessionStore(context)
        val meals = EncryptedMealQueueStore(context)
        val health = EncryptedHealthSyncStore(context)
        session.clear()
        meals.clear(userId)
        health.clear(userId)
        session.writeSession("session-token", userId)
        meals.write(userId, MealSyncSnapshot(cachedDays = setOf("2026-08-01")))
        health.write(
            userId,
            HealthSyncSnapshot(
                cursors = mapOf("steps" to StoredHealthCursor("cursor", "2026-08-01T12:00:00Z")),
            ),
        )
        val scheduler = RecordingScheduler()
        val repository = HttpAuthRepository(
            api = ApiClient({ "http://127.0.0.1:1/" }, session),
            store = session,
            syncScheduler = scheduler,
            localDataCleaner = {
                meals.clear(it)
                health.clear(it)
            },
        )

        repository.logout(false)

        assertEquals(listOf(userId), scheduler.cancelled)
        assertNull(session.readToken())
        assertNull(session.readUserId())
        assertTrue(meals.read(userId).cachedDays.isEmpty())
        assertTrue(health.read(userId).cursors.isEmpty())
    }
}

private class RecordingScheduler : MealSyncScheduler {
    val cancelled = mutableListOf<String>()
    override fun schedule(userId: String) = Unit
    override fun cancel(userId: String) {
        cancelled += userId
    }
}
