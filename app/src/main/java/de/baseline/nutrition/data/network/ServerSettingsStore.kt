package de.baseline.nutrition.data.network

import android.content.Context
import de.baseline.nutrition.data.session.SecureSessionStore
import java.net.URI

object ServerAddress {
    fun normalize(value: String, allowCleartext: Boolean): String {
        val candidate = value.trim().trimEnd('/')
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: throw IllegalArgumentException("invalid_server_url")
        val validScheme = uri.scheme == "https" || (allowCleartext && uri.scheme == "http")
        if (!validScheme || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw IllegalArgumentException("invalid_server_url")
        }
        return "$candidate/"
    }
}

class ServerSettingsStore(
    context: Context,
    defaultUrl: String,
    private val allowCleartext: Boolean,
    private val sessionStore: SecureSessionStore,
) {
    private val preferences = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
    private val fallback = ServerAddress.normalize(defaultUrl, allowCleartext)

    fun currentUrl(): String = preferences.getString("base_url", null) ?: fallback

    fun save(value: String): String {
        val normalized = ServerAddress.normalize(value, allowCleartext)
        if (normalized != currentUrl()) sessionStore.clear()
        preferences.edit().putString("base_url", normalized).apply()
        return normalized
    }
}

