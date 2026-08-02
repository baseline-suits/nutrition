package de.baseline.nutrition.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.data.settings.SettingsAccountDto
import de.baseline.nutrition.data.settings.SettingsDataSource
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.data.settings.SettingsHealthStateDto
import de.baseline.nutrition.data.settings.SettingsLocalDataSource
import de.baseline.nutrition.data.settings.SettingsProfileDto
import de.baseline.nutrition.data.sync.MealSyncUiState
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRepository
import de.baseline.nutrition.domain.onboarding.GoalCalculator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsAppDetails(
    val versionName: String,
    val versionCode: Int,
    val environment: String,
)

data class SettingsProfileDraft(
    val manual: Boolean = true,
    val birthDate: String = "",
    val biologicalInput: String = "female",
    val heightCm: String = "",
    val weightKg: String = "",
    val activityLevel: String = "sometimes",
    val goalDirection: String = "maintain",
    val targetKcal: String = "",
    val targetProtein: String = "",
    val targetCarbs: String = "",
    val targetFat: String = "",
    val calorieBudgetMode: String = "fixed",
    val timezone: String = "UTC",
    val expectedUpdatedAt: String = "",
) {
    companion object {
        fun from(profile: SettingsProfileDto) = SettingsProfileDraft(
            manual = profile.targetsManual == 1,
            birthDate = profile.birthDate.orEmpty(),
            biologicalInput = profile.biologicalInput ?: "female",
            heightCm = profile.heightCm.orEmpty(),
            weightKg = profile.weightKg.orEmpty(),
            activityLevel = profile.activityLevel ?: "sometimes",
            goalDirection = profile.goalDirection ?: "maintain",
            targetKcal = profile.targetKcal,
            targetProtein = profile.targetProtein,
            targetCarbs = profile.targetCarbs,
            targetFat = profile.targetFat,
            calorieBudgetMode = profile.calorieBudgetMode,
            timezone = profile.timezone,
            expectedUpdatedAt = profile.updatedAt,
        )
    }
}

enum class SettingsOperation { Loading, Profile, Locale, Sync, Source, Cache, Logout, Delete }
enum class SettingsError { Network, Validation, Conflict, Session, Reauthentication }
enum class SettingsNotice { ProfileSaved, LocaleSaved, Synced, SourceSaved, CacheReloaded, Disconnected }

data class SettingsUiState(
    val account: SettingsAccountDto? = null,
    val profile: SettingsProfileDto? = null,
    val profileDraft: SettingsProfileDraft = SettingsProfileDraft(),
    val healthConnection: HealthConnectionSnapshot? = null,
    val remoteHealth: SettingsHealthStateDto = SettingsHealthStateDto(),
    val healthAggregates: List<SettingsHealthAggregateDto> = emptyList(),
    val mealSync: MealSyncUiState = MealSyncUiState(),
    val operation: SettingsOperation? = null,
    val error: SettingsError? = null,
    val notice: SettingsNotice? = null,
    val healthSyncFailed: Boolean = false,
    val loggedOut: Boolean = false,
    val accountDeletionStatus: AccountDeletionStatus? = null,
    val appDetails: SettingsAppDetails,
)

