package com.lucasserafin94.iptvburo.ui.home

import androidx.compose.runtime.Immutable

/**
 * A safe source projection for the home. It intentionally contains no URL,
 * credentials, headers, stream reference, or playable payload.
 */
@Immutable
data class HomeSourceSummary(
    val id: String,
    val name: String,
    val channelCount: Int,
) {
    init {
        require(id.isNotBlank()) { "A home source id cannot be blank." }
        require(name.isNotBlank()) { "A home source name cannot be blank." }
        require(channelCount >= 0) { "A channel count cannot be negative." }
    }
}

@Immutable
data class HomeSection(
    val id: String,
    val hero: HomeItem,
    val rails: List<HomeRail>,
) {
    init {
        require(id.isNotBlank()) { "A home section id cannot be blank." }
        require(rails.map(HomeRail::id).distinct().size == rails.size) {
            "Home rail ids must be unique inside a section."
        }

        val allIds = buildList {
            add(hero.id)
            for (rail in rails) {
                addAll(rail.items.map(HomeItem::id))
            }
        }
        require(allIds.distinct().size == allIds.size) {
            "Home item ids must be unique inside a section."
        }
    }

    fun findItem(itemId: String): HomeItem? {
        if (hero.id == itemId) return hero
        return rails.firstNotNullOfOrNull { rail ->
            rail.items.firstOrNull { item -> item.id == itemId }
        }
    }

    fun resolveInitialFocusId(requestedItemId: String?): String =
        requestedItemId
            ?.takeIf { itemId -> findItem(itemId) != null }
            ?: hero.id
}

@Immutable
data class HomeRail(
    val id: String,
    val title: String,
    val kind: HomeRailKind,
    val cardFormat: HomeCardFormat,
    val items: List<HomeItem>,
    val isDemonstration: Boolean,
) {
    init {
        require(id.isNotBlank()) { "A home rail id cannot be blank." }
        require(title.isNotBlank()) { "A home rail title cannot be blank." }
        require(items.isNotEmpty()) { "A home rail cannot be empty." }
        require(items.map(HomeItem::id).distinct().size == items.size) {
            "Home item ids must be unique inside a rail."
        }
        require(items.all { item -> item.cardFormat == cardFormat }) {
            "Every item in a rail must use the rail card format."
        }
    }
}

@Immutable
data class HomeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val synopsis: String,
    val metadata: String,
    val badge: String,
    val kind: HomeItemKind,
    val cardFormat: HomeCardFormat,
    val palette: HomeArtworkPalette,
    val artwork: HomeArtwork? = null,
    val remoteArtworkUrl: String? = null,
    val progress: Float? = null,
    val isDemonstration: Boolean,
) {
    init {
        require(id.isNotBlank()) { "A home item id cannot be blank." }
        require(title.isNotBlank()) { "A home item title cannot be blank." }
        require(subtitle.isNotBlank()) { "A home item subtitle cannot be blank." }
        require(progress == null || progress.isFinite() && progress in 0f..1f) {
            "Home item progress must be between zero and one."
        }
        require(kind != HomeItemKind.SOURCE || !isDemonstration) {
            "A real source shortcut cannot be marked as demonstration content."
        }
    }

    override fun toString(): String =
        "HomeItem(id=$id, title=$title, kind=$kind, " +
            "remoteArtworkUrl=${if (remoteArtworkUrl == null) "null" else "<redacted>"})"
}

enum class HomeRailKind {
    SOURCES,
    CONTINUE_WATCHING,
    LIVE_NOW,
    EDITORIAL,
}

enum class HomeItemKind {
    DEMO_STORY,
    DEMO_LIVE_STORY,
    SOURCE,
    CATALOG,
}

enum class HomeCardFormat {
    POSTER,
    LANDSCAPE,
}

enum class HomeArtworkPalette {
    AURORA,
    COBALT,
    EMBER,
    FOREST,
    PLUM,
    SOLAR,
}

enum class HomeArtwork {
    PAPER_SUN,
    FOREST_SIGNAL,
}

@Immutable
sealed interface LivingHomeUiState {
    @Immutable
    data object Ready : LivingHomeUiState

    @Immutable
    data object Loading : LivingHomeUiState

    @Immutable
    data object Empty : LivingHomeUiState

    @Immutable
    data class Error(val message: String? = null) : LivingHomeUiState
}
