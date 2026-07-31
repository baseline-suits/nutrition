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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import de.baseline.nutrition.ui.theme.BaselineSpacing
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
        cameraPermissionGranted = permissionGranted,
        permissionRequested = permissionRequested,
        onRequestPermission = ::requestPermission,
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
        cameraPreview = { torchEnabled ->
            BarcodeCamera(
                torchEnabled = torchEnabled,
                onBarcodeDetected = viewModel::barcodeDetected,
            )
        },
    )
}

@Composable
fun BarcodeContent(
    state: BarcodeUiState,
    cameraPermissionGranted: Boolean,
    permissionRequested: Boolean,
    onRequestPermission: () -> Unit,
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
    cameraPreview: @Composable (Boolean) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
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
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.barcode_title), fontWeight = FontWeight.Bold)

        if (state.product == null) {
            if (cameraPermissionGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .testTag("barcode-camera"),
                ) {
                    cameraPreview(torchEnabled)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1.7f),
                    ) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                        ) {}
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                    OutlinedButton(
                        onClick = { torchEnabled = !torchEnabled },
                        modifier = Modifier.testTag("barcode-torch"),
                    ) {
                        Text(
                            stringResource(
                                if (torchEnabled) R.string.torch_off else R.string.torch_on,
                            ),
                        )
                    }
                    Text(
                        stringResource(R.string.barcode_scan_hint),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("barcode-permission-denied"),
                ) {
                    Column(
                        Modifier.padding(BaselineSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                    ) {
                        Text(
                            stringResource(
                                if (permissionRequested) {
                                    R.string.barcode_permission_denied
                                } else {
                                    R.string.barcode_permission_needed
                                },
                            ),
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
            ProductSearch(
                state = state,
                onQuery = onSearchQuery,
                onSearch = onSearch,
                onSelect = onSelectProduct,
            )
        } else {
            ProductDetails(
                state = state,
                onAmount = onAmount,
                onAmountUnit = onAmountUnit,
                onMealType = onMealType,
                onUse = onUseProduct,
                onScanAgain = onScanAgain,
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
    Text(stringResource(R.string.product_search_title), fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = onQuery,
        label = { Text(stringResource(R.string.product_search_query)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("barcode-search-query"),
    )
    Button(
        enabled = state.searchQuery.trim().length >= 2 && !state.loading,
        onClick = onSearch,
        modifier = Modifier.testTag("barcode-search"),
    ) {
        Text(stringResource(R.string.search))
    }
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

@Composable
private fun ProductDetails(
    state: BarcodeUiState,
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
    val preview = runCatching {
        product.toMealEditor(
            state.amount,
            state.amountUnit,
            "2000-01-01",
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
    Button(
        enabled = preview != null,
        onClick = onUse,
        modifier = Modifier.testTag("barcode-use-product"),
    ) {
        Text(stringResource(R.string.use_product))
    }
    TextButton(onClick = onScanAgain) { Text(stringResource(R.string.scan_another_product)) }
}

@Composable
private fun ProductSummary(product: ProductDto, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            Modifier.padding(BaselineSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
        ) {
            product.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.product_image, product.name),
                    modifier = Modifier.size(88.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
                Text(product.name, fontWeight = FontWeight.Bold)
                product.brand?.let { Text(it) }
                product.quantity?.let { Text(it) }
                Text(product.barcode)
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
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
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
                        }?.let(onBarcodeDetected)
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
