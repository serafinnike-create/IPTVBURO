package com.lucasserafin94.iptvburo.desktop.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlatformCapabilitiesTest {
    /**
     * The manifest is the single source of truth for what the UI offers.
     *
     * Each flag here is a deliberate release decision, asserted so that flipping one is a visible
     * change to this test rather than a silent change to what customers can see.
     */
    @Test
    fun `packaged preview contract matches what has actually shipped`() {
        val capabilities = DesktopPlatformCapabilities.current

        // Released. Both were built, tested, and then invisible because this file said otherwise —
        // which is the failure this manifest exists to prevent, working in the wrong direction.
        assertTrue(capabilities.multiviewSupported)
        assertTrue(capabilities.audioSupported)

        // Not released. The gate is what keeps a half-built feature out of the interface, and the
        // whole reason the UI reads this file instead of a Boolean somebody remembered to update.
        assertFalse(capabilities.offlineSupported)
    }

    @Test
    fun `missing and malformed capability fields fail closed`() {
        val malformed = DesktopPlatformCapabilities.parse("""{"offline":{"supported":"yes"}}""")
        val invalid = DesktopPlatformCapabilities.parse("not-json")

        assertFalse(malformed.offlineSupported)
        assertFalse(malformed.multiviewSupported)
        assertFalse(malformed.audioSupported)
        assertFalse(invalid.offlineSupported)
        assertFalse(invalid.multiviewSupported)
        assertFalse(invalid.audioSupported)
    }
}
