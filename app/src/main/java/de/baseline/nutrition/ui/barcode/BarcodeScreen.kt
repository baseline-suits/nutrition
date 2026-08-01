package de.baseline.nutrition.ui.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import de.baseline.nutrition.R
import de.baseline.nutrition.data.product.ProductDto
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.ui.theme.BaselineCard
import de.baseline.nutrition.ui.theme.BaselinePrimaryButton
import de.baseline.nutrition.ui.theme.BaselineShapes
import de.baseline.nutrition.ui.theme.BaselineSpacing
import de.baseline.nutrition.ui.theme.BaselineStatus
import java.time.OffsetDateTime

@Composable
fun BarcodeScreen(
    viewModel: BarcodeViewModel,
    selectedDay: String,
    onDraftReady: (MealEditorDraft) -> Unit,
    onManual: () -> Unit,
    onDescription: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionRequested = true
    }
    fun requestPermission() {
        permissionRequested = true
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) requestPermission()
    }
    BackHandler(onBack = onClose)

    BarcodeContent(
        state = state,
        selectedDay = selectedDay,
        cameraPermissionGranted = permissionGranted,
        permissionRequested = permissionRequested,
        onRequestPermission = ::requestPermission,
        onBarcode = viewModel::barcodeDetected,
        onSearchQuery = viewModel::updateSearch,
        onSearch = viewModel::search,
        onSelectProduct = viewModel::selectProduct,
        onAmount = viewModel::updateAmount,
        onAmountUnit = viewModel::updateAmountUnit,
        onMealType = viewModel::updateMealType,
        onUseProduct = {
            viewModel.createDraft(selectedDay)?.let(onDraftReady)
        },
        onScanAgain = viewModel::resetScanner,
        onRetry = viewModel::retryBarcode,
        onManual = onManual,
        onDescription = onDescription,
        onClose = onClose,
        cameraPreview = { torchEnabled, scanEnabled ->
            BarcodeCamera(
                torchEnabled = torchEnabled,
                scanEnabled = scanEnabled,
                onBarcodeDetected = viewModel::barcodeDetected,
            )
        },
    )
}

