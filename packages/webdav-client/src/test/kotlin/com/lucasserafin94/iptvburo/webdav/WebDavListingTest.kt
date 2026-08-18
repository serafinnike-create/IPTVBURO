package com.lucasserafin94.iptvburo.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading a `PROPFIND` response.
 *
 * The fixtures are shaped like real servers rather than invented: the namespace prefix, the
 * presence of `displayname`, and the way a collection is marked all differ between implementations,
 * and a reader that only handles one of them works against a single server and returns nothing
 * against the next.
 */
class WebDavListingTest {
    /** Nextcloud and ownCloud, which prefix with `d:` and always send a display name. */
    @Test
    fun `reads folders and files from a lowercase-prefixed response`() {
        val xml =
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/lucas/Filmes/</d:href>
                <d:propstat><d:prop>
                  <d:displayname>Filmes</d:displayname>
                  <d:resourcetype><d:collection/></d:resourcetype>
                </d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/lucas/Filmes/Duna.mkv</d:href>
                <d:propstat><d:prop>
                  <d:displayname>Duna.mkv</d:displayname>
                  <d:getcontentlength>8123456789</d:getcontentlength>
                  <d:getcontenttype>video/x-matroska</d:getcontenttype>
                  <d:resourcetype/>
                </d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            """.trimIndent()

        val entries = WebDavListing.parse(xml, requestedPath = "/remote.php/dav/files/lucas/Filmes/")

        assertEquals(1, entries.size, "The listed folder must not appear inside its own listing.")
        val film = entries.single()
        assertEquals("Duna.mkv", film.displayName)
        assertFalse(film.isDirectory)
        assertEquals(8_123_456_789L, film.sizeBytes)
        assertEquals("video/x-matroska", film.contentType)
    }

    /** Apache mod_dav, which uses `D:` and `lp1:` in the same document and sends no display name. */
    @Test
    fun `reads a response whose prefixes differ and which omits display names`() {
        val xml =
            """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/media/Series/</D:href>
                <D:propstat><D:prop><lp1:resourcetype><D:collection/></lp1:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/media/Series/Silo/</D:href>
                <D:propstat><D:prop><lp1:resourcetype><D:collection></D:collection></lp1:resourcetype></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
            """.trimIndent()

        val entries = WebDavListing.parse(xml, requestedPath = "/media/Series/")

        assertEquals(1, entries.size)
        // With no display name the folder is named from its own path, which is what a viewer sees.
        assertEquals("Silo", entries.single().displayName)
        assertTrue(entries.single().isDirectory)
    }

    /**
     * Escaping, which is where paths quietly break.
     *
     * A file with a space or a bracket arrives percent-encoded. Used without decoding, the next
     * request escapes it a second time and the server answers 404 — the folder looks empty.
     */
    @Test
    fun `percent-encoded names are decoded so the path can be used again`() {
        val xml =
            """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/media/Duna%20%282021%29%204K.mkv</d:href>
                <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            """.trimIndent()

        val entry = WebDavListing.parse(xml).single()

        assertEquals("/media/Duna (2021) 4K.mkv", entry.href)
        assertEquals("Duna (2021) 4K.mkv", entry.displayName)
    }

    /** A name can contain the characters XML has to escape; they must come back as themselves. */
    @Test
    fun `xml entities in a display name are decoded`() {
        val xml =
            """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/media/a.mkv</d:href>
                <d:propstat><d:prop>
                  <d:displayname>Tom &amp; Jerry &lt;1940&gt;</d:displayname>
                  <d:resourcetype/>
                </d:prop></d:propstat>
              </d:response>
            </d:multistatus>
            """.trimIndent()

        assertEquals("Tom & Jerry <1940>", WebDavListing.parse(xml).single().displayName)
    }

    /** An unreachable or hostile server may answer with anything; nothing is not a crash. */
    @Test
    fun `a response that is not a listing yields nothing rather than throwing`() {
        assertTrue(WebDavListing.parse("").isEmpty())
        assertTrue(WebDavListing.parse("<html><body>403 Forbidden</body></html>").isEmpty())
    }

    /** The credential must never travel in a value that is held, listed or logged. */
    @Test
    fun `an entry never prints its own path`() {
        val entry = WebDavEntry(href = "/secret/path.mkv", displayName = "a", isDirectory = false)

        assertFalse("path" in entry.toString(), "The href must be redacted in diagnostics.")
        assertTrue("redacted" in entry.toString())
    }
}
