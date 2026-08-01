package de.baseline.nutrition.ui.diary

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalorieBudgetUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dynamicBudgetShowsTransparentCalculationAndCanSwitchMode() {
        var selectedMode: String? = null
        compose.setContent {
            BaselineTheme {
                CalorieBudgetBreakdown(
                    budget = budget(status = "ready"),
                    eatenEnergy = "875.4",
                    canChangeMode = true,
                    healthConnectionLoading = false,
                    healthAvailability = HealthAvailability.Available,
                    activeCaloriesPermission = HealthPermissionState.Granted,
                    onModeChange = { selectedMode = it },
                )
            }
        }

        compose.onNodeWithTag("budget-activity-status-ready").assertIsDisplayed()
        compose.onNodeWithTag("budget-mode-fixed").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals("fixed", selectedMode) }
        compose.onNodeWithTag("budget-details-toggle").performClick()
        compose.onNodeWithTag("budget-calculation").assertIsDisplayed()
    }

    @Test
    fun activityStatesCoverSyncPartialConflictAndLocalDisable() {
        val status = mutableStateOf("not_synced")
        val permission = mutableStateOf<HealthPermissionState?>(HealthPermissionState.Granted)
        compose.setContent {
            BaselineTheme {
                CalorieBudgetBreakdown(
                    budget = budget(status.value),
                    eatenEnergy = null,
                    canChangeMode = false,
                    healthConnectionLoading = false,
                    healthAvailability = HealthAvailability.Available,
                    activeCaloriesPermission = permission.value,
                    onModeChange = {},
                )
            }
        }

        listOf("not_synced", "missing", "partial", "conflict", "ready").forEach { value ->
            compose.runOnIdle { status.value = value }
            compose.onNodeWithTag("budget-activity-status-$value").assertIsDisplayed()
        }
        compose.runOnIdle { permission.value = HealthPermissionState.Disabled }
        compose.onNodeWithTag("budget-activity-status-disabled").assertIsDisplayed()
    }

    private fun budget(status: String) = DailyBudgetDto(
        timezone = "Europe/Berlin",
        budgetMode = "dynamic",
        baseEnergy = "2000",
        energy = "2225",
        protein = "120",
        carbohydrates = "220",
        fat = "70",
        manual = true,
        calculationVersion = "manual-v1",
        activityStatus = status,
        activityEnergy = "450.5",
        activityFactor = "0.5",
        activityCapEnergy = "500",
        activityContributionEnergy = "225.25",
        budgetCalculationVersion = "active-calories-budget-v1",
        budgetUpdatedAt = "2026-08-01T12:00:00Z",
    )
}
