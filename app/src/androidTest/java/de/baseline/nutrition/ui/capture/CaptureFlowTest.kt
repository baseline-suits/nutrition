package de.baseline.nutrition.ui.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CaptureFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun descriptionHasDedicatedEntry() {
        assertEntry("capture-description", CaptureMode.Description)
    }

    @Test
    fun cameraHasDedicatedEntry() {
        assertEntry("capture-camera", CaptureMode.Camera)
    }

    @Test
    fun importHasDedicatedEntry() {
        assertEntry("capture-import", CaptureMode.Import)
    }

    @Test
    fun manualHasDedicatedEntry() {
        var selected = false
        compose.setContent {
            BaselineTheme {
                QuickAddMenu(
                    onMode = {},
                    onBarcode = {},
                    onFavorites = {},
                    onRecent = {},
                    onManual = { selected = true },
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("capture-manual").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, selected) }
    }

    @Test
    fun photoAnalysisOffersBothPhotoSourcesAndRequiresAPhoto() {
        var action = ""
        compose.setContent {
            BaselineTheme(darkTheme = true) {
                PhotoFlow(
                    state = CaptureUiState(mode = CaptureMode.Camera),
                    localError = null,
                    onText = {},
                    onMealType = {},
                    onCamera = { action = "camera" },
                    onImport = { action = "import" },
                    onAnalyze = { action = "analyze" },
                    onCancel = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("capture-photo-camera").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("camera", action) }
        compose.onNodeWithTag("capture-photo-import").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("import", action) }
        compose.onNodeWithTag("capture-analyze-photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("capture-analysis-status").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun barcodeHasDedicatedEntry() {
        var selected = false
        compose.setContent {
            BaselineTheme {
                QuickAddMenu(
                    onMode = {},
                    onBarcode = { selected = true },
                    onFavorites = {},
                    onRecent = {},
                    onManual = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("capture-barcode").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, selected) }
    }

    @Test
    fun favoritesAndRecentHaveDedicatedEntries() {
        var selected = ""
        compose.setContent {
            BaselineTheme {
                QuickAddMenu(
                    onMode = {},
                    onBarcode = {},
                    onFavorites = { selected = "favorites" },
                    onRecent = { selected = "recent" },
                    onManual = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("capture-favorites").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("favorites", selected) }
        compose.onNodeWithTag("capture-recent").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("recent", selected) }
    }

    private fun assertEntry(tag: String, expected: CaptureMode) {
        var selected: CaptureMode? = null
        compose.setContent {
            BaselineTheme {
                QuickAddMenu(
                    onMode = { selected = it },
                    onBarcode = {},
                    onFavorites = {},
                    onRecent = {},
                    onManual = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(expected, selected) }
    }
}
