package com.lucasserafin94.iptvburo.ui

data class SourceUi(
    val id: String,
    val name: String,
    val channelCount: Int,
)

data class CategoryUi(
    val id: String?,
    val name: String,
    val channelCount: Int,
)

data class ChannelUi(
    val id: String,
    val name: String,
    val categoryName: String?,
    val streamUrl: String,
    val logoUrl: String?,
    val requestHeaders: Map<String, String> = emptyMap(),
)

enum class AppSection {
    HOME,
    LIVE,
    MOVIES,
    SERIES,
    DISCOVER,
    MY_BURO,
    SEARCH,
    PROFILE,
    SOURCES,
    SETTINGS,
}

sealed interface AppContent {
    data object Home : AppContent
    data object Sources : AppContent

    data class SectionPlaceholder(
        val section: AppSection,
    ) : AppContent

    data class Story(
        val itemId: String,
    ) : AppContent

    data class Categories(
        val sourceId: String,
        val sourceName: String,
    ) : AppContent

    data class Channels(
        val sourceId: String,
        val sourceName: String,
        val categoryId: String?,
        val categoryName: String,
    ) : AppContent

    data class Player(
        val channel: ChannelUi,
    ) : AppContent

    data object Settings : AppContent
}

data class AppUiState(
    val isInitializing: Boolean = true,
    val hasAcceptedLegalNotice: Boolean = false,
    val section: AppSection = AppSection.HOME,
    val content: AppContent = AppContent.Home,
    val lastFocusedHomeItemId: String? = null,
    val sources: List<SourceUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val channels: List<ChannelUi> = emptyList(),
    val isImporting: Boolean = false,
    val lastImportedChannelCount: Int? = null,
    val hasImportError: Boolean = false,
)
