package de.baseline.nutrition.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
import de.baseline.nutrition.domain.diary.IngredientDraft
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.NutrientFields
import de.baseline.nutrition.domain.diary.PrivateFoodEditorDraft
import de.baseline.nutrition.domain.diary.parseLocalizedDecimal
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.data.sync.MealOperationStatus
import de.baseline.nutrition.data.sync.MealSyncInfo
import de.baseline.nutrition.data.sync.MealSyncOverview
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onQuickAdd: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    healthConnectionLoading: Boolean,
    healthAvailability: HealthAvailability,
    activeCaloriesPermission: HealthPermissionState?,
) {
    val state by viewModel.state.collectAsState()
    when {
        state.templateEditor != null -> TemplateEditorScreen(state, viewModel)
        state.editor != null -> MealEditorScreen(state, viewModel)
        else -> DayScreen(
            state,
            viewModel,
            onQuickAdd,
            onHistory,
            onSettings,
            healthConnectionLoading,
            healthAvailability,
            activeCaloriesPermission,
        )
    }
}

@Composable
private fun DayScreen(
    state: DiaryUiState,
    viewModel: DiaryViewModel,
    onQuickAdd: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    healthConnectionLoading: Boolean,
    healthAvailability: HealthAvailability,
    activeCaloriesPermission: HealthPermissionState?,
) {
    var deleteCandidate by remember { mutableStateOf<MealDto?>(null) }
    var photoDeleteCandidate by remember { mutableStateOf<MealDto?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.diary_title), fontWeight = FontWeight.Bold)
            TextButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) {
                Text(stringResource(R.string.settings_title))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(onClick = { viewModel.selectDay(state.selectedDay.minusDays(1)) }) { Text("‹") }
            Text(state.selectedDay.toString(), modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton(onClick = { viewModel.selectDay(state.selectedDay.plusDays(1)) }) { Text("›") }
            if (state.selectedDay != LocalDate.now()) {
                TextButton(onClick = { viewModel.selectDay(LocalDate.now()) }) { Text(stringResource(R.string.today)) }
            }
        }
        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.testTag("open-history"),
        ) {
            Text(stringResource(R.string.open_history))
        }
        SyncOverviewCard(state.sync.overview, viewModel::syncNow)
        if (state.loading && state.meals.isEmpty()) {
            Text(stringResource(R.string.loading_diary))
        } else {
            ProgressOverview(
                state = state,
                healthConnectionLoading = healthConnectionLoading,
                healthAvailability = healthAvailability,
                activeCaloriesPermission = activeCaloriesPermission,
                onBudgetMode = viewModel::setBudgetMode,
            )
            state.error?.let { ErrorText(it) }
            if (state.meals.isEmpty()) {
                Text(stringResource(R.string.empty_day_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.empty_day_description))
                Button(onClick = onQuickAdd) { Text(stringResource(R.string.add_meal_title)) }
            } else {
                mealTypes.forEach { type ->
                    val meals = state.meals.filter { it.mealType == type }
                    if (meals.isNotEmpty()) {
                        Text(stringResource(mealTypeLabel(type)), fontWeight = FontWeight.Bold)
                        meals.forEach { meal ->
                            MealCard(
                                meal = meal,
                                syncInfo = state.sync.byMealId[meal.id],
                                isFavorite = state.favorites.any { it.originalMealId == meal.id },
                                onToggleFavorite = { viewModel.toggleFavorite(meal) },
                                onEdit = { viewModel.edit(meal) },
                                onDuplicate = { viewModel.duplicate(meal) },
                                onDeletePhoto = { photoDeleteCandidate = meal },
                                onDelete = { deleteCandidate = meal },
                                onRetrySync = viewModel::retrySync,
                                onDiscardSync = viewModel::discardSync,
                                onKeepServer = viewModel::keepServer,
                                onApplyMine = viewModel::applyMine,
                            )
                        }
                    }
                }
                Button(onClick = onQuickAdd) { Text(stringResource(R.string.add_meal_title)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(onClick = viewModel::refresh) { Text(stringResource(R.string.refresh)) }
            state.lastSync?.let { Text(stringResource(R.string.last_sync, it), modifier = Modifier.padding(top = 12.dp)) }
        }
    }
    deleteCandidate?.let { meal ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_meal_title)) },
            text = { Text(stringResource(R.string.delete_meal_text, meal.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(meal); deleteCandidate = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    photoDeleteCandidate?.let { meal ->
        PhotoDeletionDialog(
            meal = meal,
            onConfirm = {
                viewModel.deletePhoto(meal)
                photoDeleteCandidate = null
            },
            onDismiss = { photoDeleteCandidate = null },
        )
    }
}

@Composable
internal fun PhotoDeletionDialog(
    meal: MealDto,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_photo_title)) },
        text = { Text(stringResource(R.string.delete_photo_text, meal.name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-delete-photo"),
            ) {
                Text(stringResource(R.string.delete_photo))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AccountDeletionDialog(
    password: String,
    confirmed: Boolean,
    busy: Boolean,
    error: String?,
    onPassword: (String) -> Unit,
    onConfirmed: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Text(stringResource(R.string.delete_account_details))
                Text(stringResource(R.string.delete_account_backup_notice))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPassword,
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.testTag("delete-account-password"),
                )
                Row {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirmed,
                        enabled = !busy,
                        modifier = Modifier.testTag("delete-account-confirmation"),
                    )
                    Text(
                        stringResource(R.string.delete_account_confirmation),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && confirmed && password.length >= 10,
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm-delete-account"),
            ) {
                Text(stringResource(R.string.delete_account))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AccountDeletionFinishedDialog(
    status: AccountDeletionStatus,
    onFinish: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.account_deletion_received_title)) },
        text = {
            Text(
                stringResource(
                    if (status == AccountDeletionStatus.Completed) {
                        R.string.account_deletion_completed
                    } else {
                        R.string.account_deletion_accepted
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.testTag("account-deletion-finished"),
            ) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}

@Composable
private fun ProgressOverview(
    state: DiaryUiState,
    healthConnectionLoading: Boolean,
    healthAvailability: HealthAvailability,
    activeCaloriesPermission: HealthPermissionState?,
    onBudgetMode: (String) -> Unit,
) {
    val summary = state.summary ?: return
    val targets = state.targets
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BaselineSpacing.medium), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.daily_progress), fontWeight = FontWeight.Bold)
            ProgressLine(R.string.energy, summary.totals["energy"], targets?.energy, "kcal")
            summary.targets?.let { budget ->
                CalorieBudgetBreakdown(
                    budget = budget,
                    eatenEnergy = summary.totals["energy"],
                    canChangeMode = state.selectedDay == LocalDate.now(),
                    healthConnectionLoading = healthConnectionLoading,
                    healthAvailability = healthAvailability,
                    activeCaloriesPermission = activeCaloriesPermission,
                    onModeChange = onBudgetMode,
                )
            }
            ProgressLine(R.string.protein, summary.totals["protein"], targets?.protein, "g")
            ProgressLine(R.string.carbohydrates, summary.totals["carbohydrates"], targets?.carbohydrates, "g")
            ProgressLine(R.string.fat, summary.totals["fat"], targets?.fat, "g")
            if (summary.missingCore.isNotEmpty()) {
                Text(stringResource(R.string.missing_values, summary.missingCore.joinToString(", ")))
            }
            Text(stringResource(if (targets?.manual == 1) R.string.manual_goal_source else R.string.formula_goal_source))
        }
    }
}

