package de.baseline.nutrition.data.settings

import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.data.sync.MealSyncUiState

interface SettingsLocalDataSource {
    suspend fun mealSyncState(): MealSyncUiState
    suspend fun syncMeals()
    suspend fun reloadCaches()
}

class DefaultSettingsLocalDataSource(
    private val diaryRepository: DiaryRepository,
) : SettingsLocalDataSource {
    override suspend fun mealSyncState(): MealSyncUiState = diaryRepository.syncUiState()

    override suspend fun syncMeals() {
        diaryRepository.syncNow()
    }

    override suspend fun reloadCaches() {
        diaryRepository.reloadCaches()
    }
}
