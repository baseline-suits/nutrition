package de.baseline.nutrition.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.baseline.nutrition.data.capture.AnalysisDraftDto
import de.baseline.nutrition.data.capture.CaptureRepository
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.domain.diary.IngredientDraft
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.NutrientFields
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CaptureMode { Menu, Description, Camera, Import }
enum class CaptureStage { Input, Prepared, Uploading, Analyzing, Error }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.Menu,
    val stage: CaptureStage = CaptureStage.Input,
    val text: String = "",
    val photoPath: String? = null,
    val attachmentId: String? = null,
    val mealType: String = "snack",
    val progress: Int = 0,
    val draft: AnalysisDraftDto? = null,
    val errorCode: String? = null,
)

class CaptureViewModel(
    private val repository: CaptureRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        CaptureUiState(
            mode = savedState.get<String>("mode")?.let(CaptureMode::valueOf) ?: CaptureMode.Menu,
            text = savedState["text"] ?: "",
            photoPath = savedState["photoPath"],
            attachmentId = savedState["attachmentId"],
            mealType = savedState["mealType"] ?: "snack",
            stage = if (
                savedState.get<String>("photoPath") != null ||
                savedState.get<String>("attachmentId") != null
            ) {
                CaptureStage.Prepared
            } else {
                CaptureStage.Input
            },
        ),
    )
    val state: StateFlow<CaptureUiState> = mutableState.asStateFlow()
    private var activeJob: Job? = null

    private var uploadKey: String
        get() = savedState["uploadKey"] ?: UUID.randomUUID().toString().also {
            savedState["uploadKey"] = it
        }
        set(value) {
            savedState["uploadKey"] = value
        }
    private var analysisKey: String
        get() = savedState["analysisKey"] ?: UUID.randomUUID().toString().also {
            savedState["analysisKey"] = it
        }
        set(value) {
            savedState["analysisKey"] = value
        }

    fun selectMode(mode: CaptureMode) {
        mutableState.update {
            it.copy(
                mode = mode,
                stage = if (it.photoPath != null) CaptureStage.Prepared else CaptureStage.Input,
                errorCode = null,
            )
        }
        savedState["mode"] = mode.name
    }

    fun updateText(value: String) {
        mutableState.update { it.copy(text = value, errorCode = null) }
        savedState["text"] = value
    }

    fun updateMealType(value: String) {
        mutableState.update { it.copy(mealType = value) }
        savedState["mealType"] = value
    }

    fun setPhoto(path: String) {
        mutableState.value.photoPath?.takeIf { it != path }?.let { File(it).delete() }
        mutableState.update {
            it.copy(
                photoPath = path,
                attachmentId = null,
                stage = CaptureStage.Prepared,
                progress = 0,
                errorCode = null,
            )
        }
        savedState["photoPath"] = path
        savedState["attachmentId"] = null
        uploadKey = UUID.randomUUID().toString()
        analysisKey = UUID.randomUUID().toString()
    }

    fun analyze(locale: String) {
        val current = mutableState.value
        if (current.stage in setOf(CaptureStage.Uploading, CaptureStage.Analyzing)) return
        when (current.mode) {
            CaptureMode.Description -> analyzeText(current, locale)
            CaptureMode.Camera, CaptureMode.Import -> analyzePhoto(current, locale)
            CaptureMode.Menu -> Unit
        }
    }

    private fun analyzeText(current: CaptureUiState, locale: String) {
        if (current.text.trim().length < 3) {
            mutableState.update { it.copy(stage = CaptureStage.Error, errorCode = "input_too_short") }
            return
        }
        mutableState.update { it.copy(stage = CaptureStage.Analyzing, errorCode = null) }
        activeJob = viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    repository.analyzeText(
                        current.text.trim(),
                        locale,
                        current.mealType,
                        analysisKey,
                    )
                }
            }.onSuccess { draft ->
                mutableState.update { it.copy(draft = draft, stage = CaptureStage.Prepared) }
            }.onFailure(::handleFailure)
        }
    }

    private fun analyzePhoto(current: CaptureUiState, locale: String) {
        if (current.photoPath == null && current.attachmentId == null) {
            mutableState.update { it.copy(stage = CaptureStage.Error, errorCode = "photo_required") }
            return
        }
        activeJob = viewModelScope.launch {
            runCatching {
                var attachmentId = current.attachmentId
                if (attachmentId == null) {
                    mutableState.update {
                        it.copy(stage = CaptureStage.Uploading, progress = 0, errorCode = null)
                    }
                    val bytes = withContext(ioDispatcher) {
                        File(requireNotNull(current.photoPath)).readBytes()
                    }
                    val upload = withContext(ioDispatcher) {
                        repository.uploadPhoto(
                            bytes = bytes,
                            onProgress = { progress ->
                                mutableState.update { state -> state.copy(progress = progress) }
                            },
                            idempotencyKey = uploadKey,
                        )
                    }
                    attachmentId = upload.id
                    savedState["attachmentId"] = attachmentId
                    current.photoPath?.let { File(it).delete() }
                    savedState["photoPath"] = null
                    mutableState.update {
                        it.copy(photoPath = null, attachmentId = attachmentId)
                    }
                }
                mutableState.update { it.copy(stage = CaptureStage.Analyzing, errorCode = null) }
                withContext(ioDispatcher) {
                    repository.analyzePhoto(
                        requireNotNull(attachmentId),
                        current.text,
                        locale,
                        current.mealType,
                        analysisKey,
                    )
                }
            }.onSuccess { draft ->
                mutableState.update { it.copy(draft = draft, stage = CaptureStage.Prepared) }
            }.onFailure(::handleFailure)
        }
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) {
            mutableState.update { it.copy(stage = CaptureStage.Prepared, errorCode = null) }
            return
        }
        val code = (error as? ApiException)?.code ?: "network_error"
        if (code == "analysis_failed" || code == "invalid_model_schema") {
            analysisKey = UUID.randomUUID().toString()
        }
        mutableState.update { it.copy(stage = CaptureStage.Error, errorCode = code) }
    }

    fun consumeDraft() {
        mutableState.update { it.copy(draft = null) }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        mutableState.update { it.copy(stage = CaptureStage.Prepared, errorCode = null) }
    }

    fun complete() {
        activeJob?.cancel()
        mutableState.value.photoPath?.let { File(it).delete() }
        savedState.keys().toList().forEach { savedState.remove<Any?>(it) }
        mutableState.value = CaptureUiState()
    }

    fun clear() {
        activeJob?.cancel()
        val current = mutableState.value
        current.photoPath?.let { File(it).delete() }
        current.attachmentId?.let { attachmentId ->
            viewModelScope.launch {
                runCatching {
                    withContext(ioDispatcher) { repository.deletePhoto(attachmentId) }
                }
            }
        }
        savedState.keys().toList().forEach { savedState.remove<Any?>(it) }
        uploadKey = UUID.randomUUID().toString()
        analysisKey = UUID.randomUUID().toString()
        mutableState.value = CaptureUiState()
    }

    override fun onCleared() {
        mutableState.value.photoPath?.let { File(it).delete() }
        super.onCleared()
    }

    companion object {
        fun factory(
            repository: CaptureRepository,
            ioDispatcher: CoroutineDispatcher,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T = CaptureViewModel(
                repository,
                ioDispatcher,
                extras.createSavedStateHandle(),
            ) as T
        }
    }
}

