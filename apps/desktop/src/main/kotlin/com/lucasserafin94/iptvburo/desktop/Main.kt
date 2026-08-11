package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import com.lucasserafin94.iptvburo.desktop.app.DesktopApp
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.platform.WindowChrome
import com.lucasserafin94.iptvburo.desktop.playback.VlcPluginCache
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.desktop.user.StoredWindowGeometry

/**
 * The window size to open at: the preferred size, shrunk to fit the usable screen.
 *
 * getMaximumWindowBounds already excludes the taskbar, so the result is what the user can actually
 * see. A margin is left so the window does not sit flush against every edge, and a floor keeps the
 * app usable on a very small display rather than collapsing to nothing.
 */
private fun preferredWindowSize(): DpSize {
    val usable =
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }.getOrNull()
    return fitToScreen(usableWidth = usable?.width ?: 0, usableHeight = usable?.height ?: 0)
}

/**
 * The preferred window size, shrunk to what [usableWidth] by [usableHeight] can actually show.
 *
 * Zero means the screen bounds are unknown, in which case the preferred size is used unchanged: a
 * guess is better than opening at the minimum on a large display.
 */
internal fun fitToScreen(
    usableWidth: Int,
    usableHeight: Int,
): DpSize {
    val maxWidth = usableWidth.takeIf { it > 0 }?.dp?.minus(WINDOW_MARGIN) ?: PREFERRED_WIDTH
    val maxHeight = usableHeight.takeIf { it > 0 }?.dp?.minus(WINDOW_MARGIN) ?: PREFERRED_HEIGHT
    return DpSize(
        width = minOf(PREFERRED_WIDTH, maxWidth).coerceAtLeast(MIN_WIDTH),
        height = minOf(PREFERRED_HEIGHT, maxHeight).coerceAtLeast(MIN_HEIGHT),
    )
}

private val PREFERRED_WIDTH = 1_380.dp
private val PREFERRED_HEIGHT = 860.dp
private val MIN_WIDTH = 900.dp
private val MIN_HEIGHT = 560.dp

/**
 * Margin subtracted from the usable screen.
 *
 * Measured, not guessed: a window asked for at exactly the working-area size came back 1550x830 on
 * a 1536x816 desktop, positioned at (-7,-7). Windows adds an invisible resize border of about 7px
 * per edge, so 24dp covers that overshoot and still leaves the window clear of the screen edges.
 */
private val WINDOW_MARGIN = 24.dp

