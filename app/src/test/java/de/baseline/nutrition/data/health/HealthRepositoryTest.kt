package de.baseline.nutrition.data.health

import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRepositoryTest {
    @Test
    fun permissionsRemainIndependentAndDistinguishDenialRevocationAndDisable() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val repository = DefaultHealthRepository(gateway, settings)

        val stepsRequest = repository.permissionRequest(HealthDataType.Steps)
        gateway.granted += stepsRequest.permissions
        repository.recordPermissionResult(stepsRequest, gateway.granted)

        val sleepRequest = repository.permissionRequest(HealthDataType.Sleep)
        repository.recordPermissionResult(sleepRequest, emptySet())
        var connection = repository.connection()
        assertEquals(HealthPermissionState.Granted, connection.capabilities.getValue(HealthDataType.Steps).permissionState)
        assertEquals(HealthPermissionState.Denied, connection.capabilities.getValue(HealthDataType.Sleep).permissionState)
        assertEquals(
            HealthPermissionState.NotRequested,
            connection.capabilities.getValue(HealthDataType.Weight).permissionState,
        )

        repository.recordPermissionResult(sleepRequest, emptySet())
        assertEquals(
            HealthPermissionState.PermanentlyDenied,
            repository.connection().capabilities.getValue(HealthDataType.Sleep).permissionState,
        )

        repository.setEnabled(HealthDataType.Steps, false)
        assertEquals(
            HealthPermissionState.Disabled,
            repository.connection().capabilities.getValue(HealthDataType.Steps).permissionState,
        )
        repository.setEnabled(HealthDataType.Steps, true)
        gateway.granted.clear()
        connection = repository.connection()
        assertEquals(HealthPermissionState.Revoked, connection.capabilities.getValue(HealthDataType.Steps).permissionState)
    }

    @Test
    fun readsEverySupportedTypeWithoutMergingSources() = runTest {
        HealthDataType.entries.forEach { type ->
            val gateway = FakeHealthConnectGateway()
            val settings = FakeHealthSettingsStorage()
            val repository = DefaultHealthRepository(gateway, settings)
            val request = repository.permissionRequest(type)
            gateway.granted += request.permissions
            repository.recordPermissionResult(request, gateway.granted)
            gateway.pages[Pair(type, null)] = HealthRecordPage(
                records = listOf(record(type, "source.one", "record-1")),
                nextPageToken = null,
            )

            val result = repository.read(type, window())

            assertEquals(type, result.type)
            assertEquals("record-1", result.records.single().externalId)
            assertEquals("source.one", result.records.single().originPackage)
            assertFalse(result.truncated)
        }
    }

    @Test
    fun paginationKeepsLatestRecordVersionAndSeparateOrigins() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val repository = DefaultHealthRepository(gateway, settings)
        val request = repository.permissionRequest(HealthDataType.Steps)
        gateway.granted += request.permissions
        repository.recordPermissionResult(request, gateway.granted)
        val old = record(
            type = HealthDataType.Steps,
            origin = "source.one",
            id = "same-id",
            value = 100.0,
            modified = Instant.parse("2026-07-01T00:00:00Z"),
        )
        val corrected = old.copy(
            value = 120.0,
            lastModifiedTimeUtc = Instant.parse("2026-07-01T01:00:00Z"),
        )
        gateway.pages[Pair(HealthDataType.Steps, null)] = HealthRecordPage(listOf(old), "page-2")
        gateway.pages[HealthDataType.Steps to "page-2"] = HealthRecordPage(
            listOf(corrected, record(HealthDataType.Steps, "source.two", "same-id", 80.0)),
            null,
        )

        val result = repository.read(HealthDataType.Steps, window())

        assertEquals(listOf(null, "page-2"), gateway.readTokens)
        assertEquals(2, result.records.size)
        assertEquals(120.0, result.records.single { it.originPackage == "source.one" }.value, 0.0)
        assertEquals(setOf("source.one", "source.two"), result.sourceSummaries.map { it.originPackage }.toSet())
    }

    @Test
    fun emptyDataIsValidAndDisconnectRevokesAllPermissions() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val repository = DefaultHealthRepository(gateway, settings)
        val request = repository.permissionRequest(HealthDataType.Weight)
        gateway.granted += request.permissions
        repository.recordPermissionResult(request, gateway.granted)
        gateway.pages[Pair(HealthDataType.Weight, null)] = HealthRecordPage(emptyList(), null)

        val result = repository.read(HealthDataType.Weight, window())
        assertTrue(result.records.isEmpty())
        assertTrue(result.sourceSummaries.isEmpty())

        repository.disconnect()
        assertTrue(gateway.revokedAll)
        assertEquals(HealthSetting(), settings.read(HealthDataType.Weight))
    }

    @Test
    fun repeatedPageTokenStopsWithoutInventingAnAggregate() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val repository = DefaultHealthRepository(gateway, settings)
        val request = repository.permissionRequest(HealthDataType.ActiveCalories)
        gateway.granted += request.permissions
        repository.recordPermissionResult(request, gateway.granted)
        gateway.pages[Pair(HealthDataType.ActiveCalories, null)] = HealthRecordPage(
            listOf(record(HealthDataType.ActiveCalories, "source", "one")),
            "repeat",
        )
        gateway.pages[HealthDataType.ActiveCalories to "repeat"] = HealthRecordPage(
            listOf(
                record(HealthDataType.ActiveCalories, "source", "two").copy(
                    startTimeUtc = Instant.parse("2026-07-01T00:30:00Z"),
                    endTimeUtc = Instant.parse("2026-07-01T01:30:00Z"),
                ),
            ),
            "repeat",
        )

        val result = repository.read(HealthDataType.ActiveCalories, window())

        assertTrue(result.truncated)
        assertEquals(2, result.records.size)
        assertTrue(result.sourceSummaries.isEmpty())
    }

    @Test
    fun offlineRetryReusesExactBatchAndClearsRawRecordsAfterSuccess() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val remote = FakeHealthSyncRemoteDataSource(failures = 1)
        val storage = FakeHealthSyncStorage()
        val repository = syncRepository(
            gateway = gateway,
            settings = settings,
            remote = remote,
            storage = storage,
            requestIds = ArrayDeque(listOf("request-offline-1")),
        )
        grant(gateway, repository, HealthDataType.Steps)
        gateway.pages[HealthDataType.Steps to null] = HealthRecordPage(
            listOf(record(HealthDataType.Steps, "source.one", "offline-record")),
            null,
        )

        runCatching { repository.sync(HealthDataType.Steps, window()) }
            .onSuccess { error("network_failure_expected") }
            .onFailure { assertTrue(it is IOException) }
        val pending = storage.read("user-1").pending.getValue("steps")

        val result = repository.sync(HealthDataType.Steps, window())

        assertEquals(2, remote.requests.size)
        assertEquals(remote.requests[0], remote.requests[1])
        assertEquals(pending, remote.requests[1])
        assertEquals(1, result.recordCount)
        assertTrue(result.cursorCommitted)
        assertTrue(storage.read("user-1").pending.isEmpty())
        assertEquals(
            "steps:2026-07-02T00:00:00Z",
            storage.read("user-1").cursors.getValue("steps").cursor,
        )
    }

    @Test
    fun partialResponseKeepsRawDataRotatesCompletedRequestAndPreservesCursor() = runTest {
        val gateway = FakeHealthConnectGateway()
        val settings = FakeHealthSettingsStorage()
        val remote = FakeHealthSyncRemoteDataSource(partialResponses = 1)
        val storage = FakeHealthSyncStorage()
        val requestIds = ArrayDeque(listOf("request-partial-1", "request-partial-2"))
        val repository = syncRepository(gateway, settings, remote, storage, requestIds)
        grant(gateway, repository, HealthDataType.ActiveCalories)
        gateway.pages[HealthDataType.ActiveCalories to null] = HealthRecordPage(
            listOf(record(HealthDataType.ActiveCalories, "source.one", "partial-record")),
            null,
        )

        val partial = repository.sync(HealthDataType.ActiveCalories, window())

        assertFalse(partial.cursorCommitted)
        assertEquals(1, partial.rejectedCount)
        assertTrue(storage.read("user-1").cursors.isEmpty())
        assertEquals(
            "request-partial-2",
            storage.read("user-1").pending.getValue("active_calories").requestId,
        )

        val completed = repository.sync(HealthDataType.ActiveCalories, window())

        assertTrue(completed.cursorCommitted)
        assertEquals(
            listOf("request-partial-1", "request-partial-2"),
            remote.requests.map(HealthSyncBatchPayload::requestId),
        )
        assertTrue(storage.read("user-1").pending.isEmpty())
        assertEquals(1, gateway.readTokens.size)
    }

    private fun window() = HealthReadWindow(
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-02T00:00:00Z"),
        ZoneId.of("Europe/Berlin"),
    )

    private suspend fun grant(
        gateway: FakeHealthConnectGateway,
        repository: DefaultHealthRepository,
        type: HealthDataType,
    ) {
        val request = repository.permissionRequest(type)
        gateway.granted += request.permissions
        repository.recordPermissionResult(request, gateway.granted)
    }

    private fun syncRepository(
        gateway: FakeHealthConnectGateway,
        settings: FakeHealthSettingsStorage,
        remote: FakeHealthSyncRemoteDataSource,
        storage: FakeHealthSyncStorage,
        requestIds: ArrayDeque<String>,
    ) = DefaultHealthRepository(
        gateway = gateway,
        settings = settings,
        remote = remote,
        syncStorage = storage,
        currentUserId = { "user-1" },
        requestId = { requestIds.removeFirst() },
    )

    private fun record(
        type: HealthDataType,
        origin: String,
        id: String,
        value: Double = 1.0,
        modified: Instant = Instant.parse("2026-07-01T00:00:00Z"),
    ) = HealthRecord(
        type = type,
        externalId = id,
        originPackage = origin,
        originAppName = origin,
        startTimeUtc = Instant.parse("2026-07-01T00:00:00Z"),
        endTimeUtc = Instant.parse("2026-07-01T01:00:00Z"),
        zoneId = "Europe/Berlin",
        startZoneOffsetSeconds = 7200,
        endZoneOffsetSeconds = 7200,
        value = value,
        unit = when (type) {
            HealthDataType.Steps -> "count"
            HealthDataType.ActiveCalories -> "kcal"
            HealthDataType.Weight -> "kg"
            else -> "s"
        },
        lastModifiedTimeUtc = modified,
    )
}

