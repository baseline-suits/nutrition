package de.baseline.nutrition.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.baseline.nutrition.core.di.AppContainer
import de.baseline.nutrition.ui.screen.AccountHomeScreen
import de.baseline.nutrition.ui.screen.AuthenticatedScreen
import de.baseline.nutrition.ui.screen.AuthScreen
import de.baseline.nutrition.ui.screen.LoggedOutScreen
import de.baseline.nutrition.ui.screen.OnboardingScreen
import de.baseline.nutrition.ui.screen.ProfileOnboardingScreen
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
fun NutritionNavHost(
    startDestination: AppStartDestination,
    container: AppContainer? = null,
    onSessionChanged: () -> Unit = {},
) {
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
        composable<LoggedOutRoute> {
            if (container == null) LoggedOutScreen()
            else AuthScreen(
                repository = container.authRepository,
                serverSettings = container.serverSettings,
                ioDispatcher = container.dispatchers.io,
                onAuthenticated = onSessionChanged,
            )
        }
        composable<OnboardingRoute> {
            if (container == null) OnboardingScreen()
            else ProfileOnboardingScreen(
                container.profileRepository,
                container.onboardingDraftStore,
                container.dispatchers.io,
                onSessionChanged,
            )
        }
        composable<AuthenticatedRoute> {
            if (container == null) AuthenticatedScreen()
            else AccountHomeScreen(
                container.authRepository,
                container.diaryRepository,
                container.captureRepository,
                container.productRepository,
                container.dispatchers.io,
                onSessionChanged,
            )
        }
    }
}
