package com.lucasserafin94.iptvburo.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataRedactorTest {
    private val redactor = SensitiveDataRedactor()

    @Test
    fun `redacts URL path query fragment and user info`() {
        val original =
            "Opening https://alice:secret@example.com/live/alice/secret/42.m3u8?token=top-secret#player"

        val redacted = redactor.redact(original)

        assertEquals("Opening https://example.com/<redacted>", redacted)
        assertFalse(redacted.contains("alice"))
        assertFalse(redacted.contains("secret"))
    }

    @Test
    fun `redacts authorization values regardless of scheme`() {
        val bearer = redactor.redact("Authorization: Bearer very-secret-token")
        val basic = redactor.redact("authorization=Basic dXNlcjpwYXNz")

        assertEquals("Authorization: <redacted>", bearer)
        assertEquals("authorization=<redacted>", basic)
    }

    @Test
    fun `redacts common secret key value pairs`() {
        val redacted =
            redactor.redact(
                "username=lucas password: hunter2 access_token='abc-123' api-key=\"key-value\"",
            )

        assertEquals(
            "username=<redacted> password: <redacted> access_token=<redacted> api-key=<redacted>",
            redacted,
        )
    }

    @Test
    fun `redacts secrets in JSON-like diagnostics`() {
        val redacted =
            redactor.redact(
                """{"username":"alice","password":"hunter2","client_secret":"abc"}""",
            )

        assertEquals(
            """{"username":<redacted>,"password":<redacted>,"client_secret":<redacted>}""",
            redacted,
        )
    }

    @Test
    fun `redacts signed URLs without leaking signature`() {
        val redacted =
            redactor.redact(
                "GET https://cdn.example.org/video/master.m3u8?X-Amz-Signature=deadbeef&Expires=9",
            )

        assertEquals("GET https://cdn.example.org/<redacted>", redacted)
        assertFalse(redacted.contains("deadbeef"))
    }

    @Test
    fun `redacts IP hosts and standalone addresses`() {
        val redacted =
            redactor.redact(
                "Origin http://192.168.1.20:8080/live responded via 10.0.0.4",
            )

        assertEquals(
            "Origin http://<redacted-host>:8080/<redacted> responded via <redacted-ip>",
            redacted,
        )
    }

    @Test
    fun `redacts compressed IPv6 addresses`() {
        val redacted =
            redactor.redact("Loopback ::1 responded before 2001:db8::8a2e:370:7334")

        assertEquals(
            "Loopback <redacted-ip> responded before <redacted-ip>",
            redacted,
        )
    }

    @Test
    fun `redacts JWT values that are not labelled`() {
        val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.c2lnbmF0dXJl"

        val redacted = redactor.redact("Playback failed for $token")

        assertEquals("Playback failed for <redacted-token>", redacted)
    }

    @Test
    fun `keeps non-sensitive diagnostics readable`() {
        val original = "Playlist import completed: 24 channels in 3 categories"

        assertEquals(original, redactor.redact(original))
    }

    @Test
    fun `redacts every URL in one message`() {
        val redacted =
            redactor.redact(
                "Fallback from https://first.example/a?q=1 to http://second.example/b?q=2.",
            )

        assertEquals(
            "Fallback from https://first.example/<redacted> to http://second.example/<redacted>.",
            redacted,
        )
        assertTrue(redacted.count { it == '<' } >= 2)
    }
}
