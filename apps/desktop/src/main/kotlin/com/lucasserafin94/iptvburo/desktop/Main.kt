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
import com.lucasserafin94.iptvburo.desktop.app.DesktopApp
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.platform.WindowChrome
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore

fun main() {
    val localRepository = InMemoryCatalogRepository()
    val xtreamRepository = SessionXtreamRepository()
    val rememberedXtreamStore = RememberedXtreamStore()
    val userStore = DesktopUserStore()

    application {
        val windowState =
            rememberWindowState(
                size = DpSize(width = 1_380.dp, height = 860.dp),
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
