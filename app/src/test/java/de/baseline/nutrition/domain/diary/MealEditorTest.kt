package de.baseline.nutrition.domain.diary

import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.diary.PrivateFoodDto
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealEditorTest {
    @Test
    fun parsesGermanAndRussianDecimals() {
        assertEquals(BigDecimal("1234.5"), parseLocalizedDecimal("1.234,5"))
        assertEquals(BigDecimal("12.75"), parseLocalizedDecimal("12,75"))
        assertEquals(BigDecimal("12.75"), parseLocalizedDecimal("12.75"))
        assertNull(parseLocalizedDecimal("kein Wert"))
    }

    @Test
    fun scalesPerHundredValuesWithoutInventingMissingMacros() {
        val food = PrivateFoodDto(
            id = "food", name = "Hafer", defaultAmount = "100", unit = "g", basis = "100g",
            nutrients = listOf(NutrientDto(key = "energy", value = "370", unit = "kcal", basis = "100g")),
            version = 1,
        )
        assertEquals("185", food.toIngredient("50").nutrients.energy)
        assertEquals("555", food.toIngredient("150").nutrients.energy)
        assertEquals("", food.toIngredient("50").nutrients.protein)
    }

    @Test
    fun keepsDirectAndIngredientTotalsDeterministic() {
        val draft = MealEditorDraft(
            name = "Test", nutrients = NutrientFields(energy = "100,5"),
            ingredients = listOf(IngredientDraft(name = "Teil", nutrients = NutrientFields(energy = "49,5"))),
        )
        assertEquals(BigDecimal("150.0"), draft.totals()["energy"])
        assertNull(draft.totals()["protein"])
    }

    @Test
    fun amountChangesScaleKnownValuesOnly() {
        val ingredient = IngredientDraft(
            name = "Joghurt", amount = "100",
            nutrients = NutrientFields(energy = "80", protein = "5"),
        ).withScaledAmount("150,5")
        assertEquals("120.4", ingredient.nutrients.energy)
        assertEquals("7.525", ingredient.nutrients.protein)
        assertEquals("", ingredient.nutrients.fat)
    }

    @Test
    fun scalesReusableMealHalfAndDoubleIncludingMicronutrients() {
        val nutrients = NutrientFields(
            energy = "100",
            additional = listOf(
                NutrientDto(
                    key = "fiber",
                    value = "8",
                    unit = "g",
                    source = "open_food_facts",
                    locked = true,
                ),
            ),
            sources = mapOf("energy" to "open_food_facts"),
            locks = mapOf("energy" to true),
            originalValues = mapOf("energy" to "100"),
        )
        val original = MealEditorDraft(
            name = "Vorlage",
            nutrients = nutrients,
            ingredients = listOf(
                IngredientDraft(name = "Teil", amount = "100", nutrients = nutrients),
            ),
        )

        val half = original.scaled("0.5").toPayload()
        val doubled = original.scaled("2").toPayload()

        assertEquals("50", half.nutrients.first { it.key == "energy" }.value)
        assertEquals("4", half.nutrients.first { it.key == "fiber" }.value)
        assertEquals("50", half.ingredients.single().amount)
        assertEquals("200", doubled.nutrients.first { it.key == "energy" }.value)
        assertEquals("16", doubled.nutrients.first { it.key == "fiber" }.value)
        assertEquals("200", doubled.ingredients.single().amount)
        assertEquals(
            "open_food_facts",
            half.nutrients.first { it.key == "energy" }.source,
        )
        assertEquals(true, half.nutrients.first { it.key == "energy" }.locked)
        assertEquals("100", original.ingredients.single().amount)
    }
}
