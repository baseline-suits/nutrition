package de.baseline.nutrition.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.diary.HistoryAggregateDto
import de.baseline.nutrition.data.diary.HistoryDayDto
import de.baseline.nutrition.data.diary.HistoryResponseDto
import de.baseline.nutrition.data.diary.HistoryWeekDto
import de.baseline.nutrition.ui.theme.BaselineTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistoryFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyRangeOffersSevenAndThirtyDays() {
        var range = 0
        compose.setContent {
            BaselineTheme {
                HistoryContent(
                    state = HistoryUiState(data = history(empty = true), loading = false),
                    onRange = { range = it },
                    onSelectDay = {},
                    onRetry = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("history-empty").assertIsDisplayed()
        compose.onNodeWithTag("history-range-30").performClick()
        compose.runOnIdle { assertEquals(30, range) }
    }

    @Test
    fun calendarMarksAndOpensHistoricalDay() {
        var selected: LocalDate? = null
        compose.setContent {
            BaselineTheme {
                HistoryContent(
                    state = HistoryUiState(data = history(), loading = false),
                    onRange = {},
                    onSelectDay = { selected = it },
                    onRetry = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("history-calories").assertIsDisplayed()
        compose.onNodeWithTag("history-macros").assertIsDisplayed()
        compose.onNodeWithTag("history-day-2026-07-02").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 7, 2), selected) }
    }

    @Test
    fun partialWeekAndCachedStateRemainReadableWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                BaselineTheme {
                    HistoryContent(
                        state = HistoryUiState(data = history(), loading = false, cached = true),
                        onRange = {},
                        onSelectDay = {},
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("history-cached").assertIsDisplayed()
        compose.onNodeWithTag("history-week-2026-06-29").performScrollTo().assertIsDisplayed()
    }

    private fun history(empty: Boolean = false): HistoryResponseDto {
        val targets = DailyBudgetDto(
            timezone = "Europe/Berlin",
            energy = "2000",
            protein = "120",
            carbohydrates = "220",
            fat = "70",
            manual = true,
            weightKg = "80",
            activityLevel = "active",
            calculationVersion = "manual-v1",
        )
        val days = (1..7).map { day ->
            val tracked = !empty && day <= 4
            HistoryDayDto(
                localDay = "2026-07-${day.toString().padStart(2, '0')}",
                status = when {
                    !tracked -> "none"
                    day == 2 -> "partial"
                    else -> "complete"
                },
                totals = if (tracked) {
                    mapOf(
                        "energy" to "500",
                        "protein" to "30",
                        "carbohydrates" to "60",
                        "fat" to "20",
                    )
                } else {
                    emptyMap()
                },
                coverage = if (tracked) mapOf("energy" to 1) else emptyMap(),
                mealCount = if (tracked) 1 else 0,
                targets = targets.takeIf { tracked },
            )
        }
        val trackedDays = if (empty) 0 else 4
        val aggregate = aggregate(trackedDays)
        return HistoryResponseDto(
            start = "2026-07-01",
            end = "2026-07-07",
            totalDays = 7,
            days = days,
            summary = aggregate,
            weeks = listOf(
                HistoryWeekDto(
                    start = "2026-06-29",
                    end = "2026-07-05",
                    trackedDays = trackedDays,
                    completeDays = if (empty) 0 else 3,
                    partialDays = if (empty) 0 else 1,
                    averages = aggregate.averages,
                    averageDenominators = aggregate.averageDenominators,
                    targetAverages = aggregate.targetAverages,
                    targetDenominators = aggregate.targetDenominators,
                    goalPercentages = aggregate.goalPercentages,
                    goalDenominators = aggregate.goalDenominators,
                ),
            ),
        )
    }

    private fun aggregate(trackedDays: Int) = HistoryAggregateDto(
        trackedDays = trackedDays,
        completeDays = if (trackedDays == 0) 0 else 3,
        partialDays = if (trackedDays == 0) 0 else 1,
        averages = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to "500",
            "protein" to "30",
            "carbohydrates" to "60",
            "fat" to "20",
        ),
        averageDenominators = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to trackedDays,
            "protein" to trackedDays,
            "carbohydrates" to trackedDays,
            "fat" to trackedDays,
        ),
        targetAverages = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to "2000",
            "protein" to "120",
            "carbohydrates" to "220",
            "fat" to "70",
        ),
        targetDenominators = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to trackedDays,
            "protein" to trackedDays,
            "carbohydrates" to trackedDays,
            "fat" to trackedDays,
        ),
        goalPercentages = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to "25",
            "protein" to "25",
            "carbohydrates" to "27.27",
            "fat" to "28.57",
        ),
        goalDenominators = if (trackedDays == 0) emptyMap() else mapOf(
            "energy" to trackedDays,
            "protein" to trackedDays,
            "carbohydrates" to trackedDays,
            "fat" to trackedDays,
        ),
    )
}
