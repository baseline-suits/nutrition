package de.baseline.nutrition.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.data.diary.DaySummaryDto
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.data.diary.DiaryTargets
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.PrivateFoodDto
import de.baseline.nutrition.data.diary.toDiaryTargets
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.sync.MealQueueFullException
import de.baseline.nutrition.data.sync.MealSyncUiState
import de.baseline.nutrition.domain.diary.IngredientDraft
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.PrivateFoodEditorDraft
import de.baseline.nutrition.domain.diary.toIngredient
import de.baseline.nutrition.domain.diary.toEditorDraft
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DiaryError { Network, Validation, Conflict, QueueFull, Session }

data class DiaryUiState(
    val selectedDay: LocalDate = LocalDate.now(),
    val meals: List<MealDto> = emptyList(),
    val summary: DaySummaryDto? = null,
    val targets: DiaryTargets? = null,
    val privateFoods: List<PrivateFoodDto> = emptyList(),
    val favorites: List<FavoriteDto> = emptyList(),
    val sync: MealSyncUiState = MealSyncUiState(),
    val editor: MealEditorDraft? = null,
    val favoriteEditor: FavoriteDto? = null,
    val templateEditor: PrivateFoodEditorDraft? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: DiaryError? = null,
    val lastSync: String? = null,
    val confirmDiscard: Boolean = false,
)

