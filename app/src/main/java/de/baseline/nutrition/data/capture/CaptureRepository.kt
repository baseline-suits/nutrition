package de.baseline.nutrition.data.capture

import de.baseline.nutrition.data.diary.IngredientDto
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.network.ApiClient
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequestDto(
    val text: String? = null,
    @SerialName("attachment_id") val attachmentId: String? = null,
    val locale: String,
    @SerialName("meal_type") val mealType: String,
)

@Serializable
data class AnalysisMealDto(
    val name: String,
    val ingredients: List<IngredientDto>,
    val nutrients: List<NutrientDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AnalysisDraftDto(
    val id: String,
    val status: String,
    val model: String,
    @SerialName("prompt_version") val promptVersion: String,
    @SerialName("schema_version") val schemaVersion: String,
    val meal: AnalysisMealDto,
    @SerialName("attachment_id") val attachmentId: String? = null,
)

@Serializable
data class UploadCreateDto(
    @SerialName("media_type") val mediaType: String,
    @SerialName("size_bytes") val sizeBytes: Int,
)

@Serializable
data class UploadDto(
    val id: String,
    val status: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("size_bytes") val sizeBytes: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("upload_path") val uploadPath: String,
    @SerialName("content_path") val contentPath: String? = null,
)

class CaptureRepository(private val api: ApiClient) {
    suspend fun analyzeText(
        text: String,
        locale: String,
        mealType: String,
        idempotencyKey: String,
    ): AnalysisDraftDto = api.request(
        path = "/v1/analysis",
        method = "POST",
        body = AnalysisRequestDto(text = text, locale = locale, mealType = mealType),
        authenticated = true,
        headers = mapOf("Idempotency-Key" to idempotencyKey),
    )

    suspend fun analyzePhoto(
        attachmentId: String,
        text: String?,
        locale: String,
        mealType: String,
        idempotencyKey: String,
    ): AnalysisDraftDto = api.request(
        path = "/v1/analysis",
        method = "POST",
        body = AnalysisRequestDto(
            text = text?.trim()?.ifBlank { null },
            attachmentId = attachmentId,
            locale = locale,
            mealType = mealType,
        ),
        authenticated = true,
        headers = mapOf("Idempotency-Key" to idempotencyKey),
    )

    suspend fun uploadPhoto(
        bytes: ByteArray,
        onProgress: (Int) -> Unit,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): UploadDto {
        val initialized: UploadDto = api.request(
            path = "/v1/uploads",
            method = "POST",
            body = UploadCreateDto("image/jpeg", bytes.size),
            authenticated = true,
            headers = mapOf("Idempotency-Key" to idempotencyKey),
        )
        api.upload<UploadDto>(
            path = "/v1/uploads/${initialized.id}/content",
            bytes = bytes,
            mediaType = "image/jpeg",
            onProgress = onProgress,
        )
        return api.request<Unit, UploadDto>(
            path = "/v1/uploads/${initialized.id}/finalize",
            method = "POST",
            authenticated = true,
        )
    }

    suspend fun deletePhoto(id: String) {
        api.request<Unit, Unit>("/v1/uploads/$id", "DELETE", authenticated = true)
    }
}
