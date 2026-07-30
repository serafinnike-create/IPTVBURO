package com.lucasserafin94.iptvburo.ui.designsystem

/**
 * Rendering tiers are deliberately product concepts rather than device classes. A user choice
 * always wins; [Auto] is the only value that may be replaced by a local recommendation.
 */
enum class BuroPerformanceTier {
    Eco,
    Balanced,
    Cinematic,
    Auto,
    ;

    fun resolve(autoRecommendation: BuroPerformanceTier = Balanced): BuroPerformanceTier =
        resolvePerformanceTier(
            requestedTier = this,
            autoRecommendation = autoRecommendation,
        )
}

typealias PerformanceTier = BuroPerformanceTier

data class BuroUiPreferences(
    val performanceTier: BuroPerformanceTier = BuroPerformanceTier.Auto,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val reducedTransparency: Boolean = false,
) {
    companion object {
        /**
         * Safe before preferences and the device benchmark have loaded: no accessibility override
         * is guessed and Auto resolves to the moderate Balanced tier.
         */
        val SafeDefaults = BuroUiPreferences()
    }
}

/**
 * Optional, local-only signals produced by a short device benchmark.
 *
 * Missing or invalid values never promote a device to Cinematic. This keeps the first launch
 * useful while avoiding expensive effects before there is enough evidence for them.
 */
data class BuroPerformanceSignals(
    val availableMemoryMb: Int? = null,
    val androidApiLevel: Int? = null,
    val benchmarkFrameTimeMillis: Double? = null,
    val droppedFramePercent: Double? = null,
    val supportsAdvancedEffects: Boolean? = null,
)

object BuroPerformanceThresholds {
    const val EcoMaximumMemoryMb = 1_536
    const val CinematicMinimumMemoryMb = 4_096
    const val EcoMaximumAndroidApi = 25
    const val CinematicMinimumAndroidApi = 29
    const val EcoFrameTimeMillis = 20.0
    const val CinematicFrameTimeMillis = 14.0
    const val EcoDroppedFramePercent = 8.0
    const val CinematicDroppedFramePercent = 2.0
}

fun resolvePerformanceTier(
    requestedTier: BuroPerformanceTier,
    autoRecommendation: BuroPerformanceTier = BuroPerformanceTier.Balanced,
): BuroPerformanceTier {
    if (requestedTier != BuroPerformanceTier.Auto) return requestedTier
    return autoRecommendation.takeUnless { it == BuroPerformanceTier.Auto }
        ?: BuroPerformanceTier.Balanced
}

fun recommendAutomaticPerformanceTier(
    signals: BuroPerformanceSignals,
): BuroPerformanceTier {
    val memoryMb = signals.availableMemoryMb.validPositiveOrNull()
    val apiLevel = signals.androidApiLevel.validPositiveOrNull()
    val frameTime = signals.benchmarkFrameTimeMillis.validNonNegativeOrNull()
    val droppedFrames = signals.droppedFramePercent.validPercentageOrNull()

    val needsEcoTier =
        memoryMb?.let { it <= BuroPerformanceThresholds.EcoMaximumMemoryMb } == true ||
            apiLevel?.let { it <= BuroPerformanceThresholds.EcoMaximumAndroidApi } == true ||
            frameTime?.let { it > BuroPerformanceThresholds.EcoFrameTimeMillis } == true ||
            droppedFrames?.let { it > BuroPerformanceThresholds.EcoDroppedFramePercent } == true
    if (needsEcoTier) return BuroPerformanceTier.Eco

    val supportsCinematicTier =
        memoryMb?.let { it >= BuroPerformanceThresholds.CinematicMinimumMemoryMb } == true &&
            apiLevel?.let { it >= BuroPerformanceThresholds.CinematicMinimumAndroidApi } == true &&
            frameTime?.let { it <= BuroPerformanceThresholds.CinematicFrameTimeMillis } == true &&
            droppedFrames?.let {
                it <= BuroPerformanceThresholds.CinematicDroppedFramePercent
            } == true &&
            signals.supportsAdvancedEffects == true

    return if (supportsCinematicTier) {
        BuroPerformanceTier.Cinematic
    } else {
        BuroPerformanceTier.Balanced
    }
}

