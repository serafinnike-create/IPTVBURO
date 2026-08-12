package com.lucasserafin94.iptvburo.data.licensing

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.google.gson.JsonObject
import com.lucasserafin94.iptvburo.BuildConfig

/**
 * Human-readable Android hardware information for the protected administration panel.
 *
 * Deliberately excludes Android ID, advertising ID, serial, MAC, account and device name. The
 * manufacturer/model combination identifies the physical product for support without creating a
 * tracking identifier.
 */
internal object AndroidDeviceProfile {
    fun report(context: Context): JsonObject =
        JsonObject().apply {
            addProperty("deviceType", deviceType(context))
            addProperty("platform", "ANDROID")
            Build.MANUFACTURER?.trim()?.takeIf(String::isNotBlank)?.let {
                addProperty("manufacturer", it)
            }
            Build.MODEL?.trim()?.takeIf(String::isNotBlank)?.let { addProperty("model", it) }
            addProperty("osVersion", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            addProperty("appVersion", BuildConfig.VERSION_NAME)
        }

    private fun deviceType(context: Context): String {
        val mode =
            (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        return when {
            mode == Configuration.UI_MODE_TYPE_TELEVISION -> "ANDROID_TV"
            context.resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP -> "ANDROID_TABLET"
            else -> "ANDROID_PHONE"
        }
    }

    private const val TABLET_MIN_WIDTH_DP = 600
}
