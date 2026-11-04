package com.lucasserafin94.iptvburo.desktop.playback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInWatcherTest {
    @Test
    fun `the sign-in page followed by another page is a sign-in`() {
        val watcher = SignInWatcher()

        assertFalse(watcher.observe("https://godorama.com/"))
        assertFalse(watcher.observe("https://godorama.com/login"))
        assertFalse(watcher.observe("https://godorama.com/login"))
        assertTrue(watcher.observe("https://godorama.com/"))
        assertFalse(watcher.observe("https://godorama.com/serie/x"), "one sign-in was counted twice")
    }

    @Test
    fun `browsing without the sign-in page is not a sign-in`() {
        val watcher = SignInWatcher()

        assertFalse(watcher.observe("https://godorama.com/"))
        assertFalse(watcher.observe("https://godorama.com/serie/login-do-amor"))
        assertFalse(watcher.observe("https://godorama.com/assistir/x/1"))
    }
}
