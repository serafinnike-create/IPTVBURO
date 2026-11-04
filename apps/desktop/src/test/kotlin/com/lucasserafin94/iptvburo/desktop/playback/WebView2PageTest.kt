package com.lucasserafin94.iptvburo.desktop.playback

import com.lucasserafin94.iptvburo.domain.model.WebServices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebView2PageTest {
    @Test
    fun `GoDorama and No Privado open inside the app, nothing else by default`() {
        assertEquals(
            setOf(WebServices.GODORAMA.id, WebServices.NO_PRIVADO.id),
            WebView2Page.IN_APP_SERVICES,
        )
        val other = WebServices.GODORAMA.copy(id = "another", homeUrl = "https://another.example/", allowedHosts = setOf("another.example"))

        assertFalse(WebView2Page.usableFor(other), "a service nobody added was given an in-app window")
    }

    @Test
    fun `each profile keeps its own data folder`() {
        val first = WebView2Page.dataFolderFor("profile-a", "godorama")
        val second = WebView2Page.dataFolderFor("profile-b", "godorama")

        assertNotEquals(first, second)
        assertEquals("profile-a-godorama", first.fileName.toString())
    }

    @Test
    fun `an unsafe profile id never escapes the folder`() {
        val folder = WebView2Page.dataFolderFor("../../escape", "godorama")

        assertTrue(folder.fileName.toString().startsWith("______escape"))
        assertEquals("webview2-profiles", folder.parent.fileName.toString())
    }
}
