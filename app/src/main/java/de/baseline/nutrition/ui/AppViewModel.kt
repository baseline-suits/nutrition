package de.baseline.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.core.di.AppContainer
import de.baseline.nutrition.domain.session.SessionRepository
import de.baseline.nutrition.domain.session.SessionState
import de.baseline.nutrition.ui.navigation.AppStartDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppLaunchState {
    data object Loading : AppLaunchState
    data class Ready(val destination: AppStartDestination) : AppLaunchState
    data object TechnicalError : AppLaunchState
}

class AppViewModel(
    private val sessionRepository: SessionRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AppLaunchState>(AppLaunchState.Loading)
    val state: StateFlow<AppLaunchState> = mutableState.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        mutableState.value = AppLaunchState.Loading
        viewModelScope.launch {
            mutableState.value = runCatching {
                withContext(ioDispatcher) { sessionRepository.restore() }
            }.fold(
                onSuccess = { session ->
                    AppLaunchState.Ready(
                        when (session) {
                            SessionState.LoggedOut -> AppStartDestination.LoggedOut
                            SessionState.OnboardingOpen -> AppStartDestination.Onboarding
                            SessionState.Authenticated -> AppStartDestination.Authenticated
                        },
                    )
                },
                onFailure = { AppLaunchState.TechnicalError },
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(
                        sessionRepository = container.sessionRepository,
                        ioDispatcher = container.dispatchers.io,
                    ) as T
            }
    }
}

