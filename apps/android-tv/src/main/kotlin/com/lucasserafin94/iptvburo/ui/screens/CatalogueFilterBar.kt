package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.domain.model.CatalogueFilter
import com.lucasserafin94.iptvburo.domain.model.CatalogueLayout
import com.lucasserafin94.iptvburo.domain.model.CatalogueSort
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroSurfaceRaised
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * Narrowing and arranging a catalogue — the Android counterpart of the desktop's filter row.
 *
 * A catalogue here runs to tens of thousands of entries, where scrolling is not a way to find
 * anything. The desktop has had genre, year and sort for a while; this brings the same three, plus
 * the layout picker, to the phone.
 *
 * Which genres and years exist is asked of the catalogue rather than hard-coded, so a provider's own
 * vocabulary is what the user sees.
 */
@Composable
internal fun CatalogueFilterBar(
    filter: CatalogueFilter,
    layout: CatalogueLayout,
    genres: List<String>,
    years: List<Int>,
    onFilterChange: (CatalogueFilter) -> Unit,
    onLayoutChange: (CatalogueLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            item(key = "layout") {
                LayoutPicker(selected = layout, onSelect = onLayoutChange)
            }
            item(key = "current-year") {
                val currentYear = remember { java.time.LocalDate.now().year }
                Chip(
                    label = stringResource(R.string.catalogue_filter_new_releases, currentYear),
                    selected = filter.year == currentYear,
                    onClick = {
                        onFilterChange(
                            filter.copy(year = if (filter.year == currentYear) null else currentYear),
                        )
                    },
                )
            }
            item(key = "sort") {
                SortPicker(
                    selected = filter.sort,
                    onSelect = { sort -> onFilterChange(filter.copy(sort = sort)) },
                )
            }
            if (genres.isNotEmpty()) {
                item(key = "genre") {
                    ValuePicker(
                        label = filter.genre ?: stringResource(R.string.catalogue_filter_genre),
                        active = filter.genre != null,
                        options = genres,
                        // "All" clears rather than being a value of its own, so there is exactly
                        // one way to express "no genre filter".
                        onClear = { onFilterChange(filter.copy(genre = null)) },
                        onSelect = { genre -> onFilterChange(filter.copy(genre = genre)) },
                    )
                }
            }
            if (years.isNotEmpty()) {
                item(key = "year") {
                    ValuePicker(
                        label = filter.year?.toString() ?: stringResource(R.string.catalogue_filter_year),
                        active = filter.year != null,
                        options = years.map(Int::toString),
                        onClear = { onFilterChange(filter.copy(year = null)) },
                        onSelect = { year -> onFilterChange(filter.copy(year = year.toIntOrNull())) },
                    )
                }
            }
            item(key = "rating") {
                // Fixed floors rather than every rating present in the catalogue: rating is
                // continuous, so "every distinct value" would be a picker with dozens of nearly
                // identical entries instead of the handful of thresholds a viewer actually means.
                ValuePicker(
                    label = filter.minRating?.let { "${it.toInt()}+" }
                        ?: stringResource(R.string.catalogue_filter_rating),
                    active = filter.minRating != null,
                    options = RATING_FLOORS.map { "${it.toInt()}+" },
                    onClear = { onFilterChange(filter.copy(minRating = null)) },
                    onSelect = { option ->
                        val floor = option.removeSuffix("+").toDoubleOrNull()
                        onFilterChange(filter.copy(minRating = floor))
                    },
                )
            }
            if (filter.isActive) {
                item(key = "reset") {
                    Chip(
                        label = stringResource(R.string.catalogue_filter_clear),
                        selected = false,
                        onClick = { onFilterChange(CatalogueFilter()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutPicker(
    selected: CatalogueLayout,
    onSelect: (CatalogueLayout) -> Unit,
) {
    // Cycles rather than opening a menu: there are three, they are visually obvious once applied,
    // and a menu for three icons is more taps than simply trying the next one.
    val next =
        when (selected) {
            CatalogueLayout.POSTER -> CatalogueLayout.COMPACT
            CatalogueLayout.COMPACT -> CatalogueLayout.LIST
            CatalogueLayout.LIST -> CatalogueLayout.POSTER
        }
    FocusSurface(
        onClick = { onSelect(next) },
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(38.dp).wrapContentWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector =
                    when (selected) {
                        CatalogueLayout.POSTER -> Icons.Default.GridView
                        CatalogueLayout.COMPACT -> Icons.Default.ViewModule
                        CatalogueLayout.LIST -> Icons.AutoMirrored.Filled.List
                    },
                contentDescription = stringResource(R.string.catalogue_layout),
                tint = BuroTextSecondary,
                modifier = Modifier.height(18.dp),
            )
            Text(
                text = stringResource(selected.labelResource()),
                color = BuroTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SortPicker(
    selected: CatalogueSort,
    onSelect: (CatalogueSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Chip(
            label = stringResource(selected.labelResource()),
            selected = selected != CatalogueSort.PROVIDER,
            onClick = { expanded = true },
        )
        // Material's default menu surface is near-white, which arrived as a bright sheet over a
        // dark app. The palette is the app's own everywhere else and has to be here too.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroSurfaceRaised),
        ) {
            CatalogueSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelResource()), color = BuroTextPrimary) },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ValuePicker(
    label: String,
    active: Boolean,
    options: List<String>,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Chip(label = label, selected = active, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(BuroSurfaceRaised),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalogue_filter_all), color = BuroTextPrimary) },
                onClick = {
                    onClear()
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = BuroTextPrimary) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        shape = RoundedCornerShape(50),
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(38.dp).wrapContentWidth(),
    ) {
        Text(
            text = if (selected) "$label  ×" else "$label  ▾",
            color = if (selected) BuroAccent else BuroTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

private fun CatalogueLayout.labelResource(): Int =
    when (this) {
        CatalogueLayout.POSTER -> R.string.catalogue_layout_poster
        CatalogueLayout.COMPACT -> R.string.catalogue_layout_compact
        CatalogueLayout.LIST -> R.string.catalogue_layout_list
    }

/** Thresholds offered by the rating filter, highest first — the ratings a viewer actually asks for. */
private val RATING_FLOORS = listOf(9.0, 8.0, 7.0, 6.0, 5.0)

private fun CatalogueSort.labelResource(): Int =
    when (this) {
        CatalogueSort.PROVIDER -> R.string.catalogue_sort_provider
        CatalogueSort.TITLE_ASC -> R.string.catalogue_sort_title_asc
        CatalogueSort.TITLE_DESC -> R.string.catalogue_sort_title_desc
        CatalogueSort.YEAR_DESC -> R.string.catalogue_sort_year_desc
        CatalogueSort.YEAR_ASC -> R.string.catalogue_sort_year_asc
        CatalogueSort.RATING_DESC -> R.string.catalogue_sort_rating_desc
    }
