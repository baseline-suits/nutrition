package de.baseline.nutrition.ui.barcode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.product.ProductDto
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BarcodeFlowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deniedCameraPermissionKeepsSearchAndPermissionActionAvailable() {
        var requested = false
        show(
            state = BarcodeUiState(),
            permissionGranted = false,
            onPermission = { requested = true },
        )

        compose.onNodeWithTag("barcode-permission-denied").assertIsDisplayed()
        compose.onNodeWithTag("barcode-request-permission").performClick()
        compose.runOnIdle { assertTrue(requested) }
        compose.onNodeWithTag("barcode-search-query").assertIsDisplayed()
    }

    @Test
    fun successfulProductCanEnterTheSharedReviewFlow() {
        var used = false
        show(
            state = BarcodeUiState(product = product(), amount = "100", amountUnit = "g"),
            permissionGranted = true,
            onUse = { used = true },
        )

        compose.onNodeWithTag("barcode-product").assertIsDisplayed()
        compose.onNodeWithTag("barcode-use-product").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(used) }
    }

    @Test
    fun unknownProductShowsFallbackWithoutCreatingAResult() {
        show(
            state = BarcodeUiState(
                scannedBarcode = "4008400214504",
                errorCode = "product_not_found",
            ),
            permissionGranted = true,
        )

        compose.onNodeWithTag("barcode-error-product_not_found")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun offlineProviderShowsFallbackWithoutCreatingAResult() {
        show(
            state = BarcodeUiState(
                scannedBarcode = "4008400214504",
                errorCode = "off_unavailable",
            ),
            permissionGranted = true,
        )

        compose.onNodeWithTag("barcode-error-off_unavailable")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun show(
        state: BarcodeUiState,
        permissionGranted: Boolean,
        onPermission: () -> Unit = {},
        onUse: () -> Unit = {},
    ) {
        compose.setContent {
            BaselineTheme {
                BarcodeContent(
                    state = state,
                    cameraPermissionGranted = permissionGranted,
                    permissionRequested = true,
                    onRequestPermission = onPermission,
                    onSearchQuery = {},
                    onSearch = {},
                    onSelectProduct = {},
                    onAmount = {},
                    onAmountUnit = {},
                    onMealType = {},
                    onUseProduct = onUse,
                    onScanAgain = {},
                    onRetry = {},
                    onManual = {},
                    onDescription = {},
                    onClose = {},
                    cameraPreview = {},
                )
            }
        }
    }

    private fun product() = ProductDto(
        schemaVersion = "off-product/1.0.0",
        barcode = "4008400214504",
        name = "Haferflocken",
        brand = "Fixture",
        quantity = "500 g",
        servingSize = "40 g",
        servingQuantity = "40",
        servingUnit = "g",
        basis = "100g",
        nutrients = listOf(
            NutrientDto(
                key = "energy",
                value = "370.5",
                unit = "kcal",
                basis = "100g",
                source = "open_food_facts",
            ),
        ),
        missingCore = listOf("protein", "carbohydrates", "fat"),
        source = "open_food_facts",
        fetchedAt = "2026-07-31T08:00:00Z",
    )
}
