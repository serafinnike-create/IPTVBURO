package com.lucasserafin94.iptvburo.ui.screens

internal enum class ChannelFooterAction {
    NONE,
    LOAD_MORE,
    RETRY,
}

internal data class ChannelFooterState(
    val isVisible: Boolean,
    val acceptsInput: Boolean,
    val action: ChannelFooterAction,
)

internal fun resolveChannelFooterState(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasError: Boolean,
): ChannelFooterState =
    when {
        isLoadingMore ->
            ChannelFooterState(
                isVisible = true,
                acceptsInput = false,
                action = ChannelFooterAction.NONE,
            )

        hasError ->
            ChannelFooterState(
                isVisible = true,
                acceptsInput = true,
                action = ChannelFooterAction.RETRY,
            )

        hasMore ->
            ChannelFooterState(
                isVisible = true,
                acceptsInput = true,
                action = ChannelFooterAction.LOAD_MORE,
            )

        else ->
            ChannelFooterState(
                isVisible = false,
                acceptsInput = false,
                action = ChannelFooterAction.NONE,
            )
    }
