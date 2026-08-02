package de.baseline.nutrition.data.health

import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthCapability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionRequest
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadResult
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
import de.baseline.nutrition.domain.health.HealthRecordAggregator
import de.baseline.nutrition.domain.health.HealthRepository
import de.baseline.nutrition.domain.health.HealthSyncResult
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class HealthSetting(
    val enabled: Boolean = false,
    val requestCount: Int = 0,
    val everGranted: Boolean = false,
)

internal interface HealthSettingsStorage {
    fun read(type: HealthDataType): HealthSetting
    fun write(type: HealthDataType, setting: HealthSetting)
}

internal class SecureHealthSettingsStorage(
    private val sessionStore: SecureSessionStore,
) : HealthSettingsStorage {
    override fun read(type: HealthDataType): HealthSetting {
        val values = sessionStore.readSecureString(key(type))?.split('|') ?: return HealthSetting()
        if (values.size != 3) return HealthSetting()
        return HealthSetting(
            enabled = values[0].toBooleanStrictOrNull() ?: false,
            requestCount = values[1].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            everGranted = values[2].toBooleanStrictOrNull() ?: false,
        )
    }

    override fun write(type: HealthDataType, setting: HealthSetting) {
        sessionStore.writeSecureString(
            key(type),
            "${setting.enabled}|${setting.requestCount}|${setting.everGranted}",
        )
    }

    private fun key(type: HealthDataType): String {
        val userId = requireNotNull(sessionStore.readUserId()) { "authenticated_user" }
        return "health_connect_${userId}_${type.name}"
    }
}