private class FakeHealthSyncStorage : HealthSyncStorage {
    private val snapshots = mutableMapOf<String, HealthSyncSnapshot>()
    override fun installationId(): String = "installation-test"
    override fun read(userId: String): HealthSyncSnapshot = snapshots[userId] ?: HealthSyncSnapshot()
    override fun write(userId: String, snapshot: HealthSyncSnapshot) {
        snapshots[userId] = snapshot
    }
    override fun clear(userId: String) {
        snapshots.remove(userId)
    }
}

private class FakeHealthSyncRemoteDataSource(
    private var failures: Int = 0,
    private var partialResponses: Int = 0,
) : HealthSyncRemoteDataSource {
    val requests = mutableListOf<HealthSyncBatchPayload>()

    override suspend fun sync(payload: HealthSyncBatchPayload): HealthSyncBatchResponse {
        requests += payload
        if (failures > 0) {
            failures -= 1
            throw IOException("offline")
        }
        val partial = partialResponses > 0
        if (partial) partialResponses -= 1
        val section = payload.sections.single()
        return HealthSyncBatchResponse(
            schemaVersion = payload.schemaVersion,
            requestId = payload.requestId,
            sections = listOf(
                HealthSyncSectionResponse(
                    dataType = section.dataType,
                    cursorCommitted = !partial,
                    reconciledDeletions = 0,
                    results = section.records.map { record ->
                        HealthSyncElementResponse(
                            dataType = record.dataType,
                            externalRecordId = record.externalRecordId,
                            originPackage = record.originPackage,
                            status = if (partial) "rejected" else "created",
                            code = if (partial) "invalid_record" else null,
                        )
                    },
                ),
            ),
        )
    }
}

private class FakeHealthSettingsStorage : HealthSettingsStorage {
    private val values = mutableMapOf<HealthDataType, HealthSetting>()
    override fun read(type: HealthDataType): HealthSetting = values[type] ?: HealthSetting()
    override fun write(type: HealthDataType, setting: HealthSetting) {
        values[type] = setting
    }
}

private class FakeHealthConnectGateway : HealthConnectGateway {
    var currentAvailability = HealthAvailability.Available
    val granted = mutableSetOf<String>()
    val pages = mutableMapOf<Pair<HealthDataType, String?>, HealthRecordPage>()
    val readTokens = mutableListOf<String?>()
    var revokedAll = false

    override fun availability() = currentAvailability
    override fun permission(type: HealthDataType) = "android.permission.health.READ_${type.name.uppercase()}"
    override suspend fun grantedPermissions(): Set<String> = granted.toSet()
    override suspend fun revokeAllPermissions() {
        revokedAll = true
        granted.clear()
    }

    override suspend fun readPage(
        type: HealthDataType,
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
    ): HealthRecordPage {
        readTokens += pageToken
        assertEquals(DefaultHealthRepository.PAGE_SIZE, pageSize)
        return pages.getValue(type to pageToken)
    }
}
