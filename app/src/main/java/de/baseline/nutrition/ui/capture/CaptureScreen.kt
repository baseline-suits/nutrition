package de.baseline.nutrition.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.baseline.nutrition.R
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.ui.theme.BaselineSpacing
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    selectedDay: String,
    onDraftReady: (MealEditorDraft) -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = Locale.current.language.takeIf { it == "ru" } ?: "de"
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        val path = cameraPath
        if (it && path != null) {
            runCatching { normalizeImage(context, Uri.fromFile(File(path))) }
                .onSuccess(viewModel::setPhoto)
                .onFailure {
                    File(path).delete()
                    localError = "photo_invalid"
                }
        } else {
            path?.let { file -> File(file).delete() }
        }
        cameraPath = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching { normalizeImage(context, uri) }
                .onSuccess(viewModel::setPhoto)
                .onFailure { localError = "photo_invalid" }
        }
    }

    fun launchCamera() {
        val file = File.createTempFile("baseline-camera-", ".jpg", context.cacheDir)
        cameraPath = file.absolutePath
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(state.mode) {
        if (state.mode == CaptureMode.Camera && state.photoPath == null &&
            state.attachmentId == null
        ) {
            launchCamera()
        } else if (state.mode == CaptureMode.Import && state.photoPath == null &&
            state.attachmentId == null
        ) {
            importLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    LaunchedEffect(state.draft?.id) {
        state.draft?.let { draft ->
            val method = when (state.mode) {
                CaptureMode.Description -> "description"
                CaptureMode.Camera -> "camera"
                CaptureMode.Import -> "gallery"
                CaptureMode.Menu -> "manual"
            }
            onDraftReady(draft.toMealEditor(selectedDay, state.mealType, method))
            viewModel.consumeDraft()
        }
    }

    fun requestClose() {
        if (state.text.isNotBlank() || state.photoPath != null || state.attachmentId != null) {
            showDiscard = true
        } else if (state.mode == CaptureMode.Menu) {
            onClose()
        } else {
            viewModel.selectMode(CaptureMode.Menu)
        }
    }
    BackHandler(onBack = ::requestClose)

    when (state.mode) {
        CaptureMode.Menu -> QuickAddMenu(
            onMode = viewModel::selectMode,
            onBarcode = onBarcode,
            onManual = onManual,
            onBack = ::requestClose,
        )
        CaptureMode.Description -> DescriptionFlow(
            state = state,
            onText = viewModel::updateText,
            onMealType = viewModel::updateMealType,
            onAnalyze = { viewModel.analyze(locale) },
            onCancel = viewModel::cancel,
            onBack = ::requestClose,
        )
        CaptureMode.Camera, CaptureMode.Import -> PhotoFlow(
            state = state,
            localError = localError,
            onText = viewModel::updateText,
            onMealType = viewModel::updateMealType,
            onChooseAgain = {
                localError = null
                if (state.mode == CaptureMode.Camera) launchCamera()
                else importLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onAnalyze = { viewModel.analyze(locale) },
            onCancel = viewModel::cancel,
            onBack = ::requestClose,
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.discard_capture_title)) },
            text = { Text(stringResource(R.string.discard_capture_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    viewModel.clear()
                    onClose()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.continue_editing))
                }
            },
        )
    }
}

@Composable
fun QuickAddMenu(
    onMode: (CaptureMode) -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    CaptureColumn {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.add_meal_title), fontWeight = FontWeight.Bold)
        CaptureChoice(
            R.string.describe_food,
            R.string.describe_food_hint,
            "capture-description",
        ) { onMode(CaptureMode.Description) }
        CaptureChoice(
            R.string.take_food_photo,
            R.string.take_food_photo_hint,
            "capture-camera",
        ) { onMode(CaptureMode.Camera) }
        CaptureChoice(
            R.string.import_food_photo,
            R.string.import_food_photo_hint,
            "capture-import",
        ) { onMode(CaptureMode.Import) }
        CaptureChoice(
            R.string.scan_barcode,
            R.string.scan_barcode_hint,
            "capture-barcode",
            onBarcode,
        )
        CaptureChoice(
            R.string.manual_entry,
            R.string.manual_entry_hint,
            "capture-manual",
            onManual,
        )
    }
}

