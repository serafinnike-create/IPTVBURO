package com.lucasserafin94.iptvburo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BootBackdropSelectionTest {
    @Test
    fun `loading backdrop keeps only distinct usable covers`() {
        val selected =
            selectBootBackdropUrls(
                listOf(null, "", "  https://img.example/a.jpg  ", "https://img.example/a.jpg", "https://img.example/b.jpg"),
            )

        assertEquals(
            listOf("https://img.example/a.jpg", "https://img.example/b.jpg"),
            selected,
        )
    }

    @Test
    fun `loading backdrop has a strict memory and request limit`() {
        val selected = selectBootBackdropUrls((1..40).map { "https://img.example/$it.jpg" })

        assertEquals(20, selected.size)
        assertEquals("https://img.example/20.jpg", selected.last())
    }
}
