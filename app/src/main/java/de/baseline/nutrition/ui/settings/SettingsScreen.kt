package de.baseline.nutrition.ui.settings

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.baseline.nutrition.R
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.data.settings.SettingsSourcePreferenceDto
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.diary.AccountDeletionDialog
import de.baseline.nutrition.ui.diary.AccountDeletionFinishedDialog
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.time.LocalDate

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenHealth: () -> Unit,
    onClose: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onClose)
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }
    SettingsContent(
        state = state,
        onDraft = viewModel::updateProfileDraft,
        onCalculate = viewModel::calculateTargets,
        onSaveProfile = viewModel::saveProfile,
        onLocale = viewModel::setLocale,
        onOpenHealth = onOpenHealth,
        onSync = viewModel::syncNow,
        onDisconnectHealth = viewModel::disconnectHealth,
        onSource = viewModel::saveSourcePreference,
        onReloadCaches = viewModel::reloadCaches,
        onLogout = viewModel::logout,
        onDeleteAccount = viewModel::deleteAccount,
        onRefresh = viewModel::refresh,
        onDismissMessage = viewModel::dismissMessage,
        onClose = onClose,
        onAccountDeletionFinished = onLoggedOut,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onDraft: (SettingsProfileDraft) -> Unit,
    onCalculate: () -> Unit,
    onSaveProfile: () -> Unit,
    onLocale: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onSync: () -> Unit,
    onDisconnectHealth: () -> Unit,
    onSource: (String, String?) -> Unit,
    onReloadCaches: () -> Unit,
    onLogout: (Boolean) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    onClose: () -> Unit,
    onAccountDeletionFinished: () -> Unit,
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteConfirmed by remember { mutableStateOf(false) }
    var showLogoutAll by rememberSaveable { mutableStateOf(false) }
    val busy = state.operation != null
    LaunchedEffect(state.accountDeletionStatus) {
        if (state.accountDeletionStatus != null) {
            deletePassword = ""
            deleteConfirmed = false
            showDelete = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
        if (state.operation == SettingsOperation.Loading && state.account == null) {
            Text(stringResource(R.string.settings_loading), modifier = Modifier.testTag("settings-loading"))
        } else {
            LanguageAndAppCard(state, onLocale)
            ProfileSettingsCard(state, onDraft, onCalculate, onSaveProfile)
            HealthAndSyncCard(
                state,
                onOpenHealth,
                onSync,
                onDisconnectHealth,
                onSource,
            )
            AccountSettingsCard(
                state,
                onReloadCaches,
                onLogoutDevice = { onLogout(false) },
                onLogoutAll = { showLogoutAll = true },
                onDelete = {
                    deletePassword = ""
                    deleteConfirmed = false
                    showDelete = true
                },
            )
        }
        state.error?.let {
            Text(
                stringResource(settingsErrorLabel(it)),
                modifier = Modifier.testTag("settings-error"),
            )
        }
        state.notice?.let {
            Text(
                stringResource(settingsNoticeLabel(it)),
                modifier = Modifier.testTag("settings-success"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(onClick = onRefresh, enabled = !busy) {
                Text(stringResource(R.string.refresh))
            }
            if (state.error != null || state.notice != null) {
                TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.ok)) }
            }
        }
    }
    if (showLogoutAll) {
        AlertDialog(
            onDismissRequest = { if (!busy) showLogoutAll = false },
            title = { Text(stringResource(R.string.settings_logout_all_title)) },
            text = { Text(stringResource(R.string.settings_logout_all_text)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showLogoutAll = false
                        onLogout(true)
                    },
                    modifier = Modifier.testTag("settings-confirm-logout-all"),
                ) { Text(stringResource(R.string.logout_all_devices)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAll = false }, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showDelete) {
        AccountDeletionDialog(
            password = deletePassword,
            confirmed = deleteConfirmed,
            busy = state.operation == SettingsOperation.Delete,
            error = state.error?.let { stringResource(settingsErrorLabel(it)) },
            onPassword = {
                deletePassword = it
                onDismissMessage()
            },
            onConfirmed = { deleteConfirmed = it },
            onConfirm = { onDeleteAccount(deletePassword) },
            onDismiss = {
                deletePassword = ""
                deleteConfirmed = false
                showDelete = false
            },
        )
    }
    state.accountDeletionStatus?.let { status ->
        AccountDeletionFinishedDialog(status, onAccountDeletionFinished)
    }
}

@Composable
private fun LanguageAndAppCard(state: SettingsUiState, onLocale: (String) -> Unit) {
    val locale = state.account?.locale ?: "de"
    Card(modifier = Modifier.fillMaxWidth().testTag("settings-language")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.settings_language_display), fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.settings_system_language,
                    Resources.getSystem().configuration.locales[0].toLanguageTag(),
                ),
            )
            Text(stringResource(R.string.settings_app_language))
            Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                listOf("de", "ru").forEach { value ->
                    FilterChip(
                        selected = locale == value,
                        enabled = state.operation == null,
                        onClick = { if (locale != value) onLocale(value) },
                        label = {
                            Text(stringResource(if (value == "ru") R.string.russian else R.string.german))
                        },
                        modifier = Modifier.testTag("settings-language-$value"),
                    )
                }
            }
            Text(stringResource(R.string.settings_language_restart_hint))
            Text(stringResource(R.string.settings_reduced_motion_system))
            HorizontalDivider()
            Text(
                stringResource(
                    R.string.settings_app_info,
                    state.appDetails.versionName,
                    state.appDetails.versionCode,
                    state.appDetails.environment,
                ),
            )
        }
    }
}

