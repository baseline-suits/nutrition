package de.baseline.nutrition.ui.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.baseline.nutrition.ui.theme.BaselineTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiaryHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsLocalizedDayAndConnectsDateNavigation() {
        var action = ""
        compose.setContent {
            BaselineTheme(darkTheme = true) {
                Column {
                    DiaryHeader(
                        selectedDay = LocalDate.of(2026, 8, 1),
                        onPrevious = { action = "previous" },
                        onNext = { action = "next" },
                        onHistory = { action = "history" },
                    )
                }
            }
        }

        compose.onNodeWithText("Tagebuch").assertIsDisplayed()
        compose.onNodeWithText("Samstag, 1. August").assertIsDisplayed()
        compose.onNodeWithTag("diary-previous-day").performClick()
        compose.runOnIdle { assertEquals("previous", action) }
        compose.onNodeWithTag("diary-selected-day").performClick()
        compose.runOnIdle { assertEquals("history", action) }
        compose.onNodeWithTag("diary-next-day").performClick()
        compose.runOnIdle { assertEquals("next", action) }
    }
}
