package com.lucasserafin94.iptvburo.domain.model

/**
 * A synthetic [StreamingDiscoveryProvider] holding invented titles. **Not a real catalogue.**
 *
 * It lives in `src/test` rather than `src/main` so it cannot be wired into a shipping binary by
 * accident, and every title it returns carries [ExternalTitle.isDemo] `true` so that even if one
 * escaped into a UI it would be labelled rather than presented as genuine availability.
 *
 * Names, providers and titles here are deliberately generic — "Provider One", "Demo" — because the
 * repository is public and fixtures naming real services would imply relationships that do not
 * exist. Every URL uses `example.invalid`, which RFC 2606 reserves so it can never resolve.
 */
class FakeStreamingDiscoveryProvider(
    private val titles: List<ExternalTitleDetails> = DEMO_CATALOGUE,
) : StreamingDiscoveryProvider {
    /** Naive substring match — enough to exercise the contract, not a search implementation. */
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

    /** Empty for an unknown id: "nothing known" is a normal answer, not an error. */
    override suspend fun offers(id: ExternalContentId): List<StreamingOffer> = details(id)?.offers.orEmpty()

    override suspend fun upcoming(limit: Int): List<ExternalTitle> =
        titles
            .map(ExternalTitleDetails::title)
            .filter { title -> title.year != null && title.year > DEMO_CURRENT_YEAR }
            .take(limit)

    companion object {
        /** Fixed so the fixture never depends on the wall clock. */
        const val DEMO_CURRENT_YEAR: Int = 2026

        const val DEMO_NAMESPACE: String = "demo"

        val USER_LIBRARY: StreamingProvider =
            StreamingProvider.of(USER_LIBRARY_PROVIDER_ID, "Your list")

        val PROVIDER_ONE: StreamingProvider = StreamingProvider.of("demo-provider-one", "Provider One")
        val PROVIDER_TWO: StreamingProvider = StreamingProvider.of("demo-provider-two", "Provider Two")
        val STORE_ONE: StreamingProvider = StreamingProvider.of("demo-store-one", "Store One")
        val STORE_TWO: StreamingProvider = StreamingProvider.of("demo-store-two", "Store Two")

        private fun demoTitle(
            id: String,
            title: String,
            kind: ExternalTitleKind = ExternalTitleKind.MOVIE,
            year: Int? = 2021,
        ) = ExternalTitle(
            id = ExternalContentId(DEMO_NAMESPACE, id),
            title = title,
            kind = kind,
            year = year,
            isDemo = true,
        )

        private fun demoTarget(
            provider: StreamingProvider,
            path: String,
        ) = ExternalLaunchTarget(
            providerId = provider.id,
            webUrl = "https://example.invalid/$path",
        )

        /** A small catalogue covering each offer type at least once. */
        val DEMO_CATALOGUE: List<ExternalTitleDetails> =
            listOf(
                ExternalTitleDetails(
                    title = demoTitle("1", "Demo Film One"),
                    overview = "A synthetic entry used only by tests.",
                    runtimeMinutes = 101,
                    genres = listOf("Demo"),
                    offers =
                        listOf(
                            StreamingOffer(
                                provider = PROVIDER_ONE,
                                type = OfferType.SUBSCRIPTION,
                                launchTarget = demoTarget(PROVIDER_ONE, "one"),
                            ),
                            StreamingOffer(
                                provider = STORE_ONE,
                                type = OfferType.RENT,
                                price = Price(499, "EUR"),
                                launchTarget = demoTarget(STORE_ONE, "rent-one"),
                            ),
                            StreamingOffer(
                                provider = STORE_TWO,
                                type = OfferType.BUY,
                                price = Price(1299, "EUR"),
                                launchTarget = demoTarget(STORE_TWO, "buy-two"),
                            ),
                        ),
                ),
                ExternalTitleDetails(
                    title = demoTitle("2", "Demo Series Two", kind = ExternalTitleKind.SERIES, year = 2019),
                    overview = "Another synthetic entry.",
                    offers =
                        listOf(
                            StreamingOffer(provider = USER_LIBRARY, type = OfferType.USER_LIBRARY),
                            StreamingOffer(
                                provider = PROVIDER_TWO,
                                type = OfferType.FREE_WITH_ADS,
                                launchTarget = demoTarget(PROVIDER_TWO, "free-two"),
                            ),
                        ),
                ),
                ExternalTitleDetails(
                    title = demoTitle("3", "Demo Film Three", year = 2028),
                    overview = "A synthetic unreleased entry.",
                    offers = listOf(StreamingOffer(provider = PROVIDER_ONE, type = OfferType.UNAVAILABLE)),
                ),
            )
    }
}
