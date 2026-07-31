package de.baseline.nutrition.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import de.baseline.nutrition.data.capture.CaptureRepository
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.ui.diary.DiaryScreen
import de.baseline.nutrition.ui.diary.DiaryViewModel
import de.baseline.nutrition.ui.capture.CaptureScreen
import de.baseline.nutrition.ui.capture.CaptureViewModel
import kotlinx.coroutines.CoroutineDispatcher

@Composable
fun AccountHomeScreen(
    authRepository: AuthRepository,
    diaryRepository: DiaryRepository,
    captureRepository: CaptureRepository,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(diaryRepository, ioDispatcher))
    val diaryState by diaryViewModel.state.collectAsState()
    val captureViewModel: CaptureViewModel = viewModel(
        factory = CaptureViewModel.factory(captureRepository, ioDispatcher),
    )
    var captureOpen by rememberSaveable { mutableStateOf(false) }
    if (captureOpen) {
        CaptureScreen(
            viewModel = captureViewModel,
            selectedDay = diaryState.selectedDay.toString(),
            onDraftReady = { draft ->
                captureViewModel.complete()
                captureOpen = false
                diaryViewModel.openDraft(draft)
            },
            onManual = {
                captureViewModel.complete()
                captureOpen = false
                diaryViewModel.newManualEntry()
            },
            onClose = {
                captureViewModel.complete()
                captureOpen = false
            },
        )
    } else {
        DiaryScreen(
            diaryViewModel,
            authRepository,
            ioDispatcher,
            onLoggedOut,
            onQuickAdd = { captureOpen = true },
        )
    }
}
