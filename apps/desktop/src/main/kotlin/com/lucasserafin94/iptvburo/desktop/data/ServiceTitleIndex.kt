package com.lucasserafin94.iptvburo.desktop.data

import com.lucasserafin94.iptvburo.domain.model.normalisedForMatching

/**
 * Which of the user's own titles belong to which streaming service.
 *
 * ## The problem this exists for
 *
 * The Serviço selector reads the playlist's category names: a list that files films under
 * "Filmes | Netflix" gets a working selector, and one that files them under "Filmes | Ação" gets
 * nothing. The second is the common case — a real list's 31 film categories were all genres — so the
 * selector had to say "não informado na sua lista", which is true and useless. The service a film
 * came from is knowable; it is just not in the playlist.
 *
 * ## Why the lookup runs backwards
 *
 * The obvious approach — ask TMDb which services carry each of the user's films — costs two requests
 * per title: one to resolve the name to a TMDb id, one to read its providers. On a catalogue of
 * 42,095 items that is tens of thousands of requests, which TMDb would rate-limit long before it
 * finished and which would take minutes of continuous network even if it did not.
 *
 * So the question is asked the other way round: **what does each service carry?** TMDb answers that
 * per provider, twenty titles a page, so the whole index costs a handful of requests per service
 * rather than two per film. The titles that come back are then matched against the library by the
 * same normalised-name rule the "já está na sua lista" row uses.
 *
 * ## What this means for the user
 *
 * The index covers what TMDb lists as popular on each service, not every film in existence. A
 * playlist title that TMDb does not associate with any service simply is not in the index, and
 * filtering by Netflix will not show it. That is a real limit and the screen must not pretend
 * otherwise — an incomplete filter presented as complete is worse than none.
 */
class ServiceTitleIndex(
    /**
     * Library ids by service label — "Netflix", "Prime Video" — as [ProviderIdentity] names them.
     *
     * The id is `localContentId`, the "MOVIE:1234" form the rest of the app already uses to open a
     * title, so a caller can filter the catalogue by it without another lookup.
     */
    private val byService: Map<String, Set<String>>,
) {
    /** The services this index knows anything about, in the order they were built. */
    val services: List<String> get() = byService.keys.toList()

    /** Whether the index found anything at all. Empty means the filter must not be offered. */
    val isEmpty: Boolean get() = byService.isEmpty()

    /** How many library titles were matched to [service]. Shown so the user can judge the coverage. */
    fun countFor(service: String): Int = byService[service]?.size ?: 0

    /** The library ids carried by [service], or empty when it is not in the index. */
    fun idsFor(service: String): Set<String> = byService[service].orEmpty()

    companion object {
        val EMPTY = ServiceTitleIndex(emptyMap())

        /**
         * Builds an index from what each service carries and what the library holds.
         *
         * [serviceTitles] is the TMDb answer — service label to the titles it lists. [library] is the
         * user's own catalogue as name/year pairs. Matching is on the normalised name plus the year,
         * which is the same rule the details page uses to decide "already in your list".
         *
         * Year is required on both sides rather than treated as optional. Without it "Dune" matches
         * the 1984 film and the 2021 one alike, and a filter that quietly conflates two different
         * films is worse than one that misses one of them.
         */
        fun build(
            serviceTitles: Map<String, List<Pair<String, Int?>>>,
            library: List<Triple<String, Int?, String>>,
        ): ServiceTitleIndex {
            if (serviceTitles.isEmpty() || library.isEmpty()) return EMPTY

            // Indexed once by normalised name, so each service's titles are a map lookup rather than
            // a scan of the whole library. The library is tens of thousands of rows and there are
            // several services; the nested-loop version is millions of comparisons.
            val libraryByName = HashMap<String, MutableList<Pair<Int?, String>>>()
            library.forEach { (name, year, identity) ->
                val key = name.normalisedForMatching()
                if (key.isNotBlank()) {
                    libraryByName.getOrPut(key) { mutableListOf() } += year to identity
                }
            }

            val result = LinkedHashMap<String, MutableSet<String>>()
            serviceTitles.forEach { (service, titles) ->
                titles.forEach { (title, year) ->
                    val candidates = libraryByName[title.normalisedForMatching()] ?: return@forEach
                    candidates.forEach { (libraryYear, identity) ->
                        // Both years present and equal, or the title is skipped. See the note above.
                        if (year != null && libraryYear == year) {
                            result.getOrPut(service) { linkedSetOf() } += identity
                        }
                    }
                }
            }
            return ServiceTitleIndex(result.mapValues { (_, set) -> set.toSet() })
        }
    }
}
