package de.baseline.nutrition.data.product

import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.network.ApiClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    @SerialName("schema_version") val schemaVersion: String,
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: String? = null,
    @SerialName("serving_unit") val servingUnit: String? = null,
    val basis: String,
    val nutrients: List<NutrientDto>,
    @SerialName("missing_core") val missingCore: List<String> = emptyList(),
    val language: String? = null,
    val country: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val source: String,
    @SerialName("fetched_at") val fetchedAt: String,
)

interface ProductDataSource {
    suspend fun barcode(value: String): ProductDto
    suspend fun search(query: String): List<ProductDto>
}

class ProductRepository(private val api: ApiClient) : ProductDataSource {
    override suspend fun barcode(value: String): ProductDto =
        api.request<Unit, ProductDto>(
            "/v1/products/barcode/$value",
            "GET",
            authenticated = true,
        )

    override suspend fun search(query: String): List<ProductDto> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return api.request<Unit, List<ProductDto>>(
            "/v1/products/search?q=$encoded",
            "GET",
            authenticated = true,
        )
    }
}
