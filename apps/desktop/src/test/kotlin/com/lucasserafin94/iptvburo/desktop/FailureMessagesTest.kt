package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.xtream.XtreamClientException
import com.lucasserafin94.iptvburo.xtream.XtreamFailureReason
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailureMessagesTest {
    private val log = "C:\\Users\\alguem\\.iptvburo\\logs\\iptvburo.log"

    private fun message(error: Throwable) = FailureMessages.forFailure(error, log)

    /**
     * The report that produced this test: a user installed a new build over an old one, their
     * catalogue was restored from disk — 32,466 titles on screen — and every action that needed the
     * provider failed with "the server did not return a compatible Xtream catalogue".
     *
     * Nothing had been asked of the provider. The credentials live separately, encrypted with
     * DPAPI, and had not been restored; the first call needing the session threw. Blaming the list
     * sent two diagnoses to the wrong place.
     */
    @Test
    fun `a missing session is not reported as a broken list`() {
        val text = message(IllegalArgumentException(FailureMessages.NO_SESSION_MARKER))

        assertTrue("sessão" in text, "must name the session as the cause: $text")
        assertFalse("Xtream compatível" in text, "must not blame the provider's catalogue: $text")
        assertTrue("catálogo continua salvo" in text, "must reassure the catalogue is intact: $text")
    }

    /** The same class of exception with any other message is an app fault, not a session problem. */
    @Test
    fun `an unrelated illegal argument is reported as an app fault`() {
        val text = message(IllegalArgumentException("index out of range"))

        assertTrue("falha do aplicativo" in text, text)
        assertFalse("sessão" in text, text)
    }

    /**
     * Only an XtreamClientException may blame the provider.
     *
     * The original mapping ran on every throwable and funnelled anything unrecognised into the
     * Xtream wording, which is how a fault in this app came to be reported as the customer's
     * server returning a malformed catalogue.
     */
    @Test
    fun `a non-Xtream failure never blames the provider`() {
        listOf(
            NullPointerException("boom"),
            IllegalStateException("bad state"),
            RuntimeException("anything"),
        ).forEach { error ->
            val text = message(error)
            assertFalse("servidor" in text, "${error::class.simpleName} blamed the server: $text")
            assertTrue("falha do aplicativo" in text, "${error::class.simpleName}: $text")
        }
    }

    /** The type is named so a screenshot is diagnostically useful. */
    @Test
    fun `an app fault names the exception type`() {
        assertTrue("NullPointerException" in message(NullPointerException()))
    }

    /**
     * The exception's own message must never reach the screen.
     *
     * OkHttp puts the full request URL into its IOException text, and an Xtream URL carries the
     * username and password in its query string. A screenshot of this card ends up in a chat.
     */
    @Test
    fun `a credential-bearing message is never shown`() {
        val leaky =
            IOException("failed to GET http://provider.example:8080/player_api.php?username=joao&password=segredo123")

        val text = message(leaky)

        assertFalse("segredo123" in text, "the password reached the screen: $text")
        assertFalse("joao" in text, "the username reached the screen: $text")
        assertFalse("provider.example" in text, "the server address reached the screen: $text")
    }

    /** Each provider-side reason keeps its own wording. */
    @Test
    fun `provider failures keep their specific wording`() {
        val expected =
            mapOf(
                XtreamFailureReason.INVALID_SERVER to "endereço do servidor",
                XtreamFailureReason.AUTHENTICATION to "recusou o usuário",
                XtreamFailureReason.NETWORK to "alcançar o servidor",
                XtreamFailureReason.HTTP to "erro HTTP",
                XtreamFailureReason.RESPONSE_TOO_LARGE to "limite seguro",
                XtreamFailureReason.INVALID_RESPONSE to "catálogo Xtream compatível",
            )

        expected.forEach { (reason, fragment) ->
            val text = message(XtreamClientException(reason, "internal detail"))
            assertTrue(fragment in text, "$reason should say '$fragment' but said: $text")
            assertFalse("internal detail" in text, "$reason leaked its internal message")
        }
    }

    /** The one failure the user cannot act on alone points at the log. */
    @Test
    fun `an incompatible catalogue names the log file`() {
        val text = message(XtreamClientException(XtreamFailureReason.INVALID_RESPONSE, "x"))

        assertEquals(true, text.contains(log), "the log path must be shown: $text")
    }
}
