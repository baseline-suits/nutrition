package de.baseline.nutrition.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.DailyBudgetDto
import de.baseline.nutrition.data.diary.HistoryAggregateDto
import de.baseline.nutrition.data.diary.HistoryDayDto
import de.baseline.nutrition.data.diary.HistoryResponseDto
import de.baseline.nutrition.data.diary.HistoryWeekDto
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.math.RoundingMode
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large)
            .testTag("history-screen"),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold)
        }
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
        if (state.loading && state.data == null) {
            Text(stringResource(R.string.loading_history))
        }
        if (state.error) {
            Card(modifier = Modifier.fillMaxWidth().testTag("history-error")) {
                Column(
                    Modifier.padding(BaselineSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                ) {
                    Text(stringResource(R.string.history_error))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        if (state.cached) {
            Text(
                stringResource(R.string.history_cached),
                modifier = Modifier.testTag("history-cached"),
            )
        }
        state.data?.let { data ->
            HistorySummaryCard(data.summary)
            CalendarCard(data, onSelectDay)
            if (data.summary.trackedDays == 0) {
                Card(modifier = Modifier.fillMaxWidth().testTag("history-empty")) {
                    Column(Modifier.padding(BaselineSpacing.medium)) {
                        Text(stringResource(R.string.history_empty_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.history_empty_text))
                    }
                }
            }
            Text(stringResource(R.string.weekly_overview), fontWeight = FontWeight.Bold)
            data.weeks.asReversed().forEach { week ->
                val context = data.days
                    .lastOrNull { it.localDay in week.start..week.end && it.targets != null }
                    ?.targets
                WeekCard(week, context)
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(summary: HistoryAggregateDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.recording_coverage), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tracked_days_count, summary.trackedDays))
            Text(
                stringResource(
                    R.string.coverage_breakdown,
                    summary.completeDays,
                    summary.partialDays,
                ),
            )
            Text(stringResource(R.string.average_denominator_hint))
        }
    }
}

@Composable
private fun CalendarCard(data: HistoryResponseDto, onSelectDay: (LocalDate) -> Unit) {
    val parsed = data.days.map { LocalDate.parse(it.localDay) to it }
    val first = parsed.firstOrNull()?.first
    val leading = first?.dayOfWeek?.value?.minus(1) ?: 0
    val cells: List<Pair<LocalDate, HistoryDayDto>?> = List(leading) { null } + parsed
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(BaselineSpacing.small),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(R.string.calendar_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.history_range,
                    LocalDate.parse(data.start).format(dateFormatter),
                    LocalDate.parse(data.end).format(dateFormatter),
                ),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
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
                                cell.first,
                                cell.second,
                                onSelectDay,
                                Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                "${stringResource(R.string.history_complete)} ✓  ·  " +
                    "${stringResource(R.string.history_partial)} ~  ·  " +
                    "${stringResource(R.string.history_none)} –",
            )
            Text(stringResource(R.string.history_complete_definition))
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
    val status = historyStatusLabel(historyDay.status)
    val marker = when (historyDay.status) {
        "complete" -> "✓"
        "partial" -> "~"
        else -> "–"
    }
    val description = stringResource(R.string.history_day_description, day.toString(), status)
    OutlinedButton(
        onClick = { onSelectDay(day) },
        modifier = modifier
            .padding(1.dp)
            .semantics { contentDescription = description }
            .testTag("history-day-$day"),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 1.dp),
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(day.dayOfMonth.toString())
            Text(marker)
        }
    }
}

@Composable
private fun WeekCard(week: HistoryWeekDto, context: DailyBudgetDto?) {
    Card(modifier = Modifier.fillMaxWidth().testTag("history-week-${week.start}")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(
                stringResource(R.string.calendar_week, week.start, week.end),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.tracked_days_count, week.trackedDays))
            if (week.trackedDays == 0) {
                Text(stringResource(R.string.history_week_empty))
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
                            it.weightKg?.display() ?: stringResource(R.string.not_available),
                            activity,
                        ),
                    )
                    Text(stringResource(R.string.history_context_hint))
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
        Text("${stringResource(label)}: ${stringResource(R.string.not_available)}")
        return
    }
    Text(
        stringResource(
            R.string.history_metric_average,
            stringResource(label),
            average.display(),
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
                target.display(),
                unit,
                goal.display(),
                goalDenominators[key] ?: 0,
            ),
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

private fun String.display(): String = toBigDecimalOrNull()
    ?.setScale(1, RoundingMode.HALF_UP)
    ?.stripTrailingZeros()
    ?.toPlainString()
    ?: this
