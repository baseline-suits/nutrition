package de.baseline.nutrition.data.diary

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.auth.HttpAuthRepository
import de.baseline.nutrition.data.health.ApiHealthSyncRemoteDataSource
import de.baseline.nutrition.data.health.DefaultHealthRepository
import de.baseline.nutrition.data.health.EncryptedHealthSyncStore
import de.baseline.nutrition.data.health.HealthConnectGateway
import de.baseline.nutrition.data.health.HealthRecordPage
import de.baseline.nutrition.data.health.SecureHealthSettingsStorage
import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.profile.ProfileRepository
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalorieBudgetApiIntegrationTest {
    @Test
    fun deviceHealthSyncUpdatesBackendOwnedDynamicBudget() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("calorieBudgetIntegration") == "true")
        val accessCode = requireNotNull(arguments.getString("calorieBudgetAccessCode"))
        val baseUrl = arguments.getString("calorieBudgetBaseUrl") ?: "http://127.0.0.1:8765"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sessionStore = SecureSessionStore(context).apply { clear() }
        val api = ApiClient({ baseUrl }, sessionStore)
        val auth = HttpAuthRepository(api, sessionStore)
        val username = "device-budget-${System.currentTimeMillis()}"
        try {
            auth.register(accessCode, username, PASSWORD, "de")
        } catch (error: ApiException) {
            throw AssertionError("register_${error.status}_${error.code}", error)
        }
        val userId = requireNotNull(sessionStore.readUserId())
        val syncStore = EncryptedHealthSyncStore(context).apply { clear(userId) }
        try {
            ProfileRepository(api).save(
                ProfileRequest(
                    locale = "de",
                    timezone = "Europe/Berlin",
                    targetKcal = "2000",
                    targetProtein = "120",
                    targetCarbs = "220",
                    targetFat = "70",
                    manual = true,
                    calorieBudgetMode = "dynamic",
                ),
            )
            val end = Instant.now().minusSeconds(60)
            val start = end.minusSeconds(86_400)
            val record = HealthRecord(
                type = HealthDataType.ActiveCalories,
                externalId = "device-budget-${UUID.randomUUID()}",
                originPackage = "de.baseline.nutrition.testsource",
                originAppName = "Baseline Test Source",
                startTimeUtc = end.minusSeconds(7200),
                endTimeUtc = end.minusSeconds(3600),
                zoneId = "Europe/Berlin",
                startZoneOffsetSeconds = 7200,
                endZoneOffsetSeconds = 7200,
                value = 301.0,
                unit = "kcal",
                lastModifiedTimeUtc = end.minusSeconds(1800),
            )
            val gateway = BudgetIntegrationGateway(record)
            val health = DefaultHealthRepository(
                gateway = gateway,
                settings = SecureHealthSettingsStorage(sessionStore),
                remote = ApiHealthSyncRemoteDataSource(api),
                syncStorage = syncStore,
                currentUserId = sessionStore::readUserId,
            )
            val permission = health.permissionRequest(HealthDataType.ActiveCalories)
            health.recordPermissionResult(permission, permission.permissions)

            val sync = health.sync(
                HealthDataType.ActiveCalories,
                HealthReadWindow(start, end, ZoneId.of("Europe/Berlin")),
            )
            assertTrue(sync.cursorCommitted)

            val diary = DiaryRepository(api)
            val day = record.assignedLocalDay().toString()
            val dynamic = requireNotNull(diary.summary(day).targets)
            assertEquals("dynamic", dynamic.budgetMode)
            assertEquals("ready", dynamic.activityStatus)
            assertEquals("301", dynamic.activityEnergy)
            assertEquals("150.5", dynamic.activityContributionEnergy)
            assertEquals("2151", dynamic.energy)

            val fixed = diary.setBudgetMode("fixed")
            assertEquals("0", fixed.activityContributionEnergy)
            assertEquals("2000", fixed.energy)
        } finally {
            runCatching { auth.deleteAccount(PASSWORD) }
            syncStore.clear(userId)
            sessionStore.clear()
        }
    }

    private companion object {
        const val PASSWORD = "a-secure-password"
    }
}

private class BudgetIntegrationGateway(private val record: HealthRecord) : HealthConnectGateway {
    override fun availability() = HealthAvailability.Available
    override fun permission(type: HealthDataType) = "test.permission.${type.name}"
    override suspend fun grantedPermissions(): Set<String> =
        setOf(permission(HealthDataType.ActiveCalories))

    override suspend fun revokeAllPermissions() = Unit

    override suspend fun readPage(
        type: HealthDataType,
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
    ) = HealthRecordPage(listOf(record), null)
}