class DefaultHealthRepository internal constructor(
    private val gateway: HealthConnectGateway,
    private val settings: HealthSettingsStorage,
    private val remote: HealthSyncRemoteDataSource? = null,
    private val syncStorage: HealthSyncStorage? = null,
    private val currentUserId: () -> String? = { null },
    private val requestId: () -> String = { UUID.randomUUID().toString() },
) : HealthRepository {
    private val syncMutex = Mutex()

    override suspend fun connection(): HealthConnectionSnapshot {
        val availability = gateway.availability()
        val granted = if (availability == HealthAvailability.Available) {
            gateway.grantedPermissions()
        } else {
            emptySet()
        }
        return HealthConnectionSnapshot(
            availability = availability,
            capabilities = HealthDataType.entries.associateWith { type ->
                HealthCapability(type, permissionState(type, granted))
            },
        )
    }

    override fun permissionRequest(type: HealthDataType) = HealthPermissionRequest(
        type = type,
        permissions = setOf(gateway.permission(type)),
    )

    override suspend fun recordPermissionResult(
        request: HealthPermissionRequest,
        granted: Set<String>,
    ) {
        val current = settings.read(request.type)
        val accepted = request.permissions.all(granted::contains)
        settings.write(
            request.type,
            current.copy(
                enabled = accepted,
                requestCount = current.requestCount + 1,
                everGranted = current.everGranted || accepted,
            ),
        )
    }

    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) {
        val current = settings.read(type)
        if (enabled) {
            val granted = gateway.grantedPermissions()
            require(gateway.permission(type) in granted) { "permission_missing" }
        }
        settings.write(type, current.copy(enabled = enabled))
        if (!enabled) syncMutex.withLock { clearPending(type) }
    }

    override suspend fun read(type: HealthDataType, window: HealthReadWindow): HealthReadResult {
        require(gateway.availability() == HealthAvailability.Available) { "health_connect_unavailable" }
        val granted = gateway.grantedPermissions()
        require(permissionState(type, granted) == HealthPermissionState.Granted) { "permission_missing" }
        val byExternalId = linkedMapOf<String, HealthRecord>()
        val seenTokens = mutableSetOf<String>()
        var nextToken: String? = null
        var pages = 0
        var truncated = false
        do {
            val page = gateway.readPage(type, window, nextToken, PAGE_SIZE)
            for (record in page.records) {
                val key = "${record.originPackage}\u0000${record.externalId}"
                val previous = byExternalId[key]
                if (previous == null || record.lastModifiedTimeUtc >= previous.lastModifiedTimeUtc) {
                    byExternalId[key] = record
                }
            }
            pages += 1
            nextToken = page.nextPageToken
            if (nextToken != null && !seenTokens.add(nextToken)) {
                truncated = true
                break
            }
            if (pages >= MAX_PAGES && nextToken != null) {
                truncated = true
                break
            }
        } while (nextToken != null)
        val records = byExternalId.values.sortedWith(
            compareBy<HealthRecord> { it.startTimeUtc }
                .thenBy { it.originPackage }
                .thenBy { it.externalId },
        )
        return HealthReadResult(
            type = type,
            records = records,
            sourceSummaries = if (truncated) emptyList() else HealthRecordAggregator.summarize(records),
            truncated = truncated,
        )
    }

    override suspend fun sync(
        type: HealthDataType,
        window: HealthReadWindow,
    ): HealthSyncResult = syncMutex.withLock {
        val remote = requireNotNull(remote) { "health_sync_unavailable" }
        val storage = requireNotNull(syncStorage) { "health_sync_unavailable" }
        val userId = requireNotNull(currentUserId()) { "authenticated_user" }
        val typeName = type.apiName()
        var snapshot = storage.read(userId)
        var batch = snapshot.pending[typeName]
        if (batch == null) {
            val read = read(type, window)
            if (read.truncated || read.records.size > MAX_BATCH_RECORDS) {
                return@withLock HealthSyncResult(
                    type = type,
                    recordCount = read.records.size,
                    sourceCount = read.records.map(HealthRecord::originPackage).distinct().size,
                    truncated = true,
                    cursorCommitted = false,
                    reconciledDeletions = 0,
                    rejectedCount = 0,
                )
            }
            batch = HealthSyncBatchPayload(
                requestId = requestId(),
                installationId = storage.installationId(),
                sections = listOf(
                    HealthSyncSectionPayload(
                        dataType = typeName,
                        windowStart = window.startInclusive.toString(),
                        windowEnd = window.endExclusive.toString(),
                        cursor = "$typeName:${window.endExclusive}",
                        records = read.records.map(HealthRecord::toSyncPayload),
                    ),
                ),
            )
            snapshot = snapshot.copy(pending = snapshot.pending + (typeName to batch))
            storage.write(userId, snapshot)
        }
        val response = remote.sync(batch)
        check(response.schemaVersion == batch.schemaVersion) { "health_sync_schema_mismatch" }
        check(response.requestId == batch.requestId) { "health_sync_request_mismatch" }
        val responseSection = response.sections.singleOrNull()
        check(responseSection?.dataType == typeName) { "health_sync_section_mismatch" }
        val section = batch.sections.single()
        val rejected = responseSection.results.count { it.status == "rejected" }
        snapshot = storage.read(userId)
        val currentPending = snapshot.pending[typeName]
        if (currentPending?.requestId == batch.requestId) {
            snapshot = if (responseSection.cursorCommitted && rejected == 0) {
                snapshot.copy(
                    pending = snapshot.pending - typeName,
                    cursors = snapshot.cursors + (
                        typeName to StoredHealthCursor(
                            cursor = section.cursor,
                            windowEnd = section.windowEnd,
                        )
                    ),
                )
            } else {
                snapshot.copy(
                    pending = snapshot.pending + (
                        typeName to batch.copy(requestId = requestId())
                    ),
                )
            }
            storage.write(userId, snapshot)
        }
        HealthSyncResult(
            type = type,
            recordCount = section.records.size,
            sourceCount = section.records.map(HealthSyncRecordPayload::originPackage).distinct().size,
            truncated = false,
            cursorCommitted = responseSection.cursorCommitted && rejected == 0,
            reconciledDeletions = responseSection.reconciledDeletions,
            rejectedCount = rejected,
        )
    }

    override suspend fun disconnect() {
        if (gateway.availability() == HealthAvailability.Available) {
            gateway.revokeAllPermissions()
        }
        HealthDataType.entries.forEach { settings.write(it, HealthSetting()) }
        syncMutex.withLock {
            currentUserId()?.let { userId -> syncStorage?.clear(userId) }
        }
    }

    private fun clearPending(type: HealthDataType) {
        val storage = syncStorage ?: return
        val userId = currentUserId() ?: return
        val snapshot = storage.read(userId)
        val typeName = type.apiName()
        if (typeName in snapshot.pending) {
            storage.write(userId, snapshot.copy(pending = snapshot.pending - typeName))
        }
    }

    private fun permissionState(type: HealthDataType, granted: Set<String>): HealthPermissionState {
        val setting = settings.read(type)
        if (gateway.permission(type) in granted) {
            return if (setting.enabled) HealthPermissionState.Granted else HealthPermissionState.Disabled
        }
        return when {
            setting.everGranted -> HealthPermissionState.Revoked
            setting.requestCount >= 2 -> HealthPermissionState.PermanentlyDenied
            setting.requestCount == 1 -> HealthPermissionState.Denied
            else -> HealthPermissionState.NotRequested
        }
    }

    companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 100
        const val MAX_BATCH_RECORDS = 500
    }
}
