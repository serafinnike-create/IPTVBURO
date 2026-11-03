package com.lucasserafin94.iptvburo.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucasserafin94.iptvburo.sync.SyncFailure
import com.lucasserafin94.iptvburo.sync.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Exchanges favourites with the household in the background: soon after one changes here, and
 * every half hour otherwise. Only ever scheduled on a device that joined a household.
 */
@HiltWorker
class FavoriteSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sync: AndroidFavoriteSync,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!sync.isMember) {
            cancelPeriodic(applicationContext)
            return Result.success()
        }
        return when (val result = sync.exchange()) {
            is SyncResult.Success -> Result.success()
            // A lost connection is worth another go; anything else is the server's answer, and
            // asking again at once would get the same one.
            is SyncResult.Failure -> if (result.reason == SyncFailure.NETWORK) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "favorite-sync-periodic"
        private const val SOON = "favorite-sync-soon"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** The half-hourly check-in, for as long as the device is in a household. */
        fun schedulePeriodic(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<FavoriteSyncWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(online)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A favourite changed: exchange a few seconds from now, so a burst of taps is one exchange. */
        fun requestSoon(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<FavoriteSyncWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setConstraints(online)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
