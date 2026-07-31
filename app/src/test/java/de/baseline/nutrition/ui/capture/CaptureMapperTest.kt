package de.baseline.nutrition.ui.capture

import de.baseline.nutrition.data.capture.AnalysisDraftDto
import de.baseline.nutrition.data.capture.AnalysisMealDto
import de.baseline.nutrition.data.diary.IngredientDto
import de.baseline.nutrition.data.diary.NutrientDto
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureMapperTest {
    @Test
    fun `analysis draft becomes editable unsaved meal without inventing macros`() {
        val draft = AnalysisDraftDto(
            id = "analysis-1",
            status = "completed",
            model = "gpt-5.6-luna",
            promptVersion = "prompt-1",
            schemaVersion = "schema-1",
            attachmentId = "photo-1",
            meal = AnalysisMealDto(
                name = "Kartoffeln",
                ingredients = listOf(
                    IngredientDto(
                        name = "Kartoffeln",
                        amount = "250",
                        unit = "g",
                        nutrients = listOf(
                            NutrientDto(
                                key = "energy",
                                value = "190",
                                unit = "kcal",
                                source = "ai_estimate",
                                locked = false,
                                accuracy = "estimated",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val editor = draft.toMealEditor("2026-07-31", "lunch", "camera")
        val payload = editor.toPayload(ZoneId.of("Europe/Berlin"))

        assertNull(editor.mealId)
        assertEquals("camera", payload.captureMethod)
        assertEquals("ai_estimate", payload.provenanceSource)
        assertEquals("photo-1", payload.attachmentId)
        assertEquals("190", payload.ingredients.single().nutrients.single().value)
        assertEquals(false, payload.ingredients.single().nutrients.single().locked)
        assertNull(editor.totals()["protein"])
    }
}
