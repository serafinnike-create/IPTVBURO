package com.lucasserafin94.iptvburo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The published FIPS 180-4 vectors, plus the boundaries where a hand-written SHA-256 goes wrong.
 *
 * This implementation exists because two callers persist its output, so it has to agree with every
 * other SHA-256 in the world rather than merely with itself. These are the standard vectors —
 * checked against the specification, not against this code — so a mistake in the padding, the
 * length encoding or the constant table shows up here rather than in a user's orphaned library.
 */
class Sha256Test {
    private fun hash(text: String): String = sha256(text.encodeToByteArray()).toHex()

    @Test
    fun `the empty string matches the published vector`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash(""))
    }

    @Test
    fun `abc matches the published vector`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("abc"))
    }

    @Test
    fun `the two-block vector matches`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    /**
     * 55 and 56 bytes straddle the padding boundary.
     *
     * A message of 56 bytes cannot fit its 1 bit and 8-byte length in the same block, so it needs a
     * second one. Getting that wrong is the classic error in a hand-written implementation, and it
     * passes every short test.
     */
    @Test
    fun `messages either side of the padding boundary match`() {
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            hash("a".repeat(55)),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            hash("a".repeat(56)),
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            hash("a".repeat(64)),
        )
    }

    @Test
    fun `a million a characters match the published vector`() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            hash("a".repeat(1_000_000)),
        )
    }

    @Test
    fun `multi-byte characters hash their utf-8 bytes`() {
        // The callers hash titles and feed URLs, which are not all ASCII, so the encoding is part
        // of the contract rather than an implementation detail.
        assertEquals("0664077f33cc3ebbaa4bbdacac0eb70e740983080f01dce29929e73b7785a7ad", hash("ação"))
    }
}
