package de.baseline.nutrition.data.sync

import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedMealQueueStoreTest {
    @Test
    fun queueSurvivesStoreRestartAndRemainsAccountBound() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = EncryptedMealQueueStore(context)
        first.clear("encrypted-user-a")
        first.clear("encrypted-user-b")
        val operation = QueuedMealOperation(
            id = "operation-1",
            userId = "encrypted-user-a",
            resourceId = "local:client-1",
            idempotencyKey = "client-1",
            type = MealOperationType.Create,
            payload = MealPayload(
                clientId = "client-1",
                localDay = "2026-07-31",
                eatenAt = "2026-07-31T12:00:00+02:00",
                timezone = "Europe/Berlin",
                mealType = "lunch",
                name = "Verschlüsselte Mahlzeit",
                nutrients = listOf(
                    NutrientDto(key = "energy", value = "500", unit = "kcal"),
                ),
            ),
            createdAt = 1,
            updatedAt = 1,
        )
        first.write(
            "encrypted-user-a",
            MealSyncSnapshot(operations = listOf(operation)),
        )

        val restarted = EncryptedMealQueueStore(context)

        assertEquals("operation-1", restarted.read("encrypted-user-a").operations.single().id)
        assertTrue(restarted.read("encrypted-user-b").operations.isEmpty())
        restarted.clear("encrypted-user-a")
        restarted.clear("encrypted-user-b")
    }
}
