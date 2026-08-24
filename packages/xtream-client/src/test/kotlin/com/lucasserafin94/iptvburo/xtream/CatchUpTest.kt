package com.lucasserafin94.iptvburo.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catch-up: which channels have a recorder, and how a past programme is addressed.
 *
 * Xtream reports the two halves separately — `tv_archive` says the recorder is on, and
 * `tv_archive_duration` says how far back it reaches — and a channel can advertise the first with a
 * zero second. Offering catch-up on such a channel produces a button that always fails, so the two
 * are folded into one value here and the tests below are mostly about that seam.
 */
class CatchUpTest {
    private val credentials =
        XtreamCredentials(
            serverUrl = "http://example.test:8080",
            username = "user",
            password = "pass",
        )

    private fun parseLive(json: String): XtreamCatalogItem? {
        var parsed: XtreamCatalogItem? = null
        XtreamCatalogStreamParser(
            contentType = XtreamContentType.LIVE,
            maximumItems = 10,
            sanitizeArtwork = { null },
        ).parse(json.byteInputStream()) { item -> parsed = parsed ?: item }
        return parsed
    }

    @Test
    fun `a channel with a recorder and a window offers catch-up`() {
        val item =
            parseLive(
                """[{"stream_id":"7","name":"Canal","tv_archive":1,"tv_archive_duration":3}]""",
            )
        assertEquals(3, item?.catchUpDays)
    }

    @Test
    fun `the flag alone is not enough`() {
        // Seen in the wild: the recorder is advertised and the window is zero, so there is nothing
        // to play. A button offered here would fail every time it was pressed.
        val item =
            parseLive(
                """[{"stream_id":"7","name":"Canal","tv_archive":1,"tv_archive_duration":0}]""",
            )
        assertNull(item?.catchUpDays)
    }

    @Test
    fun `a window without the flag is not enough either`() {
        val item =
            parseLive(
                """[{"stream_id":"7","name":"Canal","tv_archive":0,"tv_archive_duration":7}]""",
            )
        assertNull(item?.catchUpDays)
    }

    @Test
    fun `a channel that says nothing about archive offers none`() {
        val item = parseLive("""[{"stream_id":"7","name":"Canal"}]""")
        assertNull(item?.catchUpDays)
    }

    @Test
    fun `the values may arrive as strings`() {
        // Panels differ: some emit numbers, some emit the same numbers quoted.
        val item =
            parseLive(
                """[{"stream_id":"7","name":"Canal","tv_archive":"1","tv_archive_duration":"5"}]""",
            )
        assertEquals(5, item?.catchUpDays)
    }

    @Test
    fun `the timeshift url has the shape the panel expects`() {
        val url =
            XtreamClient().buildTimeshiftUrl(
                credentials = credentials,
                providerId = "7",
                startLocal = "2026-08-24:20-30",
                durationMinutes = 45,
            )
        assertEquals(
            "http://example.test:8080/timeshift/user/pass/45/2026-08-24:20-30/7.ts",
            url.toString(),
        )
    }

    @Test
    fun `a malformed start is refused rather than sent`() {
        // The path shape is fixed, so anything else is a caller's bug or an attempt to add a path
        // segment. There is no legitimate variation to escape around.
        listOf(
            "2026-08-24 20:30",
            "2026-08-24:20:30",
            "../../etc/passwd",
            "2026-08-24:20-30/extra",
            "",
        ).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                XtreamClient().buildTimeshiftUrl(credentials, "7", bad, 30)
            }
        }
    }

    @Test
    fun `an absurd duration is refused`() {
        // Asking for a day would have the provider open a file measured in gigabytes.
        assertThrows(IllegalArgumentException::class.java) {
            XtreamClient().buildTimeshiftUrl(credentials, "7", "2026-08-24:20-30", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            XtreamClient().buildTimeshiftUrl(credentials, "7", "2026-08-24:20-30", 5_000)
        }
    }

    @Test
    fun `the url carries no credentials in its printed form beyond the path it must`() {
        // The username and password are path segments here — that is Xtream's design and cannot be
        // avoided — but the test exists so that a future change putting them in a query string, or
        // logging the URL, has to face this comment.
        val url =
            XtreamClient().buildTimeshiftUrl(credentials, "7", "2026-08-24:20-30", 30)
        assertTrue("credentials must not move into a query string", url.querySize == 0)
    }
}
