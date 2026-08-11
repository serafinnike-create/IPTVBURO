package com.lucasserafin94.iptvburo.desktop.ui

import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The labels the new type selectors depend on, in every language the app ships.
 *
 * Continue watching and downloads both grew a films/series selector, and both reuse the labels the
 * catalogue toolbar already had. A missing translation there would surface as an English word in the
 * middle of a Portuguese screen — the kind of fault that is invisible to whoever added the feature
 * and obvious to the customer.
 */
class TypeFilterLabelsTest {
    @Test
    fun `every language names films, series, everything, and an empty result`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)

            listOf(
                "movies" to strings.movies,
                "series" to strings.series,
                "allItems" to strings.allItems,
                "downloadsNoMatch" to strings.downloadsNoMatch,
            ).forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "$language is missing $name")
            }
        }
    }

    /**
     * The three selector labels differ from one another.
     *
     * A copy-paste that left two languages sharing one word would produce a control with two
     * identical buttons, and every test above would still pass.
     */
    @Test
    fun `the selector labels are distinguishable`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)
            val labels = setOf(strings.allItems, strings.movies, strings.series)

            assertTrue(labels.size == 3, "$language has duplicate selector labels: $labels")
        }
    }

    /**
     * They fit a segmented control.
     *
     * The three sit side by side above a list, sharing the row with a search box. A long
     * translation pushes the search field off the edge, which is how controls have gone missing on
     * this project before.
     */
    @Test
    fun `the selector labels are short enough for a pill`() {
        DesktopLanguage.entries.forEach { language ->
            val strings = DesktopStrings.of(language)

            listOf(strings.allItems, strings.movies, strings.series).forEach { label ->
                assertTrue(
                    label.length <= 12,
                    "$language label \"$label\" is too long for a segmented control",
                )
            }
        }
    }
}
