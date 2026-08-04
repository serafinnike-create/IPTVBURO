package com.lucasserafin94.iptvburo.desktop.playback

import java.awt.BorderLayout
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser

/**
 * Plays a trailer inside the app, using an embedded Chromium.
 *
 * VLC cannot do this. Asked to open a YouTube page its own module answers "Couldn't extract youtube
 * video URL, please check for updates to this script" — the script ships with VLC 3.0.23 and the
 * site has changed since. A real browser engine is the only thing that plays these, which is why
 * the installer carries one.
 *
 * The page loaded is YouTube's own embed player, not a scraped stream: nothing here extracts a
 * hidden URL or works around a restriction. A video the uploader has disabled embedding for will
 * say so, and that is the correct outcome rather than something to defeat.
 */
class TrailerBrowser {
    private val disposed = AtomicBoolean(false)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null

    /**
     * Builds the panel showing [youtubeId], or null when Chromium is unavailable.
     *
     * Null rather than an exception: the trailer is an extra, and a machine where the engine fails
     * to start must still play films. The caller falls back to opening the browser.
     */
    fun createComponent(
        youtubeId: String,
        autoplay: Boolean,
        muted: Boolean,
    ): JPanel? =
        runCatching {
            val app = sharedApp() ?: return null
            val cefClient = app.createClient().also { client = it }
            // The embed URL with the controls the situation calls for: a trailer opened deliberately
            // gets controls, one playing behind a banner does not and must never make noise
            // unasked.
            val url =
                buildString {
                    append("https://www.youtube-nocookie.com/embed/")
                    append(youtubeId)
                    append("?autoplay=").append(if (autoplay) 1 else 0)
                    append("&mute=").append(if (muted) 1 else 0)
                    append("&controls=").append(if (muted) 0 else 1)
                    append("&modestbranding=1&rel=0&playsinline=1")
                    if (muted) append("&loop=1&playlist=").append(youtubeId)
                }
            val cefBrowser = cefClient.createBrowser(url, false, false).also { browser = it }

            JPanel(BorderLayout()).apply {
                background = Color.BLACK
                add(cefBrowser.uiComponent, BorderLayout.CENTER)
            }
        }.getOrNull()

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        // Only this browser and client. CefApp is process-wide and shutting it down would stop
        // every other trailer in the session, including one the user is watching.
        runCatching { browser?.close(true) }
        runCatching { client?.dispose() }
        browser = null
        client = null
    }

    companion object {
        @Volatile
        private var app: CefApp? = null

        @Volatile
        private var unavailable = false

        /**
         * The one Chromium instance for the process.
         *
         * CefApp cannot be initialised twice, and a failure is remembered so a machine without the
         * native libraries does not pay the startup cost again on every trailer.
         */
        @Synchronized
        private fun sharedApp(): CefApp? {
            if (unavailable) return null
            app?.let { return it }
            return runCatching {
                val settings =
                    CefSettings().apply {
                        windowless_rendering_enabled = false
                        // Nothing about a trailer needs to survive the session, and a browser cache
                        // inside an IPTV app is a store of browsing history nobody asked for.
                        cache_path = null
                        persist_session_cookies = false
                        log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
                    }
                CefApp.startup(emptyArray())
                CefApp.getInstance(settings).also { app = it }
            }.getOrElse {
                unavailable = true
                null
            }
        }

        /** Whether an embedded trailer can be shown at all on this machine. */
        fun isAvailable(): Boolean = sharedApp() != null
    }
}