@Composable
private fun ProfileSettingsCard(
    state: SettingsUiState,
    onDraft: (SettingsProfileDraft) -> Unit,
    onCalculate: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.profileDraft
    val enabled = state.operation == null
    Card(modifier = Modifier.fillMaxWidth().testTag("settings-profile")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.settings_profile_goals), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_goal_method))
            ChoiceRow(
                values = listOf("manual", "calculate"),
                selected = if (draft.manual) "manual" else "calculate",
                label = { if (it == "manual") R.string.manual_targets else R.string.calculated_targets },
                tagPrefix = "settings-goal-method",
                enabled = enabled,
            ) { onDraft(draft.copy(manual = it == "manual")) }
            if (!draft.manual) {
                SettingsField(R.string.birth_date, draft.birthDate, enabled) {
                    onDraft(draft.copy(birthDate = it))
                }
                SettingsField(R.string.height_cm, draft.heightCm, enabled) {
                    onDraft(draft.copy(heightCm = it))
                }
                SettingsField(R.string.weight_kg, draft.weightKg, enabled) {
                    onDraft(draft.copy(weightKg = it))
                }
                ChoiceRow(
                    listOf("female", "male"),
                    draft.biologicalInput,
                    { if (it == "male") R.string.male else R.string.female },
                    "settings-biological",
                    enabled,
                ) { onDraft(draft.copy(biologicalInput = it)) }
                ChoiceColumn(
                    listOf("inactive", "sometimes", "active", "very_active"),
                    draft.activityLevel,
                    ::activityLabel,
                    "settings-activity",
                    enabled,
                ) { onDraft(draft.copy(activityLevel = it)) }
                ChoiceColumn(
                    listOf("maintain", "deficit", "surplus"),
                    draft.goalDirection,
                    ::directionLabel,
                    "settings-direction",
                    enabled,
                ) { onDraft(draft.copy(goalDirection = it)) }
                OutlinedButton(onClick = onCalculate, enabled = enabled) {
                    Text(stringResource(R.string.calculate_targets))
                }
            }
            SettingsField(R.string.target_kcal, draft.targetKcal, draft.manual && enabled) {
                onDraft(draft.copy(targetKcal = it))
            }
            SettingsField(R.string.target_protein, draft.targetProtein, draft.manual && enabled) {
                onDraft(draft.copy(targetProtein = it))
            }
            SettingsField(R.string.target_carbs, draft.targetCarbs, draft.manual && enabled) {
                onDraft(draft.copy(targetCarbs = it))
            }
            SettingsField(R.string.target_fat, draft.targetFat, draft.manual && enabled) {
                onDraft(draft.copy(targetFat = it))
            }
            Text(stringResource(R.string.calorie_budget_mode))
            ChoiceRow(
                listOf("fixed", "dynamic"),
                draft.calorieBudgetMode,
                { if (it == "dynamic") R.string.dynamic_budget else R.string.fixed_budget },
                "settings-budget-mode",
                enabled,
            ) { onDraft(draft.copy(calorieBudgetMode = it)) }
            Text(
                stringResource(
                    if (draft.calorieBudgetMode == "dynamic") {
                        R.string.dynamic_budget_description
                    } else {
                        R.string.fixed_budget_description
                    },
                ),
            )
            Text(stringResource(R.string.settings_effective_day, LocalDate.now().toString()))
            Text(stringResource(R.string.settings_history_unchanged))
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.testTag("settings-save-profile"),
            ) {
                Text(
                    stringResource(
                        if (state.operation == SettingsOperation.Profile) R.string.saving else R.string.save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HealthAndSyncCard(
    state: SettingsUiState,
    onOpenHealth: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onSource: (String, String?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("settings-health-sync")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.settings_health_sync), fontWeight = FontWeight.Bold)
            Text(
                stringResource(settingsHealthStatusLabel(settingsHealthStatus(state))),
                modifier = Modifier.testTag("settings-health-status"),
            )
            state.healthConnection?.capabilities?.values?.forEach { capability ->
                Text(
                    "${stringResource(settingsHealthTypeLabel(capability.type))}: " +
                        stringResource(settingsPermissionLabel(capability.permissionState)),
                )
            }
            OutlinedButton(
                onClick = onOpenHealth,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-open-health"),
            ) {
                Text(stringResource(R.string.settings_manage_health))
            }
            val overview = state.mealSync.overview
            Text(
                stringResource(
                    R.string.settings_meal_sync_status,
                    overview.pending,
                    overview.failed,
                    overview.conflicts,
                ),
            )
            overview.lastSuccessAt?.let {
                Text(stringResource(R.string.settings_last_meal_sync, it.toString()))
            }
            state.remoteHealth.cursors.maxByOrNull { it.updatedAt }?.let {
                Text(stringResource(R.string.settings_last_health_sync, it.updatedAt))
            }
            Button(
                onClick = onSync,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-sync-now"),
            ) {
                Text(stringResource(R.string.sync_now))
            }
            OutlinedButton(onClick = onDisconnect, enabled = state.operation == null) {
                Text(stringResource(R.string.health_connect_disconnect))
            }
            Text(stringResource(R.string.settings_disconnect_retains_server_data))
            HorizontalDivider()
            Text(stringResource(R.string.settings_health_sources), fontWeight = FontWeight.Bold)
            HealthSourceSettings(
                aggregates = state.healthAggregates,
                preferences = state.remoteHealth.sourcePreferences,
                enabled = state.operation == null,
                onSource = onSource,
            )
        }
    }
}

