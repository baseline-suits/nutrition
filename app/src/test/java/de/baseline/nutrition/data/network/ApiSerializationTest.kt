package de.baseline.nutrition.data.network

import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.health.HealthSyncBatchPayload
import de.baseline.nutrition.data.health.HealthSyncRecordPayload
import de.baseline.nutrition.data.health.HealthSyncSectionPayload
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSerializationTest {
    @Test
    fun requestDefaultsRequiredByBackendAreEncoded() {
        val payload = MealPayload(
            clientId = "manual-contract",
            localDay = "2026-08-01",
            eatenAt = "2026-08-01T13:04:00+02:00",
            timezone = "Europe/Berlin",
            mealType = "snack",
            name = "Manuelle Mahlzeit",
            nutrients = listOf(
                NutrientDto(key = "energy", value = "450", unit = "kcal"),
            ),
        )

        val encoded = ApiJson.encodeToString(payload)

        assertTrue(encoded.contains("\"capture_method\":\"manual\""))
        assertTrue(encoded.contains("\"locked\":true"))
        assertTrue(encoded.contains("\"accuracy\":\"exact\""))
    }

    @Test
    fun healthBatchUsesVersionedSnakeCaseContract() {
        val payload = HealthSyncBatchPayload(
            requestId = "request-contract",
            installationId = "installation-contract",
            sections = listOf(
                HealthSyncSectionPayload(
                    dataType = "steps",
                    windowStart = "2026-07-01T00:00:00Z",
                    windowEnd = "2026-07-02T00:00:00Z",
                    cursor = "steps:2026-07-02T00:00:00Z",
                    records = listOf(
                        HealthSyncRecordPayload(
                            dataType = "steps",
                            externalRecordId = "record-1",
                            originPackage = "com.example.health",
                            startTime = "2026-07-01T08:00:00Z",
                            endTime = "2026-07-01T09:00:00Z",
                            zoneId = "Europe/Berlin",
                            startOffsetSeconds = 7200,
                            endOffsetSeconds = 7200,
                            value = "1000",
                            unit = "count",
                            lastModifiedTime = "2026-07-01T10:00:00Z",
                        ),
                    ),
                ),
            ),
        )

        val encoded = ApiJson.encodeToString(payload)

        assertTrue(encoded.contains("\"schema_version\":\"health-sync/1.0\""))
        assertTrue(encoded.contains("\"request_id\":\"request-contract\""))
        assertTrue(encoded.contains("\"installation_id\":\"installation-contract\""))
        assertTrue(encoded.contains("\"external_record_id\":\"record-1\""))
        assertTrue(encoded.contains("\"start_offset_seconds\""))
        assertTrue(encoded.contains("\"complete\":true"))
    }
}
