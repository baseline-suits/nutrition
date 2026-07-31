package de.baseline.nutrition.data.network

import de.baseline.nutrition.data.session.SecureSessionStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, val code: String) : Exception(code)

class ApiClient(
    @PublishedApi internal val baseUrl: () -> String,
    @PublishedApi internal val sessionStore: SecureSessionStore,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    inline fun <reified Request : Any, reified Response> request(
        path: String,
        method: String,
        body: Request? = null,
        authenticated: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val connection = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach(connection::setRequestProperty)
        if (authenticated) {
            sessionStore.readToken()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(json.encodeToString(body)) }
        }
        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_NO_CONTENT) {
            @Suppress("UNCHECKED_CAST")
            return Unit as Response
        }
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val content = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            if (status == 401 && authenticated) sessionStore.clear()
            val code = runCatching {
                (json.parseToJsonElement(content).jsonObject["detail"] as? JsonObject)
                    ?.get("code")?.toString()?.trim('"')
            }.getOrNull() ?: "network_error"
            throw ApiException(status, code)
        }
        return json.decodeFromString(content)
    }

    inline fun <reified Response> upload(
        path: String,
        bytes: ByteArray,
        mediaType: String,
        noinline onProgress: (Int) -> Unit = {},
    ): Response {
        val connection = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", mediaType)
        sessionStore.readToken()?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        connection.outputStream.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(64 * 1024, bytes.size - offset)
                output.write(bytes, offset, count)
                offset += count
                onProgress((offset * 100L / bytes.size).toInt())
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val content = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            if (status == 401) sessionStore.clear()
            val code = runCatching {
                (json.parseToJsonElement(content).jsonObject["detail"] as? JsonObject)
                    ?.get("code")?.toString()?.trim('"')
            }.getOrNull() ?: "network_error"
            throw ApiException(status, code)
        }
        return json.decodeFromString(content)
    }
}
