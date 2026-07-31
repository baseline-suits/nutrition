package de.baseline.nutrition.ui.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.product.ProductDataSource
import de.baseline.nutrition.data.product.ProductDto
import de.baseline.nutrition.domain.diary.IngredientDraft
import de.baseline.nutrition.domain.diary.MealEditorDraft
import de.baseline.nutrition.domain.diary.NutrientFields
import de.baseline.nutrition.domain.diary.parseLocalizedDecimal
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BarcodeUiState(
    val scannedBarcode: String? = null,
    val product: ProductDto? = null,
    val amount: String = "100",
    val amountUnit: String = "g",
    val mealType: String = "snack",
    val searchQuery: String = "",
    val searchResults: List<ProductDto> = emptyList(),
    val loading: Boolean = false,
    val errorCode: String? = null,
)

class BarcodeViewModel(
    private val repository: ProductDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BarcodeUiState())
    val state: StateFlow<BarcodeUiState> = mutableState.asStateFlow()

    fun barcodeDetected(value: String) {
        val current = mutableState.value
        if (current.loading || current.scannedBarcode == value) {
            return
        }
        mutableState.update {
            it.copy(scannedBarcode = value, loading = true, errorCode = null)
        }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.barcode(value) } }
                .onSuccess(::selectProduct)
                .onFailure(::failure)
        }
    }

    fun updateSearch(value: String) {
        mutableState.update { it.copy(searchQuery = value, errorCode = null) }
    }

    fun search() {
        val query = mutableState.value.searchQuery.trim()
        if (query.length < 2 || mutableState.value.loading) return
        mutableState.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.search(query) } }
                .onSuccess { products ->
                    mutableState.update {
                        it.copy(
                            searchResults = products,
                            loading = false,
                            errorCode = if (products.isEmpty()) "product_not_found" else null,
                        )
                    }
                }
                .onFailure(::failure)
        }
    }

    fun selectProduct(product: ProductDto) {
        val referenceUnit = product.referenceUnit()
        val hasPortion = product.servingQuantity != null && product.servingUnit == referenceUnit
        mutableState.update {
            it.copy(
                product = product,
                scannedBarcode = product.barcode,
                amount = if (hasPortion) "1" else "100",
                amountUnit = if (hasPortion) "portion" else referenceUnit,
                loading = false,
                errorCode = null,
                searchResults = emptyList(),
            )
        }
    }

    fun updateAmount(value: String) =
        mutableState.update { it.copy(amount = value, errorCode = null) }

    fun updateAmountUnit(value: String) {
        mutableState.update { state ->
            val product = state.product ?: return@update state
            val amount = when {
                value == "portion" -> "1"
                state.amountUnit == "portion" -> product.servingQuantity ?: "100"
                else -> state.amount
            }
            state.copy(amountUnit = value, amount = amount, errorCode = null)
        }
    }

    fun updateMealType(value: String) =
        mutableState.update { it.copy(mealType = value) }

    fun resetScanner() =
        mutableState.update {
            it.copy(
                scannedBarcode = null,
                product = null,
                amount = "100",
                amountUnit = "g",
                errorCode = null,
            )
        }

    fun retryBarcode() {
        val barcode = mutableState.value.scannedBarcode ?: return
        mutableState.update { it.copy(scannedBarcode = null, errorCode = null) }
        barcodeDetected(barcode)
    }

    fun createDraft(day: String): MealEditorDraft? {
        val current = mutableState.value
        val product = current.product ?: return null
        return runCatching {
            product.toMealEditor(current.amount, current.amountUnit, day, current.mealType)
        }.onFailure {
            mutableState.update { state -> state.copy(errorCode = "amount_invalid") }
        }.getOrNull()
    }

    fun complete() {
        mutableState.value = BarcodeUiState()
    }

    private fun failure(error: Throwable) {
        mutableState.update {
            it.copy(
                loading = false,
                errorCode = (error as? ApiException)?.code ?: "network_error",
            )
        }
    }

    companion object {
        fun factory(
            repository: ProductDataSource,
            ioDispatcher: CoroutineDispatcher,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BarcodeViewModel(repository, ioDispatcher) as T
        }
    }
}

fun ProductDto.toMealEditor(
    amountText: String,
    amountUnit: String,
    day: String,
    mealType: String,
): MealEditorDraft {
    val selectedAmount = requireNotNull(parseLocalizedDecimal(amountText))
    require(selectedAmount > BigDecimal.ZERO)
    val referenceUnit = referenceUnit()
    val amount = when (amountUnit) {
        referenceUnit -> selectedAmount
        "portion" -> {
            require(servingUnit == referenceUnit)
            selectedAmount * requireNotNull(parseLocalizedDecimal(servingQuantity.orEmpty()))
        }
        else -> error("unsupported amount unit")
    }
    val factor = amount.divide(BigDecimal("100"), 12, RoundingMode.HALF_UP)
    val scaledNutrients = nutrients.map { nutrient ->
        nutrient.copy(
            value = (
                requireNotNull(parseLocalizedDecimal(nutrient.value)) * factor
                ).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
            basis = "portion",
            source = "open_food_facts",
            locked = true,
        )
    }
    val scaled = scaledNutrients.associate { it.key to it.value }
    val fields = NutrientFields(
        energy = scaled["energy"].orEmpty(),
        protein = scaled["protein"].orEmpty(),
        carbohydrates = scaled["carbohydrates"].orEmpty(),
        fat = scaled["fat"].orEmpty(),
        additional = scaledNutrients.filterNot {
            it.key in setOf("energy", "protein", "carbohydrates", "fat")
        },
        sources = scaled.keys.associateWith { "open_food_facts" },
        locks = scaled.keys.associateWith { true },
        originalValues = scaled,
    )
    return MealEditorDraft(
        name = name,
        day = day,
        mealType = mealType,
        ingredients = listOf(
            IngredientDraft(
                name = listOfNotNull(name, brand).joinToString(" · "),
                amount = amount.stripTrailingZeros().toPlainString(),
                unit = referenceUnit,
                nutrients = fields,
            ),
        ),
        captureMethod = "barcode",
        provenanceSource = "open_food_facts",
        externalReference = "off:$barcode",
    )
}

fun ProductDto.referenceUnit(): String = if (basis == "100ml") "ml" else "g"
