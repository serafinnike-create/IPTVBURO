package com.lucasserafin94.iptvburo

import com.lucasserafin94.iptvburo.desktop.DailyHomeStatus
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The Home must never be left showing its skeleton with nothing able to clear it.
 *
 * loadDailyHome set the status to Loading and only then checked for a session, returning early when
 * there was none. Nothing else moves that status, so the Home span for ever - which is exactly what
 * a user saw after a refresh.
 */
class DailyHomeStatusTest {
    private fun <T> withState(block: (DesktopAppState) -> T): T {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val downloads = Files.createTempDirectory("iptvburo-home-test")
        return try {
            block(
                DesktopAppState(
                    localRepository = InMemoryCatalogRepository(),
                    xtreamRepository = SessionXtreamRepository(),
                    rememberedXtreamStore = RememberedXtreamStore(downloads.resolve("remembered.dpapi")),
                    userStore = DesktopUserStore(node),
                ),
            )
        } finally {
            node.removeNode()
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            downloads.deleteRecursively()
        }
    }

    @Test
    fun `loading the home without a session does not leave it spinning`() {
        withState { state ->
            runBlocking { state.loadDailyHome() }

            assertTrue(
                state.dailyHomeStatus !is DailyHomeStatus.Loading,
                "was ${state.dailyHomeStatus}",
            )
        }
    }

    @Test
    fun `it settles on idle so the screen can ask again`() {
        withState { state ->
            runBlocking {
                state.loadDailyHome()
                // A second attempt must still be possible; a stuck Loading would refuse it.
                state.loadDailyHome()
            }

            assertTrue(state.dailyHomeStatus is DailyHomeStatus.Idle, "was ${state.dailyHomeStatus}")
        }
    }
}
