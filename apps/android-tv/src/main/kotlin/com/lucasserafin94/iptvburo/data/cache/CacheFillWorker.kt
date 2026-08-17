package com.lucasserafin94.iptvburo.data.cache

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucasserafin94.iptvburo.data.local.dao.ChannelDao
import com.lucasserafin94.iptvburo.data.preferences.CacheSettingsStore
import com.lucasserafin94.iptvburo.domain.model.CacheFillProgress
import com.lucasserafin94.iptvburo.domain.model.CacheFillState
import com.lucasserafin94.iptvburo.domain.model.CachePolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where a resumed fill should start.
 *
 * Its own function so the rule can be asserted directly: this is what stops Continuar sending the
 * counter backwards, and it is only safe to trust an offset that describes the same list it was
 * written against. A mark whose total no longer matches, or which is already at the end, means
 * start afresh — skipping to a stale position would quietly miss real work.
 */
internal fun resumeOffset(
    markDone: Int,
    markTotal: Int,
    urlCount: Int,
): Int = if (markTotal == urlCount && markDone in 1 until urlCount) markDone else 0

/**
 * Fetches the artwork of everything in the library, so the list is ready before it is opened.
 *
 * A worker rather than a coroutine in the view model, for the reason the reminder digest is one:
 * the first fill on a large list takes minutes, and a job tied to a screen dies the moment somebody
 * leaves it or the system reclaims the process. This survives both, and reports progress the
 * settings screen can draw whether or not it was open when the work started.
 *
 * What it warms is the same Coil cache the screens read, so a poster fetched here is one the grid
 * does not fetch later. Nothing else is stored: this is the ordinary image pipeline run ahead of
 * time, and the credential gate in the image loader applies here exactly as it does to a draw.
 */