fun resolvePerformanceTier(
    requestedTier: BuroPerformanceTier,
    signals: BuroPerformanceSignals,
): BuroPerformanceTier =
    resolvePerformanceTier(
        requestedTier = requestedTier,
        autoRecommendation = recommendAutomaticPerformanceTier(signals),
    )

data class BuroMotionPolicy(
    val focusDurationMillis: Int,
    val navigationDurationMillis: Int,
    val cinematicDurationMillis: Int,
    val focusScale: Float,
    val pressedScale: Float,
    val allowsFocusZoom: Boolean,
    val allowsCrossfade: Boolean,
    val allowsParallax: Boolean,
    val allowsBackdropVideo: Boolean,
    val allowsRealtimeBlur: Boolean,
    val allowsSharedTransitions: Boolean,
    val allowsSkeletonPulse: Boolean,
) {
    val hasAnimatedTransitions: Boolean
        get() =
            focusDurationMillis > 0 ||
                navigationDurationMillis > 0 ||
                cinematicDurationMillis > 0
}

fun resolveMotionPolicy(
    performanceTier: BuroPerformanceTier,
    preferences: BuroUiPreferences = BuroUiPreferences.SafeDefaults,
): BuroMotionPolicy {
    val resolvedTier = performanceTier.resolve()

    if (preferences.reducedMotion) {
        return BuroMotionPolicy(
            focusDurationMillis = 0,
            navigationDurationMillis = 0,
            cinematicDurationMillis = 0,
            focusScale = 1f,
            pressedScale = 1f,
            allowsFocusZoom = false,
            allowsCrossfade = false,
            allowsParallax = false,
            allowsBackdropVideo = false,
            allowsRealtimeBlur = false,
            allowsSharedTransitions = false,
            allowsSkeletonPulse = false,
        )
    }

    return when (resolvedTier) {
        BuroPerformanceTier.Eco ->
            BuroMotionPolicy(
                focusDurationMillis = 100,
                navigationDurationMillis = 180,
                cinematicDurationMillis = 280,
                focusScale = 1.025f,
                pressedScale = 0.99f,
                allowsFocusZoom = true,
                allowsCrossfade = false,
                allowsParallax = false,
                allowsBackdropVideo = false,
                allowsRealtimeBlur = false,
                allowsSharedTransitions = false,
                allowsSkeletonPulse = false,
            )

        BuroPerformanceTier.Balanced ->
            BuroMotionPolicy(
                focusDurationMillis = 160,
                navigationDurationMillis = 240,
                cinematicDurationMillis = 360,
                focusScale = 1.045f,
                pressedScale = 0.985f,
                allowsFocusZoom = true,
                allowsCrossfade = true,
                allowsParallax = false,
                allowsBackdropVideo = false,
                allowsRealtimeBlur = false,
                allowsSharedTransitions = false,
                allowsSkeletonPulse = !preferences.reducedTransparency,
            )

        BuroPerformanceTier.Cinematic ->
            BuroMotionPolicy(
                focusDurationMillis = 160,
                navigationDurationMillis = 280,
                cinematicDurationMillis = 460,
                focusScale = 1.045f,
                pressedScale = 0.985f,
                allowsFocusZoom = true,
                allowsCrossfade = true,
                allowsParallax = true,
                allowsBackdropVideo = true,
                allowsRealtimeBlur = !preferences.reducedTransparency,
                allowsSharedTransitions = true,
                allowsSkeletonPulse = !preferences.reducedTransparency,
            )

        BuroPerformanceTier.Auto -> error("Auto is resolved before selecting a motion policy")
    }
}

private fun Int?.validPositiveOrNull(): Int? = this?.takeIf { it > 0 }

private fun Double?.validNonNegativeOrNull(): Double? =
    this?.takeIf { it.isFinite() && it >= 0.0 }

private fun Double?.validPercentageOrNull(): Double? =
    validNonNegativeOrNull()?.takeIf { it <= 100.0 }