fun main() {
    // First, so nothing printed during startup is lost.
    //
    // The installed app has no console: every diagnostic the code prints went to a stream nobody
    // could read, and reproducing a fault meant running from Gradle — which a customer cannot do,
    // and which does not reproduce a fault that only happens on their machine.
    DiagnosticLog.start()

    // Started before the window and left to run on its own: it takes a few seconds, only happens
    // on the first launch after an install or update, and nothing on screen depends on it.
    //
    // Building the index at build time did not survive packaging — the installer copies the
    // plugins to a different directory with new timestamps, which invalidates every entry — so
    // the index shipped in the MSI was never actually used. Worth doing, though measured to be
    // worth less than assumed on a warm cache; see VlcPluginCache for the numbers.
    //
    // A daemon thread, so a slow or wedged generator can never keep the app from exiting.
    Thread { VlcPluginCache.ensureFreshForBundledRuntime() }
        .apply {
            name = "iptvburo-vlc-cache"
            isDaemon = true
            // Below the UI: this is a startup optimisation and must never compete with drawing.
            priority = Thread.MIN_PRIORITY
        }.start()

    // Coil's network fetcher, registered by hand rather than discovered.
    //
    // coil-network-okhttp announces itself through META-INF/services, and a ServiceLoader finds it
    // only if it looks at the class loader that owns the jar. Under `gradle run` that is the
    // application loader and everything works; in the packaged app it is not, and Coil silently
    // ends up with no way to fetch an http(s) URL at all — every remote poster falls back to its
    // placeholder letter while the URL itself is perfectly good. Verified: the same URLs return
    // 200 image/jpeg through the very OkHttp client Coil would have used.
    //
    // Setting the loader explicitly removes the discovery step, so this cannot depend on how the
    // runtime was assembled.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
    }

    val localRepository = InMemoryCatalogRepository()
    val xtreamRepository = SessionXtreamRepository()
    val rememberedXtreamStore = RememberedXtreamStore()
    val userStore = DesktopUserStore()

    application {
        // Fitted to the screen rather than fixed. A 1380x860 default is taller than a 1536x864
        // laptop panel once the taskbar is subtracted, so the window opened with its lower edge off
        // screen: the last rail sat under the taskbar and the page looked unscrollable because the
        // part that would have scrolled was never visible.
        // What the user left last time, or nothing on a machine that has never been resized.
        val savedGeometry = remember { userStore.windowGeometry() }
        val windowState =
            rememberWindowState(
                size =
                    savedGeometry
                        ?.takeIf { !it.maximised }
                        ?.let { DpSize(it.width.dp, it.height.dp) }
                        ?: preferredWindowSize(),
                position =
                    savedGeometry
                        ?.takeIf { !it.maximised }
                        ?.let { WindowPosition(it.x.dp, it.y.dp) }
                        ?: WindowPosition(Alignment.Center),
                // Always maximised, so Windows itself decides the bounds. Sizing by hand meant
                // fighting the invisible 7px resize border the OS adds on each edge: a window asked
                // for at the working-area height came back 14px larger and hung below the taskbar,
                // taking the last row of the catalogue off screen with it.
                //
                // This used to restore whatever size the window was last closed at, on the grounds
                // that someone who made it small wanted it small. In practice it meant the app
                // opened in a small window after any session that ended that way — including one
                // that ended in the compact player — and a media app that opens small reads as
                // broken. Resizing during a session still works; it simply is not remembered.
                placement = WindowPlacement.Maximized,
            )

        // Remembered so leaving the compact overlay restores the window the user had, rather than
        // snapping back to the default size.
        var restoreSize by remember { mutableStateOf(windowState.size) }
        var restorePosition by remember { mutableStateOf(windowState.position) }
        var compactMode by remember { mutableStateOf(false) }
        var fullScreen by remember { mutableStateOf(false) }

        // Hoisted out of the Window so closing it can dispose the state. It used to be created
        // inside, where onCloseRequest could not reach it, and the background scope that loads the
        // streaming shelves was therefore never cancelled: TMDb requests stayed in flight against a
        // window that no longer existed.
        val appState =
            remember {
                DesktopAppState(
                    localRepository = localRepository,
                    xtreamRepository = xtreamRepository,
                    rememberedXtreamStore = rememberedXtreamStore,
                    userStore = userStore,
                )
            }

        /**
         * Remembers the window for next launch, unless it is in a temporary shape.
         *
         * Full screen and the compact overlay are both modes the user turned on for one title, not
         * a size they chose to keep; storing either would reopen the app in a shape nobody asked
         * for. In those two cases the previously stored geometry is left as it was.
         */
        fun rememberWindowGeometry() {
            if (fullScreen || compactMode) return
            userStore.setWindowGeometry(
                StoredWindowGeometry(
                    maximised = windowState.placement == WindowPlacement.Maximized,
                    width = windowState.size.width.value,
                    height = windowState.size.height.value,
                    x = windowState.position.x.value,
                    y = windowState.position.y.value,
                ),
            )
        }

        Window(
            onCloseRequest = {
                rememberWindowGeometry()
                appState.dispose()
                xtreamRepository.clear()
                localRepository.clear()
                exitApplication()
            },
            state = windowState,
            title = "IPTV BURO",
            // The .ico in the installer covers the shortcut and Explorer; this covers the running
            // window, which is what the taskbar and alt-tab show while the app is open.
            icon = painterResource("brand/buro-mark-512.png"),
        ) {
            LaunchedEffect(appState) {
                appState.restoreRememberedXtream()
            }
            // The title bar is drawn by Windows, not by Compose, so it stayed light against the
            // near-black app. Keyed on placement because the native frame is rebuilt when the
            // window enters or leaves full screen, which drops the attribute.
            LaunchedEffect(windowState.placement) {
                WindowChrome.applyDarkTitleBar(window)
            }
            // True full screen: the frame is removed and the window covers the monitor. Compose's
            // Fullscreen placement only maximises a decorated window, so a border and the taskbar
            // stayed on top of the video.
            LaunchedEffect(fullScreen) {
                WindowChrome.setBorderlessFullScreen(window, fullScreen)
                if (!fullScreen) WindowChrome.applyDarkTitleBar(window)
            }
            DesktopApp(
                appState = appState,
                ownerWindow = window,
                isFullScreen = fullScreen,
                onToggleFullScreen = { fullScreen = !fullScreen },
                isCompact = compactMode,
                onToggleCompact = {
                    if (compactMode) {
                        windowState.size = restoreSize
                        windowState.position = restorePosition
                        compactMode = false
                    } else {
                        // Watch in a corner while doing something else, the behaviour of a
                        // picture-in-picture window elsewhere.
                        restoreSize = windowState.size
                        restorePosition = windowState.position
                        windowState.placement = WindowPlacement.Floating
                        windowState.size = DpSize(width = 480.dp, height = 300.dp)
                        windowState.position = WindowPosition(Alignment.BottomEnd)
                        compactMode = true
                    }
                    // Always on top only while compact. A picture-in-picture window that falls
                    // behind whatever you click next is not one you can watch while working.
                    window.isAlwaysOnTop = compactMode
                },
                onExitForUpdate = {
                    // Same teardown as a normal close: the installer is about to replace this
                    // build, and a request still in flight would hold the old process open.
                    rememberWindowGeometry()
                    appState.dispose()
                    xtreamRepository.clear()
                    localRepository.clear()
                    exitApplication()
                },
            )
        }
    }
}
