package de.baseline.nutrition.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselinePrimaryButton
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSpacing
import de.baseline.nutrition.ui.theme.BaselineStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun CalorieBudgetScreen(
    state: SettingsUiState,
    budget: DailyBudgetDto?,
    loading: Boolean,
    updateFailed: Boolean,
    onModeChange: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    CalorieBudgetContent(
        state = state,
        budget = budget,
        loading = loading,
        updateFailed = updateFailed,
        onModeChange = onModeChange,
        onOpenHealth = onOpenHealth,
        onClose = onClose,
    )
}

@Composable
internal fun CalorieBudgetContent(
    state: SettingsUiState,
    budget: DailyBudgetDto?,
    loading: Boolean,
    updateFailed: Boolean,
    onModeChange: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var selectedMode by rememberSaveable {
        mutableStateOf(budget?.budgetMode ?: state.profileDraft.calorieBudgetMode)
    }
    LaunchedEffect(budget?.budgetMode) {
        budget?.let { selectedMode = it.budgetMode }
    }
    val today = LocalDate.now().toString()
    val aggregates = state.healthAggregates.filter { it.localDay == today }
    val activeCalories = aggregates.firstOrNull { it.dataType == "active_calories" }
    val steps = aggregates.firstOrNull { it.dataType == "steps" }
    val exercise = aggregates.firstOrNull { it.dataType == "exercise" }
    val activePermission = state.healthConnection
        ?.capabilities
        ?.get(HealthDataType.ActiveCalories)
        ?.permissionState
    val healthStatus = budgetHealthStatus(state.healthConnection?.availability, activePermission)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BaselineSpacing.screen, vertical = BaselineSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("budget-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.calorie_budget_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.calorie_budget_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HealthConnectBudgetCard(
            status = healthStatus,
            permission = activePermission,
            onOpenHealth = onOpenHealth,
        )

        if (budget == null) {
            BaselineCard(modifier = Modifier.fillMaxWidth().testTag("budget-loading")) {
                Text(stringResource(R.string.calorie_budget_loading))
            }
        } else {
            val activity = budget.activityEnergy.toDecimalOrNull()
                ?: activeCalories.readyValue()
            val factor = budget.activityFactor.toDecimalOrNull() ?: BigDecimal("0.5")
            val cap = budget.activityCapEnergy.toDecimalOrNull() ?: BigDecimal("500")
            val possibleContribution = if (budget.activityStatus == "ready" && activity != null) {
                activity.multiply(factor).coerceAtMost(cap)
            } else {
                BigDecimal.ZERO
            }
            val contribution = budget.activityContributionEnergy.toDecimalOrNull() ?: BigDecimal.ZERO
            val progress = if (cap > BigDecimal.ZERO) {
                possibleContribution.divide(cap, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
            ) {
                BudgetMetricCard(
                    title = stringResource(R.string.budget_activity_today),
                    value = activity.format(locale),
                    unit = "kcal",
                    supporting = stringResource(budgetActivityShortStatusLabel(budget.activityStatus)),
                    icon = Icons.Rounded.LocalFireDepartment,
                    progress = progress,
                    modifier = Modifier.weight(1f).testTag("budget-activity-card"),
                )
                BudgetMetricCard(
                    title = stringResource(R.string.calorie_budget_title),
                    value = budget.energy.toDecimalOrNull().format(locale),
                    unit = "kcal",
                    supporting = stringResource(
                        R.string.budget_base_short,
                        (budget.baseEnergy ?: state.profileDraft.targetKcal).toDecimalOrNull().format(locale),
                    ),
                    icon = Icons.Rounded.PieChart,
                    progress = progress,
                    modifier = Modifier.weight(1f).testTag("budget-total-card"),
                )
            }

            if (steps.readyValue() != null || exercise.readyValue() != null) {
                ActivityContextCard(steps = steps, exercise = exercise, locale = locale)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
            ) {
                BudgetModeCard(
                    mode = "fixed",
                    selected = selectedMode == "fixed",
                    enabled = !loading,
                    onSelected = { selectedMode = "fixed" },
                    modifier = Modifier.weight(1f),
                )
                BudgetModeCard(
                    mode = "dynamic",
                    selected = selectedMode == "dynamic",
                    enabled = !loading,
                    onSelected = { selectedMode = "dynamic" },
                    modifier = Modifier.weight(1f),
                )
            }

            BaselineCard(modifier = Modifier.fillMaxWidth().testTag("budget-explanation")) {
                Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.medium)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny)) {
                        Text(
                            stringResource(R.string.budget_how_it_works),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.dynamic_budget_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(
                                R.string.budget_current_calculation,
                                (budget.baseEnergy ?: state.profileDraft.targetKcal)
                                    .toDecimalOrNull().format(locale),
                                contribution.format(locale),
                                budget.energy.toDecimalOrNull().format(locale),
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("budget-current-calculation"),
                        )
                    }
                }
            }

            if (updateFailed) {
                Text(
                    stringResource(R.string.calorie_budget_update_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("budget-update-error"),
                )
            }
            BaselinePrimaryButton(
                text = stringResource(
                    when {
                        selectedMode != budget.budgetMode && selectedMode == "dynamic" -> {
                            R.string.activate_dynamic_budget
                        }
                        selectedMode != budget.budgetMode -> R.string.activate_fixed_budget
                        selectedMode == "dynamic" -> R.string.dynamic_budget_active
                        else -> R.string.fixed_budget_active
                    },
                ),
                onClick = { onModeChange(selectedMode) },
                enabled = !loading && selectedMode != budget.budgetMode,
                modifier = Modifier.testTag("budget-save-mode"),
            )
        }
        Spacer(Modifier.height(BaselineSpacing.small))
    }
}

