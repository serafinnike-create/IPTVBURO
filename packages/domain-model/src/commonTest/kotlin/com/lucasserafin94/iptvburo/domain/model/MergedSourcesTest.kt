package com.lucasserafin94.iptvburo.domain.model

import com.lucasserafin94.iptvburo.domain.model.MergedSources.Contribution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two subscriptions shown as one catalogue.
 *
 * The behaviour asked for, in the owner's words: the bigger list leads, the smaller ones fill the
 * gaps, nothing appears twice, and a list that is down is named without taking the others with it.
 */
class MergedSourcesTest {
    private fun source(
        id: String,
        vararg titles: String,
        failure: String? = null,
    ) = Contribution(sourceId = id, label = id, items = titles.toList(), failure = failure)

    private fun merge(vararg sources: Contribution<String>) =
        MergedSources.merge(sources.toList()) { title -> title.shelfDeduplicationKey() }

    /** "se em uma fonte tem avatar na outra nao, vai aparecer avatar na tela normal". */
    @Test
    fun `a title only the smaller list has still appears`() {
        val merged =
            merge(
                source("grande", "Duna", "Matrix", "Alien"),
                source("pequena", "Avatar"),
            )

        assertTrue("Avatar" in merged.items, "o titulo exclusivo da lista pequena sumiu")
        assertEquals(4, merged.items.size)
    }

    /** "agora se lista tiver avatar nas duas vai aparecer apenas da lista maior". */
    @Test
    fun `a title both lists have is kept once, from the bigger list`() {
        val merged =
            merge(
                source("pequena", "Avatar"),
                source("grande", "Duna", "Matrix", "Avatar"),
            )

        assertEquals(3, merged.items.size, "apareceu duas vezes")
        // The larger source ran first, so its three titles are all its own.
        assertEquals(3, merged.contributed["grande"])
        assertEquals(0, merged.contributed["pequena"])
    }

    /**
     * Provider decoration must not defeat the match.
     *
     * The same film arrives as "Duna 4K [DUB]" from one provider and "Duna" from another; treating
     * those as two films would show it twice, which is the thing this exists to stop.
     */
    @Test
    fun `the same film under different provider decoration is one title`() {
        val merged =
            merge(
                source("grande", "Duna 4K [DUB]", "Matrix"),
                source("pequena", "Duna"),
            )

        assertEquals(2, merged.items.size, "a decoracao do provedor enganou a comparacao")
        assertTrue("Duna 4K [DUB]" in merged.items, "ficou a copia da lista pequena")
    }

    /**
     * The whole safety property: one dead list must not blank a working library.
     */
    @Test
    fun `a source that is down is named and the rest still load`() {
        val merged =
            merge(
                source("grande", "Duna", "Matrix"),
                source("caiu", failure = "auth_failed"),
            )

        assertEquals(2, merged.items.size, "a lista viva foi perdida")
        assertTrue(merged.hasFailures)
        assertEquals("caiu", merged.failed.single().sourceId)
        assertEquals("auth_failed", merged.failed.single().failure)
    }

    @Test
    fun `every source working means nothing to report`() {
        assertFalse(merge(source("a", "Duna"), source("b", "Matrix")).hasFailures)
    }

    /**
     * The order must not depend on which request finished first.
     *
     * Two lists of the same size would otherwise swap places between loads, and the same film would
     * play from a different provider on different days for no reason the viewer can see.
     */
    @Test
    fun `two lists of equal size merge in a stable order`() {
        val first = merge(source("bbb", "Duna"), source("aaa", "Duna"))
        val second = merge(source("aaa", "Duna"), source("bbb", "Duna"))

        assertEquals(first.contributed, second.contributed)
        assertEquals(1, first.contributed["aaa"], "o desempate nao foi pelo id")
    }

    /** Losing a title is worse than showing it twice, so an unmatchable one is kept. */
    @Test
    fun `a title with no comparable name is kept rather than dropped`() {
        val merged = merge(source("a", "", ""), source("b", "Duna"))

        assertEquals(3, merged.items.size)
    }

