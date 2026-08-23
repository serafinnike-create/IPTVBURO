package com.lucasserafin94.iptvburo.metadata

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A franchise before the merely similar.
 *
 * These are different questions and the first one is usually what a viewer means. Measured against
 * the live service while this was written: asked about Superman (2025), `recommendations` answers
 * The Flash, Adão Negro and the Guardians films — related, popular, and not the sequels. The
 * collection endpoint answers Superman I, II, III and IV, which is the list somebody looking at one
 * of them is after.
 *
 * Asserted on the source rather than over the network, because a test that needs a key and an
 * internet connection is one that fails for reasons unrelated to the code.
 */
class FranchiseFirstTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt").readText()

    private val similar: String =
        source.substringAfter("fun similarTitles(").substringBefore("fun franchiseTitles(")

    @Test
    fun `the collection is asked for before recommendations are appended`() {
        val franchiseAt = similar.indexOf("franchiseTitles(tmdbId)")
        val relatedAt = similar.indexOf("""fetch("recommendations")""")
        assertTrue(franchiseAt > 0, "The franchise has to be looked up at all.")
        assertTrue(relatedAt > 0, "Recommendations remain the second half of the answer.")
        assertTrue(
            similar.contains("(franchise + related)"),
            "The franchise must lead the list, not be appended after the recommendations.",
        )
    }

    @Test
    fun `a title cannot appear twice`() {
        // A sequel is frequently in both lists, and a shelf showing the same poster twice reads as
        // a bug rather than as emphasis.
        assertTrue(similar.contains("distinctBy { it.id }"))
    }

    @Test
    fun `series skip the collection lookup`() {
        // TMDb has no collections for television, so asking would be two requests for a certain
        // empty answer on every series page.
        assertTrue(
            similar.contains("if (isSeries) emptyList() else franchiseTitles(tmdbId)"),
            "A series should not pay for a lookup that cannot succeed.",
        )
    }

    @Test
    fun `the franchise reads in release order`() {
        val franchise = source.substringAfter("fun franchiseTitles(")
        assertTrue(
            franchise.contains("sortedBy { it.year ?: Int.MAX_VALUE }"),
            "TMDb returns collection parts unsorted — it put Superman III before Superman II.",
        )
    }
}
