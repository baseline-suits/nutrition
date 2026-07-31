package de.baseline.nutrition.ui.capture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    private fun assertEntry(tag: String, expected: CaptureMode) {
        var selected: CaptureMode? = null
        compose.setContent {
            BaselineTheme {
                QuickAddMenu(
                    onMode = { selected = it },
                    onManual = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(expected, selected) }
    }
}
