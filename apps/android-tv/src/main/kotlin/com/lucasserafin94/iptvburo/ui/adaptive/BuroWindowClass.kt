package com.lucasserafin94.iptvburo.ui.adaptive

/**
 * Small, deterministic window classification shared by the Compose screens.
 *
 * It deliberately uses the available window bounds rather than device type, so
 * rotation, split screen, foldables and Android TV previews all reflow safely.
 */
enum class BuroWindowClass {
    CompactPortrait,
    CompactLandscape,
    Expanded,
}

fun resolveBuroWindowClass(
    widthDp: Float,
    heightDp: Float,
): BuroWindowClass =
    when {
        widthDp < COMPACT_WIDTH_DP && heightDp >= widthDp ->
            BuroWindowClass.CompactPortrait

        heightDp < COMPACT_HEIGHT_DP ->
            BuroWindowClass.CompactLandscape

        else -> BuroWindowClass.Expanded
    }

private const val COMPACT_WIDTH_DP = 600f
private const val COMPACT_HEIGHT_DP = 600f
