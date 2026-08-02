package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CastComponentsTest {
    @Test
    fun `normalizes provider cast text into unique clickable people`() {
        assertEquals(
            listOf("Ana Silva", "Bruno Costa", "Carla Dias"),
            " Ana Silva, Bruno Costa / Ana Silva; Carla Dias ".toCastNames(),
        )
    }
}