    /**
     * A stream that fails must be able to fall back to the other list's copy.
     *
     * That is half the value of owning a second subscription, and the viewer should never have to
     * know it happened — so every source carrying a title is remembered, best first.
     */
    @Test
    fun `a title in two lists remembers both, biggest first`() {
        val merged =
            merge(
                source("pequena", "Avatar"),
                source("grande", "Duna", "Matrix", "Avatar"),
            )

        assertEquals(
            listOf("grande", "pequena"),
            merged.sourcesFor("Avatar".shelfDeduplicationKey()),
            "a ordem de recuo tem de comecar pela maior",
        )
    }

    @Test
    fun `a title only one list has has only that one to fall back on`() {
        val merged = merge(source("grande", "Duna", "Matrix"), source("pequena", "Avatar"))

        assertEquals(listOf("pequena"), merged.sourcesFor("Avatar".shelfDeduplicationKey()))
        assertEquals(listOf("grande"), merged.sourcesFor("Duna".shelfDeduplicationKey()))
    }

    /** Provider decoration must not split one film into two fallback chains. */
    @Test
    fun `decoration does not break the fallback chain`() {
        val merged =
            merge(
                source("grande", "Duna 4K [DUB]", "Matrix"),
                source("pequena", "Duna"),
            )

        assertEquals(
            listOf("grande", "pequena"),
            merged.sourcesFor("Duna".shelfDeduplicationKey()),
        )
    }

    @Test
    fun `merging is skipped when there is nothing to merge`() {
        assertFalse(MergedSources.isWorthMerging(listOf(source("a", "Duna"))))
        assertFalse(
            MergedSources.isWorthMerging(listOf(source("a", "Duna"), source("b", failure = "down"))),
            "uma fonte viva e uma morta nao e uma juncao",
        )
        assertTrue(MergedSources.isWorthMerging(listOf(source("a", "Duna"), source("b", "Matrix"))))
    }

    /** "user pode fazer isso com ate 10 fontes". */
    @Test
    fun `no more than ten sources are attempted`() {
        val many = (1..14).map { index -> source("fonte$index", *Array(index) { "Filme $it" }) }

        val kept = MergedSources.withinLimit(many)

        assertEquals(MergedSources.MAXIMUM_SOURCES, kept.size)
        // The largest are kept, so a cap that bites still keeps the lists most likely to hold
        // whatever somebody is looking for.
        assertTrue(kept.any { it.sourceId == "fonte14" })
        assertFalse(kept.any { it.sourceId == "fonte1" })
    }

    @Test
    fun `ten or fewer sources are all attempted`() {
        val few = (1..10).map { index -> source("fonte$index", "Filme $index") }

        assertEquals(10, MergedSources.withinLimit(few).size)
    }

    /**
     * One channel offered in several qualities is one card.
     *
     * A provider carries "A&E", "A&E 480", "A&E [HD]" and "A&E [SD]" — the same channel four ways.
     * The bracketed marks were already collapsed; the bare "480" was not, so merging two lists put
     * four A&E cards on the grid. Reported with a screenshot of exactly that.
     */
    @Test
    fun `quality variants of one channel share a key`() {
        val keys =
            listOf("A&E", "A&E 480", "A&E [HD]", "A&E [SD]", "A&E [FHD][H265]", "A&E FHD")
                .map { name -> name.shelfDeduplicationKey() }
                .toSet()

        assertEquals(1, keys.size, "as variantes de qualidade nao dao a mesma chave: $keys")
    }

    /**
     * But a number that belongs to the name is not a quality mark.
     *
     * "Canal 4" and "Rede 21" are different channels from "Canal" and "Rede". A year is a separate
     * matter: `normalisedForMatching` has always dropped it on purpose, so "Duna (2021)" and "Duna"
     * are one film — that is not what this rule touches.
     */
    @Test
    fun `a number in the real name survives`() {
        listOf("Canal 4", "Rede 21")
            .forEach { name ->
                assertTrue(
                    name.shelfDeduplicationKey().any(Char::isDigit),
                    "$name perdeu o numero que faz parte do nome",
                )
            }
        // And they stay distinct from the same name without the number.
        assertTrue(
            "Canal 4".shelfDeduplicationKey() != "Canal".shelfDeduplicationKey(),
            "Canal 4 colapsou com Canal",
        )
    }

    /** And two different channels still differ. */
    @Test
    fun `different channels keep different keys`() {
        assertTrue(
            "A&E HD".shelfDeduplicationKey() != "AXN HD".shelfDeduplicationKey(),
            "dois canais diferentes colapsaram num so",
        )
    }
}