class DiaryViewModel(
    private val repository: DiaryRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private data class LoadedDiary(
        val meals: List<MealDto>,
        val summary: DaySummaryDto,
        val targets: DiaryTargets,
        val favorites: List<FavoriteDto>,
        val sync: MealSyncUiState,
    )

    private val mutableState = MutableStateFlow(DiaryUiState())
    val state: StateFlow<DiaryUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val day = mutableState.value.selectedDay.toString()
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    LoadedDiary(
                        meals = repository.meals(day),
                        summary = repository.summary(day),
                        targets = repository.targets(),
                        favorites = repository.favorites(),
                        sync = repository.syncUiState(),
                    )
                }
            }.onSuccess { loaded ->
                mutableState.update { state ->
                    val targets = loaded.summary.targets?.toDiaryTargets()
                        ?: loaded.targets.takeIf { state.selectedDay >= LocalDate.now() }
                    val lastSuccess = loaded.sync.overview.lastSuccessAt?.let {
                        Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .withNano(0)
                            .toString()
                    }
                    state.copy(meals = loaded.meals, summary = loaded.summary, targets = targets,
                        favorites = loaded.favorites, sync = loaded.sync, loading = false,
                        lastSync = lastSuccess)
                }
            }.onFailure { error ->
                mutableState.update { it.copy(loading = false, error = error.toDiaryError()) }
            }
        }
    }

    fun selectDay(day: LocalDate) {
        mutableState.update { it.copy(selectedDay = day) }
        refresh()
    }

    fun newManualEntry() {
        mutableState.update {
            it.copy(
                editor = MealEditorDraft(day = it.selectedDay.toString()),
                favoriteEditor = null,
                error = null,
            )
        }
        loadPrivateFoods()
    }

    fun openDraft(editor: MealEditorDraft) {
        mutableState.update { it.copy(editor = editor, favoriteEditor = null, error = null) }
        loadPrivateFoods()
    }

    fun openFavoriteTemplate(favorite: FavoriteDto) {
        mutableState.update {
            it.copy(
                editor = favorite.meal.toEditorDraft(),
                favoriteEditor = favorite,
                error = null,
            )
        }
        loadPrivateFoods()
    }

    fun edit(meal: MealDto) {
        mutableState.update {
            it.copy(
                editor = MealEditorDraft.from(meal),
                favoriteEditor = null,
                error = null,
            )
        }
        loadPrivateFoods()
    }

    fun updateEditor(editor: MealEditorDraft) {
        mutableState.update { it.copy(editor = editor.copy(dirty = true), error = null) }
    }

    fun requestCloseEditor() {
        val editor = mutableState.value.editor ?: return
        if (editor.dirty) mutableState.update { it.copy(confirmDiscard = true) }
        else closeEditor()
    }

    fun dismissDiscard() = mutableState.update { it.copy(confirmDiscard = false) }

    fun closeEditor() = mutableState.update {
        it.copy(
            editor = null,
            favoriteEditor = null,
            templateEditor = null,
            confirmDiscard = false,
            error = null,
        )
    }

    fun saveEditor() {
        val editor = mutableState.value.editor ?: return
        val payload = runCatching { editor.toPayload() }.getOrElse {
            mutableState.update { it.copy(error = DiaryError.Validation) }
            return
        }
        if (mutableState.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val favorite = mutableState.value.favoriteEditor
            runCatching {
                withContext(ioDispatcher) {
                    if (favorite == null) {
                        repository.save(payload, editor.mealId)
                    } else {
                        repository.updateFavorite(favorite.id, favorite.displayName, payload)
                        null
                    }
                }
            }
                .onSuccess { saved ->
                    mutableState.update { state ->
                        val meals = if (favorite == null) {
                            state.meals.filterNot {
                                it.id == saved?.id || it.clientId == saved?.clientId
                            } + listOfNotNull(saved)
                        } else {
                            state.meals
                        }
                        state.copy(
                            meals = meals,
                            editor = null,
                            favoriteEditor = null,
                            saving = false,
                        )
                    }
                    refresh()
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(saving = false, error = error.toDiaryError())
                    }
                }
        }
    }

    fun delete(meal: MealDto) = mutateAndRefresh { repository.delete(meal.id) }

    fun deletePhoto(meal: MealDto) = mutateAndRefresh {
        meal.attachmentId?.let { repository.deletePhoto(it) }
    }

    fun duplicate(meal: MealDto) = mutateAndRefresh { repository.duplicate(meal.id) }

    fun toggleFavorite(meal: MealDto) {
        val favorite = mutableState.value.favorites.firstOrNull { it.originalMealId == meal.id }
        mutateAndRefresh {
            if (favorite == null) repository.createFavorite(meal.id, meal.name)
            else repository.deleteFavorite(favorite.id)
        }
    }

    fun syncNow() = mutateAndRefresh {
        repository.syncNow()
    }

    fun setBudgetMode(mode: String) {
        if (mutableState.value.selectedDay != LocalDate.now()) return
        mutateAndRefresh { repository.setBudgetMode(mode) }
    }

    fun retrySync(operationId: String) = mutateAndRefresh {
        repository.retrySync(operationId)
    }

    fun discardSync(operationId: String) = mutateAndRefresh {
        repository.discardSync(operationId)
    }

    fun keepServer(operationId: String) = mutateAndRefresh {
        repository.keepServer(operationId)
    }

    fun applyMine(operationId: String) = mutateAndRefresh {
        repository.applyMine(operationId)
    }

    fun scaleEditor(factor: String) {
        val editor = mutableState.value.editor ?: return
        val scaled = runCatching { editor.scaled(factor) }.getOrElse {
            mutableState.update { it.copy(error = DiaryError.Validation) }
            return
        }
        updateEditor(scaled)
    }

    fun loadPrivateFoods() {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.privateFoods() } }
                .onSuccess { foods -> mutableState.update { it.copy(privateFoods = foods) } }
        }
    }

    fun addPrivateFood(food: PrivateFoodDto) {
        val ingredient = runCatching { food.toIngredient() }.getOrElse {
            mutableState.update { it.copy(error = DiaryError.Validation) }
            return
        }
        mutableState.value.editor?.let { editor ->
            updateEditor(editor.copy(ingredients = editor.ingredients + ingredient))
        }
    }

    fun newIngredient() {
        mutableState.value.editor?.let { editor ->
            updateEditor(editor.copy(ingredients = editor.ingredients + IngredientDraft()))
        }
    }

    fun editTemplate(food: PrivateFoodDto) =
        mutableState.update { it.copy(templateEditor = PrivateFoodEditorDraft.from(food), error = null) }

    fun templateFromIngredient(ingredient: IngredientDraft) =
        mutableState.update { it.copy(templateEditor = PrivateFoodEditorDraft.from(ingredient), error = null) }

    fun updateTemplate(editor: PrivateFoodEditorDraft) =
        mutableState.update { it.copy(templateEditor = editor, error = null) }

    fun closeTemplate() = mutableState.update { it.copy(templateEditor = null, error = null) }

    fun saveTemplate() {
        val editor = mutableState.value.templateEditor ?: return
        val payload = runCatching { editor.toPayload() }.getOrElse {
            mutableState.update { it.copy(error = DiaryError.Validation) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.savePrivateFood(payload, editor.id) } }
                .onSuccess {
                    mutableState.update { state -> state.copy(templateEditor = null, saving = false) }
                    loadPrivateFoods()
                }
                .onFailure { mutableState.update { it.copy(saving = false, error = DiaryError.Network) } }
        }
    }

    fun duplicateTemplate(food: PrivateFoodDto) = mutateAndReloadFoods {
        repository.duplicatePrivateFood(food.id)
    }

    fun deleteTemplate(food: PrivateFoodDto) = mutateAndReloadFoods {
        repository.deletePrivateFood(food.id)
    }

    private fun mutateAndRefresh(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { block() } }
                .onSuccess { refresh() }
                .onFailure { error ->
                    mutableState.update { it.copy(error = error.toDiaryError()) }
                }
        }
    }

    private fun mutateAndReloadFoods(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { block() } }
                .onSuccess { loadPrivateFoods() }
                .onFailure { mutableState.update { it.copy(error = DiaryError.Network) } }
        }
    }

    companion object {
        fun factory(repository: DiaryRepository, ioDispatcher: CoroutineDispatcher): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DiaryViewModel(repository, ioDispatcher) as T
            }
    }
}

private fun Throwable.toDiaryError(): DiaryError = when {
    this is MealQueueFullException -> DiaryError.QueueFull
    this is ApiException && status == 401 -> DiaryError.Session
    this is ApiException && status == 409 -> DiaryError.Conflict
    else -> DiaryError.Network
}
