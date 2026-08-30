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
        /**
         * Whether this is the Home banner, which wants the masks that merge it into the hero.
         *
         * False for a trailer in a card of its own: those masks fade the left edge and the bottom
         * into whatever is behind them, and on a card with its own edges they only smear the video.
         */
        blendIntoHero: Boolean = true,
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
                    .createBrowser(host.pageUrlFor(youtubeId, autoplay, muted, blendIntoHero), false, false)
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
                        // A cache directory of its own, thrown away when the app exits.
                        //
                        // `cache_path = null` looked like the private choice, and it was what made
                        // every trailer a white rectangle: with no cache path CEF refuses to come
                        // up at all — `N_Initialize failed` in its own log, and then every call on
                        // every browser answering "can't invoke native method … before native
                        // context initialized". The player was created, sized and parented, and had
                        // no engine behind it. That took eight wrong guesses to find, because from
                        // the outside it looks exactly like a rendering fault.
                        //
                        // The privacy intent is kept, and kept better: a directory nobody else
                        // shares, deleted on the way out, so nothing about what was watched
                        // survives the session. See [scratchCache].
                        cache_path = scratchCache()
                        persist_session_cookies = false
                        log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
                        // Black, because Chromium's own default is white.
                        //
                        // The engine paints this before any page arrives, and the browser is a
                        // heavyweight surface above Compose that nothing can clip or cover — so the
                        // default showed as a blank white rectangle wherever a trailer had not yet
                        // loaded. Reported on the Descobrir card and then across the whole banner.
                        //
                        // Set here because this is the only place it can be: CefSettings is read
                        // once, when the engine starts, and the engine is a process-wide singleton.
                        background_color = ColorType(255, 8, 9, 10)
                    }
                CefApp.startup(config.appArgs)
                CefApp.getInstance(config.appArgs, settings).also { app = it }
            }.getOrElse { error ->
                // The type only. The message was included here on the reasoning that a Chromium
                // startup failure talks about libraries rather than media — but the exception is
                // arbitrary, its message is not ours to predict, and "probably safe" is not the
                // standard this repository holds logs to. The type still distinguishes a missing
                // library from a refused initialisation, which is what this line is for.
                println("[trailer] Chromium failed to start: ${error::class.simpleName}")
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
        /**
         * A cache directory for Chromium, emptied when the app exits.
         *
         * CEF will not start without one — see the note where this is used. What it must not be is
         * somewhere permanent: a browser cache inside an IPTV app is a record of what was watched,
         * and nobody asked for that. So it lives under the system temp directory, under this
         * install's own name, and a shutdown hook deletes it.
         *
         * Best effort on the way out: a machine that loses power leaves the directory behind, and
         * the next run reuses that same path and clears it at exit anyway.
         */
        private fun scratchCache(): String {
            val dir = File(System.getProperty("java.io.tmpdir"), "iptvburo-trailer-cache")
            dir.mkdirs()
            Runtime.getRuntime().addShutdownHook(
                Thread { runCatching { dir.deleteRecursively() } },
            )
            return dir.absolutePath
        }

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

        /**
         * Starts Chromium ahead of the first trailer, off the interface thread.
         *
         * Only the first trailer of a session is slow, and this is why: it pays for starting an
         * entire embedded browser. The window gives no sign of it, so pressing "Ver trailer" looks
         * like the app has frozen — reported as exactly that. Doing the work during boot, while the
         * viewer is reading the home screen, moves the wait somewhere it costs nothing.
         *
         * Safe to call more than once and safe to call on a machine with no Chromium: [sharedApp]
         * is synchronised, caches its result, and remembers a failure so this cannot become a
         * repeated cost.
         *
         * A daemon thread deliberately. This is optional work, and a non-daemon thread part-way
         * through starting Chromium would hold the process open after the window closed.
         */
        fun warmUp() {
            if (warming.getAndSet(true)) return
            Thread { runCatching { sharedApp() } }
                .apply {
                    name = "iptvburo-trailer-warmup"
                    isDaemon = true
                    // Below the interface, like the other startup work: this is an optimisation
                    // and must never compete with drawing the window.
                    priority = Thread.MIN_PRIORITY
                }.start()
        }

        /** So a second call does not start a second thread racing the first into the lock. */
        private val warming = AtomicBoolean(false)
    }
}