@Composable
internal fun CalorieBudgetBreakdown(
    budget: DailyBudgetDto,
    eatenEnergy: String?,
    canChangeMode: Boolean,
    healthConnectionLoading: Boolean,
    healthAvailability: HealthAvailability,
    activeCaloriesPermission: HealthPermissionState?,
    onModeChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val localHealthDisabled = !healthConnectionLoading &&
        (healthAvailability != HealthAvailability.Available ||
            activeCaloriesPermission != HealthPermissionState.Granted)
    val activityStatus = if (localHealthDisabled) "disabled" else budget.activityStatus
    HorizontalDivider()
    Text(
        stringResource(
            R.string.calorie_budget_mode_value,
            stringResource(
                if (budget.budgetMode == "dynamic") {
                    R.string.dynamic_budget
                } else {
                    R.string.fixed_budget
                },
            ),
        ),
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(
            if (budget.budgetMode == "dynamic") {
                R.string.dynamic_budget_description
            } else {
                R.string.fixed_budget_description
            },
        ),
    )
    if (canChangeMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            listOf("fixed", "dynamic").forEach { mode ->
                FilterChip(
                    selected = budget.budgetMode == mode,
                    onClick = { if (mode != budget.budgetMode) onModeChange(mode) },
                    label = {
                        Text(
                            stringResource(
                                if (mode == "dynamic") {
                                    R.string.dynamic_budget
                                } else {
                                    R.string.fixed_budget
                                },
                            ),
                        )
                    },
                    modifier = Modifier.testTag("budget-mode-$mode"),
                )
            }
        }
    }
    Text(
        stringResource(activityStatusLabel(activityStatus)),
        modifier = Modifier.testTag("budget-activity-status-$activityStatus"),
    )
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.testTag("budget-details-toggle"),
    ) {
        Text(
            stringResource(
                if (expanded) R.string.hide_budget_calculation
                else R.string.show_budget_calculation,
            ),
        )
    }
    if (expanded) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
            modifier = Modifier.testTag("budget-calculation"),
        ) {
            Text(stringResource(R.string.budget_eaten, eatenEnergy.displayEnergy()))
            Text(
                stringResource(
                    R.string.budget_base_target,
                    (budget.baseEnergy ?: budget.energy).displayEnergy(),
                ),
            )
            Text(
                stringResource(
                    R.string.budget_activity_value,
                    budget.activityEnergy.displayEnergy(),
                ),
            )
            Text(stringResource(R.string.budget_activity_factor, budget.activityFactor))
            Text(stringResource(R.string.budget_activity_cap, budget.activityCapEnergy.displayEnergy()))
            Text(
                stringResource(
                    R.string.budget_activity_contribution,
                    budget.activityContributionEnergy.displayEnergy(),
                ),
            )
            Text(stringResource(R.string.budget_final_target, budget.energy.displayEnergy()))
            Text(stringResource(R.string.calorie_budget_example))
        }
    }
}

