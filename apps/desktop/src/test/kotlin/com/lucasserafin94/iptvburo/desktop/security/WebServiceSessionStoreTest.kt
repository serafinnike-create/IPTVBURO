package com.lucasserafin94.iptvburo.desktop.security

import com.lucasserafin94.iptvburo.domain.model.WebServices
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebServiceSessionStoreTest {
    private val godorama = WebServices.GODORAMA

    /** Reverses the bytes: enough to prove the file is not the plain text, without DPAPI. */
    private class FakeSealer(override val isAvailable: Boolean = true) : WebServiceSessionStore.SecretSealer {
        override fun seal(plaintext: ByteArray): ByteArray = plaintext.reversedArray()

        override fun unseal(sealed: ByteArray): ByteArray = sealed.reversedArray()
    }

    private fun cookie(
        name: String = "sid",
        value: String = "secret-session-value",
        domain: String = "godorama.com",
    ) = StoredWebCookie(name, value, domain, "/", secure = true, httpOnly = true, expiresEpochMillis = 1_900_000_000_000)

    @Test
    fun `a profile's sign-in survives a restart`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer())
        val saved = listOf(cookie(), cookie(name = "pref", value = "a b\tc\nd", domain = ".godorama.com").copy(expiresEpochMillis = null))

        store.save("profile-1", godorama, saved)

        assertEquals(saved, WebServiceSessionStore(directory, FakeSealer()).load("profile-1", godorama))
    }

    @Test
    fun `profiles do not share a sign-in`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer())

        store.save("adult-a", godorama, listOf(cookie(value = "a")))

        assertTrue(store.load("adult-b", godorama).isEmpty())
    }

    @Test
    fun `only the service's own cookies are kept`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer())

        store.save(
            "profile-1",
            godorama,
            listOf(cookie(), cookie(name = "track", domain = "ads.example"), cookie(name = "fake", domain = "godorama.com.evil.example")),
        )

        assertEquals(listOf("sid"), store.load("profile-1", godorama).map(StoredWebCookie::name))
    }

    @Test
    fun `the file on disk is sealed, not the session in the clear`() {
        val directory = Files.createTempDirectory("web-sessions")
        WebServiceSessionStore(directory, FakeSealer()).save("profile-1", godorama, listOf(cookie()))

        val written = directory.listDirectoryEntries().single().readBytes().toString(Charsets.UTF_8)
        assertFalse(written.startsWith("v1"), "the session was written without the protector")
    }

    @Test
    fun `nothing is written where the protector is unavailable`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer(isAvailable = false))

        store.save("profile-1", godorama, listOf(cookie()))

        assertTrue(directory.listDirectoryEntries().isEmpty())
        assertTrue(store.load("profile-1", godorama).isEmpty())
    }

    @Test
    fun `signing out forgets the sign-in`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer())
        store.save("profile-1", godorama, listOf(cookie()))

        store.clear("profile-1", godorama)

        assertTrue(store.load("profile-1", godorama).isEmpty())
    }

    @Test
    fun `an unsafe profile id never becomes a path`() {
        val directory = Files.createTempDirectory("web-sessions")
        val store = WebServiceSessionStore(directory, FakeSealer())

        store.save("../escape", godorama, listOf(cookie()))

        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `a cookie's value never reaches its string form`() {
        assertFalse(cookie().toString().contains("secret-session-value"))
    }

    @Test
    fun `a damaged file reads as no sign-in`() {
        assertTrue(WebServiceSessionStore.decode("garbage".toByteArray()).isEmpty())
        assertTrue(WebServiceSessionStore.decode("v1\nnot base64 at all".toByteArray()).isEmpty())
    }
}
