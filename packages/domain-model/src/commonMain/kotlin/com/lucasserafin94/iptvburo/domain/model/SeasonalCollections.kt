package com.lucasserafin94.iptvburo.domain.model

import kotlinx.datetime.LocalDate

/**
 * A themed shelf the home screen offers while the calendar is inside its window.
 *
 * The titles are carried as data rather than as UI strings because the set of collections is
 * expected to grow with the calendar, and a new entry should not force a new field on every
 * language block of the string table. [searchTerms] are matched against catalogue titles by the
 * caller, so nothing here reaches the network or the disk.
 */
data class SeasonalCollection(
    val id: String,
    val titles: Map<String, String>,
    val searchTerms: List<String>,
) {
    /**
     * The shelf name for [languageTag], falling back to English.
     *
     * A missing translation must still produce a usable shelf: an untranslated row is a smaller
     * failure than a row headed by a raw identifier.
     */
    fun title(languageTag: String): String =
        titles[languageTag] ?: titles[FALLBACK_LANGUAGE_TAG] ?: id

    companion object {
        const val FALLBACK_LANGUAGE_TAG: String = "en"
    }
}

/**
 * Maps a date to the seasonal shelves that belong on the home screen that day.
 *
 * Pure by design: the windows and their search terms are the only thing worth testing here, and
 * keeping them free of Compose and of the catalogue means every boundary can be asserted directly.
 */
object SeasonalCollections {
    /**
     * Terms are deliberately mixed Portuguese and English.
     *
     * One provider playlist routinely carries both languages side by side — "O Grinch" next to
     * "Christmas Vacation" — so a single-language term list finds roughly half of what is there.
     *
     * Terms are also kept long enough to be safe as substrings: the catalogue match is a plain
     * case-insensitive `contains`, so "natal" alone would drag in "Natalie" and "fatal". Where a
     * short word is unavoidable it is written with the surrounding space it needs.
     */
    private val christmas =
        SeasonalCollection(
            id = "christmas",
            titles =
                mapOf(
                    "pt-BR" to "Especial de Natal",
                    "en" to "Christmas Special",
                    "de" to "Weihnachtsspecial",
                    "it" to "Speciale Natale",
                ),
            searchTerms =
                listOf(
                    // "natal" is deliberately absent, and this is the rule the comment above
                    // promises. A plain `contains` on it matches "Natalie" and "fatal", and both
                    // are ordinary catalogue titles — the shelf then opens December with a film
                    // that has nothing to do with the season. "natalin" covers what the word is
                    // actually for: natalino, natalina, natalinas.
                    "natalin",
                    "christmas",
                    "xmas",
                    "papai noel",
                    "santa claus",
                    "weihnacht",
                    "noel",
                    "renas",
                    "reindeer",
                    "grinch",
                    "presepe",
                ),
        )

    private val halloween =
        SeasonalCollection(
            id = "halloween",
            titles =
                mapOf(
                    "pt-BR" to "Noites de Halloween",
                    "en" to "Halloween Nights",
                    "de" to "Halloween-Nächte",
                    "it" to "Notti di Halloween",
                ),
            searchTerms =
                listOf(
                    "halloween",
                    "terror",
                    "horror",
                    "assombrada",
                    "assombrado",
                    "haunted",
                    "zumbi",
                    "zombie",
                    "vampiro",
                    "vampire",
                    "bruxa",
                    "witch",
                    "poltergeist",
                    "exorcis",
                ),
        )

    private val valentines =
        SeasonalCollection(
            id = "valentines",
            titles =
                mapOf(
                    "pt-BR" to "Para assistir a dois",
                    "en" to "Made for Two",
                    "de" to "Zu zweit sehen",
                    "it" to "Da guardare in due",
                ),
            searchTerms =
                listOf(
                    "romance",
                    "romantic",
                    "romantico",
                    "amor",
                    "love",
                    "paixao",
                    "paixão",
                    "namorad",
                    "valentine",
                    "casament",
                    "wedding",
                    "coracao",
                    "coração",
                ),
        )

    private val newYear =
        SeasonalCollection(
            id = "new-year",
            titles =
                mapOf(
                    "pt-BR" to "Virada de ano",
                    "en" to "Ring in the Year",
                    "de" to "Jahreswechsel",
                    "it" to "Capodanno",
                ),
            searchTerms =
                listOf(
                    "ano novo",
                    "new year",
                    "reveillon",
                    "réveillon",
                    "silvester",
                    "capodanno",
                    "contagem regressiva",
                    "countdown",
                    "meia noite",
                    "midnight",
                ),
        )

