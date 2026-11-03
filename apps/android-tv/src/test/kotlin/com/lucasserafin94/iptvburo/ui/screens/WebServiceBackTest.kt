package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Back on the GoDorama screen. A television remote has no other way out, so Back must always be
 * able to leave — reported as the app being stuck on GoDorama after signing in.
 */
class WebServiceBackTest {
    @Test
    fun `Back leaves when there is no earlier page`() {
        assertTrue(leavesWebServiceOnBack(previousUrl = null, currentUrl = "https://godorama.com/"))
    }

    @Test
    fun `Back after signing in leaves instead of reopening the sign-in form`() {
        assertTrue(leavesWebServiceOnBack("https://godorama.com/login", "https://godorama.com/"))
        assertTrue(leavesWebServiceOnBack("https://godorama.com/login?next=%2F", "https://godorama.com/"))
        assertTrue(leavesWebServiceOnBack("https://godorama.com/entrar/", "https://godorama.com/serie/x"))
    }

    @Test
    fun `Back leaves when the earlier page is this same page`() {
        assertTrue(leavesWebServiceOnBack("https://godorama.com/?tab=1", "https://godorama.com/"))
    }

    @Test
    fun `Back walks the service's own pages otherwise`() {
        assertFalse(leavesWebServiceOnBack("https://godorama.com/", "https://godorama.com/serie/x"))
        assertFalse(leavesWebServiceOnBack("https://godorama.com/serie/x", "https://godorama.com/assistir/x/1"))
        // A series whose name merely contains the word is not the sign-in page.
        assertFalse(leavesWebServiceOnBack("https://godorama.com/serie/login-do-amor", "https://godorama.com/"))
    }
}
