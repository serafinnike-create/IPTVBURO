package com.lucasserafin94.iptvburo.domain.model

import java.text.Normalizer

/**
 * The JVM's own NFD, which is what this code has always called.
 *
 * Kept exactly as it was rather than replaced with a shared implementation: Android and Windows are
 * shipping, and their stored identities were produced by this call. Anything else here — however
 * equivalent it looked — would be a bet against data that already exists on people's devices.
 */
internal actual fun decomposeForFolding(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
