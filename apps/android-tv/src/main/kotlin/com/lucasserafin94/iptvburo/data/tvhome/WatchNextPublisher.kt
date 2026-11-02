package com.lucasserafin94.iptvburo.data.tvhome

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.lucasserafin94.iptvburo.domain.model.CatalogContentType
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.ContentKind
import com.lucasserafin94.iptvburo.domain.model.TitleShareLink
import com.lucasserafin94.iptvburo.ui.ContinueWatchingUi

/** One card for the television's own home screen, independent of any Android type. */
internal data class WatchNextEntry(
    val key: String,
    val title: String,
    val posterUrl: String?,
    /** Fraction watched, 0..1. */
    val progress: Float,
    /** Opens this title in the app, through the same path a shared link takes. */
    val appUri: String,
)

/**
 * What goes in the "Continue watching" row the Android TV launcher draws, from the app's own list.
 *
 * Films only, for now. A card opens the title through its [TitleShareLink], which names a film by
 * its normalised title and year so it is found again in whatever the catalogue holds by the time
 * the card is pressed; an episode would need its series' identity, which the continue-watching row
 * does not carry. Capped, because the launcher shows a handful and every card is a provider row.
 */
internal fun watchNextEntries(
    continueWatching: List<ContinueWatchingUi>,
    limit: Int = MAX_ENTRIES,
): List<WatchNextEntry> =
    continueWatching
        .asSequence()
        .filter { it.channel.contentType == CatalogContentType.MOVIE }
        .mapNotNull { item ->
            val channel = item.channel
            val link =
                TitleShareLink.of(
                    identity = ContentIdentity.of(ContentKind.MOVIE, channel.name, channel.year),
                    title = channel.name,
                    year = channel.year,
                ) ?: return@mapNotNull null
            WatchNextEntry(
                key = link.identity.key,
                title = channel.name,
                // Only a public image host: the launcher fetches this itself, and a provider's own
                // artwork address can carry the account's credentials.
                posterUrl = channel.logoUrl?.takeIf(TitleShareLink::isPublicArtwork),
                progress = item.progress.coerceIn(0f, 1f),
                appUri = link.appUri(),
            )
        }
        .distinctBy(WatchNextEntry::key)
        .take(limit)
        .toList()

/**
 * Keeps the launcher's "Continue watching" row in step with the app's.
 *
 * Television only, Android 8 and later: that is where the row exists. The app owns its rows in
 * the provider outright — they are only ever its own — so each publish replaces the set rather
 * than diffing it, which is simple and cannot leave a stale card behind.
 */
internal object WatchNextPublisher {
    @SuppressLint("RestrictedApi")
    fun publish(context: Context, entries: List<WatchNextEntry>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val resolver = context.contentResolver
        runCatching {
            resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            entries.forEach { entry ->
                val intent =
                    Intent(Intent.ACTION_VIEW, Uri.parse(entry.appUri))
                        .setPackage(context.packageName)
                val program =
                    WatchNextProgram.Builder()
                        .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                        .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setTitle(entry.title)
                        .setInternalProviderId(entry.key)
                        .setIntent(intent)
                        // The app keeps progress as a fraction, so it is expressed against a fixed
                        // nominal length: the launcher only draws the bar, and the proportion is
                        // what the bar shows.
                        .setDurationMillis(NOMINAL_DURATION_MS)
                        .setLastPlaybackPositionMillis((entry.progress * NOMINAL_DURATION_MS).toInt())
                        .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                        .apply { entry.posterUrl?.let { setPosterArtUri(Uri.parse(it)) } }
                        .build()
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            }
        }.onFailure { error ->
            // A launcher without the provider, or one that refuses the write, costs the row and
            // nothing else. Only the type is logged: titles and poster URLs stay private.
            android.util.Log.w("IptvBuroWatchNext", "Watch Next unavailable: ${error.javaClass.simpleName}")
        }
    }
}

private const val MAX_ENTRIES = 10
private const val NOMINAL_DURATION_MS = 1_000_000