@HiltWorker
class CacheFillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val artworkCache: ArtworkCacheAccess,
    private val cacheSettings: CacheSettingsStore,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val budget = cacheSettings.observeBudget().first()
        // Zero is a real answer: the viewer asked for nothing to be pre-fetched, and the work
        // finishes rather than failing so it does not sit in the queue looking broken.
        if (!budget.isEnabled) return Result.success()

        val urls =
            runCatching { channelDao.artworkUrlsForCaching(MAX_ITEMS) }
                .getOrDefault(emptyList())
                // The same rule the image loader applies. Checked here too so the fill does not
                // spend the viewer's data fetching addresses that will never be written to disk.
                .filter(::isStorableArtwork)

        if (urls.isEmpty()) {
            setProgress(progressData(done = 0, total = 0, state = CacheFillState.COMPLETE))
            return Result.success()
        }

        // Resumed from where the last pass stopped, rather than restarted.
        //
        // Warming an address already on disk is cheap but not free — Coil still has to be asked,
        // and on a list of thirty thousand that is thousands of pointless round trips. Worse, the
        // counter would visibly fall back to zero every time somebody pressed Continuar, which
        // reads as the download having lost its place.
        //
        // Guarded against a list that shrank since the mark was written: an offset past the end
        // would silently skip everything, so anything out of range starts afresh.
        val mark = runCatching { cacheSettings.observeMark().first() }.getOrNull()
        val startAt =
            resumeOffset(
                markDone = mark?.done ?: 0,
                markTotal = mark?.total ?: 0,
                urlCount = urls.size,
            )

        var done = startAt
        var budgetSpent = false
        var bytesUsed = artworkCache.bytesUsed()
        setProgress(progressData(done = done, total = urls.size, state = CacheFillState.RUNNING))

        // Walked by index rather than `drop`, which would copy the whole list to skip a prefix.
        for (index in startAt until urls.size) {
            val url = urls[index]
            // Cancellation is how pause and cancel reach this loop; checked every item so they
            // stop within a poster rather than within a hundred.
            if (isStopped) break

            // The budget is a ceiling on what is kept, not a promise to keep everything: a library
            // whose artwork exceeds the chosen size fills to that size and stops. Re-measured
            // periodically rather than every item, because walking the directory is far dearer
            // than the fetch it guards.
            if (done % BYTES_RECHECK_STEP == 0) {
                bytesUsed = artworkCache.bytesUsed()
                if (!CachePolicy.canWrite(budget, bytesUsed, APPROXIMATE_ITEM_BYTES)) {
                    // The budget is spent, which is a finished job rather than an interrupted one.
                    // Reported as COMPLETE so the card says "everything the budget allows is
                    // stored" and offers Atualizar — rather than PAUSED, which offered Continuar
                    // against a full cache and stopped again the moment it was pressed.
                    budgetSpent = true
                    break
                }
            }

            runCatching { artworkCache.warm(url) }
            done += 1

            // Reported every few items: publishing progress forty thousand times would spend more
            // on IPC than on the download it is describing.
            if (done % PROGRESS_STEP == 0 || done == urls.size) {
                setProgress(
                    progressData(
                        done = done,
                        total = urls.size,
                        state = CacheFillState.RUNNING,
                        bytesWritten = bytesUsed,
                    ),
                )
                // Also kept outside WorkManager: cancelling a worker discards its published
                // progress, and the paused bar has to keep saying how far the download got.
                cacheSettings.rememberMark(done = done, total = urls.size)
            }
        }

        cacheSettings.rememberMark(done = done, total = urls.size)
        val finalState =
            when {
                budgetSpent -> CacheFillState.COMPLETE
                isStopped -> CacheFillState.PAUSED
                else -> CacheFillState.COMPLETE
            }
        setProgress(
            progressData(
                done = done,
                total = urls.size,
                state = finalState,
                bytesWritten = artworkCache.bytesUsed(),
            ),
        )
        return Result.success()
    }

    private fun progressData(
        done: Int,
        total: Int,
        state: CacheFillState,
        bytesWritten: Long = 0L,
    ): Data =
        Data.Builder()
            .putInt(KEY_DONE, done)
            .putInt(KEY_TOTAL, total)
            .putLong(KEY_BYTES, bytesWritten)
            .putString(KEY_STATE, state.name)
            .build()

    companion object {
        const val WORK_NAME = "iptvburo-cache-fill"

        private const val KEY_DONE = "done"
        private const val KEY_TOTAL = "total"
        private const val KEY_BYTES = "bytes"
        private const val KEY_STATE = "state"

        /** Progress is published every this many items. */
        private const val PROGRESS_STEP = 10

        /**
         * How often the directory is re-measured against the budget.
         *
         * Deliberately coarse. Measuring means walking every file the cache holds, which on a full
         * library is tens of thousands of them; doing that every fifty images made the fill spend
         * more time counting bytes than fetching artwork, and contributed to the app freezing once
         * the cache filled. The ceiling is still honoured — a few hundred images of overshoot
         * against a budget measured in gigabytes is well inside the estimate the check uses anyway.
         */
        private const val BYTES_RECHECK_STEP = 500

        /** One poster at the width this app requests, for the ceiling check between measurements. */
        private const val APPROXIMATE_ITEM_BYTES = 110_000L

        /**
         * A ceiling on how many addresses one fill will consider.
         *
         * Not a limit anybody should reach: it exists so a pathological list cannot turn a
         * background courtesy into an unbounded walk.
         */
        private const val MAX_ITEMS = 100_000

        /** Starts the fill, or leaves the running one alone. */
        fun start(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // KEEP rather than REPLACE: pressing the button twice must not restart a fill that
                // is already half-way through the library.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CacheFillWorker>().build(),
            )
        }

        /**
         * Starts a fresh pass, displacing whatever is queued or running.
         *
         * REPLACE rather than cancel-then-start: cancelling is not instantaneous, and a KEEP that
         * arrived while the old worker was still winding down would be ignored — the button would
         * appear to do nothing.
         */
        fun restart(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CacheFillWorker>().build(),
            )
        }

        /** Stops the fill. What is already on disk stays there, so this is also "pause". */
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * What the fill is doing, for the settings screen.
         *
         * Read from WorkManager rather than kept in the view model, so a screen opened after the
         * work started shows the real position instead of an empty bar.
         */
        fun observeProgress(context: Context): Flow<CacheFillProgress> =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .map { infos -> infos.firstOrNull().toProgress() }

        private fun WorkInfo?.toProgress(): CacheFillProgress {
            if (this == null) return CacheFillProgress()
            val data = progress
            val state =
                runCatching { CacheFillState.valueOf(data.getString(KEY_STATE).orEmpty()) }
                    .getOrDefault(CacheFillState.IDLE)
            return CacheFillProgress(
                done = data.getInt(KEY_DONE, 0),
                total = data.getInt(KEY_TOTAL, 0),
                bytesWritten = data.getLong(KEY_BYTES, 0L),
                // A worker that has finished reports no progress data of its own, so the terminal
                // state comes from the work's state rather than from the last payload it published.
                state =
                    when {
                        this.state == WorkInfo.State.SUCCEEDED && state == CacheFillState.RUNNING ->
                            CacheFillState.COMPLETE
                        this.state == WorkInfo.State.CANCELLED -> CacheFillState.PAUSED
                        this.state.isFinished && state == CacheFillState.IDLE -> CacheFillState.IDLE
                        else -> state
                    },
            )
        }
    }
}
