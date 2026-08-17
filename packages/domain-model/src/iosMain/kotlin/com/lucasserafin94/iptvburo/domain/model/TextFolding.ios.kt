package com.lucasserafin94.iptvburo.domain.model

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

/**
 * Foundation's own NFD, which is the same Unicode operation the JVM performs.
 *
 * "Canonical mapping" is Apple's name for canonical decomposition — NFD — so an identity minted on
 * an iPhone lands on the same stem as one minted on Android for the same title. That equivalence is
 * what lets a household share favourites across platforms, and it is asserted in `commonTest`
 * rather than assumed: the test runs on every target and would fail here if the two ever diverged.
 */
@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun decomposeForFolding(value: String): String =
    (value as NSString).decomposedStringWithCanonicalMapping
