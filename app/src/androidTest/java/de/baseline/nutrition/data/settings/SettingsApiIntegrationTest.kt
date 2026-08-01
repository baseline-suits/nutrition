package de.baseline.nutrition.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.auth.HttpAuthRepository
import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.data.session.SecureSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsApiIntegrationTest {
    @Test
    fun confirmedLocaleAndVersionedProfileChangesReachBackend() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("settingsIntegration") == "true")
        val accessCode = requireNotNull(arguments.getString("settingsAccessCode"))
        val baseUrl = arguments.getString("settingsBaseUrl") ?: "http://127.0.0.1:8765"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val session = SecureSessionStore(context).apply { clear() }
        val api = ApiClient({ baseUrl }, session)
        val auth = HttpAuthRepository(api, session)
        val repository = SettingsRepository(api)
        val username = "device-settings-${System.currentTimeMillis()}"
        try {
            auth.register(accessCode, username, PASSWORD, "de")
            val initial = repository.saveProfile(profileRequest("2000"))
            assertEquals("de", repository.account().locale)

            assertEquals("ru", repository.saveLocale("ru"))
            assertEquals("ru", repository.account().locale)

            val confirmed = repository.saveProfile(
                profileRequest("2100", initial.updatedAt, locale = "ru"),
            )
            assertEquals("2100", confirmed.targetKcal)

            val conflict = runCatching {
                repository.saveProfile(
                    profileRequest("2200", initial.updatedAt, locale = "ru"),
                )
            }.exceptionOrNull()
            assertTrue(conflict is ApiException)
            assertEquals(409, (conflict as ApiException).status)
            assertEquals("2100", repository.profile().targetKcal)
        } finally {
            runCatching { auth.deleteAccount(PASSWORD) }
            session.clear()
        }
    }

    private fun profileRequest(
        targetKcal: String,
        expectedUpdatedAt: String? = null,
        locale: String = "de",
    ) = ProfileRequest(
        locale = locale,
        timezone = "Europe/Berlin",
        targetKcal = targetKcal,
        targetProtein = "120",
        targetCarbs = "220",
        targetFat = "70",
        manual = true,
        calorieBudgetMode = "fixed",
        expectedUpdatedAt = expectedUpdatedAt,
    )

    private companion object {
        const val PASSWORD = "a-secure-password"
    }
}
