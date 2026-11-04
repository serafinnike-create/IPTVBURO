package com.lucasserafin94.iptvburo.desktop

import com.lucasserafin94.iptvburo.desktop.data.InMemoryCatalogRepository
import com.lucasserafin94.iptvburo.desktop.data.SessionXtreamRepository
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressCoordinator
import com.lucasserafin94.iptvburo.desktop.playback.DesktopPlaybackProgressStore
import com.lucasserafin94.iptvburo.desktop.security.RememberedXtreamStore
import com.lucasserafin94.iptvburo.desktop.user.DesktopProfile
import com.lucasserafin94.iptvburo.desktop.user.DesktopUserStore
import com.lucasserafin94.iptvburo.domain.model.WebServices
import com.lucasserafin94.iptvburo.xtream.XtreamClient
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.OkHttpClient

/**
 * Home shows a service's tile only to a profile signed in to it, and only for a service whose
 * sign-in the app itself saw.
 */
class WebServiceTilesTest {
    private fun report(profileId: String, serviceId: String, signedIn: Boolean) =
        com.lucasserafin94.iptvburo.desktop.playback.WebServiceSignIns.listener!!.invoke(profileId, serviceId, signedIn)

    @Test
    fun `GoDorama's tile appears after signing in and goes after signing out`() =
        withState { state, _ ->
            assertTrue(state.signedInWebServices.isEmpty(), "a tile was shown before any sign-in")

            report(ADULT.id, WebServices.GODORAMA.id, true)
            assertEquals(listOf(WebServices.GODORAMA), state.signedInWebServices)

            report(ADULT.id, WebServices.GODORAMA.id, false)
            assertTrue(state.signedInWebServices.isEmpty())
        }

    @Test
    fun `a stored switch alone does not put a tile on Home`() =
        withState { state, store ->
            WebServices.all.filter { it.id != WebServices.GODORAMA.id }.forEach { other ->
                store.setWebServiceEnabled(ADULT.id, other.id, true)
            }
            state.selectProfile(ADULT.id)

            assertTrue(state.signedInWebServices.none { it.id != WebServices.GODORAMA.id })
        }

    @Test
    fun `a Kids profile never gets a tile`() =
        withState { state, _ ->
            report(KIDS.id, WebServices.GODORAMA.id, true)
            state.selectProfile(KIDS.id)

            assertTrue(state.signedInWebServices.isEmpty())
        }

    private fun withState(block: (DesktopAppState, DesktopUserStore) -> Unit) {
        val node = Preferences.userRoot().node("com/lucasserafin94/iptvburo/test-${UUID.randomUUID()}")
        val temporary = Files.createTempDirectory("iptvburo-web-services")
        val store = DesktopUserStore(node)
        store.saveProfiles(listOf(ADULT, SECOND_ADULT, KIDS))
        store.setActiveProfile(ADULT.id)
        val repository = SessionXtreamRepository(XtreamClient(httpClient = OkHttpClient()))
        val state =
            DesktopAppState(
                localRepository = InMemoryCatalogRepository(),
                xtreamRepository = repository,
                rememberedXtreamStore = RememberedXtreamStore(temporary.resolve("remembered.dpapi")),
                userStore = store,
                playbackProgressCoordinator =
                    DesktopPlaybackProgressCoordinator(DesktopPlaybackProgressStore(node.node("progress"))),
            )
        try {
            block(state, store)
        } finally {
            state.dispose()
            repository.clear()
            node.removeNode()
            @OptIn(ExperimentalPathApi::class)
            temporary.deleteRecursively()
        }
    }

    private companion object {
        val ADULT = DesktopProfile("adult-profile", "Lucas", isKids = false)
        val SECOND_ADULT = DesktopProfile("second-adult", "Outro", isKids = false)
        val KIDS = DesktopProfile("kids-profile", "Kids", isKids = true)
    }
}