@Composable
fun BarcodeContent(
    state: BarcodeUiState,
    selectedDay: String,
    cameraPermissionGranted: Boolean,
    permissionRequested: Boolean,
    onRequestPermission: () -> Unit,
    onBarcode: (String) -> Unit,
    onSearchQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectProduct: (ProductDto) -> Unit,
    onAmount: (String) -> Unit,
    onAmountUnit: (String) -> Unit,
    onMealType: (String) -> Unit,
    onUseProduct: () -> Unit,
    onScanAgain: () -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onDescription: () -> Unit,
    onClose: () -> Unit,
    cameraPreview: @Composable (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val haptics = LocalHapticFeedback.current
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var manualBarcode by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.product?.barcode) {
        if (state.product != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(state.errorCode) {
        if (state.errorCode != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background),
    ) {
        BarcodeHeader(
            torchEnabled = torchEnabled,
            torchAvailable = cameraPermissionGranted,
            onToggleTorch = { torchEnabled = !torchEnabled },
            onClose = onClose,
        )

        if (state.product == null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = BaselineSpacing.screen,
                        end = BaselineSpacing.screen,
                        bottom = BaselineSpacing.extraLarge,
                    ),
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
            ) {
                BarcodeHeading()
                if (cameraPermissionGranted) {
                    ScannerViewport(
                        torchEnabled = torchEnabled,
                        scanEnabled = true,
                        loading = state.loading,
                        cameraPreview = cameraPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    BaselineStatus(
                        text = stringResource(
                            if (state.loading) R.string.product_loading else R.string.barcode_ready,
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text = stringResource(R.string.barcode_scan_hint),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BaselineCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("barcode-permission-denied"),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact),
                        ) {
                            Text(
                                stringResource(
                                    if (permissionRequested) {
                                        R.string.barcode_permission_denied
                                    } else {
                                        R.string.barcode_permission_needed
                                    },
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onRequestPermission,
                                modifier = Modifier.testTag("barcode-request-permission"),
                            ) {
                                Text(stringResource(R.string.allow_camera))
                            }
                        }
                    }
                }
                if (state.loading) {
                    Row(
                        modifier = Modifier.testTag("barcode-loading"),
                        horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(stringResource(R.string.product_loading))
                    }
                }
                state.errorCode?.let { error ->
                    BarcodeError(
                        error = error,
                        canRetry = state.scannedBarcode != null,
                        onRetry = onRetry,
                        onManual = onManual,
                        onDescription = onDescription,
                    )
                }
                ManualBarcodeEntry(
                    value = manualBarcode,
                    loading = state.loading,
                    onValueChange = { manualBarcode = it.filter(Char::isDigit).take(14) },
                    onSubmit = { onBarcode(manualBarcode.trim()) },
                )
                ProductSearch(
                    state = state,
                    onQuery = onSearchQuery,
                    onSearch = onSearch,
                    onSelect = onSelectProduct,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = BaselineSpacing.screen,
                        end = BaselineSpacing.screen,
                        bottom = BaselineSpacing.small,
                    ),
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
            ) {
                BarcodeHeading()
                DetectedProductPanel(
                    state = state,
                    selectedDay = selectedDay,
                    torchEnabled = torchEnabled,
                    cameraPermissionGranted = cameraPermissionGranted,
                    cameraPreview = cameraPreview,
                    onAmount = onAmount,
                    onAmountUnit = onAmountUnit,
                    onMealType = onMealType,
                    onUse = onUseProduct,
                    onScanAgain = onScanAgain,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarcodeHeading() {
    Text(
        text = stringResource(R.string.barcode_title),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.barcode_subtitle),
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ScannerViewport(
    torchEnabled: Boolean,
    scanEnabled: Boolean,
    loading: Boolean,
    cameraPreview: @Composable (Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                clip = true
                shape = BaselineShapes.card
            }
            .clip(BaselineShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("barcode-camera"),
    ) {
        cameraPreview(torchEnabled, scanEnabled)
        Icon(
            imageVector = Icons.Rounded.CropFree,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.68f)
                .aspectRatio(1f),
            tint = MaterialTheme.colorScheme.primary,
        )
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DetectedProductPanel(
    state: BarcodeUiState,
    selectedDay: String,
    torchEnabled: Boolean,
    cameraPermissionGranted: Boolean,
    cameraPreview: @Composable (Boolean, Boolean) -> Unit,
    onAmount: (String) -> Unit,
    onAmountUnit: (String) -> Unit,
    onMealType: (String) -> Unit,
    onUse: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (cameraPermissionGranted) {
            ScannerViewport(
                torchEnabled = torchEnabled,
                scanEnabled = false,
                loading = false,
                cameraPreview = cameraPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .align(Alignment.TopCenter),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .align(Alignment.BottomCenter),
            shape = BaselineShapes.card,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(BaselineSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProductDetails(
                    state = state,
                    selectedDay = selectedDay,
                    onAmount = onAmount,
                    onAmountUnit = onAmountUnit,
                    onMealType = onMealType,
                    onUse = onUse,
                    onScanAgain = onScanAgain,
                )
            }
        }
    }
}

@Composable
private fun BarcodeHeader(
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = BaselineSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(start = BaselineSpacing.small),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        IconButton(
            onClick = onToggleTorch,
            enabled = torchAvailable,
            modifier = Modifier.testTag("barcode-torch"),
        ) {
            Icon(
                imageVector = if (torchEnabled) {
                    Icons.Rounded.FlashlightOff
                } else {
                    Icons.Rounded.FlashlightOn
                },
                contentDescription = stringResource(
                    if (torchEnabled) R.string.torch_off else R.string.torch_on,
                ),
            )
        }
    }
}

@Composable
private fun ManualBarcodeEntry(
    value: String,
    loading: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val canSubmit = value.length in 8..14 && !loading
    fun submit() {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit()
        }
    }
    BaselineCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact)) {
            Text(
                text = stringResource(R.string.barcode_manual_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.barcode_manual_query)) },
                singleLine = true,
                shape = BaselineShapes.input,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    IconButton(
                        onClick = ::submit,
                        enabled = canSubmit,
                        modifier = Modifier.testTag("barcode-manual-submit"),
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.barcode_manual_search),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("barcode-manual-query"),
            )
        }
    }
}

@Composable
private fun ProductSearch(
    state: BarcodeUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (ProductDto) -> Unit,
) {
    BaselineCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.compact)) {
            Text(
                text = stringResource(R.string.product_search_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.product_search_query)) },
                singleLine = true,
                shape = BaselineShapes.input,
                modifier = Modifier.fillMaxWidth().testTag("barcode-search-query"),
            )
            BaselinePrimaryButton(
                text = stringResource(R.string.search),
                enabled = state.searchQuery.trim().length >= 2 && !state.loading,
                onClick = onSearch,
                icon = Icons.Rounded.Search,
                modifier = Modifier.testTag("barcode-search"),
            )
            state.searchResults.forEach { product ->
                ProductSummary(
                    product = product,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("barcode-search-result-${product.barcode}")
                        .clickable { onSelect(product) },
                )
            }
        }
    }
}

