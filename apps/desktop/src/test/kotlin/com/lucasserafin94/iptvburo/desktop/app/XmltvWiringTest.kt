package com.lucasserafin94.iptvburo.desktop.app

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The path from a playlist's `url-tvg` to the two lines on the channel pane.
 *
 * The parser has read that attribute since long before anything opened it, which is the shape of
 * failure this guards: every hop between the file and the screen is a place where a field can be
 * quietly dropped and the only symptom is a channel with no schedule — indistinguishable from a
 * playlist that never carried one. Each hop is pinned here.
 */
class XmltvWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val ingest =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/data/InMemoryCatalogRepository.kt")
    private val models = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/model/DesktopModels.kt")
    private val state = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt")
    private val ui = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt")

    /** The loader's signature, used to slice its body out of the state file. */
    private val LOADER = "private suspend fun loadGuideFor("

    @Test
    fun `the imported catalogue carries the guide address`() {
        // Without a field to hold it, the parsed address dies at the end of the import and every
        // list looks like a list with no guide.
        assertTrue(models.contains("val epgUrls: List<String>"), "the field exists")
        assertTrue(
            ingest.contains("epgUrls = summary.header.epgUrls"),
            "the parser's header has to be read; it is otherwise discarded with the summary",
        )
    }

    /**
     * The loader's body.
     *
     * Asserted to exist first, deliberately: `substringAfter` returns the *whole file* when its
     * marker is missing, so a renamed function would otherwise leave every check below searching
     * all of DesktopAppState and passing on some unrelated line.
     */
    private fun loaderBody(): String {
        assertTrue(state.contains(LOADER), "the loader was renamed; this test needs updating")
        return state.substringAfter(LOADER).substringBefore("\n    }")
    }

    @Test
    fun `importing a playlist loads the guide it names`() {
        assertTrue(state.contains("loadGuideFor(catalog)"), "the load is triggered")
        assertTrue(
            loaderBody().contains("catalog.epgUrls.isEmpty()"),
            "a list with no guide must not reach the network at all",
        )
    }

    @Test
    fun `the guide fetch never blocks the interface`() {
        assertTrue(
            loaderBody().contains("withContext(Dispatchers.IO)"),
            "tens of megabytes over a slow host would otherwise freeze the window",
        )
    }

    @Test
    fun `a guide that cannot be fetched leaves the list working`() {
        // The channels play without a schedule. Surfacing this as an import failure would turn a
        // missing enhancement into a broken source.
        val loader = loaderBody()
        assertTrue(loader.contains("catch (failure: Exception)"), "the failure is contained")
        assertTrue(
            loader.contains("catch (cancellation: CancellationException)") &&
                loader.contains("throw cancellation"),
            "cancellation still has to propagate, or closing the window waits on the fetch",
        )
        assertTrue(
            !loader.contains("importStatus ="),
            "a failed guide must not be reported as a failed import",
        )
    }

    @Test
    fun `removing every source forgets the guide`() {
        // It is tens of megabytes of strings; keeping it after its list is gone is a leak.
        assertTrue(state.contains("if (catalogs.isEmpty()) xmltvGuideSource.clear()"))
    }

    @Test
    fun `the pane reads the guide for the selected channel`() {
        assertTrue(
            ui.contains("nowAndNext = appState.selectedChannelNowAndNext"),
            "the call site has to pass it, or the block is drawn from the default empty pair",
        )
        val lookup = state.substringAfter("val selectedChannelNowAndNext").substringBefore("\n    val ")
        assertTrue(
            lookup.contains("channel.tvgId"),
            "the join is the channel's tvg-id; anything else matches the wrong programmes",
        )
    }

    @Test
    fun `a list without a guide draws no empty schedule`() {
        // "Agora —" with nothing after it reads as a channel that is off the air.
        assertTrue(ui.contains("if (nowAndNext.first != null || nowAndNext.second != null)"))
    }

    @Test
    fun `the labels are translated rather than written into the screen`() {
        assertTrue(ui.contains("labels.guideNow"))
        assertTrue(ui.contains("labels.guideNext"))
        val tables = read("src/main/kotlin/com/lucasserafin94/iptvburo/desktop/ui/DesktopStrings.kt")
        // PT-BR twice (two variants), plus EN, DE and IT.
        assertTrue(
            tables.split("guideNow = ").size - 1 == 5,
            "every locale needs the label, or one language falls back to a compile error",
        )
    }

    @Test
    fun `a long programme title cannot squeeze its label away`() {
        // This pane is narrow, and a Row that lets the title take its natural width collapses the
        // label to one letter per line. It has happened twice elsewhere in this app.
        val line = ui.substringAfter("private fun GuideLine(").substringBefore("\n}")
        assertTrue(line.contains("Modifier.weight(1f)"), "the title takes the remaining width")
        assertTrue(line.contains("TextOverflow.Ellipsis"), "and is cut rather than wrapped forever")
    }
}
