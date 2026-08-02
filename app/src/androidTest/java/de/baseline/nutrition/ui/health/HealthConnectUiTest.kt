package de.baseline.nutrition.ui.health

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.health.AndroidHealthConnectGateway
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun partialPermissionStatesStayIndependentlyActionable() {
        val requested = mutableListOf<HealthDataType>()
        val enabled = mutableListOf<Pair<HealthDataType, Boolean>>()
        val reads = mutableListOf<HealthDataType>()
        compose.setContent {
            BaselineTheme {
                HealthConnectContent(
                    state = HealthConnectUiState(
                        loading = false,
                        availability = HealthAvailability.Available,
                        permissions = mapOf(
                            HealthDataType.Steps to HealthPermissionState.Granted,
                            HealthDataType.Sleep to HealthPermissionState.Denied,
                            HealthDataType.ActiveCalories to HealthPermissionState.Disabled,
                            HealthDataType.Exercise to HealthPermissionState.PermanentlyDenied,
                            HealthDataType.Weight to HealthPermissionState.Revoked,
                        ),
                    ),
                    onRequest = { requested += it },
                    onSetEnabled = { type, value -> enabled += type to value },
                    onRead = { reads += it },
                    onRefresh = {},
                    onDisconnect = {},
                    onClose = {},
                )
            }
        }

        HealthDataType.entries.forEach {
            compose.onNodeWithTag("health-${it.name}").assertExists()
        }
        compose.onNodeWithTag("health-read-Steps").performScrollTo().performClick()
        compose.onNodeWithTag("health-disable-Steps").performScrollTo().performClick()
        compose.onNodeWithTag("health-request-Sleep").performScrollTo().performClick()
        compose.onNodeWithTag("health-enable-ActiveCalories").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(HealthDataType.Steps), reads)
            assertEquals(listOf(HealthDataType.Sleep), requested)
            assertEquals(
                listOf(HealthDataType.Steps to false, HealthDataType.ActiveCalories to true),
                enabled,
            )
        }
    }

    @Test
    fun unavailableEnvironmentDoesNotShowDataTypeActions() {
        compose.setContent {
            BaselineTheme {
                HealthConnectContent(
                    state = HealthConnectUiState(
                        loading = false,
                        availability = HealthAvailability.Unavailable,
                    ),
                    onRequest = {},
                    onSetEnabled = { _, _ -> },
                    onRead = {},
                    onRefresh = {},
                    onDisconnect = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("health-availability").assertIsDisplayed()
    }

    @Test
    fun gatewayRequestsOnlyFiveReadPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = AndroidHealthConnectGateway(context)
        val permissions = HealthDataType.entries.map(gateway::permission)

        assertEquals(5, permissions.distinct().size)
        assertTrue(permissions.all { it.startsWith("android.permission.health.READ_") })
        assertFalse(permissions.any { "WRITE" in it })
    }
}
