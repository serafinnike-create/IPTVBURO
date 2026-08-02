package com.lucasserafin94.iptvburo.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class BuroWindowClassTest {
    @Test
    fun `phone portrait uses compact portrait layout`() {
        assertEquals(
            BuroWindowClass.CompactPortrait,
            resolveBuroWindowClass(widthDp = 393f, heightDp = 852f),
        )
    }

    @Test
    fun `rotated phone uses compact landscape layout`() {
        assertEquals(
            BuroWindowClass.CompactLandscape,
            resolveBuroWindowClass(widthDp = 852f, heightDp = 393f),
        )
    }

    @Test
    fun `television uses expanded layout`() {
        assertEquals(
            BuroWindowClass.Expanded,
            resolveBuroWindowClass(widthDp = 1_920f, heightDp = 1_080f),
        )
    }

    @Test
    fun `narrow split screen stays portrait safe`() {
        assertEquals(
            BuroWindowClass.CompactPortrait,
            resolveBuroWindowClass(widthDp = 480f, heightDp = 700f),
        )
    }
}