private fun activityStatusLabel(status: String): Int = when (status) {
    "missing" -> R.string.budget_status_missing
    "partial" -> R.string.budget_status_partial
    "ready" -> R.string.budget_status_ready
    "conflict" -> R.string.budget_status_conflict
    "disabled" -> R.string.budget_status_disabled
    else -> R.string.budget_status_not_synced
}

private fun String?.displayEnergy(): String =
    this?.toBigDecimalOrNull()?.display() ?: "–"

@Composable
private fun ProgressLine(label: Int, currentText: String?, targetText: String?, unit: String) {
    val current = currentText?.toBigDecimalOrNull()
    val target = targetText?.toBigDecimalOrNull()
    Text("${stringResource(label)}: ${current?.display() ?: stringResource(R.string.not_available)} / ${target?.display() ?: "–"} $unit")
    if (current != null && target != null && target > BigDecimal.ZERO) {
        LinearProgressIndicator(
            progress = { (current / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        val remaining = target - current
        Text(if (remaining >= BigDecimal.ZERO) {
            stringResource(R.string.remaining_value, remaining.display(), unit)
        } else {
            stringResource(R.string.exceeded_value, remaining.abs().display(), unit)
        })
    }
}

@Composable
internal fun MealCard(
    meal: MealDto,
    syncInfo: MealSyncInfo?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDeletePhoto: () -> Unit,
    onDelete: () -> Unit,
    onRetrySync: (String) -> Unit,
    onDiscardSync: (String) -> Unit,
    onKeepServer: (String) -> Unit,
    onApplyMine: (String) -> Unit,
) {
    val totals = mealTotals(meal)
    val time = runCatching { OffsetDateTime.parse(meal.eatenAt).toLocalTime().withSecond(0).withNano(0) }.getOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BaselineSpacing.medium), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(meal.name, fontWeight = FontWeight.Bold)
            Text("${time ?: "–"} · ${totals["energy"]?.display() ?: "–"} kcal")
            Text("P ${totals["protein"]?.display() ?: "–"} g · KH ${totals["carbohydrates"]?.display() ?: "–"} g · F ${totals["fat"]?.display() ?: "–"} g")
            val source = (meal.nutrients + meal.ingredients.flatMap { it.nutrients }).firstOrNull()?.source
                ?: meal.provenanceSource ?: meal.captureMethod
            Text(
                "${stringResource(R.string.source)}: ${sourceLabel(source)}" +
                    if (syncInfo == null) " · ${stringResource(R.string.synced)}" else "",
            )
            if (meal.photoDeleted) {
                Text(
                    stringResource(R.string.photo_deleted),
                    modifier = Modifier.testTag("photo-deleted-${meal.id}"),
                )
            }
            val micros = (meal.nutrients + meal.ingredients.flatMap { it.nutrients })
                .filterNot { it.key in coreNutrients }
            if (micros.isNotEmpty()) {
                Text(micros.joinToString(" · ") { "${it.key}: ${it.value} ${it.unit}" })
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
            ) {
                if (syncInfo == null) {
                    TextButton(onClick = onToggleFavorite) {
                        Text(
                            stringResource(
                                if (isFavorite) R.string.remove_favorite else R.string.add_favorite,
                            ),
                        )
                    }
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.duplicate)) }
                if (meal.attachmentId != null && syncInfo == null) {
                    TextButton(
                        onClick = onDeletePhoto,
                        modifier = Modifier.testTag("delete-photo-${meal.id}"),
                    ) {
                        Text(stringResource(R.string.delete_photo))
                    }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
            syncInfo?.let { info ->
                MealSyncControls(
                    mealId = meal.id,
                    info = info,
                    onRetry = onRetrySync,
                    onEdit = onEdit,
                    onDiscard = onDiscardSync,
                    onKeepServer = onKeepServer,
                    onApplyMine = onApplyMine,
                )
            }
        }
    }
}

