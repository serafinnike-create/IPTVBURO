package com.lucasserafin94.iptvburo.desktop.ui

import androidx.compose.ui.graphics.Color
import com.lucasserafin94.iptvburo.xtream.XtreamContentType

/** Glyph and tint identifying a catalogue category. */
data class CategoryBadge(
    val glyph: String,
    val tint: Color,
)

/**
 * Picks a badge from the provider's own category wording.
 *
 * The previous mapping resolved almost every category to one of six shared PNGs, so a rail of
 * twenty categories showed the same picture twenty times and carried no information at all. A
 * distinct glyph plus tint per genre means the chips stay distinguishable at a glance.
 *
 * Terms are matched in Portuguese, English and Spanish because providers mix languages freely
 * within one playlist, often inside a single category name.
 */
fun categoryBadgeFor(
    label: String,
    contentType: XtreamContentType,
): CategoryBadge {
    val n = label.lowercase()
    return when {
        "4k" in n || "uhd" in n || "2160" in n -> CategoryBadge("💎", Color(0xFF7BC6FF))
        "sport" in n || "esporte" in n || "futebol" in n || "deporte" in n ->
            CategoryBadge("⚽", Color(0xFF4ED59B))
        "infantil" in n || "kids" in n || "crian" in n || "cartoon" in n ->
            CategoryBadge("🧸", Color(0xFFFFB865))
        "anima" in n -> CategoryBadge("🎨", Color(0xFFFFA34D))
        "terror" in n || "horror" in n -> CategoryBadge("💀", Color(0xFFFF6B6B))
        "acao" in n || "ação" in n || "action" in n -> CategoryBadge("💥", Color(0xFFFF8A5B))
        "comedia" in n || "comédia" in n || "comedy" in n -> CategoryBadge("😄", Color(0xFFFFD166))
        "romance" in n || "romant" in n -> CategoryBadge("❤", Color(0xFFFF7BA9))
        "doc" in n -> CategoryBadge("🌍", Color(0xFF6FD3C7))
        "novela" in n -> CategoryBadge("📺", Color(0xFFC792EA))
        "religi" in n || "gospel" in n -> CategoryBadge("🕊", Color(0xFFE7E2D8))
        "music" in n || "músic" in n || "show" in n -> CategoryBadge("🎸", Color(0xFFB388FF))
        "news" in n || "noticia" in n || "notícia" in n || "jornal" in n ->
            CategoryBadge("📰", Color(0xFF9FB4C7))
        "guerra" in n || "war" in n -> CategoryBadge("🎖", Color(0xFF9C8F6E))
        "fic" in n || "sci" in n || "espac" in n -> CategoryBadge("🚀", Color(0xFF7BA7FF))
        "aventura" in n || "adventure" in n -> CategoryBadge("🧭", Color(0xFF62C99A))
        "suspense" in n || "thriller" in n || "crime" in n -> CategoryBadge("🕵", Color(0xFFA8A29A))
        "lanca" in n || "lança" in n || "novo" in n || "new" in n ->
            CategoryBadge("✨", Color(0xFFF0C877))
        "cinema" in n || "filme" in n || "movie" in n -> CategoryBadge("🎬", Color(0xFFD6A956))
        // Language variants are extremely common as standalone categories.
        "leg" in n -> CategoryBadge("💬", Color(0xFFB8B4AC))
        "dub" in n -> CategoryBadge("🎙", Color(0xFFB8B4AC))
        contentType == XtreamContentType.LIVE -> CategoryBadge("📡", Color(0xFF4ED59B))
        contentType == XtreamContentType.SERIES -> CategoryBadge("📺", Color(0xFFC792EA))
        else -> CategoryBadge("🎬", Color(0xFFD6A956))
    }
}
