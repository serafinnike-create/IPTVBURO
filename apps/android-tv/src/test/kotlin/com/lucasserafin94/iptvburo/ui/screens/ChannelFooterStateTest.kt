package com.lucasserafin94.iptvburo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFooterStateTest {
    @Test
    fun `loading keeps footer visible but blocks duplicate input`() {
        val state =
            resolveChannelFooterState(
                isLoadingMore = true,
                hasMore = true,
                hasError = false,
            )

        assertTrue(state.isVisible)
        assertFalse(state.acceptsInput)
        assertEquals(ChannelFooterAction.NONE, state.action)
    }

    @Test
    fun `failed next page keeps same footer available as retry`() {
        val state =
            resolveChannelFooterState(
                isLoadingMore = false,
                hasMore = true,
                hasError = true,
            )

        assertTrue(state.isVisible)
        assertTrue(state.acceptsInput)
        assertEquals(ChannelFooterAction.RETRY, state.action)
    }

    @Test
    fun `available next page exposes load more action`() {
        val state =
            resolveChannelFooterState(
                isLoadingMore = false,
                hasMore = true,
                hasError = false,
            )

        assertTrue(state.isVisible)
        assertTrue(state.acceptsInput)
        assertEquals(ChannelFooterAction.LOAD_MORE, state.action)
    }

    @Test
    fun `complete catalog hides footer`() {
        val state =
            resolveChannelFooterState(
                isLoadingMore = false,
                hasMore = false,
                hasError = false,
            )

        assertFalse(state.isVisible)
        assertFalse(state.acceptsInput)
        assertEquals(ChannelFooterAction.NONE, state.action)
    }
}
