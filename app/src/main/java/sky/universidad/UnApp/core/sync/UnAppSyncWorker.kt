package sky.universidad.UnApp.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import sky.universidad.UnApp.UnAppApplication
import sky.universidad.UnApp.core.common.AppResult
import java.util.concurrent.TimeUnit

class UnAppSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? UnAppApplication ?: return Result.failure()
        val prefs = app.container.preferencesRepository.preferences.first()
        if (!prefs.autoSyncEnabled) {
            return Result.success()
        }

        return when (val outcome = app.container.repository.syncAll(notifyNewTasks = true)) {
            is AppResult.Success -> Result.success()
            is AppResult.Error -> {
                if (outcome.isNetworkError) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "unapp_periodic_academic_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<UnAppSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
