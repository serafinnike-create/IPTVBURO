package com.lucasserafin94.iptvburo.ui.capabilities

import com.lucasserafin94.iptvburo.BuildConfig

/** Runtime gates generated from `packages/platform-capabilities/android-adaptive.json`. */
internal object AndroidPlatformCapabilities {
    val offlineSupported: Boolean
        get() = BuildConfig.OFFLINE_SUPPORTED
}
