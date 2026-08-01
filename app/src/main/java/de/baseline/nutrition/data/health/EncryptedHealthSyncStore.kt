package de.baseline.nutrition.data.health

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredHealthCursor(
    val cursor: String,
    val windowEnd: String,
)

@Serializable
data class HealthSyncSnapshot(
    val pending: Map<String, HealthSyncBatchPayload> = emptyMap(),
    val cursors: Map<String, StoredHealthCursor> = emptyMap(),
)

interface HealthSyncStorage {
    fun installationId(): String
    fun read(userId: String): HealthSyncSnapshot
    fun write(userId: String, snapshot: HealthSyncSnapshot)
    fun clear(userId: String)
}

class EncryptedHealthSyncStore(context: Context) : HealthSyncStorage {
    private val preferences = context.getSharedPreferences("secure_health_sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val alias = "baseline_health_sync_key"

    @Synchronized
    override fun installationId(): String {
        preferences.getString(INSTALLATION_ID, null)?.let { encrypted ->
            runCatching { decrypt(encrypted) }.getOrNull()?.let { return it }
        }
        val created = UUID.randomUUID().toString()
        check(preferences.edit().putString(INSTALLATION_ID, encrypt(created)).commit())
        return created
    }

    @Synchronized
    override fun read(userId: String): HealthSyncSnapshot {
        val name = preferenceName(userId)
        val encrypted = preferences.getString(name, null) ?: return HealthSyncSnapshot()
        return runCatching {
            json.decodeFromString<HealthSyncSnapshot>(decrypt(encrypted))
        }.getOrElse {
            preferences.edit().remove(name).commit()
            HealthSyncSnapshot()
        }
    }

    @Synchronized
    override fun write(userId: String, snapshot: HealthSyncSnapshot) {
        check(
            preferences.edit()
                .putString(preferenceName(userId), encrypt(json.encodeToString(snapshot)))
                .commit(),
        )
    }

    @Synchronized
    override fun clear(userId: String) {
        preferences.edit().remove(preferenceName(userId)).commit()
    }

    private fun preferenceName(userId: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
        return "user_" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }

    private fun decrypt(value: String): String {
        val parts = value.split(".")
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val INSTALLATION_ID = "installation_id"
    }
}
