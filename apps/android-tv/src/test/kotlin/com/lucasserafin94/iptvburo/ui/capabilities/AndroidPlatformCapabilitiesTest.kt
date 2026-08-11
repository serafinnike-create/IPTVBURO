package com.lucasserafin94.iptvburo.ui.capabilities

import com.lucasserafin94.iptvburo.ui.AppSection
import com.lucasserafin94.iptvburo.ui.navigation.availableRibbonSections
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPlatformCapabilitiesTest {
    @Test
    fun `offline is enabled on phones but remains hidden on television`() {
        assertTrue(AndroidPlatformCapabilities.offlineSupported)
        assertTrue(AndroidPlatformCapabilities.offlineSupported(isTelevision = false))
        assertFalse(AndroidPlatformCapabilities.offlineSupported(isTelevision = true))
        assertTrue(AppSection.DOWNLOADS in availableRibbonSections())
    }

    @Test
    fun `ribbon implementation remains available for a future supported capability`() {
        assertTrue(AppSection.DOWNLOADS in availableRibbonSections(offlineSupported = true))
    }
}
