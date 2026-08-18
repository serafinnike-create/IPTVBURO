package com.lucasserafin94.iptvburo.xtream

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Reports bytes to a [DownloadRate] as they are read, without buffering or copying them.
 *
 * A wrapper rather than a change to each read site: the client already reads a catalogue two ways,
 * whole into memory for the small endpoints and streamed for the large ones, and both go through
 * an `InputStream`. Counting here means one place to get right and no change to how either path
 * parses what it receives.
 *
 * The rate is fed on every read, which is cheap — an add and a timestamp. Publishing it to a
 * screen is what must not happen per block, and that is the caller's decision, not this one's.
 */
internal class CountingInputStream(
    delegate: InputStream,
    private val rate: DownloadRate,
    private val clock: () -> Long,
) : FilterInputStream(delegate) {
    override fun read(): Int {
        val value = super.read()
        if (value >= 0) rate.record(1, clock())
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) rate.record(read, clock())
        return read
    }
}
