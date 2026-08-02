package de.baseline.nutrition.data.profile

import de.baseline.nutrition.data.network.ApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRequest(
    val locale: String,
    val timezone: String,
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
    val manual: Boolean,
    val calculation: Map<String, String>? = null,
    @SerialName("calorie_budget_mode") val calorieBudgetMode: String = "fixed",
    @SerialName("expected_updated_at") val expectedUpdatedAt: String? = null,
)

@Serializable private data class SavedProfile(@SerialName("onboarding_complete") val complete: Boolean)

class ProfileRepository(private val api: ApiClient) {
    suspend fun save(profile: ProfileRequest) {
        api.request<ProfileRequest, SavedProfile>("/v1/profile", "PUT", profile, authenticated = true)
    }
}
