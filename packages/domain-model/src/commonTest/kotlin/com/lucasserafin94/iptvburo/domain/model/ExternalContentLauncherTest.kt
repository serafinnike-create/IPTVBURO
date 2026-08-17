package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExternalContentLauncherTest {
    private fun target(
        appUri: String? = null,
        webUrl: String? = null,
    ) = ExternalLaunchTarget(webUrl = webUrl, appDeepLink = appUri)

    @Test
    fun `the provider's own app is preferred over its website`() {
        val decision =
            ExternalContentLauncher.decide(
                target(appUri = "demoprovider://title/1234", webUrl = "https://demo.example.invalid/title/1234"),
            )

        assertEquals(LaunchDecision.OpenApp("demoprovider://title/1234"), decision)
    }

    @Test
    fun `the website is used when there is no app link`() {
        val decision = ExternalContentLauncher.decide(target(webUrl = "https://demo.example.invalid/title/1234"))

        assertEquals(LaunchDecision.OpenWeb("https://demo.example.invalid/title/1234"), decision)
    }

    @Test
    fun `a bad app link falls back to a good website`() {
        val decision =
            ExternalContentLauncher.decide(
                target(appUri = "javascript:alert(1)", webUrl = "https://demo.example.invalid/title/1234"),
            )

        assertEquals(LaunchDecision.OpenWeb("https://demo.example.invalid/title/1234"), decision)
    }

    @Test
    fun `a stream address is refused, not opened`() {
        // This is the case the whole class exists for: something upstream handed BURO a playable
        // URL. Opening it would be playing a protected stream in our own player.
        listOf(
            "https://demo.example.invalid/video/master.m3u8",
            "https://demo.example.invalid/video/manifest.mpd",
            "https://demo.example.invalid/dash/manifest?id=1",
            "https://demo.example.invalid/title.mp4",
        ).forEach { address ->
            val decision = ExternalContentLauncher.decide(target(webUrl = address))
            val refused = assertIs<LaunchDecision.Unavailable>(decision, "should have refused $address")
            assertEquals(LaunchRefusal.LOOKS_LIKE_A_STREAM, refused.reason, address)
        }
    }

    @Test
    fun `an address carrying a token is refused`() {
        listOf(
            "https://demo.example.invalid/title/1?token=abc",
            "https://demo.example.invalid/title/1?access_token=abc",
            "https://demo.example.invalid/title/1?session=abc",
            "https://user:secret@demo.example.invalid/title/1",
        ).forEach { address ->
            val decision = ExternalContentLauncher.decide(target(webUrl = address))
            val refused = assertIs<LaunchDecision.Unavailable>(decision, "should have refused $address")
            assertEquals(LaunchRefusal.CARRIES_CREDENTIALS, refused.reason, address)
        }
    }

    @Test
    fun `local and executable schemes are refused`() {
        listOf(
            "file:///C:/Users/someone/video.mkv",
            "javascript:alert(1)",
            "data:text/html,<script>",
            "rtmp://demo.example.invalid/live",
        ).forEach { address ->
            val decision = ExternalContentLauncher.decide(target(appUri = address))
            val refused = assertIs<LaunchDecision.Unavailable>(decision, "should have refused $address")
            assertEquals(LaunchRefusal.UNSUPPORTED_SCHEME, refused.reason, address)
        }
    }

    @Test
    fun `plain http is not accepted for a website`() {
        val decision = ExternalContentLauncher.decide(target(webUrl = "http://demo.example.invalid/title/1"))

        val refused = assertIs<LaunchDecision.Unavailable>(decision)
        assertEquals(LaunchRefusal.UNSUPPORTED_SCHEME, refused.reason)
    }

    @Test
    fun `a target that goes nowhere cannot be built at all`() {
        // The type refuses it, so the launcher never has to handle the empty case.
        assertFailsWith<IllegalArgumentException> { target() }
        assertFailsWith<IllegalArgumentException> { target(appUri = "  ", webUrl = "") }
    }

    @Test
    fun `when both addresses are refused the result is unavailable`() {
        val decision =
            ExternalContentLauncher.decide(
                target(appUri = "javascript:alert(1)", webUrl = "https://demo.example.invalid/master.m3u8"),
            )

        val refused = assertIs<LaunchDecision.Unavailable>(decision)
        assertEquals(LaunchRefusal.LOOKS_LIKE_A_STREAM, refused.reason)
    }

    @Test
    fun `a bad app link with no website reports the app link's reason`() {
        val decision = ExternalContentLauncher.decide(target(appUri = "file:///C:/video.mkv"))

        val refused = assertIs<LaunchDecision.Unavailable>(decision)
        assertEquals(LaunchRefusal.UNSUPPORTED_SCHEME, refused.reason)
    }
}
