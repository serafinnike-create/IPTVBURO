package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrCodeTest {
    @Test
    fun `encodes the activation url this app actually produces`() {
        val matrix = QrCode.encode("https://iptvburo.app/activate?code=ABCD-EFGH-JKLM")

        assertNotNull(matrix)
        // Size is always 4 * version + 17, so a valid matrix cannot be an arbitrary square.
        assertEquals(0, (matrix.size - 17) % 4)
        assertTrue(matrix.size >= 21, "matrix too small: ${matrix.size}")
    }

    @Test
    fun `finder patterns land in all three corners`() {
        // A reader locates the code by these three squares; if they are wrong nothing else matters.
        val matrix = assertNotNull(QrCode.encode("https://iptvburo.app/activate"))
        val last = matrix.size - 1

        listOf(0 to 0, last - 6 to 0, 0 to last - 6).forEach { (ox, oy) ->
            assertTrue(matrix[ox, oy], "finder outer ring missing at $ox,$oy")
            assertTrue(matrix[ox + 3, oy + 3], "finder centre missing at $ox,$oy")
            // The ring one module inside the border must be light.
            assertTrue(!matrix[ox + 1, oy + 1], "finder separator wrong at $ox,$oy")
        }
    }

    @Test
    fun `timing pattern alternates`() {
        val matrix = assertNotNull(QrCode.encode("https://iptvburo.app/activate"))

        for (i in 8 until matrix.size - 8) {
            assertEquals(i % 2 == 0, matrix[i, 6], "horizontal timing wrong at $i")
            assertEquals(i % 2 == 0, matrix[6, i], "vertical timing wrong at $i")
        }
    }

    @Test
    fun `the dark module is always set`() {
        // Required by the specification at (8, size - 8); readers use it to confirm orientation.
        val matrix = assertNotNull(QrCode.encode("hello"))

        assertTrue(matrix[8, matrix.size - 8])
    }

    @Test
    fun `longer text selects a larger version`() {
        val small = assertNotNull(QrCode.encode("https://iptvburo.app"))
        val large = assertNotNull(QrCode.encode("https://iptvburo.app/activate?code=" + "A".repeat(90)))

        assertTrue(large.size > small.size, "expected a larger matrix for longer input")
    }

    @Test
    fun `text beyond the supported range returns null instead of a broken matrix`() {
        // The caller falls back to showing the code as text; a silently unreadable QR would be
        // worse than no QR.
        assertNull(QrCode.encode("A".repeat(400)))
    }

    @Test
    fun `encoding is deterministic`() {
        val first = assertNotNull(QrCode.encode("https://iptvburo.app/activate?code=TEST-TEST-TEST"))
        val second = assertNotNull(QrCode.encode("https://iptvburo.app/activate?code=TEST-TEST-TEST"))

        assertEquals(first.size, second.size)
        for (y in 0 until first.size) {
            for (x in 0 until first.size) {
                assertEquals(first[x, y], second[x, y], "mismatch at $x,$y")
            }
        }
    }
}
