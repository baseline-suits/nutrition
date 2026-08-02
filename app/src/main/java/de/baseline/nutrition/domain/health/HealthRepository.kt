package de.baseline.nutrition.domain.health

interface HealthRepository {
    suspend fun connection(): HealthConnectionSnapshot
    fun permissionRequest(type: HealthDataType): HealthPermissionRequest
    suspend fun recordPermissionResult(request: HealthPermissionRequest, granted: Set<String>)
    suspend fun setEnabled(type: HealthDataType, enabled: Boolean)
    suspend fun read(type: HealthDataType, window: HealthReadWindow): HealthReadResult
    suspend fun sync(type: HealthDataType, window: HealthReadWindow): HealthSyncResult
    suspend fun disconnect()
}
