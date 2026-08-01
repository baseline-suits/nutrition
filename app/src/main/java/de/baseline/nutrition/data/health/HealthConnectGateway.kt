package de.baseline.nutrition.data.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRecord
import de.baseline.nutrition.domain.health.HealthSegment
import java.time.Duration

data class HealthRecordPage(
    val records: List<HealthRecord>,
    val nextPageToken: String?,
)

interface HealthConnectGateway {
    fun availability(): HealthAvailability
    fun permission(type: HealthDataType): String
    suspend fun grantedPermissions(): Set<String>
    suspend fun revokeAllPermissions()
    suspend fun readPage(
        type: HealthDataType,
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
    ): HealthRecordPage
}

class AndroidHealthConnectGateway(private val context: Context) : HealthConnectGateway {
    private val healthClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
            if (providerInstalled()) HealthAvailability.UpdateRequired else HealthAvailability.NotInstalled
        }
        else -> HealthAvailability.Unavailable
    }

    override fun permission(type: HealthDataType): String = when (type) {
        HealthDataType.Steps -> HealthPermission.getReadPermission(StepsRecord::class)
        HealthDataType.Sleep -> HealthPermission.getReadPermission(SleepSessionRecord::class)
        HealthDataType.ActiveCalories ->
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        HealthDataType.Exercise -> HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        HealthDataType.Weight -> HealthPermission.getReadPermission(WeightRecord::class)
    }

    override suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

    override suspend fun revokeAllPermissions() {
        client().permissionController.revokeAllPermissions()
    }

    override suspend fun readPage(
        type: HealthDataType,
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
    ): HealthRecordPage = when (type) {
        HealthDataType.Steps -> readRecords<StepsRecord>(window, pageToken, pageSize) { record ->
            record.toHealthRecord(window, record.count.toDouble(), "count")
        }
        HealthDataType.Sleep -> readRecords<SleepSessionRecord>(window, pageToken, pageSize) { record ->
            record.toHealthRecord(
                window = window,
                value = Duration.between(record.startTime, record.endTime).toMillis() / 1000.0,
                unit = "s",
                segments = record.stages.map { HealthSegment(it.startTime, it.endTime, it.stage) },
            )
        }
        HealthDataType.ActiveCalories ->
            readRecords<ActiveCaloriesBurnedRecord>(window, pageToken, pageSize) { record ->
                record.toHealthRecord(window, record.energy.inKilocalories, "kcal")
            }
        HealthDataType.Exercise ->
            readRecords<ExerciseSessionRecord>(window, pageToken, pageSize) { record ->
                record.toHealthRecord(
                    window = window,
                    value = Duration.between(record.startTime, record.endTime).toMillis() / 1000.0,
                    unit = "s",
                    detailType = record.exerciseType,
                )
            }
        HealthDataType.Weight -> readRecords<WeightRecord>(window, pageToken, pageSize) { record ->
            HealthRecord(
                type = HealthDataType.Weight,
                externalId = record.metadata.id,
                originPackage = record.metadata.dataOrigin.packageName,
                originAppName = appName(record.metadata.dataOrigin.packageName),
                startTimeUtc = record.time,
                endTimeUtc = record.time,
                zoneId = window.zoneId.id,
                startZoneOffsetSeconds = record.zoneOffset?.totalSeconds,
                endZoneOffsetSeconds = record.zoneOffset?.totalSeconds,
                value = record.weight.inKilograms,
                unit = "kg",
                lastModifiedTimeUtc = record.metadata.lastModifiedTime,
            )
        }
    }

    private suspend inline fun <reified T : Record> readRecords(
        window: HealthReadWindow,
        pageToken: String?,
        pageSize: Int,
        mapper: (T) -> HealthRecord,
    ): HealthRecordPage {
        val response = client().readRecords(
            ReadRecordsRequest<T>(
                timeRangeFilter = TimeRangeFilter.between(window.startInclusive, window.endExclusive),
                ascendingOrder = true,
                pageSize = pageSize,
                pageToken = pageToken,
            ),
        )
        return HealthRecordPage(response.records.map(mapper), response.pageToken)
    }

    private fun client(): HealthConnectClient {
        check(availability() == HealthAvailability.Available)
        return healthClient
    }

    @Suppress("DEPRECATION")
    private fun providerInstalled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PROVIDER, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun appName(packageName: String): String? = runCatching {
        val application = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(application).toString()
    }.getOrNull()

    private fun StepsRecord.toHealthRecord(
        window: HealthReadWindow,
        value: Double,
        unit: String,
    ) = intervalRecord(
        type = HealthDataType.Steps,
        window = window,
        startTime = startTime,
        endTime = endTime,
        startOffset = startZoneOffset?.totalSeconds,
        endOffset = endZoneOffset?.totalSeconds,
        externalId = metadata.id,
        originPackage = metadata.dataOrigin.packageName,
        lastModified = metadata.lastModifiedTime,
        value = value,
        unit = unit,
    )

    private fun ActiveCaloriesBurnedRecord.toHealthRecord(
        window: HealthReadWindow,
        value: Double,
        unit: String,
    ) = intervalRecord(
        type = HealthDataType.ActiveCalories,
        window = window,
        startTime = startTime,
        endTime = endTime,
        startOffset = startZoneOffset?.totalSeconds,
        endOffset = endZoneOffset?.totalSeconds,
        externalId = metadata.id,
        originPackage = metadata.dataOrigin.packageName,
        lastModified = metadata.lastModifiedTime,
        value = value,
        unit = unit,
    )

    private fun SleepSessionRecord.toHealthRecord(
        window: HealthReadWindow,
        value: Double,
        unit: String,
        segments: List<HealthSegment>,
    ) = intervalRecord(
        type = HealthDataType.Sleep,
        window = window,
        startTime = startTime,
        endTime = endTime,
        startOffset = startZoneOffset?.totalSeconds,
        endOffset = endZoneOffset?.totalSeconds,
        externalId = metadata.id,
        originPackage = metadata.dataOrigin.packageName,
        lastModified = metadata.lastModifiedTime,
        value = value,
        unit = unit,
        segments = segments,
    )

    private fun ExerciseSessionRecord.toHealthRecord(
        window: HealthReadWindow,
        value: Double,
        unit: String,
        detailType: Int,
    ) = intervalRecord(
        type = HealthDataType.Exercise,
        window = window,
        startTime = startTime,
        endTime = endTime,
        startOffset = startZoneOffset?.totalSeconds,
        endOffset = endZoneOffset?.totalSeconds,
        externalId = metadata.id,
        originPackage = metadata.dataOrigin.packageName,
        lastModified = metadata.lastModifiedTime,
        value = value,
        unit = unit,
        detailType = detailType,
    )

    private fun intervalRecord(
        type: HealthDataType,
        window: HealthReadWindow,
        startTime: java.time.Instant,
        endTime: java.time.Instant,
        startOffset: Int?,
        endOffset: Int?,
        externalId: String,
        originPackage: String,
        lastModified: java.time.Instant,
        value: Double,
        unit: String,
        detailType: Int? = null,
        segments: List<HealthSegment> = emptyList(),
    ) = HealthRecord(
        type = type,
        externalId = externalId,
        originPackage = originPackage,
        originAppName = appName(originPackage),
        startTimeUtc = startTime,
        endTimeUtc = endTime,
        zoneId = window.zoneId.id,
        startZoneOffsetSeconds = startOffset,
        endZoneOffsetSeconds = endOffset,
        value = value,
        unit = unit,
        lastModifiedTimeUtc = lastModified,
        detailType = detailType,
        segments = segments,
    )

    private companion object {
        const val HEALTH_CONNECT_PROVIDER = "com.google.android.apps.healthdata"
    }
}
