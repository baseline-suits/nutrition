package de.baseline.nutrition.data.health

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthRecord
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthSyncSegmentPayload(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("segment_type") val segmentType: Int,
)

@Serializable
data class HealthSyncRecordPayload(
    val operation: String = "upsert",
    @SerialName("data_type") val dataType: String,
    @SerialName("external_record_id") val externalRecordId: String,
    @SerialName("origin_package") val originPackage: String,
    @SerialName("origin_app_name") val originAppName: String? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("zone_id") val zoneId: String,
    @SerialName("start_offset_seconds") val startOffsetSeconds: Int? = null,
    @SerialName("end_offset_seconds") val endOffsetSeconds: Int? = null,
    val value: String,
    val unit: String,
    @SerialName("last_modified_time") val lastModifiedTime: String,
    @SerialName("detail_type") val detailType: Int? = null,
    val segments: List<HealthSyncSegmentPayload> = emptyList(),
)

@Serializable
data class HealthSyncSectionPayload(
    @SerialName("data_type") val dataType: String,
    @SerialName("window_start") val windowStart: String,
    @SerialName("window_end") val windowEnd: String,
    val cursor: String,
    val complete: Boolean = true,
    val records: List<HealthSyncRecordPayload>,
)

@Serializable
data class HealthSyncBatchPayload(
    @SerialName("schema_version") val schemaVersion: String = "health-sync/1.0",
    @SerialName("request_id") val requestId: String,
    @SerialName("installation_id") val installationId: String,
    val sections: List<HealthSyncSectionPayload>,
)

@Serializable
data class HealthSyncElementResponse(
    @SerialName("data_type") val dataType: String,
    @SerialName("external_record_id") val externalRecordId: String,
    @SerialName("origin_package") val originPackage: String,
    val status: String,
    val code: String? = null,
)

@Serializable
data class HealthSyncSectionResponse(
    @SerialName("data_type") val dataType: String,
    @SerialName("cursor_committed") val cursorCommitted: Boolean,
    @SerialName("reconciled_deletions") val reconciledDeletions: Int,
    val results: List<HealthSyncElementResponse>,
)

@Serializable
data class HealthSyncBatchResponse(
    @SerialName("schema_version") val schemaVersion: String,
    @SerialName("request_id") val requestId: String,
    val sections: List<HealthSyncSectionResponse>,
)

interface HealthSyncRemoteDataSource {
    suspend fun sync(payload: HealthSyncBatchPayload): HealthSyncBatchResponse
}

class ApiHealthSyncRemoteDataSource(private val api: ApiClient) : HealthSyncRemoteDataSource {
    override suspend fun sync(payload: HealthSyncBatchPayload): HealthSyncBatchResponse =
        api.request(
            "/v1/health/batches",
            "POST",
            payload,
            authenticated = true,
        )
}

internal fun HealthDataType.apiName(): String = when (this) {
    HealthDataType.Steps -> "steps"
    HealthDataType.Sleep -> "sleep"
    HealthDataType.ActiveCalories -> "active_calories"
    HealthDataType.Exercise -> "exercise"
    HealthDataType.Weight -> "weight"
}

internal fun HealthRecord.toSyncPayload() = HealthSyncRecordPayload(
    dataType = type.apiName(),
    externalRecordId = externalId,
    originPackage = originPackage,
    originAppName = originAppName,
    startTime = startTimeUtc.toString(),
    endTime = endTimeUtc.toString(),
    zoneId = zoneId,
    startOffsetSeconds = startZoneOffsetSeconds,
    endOffsetSeconds = endZoneOffsetSeconds,
    value = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(),
    unit = unit,
    lastModifiedTime = lastModifiedTimeUtc.toString(),
    detailType = detailType,
    segments = segments.map { segment ->
        HealthSyncSegmentPayload(
            startTime = segment.startTimeUtc.toString(),
            endTime = segment.endTimeUtc.toString(),
            segmentType = segment.type,
        )
    },
)
