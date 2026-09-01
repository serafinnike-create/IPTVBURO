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
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import javax.swing.SwingUtilities

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
    private var messageRouter: CefMessageRouter? = null
    private var nativeComponent: java.awt.Component? = null

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
        /** Artwork kept under the Home preview so still image and motion share one surface. */
        artworkUrl: String? = null,
        /**
         * Whether this is the Home banner, which wants the masks that merge it into the hero.
         *
         * False for a trailer in a card of its own: those masks fade the left edge and the bottom
         * into whatever is behind them, and on a card with its own edges they only smear the video.
         */
        blendIntoHero: Boolean = true,
        /** True when the trailer plays beside something else. See TrailerHostServer. */
        unattended: Boolean = false,
        /** Called only after YouTube reports actual playback, not merely a loaded host page. */
        onPlaying: () -> Unit = {},
        /** Called when the player refuses the video or never begins within its readiness window. */
        onFailed: () -> Unit = {},
    ): JPanel? =
        runCatching {
            val app = sharedApp() ?: return null
            val cefClient = app.createClient().also { client = it }
            lateinit var browserComponent: java.awt.Component
            val router =
                CefMessageRouter.create(
                    object : CefMessageRouterHandlerAdapter() {
                        override fun onQuery(
                            browser: CefBrowser?,
                            frame: org.cef.browser.CefFrame?,
                            queryId: Long,
                            request: String?,
                            persistent: Boolean,
                            callback: CefQueryCallback?,
                        ): Boolean {
                            when (request) {
                                "playing" ->
                                    SwingUtilities.invokeLater {
                                        browserComponent.isVisible = true
                                        browserComponent.parent?.revalidate()
                                        browserComponent.repaint()
                                        browser?.wasResized(
                                            browserComponent.width.coerceAtLeast(1),
                                            browserComponent.height.coerceAtLeast(1),
                                        )
                                        onPlaying()
                                    }
                                "failed" ->
                                    SwingUtilities.invokeLater {
                                        browserComponent.isVisible = false
                                        onFailed()
                                    }
                                else -> return false
                            }
                            callback?.success("ok")
                            return true
                        }
                    },
                ).also {
                    messageRouter = it
                    cefClient.addMessageRouter(it)
                }

            // Served from a loopback page rather than loading the embed as the top-level document.
            // YouTube refuses to configure its player for a frame with no page behind it — "Video
            // player configuration error, Error 153" — and a data: URL does not help because its
            // origin is null, which is the same rejection. A real http://127.0.0.1 origin is what
            // an ordinary embedding site has.
            val host = sharedHost() ?: return null
            val cefBrowser =
                cefClient
                    .createBrowser(
                        host.pageUrlFor(
                            youtubeId = youtubeId,
                            autoplay = autoplay,
                            muted = muted,
                            blendIntoHero = blendIntoHero,
                            unattended = unattended,
                            artworkUrl = artworkUrl,
                        ),
                        false,
                        false,
                    )
                    .also { browser = it }

            JPanel(BorderLayout()).apply {
                background = Color.BLACK
                // The native child paints before the loopback page and sits above both this panel
                // and Compose. Its AWT default is white, so colouring only the parent still leaves
                // a bright rectangle during a slow or failed first navigation.
                browserComponent =
                    cefBrowser.uiComponent.apply {
                        background = Color.BLACK
                        // Automatic previews stay withdrawn until the embedded player itself says
                        // it is playing, so slow networks leave the artwork visible. A trailer the
                        // viewer explicitly opened must remain displayable: windowed JCEF does not
                        // create its native peer (and therefore does not run the page) while hidden.
                        isVisible = !unattended
                    }
                nativeComponent = browserComponent
                add(browserComponent, BorderLayout.CENTER)
                // Windowed JCEF otherwise waits for an AWT hierarchy event that SwingPanel does
                // not reliably forward. The page can be playing while the native child remains a
                // black rectangle; creating it now gives the child a real browser context, and the
                // resize notification above makes it paint when Compose reveals it.
                cefBrowser.createImmediately()
            }
        }.getOrNull()

    /**
     * Turns the sound on or off on the player that is already running.
     *
     * A message to the live page, not a new player. The sound used to be baked into the embed URL,
     * so changing it rebuilt the whole browser: the picture went black, the video restarted, and
     * the trailer had to buffer again from nothing. Reported as the switch blanking the screen
     * instead of simply cutting the sound.
     *
     * Best effort by design. If the page has not finished loading, or the player has not answered
     * yet, nothing happens and the trailer keeps whatever sound it had — which is the right failure
     * for a control over decoration.
     */
    fun setSound(enabled: Boolean) {
        val live = browser ?: return
        val command = if (enabled) "unMute" else "mute"
        val script =
            """
            (function(){
              var frame=document.querySelector('iframe');
              if(!frame||!frame.contentWindow)return;
              frame.contentWindow.postMessage(JSON.stringify(
                {event:'command',func:'$command',args:[]}),'*');
              ${if (enabled) """
              frame.contentWindow.postMessage(JSON.stringify(
                {event:'command',func:'setVolume',args:[100]}),'*');
              """ else ""}
            })();
            """.trimIndent()
        runCatching { live.executeJavaScript(script, live.url, 0) }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        // Only this browser and client. CefApp is process-wide and shutting it down would stop
        // every other trailer in the session, including one the user is watching.
        runCatching { nativeComponent?.isVisible = false }
        // Silenced before anything else, and synchronously.
        //
        // `close(true)` asks the render process to go away, and it takes its time — meanwhile the
        // video keeps playing. On a banner that rotates every ten seconds the old trailer was still
        // audible under the new one, and after a few turns several were talking at once. Reported
        // exactly that way. Loading a blank page stops the audio the moment it is asked, so the
        // slow teardown afterwards is silent.
        runCatching { browser?.loadURL("about:blank") }
        runCatching { browser?.close(true) }
        runCatching { messageRouter?.let { client?.removeMessageRouter(it) } }
        runCatching { messageRouter?.dispose() }
        runCatching { client?.dispose() }
        browser = null
        nativeComponent = null
        messageRouter = null
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
                // Chromium's autoplay policy, relaxed for this embedded engine only.
                //
                // The default is `document-user-activation-required`: a page may not start a video
                // until somebody has clicked in it. A banner nobody clicks never satisfies that, so
                // the trailer sat on its first frame however correct the embed URL was — autoplay=1
                // and mute=1 both arrived intact and were simply refused.
                //
                // This is not the muted-audio rule, which stays: the trailer still starts silent
                // and raises the sound once it is playing. It is the separate policy about who
                // asked for the video, and the answer here is that the app did, deliberately, on a
                // page it serves itself.
                val args = config.appArgs + "--autoplay-policy=no-user-gesture-required"
                CefApp.getInstance(args, settings).also { app = it }
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
            val root = File(System.getProperty("java.io.tmpdir"))

            // Anything a previous run left behind, cleared first.
            //
            // Chromium takes a singleton lock on its cache directory. A run that did not shut down
            // cleanly leaves that lock held, and the next start then fails outright —
            // `N_Initialize failed`, and every trailer in the app is a dead rectangle. That is what
            // came back the morning after this was first fixed: the directory was still there from
            // the night before. Sweeping ours at startup means a crash costs the next run nothing.
            // Only the ones whose process is gone: the name carries the pid that made it, so a
            // second copy of the app running right now keeps its own cache and its own lock.
            runCatching {
                root.listFiles { file -> file.name.startsWith(CACHE_PREFIX) }
                    ?.filter { stale ->
                        val pid = stale.name.removePrefix(CACHE_PREFIX).toLongOrNull()
                        pid == null || ProcessHandle.of(pid).isEmpty
                    }?.forEach { stale -> stale.deleteRecursively() }
            }

            // And its own directory per run, so two copies of the app never contend for one lock.
            val dir = File(root, "$CACHE_PREFIX${ProcessHandle.current().pid()}")
            dir.mkdirs()
            Runtime.getRuntime().addShutdownHook(
                Thread { runCatching { dir.deleteRecursively() } },
            )
            return dir.absolutePath
        }

        /** Shared by the sweep and the directory it creates, so the two cannot drift apart. */
        private const val CACHE_PREFIX = "iptvburo-trailer-cache-"

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
