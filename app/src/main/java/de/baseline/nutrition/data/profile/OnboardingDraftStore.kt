package de.baseline.nutrition.data.profile

import de.baseline.nutrition.data.session.SecureSessionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OnboardingDraft(
    val locale: String = "de",
    val manual: Boolean = false,
    val birthDate: String = "",
    val height: String = "",
    val weight: String = "",
    val biologicalInput: String = "",
    val activity: String = "",
    val direction: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val calorieBudgetMode: String = "fixed",
)

class OnboardingDraftStore(private val sessionStore: SecureSessionStore) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): OnboardingDraft {
        val userId = sessionStore.readUserId() ?: return OnboardingDraft()
        return sessionStore.readSecureString(key(userId))?.let {
            runCatching { json.decodeFromString<OnboardingDraft>(it) }.getOrNull()
        } ?: OnboardingDraft()
    }

    fun save(draft: OnboardingDraft) {
        val userId = sessionStore.readUserId() ?: return
        sessionStore.writeSecureString(key(userId), json.encodeToString(draft))
    }

    fun clear() {
        val userId = sessionStore.readUserId() ?: return
        sessionStore.removeSecureString(key(userId))
    }

    private fun key(userId: String) = "onboarding_draft:$userId"
}