@Composable
private fun DescriptionFlow(
    state: CaptureUiState,
    onText: (String) -> Unit,
    onMealType: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    CaptureColumn {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.describe_food), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.describe_food_example))
        MealTypeRow(state.mealType, onMealType)
        OutlinedTextField(
            value = state.text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.food_description)) },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        CaptureStatus(state, onCancel)
        Button(
            enabled = state.stage !in setOf(CaptureStage.Uploading, CaptureStage.Analyzing),
            onClick = onAnalyze,
        ) { Text(stringResource(R.string.analyze_meal)) }
    }
}

@Composable
private fun PhotoFlow(
    state: CaptureUiState,
    localError: String?,
    onText: (String) -> Unit,
    onMealType: (String) -> Unit,
    onChooseAgain: () -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    CaptureColumn {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(
                if (state.mode == CaptureMode.Camera) {
                    R.string.take_food_photo
                } else {
                    R.string.import_food_photo
                },
            ),
            fontWeight = FontWeight.Bold,
        )
        MealTypeRow(state.mealType, onMealType)
        OutlinedTextField(
            value = state.text,
            onValueChange = onText,
            label = { Text(stringResource(R.string.photo_description_optional)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        state.photoPath?.let { path ->
            remember(path) {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.selected_food_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
            }
        }
        if (state.attachmentId != null && state.photoPath == null) {
            Text(stringResource(R.string.photo_prepared))
        }
        OutlinedButton(
            enabled = state.stage !in setOf(CaptureStage.Uploading, CaptureStage.Analyzing),
            onClick = onChooseAgain,
        ) {
            Text(
                stringResource(
                    if (state.mode == CaptureMode.Camera) {
                        R.string.retake_photo
                    } else {
                        R.string.replace_photo
                    },
                ),
            )
        }
        localError?.let { Text(stringResource(captureErrorLabel(it))) }
        CaptureStatus(state, onCancel)
        Button(
            enabled = (state.photoPath != null || state.attachmentId != null) &&
                state.stage !in setOf(CaptureStage.Uploading, CaptureStage.Analyzing),
            onClick = onAnalyze,
        ) { Text(stringResource(R.string.analyze_meal)) }
    }
}

@Composable
private fun CaptureStatus(state: CaptureUiState, onCancel: () -> Unit) {
    when (state.stage) {
        CaptureStage.Uploading -> {
            Text(stringResource(R.string.uploading_photo, state.progress))
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        CaptureStage.Analyzing -> {
            Text(stringResource(R.string.analyzing_meal))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        CaptureStage.Error -> state.errorCode?.let {
            Text(stringResource(captureErrorLabel(it)))
        }
        else -> Unit
    }
}

@Composable
private fun MealTypeRow(selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
        listOf("breakfast", "lunch", "dinner", "snack").forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(stringResource(mealTypeLabel(type))) },
            )
        }
    }
}

@Composable
private fun CaptureChoice(
    title: Int,
    description: Int,
    tag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(BaselineSpacing.medium)) {
            Text(stringResource(title), fontWeight = FontWeight.Bold)
            Text(stringResource(description))
        }
    }
}

@Composable
private fun CaptureColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) { content() }
}

private fun normalizeImage(context: Context, uri: Uri): String {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
                decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 25_000_000L)
            val factor = max(info.size.width, info.size.height) / 2048.0
            if (factor > 1) {
                decoder.setTargetSize(
                    ceil(info.size.width / factor).toInt(),
                    ceil(info.size.height / factor).toInt(),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }
        require(options.outWidth.toLong() * options.outHeight in 1..25_000_000L)
        val sample = generateSequence(1) { it * 2 }
            .first { max(options.outWidth, options.outHeight) / it <= 2048 }
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
        requireNotNull(decoded)
    }
    val target = File.createTempFile("baseline-photo-", ".jpg", context.cacheDir)
    FileOutputStream(target).use { output ->
        require(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
    }
    bitmap.recycle()
    return target.absolutePath
}

private fun captureErrorLabel(code: String): Int = when (code) {
    "input_too_short" -> R.string.capture_error_short
    "photo_required", "photo_invalid", "invalid_image" -> R.string.capture_error_photo
    "provider_timeout" -> R.string.capture_error_timeout
    "invalid_model_schema" -> R.string.capture_error_schema
    "invalid_session", "authentication_required" -> R.string.capture_error_session
    else -> R.string.capture_error_network
}

private fun mealTypeLabel(type: String): Int = when (type) {
    "breakfast" -> R.string.breakfast
    "lunch" -> R.string.lunch
    "dinner" -> R.string.dinner
    else -> R.string.snacks
}
