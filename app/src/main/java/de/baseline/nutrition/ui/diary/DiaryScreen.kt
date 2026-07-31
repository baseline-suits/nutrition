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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.baseline.nutrition.R
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.diary.IngredientDraft
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.NutrientFields
import de.baseline.nutrition.domain.diary.PrivateFoodEditorDraft
import de.baseline.nutrition.domain.diary.parseLocalizedDecimal
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    authRepository: AuthRepository,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    when {
        state.templateEditor != null -> TemplateEditorScreen(state, viewModel)
        state.editor != null -> MealEditorScreen(state, viewModel)
        else -> DayScreen(state, viewModel, authRepository, ioDispatcher, onLoggedOut)
    }
}

@Composable
private fun DayScreen(
    state: DiaryUiState,
    viewModel: DiaryViewModel,
    authRepository: AuthRepository,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var deleteCandidate by remember { mutableStateOf<MealDto?>(null) }
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
            TextButton(onClick = {
                scope.launch {
                    withContext(ioDispatcher) { authRepository.logout(false) }
                    onLoggedOut()
                }
            }) { Text(stringResource(R.string.logout_this_device)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(onClick = { viewModel.selectDay(state.selectedDay.minusDays(1)) }) { Text("‹") }
            Text(state.selectedDay.toString(), modifier = Modifier.padding(vertical = 12.dp))
            OutlinedButton(onClick = { viewModel.selectDay(state.selectedDay.plusDays(1)) }) { Text("›") }
            if (state.selectedDay != LocalDate.now()) {
                TextButton(onClick = { viewModel.selectDay(LocalDate.now()) }) { Text(stringResource(R.string.today)) }
            }
        }
        if (state.loading && state.meals.isEmpty()) {
            Text(stringResource(R.string.loading_diary))
        } else {
            ProgressOverview(state)
            state.error?.let { ErrorText(it) }
            if (state.meals.isEmpty()) {
                Text(stringResource(R.string.empty_day_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.empty_day_description))
                Button(onClick = viewModel::newManualEntry) { Text(stringResource(R.string.manual_entry)) }
            } else {
                mealTypes.forEach { type ->
                    val meals = state.meals.filter { it.mealType == type }
                    if (meals.isNotEmpty()) {
                        Text(stringResource(mealTypeLabel(type)), fontWeight = FontWeight.Bold)
                        meals.forEach { meal ->
                            MealCard(
                                meal = meal,
                                onEdit = { viewModel.edit(meal) },
                                onDuplicate = { viewModel.duplicate(meal) },
                                onDelete = { deleteCandidate = meal },
                            )
                        }
                    }
                }
                Button(onClick = viewModel::newManualEntry) { Text(stringResource(R.string.manual_entry)) }
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
}

@Composable
private fun ProgressOverview(state: DiaryUiState) {
    val summary = state.summary ?: return
    val targets = state.targets
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BaselineSpacing.medium), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(stringResource(R.string.daily_progress), fontWeight = FontWeight.Bold)
            ProgressLine(R.string.energy, summary.totals["energy"], targets?.energy, "kcal")
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
private fun MealCard(meal: MealDto, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val totals = mealTotals(meal)
    val time = runCatching { OffsetDateTime.parse(meal.eatenAt).toLocalTime().withSecond(0).withNano(0) }.getOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(BaselineSpacing.medium), verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            Text(meal.name, fontWeight = FontWeight.Bold)
            Text("${time ?: "–"} · ${totals["energy"]?.display() ?: "–"} kcal")
            Text("P ${totals["protein"]?.display() ?: "–"} g · KH ${totals["carbohydrates"]?.display() ?: "–"} g · F ${totals["fat"]?.display() ?: "–"} g")
            val source = (meal.nutrients + meal.ingredients.flatMap { it.nutrients }).firstOrNull()?.source
                ?: meal.provenanceSource ?: meal.captureMethod
            Text("${stringResource(R.string.source)}: ${sourceLabel(source)} · ${stringResource(R.string.synced)}")
            val micros = (meal.nutrients + meal.ingredients.flatMap { it.nutrients })
                .filterNot { it.key in coreNutrients }
            if (micros.isNotEmpty()) {
                Text(micros.joinToString(" · ") { "${it.key}: ${it.value} ${it.unit}" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDuplicate) { Text(stringResource(R.string.duplicate)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun MealEditorScreen(state: DiaryUiState, viewModel: DiaryViewModel) {
    val editor = state.editor ?: return
    var showMacros by rememberSaveable { mutableStateOf(editor.nutrients.protein.isNotBlank()) }
    var foodQuery by rememberSaveable { mutableStateOf("") }
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
        Text(stringResource(if (editor.mealId == null) R.string.new_meal else R.string.edit_meal), fontWeight = FontWeight.Bold)
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
    }))
}

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
