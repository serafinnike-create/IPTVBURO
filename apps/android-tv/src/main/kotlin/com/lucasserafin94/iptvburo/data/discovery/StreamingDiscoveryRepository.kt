package com.lucasserafin94.iptvburo.data.discovery

import com.lucasserafin94.iptvburo.BuildConfig
import com.lucasserafin94.iptvburo.di.IoDispatcher
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryCapability
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.metadata.TmdbDiscoverKind
import com.lucasserafin94.iptvburo.metadata.TmdbServiceShelf
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.metadata.TmdbTitleDetails
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The Android side of "where can I watch this" — GDD 9, the same feature Windows already ships.
 *
 * It owns no catalogue of its own. [TmdbStreamingCatalogue] lives in `packages/metadata-client` and
 * is shared with the desktop, so the two platforms cannot drift into answering the same question
 * differently. What this class adds is the Android-shaped part: reading the profile's encrypted key,
 * moving every blocking call off the main thread, and turning a missing key into a capability rather
 * than an error.
 *
 * Nothing here plays anything. Every result leads to the service's own app or site, which is what
 * keeps the feature a signpost rather than a way around a paywall.
 */
@Singleton
class StreamingDiscoveryRepository
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val shelfCache: ShelfCache,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Whether the Assinaturas destination may appear, given the key configured for this profile.
         *
         * AVAILABLE only with a key, because a key is what lets TMDb answer for real. Android ships
         * no fixture provider, so the DEMO_ONLY state this can technically express is never reached
         * here — the entry is simply absent until a key exists, which is the honest behaviour and
         * matches the desktop.
         *
         * The bundled key counts. It is what makes the feature work out of the box on a build that
         * was given one, exactly as on Windows.
         */
        fun capabilityFor(apiKey: String?): StreamingDiscoveryCapability =
            StreamingDiscoveryCapability.of(hasRealProvider = effectiveKey(apiKey) != null)

        /**
         * The key actually used for a request: the profile's own, else the one baked into the build.
         *
         * Three levels, matching the desktop: this profile's own key, then the household key that
         * every profile shares, then the one baked into the build. Each answers a different need —
         * TMDb rate-limits per key, so somebody who watches a lot has a reason to use their own.
         *
         * Clearing a level falls through to the next rather than switching metadata off, which is
         * what makes an empty field mean "use the default" rather than "turn this off".
         */
        fun effectiveKey(
            profileKey: String?,
            sharedKey: String? = null,
        ): String? =
            profileKey?.takeIf(String::isNotBlank)
                ?: sharedKey?.takeIf(String::isNotBlank)
                ?: BuildConfig.BUNDLED_TMDB_KEY.takeIf(String::isNotBlank)

        /**
         * One shelf per service for [region], or empty when there is nothing to show.
         *
         * Empty covers an unset key, a region TMDb has no listings for, and an unreachable network.
         * All three mean "show nothing" rather than an error the user has to dismiss: this screen is
         * an enhancement, and a failure here must never block the rest of the app.
         */
        suspend fun shelves(
            apiKey: String?,
            region: String,
            kind: TmdbDiscoverKind = TmdbDiscoverKind.MOVIES,
            /**
             * Skips today's cached answer.
             *
             * Set when the user did something that should change the result — saving a key, or
             * asking for a refresh — where serving the cache would look like the action failed.
             */
            refresh: Boolean = false,
        ): List<TmdbServiceShelf> {
            // Today's answer from the device first. TMDb rebuilds these lists once a day, so a
            // second request on the same day returns the same catalogue — the only difference the
            // user sees is whether the screen waits on the network to draw.
            if (!refresh) shelfCache.read(region, kind)?.let { return it }
            val catalogue = catalogueFor(apiKey, region) ?: return emptyList()
            val fetched =
                withContext(ioDispatcher) {
                    runCatching { catalogue.shelves(kind) }.getOrDefault(emptyList())
                }
            shelfCache.write(region, kind, fetched)
            return fetched
        }

        /**
         * Everywhere [title] can be watched, or null when TMDb has nothing to say.
         *
         * Null is "we cannot say", never "not available anywhere". Callers must not render it as the
         * latter — an absent answer and a negative answer are different facts.
         */
        suspend fun offersFor(
            apiKey: String?,
            region: String,
            title: ExternalTitle,
        ): ExternalTitleDetails? {
            val catalogue = catalogueFor(apiKey, region) ?: return null
            return withContext(ioDispatcher) { runCatching { catalogue.detailsFor(title) }.getOrNull() }
        }

        /**
         * Artwork, synopsis, cast and trailer for [title].
         *
         * Separate from [offersFor] on purpose: availability and the page are different questions
         * with different failure modes, and losing the whole screen because one of them failed would
         * be worse than drawing what came back.
         */
        suspend fun pageFor(
            apiKey: String?,
            region: String,
            title: ExternalTitle,
        ): TmdbTitleDetails? {
            val catalogue = catalogueFor(apiKey, region) ?: return null
            return withContext(ioDispatcher) { runCatching { catalogue.pageFor(title) }.getOrNull() }
        }

        /** Null without a key, which is what makes every call above a no-op rather than a failure. */
        private fun catalogueFor(
            apiKey: String?,
            region: String,
        ): TmdbStreamingCatalogue? {
            // The caller has already resolved profile-then-shared; only the bundled fallback is
            // left to apply here.
            val key = effectiveKey(apiKey) ?: return null
            return TmdbStreamingCatalogue(
                client =
                    TmdbClient(
                        apiKey = key,
                        client = okHttpClient,
                        language = Locale.getDefault().toLanguageTag(),
                    ),
                region = region,
            )
        }

        companion object {
            /** Matches the desktop's default so a household sees the same shelves on both. */
            const val DEFAULT_REGION: String = TmdbStreamingCatalogue.DEFAULT_REGION
        }
    }
