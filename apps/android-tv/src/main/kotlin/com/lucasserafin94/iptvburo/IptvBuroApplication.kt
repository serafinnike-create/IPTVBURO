package com.lucasserafin94.iptvburo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
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
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.lucasserafin94.iptvburo.data.cache.ArtworkCache
import com.lucasserafin94.iptvburo.data.cache.isStorableArtwork
import com.lucasserafin94.iptvburo.data.preferences.CacheSettingsStore
import com.lucasserafin94.iptvburo.domain.model.CacheBudget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class IptvBuroApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    /**
     * Lets WorkManager build a worker that has dependencies injected.
     *
     * Without this the reminder worker is constructed by the default factory, which knows nothing
     * about the repository it needs, and every scheduled run fails before it starts — silently,
     * because a worker that cannot be instantiated is not something the user ever sees.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** The viewer's cache budget, needed while the image loader is being built. */
    @Inject
    lateinit var cacheSettings: CacheSettingsStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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
            .components {
                add(OkHttpNetworkFetcherFactory())
                // The credential gate, applied to every request the app makes.
                //
                // The disk cache was previously switched off at every call site because a
                // provider's authenticated artwork address carries the subscriber's username and
                // password in its path, and writing it to disk would leave that credential behind
                // long after the source was deleted. Turning the cache back on needs the danger
                // removed, not the flag flipped: an address that carries a credential is still
                // fetched and drawn, but only from memory, while an ordinary static poster is kept.
                //
                // Here rather than at the call sites so a screen added later cannot forget it.
                add(
                    Interceptor { chain ->
                        val model = chain.request.data
                        val request =
                            if (model is String && !isStorableArtwork(model)) {
                                chain.request.newBuilder()
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build()
                            } else {
                                chain.request
                            }
                        chain.withRequest(request).proceed()
                    },
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    // A share of what this process is actually allowed, not a fixed figure: the
                    // app targets phones and televisions with very different heap limits.
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(ArtworkCache.DIRECTORY_NAME))
                    // The viewer's own figure, read once at construction. Coil builds its loader
                    // once per process, so a budget changed in Settings takes effect on the next
                    // launch; the setting screen says so rather than pretending otherwise.
                    .maxSizeBytes(configuredDiskCacheBytes())
                    .build()
            }
            // Artwork arrives while a row is already on screen; fading it in reads as loading
            // rather than as the layout jumping.
            .crossfade(true)
            .build()

    /**
     * The chosen budget in bytes, or the default for somebody who has not been asked yet.
     *
     * Read inside `diskCache { }`, which Coil evaluates lazily off the main thread the first time
     * an image is fetched. That laziness is what makes the blocking read acceptable *here* and
     * nowhere else: an earlier version called this while building the loader, which put a DataStore
     * read on whatever thread drew the first poster and helped freeze the app on launch.
     *
     * Falls back to the default rather than throwing: a cache is an optimisation, and failing to
     * read a preference is not a reason to leave the app with no artwork at all.
     */
    private fun configuredDiskCacheBytes(): Long =
        runCatching {
            runBlocking { cacheSettings.observeBudget().first() }
        }.getOrDefault(CacheBudget.DEFAULT).bytes.coerceAtLeast(MINIMUM_DISK_CACHE_BYTES)

    private companion object {
        /**
         * What the cache falls back to when the viewer has chosen zero.
         *
         * Not actually zero: Coil rejects a cache of no size, and a browsing session still needs the
         * poster it drew a second ago. Zero in the setting means "do not pre-fill the library", which
         * is the expensive part; this is a working set, not a library, and it is cleared when the
         * viewer turns the feature off.
         */
        const val MINIMUM_DISK_CACHE_BYTES = 32L * 1024L * 1024L
    }
}