@Composable
internal fun HealthSourceSettings(
    aggregates: List<SettingsHealthAggregateDto>,
    preferences: List<SettingsSourcePreferenceDto>,
    enabled: Boolean,
    onSource: (String, String?) -> Unit,
) {
    val types = (aggregates.map { it.dataType } + preferences.map { it.dataType }).distinct().sorted()
    val visible = types.filter { type ->
        aggregates.filter { it.dataType == type }.flatMap { it.sources }.map { it.originPackage }
            .distinct().size > 1 || preferences.any { it.dataType == type }
    }
    if (visible.isEmpty()) {
        Text(stringResource(R.string.settings_no_source_conflicts))
        return
    }
    visible.forEach { type ->
        val matching = aggregates.filter { it.dataType == type }
        val sources = matching.flatMap { it.sources }.map { it.originPackage }.distinct().sorted()
        val selected = preferences.firstOrNull { it.dataType == type }?.originPackage
        Text(stringResource(settingsApiTypeLabel(type)), fontWeight = FontWeight.Bold)
        if (matching.any { it.status == "conflict" }) {
            Text(stringResource(R.string.settings_source_conflict))
        }
        FilterChip(
            selected = selected == null,
            enabled = enabled,
            onClick = { if (selected != null) onSource(type, null) },
            label = { Text(stringResource(R.string.settings_source_automatic)) },
            modifier = Modifier.testTag("settings-source-$type-automatic"),
        )
        sources.forEach { source ->
            FilterChip(
                selected = selected == source,
                enabled = enabled,
                onClick = { if (selected != source) onSource(type, source) },
                label = { Text(source) },
                modifier = Modifier.testTag("settings-source-$type-$source"),
            )
        }
    }
}

@Composable
private fun AccountSettingsCard(
    state: SettingsUiState,
    onReloadCaches: () -> Unit,
    onLogoutDevice: () -> Unit,
    onLogoutAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("settings-account")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.account_and_privacy), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_signed_in_as, state.account?.username ?: "–"))
            Text(stringResource(R.string.settings_privacy_ai))
            Text(stringResource(R.string.settings_privacy_off))
            Text(stringResource(R.string.settings_privacy_health))
            OutlinedButton(
                onClick = onReloadCaches,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-reload-caches"),
            ) {
                Text(stringResource(R.string.settings_reload_caches))
            }
            Text(stringResource(R.string.settings_reload_caches_hint))
            OutlinedButton(
                onClick = onLogoutDevice,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-logout-device"),
            ) {
                Text(stringResource(R.string.logout_this_device))
            }
            OutlinedButton(
                onClick = onLogoutAll,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-logout-all"),
            ) {
                Text(stringResource(R.string.logout_all_devices))
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("settings-danger")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.settings_danger_zone), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.account_delete_summary))
            OutlinedButton(
                onClick = onDelete,
                enabled = state.operation == null,
                modifier = Modifier.testTag("settings-delete-account"),
            ) {
                Text(stringResource(R.string.delete_account))
            }
        }
    }
}

