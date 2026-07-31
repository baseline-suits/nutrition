package de.baseline.nutrition.data.sync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MealSyncWorkerTest {
    @Test
    fun workerPausesCleanlyWithoutAuthenticatedQueueOwner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val worker = TestListenableWorkerBuilder<MealSyncWorker>(context).build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
