package com.lucasserafin94.iptvburo.xtream

import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamCatalogStreamScaleTest {
    @Test
    fun `streams five hundred thousand items without building a catalog list`() {
        var callbackCount = 0
        var finalProviderId: String? = null
        val parser =
            XtreamCatalogStreamParser(
                contentType = XtreamContentType.LIVE,
                maximumItems = 1_000_000,
                sanitizeArtwork = { null },
            )

        val summary =
            GeneratedCatalogInputStream(500_000).use { input ->
                parser.parse(input) { item ->
                    callbackCount += 1
                    finalProviderId = item.providerId
                }
            }

        assertEquals(500_000, callbackCount)
        assertEquals(500_000, summary.itemCount)
        assertEquals(0, summary.skippedItemCount)
        assertEquals("499999", finalProviderId)
    }
}

/** Emits one small JSON object at a time so the fixture itself never becomes a huge String. */
private class GeneratedCatalogInputStream(
    private val itemCount: Int,
) : InputStream() {
    private var nextItem = 0
    private var current = "[".toByteArray(StandardCharsets.UTF_8)
    private var currentOffset = 0
    private var closedArray = false

    override fun read(): Int {
        while (currentOffset >= current.size) {
            if (!advance()) return -1
        }
        return current[currentOffset++].toInt() and 0xff
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
        if (length == 0) return 0
        var written = 0
        while (written < length) {
            while (currentOffset >= current.size) {
                if (!advance()) return if (written == 0) -1 else written
            }
            val count = minOf(length - written, current.size - currentOffset)
            current.copyInto(buffer, offset + written, currentOffset, currentOffset + count)
            currentOffset += count
            written += count
        }
        return written
    }

    private fun advance(): Boolean {
        currentOffset = 0
        current =
            when {
                nextItem < itemCount -> {
                    val prefix = if (nextItem == 0) "" else ","
                    val index = nextItem++
                    ("$prefix{\"stream_id\":$index,\"name\":\"Item $index\"}")
                        .toByteArray(StandardCharsets.UTF_8)
                }
                !closedArray -> {
                    closedArray = true
                    "]".toByteArray(StandardCharsets.UTF_8)
                }
                else -> return false
            }
        return true
    }
}
