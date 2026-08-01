package de.baseline.nutrition.domain.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class HealthDataType {
    Steps,
    Sleep,
    ActiveCalories,
    Exercise,
    Weight,
}

enum class HealthAvailability {
    Available,
    NotInstalled,
    UpdateRequired,
    Unavailable,
}

enum class HealthPermissionState {
    NotRequested,
    Granted,
    Disabled,
    Denied,
    PermanentlyDenied,
    Revoked,
}

data class HealthCapability(
    val type: HealthDataType,
    val permissionState: HealthPermissionState,
)

data class HealthConnectionSnapshot(
    val availability: HealthAvailability,
    val capabilities: Map<HealthDataType, HealthCapability>,
)

data class HealthPermissionRequest(
    val type: HealthDataType,
    val permissions: Set<String>,
)

data class HealthReadWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(startInclusive.isBefore(endExclusive))
        require(Duration.between(startInclusive, endExclusive) <= MAX_WINDOW)
    }

    companion object {
        val MAX_WINDOW: Duration = Duration.ofDays(30)
    }
}

data class HealthSegment(
    val startTimeUtc: Instant,
    val endTimeUtc: Instant,
    val type: Int,
)

data class HealthRecord(
    val type: HealthDataType,
    val externalId: String,
    val originPackage: String,
    val originAppName: String?,
    val startTimeUtc: Instant,
    val endTimeUtc: Instant,
    val zoneId: String,
    val startZoneOffsetSeconds: Int?,
    val endZoneOffsetSeconds: Int?,
    val value: Double,
    val unit: String,
    val lastModifiedTimeUtc: Instant,
    val detailType: Int? = null,
    val segments: List<HealthSegment> = emptyList(),
) {
    fun assignedLocalDay(): LocalDate {
        val useEnd = type == HealthDataType.Sleep
        val instant = if (useEnd) endTimeUtc else startTimeUtc
        val offsetSeconds = if (useEnd) endZoneOffsetSeconds else startZoneOffsetSeconds
        return if (offsetSeconds != null) {
            instant.atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds)).toLocalDate()
        } else {
            instant.atZone(ZoneId.of(zoneId)).toLocalDate()
        }
    }
}

data class HealthSourceSummary(
    val type: HealthDataType,
    val originPackage: String,
    val localDay: LocalDate,
    val value: Double?,
    val unit: String,
    val recordCount: Int,
    val overlapDetected: Boolean,
)

data class HealthReadResult(
    val type: HealthDataType,
    val records: List<HealthRecord>,
    val sourceSummaries: List<HealthSourceSummary>,
    val truncated: Boolean,
)

object HealthRecordAggregator {
    fun summarize(records: List<HealthRecord>): List<HealthSourceSummary> = records
        .groupBy { Triple(it.type, it.originPackage, it.assignedLocalDay()) }
        .map { (key, grouped) -> summarizeGroup(key, grouped) }
        .sortedWith(
            compareBy<HealthSourceSummary> { it.localDay }
                .thenBy { it.type.ordinal }
                .thenBy { it.originPackage },
        )

    private fun summarizeGroup(
        key: Triple<HealthDataType, String, LocalDate>,
        records: List<HealthRecord>,
    ): HealthSourceSummary {
        val sorted = records.sortedBy { it.startTimeUtc }
        val overlaps = hasOverlaps(sorted)
        val (value, unit) = when (key.first) {
            HealthDataType.Steps -> if (overlaps) null to "count" else sorted.sumOf { it.value } to "count"
            HealthDataType.ActiveCalories ->
                if (overlaps) null to "kcal" else sorted.sumOf { it.value } to "kcal"
            HealthDataType.Sleep,
            HealthDataType.Exercise,
            -> unionDurationSeconds(sorted) to "s"
            HealthDataType.Weight -> null to "kg"
        }
        return HealthSourceSummary(
            type = key.first,
            originPackage = key.second,
            localDay = key.third,
            value = value,
            unit = unit,
            recordCount = records.size,
            overlapDetected = overlaps,
        )
    }

    private fun hasOverlaps(records: List<HealthRecord>): Boolean {
        var latestEnd: Instant? = null
        for (record in records) {
            if (latestEnd != null && record.startTimeUtc.isBefore(latestEnd)) return true
            if (latestEnd == null || record.endTimeUtc.isAfter(latestEnd)) latestEnd = record.endTimeUtc
        }
        return false
    }

    private fun unionDurationSeconds(records: List<HealthRecord>): Double {
        var total = Duration.ZERO
        var currentStart: Instant? = null
        var currentEnd: Instant? = null
        for (record in records) {
            if (currentStart == null) {
                currentStart = record.startTimeUtc
                currentEnd = record.endTimeUtc
            } else if (!record.startTimeUtc.isAfter(currentEnd)) {
                if (record.endTimeUtc.isAfter(currentEnd)) currentEnd = record.endTimeUtc
            } else {
                total += Duration.between(currentStart, currentEnd)
                currentStart = record.startTimeUtc
                currentEnd = record.endTimeUtc
            }
        }
        if (currentStart != null) total += Duration.between(currentStart, currentEnd)
        return total.toMillis() / 1000.0
    }
}
