package de.baseline.nutrition.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.diary.HistoryAggregateDto
import de.baseline.nutrition.data.diary.HistoryDayDto
import de.baseline.nutrition.data.diary.HistoryResponseDto
import de.baseline.nutrition.data.diary.HistoryWeekDto
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onSelectDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    HistoryContent(
        state = state,
        onRange = viewModel::setRange,
        onSelectDay = onSelectDay,
        onRetry = viewModel::refresh,
        onClose = onClose,
    )
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onRange: (Int) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BaselineSpacing.screen, vertical = BaselineSpacing.small)
            .testTag("history-screen"),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
    ) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.history_subtitle, state.rangeDays),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            FilterChip(
                selected = state.rangeDays == 7,
                onClick = { onRange(7) },
                label = { Text(stringResource(R.string.last_7_days)) },
                modifier = Modifier.testTag("history-range-7"),
            )
            FilterChip(
                selected = state.rangeDays == 30,
                onClick = { onRange(30) },
                label = { Text(stringResource(R.string.last_30_days)) },
                modifier = Modifier.testTag("history-range-30"),
            )
        }
        when {
            state.loading && state.data == null -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
            state.error && state.data == null -> HistoryError(onRetry)
        }
        if (state.cached) {
            Text(
                stringResource(R.string.history_cached),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("history-cached"),
            )
        }
        if (state.error && state.data != null) {
            HistoryError(onRetry)
        }
        state.data?.let { data ->
            HistoryDateRange(data)
            HistoryCaloriesCard(data.summary, data.totalDays)
            HistoryMacrosCard(data.summary, data.totalDays)
            CalendarCard(data, onSelectDay)
            if (data.summary.trackedDays == 0) {
                BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-empty")) {
                    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny)) {
                        Text(
                            stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.history_empty_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HistorySummaryCard(data.summary, data.totalDays)
            Text(stringResource(R.string.weekly_overview), style = MaterialTheme.typography.titleMedium)
            data.weeks.asReversed().forEach { week ->
                val context = data.days
                    .lastOrNull { it.localDay in week.start..week.end && it.targets != null }
                    ?.targets
                WeekCard(week, context)
            }
        }
        Spacer(Modifier.height(BaselineSpacing.tiny))
    }
}

@Composable
private fun HistoryError(onRetry: () -> Unit) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-error")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.history_error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun HistoryDateRange(data: HistoryResponseDto) {
    val locale = currentLocale()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BaselineShapes.input,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BaselineSpacing.medium),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(
                    R.string.history_range,
                    LocalDate.parse(data.start).format(formatter),
                    LocalDate.parse(data.end).format(formatter),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = BaselineSpacing.small),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HistoryCaloriesCard(summary: HistoryAggregateDto, totalDays: Int) {
    val average = summary.averages["energy"].number()
    val target = summary.targetAverages["energy"].number()
    val progress = ratio(average, target)
    val denominator = summary.averageDenominators["energy"] ?: 0
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-calories")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.overview_calories), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.history_average_energy, average.displayNumber()),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(R.string.history_average_target, target.displayNumber(), "kcal"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(BaselineShapes.pill),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.history_goal_reached, (progress * 100).toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.history_data_days, denominator, totalDays),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryMacrosCard(summary: HistoryAggregateDto, totalDays: Int) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-macros")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium)) {
            Text(stringResource(R.string.macronutrients), style = MaterialTheme.typography.titleMedium)
            HistoryMacroRow(
                label = R.string.protein,
                key = "protein",
                icon = Icons.Rounded.EggAlt,
                color = MaterialTheme.colorScheme.primary,
                summary = summary,
                totalDays = totalDays,
            )
            HistoryMacroRow(
                label = R.string.carbohydrates,
                key = "carbohydrates",
                icon = Icons.Rounded.Grain,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                summary = summary,
                totalDays = totalDays,
            )
            HistoryMacroRow(
                label = R.string.fat,
                key = "fat",
                icon = Icons.Rounded.WaterDrop,
                color = MaterialTheme.colorScheme.secondary,
                summary = summary,
                totalDays = totalDays,
            )
        }
    }
}

@Composable
private fun HistoryMacroRow(
    label: Int,
    key: String,
    icon: ImageVector,
    color: Color,
    summary: HistoryAggregateDto,
    totalDays: Int,
) {
    val average = summary.averages[key].number()
    val target = summary.targetAverages[key].number()
    val progress = ratio(average, target)
    val denominator = summary.averageDenominators[key] ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f).padding(start = BaselineSpacing.small)) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(
                        R.string.history_average_macro,
                        average.displayNumber(),
                        target.displayNumber(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.history_data_days, denominator, totalDays),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(BaselineShapes.pill),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun HistorySummaryCard(summary: HistoryAggregateDto, totalDays: Int) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-summary")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.recording_coverage), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.history_tracked_of_total, summary.trackedDays, totalDays),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(
                    R.string.coverage_breakdown,
                    summary.completeDays,
                    summary.partialDays,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.average_denominator_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CalendarCard(data: HistoryResponseDto, onSelectDay: (LocalDate) -> Unit) {
    val parsed = data.days.map { LocalDate.parse(it.localDay) to it }
    val first = parsed.firstOrNull()?.first
    val leading = first?.dayOfWeek?.value?.minus(1) ?: 0
    val cells: List<Pair<LocalDate, HistoryDayDto>?> = List(leading) { null } + parsed
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-calendar")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, currentLocale()),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        if (cell == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            CalendarDay(
                                day = cell.first,
                                historyDay = cell.second,
                                onSelectDay = onSelectDay,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
            ) {
                StatusLegend("complete", Modifier.weight(1f))
                StatusLegend("partial", Modifier.weight(1f))
                StatusLegend("none", Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.history_complete_definition),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CalendarDay(
    day: LocalDate,
    historyDay: HistoryDayDto,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val description = stringResource(
        R.string.history_day_description,
        day.toString(),
        historyStatusLabel(historyDay.status),
    )
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(BaselineShapes.input)
            .clickable { onSelectDay(day) }
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .testTag("history-day-$day")
            .padding(vertical = BaselineSpacing.tiny),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        StatusIcon(historyDay.status, Modifier.size(14.dp))
    }
}

@Composable
private fun StatusLegend(status: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(status, Modifier.size(14.dp))
        Text(
            historyStatusLabel(status),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = BaselineSpacing.tiny),
        )
    }
}