    private val schoolHolidays =
        SeasonalCollection(
            id = "school-holidays",
            titles =
                mapOf(
                    "pt-BR" to "Férias em família",
                    "en" to "Family Holidays",
                    "de" to "Ferien mit der Familie",
                    "it" to "Vacanze in famiglia",
                ),
            searchTerms =
                listOf(
                    "familia",
                    "família",
                    "family",
                    "animacao",
                    "animação",
                    "animation",
                    "infantil",
                    "kids",
                    "aventura",
                    "adventure",
                    "ferias",
                    "férias",
                    "desenho",
                    "cartoon",
                ),
        )

    /**
     * The calendar table. Declaration order is the priority order used by [primaryCollectionFor],
     * so the more specific occasion is listed before any broader one it overlaps.
     *
     * Windows are stored as month/day pairs rather than absolute dates so the table survives the
     * turn of the year without maintenance. February 29 never appears as a boundary for the same
     * reason: a window that only exists in leap years is a bug waiting three years to happen.
     */
    private val windows =
        listOf(
            // The whole of December: providers publish their Christmas rows early, and the run-up
            // is when people actually browse for them.
            Window(christmas, MonthDay(12, 1), MonthDay(12, 26)),
            // Wraps the year end, which is why [Window.contains] cannot be a plain range check.
            Window(newYear, MonthDay(12, 27), MonthDay(1, 6)),
            // Halloween is a single night, but the shelf earns its place across the fortnight
            // leading to it; showing it in early October would just be a horror row.
            Window(halloween, MonthDay(10, 18), MonthDay(11, 1)),
            // Brazil keeps 12 June (Dia dos Namorados) as well as 14 February, and the app ships
            // in both markets, so both dates get a window.
            Window(valentines, MonthDay(2, 7), MonthDay(2, 15)),
            Window(valentines, MonthDay(6, 5), MonthDay(6, 13)),
            // The southern-hemisphere school break: July in Brazil, and the northern summer
            // holidays overlap it closely enough that one window serves both.
            Window(schoolHolidays, MonthDay(7, 1), MonthDay(7, 31)),
        )

    /**
     * Every collection whose window contains [date], in calendar order, without duplicates.
     *
     * Returns an empty list for most of the year on purpose: an ordinary March evening should show
     * the ordinary home screen, not a shelf reaching for a reason to exist.
     */
    fun collectionsFor(date: LocalDate): List<SeasonalCollection> =
        windows
            .filter { it.contains(MonthDay(date.monthNumber, date.dayOfMonth)) }
            .map(Window::collection)
            .distinctBy(SeasonalCollection::id)

    /** The one shelf to show, or null. The home screen has room for a single seasonal row. */
    fun primaryCollectionFor(date: LocalDate): SeasonalCollection? = collectionsFor(date).firstOrNull()

    /**
     * A day in the year, with no year attached.
     *
     * `java.time.MonthDay` is not available on every target and kotlinx-datetime has no equivalent,
     * so the seasonal windows carry their own. Comparable in calendar order, which is what the
     * wrapping check below relies on: an ordinary window compares as a range, and one that crosses
     * New Year is recognised precisely because `from` then sorts *after* `to`.
     */
    internal data class MonthDay(
        val month: Int,
        val day: Int,
    ) : Comparable<MonthDay> {
        init {
            require(month in 1..12) { "month is outside 1..12" }
            // Not validated against the month's real length: 29 February has to remain expressible,
            // and no window here ends on a date that only some years have.
            require(day in 1..31) { "day is outside 1..31" }
        }

        override fun compareTo(other: MonthDay): Int =
            if (month != other.month) month.compareTo(other.month) else day.compareTo(other.day)
    }

    private data class Window(
        val collection: SeasonalCollection,
        val from: MonthDay,
        val to: MonthDay,
    ) {
        /**
         * Inclusive on both ends.
         *
         * The wrapping branch exists for Halloween, whose window ends on 1 November: comparing
         * month/day pairs directly would exclude every date once the month rolls over.
         */
        fun contains(day: MonthDay): Boolean =
            if (from <= to) {
                day >= from && day <= to
            } else {
                day >= from || day <= to
            }
    }
}
