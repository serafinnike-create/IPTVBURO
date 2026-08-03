package com.lucasserafin94.iptvburo.stalker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StalkerMacAddressTest {
    @Test
    fun `accepts the shapes providers actually send`() {
        val expected = "00:1A:79:AB:CD:EF"
        listOf(
            "00:1A:79:AB:CD:EF",
            "00-1a-79-ab-cd-ef",
            "001A79ABCDEF",
            "00.1a.79.ab.cd.ef",
            "  00:1a:79:ab:cd:ef  ",
        ).forEach { input ->
            assertEquals(expected, StalkerMacAddress.normalise(input), "failed for: $input")
        }
    }

    @Test
    fun `rejects values that are not a MAC`() {
        listOf("", "00:1A:79:AB:CD", "00:1A:79:AB:CD:EF:00", "ZZ:1A:79:AB:CD:EF", "hello")
            .forEach { input -> assertNull(StalkerMacAddress.normalise(input), "accepted: $input") }
    }

    @Test
    fun `isValid mirrors normalise`() {
        assertTrue(StalkerMacAddress.isValid("001a79abcdef"))
        assertFalse(StalkerMacAddress.isValid("nope"))
    }

    @Test
    fun `mask keeps only the last octet`() {
        // The MAC is the entire credential on a Stalker portal, so it must never be shown in full.
        assertEquals("**:**:**:**:**:EF", StalkerMacAddress.mask("00:1a:79:ab:cd:ef"))
        assertEquals("<invalid>", StalkerMacAddress.mask("nope"))
    }
}
