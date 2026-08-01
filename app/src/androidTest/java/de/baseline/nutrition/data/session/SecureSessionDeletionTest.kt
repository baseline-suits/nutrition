package de.baseline.nutrition.data.session

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureSessionDeletionTest {
    @Test
    fun accountDeletionMarkerSurvivesAmbiguousUnauthorizedResponse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        store.clear()
        store.writeSession("session-token", "deleting-user")
        store.markAccountDeletion("deleting-user")

        store.clearPreservingAccountDeletion()

        assertNull(store.readToken())
        assertNull(store.readUserId())
        assertEquals("deleting-user", store.readAccountDeletionUserId())
        store.clear()
    }
}
