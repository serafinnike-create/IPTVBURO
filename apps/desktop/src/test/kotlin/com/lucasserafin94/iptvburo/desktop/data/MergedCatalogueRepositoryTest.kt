package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.desktop.model.XtreamCatalogPage
import com.lucasserafin94.iptvburo.desktop.model.XtreamPlaybackTarget
import com.lucasserafin94.iptvburo.desktop.model.XtreamSessionSummary
import com.lucasserafin94.iptvburo.desktop.security.XtreamLoginInput
import com.lucasserafin94.iptvburo.domain.model.ContentIdentity
import com.lucasserafin94.iptvburo.domain.model.LibraryCandidate
import com.lucasserafin94.iptvburo.xtream.XtreamAccount
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import com.lucasserafin94.iptvburo.xtream.XtreamCategory
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.xtream.XtreamShortEpg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two subscriptions browsed as one catalogue.
 *
 * The behaviour asked for: the bigger list leads, the smaller fills the gaps, nothing appears
 * twice, and a list that is down is named without taking the others with it.
 *
 * Driven with fake delegates, because what is worth exercising here is the merging and the paging
 * over it — the session repository already has its own tests for talking to a provider.
 */
class MergedCatalogueRepositoryTest {
    /** One subscription's catalogue, and nothing else. */
    private class FakeSource(
        titles: List<String>,
        private val failOnLoad: Boolean = false,
        private val categoryNames: List<String> = listOf("Filmes"),
        /**
         * Which catalogues this subscription has in memory.
         *
         * Configurable because two lists rarely have loaded the same ones: the merge has to say a
         * type is ready only when every list holds it, and a fake that always claims the same set
         * cannot show the difference.
         */
        private val loaded: Set<XtreamContentType> = setOf(XtreamContentType.MOVIE),
    ) : CatalogueRepository {
        /**
         * Authenticates, then dies when the catalogue is fetched.
         *
         * A different moment from [failOnLoad], and worth its own switch: a subscription that
         * connects and then fails is the one that exercises loadCatalog, and without it that path
         * could throw with nothing noticing.
         */
        var diesWhileLoading = false
        private val items =
            titles.mapIndexed { index, name ->
                XtreamCatalogItem(
                    providerId = index.toString(),
                    name = name,
                    contentType = XtreamContentType.MOVIE,
                    categoryIds = listOf("c1"),
                    containerExtension = "mp4",
                    artworkUrl = null,
                    year = 2026,
                    rating = null,
                    addedAtEpochSeconds = null,
                )
            }

        override fun authenticateAndLoadInitial(
            input: XtreamLoginInput,
            onProgress: (Float, SessionXtreamRepository.XtreamLoadStage) -> Unit,
        ): XtreamSessionSummary {
            if (failOnLoad) throw IllegalStateException("provedor fora do ar")
            return requireNotNull(summary())
        }

        override fun loadCatalog(
            contentType: XtreamContentType,
            forceRefresh: Boolean,
            onProgress: CatalogLoadListener?,
        ): XtreamSessionSummary {
            if (failOnLoad || diesWhileLoading) throw IllegalStateException("provedor fora do ar")
            return requireNotNull(summary())
        }

        override fun categories(contentType: XtreamContentType): List<XtreamCategory> =
            categoryNames.mapIndexed { index, name ->
                XtreamCategory(providerId = "cat$index", name = name, contentType = contentType)
            }

        override fun itemByProviderId(
            contentType: XtreamContentType,
            providerId: String,
        ): XtreamCatalogItem? = items.firstOrNull { it.providerId == providerId }

        override fun itemByContentKey(
            contentType: XtreamContentType,
            contentKey: String,
        ): XtreamCatalogItem? = null

        override fun page(
            contentType: XtreamContentType,
            categoryId: String?,
            query: String,
            requestedPage: Int,
            pageSize: Int,
            releaseYear: Int?,
            minimumRating: Double?,
            allowedIdentities: Set<ContentIdentity>?,
            kidsMode: Boolean,
            lockedCategoryIds: Set<String>,
            collapseDuplicates: Boolean,
            allowedLocalIds: Set<String>?,
        ): XtreamCatalogPage {
            // The real repository refuses anything above two hundred rows. Without that here, a
            // merge asking for thousands at once looked fine in the tests and returned nothing on
            // a real catalogue — which is how the home came up empty with two lists loaded.
            require(pageSize in 1..200) { "Invalid page size." }
            val start = requestedPage * pageSize
            return XtreamCatalogPage(
                items = items.drop(start).take(pageSize),
                pageIndex = requestedPage,
                pageSize = pageSize,
                totalMatches = items.size,
            )
        }

        override fun seriesDetails(seriesId: String) = error("nao usado")

        override fun search(
            query: String,
            limit: Int,
        ): List<XtreamCatalogItem> =
            items.filter { it.name.contains(query, ignoreCase = true) }.take(limit)

        override fun findByTitles(
            normalisedTitles: Set<String>,
            limit: Int,
        ) = emptyList<XtreamCatalogItem>()

        override fun releasesForYear(
            type: XtreamContentType,
            year: Int,
            limit: Int,
            kidsMode: Boolean,
            lockedCategoryIds: Set<String>,
            rotation: Int,
        ) = items.take(limit)

        override fun isAllowedForBrowsing(
            item: XtreamCatalogItem,
            kidsMode: Boolean,
            lockedCategoryIds: Set<String>,
        ) = true

        override fun libraryMatchCandidates(
            kidsMode: Boolean,
            lockedCategoryIds: Set<String>,
            lockedCategoryIdsByContentType: Map<XtreamContentType, Set<String>>,
        ) = emptyList<LibraryCandidate>()

        override fun movieDetails(movieId: String) = error("nao usado")

        override fun shortEpg(streamId: String) = XtreamShortEpg(emptyList(), 0)

        /** A stand-in address, so the fallback has something real to hand back. */
        override fun buildConfirmedPlaybackUri(target: XtreamPlaybackTarget): java.net.URI =
            java.net.URI("http://p.invalid/${target.contentKey}")

        override fun summary(): XtreamSessionSummary =
            XtreamSessionSummary(
                sourceId = "fonte",
                account =
                    XtreamAccount(
                        authenticated = true,
                        status = "Active",
                        isTrial = false,
                        activeConnections = 0,
                        maximumConnections = 1,
                        allowedOutputFormats = setOf("ts"),
                    ),
                loadedItemCount = items.size,
                loadedContentTypes = loaded,
            )

        override fun clearCatalogCache() = Unit

        override fun clearIncludingDiskCache() = Unit

        override fun clear() = Unit
    }

