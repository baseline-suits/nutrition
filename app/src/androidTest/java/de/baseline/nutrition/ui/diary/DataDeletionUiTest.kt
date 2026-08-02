package de.baseline.nutrition.ui.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DataDeletionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun accountDeletionRequiresPasswordAndExplicitConfirmation() {
        var submitted = false
        compose.setContent {
            var password by remember { mutableStateOf("") }
            var confirmed by remember { mutableStateOf(false) }
            BaselineTheme {
                AccountDeletionDialog(
                    password = password,
                    confirmed = confirmed,
                    busy = false,
                    error = null,
                    onPassword = { password = it },
                    onConfirmed = { confirmed = it },
                    onConfirm = { submitted = true },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("confirm-delete-account").assertIsNotEnabled()
        compose.onNodeWithTag("delete-account-password")
            .performTextInput("a-secure-password")
        compose.onNodeWithTag("delete-account-confirmation").performClick()
        compose.onNodeWithTag("confirm-delete-account").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test
    fun photoDeletionNeedsConfirmation() {
        var deleted = false
        compose.setContent {
            BaselineTheme {
                PhotoDeletionDialog(
                    meal = meal(attachmentId = "upload-1"),
                    onConfirm = { deleted = true },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("confirm-delete-photo").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun mealCardShowsDeletedPhotoWithoutBrokenDeleteAction() {
        compose.setContent {
            BaselineTheme {
                MealCard(
                    meal = meal(photoDeleted = true),
                    syncInfo = null,
                    isFavorite = false,
                    onToggleFavorite = {},
                    onEdit = {},
                    onDuplicate = {},
                    onDeletePhoto = {},
                    onDelete = {},
                    onRetrySync = {},
                    onDiscardSync = {},
                    onKeepServer = {},
                    onApplyMine = {},
                )
            }
        }

        compose.onNodeWithTag("photo-deleted-meal-1").assertIsDisplayed()
        compose.onAllNodesWithTag("delete-photo-meal-1").assertCountEquals(0)
    }

    private fun meal(
        attachmentId: String? = null,
        photoDeleted: Boolean = false,
    ) = MealDto(
        id = "meal-1",
        clientId = "client-1",
        localDay = "2026-07-31",
        eatenAt = "2026-07-31T12:00:00+02:00",
        timezone = "Europe/Berlin",
        mealType = "lunch",
        name = "Kartoffeln",
        captureMethod = "camera",
        attachmentId = attachmentId,
        photoDeleted = photoDeleted,
        version = 1,
        updatedAt = "2026-07-31T10:00:00Z",
    )
}
