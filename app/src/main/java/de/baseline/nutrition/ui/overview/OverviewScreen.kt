package de.baseline.nutrition.ui.overview

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrunchDining
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.toDiaryTargets
import de.baseline.nutrition.ui.diary.DiaryError
import de.baseline.nutrition.ui.diary.DiaryUiState
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselinePrimaryButton
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSkeleton
import de.baseline.nutrition.ui.theme.BaselineSpacing
import de.baseline.nutrition.ui.theme.BaselineStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun OverviewScreen(
    state: DiaryUiState,
    onMeal: (MealDto) -> Unit,
    onAddMeal: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> R.string.overview_greeting_morning
        in 12..17 -> R.string.overview_greeting_afternoon
        else -> R.string.overview_greeting_evening
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BaselineSpacing.screen, vertical = BaselineSpacing.small),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(greeting),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.overview_title), style = MaterialTheme.typography.titleLarge)
        Text(
            state.selectedDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            state.loading && state.summary == null -> OverviewLoading()
            else -> {
                state.error?.let { OverviewError(it, onRefresh) }
                DailyNutritionOverview(state)
                MealsOverview(state, onMeal, onAddMeal)
            }
        }
        Spacer(Modifier.height(BaselineSpacing.tiny))
    }
}

@Composable
internal fun DailyNutritionOverview(state: DiaryUiState) {
    val targets = state.summary?.targets?.toDiaryTargets() ?: state.targets
    val energy = state.summary?.totals?.get("energy").number()
    val targetEnergy = targets?.energy.number()
    val progress = ratio(energy, targetEnergy)
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("overview-calories")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Text(
                    stringResource(R.string.overview_calories),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(energy.display(), style = MaterialTheme.typography.displaySmall)
                    Text(
                        " / ${targetEnergy.display()} kcal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = BaselineSpacing.tiny),
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(BaselineShapes.pill),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
                val remaining = targetEnergy?.subtract(energy ?: BigDecimal.ZERO)?.coerceAtLeast(BigDecimal.ZERO)
                Text(
                    stringResource(R.string.overview_remaining, remaining.display()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(BaselineSpacing.medium))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 7.dp,
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
    ) {
        MacroCard(
            R.string.protein,
            state.summary?.totals?.get("protein").number(),
            targets?.protein.number(),
            MaterialTheme.colorScheme.primary,
            Icons.Rounded.EggAlt,
            Modifier.weight(1f),
        )
        MacroCard(
            R.string.carbohydrates,
            state.summary?.totals?.get("carbohydrates").number(),
            targets?.carbohydrates.number(),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
            Icons.Rounded.Grain,
            Modifier.weight(1f),
        )
        MacroCard(
            R.string.fat,
            state.summary?.totals?.get("fat").number(),
            targets?.fat.number(),
            MaterialTheme.colorScheme.secondary,
            Icons.Rounded.WaterDrop,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun MacroCard(
    label: Int,
    current: BigDecimal?,
    target: BigDecimal?,
    color: Color,
    icon: ImageVector,
    modifier: Modifier,
) {
    val progress = ratio(current, target)
    Card(
        modifier = modifier.heightIn(min = 88.dp),
        shape = BaselineShapes.compactCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Text(
                    stringResource(label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${current.display()} / ${target.display()} g",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(BaselineShapes.pill),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Text(
                "${(progress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MealsOverview(
    state: DiaryUiState,
    onMeal: (MealDto) -> Unit,
    onAddMeal: () -> Unit,
) {
    BaselineCard(modifier = Modifier.fillMaxWidth().testTag("overview-meals")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            }
            if (state.meals.isEmpty()) {
                Text(
                    stringResource(R.string.overview_no_meals),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = BaselineSpacing.medium),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.meals.sortedBy(MealDto::eatenAt).forEach { meal ->
                    MealSummaryRow(meal, onMeal)
                    }
                }
            }
            Spacer(Modifier.height(BaselineSpacing.tiny))
            BaselinePrimaryButton(
                text = stringResource(R.string.add_meal_title),
                onClick = onAddMeal,
                icon = Icons.Rounded.Add,
                modifier = Modifier.testTag("overview-add-meal"),
            )
        }
    }
}

@Composable
internal fun MealSummaryRow(meal: MealDto, onMeal: (MealDto) -> Unit) {
    val description = stringResource(R.string.overview_open_meal, meal.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(BaselineShapes.input)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onMeal(meal) }
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = mealIcon(meal.mealType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(BaselineSpacing.small))
        Column(Modifier.weight(1f)) {
            Text(stringResource(mealTypeLabel(meal.mealType)), style = MaterialTheme.typography.titleMedium)
            Text(
                meal.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${meal.nutrients.firstOrNull { it.key == "energy" }?.value.number().display()} kcal",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium)) {
        BaselineSkeleton(Modifier.fillMaxWidth().height(190.dp).testTag("overview-loading"))
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            repeat(3) { BaselineSkeleton(Modifier.weight(1f).height(126.dp)) }
        }
        BaselineSkeleton(Modifier.fillMaxWidth().height(240.dp))
    }
}

@Composable
private fun OverviewError(error: DiaryError, onRefresh: () -> Unit) {
    BaselineCard(Modifier.fillMaxWidth().testTag("overview-error")) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(
                stringResource(
                    if (error == DiaryError.Network) R.string.overview_offline else R.string.overview_error,
                ),
                color = if (error == DiaryError.Network) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            androidx.compose.material3.TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private fun String?.number(): BigDecimal? = this?.toBigDecimalOrNull()

@Composable
private fun BigDecimal?.display(): String {
    if (this == null) return "–"
    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
    return NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(setScale(1, RoundingMode.HALF_UP).stripTrailingZeros())
}

private fun ratio(current: BigDecimal?, target: BigDecimal?): Float =
    if (current == null || target == null || target <= BigDecimal.ZERO) 0f
    else current.divide(target, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)

private fun mealTypeLabel(type: String): Int = when (type) {
    "breakfast" -> R.string.breakfast
    "lunch" -> R.string.lunch
    "dinner" -> R.string.dinner
    else -> R.string.snacks
}

private fun mealIcon(type: String): ImageVector = when (type) {
    "breakfast" -> Icons.Rounded.BrunchDining
    "lunch" -> Icons.Rounded.LunchDining
    "dinner" -> Icons.Rounded.DinnerDining
    else -> Icons.Rounded.Restaurant
}
