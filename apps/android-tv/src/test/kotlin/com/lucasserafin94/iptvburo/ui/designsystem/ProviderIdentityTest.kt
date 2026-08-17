package com.lucasserafin94.iptvburo.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which streaming service a category name names.
 *
 * The badge is a guess drawn from the only signal an Xtream playlist gives — how the provider filed
 * the title — so the rule has to be generous about spelling and strict about false positives. A
 * wrong badge tells the viewer a film is on a service it is not.
 */
class ProviderIdentityTest {
    @Test
    fun `the usual services are recognised however the provider spells them`() {
        assertEquals("Netflix", providerIdentityFor("Filmes | Netflix")?.label)
        assertEquals("Netflix", providerIdentityFor("SÉRIES NETFLIX 4K")?.label)
        assertEquals("Prime Video", providerIdentityFor("Filmes | Amazon Prime")?.label)
        assertEquals("Disney+", providerIdentityFor("disney plus - infantil")?.label)
        assertEquals("Max", providerIdentityFor("Filmes | Max")?.label)
    }

    /**
     * The case the bounded pattern exists for.
     *
     * "Cinemax" contains "max", and a plain substring test badged it as the streaming service —
     * which is a different company entirely.
     */
    @Test
    fun `a name that merely contains a service name is not badged`() {
        assertNull(providerIdentityFor("Filmes | Cinemax"))
        assertNull(providerIdentityFor("Maxximum Acao"))
    }

    /** Nothing to read means no badge, rather than a default one. */
    @Test
    fun `an unknown or empty category yields nothing`() {
        assertNull(providerIdentityFor("Filmes | Lancamentos"))
        assertNull(providerIdentityFor(""))
        assertNull(providerIdentityFor(null))
    }

    /**
     * The names TMDb itself uses for the Brazilian region.
     *
     * Taken from a live response rather than guessed: the directory is what supplies the official
     * logos, so a service whose name this rule does not recognise silently keeps its monogram.
     */
    @Test
    fun `the names TMDb reports for this region are recognised`() {
        assertEquals("Netflix", providerIdentityFor("Netflix")?.label)
        assertEquals("Prime Video", providerIdentityFor("Amazon Prime Video")?.label)
        assertEquals("Disney+", providerIdentityFor("Disney Plus")?.label)
        assertEquals("Google Play Movies", providerIdentityFor("Google Play Movies")?.label)
        assertEquals("Paramount+", providerIdentityFor("Paramount Plus")?.label)
        assertEquals("Plex", providerIdentityFor("Plex")?.label)
    }

    /**
     * Apple sells two different things and TMDb lists them apart.
     *
     * The subscription service and the rental shop are separate products, so the more specific
     * name has to be tested first — a plain "apple tv" check would swallow both.
     */
    @Test
    fun `the Apple subscription and the Apple shop are told apart`() {
        assertEquals("Apple TV Store", providerIdentityFor("Apple TV Store")?.label)
        assertEquals("Apple TV+", providerIdentityFor("Apple TV")?.label)
    }
}
