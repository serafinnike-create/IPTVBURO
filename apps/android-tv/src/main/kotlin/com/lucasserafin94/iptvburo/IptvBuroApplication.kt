package com.lucasserafin94.iptvburo

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvBuroApplication : Application(), SingletonImageLoader.Factory {
    @OptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        // Third-party playback errors can contain the complete credential-bearing media URI.
        // The app reports a redacted, user-facing failure state instead.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
    }

    /**
     * The image loader every `AsyncImage` in the app uses.
     *
     * Coil 3 split networking into its own artifact and **does not register a network fetcher by
     * itself**: without this, every `http(s)` model fails to load while local ones still work, so
     * the app looked correct in previews and showed empty grey cards on the device. That is what
     * left every poster blank — catalogue artwork, cast photos and the Assinaturas shelves alike.
     *
     * Caches are configured here rather than left to their defaults because artwork is the heaviest
     * thing this app fetches: a shelf of twenty posters re-downloaded on every scroll is both slow
     * and, on a phone, someone's data allowance.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    // A share of what this process is actually allowed, not a fixed figure: the
                    // app targets phones and televisions with very different heap limits.
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            // Artwork arrives while a row is already on screen; fading it in reads as loading
            // rather than as the layout jumping.
            .crossfade(true)
            .build()

    private companion object {
        /** Enough for a browsing session's artwork without taking over the device's storage. */
        const val DISK_CACHE_BYTES = 128L * 1024L * 1024L
    }
}
