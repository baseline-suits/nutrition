package de.baseline.nutrition.data.health

import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
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

    private fun window() = HealthReadWindow(
        Instant.parse("2026-07-01T00:00:00Z"),
        Instant.parse("2026-07-02T00:00:00Z"),
        ZoneId.of("Europe/Berlin"),
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