class SettingsViewModel(
    private val remote: SettingsDataSource,
    private val local: SettingsLocalDataSource,
    private val healthRepository: HealthRepository,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val applyLocale: (String) -> Unit,
    appDetails: SettingsAppDetails,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState(appDetails = appDetails))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Loading, error = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { loadSettings() } }
                .onSuccess { loaded ->
                    mutableState.update {
                        it.copy(
                            account = loaded.account,
                            profile = loaded.profile,
                            profileDraft = SettingsProfileDraft.from(loaded.profile),
                            healthConnection = loaded.connection,
                            remoteHealth = loaded.remoteHealth,
                            healthAggregates = loaded.aggregates,
                            mealSync = loaded.mealSync,
                            operation = null,
                            error = null,
                            healthSyncFailed = false,
                        )
                    }
                    applyLocale(loaded.account.locale)
                }
                .onFailure(::fail)
        }
    }

    fun updateProfileDraft(draft: SettingsProfileDraft) {
        mutableState.update { it.copy(profileDraft = draft, error = null, notice = null) }
    }

    fun calculateTargets() {
        val draft = mutableState.value.profileDraft
        runCatching { calculatedRequest(draft, mutableState.value.account?.locale ?: "de") }
            .onSuccess { request ->
                mutableState.update {
                    it.copy(
                        profileDraft = draft.copy(
                            targetKcal = request.targetKcal,
                            targetProtein = request.targetProtein,
                            targetCarbs = request.targetCarbs,
                            targetFat = request.targetFat,
                        ),
                        error = null,
                    )
                }
            }
            .onFailure { mutableState.update { it.copy(error = SettingsError.Validation) } }
    }

    fun saveProfile() {
        if (mutableState.value.operation != null) return
        val request = runCatching {
            profileRequest(
                mutableState.value.profileDraft,
                mutableState.value.account?.locale ?: "de",
            )
        }.getOrElse {
            mutableState.update { state -> state.copy(error = SettingsError.Validation) }
            return
        }
        mutableState.update { it.copy(operation = SettingsOperation.Profile, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { remote.saveProfile(request) } }
                .onSuccess { profile ->
                    mutableState.update {
                        it.copy(
                            profile = profile,
                            profileDraft = SettingsProfileDraft.from(profile),
                            operation = null,
                            notice = SettingsNotice.ProfileSaved,
                        )
                    }
                }
                .onFailure(::fail)
        }
    }

    fun setLocale(locale: String) {
        if (locale !in setOf("de", "ru") || mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Locale, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { remote.saveLocale(locale) } }
                .onSuccess { confirmed ->
                    mutableState.update {
                        it.copy(
                            account = it.account?.copy(locale = confirmed),
                            profile = it.profile?.copy(locale = confirmed),
                            operation = null,
                            notice = SettingsNotice.LocaleSaved,
                        )
                    }
                    applyLocale(confirmed)
                }
                .onFailure(::fail)
        }
    }

    fun saveSourcePreference(dataType: String, originPackage: String?) {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Source, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    if (originPackage == null) remote.clearSourcePreference(dataType)
                    else remote.saveSourcePreference(dataType, originPackage)
                    loadRemoteHealth()
                }
            }.onSuccess { health ->
                mutableState.update {
                    it.copy(
                        remoteHealth = health.first,
                        healthAggregates = health.second,
                        operation = null,
                        notice = SettingsNotice.SourceSaved,
                    )
                }
            }.onFailure(::fail)
        }
    }

    fun syncNow() {
        if (mutableState.value.operation != null) return
        mutableState.update {
            it.copy(operation = SettingsOperation.Sync, error = null, notice = null, healthSyncFailed = false)
        }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    local.syncMeals()
                    val connection = healthRepository.connection()
                    val end = Instant.now()
                    connection.capabilities.values
                        .filter { it.permissionState == HealthPermissionState.Granted }
                        .forEach { capability ->
                            healthRepository.sync(
                                capability.type,
                                HealthReadWindow(
                                    end.minus(Duration.ofDays(7)),
                                    end,
                                    ZoneId.systemDefault(),
                                ),
                            )
                        }
                    Triple(local.mealSyncState(), connection, loadRemoteHealth())
                }
            }.onSuccess { loaded ->
                mutableState.update {
                    it.copy(
                        mealSync = loaded.first,
                        healthConnection = loaded.second,
                        remoteHealth = loaded.third.first,
                        healthAggregates = loaded.third.second,
                        operation = null,
                        notice = SettingsNotice.Synced,
                    )
                }
            }.onFailure {
                mutableState.update { state -> state.copy(healthSyncFailed = true) }
                fail(it)
            }
        }
    }

    fun disconnectHealth() {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Sync, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    healthRepository.disconnect()
                    healthRepository.connection()
                }
            }.onSuccess { connection ->
                mutableState.update {
                    it.copy(
                        healthConnection = connection,
                        operation = null,
                        notice = SettingsNotice.Disconnected,
                    )
                }
            }.onFailure(::fail)
        }
    }

    fun reloadCaches() {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Cache, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    local.reloadCaches()
                    local.mealSyncState()
                }
            }
                .onSuccess { mealSync ->
                    mutableState.update {
                        it.copy(
                            mealSync = mealSync,
                            operation = null,
                            notice = SettingsNotice.CacheReloaded,
                        )
                    }
                }
                .onFailure(::fail)
        }
    }

    fun logout(allDevices: Boolean) {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Logout, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { authRepository.logout(allDevices) } }
                .onSuccess { mutableState.update { it.copy(operation = null, loggedOut = true) } }
                .onFailure(::fail)
        }
    }

    fun deleteAccount(password: String) {
        if (mutableState.value.operation != null) return
        mutableState.update { it.copy(operation = SettingsOperation.Delete, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { authRepository.deleteAccount(password) } }
                .onSuccess { status ->
                    mutableState.update {
                        it.copy(operation = null, accountDeletionStatus = status)
                    }
                }
                .onFailure(::fail)
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(error = null, notice = null) }

    private suspend fun loadSettings(): LoadedSettings {
        val account = remote.account()
        val profile = remote.profile()
        val connection = healthRepository.connection()
        val health = loadRemoteHealth()
        return LoadedSettings(
            account,
            profile,
            connection,
            health.first,
            health.second,
            local.mealSyncState(),
        )
    }

    private suspend fun loadRemoteHealth(): Pair<SettingsHealthStateDto, List<SettingsHealthAggregateDto>> {
        val end = LocalDate.now()
        return remote.healthState() to remote.healthAggregates(
            end.minusDays(29).toString(),
            end.toString(),
        )
    }

    private fun profileRequest(draft: SettingsProfileDraft, locale: String): ProfileRequest =
        if (draft.manual) manualRequest(draft, locale) else calculatedRequest(draft, locale)

    private fun manualRequest(draft: SettingsProfileDraft, locale: String): ProfileRequest {
        val kcal = draft.targetKcal.toDouble()
        val protein = draft.targetProtein.toDouble()
        val carbs = draft.targetCarbs.toDouble()
        val fat = draft.targetFat.toDouble()
        require(kcal in 800.0..10_000.0 && protein in 0.0..1000.0)
        require(carbs in 0.0..1500.0 && fat in 0.0..500.0)
        return baseRequest(draft, locale).copy(
            targetKcal = kcal.toString(),
            targetProtein = protein.toString(),
            targetCarbs = carbs.toString(),
            targetFat = fat.toString(),
            manual = true,
        )
    }

    private fun calculatedRequest(draft: SettingsProfileDraft, locale: String): ProfileRequest {
        val result = GoalCalculator.calculate(
            LocalDate.parse(draft.birthDate),
            draft.biologicalInput,
            draft.heightCm.toDouble(),
            draft.weightKg.toDouble(),
            draft.activityLevel,
            draft.goalDirection,
        )
        return baseRequest(draft, locale).copy(
            targetKcal = result.targetKcal.toString(),
            targetProtein = result.proteinGrams.toString(),
            targetCarbs = result.carbsGrams.toString(),
            targetFat = result.fatGrams.toString(),
            manual = false,
            calculation = mapOf(
                "formula_version" to result.formulaVersion,
                "basal_kcal" to result.basalKcal.toString(),
                "maintenance_kcal" to result.maintenanceKcal.toString(),
            ),
        )
    }

    private fun baseRequest(draft: SettingsProfileDraft, locale: String) = ProfileRequest(
        locale = locale,
        timezone = draft.timezone,
        birthDate = draft.birthDate.takeIf(String::isNotBlank),
        biologicalInput = draft.biologicalInput.takeIf { draft.birthDate.isNotBlank() },
        heightCm = draft.heightCm.toDoubleOrNull()?.toString(),
        weightKg = draft.weightKg.toDoubleOrNull()?.toString(),
        activityLevel = draft.activityLevel.takeIf { draft.heightCm.isNotBlank() },
        goalDirection = draft.goalDirection.takeIf { draft.heightCm.isNotBlank() },
        targetKcal = draft.targetKcal,
        targetProtein = draft.targetProtein,
        targetCarbs = draft.targetCarbs,
        targetFat = draft.targetFat,
        manual = draft.manual,
        calorieBudgetMode = draft.calorieBudgetMode,
        expectedUpdatedAt = draft.expectedUpdatedAt,
    )

    private fun fail(error: Throwable) {
        val mapped = when {
            error is ApiException && error.status == 401 -> SettingsError.Session
            error is ApiException && error.status == 403 -> SettingsError.Reauthentication
            error is ApiException && error.status == 409 -> SettingsError.Conflict
            error is ApiException && error.status == 422 -> SettingsError.Validation
            error is IllegalArgumentException -> SettingsError.Validation
            else -> SettingsError.Network
        }
        mutableState.update {
            it.copy(
                operation = null,
                error = mapped,
                loggedOut = it.loggedOut || mapped == SettingsError.Session,
            )
        }
    }

    private data class LoadedSettings(
        val account: SettingsAccountDto,
        val profile: SettingsProfileDto,
        val connection: HealthConnectionSnapshot,
        val remoteHealth: SettingsHealthStateDto,
        val aggregates: List<SettingsHealthAggregateDto>,
        val mealSync: MealSyncUiState,
    )

    companion object {
        fun factory(
            remote: SettingsDataSource,
            local: SettingsLocalDataSource,
            healthRepository: HealthRepository,
            authRepository: AuthRepository,
            ioDispatcher: CoroutineDispatcher,
            applyLocale: (String) -> Unit,
            appDetails: SettingsAppDetails,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                remote,
                local,
                healthRepository,
                authRepository,
                ioDispatcher,
                applyLocale,
                appDetails,
            ) as T
        }
    }
}
