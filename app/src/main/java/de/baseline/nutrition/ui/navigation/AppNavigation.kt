package de.baseline.nutrition.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.baseline.nutrition.ui.screen.AuthenticatedScreen
import de.baseline.nutrition.ui.screen.LoggedOutScreen
import de.baseline.nutrition.ui.screen.OnboardingScreen
import kotlinx.serialization.Serializable

enum class AppStartDestination {
    LoggedOut,
    Onboarding,
    Authenticated,
}

@Serializable
private data object LoggedOutRoute

@Serializable
private data object OnboardingRoute

@Serializable
private data object AuthenticatedRoute

@Composable
fun NutritionNavHost(startDestination: AppStartDestination) {
    val navController = rememberNavController()
    val startRoute: Any = when (startDestination) {
        AppStartDestination.LoggedOut -> LoggedOutRoute
        AppStartDestination.Onboarding -> OnboardingRoute
        AppStartDestination.Authenticated -> AuthenticatedRoute
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable<LoggedOutRoute> { LoggedOutScreen() }
        composable<OnboardingRoute> { OnboardingScreen() }
        composable<AuthenticatedRoute> { AuthenticatedScreen() }
    }
}

