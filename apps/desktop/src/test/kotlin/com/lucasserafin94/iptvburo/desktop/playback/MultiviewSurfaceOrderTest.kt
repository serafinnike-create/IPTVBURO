package com.lucasserafin94.iptvburo.desktop.playback

import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MultiviewSurfaceOrderTest {
    @Test
    fun `reordering survivors does not remove them from their native parent`() {
        val panel = TrackingPanel()
        val first = JLabel("first")
        val second = JLabel("second")
        val third = JLabel("third")
        synchronizeMultiviewComponents(panel, listOf(first, second, third))
        panel.removals = 0

        synchronizeMultiviewComponents(panel, listOf(third, first, second))

        assertEquals(0, panel.removals, "surviving video surfaces must keep their native peers")
        assertContentEquals(arrayOf(third, first, second), panel.components)
        assertSame(panel, first.parent)
        assertSame(panel, second.parent)
        assertSame(panel, third.parent)
    }

    @Test
    fun `only a tile that disappeared is detached`() {
        val panel = TrackingPanel()
        val first = JLabel("first")
        val removed = JLabel("removed")
        val last = JLabel("last")
        synchronizeMultiviewComponents(panel, listOf(first, removed, last))
        panel.removals = 0

        synchronizeMultiviewComponents(panel, listOf(first, last))

        assertEquals(1, panel.removals)
        assertContentEquals(arrayOf(first, last), panel.components)
    }

    private class TrackingPanel : JPanel() {
        var removals = 0

        override fun remove(component: Component?) {
            removals += 1
            super.remove(component)
        }
    }
}
