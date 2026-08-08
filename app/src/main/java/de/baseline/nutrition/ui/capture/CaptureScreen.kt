package de.baseline.nutrition.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.baseline.nutrition.R
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselinePrimaryButton
import de.baseline.nutrition.ui.theme.BaselineShapes
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
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    onManual: () -> Unit,
    onMenu: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val locale = Locale.current.language.takeIf { it == "ru" } ?: "de"
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        val path = cameraPath
        if (it && path != null) {
            runCatching { normalizeImage(context, Uri.fromFile(File(path))) }
                .onSuccess {
                    localError = null
                    viewModel.setPhoto(it)
                }
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
                .onSuccess {
                    localError = null
                    viewModel.setPhoto(it)
                }
                .onFailure { localError = "photo_invalid" }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionRequested = false
        cameraPermissionGranted = granted
        if (!granted) {
            localError = "camera_permission_denied"
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionGranted = false
            if (!cameraPermissionRequested) {
                cameraPermissionRequested = true
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            return
        }
        cameraPermissionGranted = true
        runCatching {
            val file = File.createTempFile("baseline-camera-", ".jpg", context.cacheDir)
            cameraPath = file.absolutePath
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            cameraLauncher.launch(uri)
        }.onFailure { error ->
            cameraPath?.let { path -> File(path).delete() }
            cameraPath = null
            localError = if (error is SecurityException) {
                "camera_permission_denied"
            } else {
                "camera_unavailable"
            }
        }
    }

    LaunchedEffect(state.mode, cameraPermissionGranted) {
        if (state.mode == CaptureMode.Camera && state.photoPath == null &&
            state.attachmentId == null && cameraPath == null
        ) {
            if (cameraPermissionGranted) {
                launchCamera()
            } else if (!cameraPermissionRequested) {
                launchCamera()
            }
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
            onMenu()
        }
    }
    BackHandler(onBack = ::requestClose)

    when (state.mode) {
        CaptureMode.Menu -> QuickAddMenu(
            onMode = viewModel::selectMode,
            onBarcode = onBarcode,
            onFavorites = onFavorites,
            onRecent = onRecent,
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
            onText = { viewModel.updateText(it.take(250)) },
            onMealType = viewModel::updateMealType,
            onCamera = {
                localError = null
                if (state.mode == CaptureMode.Camera) launchCamera()
                else viewModel.selectMode(CaptureMode.Camera)
            },
            onImport = {
                localError = null
                if (state.mode == CaptureMode.Import) {
                    importLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                } else {
                    viewModel.selectMode(CaptureMode.Import)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddMenu(
    onMode: (CaptureMode) -> Unit,
    onBarcode: () -> Unit,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BaselineSpacing.screen)
                .padding(bottom = BaselineSpacing.large),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.quick_add_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            CaptureChoice(
                R.string.describe_food,
                R.string.describe_food_hint,
                "capture-description",
                Icons.AutoMirrored.Rounded.Chat,
            ) { onMode(CaptureMode.Description) }
            CaptureChoice(
                R.string.take_food_photo,
                R.string.take_food_photo_hint,
                "capture-camera",
                Icons.Rounded.PhotoCamera,
            ) { onMode(CaptureMode.Camera) }
            CaptureChoice(
                R.string.import_food_photo,
                R.string.import_food_photo_hint,
                "capture-import",
                Icons.Rounded.Image,
            ) { onMode(CaptureMode.Import) }
            CaptureChoice(
                R.string.scan_barcode,
                R.string.scan_barcode_hint,
                "capture-barcode",
                Icons.Rounded.QrCodeScanner,
                onBarcode,
            )
            CaptureChoice(
                R.string.manual_entry,
                R.string.manual_entry_hint,
                "capture-manual",
                Icons.Rounded.Edit,
                onManual,
            )
            Spacer(Modifier.height(BaselineSpacing.large))
            CaptureChoice(
                R.string.favorites,
                R.string.favorites_hint,
                "capture-favorites",
                Icons.Rounded.Favorite,
                onFavorites,
            )
            CaptureChoice(
                R.string.recent_meals,
                R.string.recent_meals_hint,
                "capture-recent",
                Icons.Rounded.History,
                onRecent,
            )
        }
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
internal fun PhotoFlow(
    state: CaptureUiState,
    localError: String?,
    onText: (String) -> Unit,
    onMealType: (String) -> Unit,
    onCamera: () -> Unit,
    onImport: () -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val busy = state.stage in setOf(CaptureStage.Uploading, CaptureStage.Analyzing)
    val hasPhoto = state.photoPath != null || state.attachmentId != null
    CaptureColumn {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.meal_analysis_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.meal_analysis_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        val preview = state.photoPath?.let { path ->
            remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = stringResource(R.string.selected_food_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(BaselineShapes.card),
            )
        } else {
            Box(
                contentAlignment = androidx.compose.ui.Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(BaselineShapes.card)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(BaselineSpacing.small))
                    Text(stringResource(R.string.capture_ready_description))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(
                enabled = !busy,
                onClick = onCamera,
                contentPadding = PaddingValues(horizontal = BaselineSpacing.compact),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("capture-photo-camera"),
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(BaselineSpacing.small))
                Text(stringResource(R.string.capture_take_photo_action), maxLines = 1)
            }
            OutlinedButton(
                enabled = !busy,
                onClick = onImport,
                contentPadding = PaddingValues(horizontal = BaselineSpacing.compact),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("capture-photo-import"),
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null)
                Spacer(Modifier.width(BaselineSpacing.small))
                Text(stringResource(R.string.pick_food_photo), maxLines = 1)
            }
        }
        BaselineCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Text(
                    stringResource(R.string.photo_description_optional),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onText,
                    placeholder = { Text(stringResource(R.string.photo_description_placeholder)) },
                    minLines = 3,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.character_count, state.text.length),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                )
            }
        }
        BaselinePrimaryButton(
            text = stringResource(R.string.start_analysis),
            enabled = hasPhoto && !busy,
            onClick = onAnalyze,
            icon = Icons.Rounded.AutoAwesome,
            modifier = Modifier.testTag("capture-analyze-photo"),
        )
        BaselineCard(Modifier.fillMaxWidth().testTag("capture-analysis-status")) {
            Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Text(stringResource(R.string.analysis_status_title), style = MaterialTheme.typography.titleMedium)
                if (state.stage in setOf(CaptureStage.Uploading, CaptureStage.Analyzing, CaptureStage.Error)) {
                    CaptureStatus(state, onCancel)
                } else {
                    Text(
                        stringResource(
                            if (hasPhoto) R.string.capture_photo_ready_title else R.string.capture_ready_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (hasPhoto) R.string.capture_photo_ready_description else R.string.capture_ready_description,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                localError?.let { Text(stringResource(captureErrorLabel(it))) }
            }
        }
        Text(stringResource(R.string.meal_type), style = MaterialTheme.typography.labelLarge)
        MealTypeRow(state.mealType, onMealType)
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
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = de.baseline.nutrition.ui.theme.BaselineShapes.input,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag(tag)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(BaselineSpacing.small),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = androidx.compose.ui.Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(BaselineSpacing.small))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        val sampleSize = generateSequence(1) { it * 2 }
            .first { max(options.outWidth, options.outHeight) / it <= 2048 }
        val decoded = context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
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
    "camera_permission_denied" -> R.string.capture_error_camera_permission
    "camera_unavailable" -> R.string.capture_error_camera_unavailable
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
