package de.baseline.nutrition.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startetAbgemeldet() {
        setStartDestination(AppStartDestination.LoggedOut)
        composeRule.onNodeWithTag("logged_out_state").assertIsDisplayed()
    }

    @Test
    fun startetImOnboarding() {
        setStartDestination(AppStartDestination.Onboarding)
        composeRule.onNodeWithTag("onboarding_state").assertIsDisplayed()
    }

    @Test
    fun startetAngemeldet() {
        setStartDestination(AppStartDestination.Authenticated)
        composeRule.onNodeWithTag("authenticated_state").assertIsDisplayed()
    }

    private fun setStartDestination(destination: AppStartDestination) {
        composeRule.setContent {
            BaselineTheme {
                NutritionNavHost(startDestination = destination)
            }
        }
    }
}

