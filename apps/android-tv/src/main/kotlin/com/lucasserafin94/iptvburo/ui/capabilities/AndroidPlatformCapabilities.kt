package com.lucasserafin94.iptvburo.ui.capabilities

import android.content.Context
import android.content.res.Configuration
import com.lucasserafin94.iptvburo.BuildConfig

/** Runtime gates generated from `packages/platform-capabilities/android-adaptive.json`. */
internal object AndroidPlatformCapabilities {
    /** Build-time engine gate; form-factor policy is applied by [offlineSupported]. */
    val offlineSupported: Boolean
        get() = BuildConfig.OFFLINE_SUPPORTED

    fun offlineSupported(context: Context): Boolean =
        offlineSupported(context.resources.configuration)

    fun offlineSupported(configuration: Configuration): Boolean =
        offlineSupported &&
            configuration.uiMode and Configuration.UI_MODE_TYPE_MASK !=
            Configuration.UI_MODE_TYPE_TELEVISION

    internal fun offlineSupported(isTelevision: Boolean): Boolean =
        offlineSupported && !isTelevision
}
