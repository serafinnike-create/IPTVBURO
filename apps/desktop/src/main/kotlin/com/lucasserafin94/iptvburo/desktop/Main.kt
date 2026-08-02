package com.lucasserafin94.iptvburo.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lucasserafin94.iptvburo.desktop.app.DesktopApp
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
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

        Window(
            onCloseRequest = {
                xtreamRepository.clear()
                localRepository.clear()
                exitApplication()
            },
            state = windowState,
            title = "IPTV BURO",
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
                onExitForUpdate = {
                    xtreamRepository.clear()
                    localRepository.clear()
                    exitApplication()
                },
            )
        }
    }
}
