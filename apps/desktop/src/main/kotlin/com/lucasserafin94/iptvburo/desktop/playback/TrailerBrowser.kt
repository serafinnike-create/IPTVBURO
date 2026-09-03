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

    init {
        // Registered on the process-wide set below, so a window closing abruptly can still find and
        // silence every trailer that is currently playing.
        //
        // Each instance is disposed through Compose's own DisposableEffect in the normal case, but
        // exitApplication() stops the composition and there is no guarantee every onDispose runs
        // before the process is asked to exit — a heavyweight AWT/Chromium child is exactly the
        // kind of resource that can outlive it. Reported as the trailer's audio still playing, and
        // the process still resident, after the window was gone from the screen and the taskbar.
        live.add(this)
    }

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
        /** Whether the video repeats instead of ending. See TrailerHostServer.pageUrlFor. */
        loop: Boolean = unattended,
        /** Called only after YouTube reports actual playback, not merely a loaded host page. */
        onPlaying: () -> Unit = {},
        /** Called when the player refuses the video or never begins within its readiness window. */
        onFailed: () -> Unit = {},
        /**
         * Called once YouTube itself reports the video has finished — its own state 0, not a guess
         * from a fixed timer. Lets a rotation move to the next title exactly when this one is done,
         * rather than being cut off mid-scene or left frozen on its last frame for however long a
         * flat hold guessed wrong.
         */
        onEnded: () -> Unit = {},
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
                                        val w = browserComponent.width
                                        val h = browserComponent.height
                                        // Measured: "playing" was seen arriving while the panel was
                                        // still genuinely 0x0, before Compose had laid the SwingPanel
                                        // out — coerceAtLeast(1) on this call used to mask exactly
                                        // that and hand CEF a viewport of 1x1. YouTube's own IFrame
                                        // Player script sizes itself from that viewport once, on
                                        // load, and does not reliably re-measure afterward even once
                                        // wasResized() later reports the real size — which is why the
                                        // video kept rendering small and off-centre, with YouTube's
                                        // own controls showing, long after the panel had grown to
                                        // its correct size on screen.
                                        //
                                        // wasResized() is skipped here rather than sent with a
                                        // padded minimum: sending it now would be exactly the wrong
                                        // viewport, permanently, for the reason above. The
                                        // ComponentListener below calls it once real layout exists,
                                        // which is the only viewport this video should ever measure
                                        // itself against.
                                        if (w > 0 && h > 0) {
                                            browser?.wasResized(w, h)
                                            pushPlayerSize(w, h)
                                        }
                                        println("[trailer] playing at ${w}x${h}")
                                        onPlaying()
                                    }
                                "failed" ->
                                    SwingUtilities.invokeLater {
                                        browserComponent.isVisible = false
                                        println(
                                            "[trailer] failed at " +
                                                "${browserComponent.width}x${browserComponent.height}",
                                        )
                                        onFailed()
                                    }
                                "ended" -> SwingUtilities.invokeLater(onEnded)
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
                            loop = loop,
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
                // Tell Chromium its size every time AWT lays the panel out, not only when the page
                // reports playback.
                //
                // The "playing" message carries a wasResized because a windowed child otherwise
                // stays a black rectangle. But that message can arrive while the panel is still
                // being sized — the lightbox opens at its full size in one pass — and then the only
                // resize the child ever gets is the one for the wrong dimensions. The audio ran and
                // the picture stayed black: reported twice, on the film screen's trailer button.
                //
                // A component listener answers whatever the real geometry turns out to be, whenever
                // it settles.
                browserComponent.addComponentListener(
                    object : java.awt.event.ComponentAdapter() {
                        override fun componentResized(event: java.awt.event.ComponentEvent?) {
                            val child = nativeComponent ?: return
                            if (child.width <= 0 || child.height <= 0) return
                            println(
                                "[trailer] AWT layout settled at ${child.width}x${child.height} " +
                                    "(scale ${screenScale()})",
                            )
                            browser?.wasResized(child.width, child.height)
                            // wasResized() changes what CEF paints into — the browser's own render
                            // surface. It does not make YouTube's IFrame Player recompute its
                            // internal layout: measured with a log, that call was reaching CEF
                            // correctly and the video still rendered at whatever size the player
                            // first measured when "playing" fired at 0x0. A resized CSS box around
                            // a cross-origin iframe does not by itself make the page inside it
                            // re-lay-out its own elements; only the iframe's own outer box repaints
                            // at the new size, cropping or padding whatever the page drew at its own
                            // chosen dimensions.
                            //
                            // setSize is YouTube's own documented IFrame API command for exactly
                            // this — telling the player, across the origin boundary a plain resize
                            // event cannot cross, what size to actually lay itself out at.
                            pushPlayerSize(child.width, child.height)
                        }
                    },
                )
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
    /**
     * Tells the YouTube player itself what size to lay out at, over the API a plain resize event
     * cannot cross.
     *
     * `wasResized()` only changes what CEF paints into — the browser's own render surface. It does
     * not make the player recompute its internal layout: measured with a log, that call reached
     * CEF correctly, and the video still rendered at whatever size the player first measured when
     * "playing" fired while the panel was still 0x0. A resized CSS box around a cross-origin iframe
     * repaints the iframe's own outer box at the new size; it does not make the page inside lay
     * itself out again. `setSize` is the IFrame Player API's own command for that, delivered by
     * `postMessage` — the one channel that does cross the origin boundary, which is also how the
     * sound switch above already talks to the same player.
     */
    /**
     * What one AWT pixel is worth on this screen.
     *
     * AWT reports a component's size in logical pixels; the screen draws in physical ones, and on a
     * display scaled to 125% those differ by exactly that factor. Measured: AWT called the banner's
     * panel 735x372 while it occupied 902x467 on screen — 1.25x — and the video, sized from the AWT
     * number, filled 81% of the width and 79% of the height of the frame it sat in. Every "the
     * video is small inside the trailer" report was that one ratio, on both the banner and the
     * Descobrir card.
     *
     * Read from the component rather than from the system: GetDpiForWindow answers 120 for this
     * window while the monitor itself reports 96, so a system-wide DPI query says 100% and misses
     * it entirely. The component's own GraphicsConfiguration is the only source that describes the
     * surface this panel is actually drawn onto.
     *
     * Falls back to 1.0 when there is no configuration yet, which is the right answer for a panel
     * not yet on screen: the layout listener runs again once it is.
     */
    private fun screenScale(): Double =
        nativeComponent
            ?.graphicsConfiguration
            ?.defaultTransform
            ?.scaleX
            ?.takeIf { it > 0.0 }
            ?: 1.0

    private fun pushPlayerSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val live = browser ?: return
        // Scaled to physical pixels: the sizes AWT hands out are logical, and the player draws onto
        // the physical surface. See [screenScale].
        val scale = screenScale()
        val width = (widthPx * scale).toInt().coerceAtLeast(1)
        val height = (heightPx * scale).toInt().coerceAtLeast(1)
        val script =
            """
            (function(){
              var frame=document.querySelector('iframe');
              if(!frame||!frame.contentWindow)return;
              frame.contentWindow.postMessage(JSON.stringify(
                {event:'command',func:'setSize',args:[$width,$height]}),'*');
            })();
            """.trimIndent()
        runCatching { live.executeJavaScript(script, live.url, 0) }
    }

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
        live.remove(this)
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
        // A concurrent set: dispose() runs on the AWT thread through Compose's own lifecycle, and
        // disposeAll() runs from the window's onCloseRequest, which is the same thread here — but
        // nothing prevents a future caller from being elsewhere, and a set that silently corrupted
        // under two removes at once would be a worse bug than the one this exists to close.
        private val live: MutableSet<TrailerBrowser> =
            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

        /**
         * Silences and closes every trailer still playing, wherever it was opened from.
         *
         * Called once, from the window's own close handler, because that is the one path every
         * exit goes through — the banner, Descobrir and the explicit lightbox all end up here
         * without each needing its own shutdown wiring.
         */
        fun disposeAll() {
            live.toList().forEach { browser -> browser.dispose() }
        }

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
            //
            // Age as well as the pid, because a pid alone is not proof of anything across a
            // restart. The operating system hands numbers out again, so a directory left by a run
            // that crashed before rebooting can carry a number some unrelated program now holds —
            // and it would then be spared for ever, its Chromium lock held, with the engine
            // refusing to start every time. Nothing this app made yesterday is still in use today.
            runCatching {
                val staleBefore = System.currentTimeMillis() - CACHE_MAX_AGE_MILLIS
                val ours = ProcessHandle.current().pid()
                root.listFiles { file -> file.name.startsWith(CACHE_PREFIX) }
                    ?.filter { stale ->
                        val pid = stale.name.removePrefix(CACHE_PREFIX).toLongOrNull()
                        // Never our own, whatever its age: a machine left running for days would
                        // otherwise have a second copy of the app delete the cache the first one
                        // is still using.
                        when {
                            pid == ours -> false
                            pid == null -> true
                            ProcessHandle.of(pid).isEmpty -> true
                            else -> stale.lastModified() < staleBefore
                        }
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

        /**
         * Past this, a cache directory is treated as abandoned whatever its pid says.
         *
         * A day, because a session does not outlive one and a pid does not survive a restart with
         * its meaning intact. Generous enough that a machine left running never has its live cache
         * swept from under it.
         */
        private const val CACHE_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

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
