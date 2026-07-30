package com.lucasserafin94.iptvburo.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class BuroPerformanceTierTest {
    @Test
    fun `manual tier always wins`() {
        assertEquals(
            BuroPerformanceTier.Eco,
            resolvePerformanceTier(
                requestedTier = BuroPerformanceTier.Eco,
                autoRecommendation = BuroPerformanceTier.Cinematic,
            ),
        )
    }

    @Test
    fun `auto uses a resolved local recommendation`() {
        assertEquals(
            BuroPerformanceTier.Cinematic,
            BuroPerformanceTier.Auto.resolve(BuroPerformanceTier.Cinematic),
        )
    }

    @Test
    fun `recursive auto recommendation falls back to balanced`() {
        assertEquals(
            BuroPerformanceTier.Balanced,
            BuroPerformanceTier.Auto.resolve(BuroPerformanceTier.Auto),
        )
    }

    @Test
    fun `auto recommends eco when any hard budget is exceeded`() {
        val result =
            recommendAutomaticPerformanceTier(
                BuroPerformanceSignals(
                    availableMemoryMb = 8_192,
                    androidApiLevel = 35,
                    benchmarkFrameTimeMillis = 24.0,
                    droppedFramePercent = 1.0,
                    supportsAdvancedEffects = true,
                ),
            )

        assertEquals(BuroPerformanceTier.Eco, result)
    }

    @Test
    fun `auto only promotes to cinematic with complete positive evidence`() {
        val capableDevice =
            BuroPerformanceSignals(
                availableMemoryMb = 6_144,
                androidApiLevel = 35,
                benchmarkFrameTimeMillis = 11.5,
                droppedFramePercent = 0.8,
                supportsAdvancedEffects = true,
            )
        val incompleteDevice = capableDevice.copy(benchmarkFrameTimeMillis = null)

        assertEquals(
            BuroPerformanceTier.Cinematic,
            recommendAutomaticPerformanceTier(capableDevice),
        )
        assertEquals(
            BuroPerformanceTier.Balanced,
            recommendAutomaticPerformanceTier(incompleteDevice),
        )
    }
}
