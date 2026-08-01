package de.baseline.nutrition.ui.overview

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.baseline.nutrition.data.diary.DaySummaryDto
import de.baseline.nutrition.data.diary.DiaryTargets
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.ui.components.BaselineAppShell
import de.baseline.nutrition.ui.components.MainDestination
import de.baseline.nutrition.ui.diary.DiaryUiState
import de.baseline.nutrition.ui.theme.BaselineTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OverviewScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsRealDiaryDataAndOpensPrimaryActions() {
        var action = ""
        compose.setContent {
            BaselineTheme(darkTheme = true) {
                OverviewScreen(
                    state = diaryState(),
                    onMeal = { action = "meal:${it.id}" },
                    onAddMeal = { action = "add" },
                    onRefresh = {},
                )
            }
        }

        compose.onNodeWithTag("overview-calories").assertIsDisplayed()
        compose.onNodeWithText("Haferflocken mit Beeren").assertIsDisplayed()
        compose.onNodeWithContentDescription("Haferflocken mit Beeren öffnen").performClick()
        compose.runOnIdle { assertEquals("meal:meal-1", action) }
        compose.onNodeWithTag("overview-add-meal").performClick()
        compose.runOnIdle { assertEquals("add", action) }
    }

    @Test
    fun bottomNavigationSelectsAllMainDestinations() {
        compose.setContent {
            var selected by remember { mutableStateOf(MainDestination.Overview) }
            BaselineTheme(darkTheme = true) {
                BaselineAppShell(selected = selected, onSelected = { selected = it }) {
                    Text("content-${selected.name}")
                }
            }
        }

        listOf(
            "Tagebuch" to MainDestination.Diary,
            "Statistiken" to MainDestination.Statistics,
            "Profil" to MainDestination.Profile,
            "Übersicht" to MainDestination.Overview,
        ).forEach { (label, destination) ->
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithText("content-${destination.name}").assertIsDisplayed()
        }
    }

    private fun diaryState(): DiaryUiState {
        val day = LocalDate.of(2026, 8, 1)
        return DiaryUiState(
            selectedDay = day,
            meals = listOf(
                MealDto(
                    id = "meal-1",
                    clientId = "client-1",
                    localDay = day.toString(),
                    eatenAt = "2026-08-01T08:15:00Z",
                    timezone = "Europe/Berlin",
                    mealType = "breakfast",
                    name = "Haferflocken mit Beeren",
                    captureMethod = "manual",
                    nutrients = listOf(
                        NutrientDto(key = "energy", value = "450", unit = "kcal"),
                    ),
                    version = 1,
                    updatedAt = "2026-08-01T08:16:00Z",
                ),
            ),
            summary = DaySummaryDto(
                localDay = day.toString(),
                totals = mapOf(
                    "energy" to "1250",
                    "protein" to "82",
                    "carbohydrates" to "136",
                    "fat" to "41",
                ),
                available = listOf("energy", "protein", "carbohydrates", "fat"),
                missingCore = emptyList(),
                mealCount = 1,
            ),
            targets = DiaryTargets(
                energy = "2000",
                protein = "130",
                carbohydrates = "220",
                fat = "65",
            ),
            loading = false,
        )
    }
}
