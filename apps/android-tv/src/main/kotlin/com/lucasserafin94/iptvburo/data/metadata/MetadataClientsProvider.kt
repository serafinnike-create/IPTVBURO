package com.lucasserafin94.iptvburo.data.metadata

import com.lucasserafin94.iptvburo.metadata.CriticScoresClient
import com.lucasserafin94.iptvburo.metadata.TmdbClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Builds the optional metadata clients (TMDb, OMDb) on the data layer's behalf.
 *
 * These clients used to be assembled inside `MainViewModel`, which meant the UI layer knew the
 * shape of every network dependency — the shared `OkHttpClient`, the language tag, which key each
 * service takes. That is a data-layer concern, so it lives here now: the view model asks for a
 * ready client and never touches the transport underneath it.
 *
 * A fresh client per call is deliberate and unchanged from before: the clients are thin,
 * stateless wrappers over the shared `OkHttpClient` (connection pool and timeouts included), and
 * each call site may carry a different API key — the user's TMDb key is not the OMDb key.
 */
@Singleton
class MetadataClientsProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /**
     * A TMDb client for [apiKey], speaking the device's current language so synopses and titles
     * come back in the language the interface is showing.
     */
    fun tmdb(
        apiKey: String,
        language: String = Locale.getDefault().toLanguageTag(),
    ): TmdbClient =
        TmdbClient(
            apiKey = apiKey,
            client = okHttpClient,
            language = language,
        )

    /** An OMDb client for [apiKey], used only for the critics' scores row. */
    fun criticScores(apiKey: String): CriticScoresClient =
        CriticScoresClient(apiKey = apiKey, client = okHttpClient)
}
