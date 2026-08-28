package com.lucasserafin94.iptvburo.desktop.app

import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The switch that shows every subscription as one catalogue.
 *
 * Reported as missing: a second list was connected, no switch was visible, and the two were
 * expected to add up on their own. The cause was elsewhere — the second connection closed the
 * first, so there was never more than one list and the switch stayed hidden behind its guard — but
 * the report also showed what somebody looks for and where.
 *
 * A source scan, because reaching the switch needs a window with two live subscriptions in it.
 * What is worth pinning is where it sits and that it says what it does.
 */
class MergeSwitchTest {
    private val app =
        Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt").readText()

    /**
     * Beside the lists, not buried in the profile form.
     *
     * It was offered only while creating a profile, which is not where somebody adds a second
     * subscription — they use the sidebar, which is exactly where the report says they looked.
     */
    @Test
    fun `the switch sits with the sources in the sidebar`() {
        val sourcesBlock =
            app.substringAfter("if (sources.size > 1) {").substringBefore("NavigationItem(")

        assertTrue(
            sourcesBlock.contains("onToggleMergeSources"),
            "o interruptor nao esta junto das listas na barra lateral",
        )
    }

    /**
     * And only with more than one list.
     *
     * With a single subscription there is nothing to merge, and the switch would be a question
     * about nothing.
     */
    @Test
    fun `the switch is offered only when there is something to merge`() {
        assertTrue(
            app.contains("if (sources.size > 1) {"),
            "o interruptor apareceria com uma lista so",
        )
    }

    /**
     * It has to say what happens when it is flipped.
     *
     * The switch now rebuilds the catalogue on the spot, behind the loading screen. It used to
     * store a preference and change nothing until the next launch, which reads as a dead button —
     * reported twice.
     */
    @Test
    fun `the switch says what it does`() {
        assertTrue(
            app.contains("mergeSourcesRestart"),
            "o interruptor nao diz o que acontece ao ser mudado",
        )
        assertTrue(
            app.contains("appState.applyMergeAllSources(enabled)"),
            "o interruptor guarda a escolha mas nao reorganiza nada",
        )
    }

    /** In every language the app ships, not only the one it was written in. */
    @Test
    fun `every language explains the switch`() {
        DesktopLanguage.entries.forEach { language ->
            val screens = DesktopStrings.of(language).shareStrings.screens
            assertTrue(
                screens.mergeSourcesRestart.isNotBlank(),
                "mergeSourcesRestart vazio em $language",
            )
            assertTrue(
                screens.mergeSourcesTitle.isNotBlank(),
                "mergeSourcesTitle vazio em $language",
            )
            assertTrue(
                screens.mergeSourcesOffline.isNotBlank(),
                "mergeSourcesOffline vazio em $language",
            )
        }
    }

    /**
     * A merged list that is down says so on its own row.
     *
     * Only a merge can show one: browsing a list at a time, a list that fails never opens at all.
     * Without the mark the row looks like every other, and a catalogue quietly missing one
     * subscription's titles cannot be told from one that never had them.
     */
    @Test
    fun `a list that is down is marked in the sidebar`() {
        assertTrue(
            app.contains("mergeSourcesOffline"),
            "a barra lateral nao marca a lista que nao respondeu",
        )
    }

    /**
     * A list can be renamed and forgotten from the sidebar.
     *
     * Both were reachable only from the profile form. Somebody looking at a list that stopped
     * answering is in the sidebar, not there — reported as rows that could be neither edited nor
     * deleted.
     */
    @Test
    fun `a list can be renamed and forgotten from the sidebar`() {
        assertTrue(
            app.contains("onRenameSource = appState::renameSavedSource"),
            "a barra lateral nao permite mudar o nome de uma lista",
        )
        assertTrue(
            app.contains("onRemoveSource = appState::removeSavedSource"),
            "a barra lateral nao permite esquecer uma lista",
        )
    }

    /**
     * Forgetting asks first.
     *
     * It throws away the stored password too, and the control sits inches from the one that opens
     * the list.
     */
    @Test
    fun `forgetting a list asks before it happens`() {
        assertTrue(
            app.contains("setupRemoveListConfirm"),
            "esquecer uma lista nao pede confirmacao",
        )
    }

    /**
     * Switching tab does not leave the previous type's grid on screen.
     *
     * Clicking Filmes showed a grid of live channels, every card labelled "Ao vivo", for as long as
     * the films took to arrive — reported as "clikei em filmes e abriu aovico". An empty grid under
     * the loading banner is the honest state.
     */
    @Test
    fun `changing content type clears the page it is leaving`() {
        val state =
            Path.of("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt").readText()
        val block =
            state.substringAfter("if (!alreadyLoaded) {").substringBefore("runCatching {")

        assertTrue(
            block.contains("xtreamPage = XtreamCatalogPage.empty()"),
            "a grelha do tipo anterior fica no ecra enquanto o novo carrega",
        )
    }

    /**
     * And a merged row shows no item count.
     *
     * There is no per-list count once the lists are merged — the catalogue is one thing — so a
     * literal "0 itens" under every row would read as a load that failed.
     */
    @Test
    fun `a merged row shows no count rather than zero`() {
        assertTrue(
            app.contains("source.itemCount > 0"),
            "a barra lateral mostraria 0 itens nas listas juntas",
        )
    }
}
