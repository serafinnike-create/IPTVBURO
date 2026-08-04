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
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore

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
private val WINDOW_MARGIN = 24.dp

fun main() {
    val localRepository = InMemoryCatalogRepository()
    val xtreamRepository = SessionXtreamRepository()
    val rememberedXtreamStore = RememberedXtreamStore()
    val userStore = DesktopUserStore()

    application {
        // Fitted to the screen rather than fixed. A 1380x860 default is taller than a 1536x864
        // laptop panel once the taskbar is subtracted, so the window opened with its lower edge off
        // screen: the last rail sat under the taskbar and the page looked unscrollable because the
        // part that would have scrolled was never visible.
        val windowState =
            rememberWindowState(
                size = preferredWindowSize(),
                position = WindowPosition(Alignment.Center),
            )

        // Remembered so leaving the compact overlay restores the window the user had, rather than
        // snapping back to the default size.
        var restoreSize by remember { mutableStateOf(windowState.size) }
        var restorePosition by remember { mutableStateOf(windowState.position) }
        var compactMode by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
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
            val appState =
                remember {
                    DesktopAppState(
                        localRepository = localRepository,
                        xtreamRepository = xtreamRepository,
                        rememberedXtreamStore = rememberedXtreamStore,
                        userStore = userStore,
                    )
                }
            LaunchedEffect(appState) {
                appState.restoreRememberedXtream()
            }
            // The title bar is drawn by Windows, not by Compose, so it stayed light against the
            // near-black app. Keyed on placement because the native frame is rebuilt when the
            // window enters or leaves full screen, which drops the attribute.
            LaunchedEffect(windowState.placement) {
                WindowChrome.applyDarkTitleBar(window)
            }
            DesktopApp(
                appState = appState,
                ownerWindow = window,
                isFullScreen = windowState.placement == WindowPlacement.Fullscreen,
                onToggleFullScreen = {
                    windowState.placement =
                        if (windowState.placement == WindowPlacement.Fullscreen) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Fullscreen
                        }
                },
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
                },
                onExitForUpdate = {
                    xtreamRepository.clear()
                    localRepository.clear()
                    exitApplication()
                },
            )
        }
    }
}
