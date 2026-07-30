package de.baseline.nutrition.domain.session

sealed interface SessionState {
    data object LoggedOut : SessionState
    data object OnboardingOpen : SessionState
    data object Authenticated : SessionState
}

interface SessionRepository {
    suspend fun restore(): SessionState
}

