package de.baseline.nutrition.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionRequest
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HealthTypeReadState(
    val loading: Boolean = false,
    val recordCount: Int? = null,
    val sourceCount: Int? = null,
    val truncated: Boolean = false,
    val cursorCommitted: Boolean = false,
    val rejectedCount: Int = 0,
    val error: Boolean = false,
)

data class HealthConnectUiState(
    val loading: Boolean = true,
    val availability: HealthAvailability = HealthAvailability.Unavailable,
    val permissions: Map<HealthDataType, HealthPermissionState> = emptyMap(),
    val reads: Map<HealthDataType, HealthTypeReadState> = emptyMap(),
    val pendingPermissionRequest: HealthPermissionRequest? = null,
    val disconnecting: Boolean = false,
    val error: Boolean = false,
)

class HealthConnectViewModel(
    private val repository: HealthRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HealthConnectUiState())
    val state: StateFlow<HealthConnectUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(loading = it.permissions.isEmpty(), error = false) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.connection() } }
                .onSuccess(::applyConnection)
                .onFailure { mutableState.update { state -> state.copy(loading = false, error = true) } }
        }
    }

    fun requestPermission(type: HealthDataType) {
        if (mutableState.value.availability != HealthAvailability.Available) return
        mutableState.update {
            it.copy(pendingPermissionRequest = repository.permissionRequest(type), error = false)
        }
    }

    fun onPermissionResult(request: HealthPermissionRequest, granted: Set<String>) {
        mutableState.update { it.copy(pendingPermissionRequest = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) { repository.recordPermissionResult(request, granted) }
            }.onSuccess { refresh() }
                .onFailure { mutableState.update { state -> state.copy(error = true) } }
        }
    }

    fun setEnabled(type: HealthDataType, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.setEnabled(type, enabled) } }
                .onSuccess { refresh() }
                .onFailure { mutableState.update { state -> state.copy(error = true) } }
        }
    }

    fun read(type: HealthDataType) {
        mutableState.update {
            it.copy(
                reads = it.reads + (type to (it.reads[type] ?: HealthTypeReadState()).copy(
                    loading = true,
                    error = false,
                )),
            )
        }
        viewModelScope.launch {
            val end = Instant.now()
            runCatching {
                withContext(ioDispatcher) {
                    repository.sync(
                        type,
                        HealthReadWindow(
                            startInclusive = end.minus(READ_WINDOW),
                            endExclusive = end,
                            zoneId = ZoneId.systemDefault(),
                        ),
                    )
                }
            }.onSuccess { result ->
                mutableState.update {
                    it.copy(
                        reads = it.reads + (type to HealthTypeReadState(
                            recordCount = result.recordCount,
                            sourceCount = result.sourceCount,
                            truncated = result.truncated,
                            cursorCommitted = result.cursorCommitted,
                            rejectedCount = result.rejectedCount,
                        )),
                    )
                }
            }.onFailure {
                mutableState.update { state ->
                    state.copy(
                        reads = state.reads + (type to HealthTypeReadState(error = true)),
                    )
                }
                refresh()
            }
        }
    }

    fun disconnect() {
        mutableState.update { it.copy(disconnecting = true, error = false) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.disconnect() } }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(reads = emptyMap(), disconnecting = false)
                    }
                    refresh()
                }
                .onFailure {
                    mutableState.update { state -> state.copy(disconnecting = false, error = true) }
                }
        }
    }

    private fun applyConnection(connection: HealthConnectionSnapshot) {
        val permissions = connection.capabilities.mapValues { entry -> entry.value.permissionState }
        mutableState.update {
            it.copy(
                loading = false,
                availability = connection.availability,
                permissions = permissions,
                reads = it.reads.filterKeys { type ->
                    permissions[type] == HealthPermissionState.Granted
                },
                error = false,
            )
        }
    }

    companion object {
        private val READ_WINDOW: Duration = Duration.ofDays(7)

        fun factory(
            repository: HealthRepository,
            ioDispatcher: CoroutineDispatcher,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HealthConnectViewModel(repository, ioDispatcher) as T
        }
    }
}
