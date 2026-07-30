package com.lucasserafin94.iptvburo.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuroMotionPolicyTest {
    @Test
    fun `reduced motion removes zoom parallax video and animated transitions`() {
        val policy =
            resolveMotionPolicy(
                performanceTier = BuroPerformanceTier.Cinematic,
                preferences = BuroUiPreferences(reducedMotion = true),
            )

        assertEquals(1f, policy.focusScale)
        assertEquals(1f, policy.pressedScale)
        assertFalse(policy.hasAnimatedTransitions)
        assertFalse(policy.allowsFocusZoom)
        assertFalse(policy.allowsCrossfade)
        assertFalse(policy.allowsParallax)
        assertFalse(policy.allowsBackdropVideo)
        assertFalse(policy.allowsRealtimeBlur)
        assertFalse(policy.allowsSharedTransitions)
        assertFalse(policy.allowsSkeletonPulse)
    }

    @Test
    fun `eco keeps only a short simple focus animation`() {
        val policy = resolveMotionPolicy(BuroPerformanceTier.Eco)

        assertTrue(policy.focusDurationMillis in 80..180)
        assertTrue(policy.allowsFocusZoom)
        assertFalse(policy.allowsCrossfade)
        assertFalse(policy.allowsParallax)
        assertFalse(policy.allowsBackdropVideo)
        assertFalse(policy.allowsRealtimeBlur)
        assertFalse(policy.allowsSkeletonPulse)
    }

    @Test
    fun `balanced enables premium focus without cinematic effects`() {
        val policy = resolveMotionPolicy(BuroPerformanceTier.Balanced)

        assertTrue(policy.allowsFocusZoom)
        assertTrue(policy.allowsCrossfade)
        assertTrue(policy.allowsSkeletonPulse)
        assertFalse(policy.allowsParallax)
        assertFalse(policy.allowsBackdropVideo)
        assertFalse(policy.allowsRealtimeBlur)
    }

    @Test
    fun `reduced transparency disables cinematic realtime blur`() {
        val policy =
            resolveMotionPolicy(
                performanceTier = BuroPerformanceTier.Cinematic,
                preferences = BuroUiPreferences(reducedTransparency = true),
            )

        assertTrue(policy.allowsParallax)
        assertTrue(policy.allowsBackdropVideo)
        assertFalse(policy.allowsRealtimeBlur)
        assertFalse(policy.allowsSkeletonPulse)
    }
}