fun AnalysisDraftDto.toMealEditor(
    day: String,
    mealType: String,
    captureMethod: String,
): MealEditorDraft = MealEditorDraft(
    name = meal.name,
    mealType = mealType,
    day = day,
    nutrients = meal.nutrients.toFields(),
    ingredients = meal.ingredients.map { ingredient ->
        IngredientDraft(
            name = ingredient.name,
            preparation = ingredient.preparation.orEmpty(),
            amount = ingredient.amount,
            unit = ingredient.unit,
            nutrients = ingredient.nutrients.toFields(),
        )
    },
    captureMethod = captureMethod,
    provenanceSource = "ai_estimate",
    attachmentId = attachmentId,
    analysisWarnings = meal.warnings,
    dirty = false,
)

private fun List<NutrientDto>.toFields(): NutrientFields {
    val values = associate { it.key to it.value }
    return NutrientFields(
        energy = values["energy"].orEmpty(),
        protein = values["protein"].orEmpty(),
        carbohydrates = values["carbohydrates"].orEmpty(),
        fat = values["fat"].orEmpty(),
        additional = filterNot {
            it.key in setOf("energy", "protein", "carbohydrates", "fat")
        },
        sources = associate { it.key to it.source },
        locks = associate { it.key to it.locked },
        originalValues = values,
    )
}
