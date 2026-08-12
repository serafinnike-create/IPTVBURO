package com.lucasserafin94.iptvburo.desktop.license

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The QR code on the activation screen.
 *
 * These tests decode rather than inspect. An earlier hand-written encoder passed a full set of
 * structural checks — finder patterns in place, timing rows alternating, correct data and error
 * correction bytes — and produced codes no reader could decode. Checking the parts is not the same
 * as checking the whole, and here only the whole matters: a code that does not scan is invisible
 * until a customer is standing in front of it.
 */
class QrCodeTest {

    @Test
    fun `a purchase url survives a round trip`() {
        val url = LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW")

        assertEquals(url, decode(QrCode.encode(url)))
    }

    @Test
    fun `a short url survives a round trip`() {
        val url = "https://a.co/x"

        assertEquals(url, decode(QrCode.encode(url)))
    }

    @Test
    fun `an activation url survives a round trip`() {
        val url = "https://${LicenseEndpoints.DOMAIN}/ativar?device=AAAA-BBBB-CCCC"

        assertEquals(url, decode(QrCode.encode(url)))
    }

    /**
     * Every device id shape the fingerprint can produce.
     *
     * The alphabet excludes 0/O and 1/I, so these are the characters that actually occur. A code
     * that failed only for certain ids would be close to impossible to diagnose from a support
     * message.
     */
    @Test
    fun `every plausible device id encodes and decodes`() {
        val ids = listOf(
            "AAAA-AAAA-AAAA",
            "9999-9999-9999",
            "FP86-XARB-9JZW",
            "ZZZZ-2345-6789",
            "K7M2-N4P8-Q9R3",
        )

        ids.forEach { id ->
            val url = LicenseEndpoints.purchaseUrl(id)
            assertEquals(url, decode(QrCode.encode(url)), "failed for $id")
        }
    }

    @Test
    fun `the grid is square and not empty`() {
        val matrix = QrCode.encode(LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW"))

        assertTrue(matrix.isNotEmpty())
        assertTrue(matrix.all { row -> row.size == matrix.size }, "a QR code is square")
    }

    @Test
    fun `there is no built-in margin`() {
        // The renderer draws its own quiet zone. A margin inside the matrix would be padded twice,
        // making the modules smaller and the code harder to read at the same on-screen size.
        val matrix = QrCode.encode(LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW"))

        assertTrue(matrix[0][0], "the top-left module should be the finder pattern, not blank")
    }

    @Test
    fun `different ids produce different codes`() {
        val first = QrCode.encode(LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW"))
        val second = QrCode.encode(LicenseEndpoints.purchaseUrl("AAAA-BBBB-CCCC"))

        assertTrue(
            first.size != second.size || first.indices.any { !first[it].contentEquals(second[it]) },
            "two different urls encoded identically",
        )
    }

    @Test
    fun `encoding is deterministic`() {
        val url = LicenseEndpoints.purchaseUrl("FP86-XARB-9JZW")
        val first = QrCode.encode(url)
        val second = QrCode.encode(url)

        first.indices.forEach { row ->
            assertTrue(first[row].contentEquals(second[row]), "row $row differed between runs")
        }
    }

    /**
     * Decodes a grid the way a camera would.
     *
     * The modules are scaled up and given a quiet zone, because a decoder needs both: at one pixel
     * per module there is nothing to threshold, and without the blank border the finder patterns
     * cannot be distinguished from the edge of the image.
     */
    private fun decode(matrix: Array<BooleanArray>): String {
        val scale = 6
        val quiet = 4
        val side = (matrix.size + quiet * 2) * scale

        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until side) {
            for (x in 0 until side) {
                val row = y / scale - quiet
                val column = x / scale - quiet
                val dark = row in matrix.indices &&
                    column in matrix.indices &&
                    matrix[row][column]
                if (dark) pixels[y * side + x] = 0xFF000000.toInt()
            }
        }

        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader()
            .decode(bitmap, mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"))
            .text
    }
}
