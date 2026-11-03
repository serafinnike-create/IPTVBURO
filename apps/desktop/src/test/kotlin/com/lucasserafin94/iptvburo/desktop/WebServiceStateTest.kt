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
 * GoDorama through [DesktopAppState]: who sees it, and the "only GoDorama" setup.
 *
 * No network and no browser engine: what is asserted is who the state lets near the service, which
 * is decided before either is touched.
 */
class WebServiceStateTest {
    @Test
    fun `the adult profile turns GoDorama on and the Kids profile never sees it`() =
        withState { state, store ->
            state.setWebServiceEnabled(WebServices.GODORAMA.id, true)
            assertEquals(listOf(WebServices.GODORAMA), state.visibleWebServices)

            // Stored for the Kids profile by any route at all, it is still not drawn.
            store.setWebServiceEnabled(KIDS.id, WebServices.GODORAMA.id, true)
            state.selectProfile(KIDS.id)

            assertTrue(state.visibleWebServices.isEmpty(), "a Kids profile was offered an 18+ service")
            assertFalse(state.canUseWebServices)
            assertEquals(null, state.openedWebService)
        }

    @Test
    fun `a Kids profile cannot turn a service on`() =
        withState { state, store ->
            state.selectProfile(KIDS.id)

            state.setWebServiceEnabled(WebServices.GODORAMA.id, true)

            assertTrue(store.webServiceIds(KIDS.id).isEmpty())
        }

    @Test
    fun `another profile does not inherit the service`() =
        withState { state, _ ->
            state.setWebServiceEnabled(WebServices.GODORAMA.id, true)

            state.selectProfile(SECOND_ADULT.id)

            assertTrue(state.visibleWebServices.isEmpty())
        }

    @Test
    fun `an unknown service id is refused`() =
        withState { state, store ->
            state.setWebServiceEnabled("noprivado", true)

            assertTrue(store.webServiceIds(ADULT.id).isEmpty())
        }

    @Test
    fun `setup can finish with only GoDorama and no list`() =
        withState { state, store ->
            state.completeSetupWithWebServiceOnly("Ana", avatarIndex = 2, serviceId = WebServices.GODORAMA.id)

            val created = state.profiles.single { it.name == "Ana" }
            assertFalse(created.isKids)
            assertEquals(null, created.sourceId)
            assertEquals(created.id, state.activeProfileId)
            assertEquals(setOf(WebServices.GODORAMA.id), store.webServiceIds(created.id))
            assertEquals(listOf(WebServices.GODORAMA), state.visibleWebServices)
        }

    @Test
    fun `the GoDorama notice opens its page and does not come back`() =
        withState { state, store ->
            state.dismissWebServiceOffer(openPage = true)

            assertEquals(DesktopDestination.WEB_SERVICES, state.destination)
            assertTrue(store.webServicesOffered(ADULT.id))
            assertFalse(state.shouldOfferWebServices)
        }

    @Test
    fun `the Kids profile is never told about GoDorama`() =
        withState { state, _ ->
            state.selectProfile(KIDS.id)

            assertFalse(state.shouldOfferWebServices)
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
