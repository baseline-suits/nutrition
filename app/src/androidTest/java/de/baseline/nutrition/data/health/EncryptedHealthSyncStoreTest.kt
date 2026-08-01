package de.baseline.nutrition.data.health

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedHealthSyncStoreTest {
    @Test
    fun installationAndPendingBatchSurviveRestartWhileUserClearKeepsInstallation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val userId = "encrypted-health-sync-user"
        val first = EncryptedHealthSyncStore(context)
        first.clear(userId)
        val installationId = first.installationId()
        val batch = HealthSyncBatchPayload(
            requestId = "encrypted-request",
            installationId = installationId,
            sections = listOf(
                HealthSyncSectionPayload(
                    dataType = "steps",
                    windowStart = "2026-07-01T00:00:00Z",
                    windowEnd = "2026-07-02T00:00:00Z",
                    cursor = "encrypted-cursor",
                    records = listOf(
                        HealthSyncRecordPayload(
                            dataType = "steps",
                            externalRecordId = "encrypted-record",
                            originPackage = "com.example.health",
                            startTime = "2026-07-01T08:00:00Z",
                            endTime = "2026-07-01T09:00:00Z",
                            zoneId = "Europe/Berlin",
                            value = "1000",
                            unit = "count",
                            lastModifiedTime = "2026-07-01T10:00:00Z",
                        ),
                    ),
                ),
            ),
        )
        first.write(
            userId,
            HealthSyncSnapshot(pending = mapOf("steps" to batch)),
        )

        val restarted = EncryptedHealthSyncStore(context)

        assertEquals(installationId, restarted.installationId())
        assertEquals(batch, restarted.read(userId).pending.getValue("steps"))
        val storedValues = context.getSharedPreferences(
            "secure_health_sync",
            android.content.Context.MODE_PRIVATE,
        ).all.values.joinToString()
        assertFalse(storedValues.contains("encrypted-request"))

        restarted.clear(userId)
        assertTrue(restarted.read(userId).pending.isEmpty())
        assertEquals(installationId, restarted.installationId())
    }
}
