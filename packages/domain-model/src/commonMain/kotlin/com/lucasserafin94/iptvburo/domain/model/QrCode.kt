package com.lucasserafin94.iptvburo.domain.model

/**
 * Minimal QR encoder for the activation URL.
 *
 * Written rather than pulled in as a dependency: ZXing is ~500 KB of decoder, camera and format
 * support for what is one short ASCII URL per screen. This covers byte mode, versions 1–10 and
 * error correction level M, which is enough for any URL the activation flow produces, and it is
 * shared by Android and Windows.
 *
 * Beyond version 10 the alignment-pattern table grows; [encode] reports that case rather than
 * silently emitting an unreadable matrix.
 */
object QrCode {
    /** Square bit matrix. `true` is a dark module. */
    class Matrix(val size: Int) {
        private val bits = BooleanArray(size * size)

        operator fun get(
            x: Int,
            y: Int,
        ): Boolean = bits[y * size + x]

        internal operator fun set(
            x: Int,
            y: Int,
            value: Boolean,
        ) {
            bits[y * size + x] = value
        }
    }

    /**
     * Encodes [text] as a QR matrix, or returns null when it does not fit within version 10.
     *
     * Returning null instead of throwing keeps the caller's fallback simple: show the code as text
     * rather than crash a settings screen over a cosmetic feature.
     */
    fun encode(text: String): Matrix? {
        val data = text.toByteArray(Charsets.ISO_8859_1)
        val version = (1..10).firstOrNull { data.size <= byteCapacity(it) } ?: return null

        val totalCodewords = totalDataCodewords(version)
        val bits = BitBuffer()
        bits.append(0b0100, 4) // byte mode
        bits.append(data.size, if (version < 10) 8 else 16)
        data.forEach { bits.append(it.toInt() and 0xFF, 8) }

        // Terminator, then pad to a byte boundary, then the two alternating pad codewords the
        // specification mandates.
        repeat(minOf(4, totalCodewords * 8 - bits.length)) { bits.append(0, 1) }
        while (bits.length % 8 != 0) bits.append(0, 1)
        var padAlternate = true
        while (bits.length / 8 < totalCodewords) {
            bits.append(if (padAlternate) 0xEC else 0x11, 8)
            padAlternate = !padAlternate
        }

        val blocks = splitIntoBlocks(bits.toBytes(), version)
        val matrix = Matrix(version * 4 + 17)
        drawFunctionPatterns(matrix, version)
        drawVersion(matrix, version)
        drawCodewords(matrix, blocks, version)
        applyBestMask(matrix, version)
        return matrix
    }

    // -- capacity tables (level M) -----------------------------------------------------------

    private val DATA_CODEWORDS_M =
        intArrayOf(0, 16, 28, 44, 64, 86, 108, 124, 154, 182, 216)
    private val EC_CODEWORDS_M =
        intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    private val EC_BLOCKS_M =
        intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

    private fun totalDataCodewords(version: Int) = DATA_CODEWORDS_M[version]

    private fun byteCapacity(version: Int): Int {
        val headerBits = 4 + if (version < 10) 8 else 16
        return (totalDataCodewords(version) * 8 - headerBits) / 8
    }

    // -- bit buffer ---------------------------------------------------------------------------

    private class BitBuffer {
        private val data = ArrayList<Boolean>()
        val length get() = data.size

        fun append(
            value: Int,
            bitCount: Int,
        ) {
            for (index in bitCount - 1 downTo 0) data.add((value ushr index) and 1 == 1)
        }

        fun toBytes(): ByteArray {
            val out = ByteArray(data.size / 8)
            data.forEachIndexed { index, bit ->
                if (bit) {
                    val byteIndex = index / 8
                    if (byteIndex < out.size) {
                        out[byteIndex] = (out[byteIndex].toInt() or (1 shl (7 - index % 8))).toByte()
                    }
                }
            }
            return out
        }
    }

    // -- Reed-Solomon --------------------------------------------------------------------------

