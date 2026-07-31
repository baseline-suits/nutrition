package de.baseline.nutrition.ui.reuse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.data.diary.DiaryRepository
import de.baseline.nutrition.data.diary.FavoriteDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.ReuseRequestPayload
import de.baseline.nutrition.data.network.ApiException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReuseMode { Favorites, Recent }

data class ReuseUiState(
    val favorites: List<FavoriteDto> = emptyList(),
    val recent: List<MealDto> = emptyList(),
    val loading: Boolean = true,
    val activeRequest: String? = null,
    val draft: MealPayload? = null,
    val errorCode: String? = null,
)

class ReuseViewModel(
    private val repository: DiaryRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReuseUiState())
    val state: StateFlow<ReuseUiState> = mutableState.asStateFlow()

    fun refresh() {
        mutableState.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    repository.favorites() to repository.recentMeals()
                }
            }.onSuccess { (favorites, recent) ->
                mutableState.update {
                    it.copy(
                        favorites = favorites,
                        recent = recent,
                        loading = false,
                        errorCode = null,
                    )
                }
            }.onFailure(::failure)
        }
    }

    fun reuseFavorite(favorite: FavoriteDto, day: String) {
        requestDraft(favorite.id, favorite.meal.mealType, day) { payload ->
            repository.favoriteDraft(favorite.id, payload)
        }
    }

    fun reuseRecent(meal: MealDto, day: String) {
        requestDraft(meal.id, meal.mealType, day) { payload ->
            repository.recentDraft(meal.id, payload)
        }
    }

    fun renameFavorite(favorite: FavoriteDto, displayName: String) {
        val name = displayName.trim()
        if (name.isEmpty() || mutableState.value.activeRequest != null) return
        mutableState.update { it.copy(activeRequest = favorite.id, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { repository.updateFavorite(favorite.id, name) }
            }.onSuccess { changed ->
                mutableState.update { state ->
                    state.copy(
                        favorites = state.favorites.map {
                            if (it.id == changed.id) changed else it
                        },
                        activeRequest = null,
                    )
                }
            }.onFailure(::failure)
        }
    }

    fun deleteFavorite(favorite: FavoriteDto) {
        if (mutableState.value.activeRequest != null) return
        mutableState.update { it.copy(activeRequest = favorite.id, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { repository.deleteFavorite(favorite.id) }
            }.onSuccess {
                mutableState.update { state ->
                    state.copy(
                        favorites = state.favorites.filterNot { it.id == favorite.id },
                        activeRequest = null,
                    )
                }
            }.onFailure(::failure)
        }
    }

    fun consumeDraft() = mutableState.update { it.copy(draft = null, activeRequest = null) }

    private fun requestDraft(
        id: String,
        mealType: String,
        day: String,
        block: suspend (ReuseRequestPayload) -> MealPayload,
    ) {
        if (mutableState.value.activeRequest != null) return
        val payload = runCatching { reusePayload(day, mealType) }.getOrElse {
            mutableState.update { it.copy(errorCode = "validation_error") }
            return
        }
        mutableState.update { it.copy(activeRequest = id, errorCode = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { block(payload) } }
                .onSuccess { draft ->
                    mutableState.update { it.copy(draft = draft, activeRequest = null) }
                }
                .onFailure(::failure)
        }
    }

    private fun failure(error: Throwable) {
        mutableState.update {
            it.copy(
                loading = false,
                activeRequest = null,
                errorCode = (error as? ApiException)?.code ?: "network_error",
            )
        }
    }

    companion object {
        fun factory(
            repository: DiaryRepository,
            ioDispatcher: CoroutineDispatcher,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReuseViewModel(repository, ioDispatcher) as T
        }
    }
}

private fun reusePayload(day: String, mealType: String): ReuseRequestPayload {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.parse(day)
    val time = LocalTime.now().withSecond(0).withNano(0)
    return ReuseRequestPayload(
        clientId = UUID.randomUUID().toString(),
        localDay = date.toString(),
        eatenAt = date.atTime(time).atZone(zone).toOffsetDateTime().toString(),
        timezone = zone.id,
        mealType = mealType,
    )
}
