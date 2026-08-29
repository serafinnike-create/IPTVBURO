package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The live guide is reachable and connected.
 *
 * A source scan, because the screen needs a window with a live catalogue and a provider answering
 * EPG requests. The arithmetic behind it is covered by LiveGuideTest in the domain model; what is
 * worth pinning here is that the screen exists, that something opens it, and that its callbacks go
 * somewhere — a composable nobody renders and a handler nobody calls both compile perfectly.
 */
class LiveGuideWiringTest {
    private val app =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()
    private val screen =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/LiveGuideScreen.kt").readText()
    private val state =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()

    /** There is a way in, and it is in the sidebar where the other sections are. */
    @Test
    fun `the guide can be opened from the sidebar`() {
        assertTrue(
            app.contains("onGuide = { scope.launch { appState.openGuide() } }"),
            "nada abre o guia",
        )
        assertTrue(
            app.contains("selected = destination == DesktopDestination.GUIDE"),
            "o guia nao tem entrada propria na barra lateral",
        )
    }

    /** And the destination actually renders the screen rather than falling through. */
    @Test
    fun `the guide destination renders the guide`() {
        val branch =
            app.substringAfter("visibleDestination == DesktopDestination.GUIDE) {")
                .substringBefore("} else if")

        assertTrue(branch.contains("LiveGuideScreen("), "o destino do guia nao mostra o guia")
    }

    /**
     * Focusing a channel fetches its schedule.
     *
     * The whole screen is the schedule beside the list; a focus that changed a highlight and asked
     * for nothing would leave the right-hand column empty for ever.
     */
    @Test
    fun `focusing a channel fetches its schedule`() {
        assertTrue(
            app.contains("scope.launch { appState.focusGuideChannel(channelId) }"),
            "mudar de canal no guia nao vai buscar nada",
        )
        assertTrue(
            state.contains("fetchGuideSchedule(channelId)"),
            "o canal em foco nao tem a programacao pedida",
        )
    }

    /**
     * And the rows either side are fetched with it.
     *
     * Moving down a list one row at a time is how a guide is read, so the next few are worth having
     * before they are reached — see LiveGuide.prefetchWindow.
     */
    @Test
    fun `the neighbouring channels are fetched too`() {
        assertTrue(
            state.contains("LiveGuide.prefetchWindow(index, channels.size)"),
            "so o canal em foco e carregado, e descer a lista espera a cada linha",
        )
    }

    /**
     * A schedule already in hand is not asked for again.
     *
     * Moving down and back up a list is ordinary, and a request per pass would be a request for
     * data that has not changed.
     */
    @Test
    fun `a fresh schedule is not fetched again`() {
        assertTrue(
            state.contains("LiveGuide.isFresh(held.fetchedAtEpochSeconds, nowSeconds)"),
            "a programacao e pedida outra vez mesmo quando ja esta em maos",
        )
    }

    /** The same channel is never fetched twice at once. */
    @Test
    fun `a channel already being fetched is not fetched again`() {
        assertTrue(
            state.contains("if (!guideInFlight.add(channelId)) return"),
            "o mesmo canal pode ser pedido duas vezes ao mesmo tempo",
        )
    }

    /**
     * The clock moves.
     *
     * A guide whose "now" was fixed when the screen opened keeps a finished programme at the top
     * and a progress bar frozen where it started.
     */
    @Test
    fun `the guide clock ticks`() {
        assertTrue(screen.contains("delay(60_000L)"), "o relogio do guia nao anda")
        assertTrue(
            app.contains("nowEpochSeconds = rememberGuideClock()"),
            "o ecra nao recebe um relogio que ande",
        )
    }

    /**
     * Watching plays where it stands.
     *
     * From a guide the viewer has already read what is on and pressed Watch; sending them to a page
     * about the channel to press play again is a step that answers nothing.
     */
    @Test
    fun `watching from the guide plays straight away`() {
        val branch =
            app.substringAfter("visibleDestination == DesktopDestination.GUIDE) {")
                .substringBefore("} else if")

        assertTrue(
            branch.contains("appState.prepareXtreamPlayback("),
            "o botao de ver nao toca nada",
        )
        // Through the shared builder, so the progress identity and the buffer decision are the ones
        // every other screen uses rather than a second opinion.
        assertTrue(
            branch.contains("XtreamPlaybackTarget.CatalogItem("),
            "o guia constroi o alvo de reproducao a sua maneira",
        )
    }

    /** And the screen is named in every language the app ships. */
    @Test
    fun `every language names the guide`() {
        DesktopLanguage.entries.forEach { language ->
            val screens = DesktopStrings.of(language).shareStrings.screens
            assertTrue(screens.guideTitle.isNotBlank(), "guideTitle vazio em $language")
            assertTrue(screens.guideNoSchedule.isNotBlank(), "guideNoSchedule vazio em $language")
            assertTrue(screens.guideWatch.isNotBlank(), "guideWatch vazio em $language")
        }
    }