    private val EXP = IntArray(256)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        EXP[255] = EXP[0]
    }

    private fun multiply(
        a: Int,
        b: Int,
    ): Int = if (a == 0 || b == 0) 0 else EXP[(LOG[a] + LOG[b]) % 255]

    private fun generatorPolynomial(degree: Int): IntArray {
        var result = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(result.size + 1)
            for (j in result.indices) {
                next[j] = next[j] xor multiply(result[j], 1)
                next[j + 1] = next[j + 1] xor multiply(result[j], EXP[i])
            }
            result = next
        }
        return result
    }

    private fun errorCorrection(
        data: ByteArray,
        ecCount: Int,
    ): ByteArray {
        val generator = generatorPolynomial(ecCount)
        val result = IntArray(ecCount)
        for (byte in data) {
            val factor = (byte.toInt() and 0xFF) xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) {
                result[i] = result[i] xor multiply(generator[i + 1], factor)
            }
        }
        return ByteArray(ecCount) { result[it].toByte() }
    }

    private fun splitIntoBlocks(
        data: ByteArray,
        version: Int,
    ): ByteArray {
        val blockCount = EC_BLOCKS_M[version]
        val ecPerBlock = EC_CODEWORDS_M[version]
        val shortLength = data.size / blockCount
        val longBlocks = data.size % blockCount

        val dataBlocks = ArrayList<ByteArray>()
        val ecBlocks = ArrayList<ByteArray>()
        var offset = 0
        for (index in 0 until blockCount) {
            val length = shortLength + if (index >= blockCount - longBlocks) 1 else 0
            val block = data.copyOfRange(offset, offset + length)
            offset += length
            dataBlocks += block
            ecBlocks += errorCorrection(block, ecPerBlock)
        }

        // Interleave, as the specification requires.
        val out = ArrayList<Byte>()
        val maxData = dataBlocks.maxOf { it.size }
        for (i in 0 until maxData) {
            dataBlocks.forEach { block -> if (i < block.size) out += block[i] }
        }
        for (i in 0 until ecPerBlock) {
            ecBlocks.forEach { block -> out += block[i] }
        }
        return out.toByteArray()
    }

    // -- matrix --------------------------------------------------------------------------------

    private fun isFunctionModule(
        size: Int,
        version: Int,
        x: Int,
        y: Int,
    ): Boolean {
        if (x < 9 && y < 9) return true
        if (x >= size - 8 && y < 9) return true
        if (x < 9 && y >= size - 8) return true
        if (x == 6 || y == 6) return true
        if (version >= 7 && ((x >= size - 11 && y < 6) || (y >= size - 11 && x < 6))) return true
        if (version >= 2) {
            val centres = alignmentCentres(version)
            for (cx in centres) {
                for (cy in centres) {
                    if (cx <= 8 && cy <= 8) continue
                    if (cx >= size - 9 && cy <= 8) continue
                    if (cx <= 8 && cy >= size - 9) continue
                    if (x in cx - 2..cx + 2 && y in cy - 2..cy + 2) return true
                }
            }
        }
        return false
    }

    private fun alignmentCentres(version: Int): IntArray =
        when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            7 -> intArrayOf(6, 22, 38)
            8 -> intArrayOf(6, 24, 42)
            9 -> intArrayOf(6, 26, 46)
            10 -> intArrayOf(6, 28, 50)
            else -> intArrayOf(6)
        }

    private fun drawFunctionPatterns(
        matrix: Matrix,
        version: Int,
    ) {
        val size = matrix.size
        fun finder(ox: Int, oy: Int) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val x = ox + dx
                    val y = oy + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val ring = maxOf(kotlin.math.abs(dx - 3), kotlin.math.abs(dy - 3))
                    matrix[x, y] = ring != 2 && ring <= 3
                }
            }
        }
        finder(0, 0)
        finder(size - 7, 0)
        finder(0, size - 7)

        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            matrix[i, 6] = dark
            matrix[6, i] = dark
        }

        if (version >= 2) {
            val centres = alignmentCentres(version)
            for (cx in centres) {
                for (cy in centres) {
                    if (cx <= 8 && cy <= 8) continue
                    if (cx >= size - 9 && cy <= 8) continue
                    if (cx <= 8 && cy >= size - 9) continue
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            matrix[cx + dx, cy + dy] =
                                maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                        }
                    }
                }
            }
        }
        matrix[8, size - 8] = true
    }

    private fun drawCodewords(
        matrix: Matrix,
        data: ByteArray,
        version: Int,
    ) {
        val size = matrix.size
        var bitIndex = 0
        var column = size - 1
        while (column >= 1) {
            if (column == 6) column = 5
            for (step in 0 until size) {
                for (offset in 0 until 2) {
                    val x = column - offset
                    val upward = ((column + 1) and 2) == 0
                    val y = if (upward) size - 1 - step else step
                    if (isFunctionModule(size, version, x, y)) continue
                    if (bitIndex < data.size * 8) {
                        val byte = data[bitIndex / 8].toInt() and 0xFF
                        matrix[x, y] = (byte ushr (7 - bitIndex % 8)) and 1 == 1
                        bitIndex++
                    }
                }
            }
            column -= 2
        }
    }

    /** Version 7+ requires two copies of its BCH-protected 18-bit version information. */
    private fun drawVersion(
        matrix: Matrix,
        version: Int,
    ) {
        if (version < 7) return
        var remainder = version shl 12
        for (bit in 17 downTo 12) {
            if ((remainder ushr bit) and 1 == 1) {
                remainder = remainder xor (0x1F25 shl (bit - 12))
            }
        }
        val bits = (version shl 12) or remainder
        for (index in 0 until 18) {
            val dark = (bits ushr index) and 1 == 1
            matrix[matrix.size - 11 + index % 3, index / 3] = dark
            matrix[index / 3, matrix.size - 11 + index % 3] = dark
        }
    }

    /**
     * Applies mask 0 and writes the matching format bits.
     *
     * A full implementation scores all eight masks; for a short fixed-shape URL the gain is not
     * worth the code, and any conforming reader decodes mask 0 exactly as well.
     */
    private fun applyBestMask(
        matrix: Matrix,
        version: Int,
    ) {
        val size = matrix.size
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunctionModule(size, version, x, y)) continue
                if ((x + y) % 2 == 0) matrix[x, y] = !matrix[x, y]
            }
        }

        // Level M, mask 0 → format value 0b101010000010010 after BCH and the fixed XOR.
        val format = 0b101010000010010
        for (i in 0..5) matrix[8, i] = (format ushr i) and 1 == 1
        matrix[8, 7] = (format ushr 6) and 1 == 1
        matrix[8, 8] = (format ushr 7) and 1 == 1
        matrix[7, 8] = (format ushr 8) and 1 == 1
        for (i in 9..14) matrix[14 - i, 8] = (format ushr i) and 1 == 1
        for (i in 0..7) matrix[size - 1 - i, 8] = (format ushr i) and 1 == 1
        for (i in 8..14) matrix[8, size - 15 + i] = (format ushr i) and 1 == 1
    }
}
