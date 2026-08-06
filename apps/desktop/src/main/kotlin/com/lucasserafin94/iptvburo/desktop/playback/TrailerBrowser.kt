package com.lucasserafin94.iptvburo.desktop.playback

import com.jetbrains.cef.JCefAppConfig
import java.awt.BorderLayout
import java.awt.Color
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.SystemBootstrap
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

            // Served from a loopback page rather than loading the embed as the top-level document.
            // YouTube refuses to configure its player for a frame with no page behind it — "Video
            // player configuration error, Error 153" — and a data: URL does not help because its
            // origin is null, which is the same rejection. A real http://127.0.0.1 origin is what
            // an ordinary embedding site has.
            val host = sharedHost() ?: return null
            val cefBrowser =
                cefClient
                    .createBrowser(host.pageUrlFor(youtubeId, autoplay, muted), false, false)
                    .also { browser = it }

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

        @Volatile
        private var host: TrailerHostServer? = null

        /**
         * The one loopback page server for the process.
         *
         * Started on first use and left running: it holds a single socket and one route, and
         * restarting it per trailer would churn ports for no gain.
         */
        @Synchronized
        private fun sharedHost(): TrailerHostServer? {
            host?.let { return it }
            return TrailerHostServer.start()?.also { started ->
                host = started
                println("[trailer] host page server on ${started.origin}")
            } ?: run {
                println("[trailer] host page server failed to bind")
                null
            }
        }

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
            // Checked before anything else, because CefApp.startup and getInstance both *succeed*
            // without the native runtime present — they hand back an object whose first browser
            // then renders a blank white rectangle the user has to dismiss. Asking the loader for
            // the library is the only reliable way to tell an installed runtime from a missing one.
            val runtime = locateNativeRuntime()
            if (runtime == null) {
                println("[trailer] no Chromium runtime found; falling back to the system browser")
                unavailable = true
                return null
            }
            println("[trailer] Chromium runtime at ${runtime.absolutePath}")
            return runCatching {
                // Chromium lives beside the app, not on the library path, so the bundle directory
                // has to be handed to JCEF explicitly. JCefAppConfig derives the whole layout from
                // it: the loader that System.load()s libcef.dll by absolute path, plus the resource,
                // locale and helper-process paths. Without this, startup "succeeds" and the first
                // browser renders a blank white rectangle.
                val config = JCefAppConfig.getInstance(runtime.absolutePath)
                SystemBootstrap.setLoader(config.loader)
                val settings =
                    config.cefSettings.apply {
                        windowless_rendering_enabled = false
                        // Nothing about a trailer needs to survive the session, and a browser cache
                        // inside an IPTV app is a store of browsing history nobody asked for.
                        cache_path = null
                        persist_session_cookies = false
                        log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
                    }
                CefApp.startup(config.appArgs)
                CefApp.getInstance(config.appArgs, settings).also { app = it }
            }.getOrElse { error ->
                // The type and message only — no paths, no URLs. Enough to tell a missing library
                // from a refused initialisation, which look identical from the UI.
                println("[trailer] Chromium failed to start: ${error::class.simpleName}: ${error.message}")
                unavailable = true
                null
            }
        }

        /**
         * The directory holding Chromium's native runtime, or null when it is not on this machine.
         *
         * The installed layout comes first — that is where the installer puts it — then the same
         * directory as produced by a Gradle build run from the repository, then the library path for
         * a runtime installed elsewhere and pointed at by the launcher. A missing runtime is an
         * ordinary case rather than an error, so this must be cheap and must never throw: the caller
         * falls back to the system browser.
         */
        private fun locateNativeRuntime(): File? {
            val resources = System.getProperty("compose.application.resources.dir")?.let(::File)
            val workingDirectory = File(System.getProperty("user.dir"))
            val candidates =
                listOfNotNull(
                    resources?.resolve("jcef"),
                    resources?.resolve("windows/jcef"),
                    workingDirectory.resolve("apps/desktop/build/generated/app-resources/windows/jcef"),
                ) +
                    System.getProperty("java.library.path").orEmpty()
                        .split(File.pathSeparator)
                        .filter(String::isNotBlank)
                        .map(::File)
            return runCatching {
                candidates.firstOrNull { directory -> directory.resolve(CHROMIUM_LIBRARY).isFile }
            }.getOrNull()
        }

        private const val CHROMIUM_LIBRARY = "libcef.dll"

        /** Whether an embedded trailer can be shown at all on this machine. */
        fun isAvailable(): Boolean = sharedApp() != null
    }
}
