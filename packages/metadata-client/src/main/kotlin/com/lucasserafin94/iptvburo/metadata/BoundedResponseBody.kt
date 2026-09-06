package com.lucasserafin94.iptvburo.metadata

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.ResponseBody

/**
 * The response body was larger than the configured ceiling.
 *
 * Internal: every client in this package already converts a failed read into a null result, so
 * this only needs to be distinguishable inside that machinery, not by callers.
 */
internal class MetadataResponseTooLargeException : IOException()

/**
 * Reads the whole body as text, refusing anything past [maximumBytes].
 *
 * `ResponseBody.string()` alone materialises whatever the server chooses to send. The declared
 * length is checked first and the stream is then capped anyway, because a chunked response
 * declares nothing — the cap is the only check that always runs.
 */
internal fun ResponseBody.boundedString(maximumBytes: Int): String {
    if (contentLength() > maximumBytes) throw MetadataResponseTooLargeException()
    val bytes = byteStream().readAtMost(maximumBytes + 1)
    if (bytes.size > maximumBytes) throw MetadataResponseTooLargeException()
    return String(bytes, Charsets.UTF_8)
}

private fun InputStream.readAtMost(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, READ_BUFFER_BYTES))
    val buffer = ByteArray(READ_BUFFER_BYTES)
    var remaining = maximumBytes
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        if (read == 0) {
            val singleByte = read()
            if (singleByte < 0) break
            output.write(singleByte)
            remaining -= 1
        } else {
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
    return output.toByteArray()
}

private const val READ_BUFFER_BYTES = 32 * 1024

/**
 * Ceiling for one metadata response, held whole in memory.
 *
 * Every endpoint these clients call answers a small JSON document — a cast list, a ratings row, a
 * poster address. Thirty-two megabytes is orders of magnitude past the largest legitimate answer
 * and short of what would pressure the heap.
 */
internal const val DEFAULT_MAXIMUM_RESPONSE_BYTES = 32 * 1024 * 1024
