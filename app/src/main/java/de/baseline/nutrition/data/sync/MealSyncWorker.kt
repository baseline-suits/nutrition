package de.baseline.nutrition.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.baseline.nutrition.BaselineApplication
import java.util.concurrent.TimeUnit

class WorkManagerMealSyncScheduler(context: Context) : MealSyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule(userId: String) {
        val request = OneTimeWorkRequestBuilder<MealSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("meal-sync")
            .build()
        workManager.enqueueUniqueWork(
            "meal-sync-$userId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(userId: String) {
        workManager.cancelUniqueWork("meal-sync-$userId")
    }
}

class MealSyncWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? BaselineApplication ?: return Result.failure()
        val result = application.container.diaryRepository.syncPending()
        return when {
            result.authRequired -> Result.success()
            result.hasRetryableWork -> Result.retry()
            else -> Result.success()
        }
    }
}