@Composable
private fun StatusIcon(status: String, modifier: Modifier = Modifier) {
    val icon = when (status) {
        "complete" -> Icons.Rounded.CheckCircle
        "partial" -> Icons.Rounded.RemoveCircleOutline
        else -> Icons.Rounded.RadioButtonUnchecked
    }
    val color = when (status) {
        "complete" -> MaterialTheme.colorScheme.primary
        "partial" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun WeekCard(week: HistoryWeekDto, context: DailyBudgetDto?) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("history-week-${week.start}")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(
                stringResource(
                    R.string.calendar_week,
                    LocalDate.parse(week.start).format(formatter),
                    LocalDate.parse(week.end).format(formatter),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.tracked_days_count, week.trackedDays),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (week.trackedDays == 0) {
                Text(
                    stringResource(R.string.history_week_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HistoryMetric(
                    R.string.energy,
                    "energy",
                    "kcal",
                    week.trackedDays,
                    week.averages,
                    week.averageDenominators,
                    week.targetAverages,
                    week.goalPercentages,
                    week.goalDenominators,
                )
                HistoryMetric(
                    R.string.protein,
                    "protein",
                    "g",
                    week.trackedDays,
                    week.averages,
                    week.averageDenominators,
                    week.targetAverages,
                    week.goalPercentages,
                    week.goalDenominators,
                )
                HistoryMetric(
                    R.string.carbohydrates,
                    "carbohydrates",
                    "g",
                    week.trackedDays,
                    week.averages,
                    week.averageDenominators,
                    week.targetAverages,
                    week.goalPercentages,
                    week.goalDenominators,
                )
                HistoryMetric(
                    R.string.fat,
                    "fat",
                    "g",
                    week.trackedDays,
                    week.averages,
                    week.averageDenominators,
                    week.targetAverages,
                    week.goalPercentages,
                    week.goalDenominators,
                )
                context?.let {
                    val activity = it.activityLevel?.let { value -> activityLabel(value) }
                        ?: stringResource(R.string.not_available)
                    Text(
                        stringResource(
                            R.string.history_context,
                            it.weightKg?.displayNumber() ?: stringResource(R.string.not_available),
                            activity,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.history_context_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(
    label: Int,
    key: String,
    unit: String,
    trackedDays: Int,
    averages: Map<String, String>,
    denominators: Map<String, Int>,
    targetAverages: Map<String, String>,
    goalPercentages: Map<String, String>,
    goalDenominators: Map<String, Int>,
) {
    val average = averages[key]
    if (average == null) {
        Text(
            "${stringResource(label)}: ${stringResource(R.string.not_available)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        stringResource(
            R.string.history_metric_average,
            stringResource(label),
            average.displayNumber(),
            unit,
            denominators[key] ?: 0,
            trackedDays,
        ),
    )
    val target = targetAverages[key]
    val goal = goalPercentages[key]
    if (target != null && goal != null) {
        Text(
            stringResource(
                R.string.history_metric_goal,
                target.displayNumber(),
                unit,
                goal.displayNumber(),
                goalDenominators[key] ?: 0,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun historyStatusLabel(status: String): String = stringResource(
    when (status) {
        "complete" -> R.string.history_complete
        "partial" -> R.string.history_partial
        else -> R.string.history_none
    },
)

@Composable
private fun activityLabel(activity: String): String = stringResource(
    when (activity) {
        "inactive" -> R.string.inactive
        "sometimes" -> R.string.sometimes_active
        "active" -> R.string.active
        "very_active" -> R.string.very_active
        else -> R.string.not_available
    },
)

private fun String?.number(): BigDecimal? = this?.toBigDecimalOrNull()

private fun ratio(current: BigDecimal?, target: BigDecimal?): Float =
    if (current == null || target == null || target <= BigDecimal.ZERO) 0f
    else current.divide(target, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)

@Composable
private fun BigDecimal?.displayNumber(): String = this?.let {
    NumberFormat.getNumberInstance(currentLocale()).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(it.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros())
} ?: "–"

@Composable
private fun String.displayNumber(): String = number().displayNumber()

@Composable
private fun currentLocale(): Locale = Locale.forLanguageTag(
    LocalConfiguration.current.locales[0].toLanguageTag(),
)
