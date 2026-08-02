package de.baseline.nutrition.ui.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import de.baseline.nutrition.ui.overview.DailyNutritionOverview
import de.baseline.nutrition.ui.overview.MealSummaryRow
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselinePrimaryButton
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSpacing
import de.baseline.nutrition.ui.theme.BaselineStatus
import de.baseline.nutrition.ui.theme.BaselineSkeleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onQuickAdd: () -> Unit,
    onHistory: () -> Unit,
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
    healthConnectionLoading: Boolean,
    healthAvailability: HealthAvailability,
    activeCaloriesPermission: HealthPermissionState?,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<MealDto?>(null) }
    var photoDeleteCandidate by remember { mutableStateOf<MealDto?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BaselineSpacing.screen, vertical = BaselineSpacing.small)
                .padding(bottom = 88.dp)
                .testTag("diary-day"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DiaryHeader(
                selectedDay = state.selectedDay,
                onPrevious = { viewModel.selectDay(state.selectedDay.minusDays(1)) },
                onNext = { viewModel.selectDay(state.selectedDay.plusDays(1)) },
                onHistory = onHistory,
            )
            if (state.loading && state.summary == null) {
                BaselineSkeleton(Modifier.fillMaxWidth().height(170.dp).testTag("diary-loading"))
            } else {
                state.error?.let { ErrorText(it) }
                DailyNutritionOverview(state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.overview_meals),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    val sync = state.sync.overview
                    when {
                        sync.conflicts > 0 || sync.failed > 0 -> BaselineStatus(
                            stringResource(R.string.overview_sync_failed, sync.conflicts + sync.failed),
                        )
                        sync.pending > 0 -> BaselineStatus(
                            stringResource(R.string.overview_sync_pending, sync.pending),
                        )
                    }
                    if (state.meals.isNotEmpty()) {
                        TextButton(
                            onClick = { editing = !editing },
                            modifier = Modifier.testTag("diary-edit-mode"),
                        ) {
                            Text(stringResource(if (editing) R.string.done else R.string.edit))
                        }
                    }
                }
                if (state.meals.isEmpty()) {
                    BaselineCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                            Text(stringResource(R.string.empty_day_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.empty_day_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (editing) {
                    SyncOverviewCard(state.sync.overview, viewModel::syncNow)
                    state.summary?.targets?.let { budget ->
                        CalorieBudgetBreakdown(
                            budget = budget,
                            eatenEnergy = state.summary.totals["energy"],
                            canChangeMode = state.selectedDay == LocalDate.now(),
                            healthConnectionLoading = healthConnectionLoading,
                            healthAvailability = healthAvailability,
                            activeCaloriesPermission = activeCaloriesPermission,
                            onModeChange = viewModel::setBudgetMode,
                        )
                    }
                    state.meals.sortedBy(MealDto::eatenAt).forEach { meal ->
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
                    OutlinedButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.refresh))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.meals.sortedBy(MealDto::eatenAt).forEach { meal ->
                            MealSummaryRow(meal = meal, onMeal = viewModel::edit)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onQuickAdd,
            shape = androidx.compose.foundation.shape.CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BaselineSpacing.medium)
                .size(64.dp)
                .testTag("diary-add-meal"),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_meal_title))
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
internal fun DiaryHeader(
    selectedDay: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onHistory: () -> Unit,
) {
    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
    val formattedDay = selectedDay.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
    Text(stringResource(R.string.diary_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.diary_subtitle),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.testTag("diary-previous-day")) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.previous_day))
            }
        }
        Surface(
            onClick = onHistory,
            shape = BaselineShapes.pill,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag("diary-selected-day"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = BaselineSpacing.medium, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(formattedDay, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            IconButton(onClick = onNext, modifier = Modifier.testTag("diary-next-day")) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.next_day))
            }
        }
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
    var showMealDetails by rememberSaveable(editor.clientId) { mutableStateOf(editor.name.isBlank()) }
    var showDirectNutrients by rememberSaveable(editor.clientId) {
        mutableStateOf(editor.ingredients.isEmpty())
    }
    var showTools by rememberSaveable(editor.clientId) { mutableStateOf(false) }
    var showPrivateFoods by rememberSaveable(editor.clientId) { mutableStateOf(false) }
    var foodQuery by rememberSaveable { mutableStateOf("") }
    var scaleFactor by rememberSaveable(editor.clientId) { mutableStateOf("1") }
    BackHandler(onBack = viewModel::requestCloseEditor)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background),
    ) {
        MealReviewHeader(
            showDiscard = editor.mealId == null && state.favoriteEditor == null,
            onBack = viewModel::requestCloseEditor,
            onDiscard = viewModel::requestCloseEditor,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = BaselineSpacing.screen,
                    end = BaselineSpacing.screen,
                    bottom = BaselineSpacing.extraLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
        ) {
            Text(
                text = stringResource(
                    when {
                        state.favoriteEditor != null -> R.string.edit_template
                        editor.mealId == null -> R.string.new_meal
                        else -> R.string.edit_meal
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.meal_review_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (editor.analysisWarnings.isNotEmpty()) {
                BaselineCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                        Text(
                            stringResource(R.string.analysis_warnings),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        editor.analysisWarnings.forEach { warning -> Text("• $warning") }
                    }
                }
            }
            MealDetailsCard(
                editor = editor,
                expanded = showMealDetails,
                onExpanded = { showMealDetails = !showMealDetails },
                onChange = viewModel::updateEditor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.nutrition_overview),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            editor.ingredients.forEachIndexed { index, ingredient ->
                IngredientEditor(
                    ingredient = ingredient,
                    onChange = { changed ->
                        viewModel.updateEditor(
                            editor.copy(ingredients = editor.ingredients.replace(index, changed)),
                        )
                    },
                    onUp = if (index > 0) {{
                        viewModel.updateEditor(
                            editor.copy(ingredients = editor.ingredients.swap(index, index - 1)),
                        )
                    }} else null,
                    onDown = if (index < editor.ingredients.lastIndex) {{
                        viewModel.updateEditor(
                            editor.copy(ingredients = editor.ingredients.swap(index, index + 1)),
                        )
                    }} else null,
                    onDelete = {
                        viewModel.updateEditor(
                            editor.copy(
                                ingredients = editor.ingredients.filterIndexed { i, _ -> i != index },
                            ),
                        )
                    },
                    onTemplate = { viewModel.templateFromIngredient(ingredient) },
                )
            }
            OutlinedButton(
                onClick = viewModel::newIngredient,
                modifier = Modifier.fillMaxWidth(),
                shape = BaselineShapes.button,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(BaselineSpacing.small))
                Text(stringResource(R.string.add_ingredient))
            }

            MealNutritionCard(
                editor = editor,
                expanded = showDirectNutrients,
                onExpanded = { showDirectNutrients = !showDirectNutrients },
                onChange = viewModel::updateEditor,
            )

            TextButton(onClick = { showTools = !showTools }) {
                Icon(
                    if (showTools) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    stringResource(
                        if (showTools) R.string.hide_meal_tools else R.string.meal_tools,
                    ),
                )
            }
            if (showTools) {
                BaselineCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium)) {
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
                    }
                }
            }

            if (state.privateFoods.isNotEmpty()) {
                TextButton(onClick = { showPrivateFoods = !showPrivateFoods }) {
                    Icon(
                        if (showPrivateFoods) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                    Text(stringResource(R.string.private_foods))
                }
                if (showPrivateFoods) {
                    Field(foodQuery, { foodQuery = it }, R.string.search_private_foods)
                    state.privateFoods.filter { food ->
                        foodQuery.isBlank() || food.name.contains(foodQuery, ignoreCase = true) ||
                            food.brand.orEmpty().contains(foodQuery, ignoreCase = true)
                    }.forEach { food ->
                        BaselineCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                                Text(
                                    listOfNotNull(food.name, food.brand).joinToString(" · "),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${food.defaultAmount} ${food.unit} · ${food.basis}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    TextButton(onClick = { viewModel.addPrivateFood(food) }) {
                                        Text(stringResource(R.string.add))
                                    }
                                    TextButton(onClick = { viewModel.editTemplate(food) }) {
                                        Text(stringResource(R.string.edit))
                                    }
                                    TextButton(onClick = { viewModel.duplicateTemplate(food) }) {
                                        Text(stringResource(R.string.duplicate))
                                    }
                                    TextButton(onClick = { viewModel.deleteTemplate(food) }) {
                                        Text(stringResource(R.string.delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let { ErrorText(it) }
            BaselinePrimaryButton(
                text = stringResource(if (state.saving) R.string.saving else R.string.save),
                enabled = !state.saving,
                onClick = viewModel::saveEditor,
                icon = Icons.Rounded.Check,
                modifier = Modifier.testTag("meal-save"),
            )
            Text(
                text = stringResource(R.string.meal_review_save_hint),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
private fun MealReviewHeader(
    showDiscard: Boolean,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = BaselineSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(start = BaselineSpacing.small),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (showDiscard) {
            IconButton(onClick = onDiscard) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.discard),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun MealDetailsCard(
    editor: MealEditorDraft,
    expanded: Boolean,
    onExpanded: () -> Unit,
    onChange: (MealEditorDraft) -> Unit,
) {
    BaselineCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
                ) {
                    Text(
                        editor.name.ifBlank { stringResource(R.string.new_meal) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${stringResource(mealTypeLabel(editor.mealType))} · ${editorMoment(editor)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onExpanded) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                    )
                }
            }
            if (expanded) {
                Field(editor.name, { onChange(editor.copy(name = it)) }, R.string.meal_name)
                Text(stringResource(R.string.meal_type), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                ) {
                    mealTypes.forEach { type ->
                        FilterChip(
                            selected = editor.mealType == type,
                            onClick = { onChange(editor.copy(mealType = type)) },
                            label = { Text(stringResource(mealTypeLabel(type))) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                    Field(
                        editor.day,
                        { onChange(editor.copy(day = it)) },
                        R.string.date,
                        Modifier.weight(1f),
                    )
                    Field(
                        editor.time,
                        { onChange(editor.copy(time = it)) },
                        R.string.time,
                        Modifier.weight(1f),
                    )
                }
                Field(editor.note, { onChange(editor.copy(note = it)) }, R.string.note)
            }
        }
    }
}

@Composable
private fun MealNutritionCard(
    editor: MealEditorDraft,
    expanded: Boolean,
    onExpanded: () -> Unit,
    onChange: (MealEditorDraft) -> Unit,
) {
    val totals = editor.totals()
    BaselineCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium)) {
            Text(stringResource(R.string.nutrition_overview), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        totals["energy"]?.display() ?: "–",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(R.string.kcal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1.7f),
                    verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                ) {
                    NutritionValue(R.string.protein, totals["protein"])
                    NutritionValue(R.string.carbohydrates, totals["carbohydrates"])
                    NutritionValue(R.string.fat, totals["fat"])
                }
            }
            TextButton(onClick = onExpanded) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    stringResource(
                        if (expanded) R.string.show_less else R.string.edit_nutrients,
                    ),
                )
            }
            if (expanded) {
                Field(
                    editor.nutrients.energy,
                    { onChange(editor.copy(nutrients = editor.nutrients.copy(energy = it))) },
                    R.string.energy_kcal,
                )
                NutrientEditor(
                    editor.nutrients,
                    { onChange(editor.copy(nutrients = it)) },
                    false,
                )
            }
        }
    }
}

@Composable
private fun NutritionValue(label: Int, value: BigDecimal?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${value?.display() ?: "–"} g", fontWeight = FontWeight.SemiBold)
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
    var expanded by rememberSaveable(ingredient.key) { mutableStateOf(ingredient.name.isBlank()) }
    var lastValidAmount by rememberSaveable(ingredient.key) { mutableStateOf(ingredient.amount) }
    val editDescription = stringResource(R.string.edit)
    val amountDescription = stringResource(R.string.amount)
    val unitDescription = stringResource(R.string.unit)
    LaunchedEffect(ingredient.amount) {
        val parsed = parseLocalizedDecimal(ingredient.amount)
        if (parsed != null && parsed > BigDecimal.ZERO) {
            lastValidAmount = ingredient.amount
        }
    }
    fun updateAmount(value: String) {
        val previous = parseLocalizedDecimal(lastValidAmount)
        val next = parseLocalizedDecimal(value)
        if (previous != null && previous > BigDecimal.ZERO && next != null && next > BigDecimal.ZERO) {
            onChange(ingredient.copy(amount = lastValidAmount).withScaledAmount(value))
            lastValidAmount = value
        } else {
            onChange(ingredient.copy(amount = value))
        }
    }
    BaselineCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .semantics {
                            contentDescription = "${ingredient.name}, $editDescription"
                        },
                    verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ingredient.name.ifBlank { stringResource(R.string.ingredient_name) },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = BaselineSpacing.tiny)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val detail = ingredient.preparation.ifBlank {
                        ingredient.nutrients.energy.takeIf(String::isNotBlank)?.let { "$it kcal" }.orEmpty()
                    }
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedTextField(
                    value = ingredient.amount,
                    onValueChange = ::updateAmount,
                    singleLine = true,
                    shape = BaselineShapes.input,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .width(76.dp)
                        .semantics { contentDescription = amountDescription },
                )
                OutlinedTextField(
                    value = ingredient.unit,
                    onValueChange = { onChange(ingredient.copy(unit = it)) },
                    singleLine = true,
                    shape = BaselineShapes.input,
                    modifier = Modifier
                        .width(64.dp)
                        .semantics { contentDescription = unitDescription },
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.remove),
                    )
                }
            }
            if (expanded) {
                Field(ingredient.name, { onChange(ingredient.copy(name = it)) }, R.string.ingredient_name)
                Field(
                    ingredient.preparation,
                    { onChange(ingredient.copy(preparation = it)) },
                    R.string.preparation,
                )
                NutrientEditor(
                    ingredient.nutrients,
                    { onChange(ingredient.copy(nutrients = it)) },
                    true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    onUp?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.move_up),
                            )
                        }
                    }
                    onDown?.let {
                        IconButton(onClick = it) {
                            Icon(
                                Icons.Rounded.ArrowDownward,
                                contentDescription = stringResource(R.string.move_down),
                            )
                        }
                    }
                    IconButton(onClick = onTemplate) {
                        Icon(
                            Icons.Rounded.BookmarkAdd,
                            contentDescription = stringResource(R.string.save_as_template),
                        )
                    }
                }
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

private fun editorMoment(editor: MealEditorDraft): String {
    val day = runCatching {
        LocalDate.parse(editor.day).format(
            DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.getDefault()),
        )
    }.getOrDefault(editor.day)
    return "$day · ${editor.time}"
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
