package de.baseline.nutrition.ui.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.ui.diary.DiaryScreen
import de.baseline.nutrition.ui.diary.DiaryViewModel
import kotlinx.coroutines.CoroutineDispatcher

@Composable
fun AccountHomeScreen(
    authRepository: AuthRepository,
    diaryRepository: DiaryRepository,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(diaryRepository, ioDispatcher))
    DiaryScreen(diaryViewModel, authRepository, ioDispatcher, onLoggedOut)
}
