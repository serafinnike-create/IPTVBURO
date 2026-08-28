package com.lucasserafin94.iptvburo.ui

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Several subscriptions browsed as one catalogue.
 *
 * A source scan because the query lives in Room and the switch in a Compose tree, neither of which
 * a plain unit test reaches. What is worth pinning is that the ends are joined — a stored choice
 * nobody acts on is exactly what shipped on Windows the first time.
 */
class MergeSourcesWiringTest {
    private fun read(relative: String): String = Path.of(relative).readText()

    private val shell =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt")
    private val viewModel = read("src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt")
    private val activity = read("src/main/kotlin/com/lucasserafin94/iptvburo/MainActivity.kt")
    private val dao =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/data/local/dao/ChannelDao.kt")
    private val repository =
        read("src/main/kotlin/com/lucasserafin94/iptvburo/data/repository/RoomCatalogRepository.kt")

    /** With one list there is nothing to merge, and the switch would ask about nothing. */
    @Test
    fun `the switch appears only with more than one list`() {
        assertTrue("sem a condicao das duas listas", shell.contains("sources.size > 1"))
        assertTrue("sem o rotulo traduzido", shell.contains("R.string.merge_sources_title"))
    }

    /** A stored choice nobody acts on is what shipped on Windows the first time. */
    @Test
    fun `the choice reaches the query`() {
        assertTrue("a activity nao liga o interruptor", activity.contains("onToggleMergeSources"))
        assertTrue("a escolha nao e guardada", viewModel.contains("sourceMergeSettings.setMergeEverySource"))
        assertTrue("a escolha nao decide o id da fonte", viewModel.contains("if (mergeEverySource)"))
    }

    /**
     * The biggest list wins a shared title. Grouped on the lowercased name rather than the
     * provider's id, since two subscriptions number the same film differently.
     */
    @Test
    fun `the merged query keeps one copy, from the largest list`() {
        assertTrue("sem consulta juntada", dao.contains("loadMergedPage"))
        assertTrue("nao escolhe a maior lista", dao.contains("MIN(source_rank)"))
        assertTrue("agrupa pelo id em vez do nome", dao.contains("GROUP BY LOWER(TRIM(c.name))"))
    }

    /** Counting rows instead of distinct titles would promise pages that are not there. */
    @Test
    fun `the merged count matches what the page can show`() {
        assertTrue(
            "a contagem nao e de titulos distintos",
            dao.contains("COUNT(DISTINCT LOWER(TRIM(name)))"),
        )
        assertTrue("o repositorio nao usa a contagem juntada", repository.contains("countMerged"))
    }
}
