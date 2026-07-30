package de.baseline.nutrition.data.session

import de.baseline.nutrition.domain.session.SessionRepository
import de.baseline.nutrition.domain.session.SessionState

/**
 * Sicherer Sitzungsspeicher wird mit ID-297 ergänzt. Bis dahin startet die App
 * deterministisch und ohne Demo- oder Platzhalterkonto im abgemeldeten Zustand.
 */
class LocalSessionRepository : SessionRepository {
    override suspend fun restore(): SessionState = SessionState.LoggedOut
}