@Composable
fun MealSyncControls(
    mealId: String,
    info: MealSyncInfo,
    onRetry: (String) -> Unit,
    onEdit: () -> Unit,
    onDiscard: (String) -> Unit,
    onKeepServer: (String) -> Unit,
    onApplyMine: (String) -> Unit,
) {
    Text(
        syncStatusLabel(info.status),
        modifier = Modifier.testTag("meal-sync-status-$mealId"),
    )
    when (info.status) {
        MealOperationStatus.FailedRetryable,
        MealOperationStatus.FailedPermanent,
        -> Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .testTag("meal-sync-actions-$mealId"),
        ) {
            TextButton(onClick = { onRetry(info.operationId) }) {
                Text(stringResource(R.string.sync_retry))
            }
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.edit))
            }
            TextButton(onClick = { onDiscard(info.operationId) }) {
                Text(stringResource(R.string.discard_local_change))
            }
        }
        MealOperationStatus.Conflict -> Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .testTag("meal-conflict-actions-$mealId"),
        ) {
            TextButton(onClick = { onKeepServer(info.operationId) }) {
                Text(stringResource(R.string.keep_server_version))
            }
            TextButton(onClick = { onApplyMine(info.operationId) }) {
                Text(stringResource(R.string.apply_my_version))
            }
        }
        else -> Unit
    }
    if (info.errorCode != null) {
        Text(stringResource(R.string.sync_error_code, info.errorCode))
    }
}

@Composable
private fun SyncOverviewCard(overview: MealSyncOverview, onSync: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("sync-overview")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.sync_status_title), fontWeight = FontWeight.Bold)
            if (overview.pending == 0 && overview.failed == 0 && overview.conflicts == 0) {
                Text(stringResource(R.string.sync_all_current))
            } else {
                Text(
                    stringResource(
                        R.string.sync_counts,
                        overview.pending,
                        overview.failed,
                        overview.conflicts,
                    ),
                )
                Button(onClick = onSync, modifier = Modifier.testTag("sync-now")) {
                    Text(stringResource(R.string.sync_now))
                }
            }
            overview.lastSuccessAt?.let { timestamp ->
                val local = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .withSecond(0)
                    .withNano(0)
                Text(stringResource(R.string.sync_last_success, local.toString()))
            }
        }
    }
}

