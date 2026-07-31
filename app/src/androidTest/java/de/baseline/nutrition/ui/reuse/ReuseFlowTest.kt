package de.baseline.nutrition.ui.reuse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.IngredientDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.ui.diary.MealScaleControls
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReuseFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun favoriteCreatesEditableDraftForSelectedDay() {
        var reused: FavoriteDto? = null
        var edited: FavoriteDto? = null
        var day: String? = null
        val favorite = favorite()
        compose.setContent {
            BaselineTheme {
                ReuseContent(
                    state = ReuseUiState(favorites = listOf(favorite), loading = false),
                    mode = ReuseMode.Favorites,
                    selectedDay = "2026-08-01",
                    onReuseFavorite = { selected, selectedDay ->
                        reused = selected
                        day = selectedDay
                    },
                    onReuseRecent = { _, _ -> },
                    onEditTemplate = { edited = it },
                    onRename = { _, _ -> },
                    onDelete = {},
                    onRetry = {},
                    onManual = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("favorite-favorite-1").assertIsDisplayed()
        compose.onNodeWithTag("favorite-reuse-favorite-1").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("favorite-1", reused?.id)
            assertEquals("2026-08-01", day)
        }
        compose.onNodeWithTag("favorite-edit-template-favorite-1")
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals("favorite-1", edited?.id) }
    }

    @Test
    fun halfPortionControlAppliesDeterministicFactorBeforeSave() {
        var factor: String? = null
        compose.setContent {
            BaselineTheme {
                MealScaleControls(
                    factor = "1",
                    onFactor = {},
                    onApply = {},
                    onHalf = { factor = "0.5" },
                    onDouble = { factor = "2" },
                )
            }
        }

        compose.onNodeWithTag("meal-scale-half").performClick()
        compose.runOnIdle { assertEquals("0.5", factor) }
    }

    private fun favorite() = FavoriteDto(
        id = "favorite-1",
        originalMealId = "meal-1",
        displayName = "Standardfrühstück",
        meal = MealPayload(
            clientId = "snapshot",
            localDay = "2026-07-31",
            eatenAt = "2026-07-31T08:00:00+02:00",
            timezone = "Europe/Berlin",
            mealType = "breakfast",
            name = "Haferfrühstück",
            captureMethod = "barcode",
            ingredients = listOf(
                IngredientDto(
                    name = "Haferflocken",
                    amount = "100",
                    unit = "g",
                    nutrients = listOf(
                        NutrientDto(
                            key = "energy",
                            value = "370",
                            unit = "kcal",
                            source = "open_food_facts",
                        ),
                    ),
                ),
            ),
            provenanceSource = "open_food_facts",
        ),
        createdAt = "2026-07-31T08:00:00Z",
        updatedAt = "2026-07-31T08:00:00Z",
    )
}
