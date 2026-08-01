package de.baseline.nutrition.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import de.baseline.nutrition.BuildConfig
import de.baseline.nutrition.data.capture.CaptureRepository
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.data.product.ProductRepository
import de.baseline.nutrition.data.settings.SettingsDataSource
import de.baseline.nutrition.data.settings.SettingsLocalDataSource
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.health.HealthRepository
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.ui.barcode.BarcodeScreen
import de.baseline.nutrition.ui.barcode.BarcodeViewModel
import de.baseline.nutrition.ui.diary.DiaryScreen
import de.baseline.nutrition.ui.diary.DiaryError
import de.baseline.nutrition.ui.diary.DiaryViewModel
import de.baseline.nutrition.ui.history.HistoryScreen
import de.baseline.nutrition.ui.history.HistoryViewModel
import de.baseline.nutrition.ui.health.HealthConnectScreen
import de.baseline.nutrition.ui.health.HealthConnectViewModel
import de.baseline.nutrition.ui.LocaleController
import de.baseline.nutrition.ui.capture.CaptureScreen
import de.baseline.nutrition.ui.capture.CaptureViewModel
import de.baseline.nutrition.ui.reuse.ReuseMode
import de.baseline.nutrition.ui.reuse.ReuseScreen
import de.baseline.nutrition.ui.reuse.ReuseViewModel
import de.baseline.nutrition.ui.settings.SettingsAppDetails
import de.baseline.nutrition.ui.settings.SettingsScreen
import de.baseline.nutrition.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineDispatcher

@Composable
fun AccountHomeScreen(
    authRepository: AuthRepository,
    diaryRepository: DiaryRepository,
    captureRepository: CaptureRepository,
    productRepository: ProductRepository,
    healthRepository: HealthRepository,
    settingsRepository: SettingsDataSource,
    settingsLocalDataSource: SettingsLocalDataSource,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(diaryRepository, ioDispatcher))
    val diaryState by diaryViewModel.state.collectAsState()
    val captureViewModel: CaptureViewModel = viewModel(
        factory = CaptureViewModel.factory(captureRepository, ioDispatcher),
    )
    val barcodeViewModel: BarcodeViewModel = viewModel(
        factory = BarcodeViewModel.factory(productRepository, ioDispatcher),
    )
    val reuseViewModel: ReuseViewModel = viewModel(
        factory = ReuseViewModel.factory(diaryRepository, ioDispatcher),
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(diaryRepository, ioDispatcher),
    )
    val healthViewModel: HealthConnectViewModel = viewModel(
        factory = HealthConnectViewModel.factory(healthRepository, ioDispatcher),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            remote = settingsRepository,
            local = settingsLocalDataSource,
            healthRepository = healthRepository,
            authRepository = authRepository,
            ioDispatcher = ioDispatcher,
            applyLocale = LocaleController::apply,
            appDetails = SettingsAppDetails(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                environment = BuildConfig.ENVIRONMENT,
            ),
        ),
    )
    val healthState by healthViewModel.state.collectAsState()
    LaunchedEffect(diaryState.error, diaryState.sync.overview.authRequired) {
        if (diaryState.error == DiaryError.Session || diaryState.sync.overview.authRequired) {
            onLoggedOut()
        }
    }
    var activeFlow by rememberSaveable { mutableStateOf("diary") }
    when (activeFlow) {
        "capture" -> CaptureScreen(
            viewModel = captureViewModel,
            selectedDay = diaryState.selectedDay.toString(),
            onDraftReady = { draft ->
                captureViewModel.complete()
                activeFlow = "diary"
                diaryViewModel.openDraft(draft)
            },
            onBarcode = {
                captureViewModel.complete()
                activeFlow = "barcode"
            },
            onFavorites = {
                captureViewModel.complete()
                activeFlow = "favorites"
            },
            onRecent = {
                captureViewModel.complete()
                activeFlow = "recent"
            },
            onManual = {
                captureViewModel.complete()
                activeFlow = "diary"
                diaryViewModel.newManualEntry()
            },
            onClose = {
                captureViewModel.complete()
                activeFlow = "diary"
            },
        )
        "barcode" -> BarcodeScreen(
            viewModel = barcodeViewModel,
            selectedDay = diaryState.selectedDay.toString(),
            onDraftReady = { draft ->
                barcodeViewModel.complete()
                activeFlow = "diary"
                diaryViewModel.openDraft(draft)
            },
            onManual = {
                barcodeViewModel.complete()
                activeFlow = "diary"
                diaryViewModel.newManualEntry()
            },
            onDescription = {
                barcodeViewModel.complete()
                captureViewModel.selectMode(de.baseline.nutrition.ui.capture.CaptureMode.Description)
                activeFlow = "capture"
            },
            onClose = {
                barcodeViewModel.complete()
                activeFlow = "diary"
            },
        )
        "favorites", "recent" -> ReuseScreen(
            viewModel = reuseViewModel,
            mode = if (activeFlow == "favorites") ReuseMode.Favorites else ReuseMode.Recent,
            selectedDay = diaryState.selectedDay.toString(),
            onDraftReady = { draft ->
                activeFlow = "diary"
                diaryViewModel.openDraft(draft)
            },
            onEditTemplate = { favorite ->
                activeFlow = "diary"
                diaryViewModel.openFavoriteTemplate(favorite)
            },
            onManual = {
                activeFlow = "diary"
                diaryViewModel.newManualEntry()
            },
            onClose = { activeFlow = "diary" },
        )
        "history" -> HistoryScreen(
            viewModel = historyViewModel,
            onSelectDay = { day ->
                activeFlow = "diary"
                diaryViewModel.selectDay(day)
            },
            onClose = { activeFlow = "diary" },
        )
        "health" -> HealthConnectScreen(
            viewModel = healthViewModel,
            onClose = {
                settingsViewModel.refresh()
                activeFlow = "settings"
            },
        )
        "settings" -> SettingsScreen(
            viewModel = settingsViewModel,
            onOpenHealth = { activeFlow = "health" },
            onClose = {
                diaryViewModel.refresh()
                activeFlow = "diary"
            },
            onLoggedOut = onLoggedOut,
        )
        else -> DiaryScreen(
            viewModel = diaryViewModel,
            onQuickAdd = { activeFlow = "capture" },
            onHistory = { activeFlow = "history" },
            onSettings = {
                settingsViewModel.refresh()
                activeFlow = "settings"
            },
            healthConnectionLoading = healthState.loading,
            healthAvailability = healthState.availability,
            activeCaloriesPermission = healthState.permissions[HealthDataType.ActiveCalories],
        )
    }
}
