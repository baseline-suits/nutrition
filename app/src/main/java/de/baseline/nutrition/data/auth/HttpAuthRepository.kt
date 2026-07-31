package de.baseline.nutrition.data.auth

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.session.SessionState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable private data class Credentials(val username: String, val password: String)
@Serializable private data class Registration(
    @SerialName("access_code") val accessCode: String,
    val username: String,
    val password: String,
    val locale: String,
)
@Serializable private data class SessionResponse(
    val token: String,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
    @SerialName("user_id") val userId: String,
)
@Serializable private data class SessionCheck(
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
)
@Serializable private data class HealthResponse(val status: String)

class HttpAuthRepository(
    private val api: ApiClient,
    private val store: SecureSessionStore,
) : AuthRepository {
    override suspend fun checkServer() {
        val response = api.request<Unit, HealthResponse>("/health", "GET")
        check(response.status == "ok")
    }

    override suspend fun restore(): SessionState {
        if (store.readToken() == null) return SessionState.LoggedOut
        return try {
            val session = api.request<Unit, SessionCheck>("/v1/auth/session", "GET", authenticated = true)
            if (session.onboardingComplete) SessionState.Authenticated else SessionState.OnboardingOpen
        } catch (error: ApiException) {
            if (error.status == 401) SessionState.LoggedOut else throw error
        }
    }

    override suspend fun login(username: String, password: String) {
        val response = api.request<Credentials, SessionResponse>(
            "/v1/auth/login", "POST", Credentials(username.trim(), password),
        )
        store.writeSession(response.token, response.userId)
    }

    override suspend fun register(accessCode: String, username: String, password: String, locale: String) {
        val response = api.request<Registration, SessionResponse>(
            "/v1/auth/register", "POST", Registration(accessCode.trim(), username.trim(), password, locale),
        )
        store.writeSession(response.token, response.userId)
    }

    override suspend fun logout(allDevices: Boolean) {
        runCatching {
            api.request<Unit, Unit>(
                if (allDevices) "/v1/auth/logout-all" else "/v1/auth/logout",
                "POST",
                authenticated = true,
            )
        }
        store.clear()
    }
}
