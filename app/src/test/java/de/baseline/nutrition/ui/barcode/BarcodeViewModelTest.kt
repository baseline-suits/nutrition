package de.baseline.nutrition.ui.barcode

import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.product.ProductDataSource
import de.baseline.nutrition.data.product.ProductDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun duplicateScannerHitCreatesOnlyOneLookup() = runTest(dispatcher) {
        val repository = FakeProducts()
        val viewModel = BarcodeViewModel(repository, dispatcher)

        viewModel.barcodeDetected("4008400214504")
        viewModel.barcodeDetected("4008400214504")

        assertEquals(1, repository.lookups)
        assertEquals("4008400214504", viewModel.state.value.product?.barcode)
    }

    @Test
    fun failedBarcodeNeedsExplicitRetryBeforeSecondLookup() = runTest(dispatcher) {
        val repository = FakeProducts(error = ApiException(404, "product_not_found"))
        val viewModel = BarcodeViewModel(repository, dispatcher)

        viewModel.barcodeDetected("4008400214504")
        viewModel.barcodeDetected("4008400214504")
        assertEquals(1, repository.lookups)

        viewModel.retryBarcode()
        assertEquals(2, repository.lookups)
    }
}

private class FakeProducts(
    private val error: Throwable? = null,
) : ProductDataSource {
    var lookups = 0

    override suspend fun barcode(value: String): ProductDto {
        lookups += 1
        error?.let { throw it }
        return ProductDto(
            schemaVersion = "off-product/1.0.0",
            barcode = value,
            name = "Test",
            basis = "100g",
            nutrients = listOf(
                NutrientDto(
                    key = "energy",
                    value = "100",
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

    override suspend fun search(query: String): List<ProductDto> = emptyList()
}
