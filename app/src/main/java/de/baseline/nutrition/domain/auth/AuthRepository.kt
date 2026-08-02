package de.baseline.nutrition.domain.auth

import de.baseline.nutrition.domain.session.SessionRepository

enum class AccountDeletionStatus { Completed, Accepted }

interface AuthRepository : SessionRepository {
    suspend fun checkServer()
    suspend fun login(username: String, password: String)
    suspend fun register(accessCode: String, username: String, password: String, locale: String)
    suspend fun logout(allDevices: Boolean = false)
    suspend fun deleteAccount(password: String): AccountDeletionStatus
}
