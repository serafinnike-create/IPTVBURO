package com.lucasserafin94.iptvburo.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A subscription arrives in whatever shape the seller happened to use.
 *
 * The person pasting it did not choose the format and should not have to recognise it. Every case
 * here is a real thing providers send; the app's job is to find the same account in all of them.
 */
class XtreamSubscriptionParserTest {
    @Test
    fun `a playlist url carries its credentials in the query`() {
        val link =
            XtreamSubscriptionParser.parse(
                "http://exemplo.test:8080/get.php?username=assinante&password=segredo&type=m3u_plus",
            )

        assertEquals("assinante", link?.username)
        assertEquals("segredo", link?.password)
        assertEquals("http://exemplo.test:8080/", link?.endpoint?.baseUrl.toString())
    }

    @Test
    fun `an api url is read the same way`() {
        val link =
            XtreamSubscriptionParser.parse(
                "http://exemplo.test:8080/player_api.php?username=assinante&password=segredo",
            )

        assertEquals("assinante", link?.username)
        assertEquals("http://exemplo.test:8080/", link?.endpoint?.baseUrl.toString())
    }

    @Test
    fun `the short playlist form puts them in the path`() {
        val link = XtreamSubscriptionParser.parse("http://exemplo.test:8080/playlist/assinante/segredo/m3u_plus")

        assertEquals("assinante", link?.username)
        assertEquals("segredo", link?.password)
    }

    @Test
    fun `a stream url identifies the account too`() {
        // What someone copies out of a running player, rather than what the provider emailed.
        val link = XtreamSubscriptionParser.parse("http://exemplo.test:8080/live/assinante/segredo/12345.ts")

        assertEquals("assinante", link?.username)
        assertEquals("segredo", link?.password)
    }

    @Test
    fun `credentials in the userinfo are read`() {
        val link = XtreamSubscriptionParser.parse("http://assinante:segredo@exemplo.test:8080/")

        assertEquals("assinante", link?.username)
        assertEquals("segredo", link?.password)
        // The endpoint must not carry them onward: they belong in the vault, not in a stored URL.
        assertFalse(link?.endpoint?.baseUrl.toString()?.contains("segredo") ?: true)
    }

    @Test
    fun `a plain server address carries no credentials`() {
        // Not a failure. It means the user will type the username and password themselves, which is
        // the ordinary case, and the caller keeps what they typed.
        assertNull(XtreamSubscriptionParser.parse("http://exemplo.test:8080/"))
        assertNull(XtreamSubscriptionParser.parse("exemplo.test"))
    }

    @Test
    fun `two arbitrary path segments are not treated as a login`() {
        // Without the marker segment this is just a server behind a path. Reading the last two
        // segments as credentials would turn a working address into a login full of nonsense.
        assertNull(XtreamSubscriptionParser.parse("http://exemplo.test:8080/algum/caminho"))
    }

    @Test
    fun `an incomplete pair is refused rather than half-used`() {
        assertNull(XtreamSubscriptionParser.parse("http://exemplo.test:8080/get.php?username=assinante"))
        assertNull(XtreamSubscriptionParser.parse("http://exemplo.test:8080/get.php?password=segredo"))
    }

    @Test
    fun `rubbish is refused without throwing`() {
        // This runs on text a user pasted, so it has to answer rather than crash the screen.
        listOf("", "   ", "http://", "ftp://exemplo.test/a/b/c", "://////").forEach { value ->
            assertNull("aceitou entrada invalida: $value", XtreamSubscriptionParser.parse(value))
        }
    }

    @Test
    fun `the credentials never appear in toString`() {
        val link = XtreamSubscriptionParser.parse("http://exemplo.test:8080/get.php?username=assinante&password=segredo")

        val printed = link.toString()
        assertFalse("a senha vazou no toString", printed.contains("segredo"))
        assertFalse("o usuario vazou no toString", printed.contains("assinante"))
    }
}
