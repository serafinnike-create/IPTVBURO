package com.lucasserafin94.iptvburo

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Starting the app on a machine that already has an adult-artwork key configured.
 *
 * This shipped broken. The shelf was built in the constructor, where `streamingScope` is still null
 * because it is declared far below — so the app threw before drawing anything, and the installer
 * was unusable for anyone who had pasted a key. 983 tests passed: every one of them built the state
 * against a fresh store, where the key is blank and the failing branch never ran.
 *
 * So what these hold is the startup path with the key present, which is the one that broke.
 */
class AdultArtworkShelfStartupTest {
    private fun <T> withState(
        adultKey: String?,
        block: (DesktopAppState) -> T,
    ): T {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val downloads = Files.createTempDirectory("iptvburo-adult-shelf-test")
        return try {
            val userStore = DesktopUserStore(node)
            // Set before the state is built: a machine that already has a key is exactly the case
            // that failed, and setting it afterwards would take the rebuild path instead.
            adultKey?.let(userStore::setAdultMetadataApiKey)
            block(
                DesktopAppState(
                    localRepository = InMemoryCatalogRepository(),
                    xtreamRepository = SessionXtreamRepository(),
                    rememberedXtreamStore = RememberedXtreamStore(downloads.resolve("remembered.dpapi")),
                    userStore = userStore,
                ),
            )
        } finally {
            node.removeNode()
            @OptIn(kotlin.io.path.ExperimentalPathApi::class)
            downloads.deleteRecursively()
        }
    }

    @Test
    fun `the app starts on a machine that already has a key`() {
        withState(adultKey = "chave-sintetica") { state ->
            // Reaching this line is most of the test: the constructor threw before.
            assertNotNull(state.adultArtworkShelf, "sem prateleira, a grelha nao busca capa nenhuma")
        }
    }

    @Test
    fun `the shelf is the same one across reads, so its cache survives a redraw`() {
        withState(adultKey = "chave-sintetica") { state ->
            // A shelf rebuilt per read would forget every lookup, which is the whole point of it.
            assertSame(state.adultArtworkShelf, state.adultArtworkShelf)
        }
    }

    @Test
    fun `no key means no shelf and no requests`() {
        withState(adultKey = null) { state ->
            assertNull(state.adultArtworkShelf)
        }
    }

    @Test
    fun `pasting a key builds a shelf without a restart`() {
        withState(adultKey = null) { state ->
            state.updateAdultMetadataApiKey("chave-colada-agora")

            assertNotNull(state.adultArtworkShelf)
        }
    }

    @Test
    fun `replacing the key replaces the shelf, so old covers are not served on`() {
        withState(adultKey = "chave-antiga") { state ->
            val before = assertNotNull(state.adultArtworkShelf)
            state.updateAdultMetadataApiKey("chave-nova")

            val after = assertNotNull(state.adultArtworkShelf)
            assertNotNull(after)
            assert(before !== after) { "a prateleira da chave antiga continuou a servir capas" }
        }
    }

    @Test
    fun `clearing the key removes the shelf`() {
        withState(adultKey = "chave-antiga") { state ->
            state.updateAdultMetadataApiKey("")

            assertNull(state.adultArtworkShelf)
        }
    }
}
