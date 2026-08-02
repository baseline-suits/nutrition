package de.baseline.nutrition.ui.barcode

import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.product.ProductDto
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BarcodeMapperTest {
    @Test
    fun scalesPerHundredValuesForGramsAndKnownPortions() {
        val product = product()

        val fifty = product.toMealEditor("50", "g", "2026-07-31", "lunch")
        val oneFifty = product.toMealEditor("150", "g", "2026-07-31", "lunch")
        val twoPortions = product.toMealEditor("2", "portion", "2026-07-31", "lunch")

        assertEquals("185.25", fifty.ingredients.single().nutrients.energy)
        assertEquals("555.75", oneFifty.ingredients.single().nutrients.energy)
        assertEquals("296.4", twoPortions.ingredients.single().nutrients.energy)
        assertEquals("80", twoPortions.ingredients.single().amount)
        assertNull(twoPortions.totals()["fat"])
        assertEquals(
            "5.05",
            fifty.toPayload(ZoneId.of("Europe/Berlin"))
                .ingredients.single().nutrients.first { it.key == "fiber" }.value,
        )
    }

    @Test
    fun keepsOffSourceUntilUserChangesAValue() {
        val editor = product().toMealEditor("50", "g", "2026-07-31", "lunch")
        val original = editor.toPayload(ZoneId.of("Europe/Berlin"))
        val changedIngredient = editor.ingredients.single().let { ingredient ->
            ingredient.copy(nutrients = ingredient.nutrients.copy(energy = "190"))
        }
        val changed = editor.copy(ingredients = listOf(changedIngredient))
            .toPayload(ZoneId.of("Europe/Berlin"))

        assertEquals("open_food_facts", original.provenanceSource)
        assertEquals("off:4008400214504", original.externalReference)
        val originalEnergy = original.ingredients.single().nutrients.first { it.key == "energy" }
        val changedEnergy = changed.ingredients.single().nutrients.first { it.key == "energy" }
        assertEquals("open_food_facts", originalEnergy.source)
        assertEquals(true, originalEnergy.locked)
        assertEquals("user", changedEnergy.source)
        assertEquals(true, changedEnergy.locked)
    }

    @Test
    fun refusesPortionWithoutReliableBaseUnitConversion() {
        val product = product().copy(servingQuantity = null, servingUnit = null)

        assertThrows(IllegalArgumentException::class.java) {
            product.toMealEditor("1", "portion", "2026-07-31", "snack")
        }
    }

    private fun product() = ProductDto(
        schemaVersion = "off-product/1.0.0",
        barcode = "4008400214504",
        name = "Haferflocken",
        brand = "Fixture",
        quantity = "500 g",
        servingSize = "40 g",
        servingQuantity = "40",
        servingUnit = "g",
        basis = "100g",
        nutrients = listOf(
            NutrientDto(
                key = "energy",
                value = "370.5",
                unit = "kcal",
                basis = "100g",
                source = "open_food_facts",
                locked = true,
            ),
            NutrientDto(
                key = "protein",
                value = "13.2",
                unit = "g",
                basis = "100g",
                source = "open_food_facts",
                locked = true,
            ),
            NutrientDto(
                key = "fiber",
                value = "10.1",
                unit = "g",
                basis = "100g",
                source = "open_food_facts",
                locked = true,
            ),
        ),
        missingCore = listOf("carbohydrates", "fat"),
        language = "de",
        country = "germany",
        imageUrl = null,
        source = "open_food_facts",
        fetchedAt = "2026-07-31T08:00:00Z",
    )
}
