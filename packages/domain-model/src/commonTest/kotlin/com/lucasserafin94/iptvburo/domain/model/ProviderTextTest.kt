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
}