    private fun merged(vararg sources: Pair<String, FakeSource>): MergedCatalogueRepository {
        val queue = sources.map { it.second }.toMutableList()
        val repository = MergedCatalogueRepository(newDelegate = { queue.removeAt(0) })
        sources.forEach { (label, _) ->
            repository.addSource(
                sourceId = label,
                label = label,
                input =
                    XtreamLoginInput(
                        server = "http://p.invalid".toCharArray(),
                        username = "u".toCharArray(),
                        password = "p".toCharArray(),
                    ),
            )
        }
        return repository
    }

    private fun page(
        repository: MergedCatalogueRepository,
        index: Int = 0,
        size: Int = 80,
    ) = repository.page(
        contentType = XtreamContentType.MOVIE,
        categoryId = null,
        query = "",
        requestedPage = index,
        pageSize = size,
    )

    /** The owner's example: a film only the second list has must still appear. */
    @Test
    fun `a title only the smaller list has still appears`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix", "Alien")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        val names = page(repository).items.map { it.name }
        assertTrue("Avatar" in names, "o titulo exclusivo da lista pequena sumiu: $names")
        assertEquals(4, names.size)
    }

    /** And a film both lists have must appear once, from the bigger one. */
    @Test
    fun `a shared title appears once`() {
        val repository =
            merged(
                "pequena" to FakeSource(listOf("Avatar")),
                "grande" to FakeSource(listOf("Duna", "Matrix", "Avatar")),
            )

        val names = page(repository).items.map { it.name }
        assertEquals(3, names.size, "apareceu duas vezes: $names")
        assertEquals(1, names.count { it == "Avatar" })
    }

    /**
     * The hard part: page three of a merged catalogue is not page three of any one subscription.
     */
    @Test
    fun `paging runs over the merged result, not over one source`() {
        val repository =
            merged(
                "grande" to FakeSource((1..10).map { "Filme $it" }),
                "pequena" to FakeSource((11..14).map { "Filme $it" }),
            )

        val first = page(repository, index = 0, size = 5)
        val second = page(repository, index = 1, size = 5)
        val third = page(repository, index = 2, size = 5)

        assertEquals(14, first.totalMatches, "o total tem de ser o do catalogo juntado")
        assertEquals(5, first.items.size)
        assertEquals(4, third.items.size, "a ultima pagina cruza as duas fontes")
        // No title appears on two pages: the cut is over one merged list, not two separate ones.
        val seen = (first.items + second.items + third.items).map { it.name }
        assertEquals(seen.size, seen.toSet().size, "um titulo apareceu em duas paginas")
    }

    /** The whole safety property: one dead list must not blank a working library. */
    @Test
    fun `a source that is down is named and the rest still load`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "caiu" to FakeSource(emptyList(), failOnLoad = true),
            )

        assertEquals(listOf("caiu"), repository.failedSources)
        assertEquals(2, page(repository).items.size, "a lista viva foi perdida")
    }

    /**
     * A list that authenticates and then fails to load must not take the others with it either.
     *
     * Separate from the test above because they fail at different moments: that one never
     * connects, this one connects and then dies when the catalogue is fetched. Only this one
     * exercises loadCatalog, and without it that path could throw and nothing would notice.
     */
    @Test
    fun `a source that dies while loading does not take the others with it`() {
        val dying = FakeSource(listOf("Avatar"))
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "morre" to dying,
            )
        // Only after it has connected, so the failure lands in loadCatalog and nowhere else.
        dying.diesWhileLoading = true

        repository.loadCatalog(XtreamContentType.MOVIE)

        assertEquals(listOf("morre"), repository.failedSources)
        assertEquals(2, page(repository).items.size, "a lista viva foi perdida")
    }

    /**
     * A search reaching only one list is the sharpest form of the original problem: typing a name
     * and being told it is not there when you own it.
     */
    @Test
    fun `search reaches every subscription`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        assertEquals(1, repository.search("avatar", limit = 20).size)
    }

    /** The header count has to be the merged one, or two lists look like half a library. */
    @Test
    fun `the summary counts every subscription`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix", "Alien")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        assertEquals(4, repository.summary()?.loadedItemCount)
    }

    /** Two lists carrying the same category must not show it twice. */
    @Test
    fun `a category both lists carry is listed once`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna"), categoryNames = listOf("Filmes", "Series")),
                "pequena" to FakeSource(listOf("Avatar"), categoryNames = listOf("Filmes")),
            )

        val names = repository.categories(XtreamContentType.MOVIE).map { it.name }
        assertEquals(listOf("Filmes", "Series"), names)
    }

    /** One subscription merges to itself, and must not pay for a merge pass. */
    @Test
    fun `a single subscription behaves exactly as before`() {
        val repository = merged("so uma" to FakeSource(listOf("Duna", "Matrix")))

        assertEquals(2, page(repository).items.size)
        assertTrue(!repository.isMerging)
    }

    /**
     * Connecting a second subscription must not close the first.
     *
     * It did: the list was cleared before the new one was added, so the viewer watched one library
     * vanish as another arrived. Reported exactly that way — "a primeira fonte fechou e abriu a
     * segunda". The whole point of this repository is that subscriptions accumulate.
     */
    @Test
    fun `connecting a second subscription keeps the first`() {
        val queue = mutableListOf(FakeSource(listOf("Duna", "Matrix")), FakeSource(listOf("Avatar")))
        val repository = MergedCatalogueRepository(newDelegate = { queue.removeAt(0) })

        repeat(2) {
            repository.authenticateAndLoadInitial(
                input =
                    XtreamLoginInput(
                        server = "http://p.invalid".toCharArray(),
                        username = "u".toCharArray(),
                        password = "p".toCharArray(),
                    ),
            ) { _, _ -> }
        }

        assertTrue(repository.isMerging, "a segunda ligacao fechou a primeira")
        assertEquals(3, page(repository).items.size, "uma das listas desapareceu")
    }

    /**
     * A stream that fails must be able to fall back to another list's copy.
     *
     * Half the value of owning a second subscription is that a dead stream is not the end of the
     * evening, and the viewer should never have to know a swap happened.
     */
    @Test
    fun `a failed stream can be asked for from the next list`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "pequena" to FakeSource(listOf("Duna")),
            )
        val target =
            XtreamPlaybackTarget.CatalogItem(
                providerId = "0",
                contentType = XtreamContentType.MOVIE,
                containerExtension = "mp4",
                contentKey = "duna",
            )

        // Skipping none is the first list; skipping one has to reach the second.
        assertNotNull(repository.buildAlternativePlaybackUri(target, exclude = 0))
        assertNotNull(repository.buildAlternativePlaybackUri(target, exclude = 1))
        // And once every list has been tried there is nothing left to offer, which is when the
        // viewer genuinely has to be told rather than kept waiting.
        assertEquals(null, repository.buildAlternativePlaybackUri(target, exclude = 2))
    }

    /**
     * The home picks a page at random from the count this reports, so the two must agree.
     *
     * With two lists loaded the home came up empty — movies, series and live all zero — while the
     * header counted a hundred thousand items. A page index taken from a count that does not match
     * what can be served lands on nothing.
     */
    @Test
    fun `every page the reported count promises actually has items`() {
        val repository =
            merged(
                "grande" to FakeSource((1..300).map { "Filme $it" }),
                "pequena" to FakeSource((301..420).map { "Filme $it" }),
            )

        val first = page(repository, index = 0, size = 80)
        val pageCount = first.pageCount
        assertTrue(pageCount > 1, "sem paginas para percorrer")
        // Every one of them, not just the first: the home asks for whichever the day's seed picks.
        (0 until pageCount).forEach { index ->
            assertTrue(
                page(repository, index = index, size = 80).items.isNotEmpty(),
                "a pagina $index de $pageCount veio vazia",
            )
        }
    }

    /** The cap the owner named. */
    @Test
    fun `no more than ten subscriptions are accepted`() {
        val sources = (1..12).map { index -> "fonte$index" to FakeSource(listOf("Filme $index")) }
        val repository = merged(*sources.toTypedArray())

        assertEquals(10, page(repository).items.size, "aceitou mais do que dez fontes")
    }

    /**
     * A title the second list contributed is not in the first, so opening it must not look only
     * there — that would fail to open something the grid had just shown.
     */
    @Test
    fun `a title is found in whichever list holds it`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix", "Alien")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        assertNotNull(repository.itemByProviderId(XtreamContentType.MOVIE, "0"))
    }

    /**
     * Every subscription is reported, so the sidebar can show one row each.
     *
     * The sidebar listed a single row for the most recent session, which with a merge open meant
     * two lists looked like one — and the switch that turns merging on hides itself below two
     * sources, so it became unreachable exactly when it was wanted. Reported as a second list that
     * closed the first, with no switch anywhere on screen.
     */
    @Test
    fun `every subscription is reported for the sidebar`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        assertEquals(
            listOf("grande", "pequena"),
            repository.heldSources.map { it.label },
            "a barra lateral nao veria as duas listas",
        )
    }

    /**
     * Including one that is down.
     *
     * A failed subscription still belongs on screen: dropping it would leave the viewer unable to
     * tell a list that stopped answering from one they never added.
     */
    @Test
    fun `a subscription that is down is still reported`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna")),
                "avariada" to FakeSource(listOf("Avatar"), failOnLoad = true),
            )

        val down = repository.heldSources.filter { !it.isWorking }.map { it.label }
        assertEquals(listOf("avariada"), down, "a lista avariada desapareceu da barra lateral")
        assertEquals(2, repository.heldSources.size, "a barra lateral perdeu uma linha")
    }

    /**
     * A forgotten subscription leaves the merge at once.
     *
     * Clearing the stored password while the loaded catalogue stayed in the merge left the row on
     * screen until the app restarted, which is the same "it cannot be deleted" that was reported.
     */
    @Test
    fun `a forgotten subscription leaves the merge`() {
        val repository =
            merged(
                "grande" to FakeSource(listOf("Duna", "Matrix")),
                "pequena" to FakeSource(listOf("Avatar")),
            )

        assertTrue(repository.removeSource("pequena"), "nao encontrou a lista a esquecer")

        assertEquals(
            listOf("grande"),
            repository.heldSources.map { it.label },
            "a lista esquecida continua na barra lateral",
        )
        assertTrue(
            page(repository).items.none { it.name == "Avatar" },
            "o catalogo ainda mostra titulos da lista esquecida",
        )
    }

    /**
     * Two large lists together hold more than either alone.
     *
     * The merge used to read only the first five thousand matching rows of each subscription, so
     * two lists of tens of thousands showed fewer films than one — reported exactly that way:
     * "serie tinha menos que uma lista filmes tbm".
     */
    @Test
    fun `two large lists hold more than either alone`() {
        val big = (1..8_000).map { "Filme grande numero$it" }
        val small = (1..6_000).map { "Filme pequeno numero$it" }
        val repository =
            merged(
                "grande" to FakeSource(big),
                "pequena" to FakeSource(small),
            )

        val total = page(repository).totalMatches

        assertTrue(
            total >= big.size + small.size,
            "juntas mostram $total titulos, menos do que as ${big.size + small.size} que existem",
        )
    }

    /**
     * And the last page of a large merge is reachable.
     *
     * A total the paging cannot actually serve hands back a page index with nothing behind it,
     * which is how a catalogue ends up showing an empty final screen.
     */
    @Test
    fun `the last page of a large merge is not empty`() {
        val repository =
            merged(
                "grande" to FakeSource((1..8_000).map { "Filme grande numero$it" }),
                "pequena" to FakeSource((1..6_000).map { "Filme pequeno numero$it" }),
            )

        val first = page(repository)
        val last = page(repository, index = first.pageCount - 1)

        assertTrue(last.items.isNotEmpty(), "a ultima pagina do catalogo juntado veio vazia")
    }

    /**
     * A type is loaded only when every subscription holds it.
     *
     * The union said a type was ready as soon as one list had it, so the caller skipped the fetch
     * and the other lists never loaded it — and with none of them holding films, the Films tab
     * paged over whatever they did hold and filled with live channels. Reported as "clikei em
     * filmes e abriu aovico".
     */
    @Test
    fun `a content type is loaded only when every list holds it`() {
        val repository =
            merged(
                "grande" to FakeSource(
                    listOf("Duna"),
                    loaded = setOf(XtreamContentType.MOVIE, XtreamContentType.LIVE),
                ),
                "pequena" to FakeSource(
                    listOf("Avatar"),
                    loaded = setOf(XtreamContentType.LIVE),
                ),
            )

        val loaded = repository.summary()?.loadedContentTypes.orEmpty()

        assertTrue(
            XtreamContentType.LIVE in loaded,
            "ao vivo esta nas duas listas e devia contar como carregado",
        )
        assertTrue(
            XtreamContentType.MOVIE !in loaded,
            "filmes so esta numa lista, mas o conjunto diz que esta carregado: $loaded",
        )
    }

    /**
     * The merge is bounded, so it cannot take the whole heap.
     *
     * A merged view holds a built item per title across every list at once, and the app runs in a
     * 768 MB heap. Reading without a ceiling killed the process with nothing written to the log —
     * the columnar catalogue exists precisely so a whole list is not held as objects, and merging
     * is the one path that has to build them.
     */
    @Test
    fun `a merged view is bounded`() {
        assertTrue(
            MergedCatalogueRepository.MERGE_ITEMS_TOTAL in 20_000..120_000,
            "o limite total saiu do intervalo que cabe na memoria da aplicacao",
        )
        assertTrue(
            MergedCatalogueRepository.MERGE_ITEMS_PER_SOURCE <= MergedCatalogueRepository.MERGE_ITEMS_TOTAL,
            "uma so lista pode encher o orcamento inteiro",
        )
    }

    /** And forgetting one that was never held changes nothing. */
    @Test
    fun `forgetting an unknown subscription is harmless`() {
        val repository = merged("grande" to FakeSource(listOf("Duna")))

        assertTrue(!repository.removeSource("inexistente"), "afirmou ter esquecido o que nao tinha")
        assertEquals(1, repository.heldSources.size, "perdeu uma lista que devia ter ficado")
    }
}
