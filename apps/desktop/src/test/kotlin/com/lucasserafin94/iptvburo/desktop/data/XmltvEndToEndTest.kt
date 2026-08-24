package com.lucasserafin94.iptvburo.desktop.data

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * A real playlist file through to a real schedule.
 *
 * The unit tests each cover one hop. This walks the whole path the way the app does — parse an M3U
 * off disk, read the guide address out of its header, fetch that guide, and ask for the schedule of
 * a channel by the id the playlist gave it — because every hop in between is a place where the
 * address or the id can be dropped while each individual piece still passes its own tests.
 */
class XmltvEndToEndTest {
    @Test
    fun `a playlist naming a guide ends up with a schedule on its channels`() {
        val server = MockWebServer()
        // Deliberately mixed case in the playlist against lower case in the guide: that is what
        // real pairs of files look like, and it is the join most likely to silently miss.
        val guideXml =
            """<?xml version="1.0" encoding="UTF-8"?>
               <tv>
                 <channel id="canal.um"><display-name>Canal Um</display-name></channel>
                 <programme channel="canal.um" start="20260824200000 +0000" stop="20260824210000 +0000">
                   <title>Jornal</title><desc>Noticias.</desc></programme>
                 <programme channel="canal.um" start="20260824210000 +0000" stop="20260824220000 +0000">
                   <title>Novela</title></programme>
               </tv>"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(guideXml))
        server.start()

        try {
            val guideUrl = server.url("/guia.xml").toString()
            val playlist = Files.createTempFile("lista", ".m3u")
            playlist.writeText(
                """
                #EXTM3U url-tvg="$guideUrl"
                #EXTINF:-1 tvg-id="CANAL.UM" group-title="Aberta",Canal Um
                http://127.0.0.1:1/um.ts
                #EXTINF:-1 tvg-id="sem.guia" group-title="Aberta",Canal Sem Guia
                http://127.0.0.1:1/dois.ts
                """.trimIndent(),
            )

            try {
                val catalog =
                    InMemoryCatalogRepository().importLocal(
                        path = playlist,
                        sourceLabel = "Lista de teste",
                    )

                // The address survived the import.
                assertEquals(listOf(guideUrl), catalog.epgUrls, "the guide address reached the catalogue")

                val source =
                    XmltvGuideSource(
                        OkHttpClient.Builder()
                            .connectTimeout(2, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build(),
                    )
                assertTrue(source.load(catalog.epgUrls), "the guide loaded")

                // 20:30 UTC, inside the first programme.
                val at = 1_787_603_400L
                val withGuide = catalog.channels.first { it.name == "Canal Um" }
                val (now, next) = source.shortEpg(withGuide.tvgId).nowAndNext(at)
                assertNotNull(now, "the channel found its schedule despite the difference in case")
                assertEquals("Jornal", now.title)
                assertEquals("Novela", next?.title)

                // And a channel the guide never mentions stays empty rather than borrowing one.
                val without = catalog.channels.first { it.name == "Canal Sem Guia" }
                assertNull(source.shortEpg(without.tvgId).nowAndNext(at).first)
            } finally {
                Files.deleteIfExists(playlist)
            }
        } finally {
            server.shutdown()
        }
    }
}
