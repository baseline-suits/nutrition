package de.baseline.nutrition.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.data.diary.HistoryDataSource
import de.baseline.nutrition.data.diary.HistoryResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val rangeDays: Int = 7,
    val data: HistoryResponseDto? = null,
    val loading: Boolean = true,
    val cached: Boolean = false,
    val error: Boolean = false,
)

class HistoryViewModel(
    private val repository: HistoryDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun setRange(days: Int) {
        require(days == 7 || days == 30)
        if (mutableState.value.rangeDays == days) return
        mutableState.update { it.copy(rangeDays = days) }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val days = mutableState.value.rangeDays
        mutableState.update { it.copy(loading = it.data == null, error = false) }
        loadJob = viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.history(days) } }
                .onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            data = result.data,
                            loading = false,
                            cached = result.cached,
                            error = false,
                        )
                    }
                }
                .onFailure {
                    mutableState.update {
                        it.copy(loading = false, cached = false, error = true)
                    }
                }
        }
    }

    companion object {
        fun factory(
            repository: HistoryDataSource,
            ioDispatcher: CoroutineDispatcher,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HistoryViewModel(repository, ioDispatcher) as T
        }
    }
}
