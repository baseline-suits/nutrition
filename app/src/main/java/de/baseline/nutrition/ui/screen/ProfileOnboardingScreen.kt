package de.baseline.nutrition.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.baseline.nutrition.R
import de.baseline.nutrition.data.profile.ProfileRepository
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.domain.onboarding.GoalCalculator
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileOnboardingScreen(
    repository: ProfileRepository,
    ioDispatcher: CoroutineDispatcher,
    onComplete: () -> Unit,
) {
    var locale by rememberSaveable { mutableStateOf("de") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var birthDate by rememberSaveable { mutableStateOf("1990-01-01") }
    var height by rememberSaveable { mutableStateOf("175") }
    var weight by rememberSaveable { mutableStateOf("70") }
    var biologicalInput by rememberSaveable { mutableStateOf("female") }
    var activity by rememberSaveable { mutableStateOf("sometimes") }
    var direction by rememberSaveable { mutableStateOf("maintain") }
    var kcal by rememberSaveable { mutableStateOf("2000") }
    var protein by rememberSaveable { mutableStateOf("120") }
    var carbs by rememberSaveable { mutableStateOf("220") }
    var fat by rememberSaveable { mutableStateOf("70") }
    var error by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Text(stringResource(R.string.onboarding_title))
        Choice(R.string.language, locale, listOf("de", "ru")) { locale = it }
        Choice(R.string.goal_method, if (manual) "manual" else "calculate", listOf("calculate", "manual")) {
            manual = it == "manual"
        }
        if (!manual) {
            Field(R.string.birth_date, birthDate) { birthDate = it }
            Field(R.string.height_cm, height) { height = it }
            Field(R.string.weight_kg, weight) { weight = it }
            Choice(R.string.biological_input, biologicalInput, listOf("female", "male")) { biologicalInput = it }
            Choice(R.string.activity_level, activity, listOf("inactive", "sometimes", "active", "very_active")) {
                activity = it
            }
            Choice(R.string.goal_direction, direction, listOf("maintain", "deficit", "surplus")) { direction = it }
            Button(onClick = {
                runCatching {
                    GoalCalculator.calculate(
                        LocalDate.parse(birthDate), biologicalInput, height.toDouble(), weight.toDouble(),
                        activity, direction,
                    )
                }.onSuccess {
                    kcal = it.targetKcal.toString()
                    protein = it.proteinGrams.toString()
                    carbs = it.carbsGrams.toString()
                    fat = it.fatGrams.toString()
                    error = false
                }.onFailure { error = true }
            }) { Text(stringResource(R.string.calculate_targets)) }
        }
        Field(R.string.target_kcal, kcal) { kcal = it }
        Field(R.string.target_protein, protein) { protein = it }
        Field(R.string.target_carbs, carbs) { carbs = it }
        Field(R.string.target_fat, fat) { fat = it }
        Text(stringResource(R.string.goal_disclaimer))
        if (error) Text(stringResource(R.string.onboarding_validation_error))
        Button(enabled = !saving, onClick = {
            val request = runCatching {
                val calculation = if (manual) null else GoalCalculator.calculate(
                    LocalDate.parse(birthDate), biologicalInput, height.toDouble(), weight.toDouble(),
                    activity, direction,
                )
                ProfileRequest(
                    locale = locale,
                    timezone = ZoneId.systemDefault().id,
                    birthDate = if (manual) null else LocalDate.parse(birthDate).toString(),
                    biologicalInput = if (manual) null else biologicalInput,
                    heightCm = if (manual) null else height.toDouble().toString(),
                    weightKg = if (manual) null else weight.toDouble().toString(),
                    activityLevel = if (manual) null else activity,
                    goalDirection = if (manual) null else direction,
                    targetKcal = kcal.toDouble().toString(),
                    targetProtein = protein.toDouble().toString(),
                    targetCarbs = carbs.toDouble().toString(),
                    targetFat = fat.toDouble().toString(),
                    manual = manual,
                    calculation = calculation?.let {
                        mapOf(
                            "formula_version" to it.formulaVersion,
                            "basal_kcal" to it.basalKcal.toString(),
                            "maintenance_kcal" to it.maintenanceKcal.toString(),
                        )
                    },
                )
            }.getOrElse { error = true; return@Button }
            saving = true
            scope.launch {
                runCatching { withContext(ioDispatcher) { repository.save(request) } }
                    .onSuccess { onComplete() }
                    .onFailure { error = true }
                saving = false
            }
        }) { Text(stringResource(R.string.finish_onboarding)) }
    }
}

@Composable
private fun Field(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(stringResource(label)) })
}

@Composable
private fun Choice(label: Int, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    Text(stringResource(label))
    values.forEach { value ->
        FilterChip(
            selected = selected == value,
            onClick = { onSelect(value) },
            label = { Text(stringResource(optionLabel(value))) },
        )
    }
}

private fun optionLabel(value: String): Int = when (value) {
    "de" -> R.string.german
    "ru" -> R.string.russian
    "manual" -> R.string.manual_targets
    "calculate" -> R.string.calculated_targets
    "female" -> R.string.female
    "male" -> R.string.male
    "inactive" -> R.string.inactive
    "sometimes" -> R.string.sometimes_active
    "active" -> R.string.active
    "very_active" -> R.string.very_active
    "maintain" -> R.string.maintain
    "deficit" -> R.string.deficit
    else -> R.string.surplus
}
