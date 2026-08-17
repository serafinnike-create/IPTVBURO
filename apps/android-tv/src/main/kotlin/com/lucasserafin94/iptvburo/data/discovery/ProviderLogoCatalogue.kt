package com.lucasserafin94.iptvburo.data.discovery

import com.lucasserafin94.iptvburo.metadata.TmdbClient
import com.lucasserafin94.iptvburo.ui.designsystem.providerIdentityFor
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * The streaming services' own logos, keyed by the identity this app recognises them as.
 *
 * ## Why a catalogue rather than a lookup per title
 *
 * A details page can afford to ask TMDb what a single film streams on. A grid cannot: the home
 * screen draws dozens of cards at once and the catalogue thousands, so a request per card would be
 * hundreds of calls to draw one screen.
 *
 * TMDb publishes the whole directory for a region in **one** request — every service, with its
 * logo — which is exactly the shape this needs. Fetched once, held for the process, and read
 * synchronously by every card that wants a mark.
 *
 * The logos are TMDb's own distribution of the providers' marks, licensed for this use with
 * attribution. That is what makes showing a real Netflix or Prime logo legitimate here, where
 * drawing an imitation would not be.
 */
@Singleton
class ProviderLogoCatalogue @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /** Logo URL by [com.lucasserafin94.iptvburo.ui.designsystem.ProviderIdentity.label]. */
    private val logos = MutableStateFlow<Map<String, String>>(emptyMap())

    val logosByLabel: StateFlow<Map<String, String>> = logos

    /** Guards against several screens triggering the same fetch on first launch. */
    private val loading = Mutex()

    /** The key the current catalogue was built with, so a changed key refetches. */
    private var loadedWithKey: String? = null

    /**
     * Fills the catalogue, unless it already holds the answer for this key.
     *
     * Both directories are read — films and series list different services, and a household
     * browsing either should see the right marks. Failure is silence: a missing logo falls back to
     * the monogram on the service's colour, which is a worse badge but never a broken screen.
     */
    suspend fun ensureLoaded(apiKey: String?) {
        val key = apiKey?.takeIf(String::isNotBlank) ?: return
        if (loadedWithKey == key && logos.value.isNotEmpty()) return

        loading.withLock {
            if (loadedWithKey == key && logos.value.isNotEmpty()) return

            // Moved off the caller's thread deliberately.
            //
            // `TmdbClient` is blocking, and the view model observes profiles on the main
            // dispatcher: called from there the request never left the device — OkHttp refuses a
            // synchronous call on the main thread — and the directory came back empty in a few
            // milliseconds, which read exactly like "TMDb has no services for this region".
            withContext(Dispatchers.IO) {
                loadDirectories(key)
            }
        }
    }

    private fun loadDirectories(key: String) {
        run {
            val region = Locale.getDefault().country.takeIf { it.isNotBlank() } ?: DEFAULT_REGION
            val client =
                TmdbClient(
                    apiKey = key,
                    client = okHttpClient,
                    language = Locale.getDefault().toLanguageTag(),
                )

            val collected = mutableMapOf<String, String>()
            listOf(false, true).forEach { forSeries ->
                runCatching { client.watchProviderDirectory(region, forSeries) }
                    .getOrDefault(emptyList())
                    .forEach { provider ->
                        val logo = provider.logoUrl ?: return@forEach
                        // Matched through the same rule the badges use, so "Amazon Prime Video"
                        // from TMDb and "Prime Video" from a playlist resolve to one identity.
                        val label = providerIdentityFor(provider.name)?.label ?: return@forEach
                        // First wins: the directory is ordered by TMDb's display priority, so the
                        // earlier entry is the one they consider canonical for the region.
                        collected.putIfAbsent(label, logo)
                    }
            }

            if (collected.isNotEmpty()) {
                logos.value = collected
                loadedWithKey = key
            }
        }
    }

    private companion object {
        /** Used when the device reports no country, which happens on some emulators. */
        const val DEFAULT_REGION = "BR"
    }
}
