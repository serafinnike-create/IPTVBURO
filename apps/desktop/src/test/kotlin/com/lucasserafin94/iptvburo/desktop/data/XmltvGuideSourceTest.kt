package com.lucasserafin94.iptvburo.desktop.data

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer

/**
 * Turning a playlist's own guide file into the schedule the existing screen reads.
 *
 * The join between the two files is the channel id, and it is the fragile part: the playlist and
 * the guide are usually produced by different tools, so an id that differs only in case or spacing
 * is common. Getting that wrong shows up as a channel with no schedule at all, which looks like a
 * provider problem rather than a matching one — so most of this is about the join.
 */
class XmltvGuideSourceTest {
    private fun guide(body: String) = """<?xml version="1.0" encoding="UTF-8"?><tv>$body</tv>"""

    private val twoChannels =
        guide(
            """<programme channel="Globo.br" start="20260824200000 +0000" stop="20260824210000 +0000">
                 <title>Jornal</title><desc>Noticias.</desc></programme>
               <programme channel="Globo.br" start="20260824210000 +0000" stop="20260824220000 +0000">
                 <title>Novela</title></programme>
               <programme channel="SBT.br" start="20260824200000 +0000" stop="20260824213000 +0000">
                 <title>Outro canal</title></programme>""",
        )

    private fun withServer(
        body: String,
        gzip: Boolean = false,
        code: Int = 200,
        block: (XmltvGuideSource, String) -> Unit,
    ) {
        val server = MockWebServer()
        val response = MockResponse().setResponseCode(code)
        if (gzip) {
            val compressed = Buffer()
            GzipSink(compressed).buffer().use { it.writeUtf8(body) }
            response.setBody(compressed)
        } else {
            response.setBody(body)
        }
        server.enqueue(response)
        server.start()
        try {
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
            block(XmltvGuideSource(client), server.url("/epg.xml").toString())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a channel gets the programmes that name it, and no others`() {
        withServer(twoChannels) { source, url ->
            assertTrue(source.load(listOf(url)))
            assertEquals(listOf("Jornal", "Novela"), source.shortEpg("Globo.br").programs.map { it.title })
            assertEquals(listOf("Outro canal"), source.shortEpg("SBT.br").programs.map { it.title })
        }
    }

    @Test
    fun `the id is matched regardless of case and spacing`() {
        // The playlist and the guide come from different tools often enough that insisting on an
        // exact match leaves channels blank for a reason the viewer cannot see or fix.
        withServer(twoChannels) { source, url ->
            source.load(listOf(url))
            listOf("globo.br", "GLOBO.BR", "  Globo.br  ").forEach { variant ->
                assertEquals(
                    2,
                    source.shortEpg(variant).programs.size,
                    "an id differing only in case or spacing must still match: $variant",
                )
            }
        }
    }

    @Test
    fun `a channel the guide does not mention gets an empty schedule, not someone else's`() {
        withServer(twoChannels) { source, url ->
            source.load(listOf(url))
            assertTrue(source.shortEpg("nao.existe").programs.isEmpty())
            assertTrue(source.shortEpg(null).programs.isEmpty())
            assertTrue(source.shortEpg("   ").programs.isEmpty())
        }
    }

    @Test
    fun `programmes come back in broadcast order`() {
        // Out of order they would draw as a scrambled evening, and now-and-next would pick wrongly.
        val shuffled =
            guide(
                """<programme channel="c1" start="20260824220000 +0000" stop="20260824230000 +0000">
                     <title>Terceiro</title></programme>
                   <programme channel="c1" start="20260824200000 +0000" stop="20260824210000 +0000">
                     <title>Primeiro</title></programme>
                   <programme channel="c1" start="20260824210000 +0000" stop="20260824220000 +0000">
                     <title>Segundo</title></programme>""",
            )
        withServer(shuffled) { source, url ->
            source.load(listOf(url))
            assertEquals(
                listOf("Primeiro", "Segundo", "Terceiro"),
                source.shortEpg("c1").programs.map { it.title },
            )
        }
    }

    @Test
    fun `now and next read correctly off the loaded guide`() {
        // The whole reason for returning the existing shape: the screen's own logic must work on it.
        withServer(twoChannels) { source, url ->
            source.load(listOf(url))
            // 20:30 UTC, inside the first programme.
            val at = XmltvNow.epochOf("20260824203000")
            val (now, next) = source.shortEpg("Globo.br").nowAndNext(at)
            assertEquals("Jornal", now?.title)
            assertEquals("Novela", next?.title)
        }
    }

    @Test
    fun `a gzipped guide is read`() {
        withServer(twoChannels, gzip = true) { source, url ->
            assertTrue(source.load(listOf(url)))
            assertEquals(2, source.shortEpg("Globo.br").programs.size)
        }
    }

    @Test
    fun `an unreachable guide is a missing schedule, not a failure`() {
        // The channels still play. Throwing here would turn an enhancement into a broken source.
        val source = XmltvGuideSource()
        assertFalse(source.load(listOf("http://127.0.0.1:1/epg.xml")))
        assertFalse(source.isLoaded)
        assertTrue(source.shortEpg("qualquer").programs.isEmpty())
    }

    @Test
    fun `a server error is not mistaken for an empty guide`() {
        withServer(twoChannels, code = 500) { source, url ->
            assertFalse(source.load(listOf(url)))
            assertFalse(source.isLoaded)
        }
    }

    @Test
    fun `a playlist with no guide address loads nothing`() {
        val source = XmltvGuideSource()
        assertFalse(source.load(emptyList()))
    }

    @Test
    fun `the first address that works wins`() {
        withServer(twoChannels) { source, working ->
            assertTrue(source.load(listOf("http://127.0.0.1:1/dead.xml", working)))
            assertEquals(3, source.loadedProgrammeCount)
        }
    }

    @Test
    fun `clearing drops the schedule`() {
        withServer(twoChannels) { source, url ->
            source.load(listOf(url))
            source.clear()
            assertFalse(source.isLoaded)
            assertEquals(0, source.loadedProgrammeCount)
            assertTrue(source.shortEpg("Globo.br").programs.isEmpty())
        }
    }

    @Test
    fun `the address never appears in the printed form`() {
        // A guide URL can carry a token, and this string reaches logs and diagnostics.
        withServer(twoChannels) { source, url ->
            source.load(listOf("$url?token=guide-secret"))
            val printed = source.toString()
            assertFalse(printed.contains("guide-secret"), "a token must not reach a log")
            assertFalse(printed.contains("127.0.0.1"), "nor the host")
        }
    }
}

/** Epoch seconds for an XMLTV timestamp read as UTC, so a test can name a moment readably. */
private object XmltvNow {
    fun epochOf(stamp: String): Long =
        java.time.LocalDateTime
            .parse(stamp, java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            .toEpochSecond(java.time.ZoneOffset.UTC)
}
