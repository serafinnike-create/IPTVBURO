package com.lucasserafin94.iptvburo

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvBuroApplication : Application() {
    @OptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        // Third-party playback errors can contain the complete credential-bearing media URI.
        // The app reports a redacted, user-facing failure state instead.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
    }
}
