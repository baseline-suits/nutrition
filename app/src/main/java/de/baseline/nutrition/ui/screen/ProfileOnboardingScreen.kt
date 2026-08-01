package de.baseline.nutrition.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import de.baseline.nutrition.R
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.profile.ProfileRepository
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.data.profile.OnboardingDraft
import de.baseline.nutrition.data.profile.OnboardingDraftStore
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.onboarding.GoalCalculator
import de.baseline.nutrition.ui.LocaleController
import de.baseline.nutrition.ui.diary.AccountDeletionDialog
import de.baseline.nutrition.ui.diary.AccountDeletionFinishedDialog
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileOnboardingScreen(
    repository: ProfileRepository,
    draftStore: OnboardingDraftStore,
    authRepository: AuthRepository,
    ioDispatcher: CoroutineDispatcher,
    onComplete: () -> Unit,
) {
    val restored = remember { draftStore.load() }
    var locale by rememberSaveable { mutableStateOf(restored.locale) }
    var manual by rememberSaveable { mutableStateOf(restored.manual) }
    var birthDate by rememberSaveable { mutableStateOf(restored.birthDate) }
    var height by rememberSaveable { mutableStateOf(restored.height) }
    var weight by rememberSaveable { mutableStateOf(restored.weight) }
    var biologicalInput by rememberSaveable { mutableStateOf(restored.biologicalInput) }
    var activity by rememberSaveable { mutableStateOf(restored.activity) }
    var direction by rememberSaveable { mutableStateOf(restored.direction) }
    var kcal by rememberSaveable { mutableStateOf(restored.kcal) }
    var protein by rememberSaveable { mutableStateOf(restored.protein) }
    var carbs by rememberSaveable { mutableStateOf(restored.carbs) }
    var fat by rememberSaveable { mutableStateOf(restored.fat) }
    var error by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var showAccountDeletion by rememberSaveable { mutableStateOf(false) }
    var accountPassword by remember { mutableStateOf("") }
    var accountConfirmed by remember { mutableStateOf(false) }
    var accountDeleting by remember { mutableStateOf(false) }
    var accountDeletionError by remember { mutableStateOf<String?>(null) }
    var accountDeletionStatus by remember { mutableStateOf<AccountDeletionStatus?>(null) }
    val scope = rememberCoroutineScope()
    val reauthenticationError = stringResource(R.string.account_delete_password_error)
    val accountDeletionGenericError = stringResource(R.string.account_delete_failed)

    fun currentDraft() = OnboardingDraft(
        locale = locale,
        manual = manual,
        birthDate = birthDate,
        height = height,
        weight = weight,
        biologicalInput = biologicalInput,
        activity = activity,
        direction = direction,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
    )

    LaunchedEffect(Unit) {
        LocaleController.apply(locale)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Text(stringResource(R.string.onboarding_title))
        Choice(R.string.language, locale, listOf("de", "ru")) {
            locale = it
            draftStore.save(currentDraft().copy(locale = it))
            LocaleController.apply(it)
        }
        Choice(R.string.goal_method, if (manual) "manual" else "calculate", listOf("calculate", "manual")) {
            manual = it == "manual"
            draftStore.save(currentDraft().copy(manual = it == "manual"))
        }
        if (!manual) {
            Field(R.string.birth_date, birthDate) {
                birthDate = it
                draftStore.save(currentDraft().copy(birthDate = it))
            }
            Field(R.string.height_cm, height) {
                height = it
                draftStore.save(currentDraft().copy(height = it))
            }
            Field(R.string.weight_kg, weight) {
                weight = it
                draftStore.save(currentDraft().copy(weight = it))
            }
            Choice(R.string.biological_input, biologicalInput, listOf("female", "male")) {
                biologicalInput = it
                draftStore.save(currentDraft().copy(biologicalInput = it))
            }
            Choice(R.string.activity_level, activity, listOf("inactive", "sometimes", "active", "very_active")) {
                activity = it
                draftStore.save(currentDraft().copy(activity = it))
            }
            Choice(R.string.goal_direction, direction, listOf("maintain", "deficit", "surplus")) {
                direction = it
                draftStore.save(currentDraft().copy(direction = it))
            }
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
                    draftStore.save(
                        currentDraft().copy(
                            kcal = it.targetKcal.toString(),
                            protein = it.proteinGrams.toString(),
                            carbs = it.carbsGrams.toString(),
                            fat = it.fatGrams.toString(),
                        ),
                    )
                    error = false
                }.onFailure { error = true }
            }) { Text(stringResource(R.string.calculate_targets)) }
        }
        Field(R.string.target_kcal, kcal) {
            kcal = it
            draftStore.save(currentDraft().copy(kcal = it))
        }
        Field(R.string.target_protein, protein) {
            protein = it
            draftStore.save(currentDraft().copy(protein = it))
        }
        Field(R.string.target_carbs, carbs) {
            carbs = it
            draftStore.save(currentDraft().copy(carbs = it))
        }
        Field(R.string.target_fat, fat) {
            fat = it
            draftStore.save(currentDraft().copy(fat = it))
        }
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
                    .onSuccess {
                        draftStore.clear()
                        onComplete()
                    }
                    .onFailure { error = true }
                saving = false
            }
        }) { Text(stringResource(R.string.finish_onboarding)) }
        Text(stringResource(R.string.account_and_privacy))
        Text(stringResource(R.string.account_delete_summary))
        OutlinedButton(
            onClick = {
                accountPassword = ""
                accountConfirmed = false
                accountDeletionError = null
                showAccountDeletion = true
            },
            modifier = Modifier.testTag("onboarding-delete-account"),
        ) {
            Text(stringResource(R.string.delete_account))
        }
    }
    if (showAccountDeletion) {
        AccountDeletionDialog(
            password = accountPassword,
            confirmed = accountConfirmed,
            busy = accountDeleting,
            error = accountDeletionError,
            onPassword = {
                accountPassword = it
                accountDeletionError = null
            },
            onConfirmed = { accountConfirmed = it },
            onConfirm = {
                accountDeleting = true
                accountDeletionError = null
                scope.launch {
                    runCatching {
                        withContext(ioDispatcher) {
                            authRepository.deleteAccount(accountPassword)
                        }
                    }.onSuccess {
                        draftStore.clear()
                        accountDeletionStatus = it
                        accountPassword = ""
                        accountConfirmed = false
                        showAccountDeletion = false
                    }.onFailure {
                        accountDeletionError =
                            if (it is ApiException && it.status == 403) {
                                reauthenticationError
                            } else {
                                accountDeletionGenericError
                            }
                    }
                    accountDeleting = false
                }
            },
            onDismiss = {
                accountPassword = ""
                accountConfirmed = false
                showAccountDeletion = false
            },
        )
    }
    accountDeletionStatus?.let { status ->
        AccountDeletionFinishedDialog(status, onComplete)
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
