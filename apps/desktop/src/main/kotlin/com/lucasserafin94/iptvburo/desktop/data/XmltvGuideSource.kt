package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.playlist.XmltvParser
import com.lucasserafin94.iptvburo.playlist.XmltvProgramme
import com.lucasserafin94.iptvburo.xtream.XtreamEpgProgram
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import java.io.IOException
import java.time.Duration
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The programme guide for a playlist that brought its own.
 *
 * An M3U list names its guide in `url-tvg` and identifies each channel with a `tvg-id`; those two
 * are the whole mechanism. This fetches the file once, indexes it by channel id, and answers from
 * memory afterwards, because the alternative — re-reading tens of megabytes each time a channel is
 * opened — would make the guide unusable.
 *
 * The result is shaped as [XtreamShortEpg] deliberately. The guide screen, its now-and-next, and
 * catch-up were all built against that type and none of them care where a programme came from, so
 * matching it here means an M3U source lights up the existing screen rather than needing a second
 * one.
 */
class XmltvGuideSource(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            // A guide is large and often served from the same slow host as the playlist.
            .readTimeout(Duration.ofMinutes(3))
            .build(),
) {
    /**
     * Programmes by channel id, each list in broadcast order.
     *
     * Held whole: a guide of a few hundred thousand programmes is tens of megabytes of strings,
     * which is affordable once and unaffordable per channel opened.
     */
    @Volatile
    private var programmesByChannel: Map<String, List<XtreamEpgProgram>> = emptyMap()

    @Volatile
    private var loadedFrom: String? = null

    /** How many programmes the last load produced, for the diagnostics panel. */
    @Volatile
    var loadedProgrammeCount: Int = 0
        private set

    /** Whether a guide has been loaded and has anything in it. */
    val isLoaded: Boolean
        get() = programmesByChannel.isNotEmpty()

    /**
     * Fetches and indexes the guide at the first address that yields one.
     *
     * A playlist may name several: they are alternates, not parts of a set, so the first that works
     * wins and the rest are not fetched. A guide that is merely unreachable is not an error worth
     * surfacing — the channels still play, and the schedule is an enhancement — so this reports
     * failure by returning false rather than by throwing.
     *
     * @return true when a guide was loaded and contained at least one programme.
     */
    fun load(epgUrls: List<String>): Boolean {
        for (url in epgUrls.take(MAX_CANDIDATE_URLS)) {
            val indexed = runCatching { fetchAndIndex(url) }.getOrNull()
            if (!indexed.isNullOrEmpty()) {
                programmesByChannel = indexed
                loadedFrom = url
                loadedProgrammeCount = indexed.values.sumOf { it.size }
                return true
            }
        }
        return false
    }

    /** Drops the guide, so a source that is removed does not keep its schedule in memory. */
    fun clear() {
        programmesByChannel = emptyMap()
        loadedFrom = null
        loadedProgrammeCount = 0
    }

    /**
     * The schedule for one channel, in the shape the guide screen already reads.
     *
     * Matching is case-insensitive and trimmed. Playlists and guides are frequently produced by
     * different tools from the same data, and a difference of case in an id is common enough that
     * honouring it exactly would leave channels blank for no reason a viewer could act on.
     */
    fun shortEpg(tvgId: String?): XtreamShortEpg {
        val key = tvgId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (key.isEmpty()) return XtreamShortEpg(programs = emptyList(), skippedProgramCount = 0)
        return XtreamShortEpg(
            programs = programmesByChannel[key].orEmpty(),
            skippedProgramCount = 0,
        )
    }

    private fun fetchAndIndex(url: String): Map<String, List<XtreamEpgProgram>> {
        val request =
            Request.Builder()
                .url(url)
                // Asked for explicitly because guides are large and nearly always served
                // compressed. OkHttp adds this itself, but only when it may also strip the header
                // from the response — and here the parser sniffs the bytes, so being explicit
                // keeps the two halves consistent.
                .header("Accept-Encoding", "gzip")
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                // The status only. A guide URL can carry a token, and this message is surfaced.
                throw IOException("The guide server answered ${response.code}.")
            }
            val body = response.body ?: throw IOException("The guide server sent no content.")

            val collected = HashMap<String, MutableList<XtreamEpgProgram>>()
            XmltvParser.parse(body.byteStream()) { programme ->
                collected
                    .getOrPut(programme.channelId.lowercase(Locale.ROOT)) { mutableListOf() }
                    .add(programme.toXtreamProgram())
            }
            // Sorted once here rather than on every channel opened. A guide is usually already in
            // order, but "usually" would show up as a scrambled evening on the ones that are not.
            return collected.mapValues { (_, programmes) ->
                programmes.sortedBy { it.startEpochSeconds ?: Long.MAX_VALUE }
            }
        }
    }

    private fun XmltvProgramme.toXtreamProgram(): XtreamEpgProgram =
        XtreamEpgProgram(
            title = title,
            description = description,
            startEpochSeconds = startEpochSeconds,
            endEpochSeconds = endEpochSeconds,
        )

    /** Never the URL: a guide address can carry a token. */
    override fun toString(): String =
        "XmltvGuideSource(loaded=$isLoaded, programmes=$loadedProgrammeCount)"

    private companion object {
        /**
         * Alternates, tried in order. A playlist listing more than a few is not offering choices,
         * and trying all of them would stall the guide behind a run of timeouts.
         */
        const val MAX_CANDIDATE_URLS = 3
    }
}
