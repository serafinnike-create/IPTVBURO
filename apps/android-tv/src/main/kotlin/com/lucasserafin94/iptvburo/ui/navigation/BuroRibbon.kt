package com.lucasserafin94.iptvburo.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.AppSection
import com.lucasserafin94.iptvburo.ui.designsystem.BuroChip
import com.lucasserafin94.iptvburo.ui.designsystem.BuroSpacing
import com.lucasserafin94.iptvburo.ui.designsystem.BuroTheme

private data class RibbonDestination(
    val section: AppSection,
    @param:StringRes val labelResource: Int,
)

private val ribbonDestinations =
    listOf(
        RibbonDestination(AppSection.HOME, R.string.buro_nav_home),
        RibbonDestination(AppSection.LIVE, R.string.buro_nav_live),
        RibbonDestination(AppSection.MOVIES, R.string.buro_nav_movies),
        RibbonDestination(AppSection.SERIES, R.string.buro_nav_series),
        RibbonDestination(AppSection.DISCOVER, R.string.buro_nav_discover),
        RibbonDestination(AppSection.MY_BURO, R.string.buro_nav_my_buro),
        RibbonDestination(AppSection.SEARCH, R.string.buro_nav_search),
        RibbonDestination(AppSection.PROFILE, R.string.buro_nav_profile),
    )

/**
 * Compact, D-pad-first primary navigation for IPTV BURO.
 *
 * When [selectedSection] is null, no destination is visually selected and
 * [selectedItemFocusRequester] targets Home as the safe return destination.
 */
@Composable
fun BuroRibbon(
    selectedSection: AppSection?,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
    selectedItemFocusRequester: FocusRequester? = null,
    onItemFocused: (AppSection) -> Unit = {},
) {
    val colors = BuroTheme.colors
    val ribbonSelection =
        selectedSection?.takeIf { candidate ->
            ribbonDestinations.any { destination -> destination.section == candidate }
        }
    val focusTarget = ribbonSelection ?: AppSection.HOME

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.overlay),
    ) {
        val compact = maxWidth < 600.dp
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (compact) compactRibbonHeight else ribbonHeight)
                        .padding(start = if (compact) BuroSpacing.Sm else BuroSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!compact) {
                    BuroBrand()
                    Spacer(Modifier.width(BuroSpacing.Lg))
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding =
                        PaddingValues(
                            end = if (compact) BuroSpacing.Sm else BuroSpacing.Lg,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        items = ribbonDestinations,
                        key = { destination -> destination.section.name },
                    ) { destination ->
                        val focusRequesterModifier =
                            if (
                                destination.section == focusTarget &&
                                selectedItemFocusRequester != null
                            ) {
                                Modifier.focusRequester(selectedItemFocusRequester)
                            } else {
                                Modifier
                            }
                        BuroChip(
                            label = stringResource(destination.labelResource),
                            onClick = { onSelect(destination.section) },
                            selected = ribbonSelection == destination.section,
                            modifier =
                                focusRequesterModifier.onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        onItemFocused(destination.section)
                                    }
                                },
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle),
            )
        }
    }
}

@Composable
private fun BuroBrand() {
    val colors = BuroTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        Text(
            text = "IPTV",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "BURO",
            color = colors.brandSecondary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private val ribbonHeight = 64.dp
private val compactRibbonHeight = 58.dp
