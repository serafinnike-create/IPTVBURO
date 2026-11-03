package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebServicesTest {
    private val godorama = WebServices.GODORAMA

    @Test
    fun `an age-restricted service is offered to the adult profile only`() {
        assertTrue(WebServicePolicy.isOfferedTo(godorama, ViewerKind.ADULT))
        assertFalse(WebServicePolicy.isOfferedTo(godorama, ViewerKind.KIDS))
        assertFalse(WebServicePolicy.isOfferedTo(godorama, ViewerKind.GUEST))
    }

    @Test
    fun `a service with no age requirement is offered to everyone`() {
        val open = godorama.copy(id = "open", minimumAge = 0)

        ViewerKind.entries.forEach { viewer -> assertTrue(WebServicePolicy.isOfferedTo(open, viewer)) }
    }

    @Test
    fun `a service turned on is hidden from Kids without forgetting the choice`() {
        val enabled = setOf(godorama.id)

        assertEquals(listOf(godorama), WebServicePolicy.visibleServices(enabled, ViewerKind.ADULT))
        assertEquals(emptyList(), WebServicePolicy.visibleServices(enabled, ViewerKind.KIDS))
        assertEquals(emptyList(), WebServicePolicy.visibleServices(emptySet(), ViewerKind.ADULT))
    }

    @Test
    fun `an unknown stored id is ignored`() {
        assertEquals(emptyList(), WebServicePolicy.visibleServices(setOf("noprivado"), ViewerKind.ADULT))
        assertEquals(null, WebServices.byId("noprivado"))
        assertEquals(godorama, WebServices.byId("godorama"))
    }

    @Test
    fun `GoDorama claims only what its terms allow`() {
        assertTrue(godorama.supports(WebServiceCapability.EMBEDDED_PAGE))
        assertTrue(godorama.supports(WebServiceCapability.ACCOUNT_HISTORY))
        assertFalse(godorama.supports(WebServiceCapability.NATIVE_RAILS))
        assertFalse(godorama.supports(WebServiceCapability.NATIVE_PLAYBACK))
        assertFalse(godorama.supports(WebServiceCapability.OFFLINE_DOWNLOAD))
    }

    @Test
    fun `the service's own pages stay inside the view`() {
        assertEquals(WebNavigation.StayInside, WebServicePolicy.navigation(godorama, "https://godorama.com/serie/x"))
        assertEquals(WebNavigation.StayInside, WebServicePolicy.navigation(godorama, "https://GoDorama.com:443/login"))
        assertEquals(WebNavigation.StayInside, WebServicePolicy.navigation(godorama, "https://www.godorama.com/"))
    }

    @Test
    fun `a look-alike host is not the service`() {
        assertEquals(
            WebNavigation.OpenOutside("https://godorama.com.evil.example/"),
            WebServicePolicy.navigation(godorama, "https://godorama.com.evil.example/"),
        )
        assertEquals(
            WebNavigation.OpenOutside("https://notgodorama.com/"),
            WebServicePolicy.navigation(godorama, "https://notgodorama.com/"),
        )
    }

    @Test
    fun `other pages open outside and everything else is blocked`() {
        assertEquals(
            WebNavigation.OpenOutside("https://wa.me/5500000000000"),
            WebServicePolicy.navigation(godorama, "https://wa.me/5500000000000"),
        )
        listOf(
            "http://godorama.com/",
            "javascript:alert(1)",
            "intent://scan#Intent;end",
            "file:///sdcard/x",
            "https://user:secret@godorama.com/",
            "",
        ).forEach { url -> assertEquals(WebNavigation.Block, WebServicePolicy.navigation(godorama, url), url) }
    }

    @Test
    fun `only the scheme and host reach a log`() {
        assertEquals(
            "https://api.godorama.com",
            WebServicePolicy.loggable("https://api.godorama.com/media/x.webp?exp=1&sig=abc"),
        )
        assertEquals("<unparsable>", WebServicePolicy.loggable("not a url"))
        assertEquals("WebServiceBrand(godorama)", godorama.toString())
    }

    @Test
    fun `GoDorama turns on without the 18+ question but stays off Kids`() {
        assertFalse(godorama.asksAgeConfirmation)
        assertFalse(WebServicePolicy.isOfferedTo(godorama, ViewerKind.KIDS))
        assertTrue(godorama.copy(asksAgeConfirmation = true).asksAgeConfirmation)
    }

    @Test
    fun `a brand must open over https`() {
        assertFailsWith<IllegalArgumentException> { godorama.copy(homeUrl = "http://godorama.com/") }
    }
}
