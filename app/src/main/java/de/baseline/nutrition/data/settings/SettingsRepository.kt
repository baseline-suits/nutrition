package de.baseline.nutrition.data.settings

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.profile.ProfileRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SettingsAccountDto(
    val id: String,
    val username: String,
    val locale: String,
    val timezone: String,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
)

@Serializable
data class SettingsProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("biological_input") val biologicalInput: String? = null,
    @SerialName("height_cm") val heightCm: String? = null,
    @SerialName("weight_kg") val weightKg: String? = null,
    @SerialName("activity_level") val activityLevel: String? = null,
    @SerialName("goal_direction") val goalDirection: String? = null,
    @SerialName("target_kcal") val targetKcal: String,
    @SerialName("target_protein_g") val targetProtein: String,
    @SerialName("target_carbs_g") val targetCarbs: String,
    @SerialName("target_fat_g") val targetFat: String,
    @SerialName("targets_manual") val targetsManual: Int,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("calorie_budget_mode") val calorieBudgetMode: String = "fixed",
    @SerialName("budget_mode_effective_day") val budgetModeEffectiveDay: String? = null,
    val locale: String,
    val timezone: String,
)

@Serializable
data class SettingsHealthCursorDto(
    @SerialName("installation_id") val installationId: String,
    @SerialName("data_type") val dataType: String,
    val cursor: String,
    @SerialName("window_end") val windowEnd: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SettingsSourcePreferenceDto(
    @SerialName("data_type") val dataType: String,
    @SerialName("origin_package") val originPackage: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class SettingsHealthStateDto(
    val cursors: List<SettingsHealthCursorDto> = emptyList(),
    @SerialName("source_preferences")
    val sourcePreferences: List<SettingsSourcePreferenceDto> = emptyList(),
)

@Serializable
data class SettingsHealthSourceDto(
    @SerialName("origin_package") val originPackage: String,
    @SerialName("record_count") val recordCount: Int,
    val value: String? = null,
    @SerialName("overlap_detected") val overlapDetected: Boolean,
    val selected: Boolean,
)

@Serializable
data class SettingsHealthAggregateDto(
    @SerialName("data_type") val dataType: String,
    @SerialName("local_day") val localDay: String,
    val status: String,
    val value: String? = null,
    val unit: String,
    @SerialName("selected_origin_package") val selectedOriginPackage: String? = null,
    val sources: List<SettingsHealthSourceDto> = emptyList(),
)

@Serializable
private data class LocaleSettingPayload(val locale: String)

@Serializable
private data class LocaleSettingResponse(val locale: String)

@Serializable
private data class SourcePreferencePayload(
    @SerialName("origin_package") val originPackage: String,
)

interface SettingsDataSource {
    suspend fun account(): SettingsAccountDto
    suspend fun profile(): SettingsProfileDto
    suspend fun saveProfile(profile: ProfileRequest): SettingsProfileDto
    suspend fun saveLocale(locale: String): String
    suspend fun healthState(): SettingsHealthStateDto
    suspend fun healthAggregates(start: String, end: String): List<SettingsHealthAggregateDto>
    suspend fun saveSourcePreference(dataType: String, originPackage: String)
    suspend fun clearSourcePreference(dataType: String)
}

class SettingsRepository(private val api: ApiClient) : SettingsDataSource {
    override suspend fun account(): SettingsAccountDto =
        api.request<Unit, SettingsAccountDto>("/v1/auth/session", "GET", authenticated = true)

    override suspend fun profile(): SettingsProfileDto =
        api.request<Unit, SettingsProfileDto>("/v1/profile", "GET", authenticated = true)

    override suspend fun saveProfile(profile: ProfileRequest): SettingsProfileDto {
        api.request<ProfileRequest, JsonObject>(
            "/v1/profile",
            "PUT",
            profile,
            authenticated = true,
        )
        return profile()
    }

    override suspend fun saveLocale(locale: String): String =
        api.request<LocaleSettingPayload, LocaleSettingResponse>(
            "/v1/settings/locale",
            "PUT",
            LocaleSettingPayload(locale),
            authenticated = true,
        ).locale

    override suspend fun healthState(): SettingsHealthStateDto =
        api.request<Unit, SettingsHealthStateDto>("/v1/health/state", "GET", authenticated = true)

    override suspend fun healthAggregates(
        start: String,
        end: String,
    ): List<SettingsHealthAggregateDto> = api.request<Unit, List<SettingsHealthAggregateDto>>(
        "/v1/health/aggregates?start=$start&end=$end",
        "GET",
        authenticated = true,
    )

    override suspend fun saveSourcePreference(dataType: String, originPackage: String) {
        api.request<SourcePreferencePayload, SettingsSourcePreferenceDto>(
            "/v1/health/source-preferences/$dataType",
            "PUT",
            SourcePreferencePayload(originPackage),
            authenticated = true,
        )
    }

    override suspend fun clearSourcePreference(dataType: String) {
        api.request<Unit, Unit>(
            "/v1/health/source-preferences/$dataType",
            "DELETE",
            authenticated = true,
        )
    }
}
