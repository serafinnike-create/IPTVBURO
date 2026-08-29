package com.lucasserafin94.iptvburo.ui

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live guide is reachable and connected on the television.
 *
 * A source scan, because the screen needs an Android runtime and a provider answering EPG requests.
 * The arithmetic behind it is covered by LiveGuideTest in the shared domain model; what is worth
 * pinning here is that the screen exists, that something opens it, and that its callbacks go
 * somewhere — a composable nobody renders and a handler nobody calls both compile perfectly.
 */
class LiveGuideWiringTest {
    private val shell =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt").readText()
    private val screen =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/LiveGuideScreen.kt").readText()
    private val viewModel =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt").readText()
    private val activity =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/MainActivity.kt").readText()

    /** The section exists and the shell renders the guide for it. */
    @Test
    fun `the guide has a section that renders it`() {
        assertTrue(
            "o guia nao tem seccao propria",
            viewModel.contains("AppSection.GUIDE -> {"),
        )
        assertTrue(
            "o destino do guia nao mostra o guia",
            shell.contains("is AppContent.Guide -> LiveGuideScreen("),
        )
    }

    /** And it is on the ribbon, where the other sections are. */
    @Test
    fun `the guide is on the ribbon`() {
        assertTrue(
            "o guia nao aparece na fita de navegacao",
            shell.contains("AppSection.GUIDE,"),
        )
        assertTrue(
            "o guia nao tem nome na fita",
            shell.contains("AppSection.GUIDE -> R.string.buro_nav_guide"),
        )
    }

    /**
     * Landing on a row with the remote shows that channel's schedule.
     *
     * On a television the D-pad is how everything moves, so focus is selection: asking for a second
     * press to see what is on would be the work the guide exists to remove.
     */
    @Test
    fun `focus selects the channel on a television`() {
        assertTrue(
            "chegar a uma linha com o comando nao mostra a programacao",
            screen.contains("LaunchedEffect(isFocused) { if (isFocused) onFocus() }"),
        )
        assertTrue(
            "nada liga o foco ao modelo",
            activity.contains("onFocusGuideChannel = viewModel::focusGuideChannel"),
        )
    }

    /** The focused channel and the rows either side are fetched. */
    @Test
    fun `the focused channel and its neighbours are fetched`() {
        assertTrue(
            "o canal em foco nao tem a programacao pedida",
            viewModel.contains("fetchGuideSchedule(channel)"),
        )
        assertTrue(
            "so o canal em foco e carregado, e descer a lista espera a cada linha",
            viewModel.contains("LiveGuide.prefetchWindow(index, channels.size)"),
        )
    }

    /** A schedule already in hand is not asked for again. */
    @Test
    fun `a fresh schedule is not fetched again`() {
        assertTrue(
            "a programacao e pedida outra vez mesmo quando ja esta em maos",
            viewModel.contains("LiveGuide.isFresh(fetchedAt, nowSeconds)"),
        )
        assertTrue(
            "o mesmo canal pode ser pedido duas vezes ao mesmo tempo",
            viewModel.contains("if (!guideInFlight.add(channel.id)) return"),
        )
    }

    /**
     * The held schedules are bounded.
     *
     * Each is a few hours of programmes for one channel, and an evening of browsing a
     * four-hundred-channel list would otherwise hold the whole catalogue's schedule in memory.
     */
    @Test
    fun `the held schedules are bounded`() {
        assertTrue(
            "as programacoes guardadas crescem sem limite",
            viewModel.contains("state.guideSchedules.size >= LiveGuide.MAX_CACHED_SCHEDULES"),
        )
    }

    /**
     * The list is keyed on the position as well as the id.
     *
     * A provider files one stream under two categories and sends it twice, and with two
     * subscriptions merged the same number arrives from both. A duplicate key is a crash.
     */
    @Test
    fun `the channel list cannot collide on a key`() {
        assertTrue(
            "a lista de canais pode ter chaves repetidas",
            screen.contains("key = { index, channel -> \"\$index:\${channel.id}\" }"),
        )
    }

    /**
     * Watching uses the ordinary route.
     *
     * Its own would need its own resume decision and its own progress identity, and the two would
     * drift from the ones every other screen uses.
     */
    @Test
    fun `watching from the guide uses the ordinary route`() {
        val branch = shell.substringAfter("is AppContent.Guide -> LiveGuideScreen(").substringBefore(")")

        assertTrue(
            "o guia toca por um caminho proprio",
            branch.contains("onWatch = onOpenChannel"),
        )
    }
}
