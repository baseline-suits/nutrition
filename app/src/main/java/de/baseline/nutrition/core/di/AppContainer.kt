package de.baseline.nutrition.core.di

import android.content.Context
import de.baseline.nutrition.core.config.AppConfiguration
import de.baseline.nutrition.core.config.BuildEnvironment
import de.baseline.nutrition.core.coroutines.DefaultDispatcherProvider
import de.baseline.nutrition.core.coroutines.DispatcherProvider
import de.baseline.nutrition.data.auth.HttpAuthRepository
import de.baseline.nutrition.data.capture.CaptureRepository
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.data.health.AndroidHealthConnectGateway
import de.baseline.nutrition.data.health.DefaultHealthRepository
import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ServerSettingsStore
import de.baseline.nutrition.data.profile.ProfileRepository
import de.baseline.nutrition.data.product.ProductRepository
import de.baseline.nutrition.data.profile.OnboardingDraftStore
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.data.sync.ApiMealRemoteDataSource
import de.baseline.nutrition.data.sync.EncryptedMealQueueStore
import de.baseline.nutrition.data.sync.MealSyncManager
import de.baseline.nutrition.data.sync.WorkManagerMealSyncScheduler
import de.baseline.nutrition.domain.auth.AuthRepository

class AppContainer(
    context: Context,
    val configuration: AppConfiguration = AppConfiguration.current(),
    val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) {
    private val sessionStore = SecureSessionStore(context)
    val serverSettings = ServerSettingsStore(
        context = context,
        defaultUrl = configuration.apiBaseUrl,
        allowCleartext = configuration.environment == BuildEnvironment.Local,
        sessionStore = sessionStore,
    )
    private val api = ApiClient(serverSettings::currentUrl, sessionStore)
    private val syncScheduler = WorkManagerMealSyncScheduler(context)
    private val mealQueueStore = EncryptedMealQueueStore(context)
    private val mealSyncManager = MealSyncManager(
        store = mealQueueStore,
        remote = ApiMealRemoteDataSource(api),
        currentUserId = sessionStore::readUserId,
        scheduler = syncScheduler,
    )
    val diaryRepository = DiaryRepository(api, sessionStore, mealSyncManager)
    val healthRepository = DefaultHealthRepository(
        AndroidHealthConnectGateway(context.applicationContext),
        sessionStore,
    )
    val authRepository: AuthRepository = HttpAuthRepository(
        api,
        sessionStore,
        syncScheduler,
        diaryRepository::clearLocalData,
    )
    val sessionRepository = authRepository
    val profileRepository = ProfileRepository(api)
    val onboardingDraftStore = OnboardingDraftStore(sessionStore)
    val captureRepository = CaptureRepository(api)
    val productRepository = ProductRepository(api)
}
