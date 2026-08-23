package com.lucasserafin94.iptvburo.metadata

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A film that belongs to no franchise must still get a shelf.
 *
 * This is the defect the shelf actually shipped with. TMDb answers
 * `"belongs_to_collection": null` for any standalone film — which is most films — and Gson hands
 * that back as `JsonNull`. Calling `getAsJsonObject` on it throws ClassCastException rather than
 * returning null, and that exception escaped `franchiseTitles` and killed the whole `similarTitles`
 * call before the recommendations were ever requested.
 *
 * So a franchise film worked and every other film showed an empty shelf. Measured on the reported
 * title: Tycus (id 2096) threw, while Superman (id 1924) returned sixteen. After the fix, Tycus
 * returns sixteen and Superman is unchanged.
 *
 * Asserted on the source rather than over the network, because a test that needs a key and an
 * internet connection fails for reasons unrelated to the code.
 */
class StandaloneFilmShelfTest {
    private val source: String =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt").readText()

    private val franchise: String =
        source.substringAfter("fun franchiseTitles(")

    @Test
    fun `a null collection is checked rather than cast`() {
        assertFalse(
            franchise.substringBefore("val collectionUrl")
                .contains("""getAsJsonObject("belongs_to_collection")"""),
            "getAsJsonObject throws on JsonNull, which is what TMDb sends for a standalone film.",
        )
        assertTrue(
            franchise.contains("""?.get("belongs_to_collection")""") &&
                franchise.contains("?.takeIf { it.isJsonObject }"),
            "The field has to be tested for being an object before it is read as one.",
        )
    }

    @Test
    fun `the recommendations are not behind the franchise lookup`() {
        // The reason the crash was so damaging: franchiseTitles runs first, so anything it throws
        // takes the recommendations with it. The order is deliberate and stays, but the lookup must
        // not be able to fail the whole call.
        val similar = source.substringAfter("fun similarTitles(").substringBefore("fun franchiseTitles(")
        val franchiseAt = similar.indexOf("franchiseTitles(tmdbId)")
        val fetchAt = similar.indexOf("""fetch("recommendations")""")
        assertTrue(franchiseAt in 0 until fetchAt, "The franchise still leads the shelf.")
    }

    @Test
    fun `no debug printing survived the investigation`() {
        // A println here would carry an endpoint and an id into whatever log the app writes, and
        // this file also handles the key. Found by a colleague reading the diff, not by a test —
        // hence this one.
        assertFalse(
            source.contains("println(\"[tmdb]"),
            "Debug output from chasing the empty shelf must not ship.",
        )
    }
}
