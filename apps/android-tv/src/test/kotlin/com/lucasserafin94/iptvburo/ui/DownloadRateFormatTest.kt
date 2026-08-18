package com.lucasserafin94.iptvburo.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRateFormatTest {
    private val english = Locale.forLanguageTag("en")
    private val brazilian = Locale.forLanguageTag("pt-BR")

    @Test
    fun `a megabyte a second reads as one point zero`() {
        assertEquals("1.0 MB", formatDownloadRate(1_024L * 1_024L, english))
    }

    @Test
    fun `kilobytes below a megabyte`() {
        assertEquals("512.0 KB", formatDownloadRate(512L * 1_024L, english))
    }

    @Test
    fun `a crawl is reported in bytes, without a decimal`() {
        // The interesting fact at this speed is that it is slow, not the fraction.
        assertEquals("400 B", formatDownloadRate(400, english))
    }

    @Test
    fun `the viewer's decimal separator is used`() {
        assertEquals("1,4 MB", formatDownloadRate(1_468_006, brazilian))
        assertEquals("1.4 MB", formatDownloadRate(1_468_006, english))
    }

    @Test
    fun `no measurement shows nothing rather than zero`() {
        // A zero on screen reads as "stopped", which is a claim the app cannot make between
        // requests or before enough bytes have arrived to divide by.
        assertNull(formatDownloadRate(null, english))
    }

    @Test
    fun `zero and negative are treated as no measurement`() {
        assertNull(formatDownloadRate(0, english))
        assertNull(formatDownloadRate(-1, english))
    }
}
