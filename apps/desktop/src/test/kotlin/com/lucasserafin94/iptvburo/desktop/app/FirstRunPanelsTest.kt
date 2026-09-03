package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The first screen of a new install shows one thing at a time.
 *
 * The cache panel and the profile gate occupy the same area, and neither is a dialog that dims what
 * is under it. On a first run both conditions were true at once, so a brand new install opened on
 * "Guardar capas neste computador" and "Quem esta assistindo?" painted through each other, every
 * label unreadable — the worst possible first impression of the app.
 *
 * A source scan, because the real check needs a window, a connected source and an empty profile
 * store. What it pins is that the panel waits for somebody to be watching.
 */
class FirstRunPanelsTest {
    private val app =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    @Test
    fun `the cache panel waits for an active profile`() {
        val marker = "CacheFirstRunPanel(appState = appState)"
        assertTrue(marker in app, "o painel mudou de nome: este teste ja nao le nada")

        val condition = app.substringBefore(marker).substringAfterLast("} else if (")

        assertTrue(
            "appState.activeProfile != null" in condition,
            "o painel de capas volta a desenhar por baixo do ecra de perfis numa instalacao nova",
        )
    }

    /**
     * And the gate itself hides what is behind it when there is no way past it.
     *
     * A 76% scrim reads as a dialog over something worth seeing. On a first run there is nothing
     * behind it yet, and the home screen showed through: the app name landed on a film title.
     */
    @Test
    fun `the profile gate is opaque when it cannot be dismissed`() {
        val marker = "fun DesktopProfileGate("
        assertTrue(marker in app, "o ecra de perfis mudou de nome: este teste ja nao le nada")

        val body = app.substringAfter(marker).substringBefore("contentAlignment = Alignment.Center")

        assertTrue(
            "if (onDismiss == null) BuroColors.Canvas" in body,
            "o ecra de perfis volta a deixar ver a biblioteca por tras numa instalacao nova",
        )
    }
}
