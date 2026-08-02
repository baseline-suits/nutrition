package de.baseline.nutrition.domain.health

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRecordAggregatorTest {
    @Test
    fun sleepAcrossMidnightBelongsToWakeDayAndOverlapUsesUnionDuration() {
        val first = record(
            type = HealthDataType.Sleep,
            id = "sleep-1",
            start = "2026-07-01T20:00:00Z",
            end = "2026-07-02T00:00:00Z",
            offset = 7200,
        )
        val second = record(
            type = HealthDataType.Sleep,
            id = "sleep-2",
            start = "2026-07-01T23:00:00Z",
            end = "2026-07-02T04:00:00Z",
            offset = 7200,
        )

        val summary = HealthRecordAggregator.summarize(listOf(first, second)).single()

        assertEquals("2026-07-02", summary.localDay.toString())
        assertEquals(Duration.ofHours(8).seconds.toDouble(), summary.value ?: 0.0, 0.0)
        assertTrue(summary.overlapDetected)
    }

    @Test
    fun overlappingStepsAndCaloriesAreFlaggedInsteadOfBlindlySummed() {
        for (type in listOf(HealthDataType.Steps, HealthDataType.ActiveCalories)) {
            val first = record(type, "one", "2026-07-01T08:00:00Z", "2026-07-01T09:00:00Z")
            val second = record(type, "two", "2026-07-01T08:30:00Z", "2026-07-01T09:30:00Z")

            val summary = HealthRecordAggregator.summarize(listOf(first, second)).single()

            assertTrue(summary.overlapDetected)
            assertNull(summary.value)
        }
    }

    @Test
    fun sourceAppsRemainSeparateAndExerciseDurationUsesIntervalUnion() {
        val sourceOne = record(
            HealthDataType.Exercise,
            "one",
            "2026-07-01T08:00:00Z",
            "2026-07-01T09:00:00Z",
            origin = "watch.app",
        )
        val overlapping = record(
            HealthDataType.Exercise,
            "two",
            "2026-07-01T08:30:00Z",
            "2026-07-01T10:00:00Z",
            origin = "watch.app",
        )
        val phone = record(
            HealthDataType.Exercise,
            "three",
            "2026-07-01T08:00:00Z",
            "2026-07-01T08:45:00Z",
            origin = "phone.app",
        )

        val summaries = HealthRecordAggregator.summarize(listOf(sourceOne, overlapping, phone))

        assertEquals(setOf("watch.app", "phone.app"), summaries.map { it.originPackage }.toSet())
        assertEquals(
            Duration.ofHours(2).seconds.toDouble(),
            summaries.single { it.originPackage == "watch.app" }.value ?: 0.0,
            0.0,
        )
    }

    @Test
    fun recordOffsetWinsWhenDeviceZoneChanges() {
        val nearMidnight = record(
            type = HealthDataType.Steps,
            id = "timezone",
            start = "2026-06-30T23:30:00Z",
            end = "2026-07-01T00:00:00Z",
            offset = 7200,
        )

        assertEquals("2026-07-01", nearMidnight.assignedLocalDay().toString())
    }

    @Test
    fun weightRemainsAnIndividualMeasurement() {
        val summary = HealthRecordAggregator.summarize(
            listOf(record(HealthDataType.Weight, "weight", "2026-07-01T08:00:00Z", "2026-07-01T08:00:00Z")),
        ).single()

        assertEquals(1, summary.recordCount)
        assertNull(summary.value)
        assertEquals("kg", summary.unit)
    }

    private fun record(
        type: HealthDataType,
        id: String,
        start: String,
        end: String,
        offset: Int = 0,
        origin: String = "source.app",
    ) = HealthRecord(
        type = type,
        externalId = id,
        originPackage = origin,
        originAppName = null,
        startTimeUtc = Instant.parse(start),
        endTimeUtc = Instant.parse(end),
        zoneId = "America/New_York",
        startZoneOffsetSeconds = offset,
        endZoneOffsetSeconds = offset,
        value = 10.0,
        unit = when (type) {
            HealthDataType.Steps -> "count"
            HealthDataType.ActiveCalories -> "kcal"
            HealthDataType.Weight -> "kg"
            else -> "s"
        },
        lastModifiedTimeUtc = Instant.parse("2026-07-01T12:00:00Z"),
    )
}
