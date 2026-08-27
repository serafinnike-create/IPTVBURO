package com.lucasserafin94.iptvburo.data.licensing

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the television accepts from a seller's delivery.
 *
 * The rule that decides whether somebody's list is replaced, so it is exercised directly rather
 * than through the client, which needs an Android context to build.
 */
class AssignedPlaylistParsingTest {
    private fun parse(json: String): AssignedPlaylist? =
        assignedPlaylistFrom(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `a full delivery is accepted`() {
        val assigned =
            requireNotNull(
                parse(
                    """
                    {"server":"http://provedor.invalid:8080","username":"cliente","password":"senha",
                     "listLabel":"Plano Familia","metadataKey":"chave-tmdb"}
                    """.trimIndent(),
                ),
            )

        assertEquals("http://provedor.invalid:8080", assigned.serverUrl)
        assertEquals("Plano Familia", assigned.listLabel)
        assertEquals("chave-tmdb", assigned.metadataKey)
    }

    /**
     * The report behind this: "nao deixa eu enviar so api tmdb preciso enviar tudo". A seller whose
     * customer already has a working list must not have to retype the whole connection.
     */
    @Test
    fun `a delivery with only a key is accepted`() {
        val assigned = requireNotNull(parse("""{"metadataKey":"chave-tmdb"}"""))

        assertEquals("chave-tmdb", assigned.metadataKey)
        assertNull("sem credencial, nao inventa endereco", assigned.serverUrl)
    }

    @Test
    fun `a delivery with only a name is accepted`() {
        assertEquals("Plano Familia", requireNotNull(parse("""{"listLabel":"Plano Familia"}""")).listLabel)
    }

    /**
     * Half a credential is not a partial delivery — it is a list that will never open, and the
     * failure would surface on the customer's television rather than in the seller's panel.
     */
    @Test
    fun `half a credential is refused`() {
        // Each carries a key as well, so the "nothing arrived" guard cannot be what refuses them:
        // without that, all four would be caught by the wrong rule and this test would pass with
        // the credential rule deleted. It did.
        listOf(
            """{"server":"http://a.invalid","username":"u","metadataKey":"k"}""",
            """{"server":"http://a.invalid","password":"p","metadataKey":"k"}""",
            """{"username":"u","password":"p","metadataKey":"k"}""",
            """{"server":"http://a.invalid","username":"u","password":"","metadataKey":"k"}""",
        ).forEach { body ->
            assertNull("devia recusar: $body", parse(body))
        }
    }

    @Test
    fun `a delivery carrying nothing at all is refused`() {
        // Building one would have the television confirm and erase a delivery it never applied.
        assertNull(parse("{}"))
        assertNull(parse("""{"server":null,"metadataKey":null}"""))
    }

    /** The credentials must never be readable in a crash log. */
    @Test
    fun `the credentials are redacted in toString`() {
        val assigned =
            requireNotNull(
                parse("""{"server":"http://a.invalid","username":"cliente","password":"senha-secreta"}"""),
            )

        val rendered = assigned.toString()
        assertFalse("a senha vazou no toString", rendered.contains("senha-secreta"))
        assertFalse("o usuario vazou no toString", rendered.contains("cliente"))
    }
}
