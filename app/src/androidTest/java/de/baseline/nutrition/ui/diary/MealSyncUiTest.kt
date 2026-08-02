package de.baseline.nutrition.ui.diary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.baseline.nutrition.data.sync.MealOperationStatus
import de.baseline.nutrition.data.sync.MealSyncInfo
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MealSyncUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun permanentFailureOffersRetryEditAndDiscard() {
        var action = ""
        compose.setContent {
            BaselineTheme {
                MealSyncControls(
                    mealId = "meal-1",
                    info = MealSyncInfo(
                        operationId = "operation-1",
                        status = MealOperationStatus.FailedPermanent,
                        attempts = 1,
                        errorCode = "validation_error",
                    ),
                    onRetry = { action = "retry:$it" },
                    onEdit = { action = "edit" },
                    onDiscard = { action = "discard:$it" },
                    onKeepServer = {},
                    onApplyMine = {},
                )
            }
        }

        compose.onNodeWithTag("meal-sync-status-meal-1").assertIsDisplayed()
        compose.onNodeWithTag("meal-sync-actions-meal-1").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.runOnIdle { assertEquals("retry:operation-1", action) }
    }

    @Test
    fun conflictRequiresExplicitServerOrLocalChoice() {
        var action = ""
        compose.setContent {
            BaselineTheme {
                MealSyncControls(
                    mealId = "meal-2",
                    info = MealSyncInfo(
                        operationId = "operation-2",
                        status = MealOperationStatus.Conflict,
                        attempts = 1,
                    ),
                    onRetry = {},
                    onEdit = {},
                    onDiscard = {},
                    onKeepServer = { action = "server:$it" },
                    onApplyMine = { action = "mine:$it" },
                )
            }
        }

        compose.onNodeWithTag("meal-conflict-actions-meal-2").assertIsDisplayed()
        compose.onNodeWithText("Serverstand behalten").performClick()
        compose.runOnIdle { assertEquals("server:operation-2", action) }
        compose.onNodeWithText("Meine Version anwenden").performClick()
        compose.runOnIdle { assertEquals("mine:operation-2", action) }
    }
}