@Composable
private fun ProductDetails(
    state: BarcodeUiState,
    selectedDay: String,
    onAmount: (String) -> Unit,
    onAmountUnit: (String) -> Unit,
    onMealType: (String) -> Unit,
    onUse: () -> Unit,
    onScanAgain: () -> Unit,
) {
    val product = requireNotNull(state.product)
    ProductSummary(
        product = product,
        modifier = Modifier.fillMaxWidth().testTag("barcode-product"),
    )
    Text(
        "${stringResource(R.string.source)}: ${stringResource(R.string.source_off)} · " +
            stringResource(R.string.product_imported_at, product.fetchedAt.displayDate()),
    )
    if (product.missingCore.isNotEmpty()) {
        val missingLabels = mutableListOf<String>()
        for (key in product.missingCore) {
            missingLabels += stringResource(nutrientLabelResource(key))
        }
        Text(
            stringResource(
                R.string.product_missing_values,
                missingLabels.joinToString(", "),
            ),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("barcode-product-incomplete"),
        )
    }
    val preview = runCatching {
        product.toMealEditor(
            state.amount,
            state.amountUnit,
            selectedDay,
            state.mealType,
        ).totals()
    }.getOrNull()
    preview?.let { totals ->
        Text(
            stringResource(
                R.string.product_scaled_values,
                totals["energy"]?.stripTrailingZeros()?.toPlainString() ?: "–",
                totals["protein"]?.stripTrailingZeros()?.toPlainString() ?: "–",
                totals["carbohydrates"]?.stripTrailingZeros()?.toPlainString() ?: "–",
                totals["fat"]?.stripTrailingZeros()?.toPlainString() ?: "–",
            ),
        )
    }
    state.errorCode?.let {
        Text(
            stringResource(barcodeErrorLabel(it)),
            color = MaterialTheme.colorScheme.error,
        )
    }
    BaselinePrimaryButton(
        text = stringResource(R.string.use_product),
        enabled = preview != null,
        onClick = onUse,
        icon = Icons.Rounded.Add,
        modifier = Modifier.testTag("barcode-use-product"),
    )
    Text(stringResource(R.string.amount), fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = state.amount,
        onValueChange = onAmount,
        label = { Text(stringResource(R.string.amount)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("barcode-amount"),
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
    ) {
        val referenceUnit = product.referenceUnit()
        FilterChip(
            selected = state.amountUnit == referenceUnit,
            onClick = { onAmountUnit(referenceUnit) },
            label = { Text(referenceUnit) },
        )
        if (product.servingQuantity != null && product.servingUnit == referenceUnit) {
            FilterChip(
                selected = state.amountUnit == "portion",
                onClick = { onAmountUnit("portion") },
                label = {
                    Text(
                        stringResource(
                            R.string.product_portion,
                            product.servingQuantity,
                            product.servingUnit,
                        ),
                    )
                },
            )
        }
    }
    BarcodeMealTypeRow(state.mealType, onMealType)
    TextButton(onClick = onScanAgain) { Text(stringResource(R.string.scan_another_product)) }
}

@Composable
private fun ProductSummary(product: ProductDto, modifier: Modifier = Modifier) {
    BaselineCard(modifier = modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            product.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.product_image, product.name),
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.tiny),
            ) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                product.brand?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                product.quantity?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    product.barcode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun BarcodeError(
    error: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onDescription: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("barcode-error-$error"),
    ) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(
                stringResource(barcodeErrorLabel(error)),
                color = MaterialTheme.colorScheme.error,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
            ) {
                if (canRetry) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                OutlinedButton(onClick = onManual) { Text(stringResource(R.string.manual_entry)) }
                OutlinedButton(onClick = onDescription) {
                    Text(stringResource(R.string.describe_food))
                }
            }
        }
    }
}

@Composable
private fun BarcodeMealTypeRow(selected: String, onSelected: (String) -> Unit) {
    Text(stringResource(R.string.meal_type), fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
    ) {
        listOf("breakfast", "lunch", "dinner", "snack").forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(stringResource(mealTypeLabel(type))) },
            )
        }
    }
}

@OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
private fun BarcodeCamera(
    torchEnabled: Boolean,
    scanEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val currentScanEnabled = rememberUpdatedState(scanEnabled)
    val currentOnBarcodeDetected = rememberUpdatedState(onBarcodeDetected)
    var camera by remember { mutableStateOf<Camera?>(null) }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
    LaunchedEffect(camera, torchEnabled) {
        camera?.cameraControl?.enableTorch(torchEnabled)
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                )
                .build(),
        )
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    if (!currentScanEnabled.value) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    scanner.process(
                        InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        ),
                    ).addOnSuccessListener { barcodes ->
                        barcodes.firstNotNullOfOrNull { barcode ->
                            barcode.rawValue?.takeIf(String::isNotBlank)
                        }?.let(currentOnBarcodeDetected.value)
                    }.addOnCompleteListener {
                        imageProxy.close()
                    }
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            },
            executor,
        )
        onDispose {
            camera = null
            scanner.close()
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }
}

private fun nutrientLabelResource(key: String): Int =
    when (key) {
        "energy" -> R.string.energy
        "protein" -> R.string.protein
        "carbohydrates" -> R.string.carbohydrates
        else -> R.string.fat
    }

private fun barcodeErrorLabel(code: String): Int = when (code) {
    "product_not_found" -> R.string.product_not_found
    "product_incomplete" -> R.string.product_incomplete
    "invalid_barcode" -> R.string.barcode_invalid
    "off_rate_limit" -> R.string.product_rate_limit
    "off_not_configured", "off_unavailable", "network_error" -> R.string.product_offline
    "amount_invalid" -> R.string.product_amount_invalid
    else -> R.string.product_offline
}

private fun mealTypeLabel(type: String): Int = when (type) {
    "breakfast" -> R.string.breakfast
    "lunch" -> R.string.lunch
    "dinner" -> R.string.dinner
    else -> R.string.snacks
}

private fun String.displayDate(): String = runCatching {
    OffsetDateTime.parse(this).toLocalDate().toString()
}.getOrDefault(this)
