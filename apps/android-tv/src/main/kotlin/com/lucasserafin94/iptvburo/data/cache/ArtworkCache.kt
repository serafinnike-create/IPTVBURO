package com.lucasserafin94.iptvburo.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The part of the cache the view model depends on.
 *
 * An interface for the same reason `PlaybackSessionStore` is one: the implementation needs a
 * working Android context to reach Coil and the cache directory, so a plain JVM test asserting what
 * navigation does would otherwise have to stand one up to reach it.
 */
interface ArtworkCacheAccess {
    suspend fun warm(url: String): Boolean

    fun bytesUsed(): Long

    fun clear()
}

/**
 * The artwork Coil keeps on disk, seen from outside Coil.
 *
 * The Android counterpart of the desktop object of the same name, and deliberately the same three
 * operations: fetch an image *now* so a later draw is instant, say how much room the cache takes,
 * and empty it.
 *
 * Everything here is best-effort. A cache is an optimisation, so failing to warm one poster or to
 * measure the directory must never surface as an error — the app simply behaves as it did before
 * the cache existed.
 */
@Singleton
class ArtworkCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ArtworkCacheAccess {
    /**
     * Fetches [url] into the cache, suspending until it is written.
     *
     * Suspending — unlike the desktop's fire-and-forget `enqueue` — because the fill has to report
     * honest progress and respect a pause. Enqueuing forty thousand requests at once would hand
     * Coil the whole library in a burst, report "done" before anything had landed, and leave the
     * pause button with nothing to stop.
     */
    override suspend fun warm(url: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val request =
                ImageRequest.Builder(context)
                    .data(url)
                    // The bytes on disk are the point; nothing is waiting to draw this bitmap, so
                    // decoding it into memory would cost more than the download itself.
                    .listener(
                        onSuccess = { _, _ -> if (continuation.isActive) continuation.resume(true) },
                        onError = { _, _ -> if (continuation.isActive) continuation.resume(false) },
                        onCancel = { if (continuation.isActive) continuation.resume(false) },
                    )
                    .build()
            val disposable = SingletonImageLoader.get(context).enqueue(request)
            continuation.invokeOnCancellation { disposable.dispose() }
        }

    /**
     * How many bytes the cache directory holds.
     *
     * Measured by walking the directory rather than asked of Coil, because Coil's own accounting is
     * internal and because this is the number the device's own storage screen reports — showing
     * anything else would be showing a figure the viewer can disprove.
     *
     * **Never call this from the main thread.** A full cache on a real list is tens of thousands of
     * files — measured at 49,007 on a device — and walking them takes long enough to freeze the UI
     * outright. That is exactly what happened: the settings screen stopped responding once the
     * cache filled. The result is cached below so repeated asks are answered without walking again.
     */
    override fun bytesUsed(): Long {
        val cached = lastMeasurement
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.takenAtMillis < MEASUREMENT_FRESHNESS_MILLIS) {
            return cached.bytes
        }
        val measured =
            runCatching {
                directory().walkBottomUp().filter(File::isFile).sumOf(File::length)
            }.getOrDefault(0L)
        lastMeasurement = Measurement(bytes = measured, takenAtMillis = now)
        return measured
    }

    /**
     * The last figure measured, so a screen that asks repeatedly does not re-walk the directory.
     *
     * Volatile because the fill worker measures on a background thread while the settings screen
     * reads on another; a stale-by-seconds byte count is a fair trade, an inconsistent one is not.
     */
    @Volatile
    private var lastMeasurement: Measurement? = null

    private data class Measurement(val bytes: Long, val takenAtMillis: Long)

    /** Deletes everything held. The next draw fetches again, which is the cost of asking for this. */
    override fun clear() {
        runCatching { directory().deleteRecursively() }
        // Forgotten rather than set to zero, so the next ask measures the directory that now exists
        // instead of trusting a figure taken before it was emptied.
        lastMeasurement = null
    }

    /** The same directory the image loader is configured with, and the only place this writes. */
    fun directory(): File = context.cacheDir.resolve(DIRECTORY_NAME)

    companion object {
        /**
         * How long a measurement is reused before the directory is walked again.
         *
         * Long enough that a fill reporting progress every few items does not re-walk tens of
         * thousands of files, short enough that the figure on screen still tracks a running
         * download. The size shown is a courtesy, not an accounting record.
         */
        private const val MEASUREMENT_FRESHNESS_MILLIS = 10_000L

        /** Shared with the image loader so both sides agree on where the cache lives. */
        const val DIRECTORY_NAME = "image_cache"
    }
}