@Composable
private fun HealthConnectBudgetCard(
    status: BudgetHealthStatus,
    permission: HealthPermissionState?,
    onOpenHealth: () -> Unit,
) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("budget-health-connect")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                    Icon(
                        Icons.Rounded.MonitorHeart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.health_connect_title), style = MaterialTheme.typography.titleMedium)
                }
                BaselineStatus(text = stringResource(budgetHealthStatusLabel(status)))
            }
            Text(
                text = if (permission == null) {
                    stringResource(R.string.budget_health_no_activity_access)
                } else {
                    stringResource(budgetPermissionLabel(permission))
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenHealth, modifier = Modifier.testTag("budget-open-health")) {
                Text(stringResource(R.string.settings_manage_health))
            }
        }
    }
}

@Composable
private fun BudgetMetricCard(
    title: String,
    value: String,
    unit: String,
    supporting: String,
    icon: ImageVector,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 190.dp),
        shape = BaselineShapes.compactCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny)) {
                Text(value, style = MaterialTheme.typography.headlineMedium)
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivityContextCard(
    steps: SettingsHealthAggregateDto?,
    exercise: SettingsHealthAggregateDto?,
    locale: Locale,
) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("budget-activity-context")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.budget_activity_context), style = MaterialTheme.typography.titleMedium)
            ActivityContextRow(
                icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                label = stringResource(R.string.health_type_steps),
                value = steps.readyValue().format(locale),
            )
            ActivityContextRow(
                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                label = stringResource(R.string.health_type_exercise),
                value = exercise.readyValue()
                    ?.divide(BigDecimal("60"), 1, RoundingMode.HALF_UP)
                    .format(locale),
                unit = stringResource(R.string.minutes_short),
            )
        }
    }
}

@Composable
private fun ActivityContextRow(icon: ImageVector, label: String, value: String, unit: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label)
        }
        Text(
            listOf(value, unit).filter(String::isNotBlank).joinToString(" "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetModeCard(
    mode: String,
    selected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onSelected,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 112.dp)
            .testTag("settings-budget-mode-$mode"),
        shape = BaselineShapes.compactCard,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(if (mode == "dynamic") R.string.dynamic_budget else R.string.fixed_budget),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                stringResource(
                    if (mode == "dynamic") {
                        R.string.dynamic_budget_short_description
                    } else {
                        R.string.fixed_budget_short_description
                    },
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private enum class BudgetHealthStatus { Connected, NotConnected, Unavailable }

private fun budgetHealthStatus(
    availability: HealthAvailability?,
    permission: HealthPermissionState?,
): BudgetHealthStatus = when {
    availability != HealthAvailability.Available -> BudgetHealthStatus.Unavailable
    permission == HealthPermissionState.Granted -> BudgetHealthStatus.Connected
    else -> BudgetHealthStatus.NotConnected
}

private fun budgetHealthStatusLabel(status: BudgetHealthStatus): Int = when (status) {
    BudgetHealthStatus.Connected -> R.string.budget_health_connected
    BudgetHealthStatus.NotConnected -> R.string.budget_health_not_connected
    BudgetHealthStatus.Unavailable -> R.string.budget_health_unavailable
}

private fun budgetPermissionLabel(permission: HealthPermissionState): Int = when (permission) {
    HealthPermissionState.NotRequested -> R.string.health_permission_not_requested
    HealthPermissionState.Granted -> R.string.health_permission_granted
    HealthPermissionState.Disabled -> R.string.health_permission_disabled
    HealthPermissionState.Denied -> R.string.health_permission_denied
    HealthPermissionState.PermanentlyDenied -> R.string.health_permission_permanently_denied
    HealthPermissionState.Revoked -> R.string.health_permission_revoked
}

private fun budgetActivityShortStatusLabel(status: String): Int = when (status) {
    "missing" -> R.string.budget_status_missing_short
    "partial" -> R.string.budget_status_partial_short
    "ready" -> R.string.budget_status_ready_short
    "conflict" -> R.string.budget_status_conflict_short
    else -> R.string.budget_status_not_synced_short
}

private fun SettingsHealthAggregateDto?.readyValue(): BigDecimal? =
    this?.takeIf { it.status == "ready" }?.value.toDecimalOrNull()

private fun String?.toDecimalOrNull(): BigDecimal? = this?.toBigDecimalOrNull()

private fun BigDecimal?.format(locale: Locale): String {
    if (this == null) return "–"
    return NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(this)
}
