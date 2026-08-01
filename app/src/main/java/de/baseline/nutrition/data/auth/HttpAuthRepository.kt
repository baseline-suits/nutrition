package de.baseline.nutrition.data.auth

import de.baseline.nutrition.data.network.ApiClient
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.session.SecureSessionStore
import de.baseline.nutrition.data.sync.MealSyncScheduler
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
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
@Serializable private data class AccountDeletionRequest(
    val password: String,
    val confirmation: String = "DELETE",
)
@Serializable private data class AccountDeletionResponse(
    @SerialName("deletion_id") val deletionId: String,
    val status: String,
)

class HttpAuthRepository(
    private val api: ApiClient,
    private val store: SecureSessionStore,
    private val syncScheduler: MealSyncScheduler? = null,
    private val localDataCleaner: (suspend (String) -> Unit)? = null,
) : AuthRepository {
    override suspend fun checkServer() {
        val response = api.request<Unit, HealthResponse>("/health", "GET")
        check(response.status == "ok")
    }

    override suspend fun restore(): SessionState {
        store.readAccountDeletionUserId()?.let { userId ->
            syncScheduler?.cancel(userId)
            localDataCleaner?.invoke(userId)
            store.clear()
            return SessionState.LoggedOut
        }
        if (store.readToken() == null) return SessionState.LoggedOut
        return try {
            val session = api.request<Unit, SessionCheck>("/v1/auth/session", "GET", authenticated = true)
            store.readUserId()?.let { syncScheduler?.schedule(it) }
            if (session.onboardingComplete) SessionState.Authenticated else SessionState.OnboardingOpen
        } catch (error: ApiException) {
            if (error.status == 401) SessionState.LoggedOut else throw error
        }
    }

    override suspend fun login(username: String, password: String) {
        val response = api.request<Credentials, SessionResponse>(
            "/v1/auth/login", "POST", Credentials(username.trim(), password),
        )
        store.clearAccountDeletionMarker()
        store.writeSession(response.token, response.userId)
        syncScheduler?.schedule(response.userId)
    }

    override suspend fun register(accessCode: String, username: String, password: String, locale: String) {
        val response = api.request<Registration, SessionResponse>(
            "/v1/auth/register", "POST", Registration(accessCode.trim(), username.trim(), password, locale),
        )
        store.clearAccountDeletionMarker()
        store.writeSession(response.token, response.userId)
        syncScheduler?.schedule(response.userId)
    }

    override suspend fun logout(allDevices: Boolean) {
        val userId = store.readUserId()
        runCatching {
            api.request<Unit, Unit>(
                if (allDevices) "/v1/auth/logout-all" else "/v1/auth/logout",
                "POST",
                authenticated = true,
            )
        }
        userId?.let { syncScheduler?.cancel(it) }
        userId?.let { localDataCleaner?.invoke(it) }
        store.clear()
    }

    override suspend fun deleteAccount(password: String): AccountDeletionStatus {
        val userId = requireNotNull(store.readUserId()) { "authenticated_user" }
        store.markAccountDeletion(userId)
        syncScheduler?.cancel(userId)
        val response = try {
            api.request<AccountDeletionRequest, AccountDeletionResponse>(
                "/v1/account",
                "DELETE",
                AccountDeletionRequest(password),
                authenticated = true,
            )
        } catch (error: Exception) {
            if (error is ApiException && error.status in setOf(403, 422)) {
                store.clearAccountDeletionMarker()
                syncScheduler?.schedule(userId)
            } else {
                localDataCleaner?.invoke(userId)
            }
            throw error
        }
        localDataCleaner?.invoke(userId)
        store.clear()
        return when (response.status) {
            "completed" -> AccountDeletionStatus.Completed
            "accepted" -> AccountDeletionStatus.Accepted
            else -> error("unexpected_account_deletion_status")
        }
    }
}