@Composable
private fun MealEditorScreen(state: DiaryUiState, viewModel: DiaryViewModel) {
    val editor = state.editor ?: return
    var showMacros by rememberSaveable { mutableStateOf(editor.nutrients.protein.isNotBlank()) }
    var foodQuery by rememberSaveable { mutableStateOf("") }
    var scaleFactor by rememberSaveable(editor.clientId) { mutableStateOf("1") }
    BackHandler(onBack = viewModel::requestCloseEditor)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        TextButton(onClick = viewModel::requestCloseEditor) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(
                when {
                    state.favoriteEditor != null -> R.string.edit_template
                    editor.mealId == null -> R.string.new_meal
                    else -> R.string.edit_meal
                },
            ),
            fontWeight = FontWeight.Bold,
        )
        if (editor.analysisWarnings.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(BaselineSpacing.medium)) {
                    Text(stringResource(R.string.analysis_warnings), fontWeight = FontWeight.Bold)
                    editor.analysisWarnings.forEach { Text("• $it") }
                }
            }
        }
        Field(editor.name, { viewModel.updateEditor(editor.copy(name = it)) }, R.string.meal_name)
        Text(stringResource(R.string.meal_type))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            mealTypes.forEach { type ->
                FilterChip(
                    selected = editor.mealType == type,
                    onClick = { viewModel.updateEditor(editor.copy(mealType = type)) },
                    label = { Text(stringResource(mealTypeLabel(type))) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Field(editor.day, { viewModel.updateEditor(editor.copy(day = it)) }, R.string.date, Modifier.weight(1f))
            Field(editor.time, { viewModel.updateEditor(editor.copy(time = it)) }, R.string.time, Modifier.weight(1f))
        }
        MealScaleControls(
            factor = scaleFactor,
            onFactor = { scaleFactor = it },
            onApply = {
                viewModel.scaleEditor(scaleFactor)
                scaleFactor = "1"
            },
            onHalf = { viewModel.scaleEditor("0.5") },
            onDouble = { viewModel.scaleEditor("2") },
        )
        Text(stringResource(R.string.quick_nutrients), fontWeight = FontWeight.Bold)
        Field(editor.nutrients.energy, {
            viewModel.updateEditor(editor.copy(nutrients = editor.nutrients.copy(energy = it)))
        }, R.string.energy_kcal)
        TextButton(onClick = { showMacros = !showMacros }) {
            Text(stringResource(if (showMacros) R.string.show_less else R.string.show_more))
        }
        if (showMacros) NutrientEditor(editor.nutrients, { viewModel.updateEditor(editor.copy(nutrients = it)) }, false)
        Field(editor.note, { viewModel.updateEditor(editor.copy(note = it)) }, R.string.note)
        Text(stringResource(R.string.live_total, totalsText(editor)), fontWeight = FontWeight.Bold)
        HorizontalDivider()
        Text(stringResource(R.string.ingredients), fontWeight = FontWeight.Bold)
        editor.ingredients.forEachIndexed { index, ingredient ->
            IngredientEditor(
                ingredient = ingredient,
                onChange = { changed ->
                    viewModel.updateEditor(editor.copy(ingredients = editor.ingredients.replace(index, changed)))
                },
                onUp = if (index > 0) {{
                    viewModel.updateEditor(editor.copy(ingredients = editor.ingredients.swap(index, index - 1)))
                }} else null,
                onDown = if (index < editor.ingredients.lastIndex) {{
                    viewModel.updateEditor(editor.copy(ingredients = editor.ingredients.swap(index, index + 1)))
                }} else null,
                onDelete = { viewModel.updateEditor(editor.copy(ingredients = editor.ingredients.filterIndexed { i, _ -> i != index })) },
                onTemplate = { viewModel.templateFromIngredient(ingredient) },
            )
        }
        OutlinedButton(onClick = viewModel::newIngredient) { Text(stringResource(R.string.add_ingredient)) }
        if (state.privateFoods.isNotEmpty()) {
            Text(stringResource(R.string.private_foods), fontWeight = FontWeight.Bold)
            Field(foodQuery, { foodQuery = it }, R.string.search_private_foods)
            state.privateFoods.filter { food ->
                foodQuery.isBlank() || food.name.contains(foodQuery, ignoreCase = true) ||
                    food.brand.orEmpty().contains(foodQuery, ignoreCase = true)
            }.forEach { food ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(BaselineSpacing.small)) {
                        Text(listOfNotNull(food.name, food.brand).joinToString(" · "))
                        Text("${food.defaultAmount} ${food.unit} · ${food.basis}")
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { viewModel.addPrivateFood(food) }) { Text(stringResource(R.string.add)) }
                            TextButton(onClick = { viewModel.editTemplate(food) }) { Text(stringResource(R.string.edit)) }
                            TextButton(onClick = { viewModel.duplicateTemplate(food) }) { Text(stringResource(R.string.duplicate)) }
                            TextButton(onClick = { viewModel.deleteTemplate(food) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
        state.error?.let { ErrorText(it) }
        Button(enabled = !state.saving, onClick = viewModel::saveEditor) {
            Text(stringResource(if (state.saving) R.string.saving else R.string.save))
        }
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscard,
            title = { Text(stringResource(R.string.discard_changes)) },
            text = { Text(stringResource(R.string.discard_changes_text)) },
            confirmButton = { TextButton(onClick = viewModel::closeEditor) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissDiscard) { Text(stringResource(R.string.continue_editing)) } },
        )
    }
}

@Composable
private fun IngredientEditor(
    ingredient: IngredientDraft,
    onChange: (IngredientDraft) -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onDelete: () -> Unit,
    onTemplate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BaselineSpacing.medium), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Field(ingredient.name, { onChange(ingredient.copy(name = it)) }, R.string.ingredient_name)
            Field(ingredient.preparation, { onChange(ingredient.copy(preparation = it)) }, R.string.preparation)
            Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Field(ingredient.amount, { onChange(ingredient.withScaledAmount(it)) }, R.string.amount, Modifier.weight(1f))
                Field(ingredient.unit, { onChange(ingredient.copy(unit = it)) }, R.string.unit, Modifier.weight(1f))
            }
            NutrientEditor(ingredient.nutrients, { onChange(ingredient.copy(nutrients = it)) }, true)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                onUp?.let { TextButton(onClick = it) { Text("↑") } }
                onDown?.let { TextButton(onClick = it) { Text("↓") } }
                TextButton(onClick = onTemplate) { Text(stringResource(R.string.save_as_template)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

@Composable
private fun NutrientEditor(fields: NutrientFields, onChange: (NutrientFields) -> Unit, includeEnergy: Boolean) {
    if (includeEnergy) Field(fields.energy, { onChange(fields.copy(energy = it)) }, R.string.energy_kcal)
    Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        Field(fields.protein, { onChange(fields.copy(protein = it)) }, R.string.protein_g, Modifier.weight(1f))
        Field(fields.carbohydrates, { onChange(fields.copy(carbohydrates = it)) }, R.string.carbs_g, Modifier.weight(1f))
        Field(fields.fat, { onChange(fields.copy(fat = it)) }, R.string.fat_g, Modifier.weight(1f))
    }
}

@Composable
fun MealScaleControls(
    factor: String,
    onFactor: (String) -> Unit,
    onApply: () -> Unit,
    onHalf: () -> Unit,
    onDouble: () -> Unit,
) {
    Text(stringResource(R.string.scale_meal), fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        Field(
            factor,
            onFactor,
            R.string.scale_factor,
            Modifier.weight(1f).testTag("meal-scale-factor"),
        )
        Button(onClick = onApply, modifier = Modifier.testTag("meal-scale-apply")) {
            Text(stringResource(R.string.apply))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        OutlinedButton(onClick = onHalf, modifier = Modifier.testTag("meal-scale-half")) {
            Text(stringResource(R.string.half_portion))
        }
        OutlinedButton(onClick = onDouble, modifier = Modifier.testTag("meal-scale-double")) {
            Text(stringResource(R.string.double_portion))
        }
    }
}

@Composable
private fun TemplateEditorScreen(state: DiaryUiState, viewModel: DiaryViewModel) {
    val editor = state.templateEditor ?: return
    BackHandler(onBack = viewModel::closeTemplate)
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        TextButton(onClick = viewModel::closeTemplate) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.private_food_editor), fontWeight = FontWeight.Bold)
        Field(editor.name, { viewModel.updateTemplate(editor.copy(name = it)) }, R.string.food_name)
        Field(editor.brand, { viewModel.updateTemplate(editor.copy(brand = it)) }, R.string.brand_optional)
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Field(editor.amount, { viewModel.updateTemplate(editor.copy(amount = it)) }, R.string.standard_amount, Modifier.weight(1f))
            Field(editor.unit, { viewModel.updateTemplate(editor.copy(unit = it)) }, R.string.unit, Modifier.weight(1f))
        }
        Text(stringResource(R.string.reference_basis))
        listOf("100g", "100ml", "portion").forEach { basis ->
            FilterChip(
                selected = editor.basis == basis,
                onClick = { viewModel.updateTemplate(editor.copy(basis = basis)) },
                label = { Text(basis) },
            )
        }
        NutrientEditor(editor.nutrients, { viewModel.updateTemplate(editor.copy(nutrients = it)) }, true)
        Text(stringResource(R.string.template_snapshot_hint))
        state.error?.let { ErrorText(it) }
        Button(enabled = !state.saving, onClick = viewModel::saveTemplate) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        singleLine = true, modifier = modifier,
    )
}

