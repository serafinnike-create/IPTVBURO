package com.lucasserafin94.iptvburo.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsDowngradeGuardTest {
    @Test
    fun `an HTTPS request may not be redirected to cleartext`() {
        assertTrue(HttpsDowngradeGuard.isDowngrade("https://a.test/live".toHttpUrl(), "http://b.test/live".toHttpUrl()))
    }

    @Test
    fun `an upgrade to HTTPS and same-scheme hops are allowed`() {
        assertFalse(HttpsDowngradeGuard.isDowngrade("http://a.test/live".toHttpUrl(), "https://b.test/live".toHttpUrl()))
        assertFalse(HttpsDowngradeGuard.isDowngrade("http://a.test/live".toHttpUrl(), "http://b.test/live".toHttpUrl()))
        assertFalse(HttpsDowngradeGuard.isDowngrade("https://a.test/live".toHttpUrl(), "https://b.test/live".toHttpUrl()))
    }
}
