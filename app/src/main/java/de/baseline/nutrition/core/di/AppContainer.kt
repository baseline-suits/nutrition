package de.baseline.nutrition.core.di

import de.baseline.nutrition.core.config.AppConfiguration
import de.baseline.nutrition.core.coroutines.DefaultDispatcherProvider
import de.baseline.nutrition.core.coroutines.DispatcherProvider
import de.baseline.nutrition.data.session.LocalSessionRepository
import de.baseline.nutrition.domain.session.SessionRepository

class AppContainer(
    val configuration: AppConfiguration = AppConfiguration.current(),
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    val sessionRepository: SessionRepository = LocalSessionRepository(),
)

