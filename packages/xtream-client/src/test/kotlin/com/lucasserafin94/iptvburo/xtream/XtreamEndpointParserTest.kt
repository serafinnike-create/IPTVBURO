package com.lucasserafin94.iptvburo.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What counts as a server address on the connect form.
 *
 * Reported as a correct address being refused, so these pin the plain cases first: if a bare host
 * with a scheme were rejected, nothing else here would matter.
 */
class XtreamEndpointParserTest {
    private fun parse(raw: String) = XtreamEndpointParser.parse(raw).baseUrl.toString()

    /** The ordinary case, and the one the report was about. */
    @Test
    fun `a plain http host is accepted`() {
        assertEquals("http://buro.ac/", parse("http://buro.ac"))
    }

    @Test
    fun `a trailing slash changes nothing`() {
        assertEquals("http://buro.ac/", parse("http://buro.ac/"))
    }

    /**
     * Surrounding spaces are trimmed rather than refused.
     *
     * An address arrives pasted far more often than typed, and a leading space picked up from a
     * chat message is not a different server.
     */
    @Test
    fun `surrounding spaces are ignored`() {
        assertEquals("http://buro.ac/", parse("  http://buro.ac  "))
    }

    /** A host with no scheme is assumed to be https rather than rejected. */
    @Test
    fun `a bare host is assumed https`() {
        assertEquals("https://buro.ac/", parse("buro.ac"))
    }

    /** A port survives, since a great many providers run on one. */
    @Test
    fun `a port is kept`() {
        assertEquals("http://buro.ac:8080/", parse("http://buro.ac:8080"))
    }

    /**
     * A typed address with a mistake in the scheme is refused.
     *
     * `http:7/host` is what a slip of one key produces, and it is not the same server as
     * `http://host` — accepting it by guessing would connect somewhere the viewer never typed.
     */
    @Test
    fun `a mistyped scheme is refused`() {
        val failure = runCatching { parse("http:7/buro.ac") }.exceptionOrNull()

        assertTrue(
            "um esquema mal escrito devia ser recusado, veio: $failure",
            failure is XtreamClientException && failure.reason == XtreamFailureReason.INVALID_SERVER_SCHEME,
        )
    }

    /**
     * The credentials are stripped from the address.
     *
     * A provider's panel sometimes hands out a URL with the username and password already in it,
     * and those must not survive into a stored base address.
     */
    @Test
    fun `credentials in the address are dropped`() {
        val parsed = parse("http://user:secret@buro.ac")

        assertTrue("o utilizador ficou no endereco: $parsed", !parsed.contains("user"))
        assertTrue("a senha ficou no endereco: $parsed", !parsed.contains("secret"))
    }

    /** As is the query, which is where a panel puts the credentials when not in the userinfo. */
    @Test
    fun `a query string is dropped`() {
        assertEquals("http://buro.ac/", parse("http://buro.ac/get.php?username=u&password=p"))
    }

    /**
     * A single missing slash is caught too.
     *
     * `http:/host` is the other half of the same slip, and it used to become a request to a host
     * called `http` rather than to the host that was typed.
     */
    @Test
    fun `a scheme with one slash is refused`() {
        val failure = runCatching { parse("http:/buro.ac") }.exceptionOrNull()

        assertTrue(
            "um esquema com uma barra devia ser recusado, veio: $failure",
            failure is XtreamClientException,
        )
    }

    /**
     * But a bare host with a port still works.
     *
     * This is the case the new check must not break: a colon in an address is far more often a
     * port than a mistyped scheme, and refusing those would lock out a great many providers.
     */
    @Test
    fun `a bare host with a port is still accepted`() {
        assertEquals("https://buro.ac:8080/", parse("buro.ac:8080"))
    }

    /** Including one with a path after the port. */
    @Test
    fun `a bare host with a port and path is accepted`() {
        assertEquals("https://buro.ac:8080/live", parse("buro.ac:8080/live"))
    }

    /** Only http and https, so nothing else is dialled by pasting an address. */
    @Test
    fun `other schemes are refused`() {
        listOf("ftp://buro.ac", "file:///etc/passwd").forEach { raw ->
            val failure = runCatching { parse(raw) }.exceptionOrNull()
            assertTrue(
                "$raw devia ser recusado, veio: $failure",
                failure is XtreamClientException,
            )
        }
    }
}
