package de.baseline.nutrition.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.settings.SettingsAccountDto
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthCapability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.theme.BaselineTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalorieBudgetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsRealBudgetValuesAndChangesModeOnlyAfterConfirmation() {
        var changedMode: String? = null
        compose.setContent {
            BaselineTheme(darkTheme = true) {
                CalorieBudgetContent(
                    state = connectedState(),
                    budget = budget(),
                    loading = false,
                    updateFailed = false,
                    onModeChange = { changedMode = it },
                    onOpenHealth = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("budget-health-connect").assertIsDisplayed()
        compose.onNodeWithTag("budget-current-calculation")
            .assertTextContains("2.000", substring = true)
            .assertTextContains("225", substring = true)
            .assertTextContains("2.225", substring = true)
        compose.onNodeWithTag("settings-budget-mode-fixed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, changedMode) }
        compose.onNodeWithTag("budget-save-mode").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("fixed", changedMode) }
    }

    @Test
    fun missingHealthDataNeverCreatesActivityCalories() {
        compose.setContent {
            BaselineTheme(darkTheme = true) {
                CalorieBudgetContent(
                    state = connectedState().copy(healthAggregates = emptyList()),
                    budget = budget().copy(
                        activityStatus = "missing",
                        activityEnergy = null,
                        activityContributionEnergy = "0",
                        energy = "2000",
                    ),
                    loading = false,
                    updateFailed = false,
                    onModeChange = {},
                    onOpenHealth = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("budget-current-calculation")
            .assertTextContains("0", substring = true)
            .assertTextContains("2.000", substring = true)
    }

    private fun connectedState() = SettingsUiState(
        account = SettingsAccountDto(
            id = "user-1",
            username = "budget-user",
            locale = "de",
            timezone = "Europe/Berlin",
            onboardingComplete = true,
        ),
        profileDraft = SettingsProfileDraft(
            targetKcal = "2000",
            targetProtein = "120",
            targetCarbs = "220",
            targetFat = "70",
            calorieBudgetMode = "dynamic",
            timezone = "Europe/Berlin",
        ),
        healthConnection = HealthConnectionSnapshot(
            availability = HealthAvailability.Available,
            capabilities = mapOf(
                HealthDataType.ActiveCalories to HealthCapability(
                    HealthDataType.ActiveCalories,
                    HealthPermissionState.Granted,
                ),
            ),
        ),
        healthAggregates = listOf(
            aggregate("active_calories", "450.5", "kcal"),
            aggregate("steps", "6284", "count"),
            aggregate("exercise", "1800", "s"),
        ),
        appDetails = SettingsAppDetails("0.1.0-debug", 1, "local"),
    )

    private fun aggregate(type: String, value: String, unit: String) = SettingsHealthAggregateDto(
        dataType = type,
        localDay = LocalDate.now().toString(),
        status = "ready",
        value = value,
        unit = unit,
    )

    private fun budget() = DailyBudgetDto(
        timezone = "Europe/Berlin",
        budgetMode = "dynamic",
        baseEnergy = "2000",
        energy = "2225",
        protein = "120",
        carbohydrates = "220",
        fat = "70",
        manual = true,
        calculationVersion = "manual-v1",
        activityStatus = "ready",
        activityEnergy = "450.5",
        activityFactor = "0.5",
        activityCapEnergy = "500",
        activityContributionEnergy = "225.25",
        budgetCalculationVersion = "active-calories-budget-v1",
    )
}
