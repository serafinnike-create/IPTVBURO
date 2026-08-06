package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.ExternalContentId
import com.lucasserafin94.iptvburo.domain.model.ExternalLaunchTarget
import com.lucasserafin94.iptvburo.domain.model.ExternalTitle
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleDetails
import com.lucasserafin94.iptvburo.domain.model.ExternalTitleKind
import com.lucasserafin94.iptvburo.domain.model.OfferType
import com.lucasserafin94.iptvburo.domain.model.Price
import com.lucasserafin94.iptvburo.domain.model.StreamingDiscoveryProvider
import com.lucasserafin94.iptvburo.domain.model.StreamingOffer
import com.lucasserafin94.iptvburo.domain.model.StreamingProvider
import com.lucasserafin94.iptvburo.domain.model.USER_LIBRARY_PROVIDER_ID

/**
 * A placeholder catalogue so the Assinaturas screen can be seen before a real source is connected.
 *
 * **Everything here is invented.** No request is made, nothing is verified, and no service has been
 * consulted. The listings exist to show the layout and the ranking working, nothing else.
 *
 * The service names are real ones because the screen has to be judged as it will look, and generic
 * placeholders make the ranking impossible to read at a glance. That makes the DEMO marking do real
 * work rather than being decoration: [ExternalTitle.isDemo] is true on every title,
 * `StreamingDiscoveryCapability.DEMO_ONLY` forces a badge onto every row, and the screen carries a
 * notice saying plainly that nothing real is connected. Those three must stay together — a build
 * that reaches users showing these names *without* the marking would be asserting availability that
 * was never checked, about companies that never agreed to it.
 *
 * No logos. GDD 9 section 10 forbids copying brand artwork, so providers are text only, and the
 * destinations are the services' ordinary public homepages — no deep links, because a made-up deep
 * link to a real service would 404 in the user's browser.
 *
 * This class is deleted, not extended, once a real provider answers.
 */
class DemoStreamingDiscoveryProvider(
    private val titles: List<ExternalTitleDetails> = DEMO_CATALOGUE,
) : StreamingDiscoveryProvider {
    override suspend fun search(
        query: String,
        limit: Int,
    ): List<ExternalTitle> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return titles
            .map(ExternalTitleDetails::title)
            .filter { it.title.contains(needle, ignoreCase = true) }
            .take(limit)
    }

    override suspend fun details(id: ExternalContentId): ExternalTitleDetails? = titles.firstOrNull { it.title.id == id }

    /** Empty for an unknown id: "nothing known" is an ordinary answer, not an error. */
    override suspend fun offers(id: ExternalContentId): List<StreamingOffer> = details(id)?.offers.orEmpty()

    override suspend fun upcoming(limit: Int): List<ExternalTitle> =
        titles
            .map(ExternalTitleDetails::title)
            .filter { title -> (title.year ?: 0) >= DEMO_YEAR }
            .take(limit)

    companion object {
        const val DEMO_NAMESPACE: String = "demo"

        /** Fixed rather than read from the clock, so the demo never changes under the user. */
        const val DEMO_YEAR: Int = 2026

        private val YOUR_LIST = StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "Sua lista")

        // Public homepages only. A fabricated per-title path would send the user to a 404 on a real
        // company's site.
        private val NETFLIX = StreamingProvider.of("netflix", "Netflix")
        private val PRIME_VIDEO = StreamingProvider.of("prime-video", "Prime Video")
        private val DISNEY_PLUS = StreamingProvider.of("disney-plus", "Disney+")
        private val APPLE_TV = StreamingProvider.of("apple-tv", "Apple TV")
        private val GOOGLE_PLAY = StreamingProvider.of("google-play", "Google Play Filmes")

        private fun homepage(url: String) = ExternalLaunchTarget(webUrl = url)

        private val NETFLIX_HOME = homepage("https://www.netflix.com/")
        private val PRIME_HOME = homepage("https://www.primevideo.com/")
        private val DISNEY_HOME = homepage("https://www.disneyplus.com/")
        private val APPLE_HOME = homepage("https://tv.apple.com/")
        private val PLAY_HOME = homepage("https://play.google.com/store/movies")

        private fun demoTitle(
            id: String,
            title: String,
            year: Int?,
            kind: ExternalTitleKind = ExternalTitleKind.MOVIE,
        ) = ExternalTitle(
            id = ExternalContentId(DEMO_NAMESPACE, id),
            title = title,
            kind = kind,
            year = year,
            isDemo = true,
        )

        /**
         * A catalogue covering every offer type at least once, so the ranking can be seen working.
         *
         * The titles are invented on purpose even though the services are not: attaching a made-up
         * availability answer to a real film is a claim someone could act on and find wrong, whereas
         * an obviously fictional title cannot mislead anyone about a real one.
         */
        val DEMO_CATALOGUE: List<ExternalTitleDetails> =
            listOf(
                ExternalTitleDetails(
                    title = demoTitle("1", "Exemplo: O Filme", year = 2021),
                    overview = "Título de exemplo. Esta disponibilidade é fictícia.",
                    runtimeMinutes = 118,
                    genres = listOf("Demonstração"),
                    offers =
                        listOf(
                            // Deliberately unordered here so the screen's ordering is doing the work
                            // rather than the fixture's.
                            StreamingOffer(
                                provider = GOOGLE_PLAY,
                                type = OfferType.BUY,
                                price = Price(3990, "BRL"),
                                launchTarget = PLAY_HOME,
                            ),
                            StreamingOffer(
                                provider = APPLE_TV,
                                type = OfferType.RENT,
                                price = Price(1490, "BRL"),
                                launchTarget = APPLE_HOME,
                            ),
                            StreamingOffer(
                                provider = NETFLIX,
                                type = OfferType.SUBSCRIPTION,
                                launchTarget = NETFLIX_HOME,
                            ),
                            StreamingOffer(
                                provider = GOOGLE_PLAY,
                                type = OfferType.RENT,
                                price = Price(990, "BRL"),
                                launchTarget = PLAY_HOME,
                            ),
                            StreamingOffer(
                                provider = DISNEY_PLUS,
                                type = OfferType.SUBSCRIPTION,
                                launchTarget = DISNEY_HOME,
                            ),
                        ),
                ),
                ExternalTitleDetails(
                    title = demoTitle("2", "Exemplo: A Série", year = 2019, kind = ExternalTitleKind.SERIES),
                    overview = "Série de exemplo. Esta disponibilidade é fictícia.",
                    genres = listOf("Demonstração"),
                    offers =
                        listOf(
                            StreamingOffer(
                                provider = PRIME_VIDEO,
                                type = OfferType.FREE_WITH_ADS,
                                launchTarget = PRIME_HOME,
                            ),
                            // The user's own list outranks everything and carries no price — the one
                            // rule most worth seeing demonstrated on screen.
                            StreamingOffer(provider = YOUR_LIST, type = OfferType.USER_LIBRARY),
                            StreamingOffer(
                                provider = NETFLIX,
                                type = OfferType.SUBSCRIPTION,
                                launchTarget = NETFLIX_HOME,
                            ),
                        ),
                ),
                ExternalTitleDetails(
                    title = demoTitle("3", "Exemplo: Em Breve", year = DEMO_YEAR + 2),
                    overview = "Ainda sem lançamento. Exemplo fictício.",
                    genres = listOf("Demonstração"),
                    offers = listOf(StreamingOffer(provider = NETFLIX, type = OfferType.UNAVAILABLE)),
                ),
            )
    }
}
