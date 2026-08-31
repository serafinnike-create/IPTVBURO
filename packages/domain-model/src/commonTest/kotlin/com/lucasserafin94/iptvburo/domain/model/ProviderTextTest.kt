package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Synopses that arrive with their accents already destroyed.
 *
 * Seen on a real list: "O tenente Marion Cobretti est? no centro de uma s?rie de assassinatos" and
 * "carteiras de motoristas ca?adas e carros apreendidos por viola??es de tr?nsito". The provider
 * converts its catalogue out of a single-byte encoding badly, so every accented letter reaches us
 * as a question mark. Nothing can be decoded back — the bytes are gone upstream.
 */
class ProviderTextTest {
    /** The two paragraphs this was found on, verbatim. */
    @Test
    fun `a synopsis with lost accents is damaged`() {
        assertTrue(
            ProviderText.isDamaged(
                "O tenente Marion \"Cobra\" Cobretti est? no centro de uma s?rie de assassinatos " +
                    "cometidos por uma sociedade secreta chamada Nova Ordem, em que os assassinos " +
                    "escolhem os membros mais fracos da sociedade para o exterm?nio. Com o n?mero " +
                    "de homic?dios aumentando",
            ),
        )
        assertTrue(
            ProviderText.isDamaged(
                "Com carteiras de motoristas ca?adas e carros apreendidos por viola??es de tr?nsito",
            ),
        )
    }

    /** A clean Portuguese synopsis is left alone. */
    @Test
    fun `a synopsis with real accents is not damaged`() {
        assertFalse(
            ProviderText.isDamaged(
                "Irreverente comédia da HBO que traz para a tela as histórias mais absurdas da " +
                    "Flórida, recriadas por estrelas de Hollywood.",
            ),
        )
    }

    /**
     * A synopsis that genuinely asks a question survives.
     *
     * This is the case a cruder rule would destroy: counting every question mark would throw away
     * a perfectly good paragraph because it ends in one.
     */
    @Test
    fun `a real question is not damage`() {
        assertFalse(ProviderText.isDamaged("Quem matou o pai dele? Ninguém sabe."))
        assertFalse(ProviderText.isDamaged("Será que ela consegue? O tempo dirá."))
    }

    /**
     * One mark alone is not enough.
     *
     * A single hit can be a typo in an otherwise readable paragraph, and discarding a good synopsis
     * is its own harm.
     */
    @Test
    fun `a single mark is tolerated`() {
        assertFalse(ProviderText.isDamaged("O filme conta a hist?ria de um homem comum."))
    }

    /**
     * The short synopsis that slipped through the first attempt.
     *
     * "est? sendo tra?do" has only one question mark inside a word, so a rule that looked only
     * there scored one and let it reach the banner — which is exactly where it was seen.
     */
    @Test
    fun `a short damaged synopsis is caught`() {
        assertTrue(
            ProviderText.isDamaged(
                "Um escritor fracassado que acredita que est? sendo tra?do pela sua esposa " +
                    "recebe a visita inesperada de uma misteriosa mulher verde.",
            ),
        )
    }

    /** Nothing to judge is not damage. */
    @Test
    fun `blank and null are not damaged`() {
        assertFalse(ProviderText.isDamaged(null))
        assertFalse(ProviderText.isDamaged(""))
        assertFalse(ProviderText.isDamaged("   "))
    }

    /** And the convenience wrapper hands back the text or nothing. */
    @Test
    fun `usable text survives and damaged text becomes null`() {
        val clean = "Irreverente comédia da HBO."
        assertEquals(clean, ProviderText.usableOrNull(clean))
        assertEquals(null, ProviderText.usableOrNull("o exterm?nio com o n?mero de homic?dios"))
    }

    /**
     * A short label is repaired rather than hidden.
     *
     * "Thriller, A??o • Fic??o cient?fica" was reported on a details page. Dropping the genre the
     * way a damaged synopsis is dropped would leave the fact line with a hole in it and tell the
     * viewer even less, so the handful of category names a catalogue actually carries are put back.
     */
    @Test
    fun `a damaged genre gets its accents back`() {
        assertEquals("Ação", ProviderText.repairLabel("A??o"))
        assertEquals("Ficção científica", ProviderText.repairLabel("Fic??o cient?fica"))
        assertEquals("Animação", ProviderText.repairLabel("Anima??o"))
        assertEquals("Comédia", ProviderText.repairLabel("Com?dia"))
    }

    /** A label that arrived intact is not touched. */
    @Test
    fun `an undamaged label passes through`() {
        assertEquals("Thriller", ProviderText.repairLabel("Thriller"))
        assertEquals("Ficção científica", ProviderText.repairLabel("Ficção científica"))
        assertEquals("United States of America", ProviderText.repairLabel("United States of America"))
    }

    /**
     * And a word the repair does not know keeps its question mark.
     *
     * Restoring accents by rule would put them in words the provider spelled correctly, and a wrong
     * accent is worse than a question mark: one reads as a broken feed, the other as the app not
     * knowing the language.
     */
    @Test
    fun `an unknown word is left as it arrived`() {
        assertEquals("Zorglub?vski", ProviderText.repairLabel("Zorglub?vski"))
    }

    /** Nothing in, nothing out — the caller's own fallback still applies. */
    @Test
    fun `a blank label is handed back unchanged`() {
        assertEquals(null, ProviderText.repairLabel(null))
        assertEquals("", ProviderText.repairLabel(""))
    }
}
