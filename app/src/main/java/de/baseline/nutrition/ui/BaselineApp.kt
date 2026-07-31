package de.baseline.nutrition.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.baseline.nutrition.core.di.AppContainer
import de.baseline.nutrition.ui.navigation.NutritionNavHost
import de.baseline.nutrition.ui.screen.LoadingScreen
import de.baseline.nutrition.ui.screen.TechnicalErrorScreen
import de.baseline.nutrition.ui.theme.BaselineTheme

@Composable
fun BaselineApp(
    container: AppContainer,
    appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(container)),
) {
    val state by appViewModel.state.collectAsStateWithLifecycle()

    BaselineTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            when (val current = state) {
                AppLaunchState.Loading -> LoadingScreen()
                is AppLaunchState.Ready -> key(current.destination) {
                    NutritionNavHost(
                        startDestination = current.destination,
                        container = container,
                        onSessionChanged = appViewModel::restoreSession,
                    )
                }
                AppLaunchState.TechnicalError -> TechnicalErrorScreen(onRetry = appViewModel::restoreSession)
            }
        }
    }
}