@Composable
private fun ErrorText(error: DiaryError) {
    Text(stringResource(when (error) {
        DiaryError.Network -> R.string.diary_network_error
        DiaryError.Validation -> R.string.diary_validation_error
        DiaryError.Conflict -> R.string.diary_conflict_error
        DiaryError.QueueFull -> R.string.sync_queue_full
        DiaryError.Session -> R.string.capture_error_session
    }))
}

@Composable
private fun syncStatusLabel(status: MealOperationStatus?): String = stringResource(
    when (status) {
        MealOperationStatus.Pending -> R.string.sync_pending
        MealOperationStatus.Syncing -> R.string.sync_syncing
        MealOperationStatus.FailedRetryable -> R.string.sync_failed_retryable
        MealOperationStatus.FailedPermanent -> R.string.sync_failed_permanent
        MealOperationStatus.Conflict -> R.string.sync_conflict
        MealOperationStatus.Synced, null -> R.string.synced
    },
)

@Composable
private fun sourceLabel(source: String): String = stringResource(when (source) {
    "user", "manual" -> R.string.source_user
    "open_food_facts", "barcode" -> R.string.source_off
    else -> R.string.source_ai
})

private fun mealTotals(meal: MealDto): Map<String, BigDecimal?> = coreNutrients.associateWith { key ->
    val values = (meal.nutrients + meal.ingredients.flatMap { it.nutrients })
        .filter { it.key == key && it.basis == "portion" }
        .mapNotNull { parseLocalizedDecimal(it.value) }
    values.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
}

private fun totalsText(editor: MealEditorDraft): String = editor.totals().let { totals ->
    "${totals["energy"]?.display() ?: "–"} kcal · P ${totals["protein"]?.display() ?: "–"} g · " +
        "KH ${totals["carbohydrates"]?.display() ?: "–"} g · F ${totals["fat"]?.display() ?: "–"} g"
}

private fun BigDecimal.display(): String = setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
private fun <T> List<T>.swap(first: Int, second: Int): List<T> = toMutableList().also {
    val value = it[first]
    it[first] = it[second]
    it[second] = value
}

private val mealTypes = listOf("breakfast", "lunch", "dinner", "snack", "other")
private val coreNutrients = listOf("energy", "protein", "carbohydrates", "fat")
private fun mealTypeLabel(type: String): Int = when (type) {
    "breakfast" -> R.string.breakfast
    "lunch" -> R.string.lunch
    "dinner" -> R.string.dinner
    "snack" -> R.string.snacks
    else -> R.string.other
}
