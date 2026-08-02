package com.lucasserafin94.iptvburo.playlist

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun `parses the documented Apple HLS fixture`() {
        val result = fixture("apple-bipbop.m3u").use(M3uParser()::parse)

        assertEquals("IPTV BURO Apple Legal Fixture", result.header.name)
        assertEquals(listOf("https://example.invalid/epg.xml"), result.header.epgUrls)
        assertEquals(0.0, result.header.tvgShiftHours)
        assertEquals(1, result.channels.size)
        assertTrue(result.warnings.isEmpty())
        assertTrue(result.bytesRead > 0)

        val channel = result.channels.single()
        assertEquals("Apple BipBop — Official HLS Sample", channel.name)
        assertEquals(
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/" +
                "bipbop_16x9/bipbop_16x9_variant.m3u8",
            channel.streamUri,
        )
        assertEquals(-1L, channel.durationSeconds)
        assertEquals("apple-bipbop", channel.tvgId)
        assertEquals("Apple BipBop", channel.tvgName)
        assertEquals("https://example.invalid/logos/apple-bipbop.png", channel.logoUri)
        assertEquals("Legal Samples", channel.groupTitle)
        assertEquals("Legal Samples", channel.tags["EXTGRP"])
        assertEquals("en", channel.attributes["language"])
        assertEquals("IPTV-BURO-Legal-Test/0.1", channel.requestHeaders["User-Agent"])
        assertEquals(
            "https://developer.apple.com/streaming/examples/",
            channel.requestHeaders["Referer"],
        )
    }

    @Test
    fun `parses quoted attributes commas generic tags and logo fallback`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:123.8 tvg-id="news-1" tvg-name="News, International" group-title="News and Talk",Display, Name
            #EXTGENRE:News
            #EXTIMG:https://example.invalid/fallback-logo.png
            https://example.invalid/news/master.m3u8
            """.trimIndent()

        val result = parse(playlist)
        val channel = result.channels.single()

        assertEquals("Display, Name", channel.name)
        assertEquals(123L, channel.durationSeconds)
        assertEquals("News, International", channel.tvgName)
        assertEquals("News and Talk", channel.groupTitle)
        assertEquals("News", channel.tags["EXTGENRE"])
        assertEquals("https://example.invalid/fallback-logo.png", channel.logoUri)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `combines known header formats using the most specific value`() {
        val playlist =
            """
            #EXTM3U http-user-agent="Global Agent"
            #EXTINF:-1 http-referrer="https://entry.example" tvg-name="Headers",Headers
            #EXTVLCOPT:http-user-agent=Entry Agent
            #EXTHTTP:{"Origin":"https://origin.example","Cookie":"secret-cookie"}
            https://example.invalid/headers.m3u8|User-Agent=Pipe%20Agent&Referer=https%3A%2F%2Fpipe.example&Authorization=Bearer%20secret-token
            """.trimIndent()

        val channel = parse(playlist).channels.single()

        assertEquals("Pipe Agent", channel.requestHeaders["User-Agent"])
        assertEquals("https://pipe.example", channel.requestHeaders["Referer"])
        assertEquals("https://origin.example", channel.requestHeaders["Origin"])
        assertEquals("secret-cookie", channel.requestHeaders["Cookie"])
        assertEquals("Bearer secret-token", channel.requestHeaders["Authorization"])
    }

    @Test
    fun `parses Kodi encoded stream headers`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Kodi headers
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Kodi%20Agent&Referer=https%3A%2F%2Fkodi.example
            https://example.invalid/kodi.m3u8
            """.trimIndent()

        val result = parse(playlist)
        val channel = result.channels.single()

        assertEquals("Kodi Agent", channel.requestHeaders["User-Agent"])
        assertEquals("https://kodi.example", channel.requestHeaders["Referer"])
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `reports recoverable warnings and continues with the next valid entry`() {
        val playlist =
            """
            #EXTINF:not-a-duration broken-attribute,Incomplete
            #EXTINF:-1 tvg-name="Recovered",Recovered
            https://example.invalid/recovered.m3u8
            javascript://example.invalid/unsafe
            """.trimIndent()

        val result = parse(playlist)
        val codes = result.warnings.map(M3uWarning::code).toSet()

        assertEquals(1, result.channels.size)
        assertEquals("Recovered", result.channels.single().name)
        assertTrue(M3uWarningCode.MISSING_PLAYLIST_HEADER in codes)
        assertTrue(M3uWarningCode.INVALID_DURATION in codes)
        assertTrue(M3uWarningCode.MALFORMED_ATTRIBUTE in codes)
        assertTrue(M3uWarningCode.MISSING_CHANNEL_URI in codes)
        assertTrue(M3uWarningCode.UNSAFE_STREAM_URI_SCHEME in codes)
    }

    @Test
    fun `skips a malformed uri and resumes parsing`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Broken
            https://example.invalid/a path/master.m3u8
            #EXTINF:-1,Valid
            https://example.invalid/valid.m3u8
            """.trimIndent()

        val result = parse(playlist)

        assertEquals(listOf("Valid"), result.channels.map(ParsedChannel::name))
        assertTrue(
            result.warnings.any { it.code == M3uWarningCode.INVALID_STREAM_URI },
        )
    }

    @Test
    fun `accepts input exactly at the byte limit`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Exact limit
            https://example.invalid/exact.m3u8
            """.trimIndent()
        val bytes = playlist.toByteArray(StandardCharsets.UTF_8)
        val parser = M3uParser(M3uParserLimits(maxBytes = bytes.size.toLong()))

        val result = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(1, result.channels.size)
        assertEquals(bytes.size.toLong(), result.bytesRead)
    }

    @Test
    fun `rejects input above the byte limit without exposing input`() {
        val secret = "secret-token-that-must-not-be-reported"
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Limit
            https://example.invalid/limit.m3u8?token=$secret
            """.trimIndent()
        val bytes = playlist.toByteArray(StandardCharsets.UTF_8)
        val parser = M3uParser(M3uParserLimits(maxBytes = (bytes.size - 1).toLong()))

        val error =
            assertThrows(M3uLimitExceededException::class.java) {
                parser.parse(ByteArrayInputStream(bytes))
            }

        assertEquals((bytes.size - 1).toLong(), error.maxBytes)
        assertFalse(error.message.orEmpty().contains(secret))
    }

    @Test
    fun `enforces channel and line limits`() {
        val twoChannels = fixture("synthetic-two-channels.m3u").use { it.readBytes() }
        val channelLimited =
            M3uParser(
                M3uParserLimits(
                    maxBytes = twoChannels.size.toLong(),
                    maxChannels = 1,
                ),
            )

        assertThrows(M3uChannelLimitExceededException::class.java) {
            channelLimited.parse(ByteArrayInputStream(twoChannels))
        }

        val longLine =
            """
            #EXTM3U
            #EXTINF:-1,This line is longer than configured
            https://example.invalid/line.m3u8
            """.trimIndent()
        assertThrows(M3uLineLimitExceededException::class.java) {
            M3uParser(M3uParserLimits(maxLineLength = 16)).parse(
                ByteArrayInputStream(longLine.toByteArray(StandardCharsets.UTF_8)),
            )
        }
    }

    @Test
    fun `streaming api emits channels without returning a channel collection`() {
        val emitted = mutableListOf<ParsedChannel>()

        val summary =
            fixture("synthetic-two-channels.m3u").use { input ->
                M3uParser().parseStreaming(input, emitted::add)
            }

        assertEquals(2, summary.channelCount)
        assertEquals(listOf("Synthetic Live", "Synthetic VOD"), emitted.map(ParsedChannel::name))
        assertTrue(summary.warnings.isEmpty())
    }

    @Test
    fun `supports UTF-8 BOM and CRLF input`() {
        val playlist =
            "\uFEFF#EXTM3U\r\n" +
                "#EXTINF:-1 tvg-name=\"BOM channel\",BOM channel\r\n" +
                "https://example.invalid/bom.m3u8\r\n"

        val result = parse(playlist)

        assertEquals("BOM channel", result.channels.single().name)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `supports UTF-16 little and big endian BOM input`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1 group-title="Notícias",Ação e Café
            https://stream.provider.example/live/channel-1.m3u8
            """.trimIndent()
        val encodings =
            listOf(
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) to StandardCharsets.UTF_16LE,
                byteArrayOf(0xFE.toByte(), 0xFF.toByte()) to StandardCharsets.UTF_16BE,
            )

        encodings.forEach { (bom, charset) ->
            val payload = bom + playlist.toByteArray(charset)
            val result = M3uParser().parse(ByteArrayInputStream(payload))

            assertEquals("Ação e Café", result.channels.single().name)
            assertEquals("Notícias", result.channels.single().groupTitle)
            assertTrue(result.warnings.isEmpty())
            assertEquals(payload.size.toLong(), result.bytesRead)
        }
    }

    @Test
    fun `falls back to Windows-1252 with an explicit warning`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1 group-title="News",Â£ Café
            https://stream.provider.example/live/channel-2.ts
            """.trimIndent()
        val windows1252 = Charset.forName("windows-1252")

        val result =
            M3uParser().parse(
                ByteArrayInputStream(playlist.toByteArray(windows1252)),
            )

        assertEquals("Â£ Café", result.channels.single().name)
        assertEquals("News", result.channels.single().groupTitle)
        assertEquals(
            1,
            result.warnings.count {
                it.code == M3uWarningCode.WINDOWS_1252_FALLBACK
            },
        )
        assertEquals(0, result.suppressedWarningCount)
    }

    @Test
    fun `treats a UTF-8 BOM as authoritative and reports malformed bytes`() {
        val malformedUtf8 =
            byteArrayOf(
                0xEF.toByte(),
                0xBB.toByte(),
                0xBF.toByte(),
                '#'.code.toByte(),
                0xE9.toByte(),
            )

        assertThrows(MalformedInputException::class.java) {
            M3uParser().parse(ByteArrayInputStream(malformedUtf8))
        }
    }

    @Test
    fun `caps warnings and reports how many were suppressed`() {
        val playlist =
            (1..10).joinToString(separator = "\n") { index ->
                "javascript://unsafe.example/channel-$index"
            }
        val parser = M3uParser(M3uParserLimits(maxWarnings = 3))
        val playlistBytes = playlist.toByteArray(StandardCharsets.UTF_8)

        val result = parser.parse(
            ByteArrayInputStream(playlistBytes),
        )
        val summary =
            parser.parseStreaming(ByteArrayInputStream(playlistBytes)) {
                throw AssertionError("The unsafe input must not emit channels")
            }

        assertEquals(3, result.warnings.size)
        assertEquals(9, result.suppressedWarningCount)
        assertEquals(3, summary.warnings.size)
        assertEquals(9, summary.suppressedWarningCount)
        assertTrue(
            result.warnings.any {
                it.code == M3uWarningCode.MISSING_PLAYLIST_HEADER
            },
        )
    }

    @Test
    fun `preserves literal plus characters while decoding percent encoded headers`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Encoded headers
            https://stream.provider.example/live/channel-3.m3u8|Authorization=Token+literal%2Bencoded&User-Agent=Agent+Literal%20Space
            """.trimIndent()

        val channel = parse(playlist).channels.single()

        assertEquals(
            "Token+literal+encoded",
            channel.requestHeaders["Authorization"],
        )
        assertEquals(
            "Agent+Literal Space",
            channel.requestHeaders["User-Agent"],
        )
    }

    @Test(timeout = 120_000)
    fun `default limits stream more than legacy bytes and channel counts`() {
        val legacyMaxBytes = 25L * 1024L * 1024L
        val legacyMaxChannels = 100_000
        val generatedInput =
            GeneratedPlaylistInputStream(
                channelCount = legacyMaxChannels + 1,
                channelNamePadding = 200,
            )
        var emittedChannelCount = 0

        val summary =
            M3uParser().parseStreaming(generatedInput) {
                emittedChannelCount += 1
            }

        assertEquals(legacyMaxChannels + 1, emittedChannelCount)
        assertEquals(legacyMaxChannels + 1, summary.channelCount)
        assertTrue(summary.bytesRead > legacyMaxBytes)
        assertTrue(summary.warnings.isEmpty())
        assertEquals(0, summary.suppressedWarningCount)
        assertEquals(256L * 1024L * 1024L, M3uParserLimits.DEFAULT_MAX_BYTES)
        assertEquals(500_000, M3uParserLimits.DEFAULT_MAX_CHANNELS)
    }

    @Test
    fun `empty input produces header and playlist warnings`() {
        val result = parse("")
        val codes = result.warnings.map(M3uWarning::code)

        assertTrue(M3uWarningCode.MISSING_PLAYLIST_HEADER in codes)
        assertTrue(M3uWarningCode.EMPTY_PLAYLIST in codes)
        assertTrue(result.channels.isEmpty())
        assertEquals(0L, result.bytesRead)
    }

    @Test
    fun `warnings and model strings never expose header or uri secrets`() {
        val secret = "do-not-log-this-secret"
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Unsafe
            #EXTVLCOPT:unsupported-header=$secret
            javascript://example.invalid/?token=$secret
            """.trimIndent()

        val result = parse(playlist)
        val combinedWarnings = result.warnings.joinToString()

        assertFalse(combinedWarnings.contains(secret))
        assertTrue(result.channels.isEmpty())

        val parsedChannel =
            ParsedChannel(
                name = "Secret test",
                streamUri = "https://example.invalid/live.m3u8?token=$secret",
                logoUri = "https://example.invalid/logo.png?token=$secret",
                requestHeaders = mapOf("Authorization" to "Bearer $secret"),
            )
        assertFalse(parsedChannel.toString().contains(secret))
    }

    @Test
    fun `parser leaves input ownership with the caller`() {
        var closed = false
        val input =
            object : ByteArrayInputStream(
                "#EXTM3U\n".toByteArray(StandardCharsets.UTF_8),
            ) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }

        M3uParser().parse(input)

        assertFalse(closed)
    }

    @Test
    fun `missing optional values remain null`() {
        val result =
            parse(
                """
                #EXTM3U
                https://example.invalid/bare.m3u8
                """.trimIndent(),
            )

        val channel = result.channels.single()
        assertEquals("Channel 1", channel.name)
        assertNull(channel.durationSeconds)
        assertNull(channel.tvgId)
        assertNull(channel.groupTitle)
        assertTrue(
            result.warnings.any { it.code == M3uWarningCode.MISSING_EXTINF },
        )
    }

    private fun parse(value: String): M3uParseResult =
        M3uParser().parse(
            ByteArrayInputStream(value.toByteArray(StandardCharsets.UTF_8)),
        )

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing test fixture: $name"
        }

    private class GeneratedPlaylistInputStream(
        private val channelCount: Int,
        channelNamePadding: Int,
    ) : InputStream() {
        private val padding = "x".repeat(channelNamePadding)
        private var currentChunk =
            "#EXTM3U\n".toByteArray(StandardCharsets.UTF_8)
        private var currentOffset = 0
        private var nextChannelIndex = 0

        override fun read(): Int {
            if (!ensureChunk()) return -1
            return currentChunk[currentOffset++].toInt() and 0xFF
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
            if (length == 0) return 0

            var written = 0
            while (written < length && ensureChunk()) {
                val count =
                    minOf(
                        length - written,
                        currentChunk.size - currentOffset,
                    )
                currentChunk.copyInto(
                    destination = buffer,
                    destinationOffset = offset + written,
                    startIndex = currentOffset,
                    endIndex = currentOffset + count,
                )
                currentOffset += count
                written += count
            }
            return if (written == 0) -1 else written
        }

        private fun ensureChunk(): Boolean {
            if (currentOffset < currentChunk.size) return true
            if (nextChannelIndex >= channelCount) return false

            val channelNumber = nextChannelIndex + 1
            currentChunk =
                buildString {
                    append("#EXTINF:-1 tvg-id=\"synthetic-")
                    append(channelNumber)
                    append("\" group-title=\"Scale\",Channel ")
                    append(channelNumber)
                    append(' ')
                    append(padding)
                    append('\n')
                    append("https://stream.provider.example/live/channel-")
                    append(channelNumber)
                    append(".ts\n")
                }.toByteArray(StandardCharsets.UTF_8)
            currentOffset = 0
            nextChannelIndex += 1
            return true
        }
    }
}
