package de.baseline.nutrition.data.health

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.auth.HttpAuthRepository
import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthSyncApiIntegrationTest {
    @Test
    fun authenticatedDeviceBatchReachesBackendAndAdvancesCursor() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("healthSyncIntegration") == "true")
        val accessCode = requireNotNull(arguments.getString("healthSyncAccessCode"))
        val baseUrl = arguments.getString("healthSyncBaseUrl") ?: "http://127.0.0.1:8765"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sessionStore = SecureSessionStore(context).apply { clear() }
        val api = ApiClient({ baseUrl }, sessionStore)
        val auth = HttpAuthRepository(api, sessionStore)
        val username = "device-health-${System.currentTimeMillis()}"
        try {
            auth.register(accessCode, username, PASSWORD, "de")
        } catch (error: ApiException) {
            throw AssertionError("register_${error.status}_${error.code}", error)
        }
        val userId = requireNotNull(sessionStore.readUserId())
        val syncStore = EncryptedHealthSyncStore(context).apply { clear(userId) }
        try {
            val end = Instant.now().minusSeconds(60)
            val start = end.minusSeconds(86_400)
            val record = HealthRecord(
                type = HealthDataType.Steps,
                externalId = "device-${UUID.randomUUID()}",
                originPackage = "de.baseline.nutrition.testsource",
                originAppName = "Baseline Test Source",
                startTimeUtc = end.minusSeconds(7200),
                endTimeUtc = end.minusSeconds(3600),
                zoneId = "Europe/Berlin",
                startZoneOffsetSeconds = 7200,
                endZoneOffsetSeconds = 7200,
                value = 1234.0,
                unit = "count",
                lastModifiedTimeUtc = end.minusSeconds(1800),
            )
            val gateway = IntegrationGateway(record)
            val repository = DefaultHealthRepository(
                gateway = gateway,
                settings = SecureHealthSettingsStorage(sessionStore),
                remote = ApiHealthSyncRemoteDataSource(api),
                syncStorage = syncStore,
                currentUserId = sessionStore::readUserId,
            )
            val permission = repository.permissionRequest(HealthDataType.Steps)
            repository.recordPermissionResult(permission, permission.permissions)

            val result = repository.sync(
                HealthDataType.Steps,
                HealthReadWindow(start, end, ZoneId.of("Europe/Berlin")),
            )

            assertTrue(result.cursorCommitted)
            assertEquals(1, result.recordCount)
            assertTrue(syncStore.read(userId).pending.isEmpty())
            assertTrue("steps" in syncStore.read(userId).cursors)
            val day = record.assignedLocalDay()
            val records = api.request<Unit, List<HealthRecordProbe>>(
                "/v1/health/records?data_type=steps&start=$day&end=$day",
                "GET",
                authenticated = true,
            )
            assertEquals(record.externalId, records.single().externalRecordId)
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

@Serializable
private data class HealthRecordProbe(
    @SerialName("external_record_id") val externalRecordId: String,
)

private class IntegrationGateway(private val record: HealthRecord) : HealthConnectGateway {
    override fun availability() = HealthAvailability.Available
    override fun permission(type: HealthDataType) = "test.permission.${type.name}"
    override suspend fun grantedPermissions(): Set<String> = setOf(permission(HealthDataType.Steps))
    override suspend fun revokeAllPermissions() = Unit
    override suspend fun readPage(
        type: HealthDataType,
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
    ) = HealthRecordPage(listOf(record), null)
}
