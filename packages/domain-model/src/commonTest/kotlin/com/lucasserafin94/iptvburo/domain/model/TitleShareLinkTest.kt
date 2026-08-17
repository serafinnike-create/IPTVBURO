package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleShareLinkTest {
    private val duneIdentity = ContentIdentity.of(ContentKind.MOVIE, "Duna: Parte Dois", 2024)

    private fun duneLink(
        artworkUrl: String? = "https://image.tmdb.org/t/p/w500/poster.jpg",
        description: String? = "Paul Atreides se une aos Fremen.",
    ) = TitleShareLink.of(
        identity = duneIdentity,
        title = "Duna: Parte Dois",
        year = 2024,
        artworkUrl = artworkUrl,
        description = description,
    )

    // -----------------------------------------------------------------------------------------
    // The security boundary: what must never travel in a share
    // -----------------------------------------------------------------------------------------

    @Test
    fun `provider hosted poster is dropped rather than shared`() {
        // The subscriber's own server. Sharing this address publishes where their account lives,
        // to everyone in the WhatsApp group.
        val link = duneLink(artworkUrl = "http://provider.example.com:8080/images/1234.jpg")

        assertNull(link?.artworkUrl)
        assertFalse(link!!.webUrl().contains("provider.example.com"))
    }

    @Test
    fun `poster carrying credentials is refused even on an allowlisted host`() {
        // Userinfo in the authority is the case a naive `endsWith("image.tmdb.org")` check would
        // wave through, having read the host from the wrong side of the '@'.
        assertFalse(TitleShareLink.isPublicArtwork("https://user:secret@evil.example/x.jpg"))
        assertFalse(TitleShareLink.isPublicArtwork("https://image.tmdb.org:pass@evil.example/x.jpg"))
    }

    @Test
    fun `host must match exactly, not merely end with an allowlisted name`() {
        assertFalse(TitleShareLink.isPublicArtwork("https://image.tmdb.org.evil.example/x.jpg"))
        assertFalse(TitleShareLink.isPublicArtwork("https://notimage.tmdb.org/x.jpg"))
    }

    @Test
    fun `non https artwork is refused`() {
        assertFalse(TitleShareLink.isPublicArtwork("http://image.tmdb.org/t/p/w500/poster.jpg"))
        assertFalse(TitleShareLink.isPublicArtwork("file:///C:/Users/me/poster.jpg"))
        assertFalse(TitleShareLink.isPublicArtwork("iptvburo://title?img=x"))
    }

    @Test
    fun `public tmdb poster is kept`() {
        assertTrue(TitleShareLink.isPublicArtwork("https://image.tmdb.org/t/p/w500/poster.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", duneLink()?.artworkUrl)
    }

    @Test
    fun `a hostile incoming link cannot point the app at an arbitrary image host`() {
        // Parsing applies the same rule as building. Without this, a hand-written link would make
        // the recipient's app fetch from a host of the sender's choosing.
        val hostile =
            "iptvburo://title?id=movie%3Aduna-parte-dois%3A2024&t=Duna&img=" +
                "https%3A%2F%2Ftracker.evil.example%2Fbeacon.png"

        assertNull(TitleShareLink.parse(hostile)?.artworkUrl)
    }

    @Test
    fun `description is capped so a share cannot become a data channel`() {
        val link = duneLink(description = "x".repeat(5_000))

        val description = assertNotNull(link?.description)
        assertTrue(description.length <= TitleShareLink.MAX_DESCRIPTION + 1, "was ${description.length}")
    }

    // -----------------------------------------------------------------------------------------
    // Round trip
    // -----------------------------------------------------------------------------------------

    @Test
    fun `app uri round trips through parse`() {
        val original = assertNotNull(duneLink())

        val parsed = assertNotNull(TitleShareLink.parse(original.appUri()))

        assertEquals(original.identity, parsed.identity)
        assertEquals(original.title, parsed.title)
        assertEquals(original.year, parsed.year)
        assertEquals(original.artworkUrl, parsed.artworkUrl)
        assertEquals(original.description, parsed.description)
    }

    @Test
    fun `web url round trips through parse`() {
        val original = assertNotNull(duneLink())

        assertEquals(original.identity, TitleShareLink.parse(original.webUrl())?.identity)
    }

    @Test
    fun `titles with spaces and accents survive the round trip`() {
        // A space encoded as '+' would come back as a literal '+' here, which is why the encoder
        // does not use URLEncoder.
        val link =
            assertNotNull(
                TitleShareLink.of(
                    identity = ContentIdentity.of(ContentKind.MOVIE, "O Auto da Compadecida", 2000),
                    title = "O Auto da Compadecida + Extras",
                    year = 2000,
                ),
            )

        assertEquals("O Auto da Compadecida + Extras", TitleShareLink.parse(link.appUri())?.title)
    }

    /**
     * An accented character at the very end of a title is the ordinary case, not an edge one:
     * "José" is three ASCII characters followed by a single escape, and that escape is the last
     * thing in the value. Losing it would change which film the link names.
     */
    @Test
    fun `a title ending in an escape decodes fully`() {
        val link =
            assertNotNull(
                TitleShareLink.of(
                    identity = ContentIdentity.of(ContentKind.MOVIE, "José", 1998),
                    title = "José",
                    year = 1998,
                ),
            )

        assertEquals("José", TitleShareLink.parse(link.appUri())?.title)
    }

    @Test
    fun `ampersands in a title do not break the query`() {
        val link =
            assertNotNull(
                TitleShareLink.of(
                    identity = duneIdentity,
                    title = "Fire & Blood",
                    description = "A=B&C=D",
                ),
            )

        val parsed = assertNotNull(TitleShareLink.parse(link.appUri()))
        assertEquals("Fire & Blood", parsed.title)
        assertEquals("A=B&C=D", parsed.description)
    }

    // -----------------------------------------------------------------------------------------
    // Resolution against the recipient's own catalogue
    // -----------------------------------------------------------------------------------------

    @Test
    fun `link resolves to the same identity the recipient computes for their own copy`() {
        // The reason this feature works at all: the sender's list decorates the title one way, the
        // recipient's another, and both reduce to the identity carried in the link.
        val senderIdentity = ContentIdentity.of(ContentKind.MOVIE, "[4K] Duna: Parte Dois (2024) DUAL", 2024)
        val link = assertNotNull(TitleShareLink.of(senderIdentity, "Duna: Parte Dois", 2024))

        val recipientIdentity = ContentIdentity.of(ContentKind.MOVIE, "Duna Parte Dois 1080p LEG", 2024)

        assertEquals(recipientIdentity, TitleShareLink.parse(link.appUri())?.identity)
    }

    // -----------------------------------------------------------------------------------------
    // Malformed input
    // -----------------------------------------------------------------------------------------

    @Test
    fun `garbage does not parse`() {
        assertNull(TitleShareLink.parse(""))
        assertNull(TitleShareLink.parse("iptvburo://title"))
        assertNull(TitleShareLink.parse("https://iptvburo.pages.dev/t/?t=NoIdentity"))
        assertNull(TitleShareLink.parse("iptvburo://title?id=movie%3Aduna"))
    }

    @Test
    fun `a blank title produces no link`() {
        assertNull(TitleShareLink.of(duneIdentity, "   "))
    }

    @Test
    fun `blank description and artwork become null rather than empty text`() {
        val link = assertNotNull(duneLink(artworkUrl = "  ", description = "   "))

        assertNull(link.artworkUrl)
        assertNull(link.description)
    }
}
