package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamingDiscoveryCapabilityTest {
    @Test
    fun `nothing configured hides the area entirely`() {
        val capability = StreamingDiscoveryCapability.of(hasRealProvider = false)

        assertEquals(StreamingDiscoveryCapability.UNAVAILABLE, capability)
        assertFalse(capability.isVisible)
    }

    @Test
    fun `the fixture alone shows the area but marks everything DEMO`() {
        val capability = StreamingDiscoveryCapability.of(hasRealProvider = false, hasFixtureProvider = true)

        assertEquals(StreamingDiscoveryCapability.DEMO_ONLY, capability)
        assertTrue(capability.isVisible)
        assertTrue(capability.requiresDemoLabel)
    }

    @Test
    fun `a real catalogue drops the DEMO label`() {
        val capability = StreamingDiscoveryCapability.of(hasRealProvider = true)

        assertEquals(StreamingDiscoveryCapability.AVAILABLE, capability)
        assertTrue(capability.isVisible)
        assertFalse(capability.requiresDemoLabel)
    }

    @Test
    fun `a real catalogue wins over the fixture`() {
        assertEquals(
            StreamingDiscoveryCapability.AVAILABLE,
            StreamingDiscoveryCapability.of(hasRealProvider = true, hasFixtureProvider = true),
        )
    }

    @Test
    fun `an invisible capability never asks for a DEMO label`() {
        // Nothing is on screen to label, and a caller reading requiresDemoLabel without checking
        // isVisible must not be told to render a badge for a hidden area.
        assertFalse(StreamingDiscoveryCapability.UNAVAILABLE.requiresDemoLabel)
    }
}
