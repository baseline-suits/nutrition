package de.baseline.nutrition.data.network

import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
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
}