    /**
     * The pointer merely crossing a row does not select it.
     *
     * Following the pointer was tried and is worse in the hand: the mouse passes over rows on its
     * way to somewhere else, each pass changed the channel and started a stream, and the viewer
     * ends up fighting the screen to reach the one they wanted. Reported after using it.
     */
    @Test
    fun `hovering a channel does not select it`() {
        assertTrue(
            !screen.contains("collectIsHoveredAsState"),
            "o canal volta a ser seleccionado so por passar o rato por cima",
        )
    }

    /** And the arrows move between channels, with Enter playing the one in focus. */
    @Test
    fun `the arrow keys move through the guide`() {
        assertTrue(screen.contains("Key.DirectionDown ->"), "a seta para baixo nao faz nada")
        assertTrue(screen.contains("Key.DirectionUp ->"), "a seta para cima nao faz nada")
        assertTrue(screen.contains("Key.Enter, Key.NumPadEnter ->"), "o Enter nao toca o canal")
        // The keyboard is taken on arrival: on a guide the arrows are how somebody moves, and
        // needing a click first is a step nobody expects.
        assertTrue(
            screen.contains("LaunchedEffect(Unit) { runCatching { keyboard.requestFocus() } }"),
            "as setas so funcionam depois de um clique",
        )
    }

    /**
     * The list follows the focus.
     *
     * Without it the arrows move the selection off the bottom of the screen and the viewer is
     * driving something they cannot see.
     */
    @Test
    fun `the list scrolls to keep the focus visible`() {
        assertTrue(
            screen.contains("listState.animateScrollToItem(index)"),
            "a lista nao acompanha o canal em foco",
        )
    }

    /**
     * Both columns say they scroll.
     *
     * Four hundred channels and a day of schedule both run past the fold, and with nothing on
     * screen saying so the rest looks like it does not exist.
     */
    @Test
    fun `both columns have a scrollbar`() {
        assertTrue(
            (screen.split("VerticalScrollbar(").size - 1) >= 2,
            "falta a barra de rolagem numa das colunas do guia",
        )
    }

    /**
     * Channel names are cleaned like everywhere else.
     *
     * A viewer reading a channel list does not need "[FHD]" on every row to know their subscription
     * carries HD.
     */
    @Test
    fun `channel names are cleaned`() {
        assertTrue(
            screen.contains("channel.name.editorialTitle()"),
            "os nomes dos canais mostram as marcas de qualidade do fornecedor",
        )
    }

    /**
     * The focused channel plays in the panel.
     *
     * Selecting a channel and then having to press play to hear it is the step a guide exists to
     * remove — a satellite box starts the channel as you land on it.
     */
    @Test
    fun `the focused channel plays in the panel`() {
        assertTrue(screen.contains("GuidePreview("), "o canal em foco nao toca no painel")
        assertTrue(
            app.contains("previewRequestFor = { channel ->"),
            "nada fornece o fluxo para a previa",
        )
    }

    /**
     * But only once the focus stops moving.
     *
     * The focus follows the pointer and the arrow keys, so a sweep down the list would open and
     * abandon a connection per row. Many subscriptions allow only one at a time, and a viewer
     * scanning their own guide would lock themselves out of their own account.
     */
    @Test
    fun `the preview waits for the focus to settle`() {
        assertTrue(
            screen.contains("delay(PREVIEW_SETTLE_MILLIS)"),
            "a previa abre um fluxo em cada linha por onde o foco passa",
        )
    }

    /**
     * And the preview goes through the shared playback route.
     *
     * Its own would need its own credential handling, and the guide would then be a second place a
     * signed address could be held.
     */
    @Test
    fun `the preview uses the shared playback route`() {
        val supplier =
            app.substringAfter("previewRequestFor = { channel ->").substringBefore("strings = text,")

        assertTrue(
            supplier.contains("prepareXtreamPlayback("),
            "a previa constroi o endereco a sua maneira",
        )
    }

    /**
     * The held schedules are bounded.
     *
     * Each is a few hours of programmes for one channel, and an evening of browsing a
     * four-hundred-channel list would otherwise hold the whole catalogue's schedule in memory —
     * which is exactly how the merge killed the app earlier.
     */
    @Test
    fun `the held schedules are bounded`() {
        assertTrue(
            state.contains("guideSchedules.size >= LiveGuide.MAX_CACHED_SCHEDULES"),
            "as programacoes guardadas crescem sem limite",
        )
    }
}