@Composable
private fun SettingsField(label: Int, value: String, enabled: Boolean = true, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceRow(
    values: List<String>,
    selected: String,
    label: (String) -> Int,
    tagPrefix: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                enabled = enabled,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label(value))) },
                modifier = Modifier.testTag("$tagPrefix-$value"),
            )
        }
    }
}

@Composable
private fun ChoiceColumn(
    values: List<String>,
    selected: String,
    label: (String) -> Int,
    tagPrefix: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                enabled = enabled,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label(value))) },
                modifier = Modifier.testTag("$tagPrefix-$value"),
            )
        }
    }
}

private enum class SettingsHealthStatus { Disconnected, Partial, Complete, Conflict, Error }

private fun settingsHealthStatus(state: SettingsUiState): SettingsHealthStatus {
    if (state.healthSyncFailed) return SettingsHealthStatus.Error
    if (state.healthAggregates.any { it.status == "conflict" }) return SettingsHealthStatus.Conflict
    val connection = state.healthConnection ?: return SettingsHealthStatus.Disconnected
    if (connection.availability != HealthAvailability.Available) return SettingsHealthStatus.Disconnected
    val granted = connection.capabilities.values.count {
        it.permissionState == HealthPermissionState.Granted
    }
    return when {
        granted == HealthDataType.entries.size -> SettingsHealthStatus.Complete
        granted > 0 -> SettingsHealthStatus.Partial
        else -> SettingsHealthStatus.Disconnected
    }
}

private fun settingsHealthStatusLabel(status: SettingsHealthStatus): Int = when (status) {
    SettingsHealthStatus.Disconnected -> R.string.settings_health_not_connected
    SettingsHealthStatus.Partial -> R.string.settings_health_partially_allowed
    SettingsHealthStatus.Complete -> R.string.settings_health_fully_allowed
    SettingsHealthStatus.Conflict -> R.string.settings_health_conflict
    SettingsHealthStatus.Error -> R.string.settings_health_sync_error
}

private fun settingsHealthTypeLabel(type: HealthDataType): Int = when (type) {
    HealthDataType.Steps -> R.string.health_type_steps
    HealthDataType.Sleep -> R.string.health_type_sleep
    HealthDataType.ActiveCalories -> R.string.health_type_active_calories
    HealthDataType.Exercise -> R.string.health_type_exercise
    HealthDataType.Weight -> R.string.health_type_weight
}

private fun settingsApiTypeLabel(type: String): Int = when (type) {
    "steps" -> R.string.health_type_steps
    "sleep" -> R.string.health_type_sleep
    "active_calories" -> R.string.health_type_active_calories
    "exercise" -> R.string.health_type_exercise
    else -> R.string.health_type_weight
}

private fun settingsPermissionLabel(state: HealthPermissionState): Int = when (state) {
    HealthPermissionState.NotRequested -> R.string.health_permission_not_requested
    HealthPermissionState.Granted -> R.string.health_permission_granted
    HealthPermissionState.Disabled -> R.string.health_permission_disabled
    HealthPermissionState.Denied -> R.string.health_permission_denied
    HealthPermissionState.PermanentlyDenied -> R.string.health_permission_permanently_denied
    HealthPermissionState.Revoked -> R.string.health_permission_revoked
}

private fun activityLabel(value: String): Int = when (value) {
    "inactive" -> R.string.inactive
    "active" -> R.string.active
    "very_active" -> R.string.very_active
    else -> R.string.sometimes_active
}

private fun directionLabel(value: String): Int = when (value) {
    "deficit" -> R.string.deficit
    "surplus" -> R.string.surplus
    else -> R.string.maintain
}

private fun settingsErrorLabel(error: SettingsError): Int = when (error) {
    SettingsError.Validation -> R.string.settings_error_validation
    SettingsError.Conflict -> R.string.settings_error_conflict
    SettingsError.Session -> R.string.settings_error_session
    SettingsError.Reauthentication -> R.string.account_delete_password_error
    SettingsError.Network -> R.string.settings_error_network
}

private fun settingsNoticeLabel(notice: SettingsNotice): Int = when (notice) {
    SettingsNotice.ProfileSaved -> R.string.settings_saved_profile
    SettingsNotice.LocaleSaved -> R.string.settings_saved_locale
    SettingsNotice.Synced -> R.string.settings_sync_completed
    SettingsNotice.SourceSaved -> R.string.settings_saved_source
    SettingsNotice.CacheReloaded -> R.string.settings_cache_reloaded
    SettingsNotice.Disconnected -> R.string.settings_health_disconnected
}
