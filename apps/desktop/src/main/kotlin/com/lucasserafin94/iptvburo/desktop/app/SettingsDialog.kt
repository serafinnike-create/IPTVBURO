package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.desktop.data.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleColour
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleSize
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableList
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.strings
import com.lucasserafin94.iptvburo.desktop.user.DesktopLanguage

/**
 * Settings as a dialog rather than a dropdown menu.
 *
 * A DropdownMenu measures its content with unbounded height and scrolls it without showing a
 * scrollbar, so as settings were added the panel simply grew past the top and bottom of the screen
 * with nothing to say there was more. Three attempts to make it scroll inside that component either
 * crashed — "measured with an infinity maximum height" — or produced no visible indicator.
 *
 * A dialog has a real height to scroll within, and a LazyColumn inside it behaves like every other
 * scrolling surface in the app: visible scrollbar, arrow keys, pointer-edge travel.
 */
@Composable
fun SettingsDialog(
    appState: DesktopAppState,
    onDismiss: () -> Unit,
    /**
     * Everything that used to live in the dropdown menu beside this dialog.
     *
     * There were two settings screens: this one, which scrolls and shows a scrollbar, and a
     * DropdownMenu holding the API key, the update button and the session controls, which scrolls
     * without any indicator at all — so a user who could not see past its fold reported the
     * settings as missing. One screen, with one scrollbar, is the fix.
     */
    updateBusy: Boolean = false,
    onUpdate: () -> Unit = {},
    sessionActive: Boolean = false,
    onEndSession: () -> Unit = {},
    catalogRefreshing: Boolean = false,
    onRefreshCatalog: () -> Unit = {},
    onOpenTmdbSettings: () -> Unit = {},
) {
    val text = strings
    val listState = rememberLazyListState()
    var categoriesExpanded by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // No scrim. Dimming the whole app behind a settings panel made the library look
                // switched off; the panel's own surface and elevation already separate it from what
                // is behind. The click target stays, so tapping outside still closes it.
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    // A floor as well as a ceiling. Sized only as a fraction, the panel came out
                    // narrow enough that the language pills stacked one per line and every hint
                    // broke across three — the settings were readable but the screen was not.
                    .widthIn(min = 520.dp, max = 720.dp)
                    .fillMaxWidth(0.55f)
                    .heightIn(max = 760.dp)
                    .clip(BuroRadius.Large)
                    .background(BuroColors.Surface)
                    // A border now that nothing dims behind it: without the scrim the panel's edge
                    // was the only thing separating it from the catalogue, and on a dark screen
                    // that edge was invisible.
                    .border(1.dp, BuroColors.Border, BuroRadius.Large)
                    // Consumes the click so pressing inside does not dismiss the dialog under it.
                    .clickable(enabled = false) {}
                    .padding(BuroSpacing.Lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = BuroSpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text.settings,
                    color = BuroColors.Text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "✕",
                    color = BuroColors.TextMuted,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(BuroSpacing.Xs),
                )
            }

            // Weighted, never fillMaxSize: an unweighted Column child is measured against unbounded
            // height, so a scrollable one lays its whole content out past the bottom and never
            // scrolls at all. This app has shipped that mistake more than once.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .arrowScrollableList(listState)
                            .edgeScrollableVertically(listState),
                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Lg),
                    // Room for the scrollbar, so it never sits on top of a pill.
                    contentPadding = PaddingValues(end = BuroSpacing.Md),
                ) {
                    item(key = "language") {
                        SettingsSection(text.languageLabel, text.languageHint) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                            ) {
                                DesktopLanguage.entries.forEach { option ->
                                    SettingsPill(
                                        label = languageDisplayName(option),
                                        selected = option == appState.language,
                                        onClick = { appState.updateLanguage(option) },
                                    )
                                }
                            }
                        }
                    }

                    item(key = "region") {
                        SettingsSection(text.subscriptionsRegion, text.regionHint) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                            ) {
                                TmdbStreamingCatalogue.SUPPORTED_REGIONS.forEach { option ->
                                    SettingsPill(
                                        label = regionDisplayName(option),
                                        selected = option == appState.streamingRegion,
                                        onClick = { appState.changeStreamingRegion(option) },
                                    )
                                }
                            }
                        }
                    }

                    // The TMDb key, moved here from the dropdown. It sits under the region because
                    // both govern the same thing: what the app can say about a title.
                    item(key = "metadata-key") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        SettingsSection(text.metadataKeyLabel, text.metadataKeyUses) {
                            Column {
                                Text(
                                    text = text.metadataKeyHint,
                                    color = BuroColors.Primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable(onClick = onOpenTmdbSettings),
                                )
                                Spacer(Modifier.height(BuroSpacing.Xs))
                                OutlinedTextField(
                                    value = appState.metadataApiKey,
                                    onValueChange = appState::updateMetadataApiKey,
                                    singleLine = true,
                                    placeholder = {
                                        Text(text.metadataKeyPlaceholder, color = BuroColors.TextSubtle)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = BuroRadius.Small,
                                    colors =
                                        TextFieldDefaults.colors(
                                            focusedTextColor = BuroColors.Text,
                                            unfocusedTextColor = BuroColors.Text,
                                            focusedContainerColor = BuroColors.Surface,
                                            unfocusedContainerColor = BuroColors.Surface,
                                            focusedIndicatorColor = BuroColors.Primary,
                                            unfocusedIndicatorColor = BuroColors.BorderSoft,
                                            cursorColor = BuroColors.Primary,
                                        ),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    // There is no Save button — the key applies as it is typed — so
                                    // without this the user is left wondering whether it took.
                                    text =
                                        if (appState.metadataApiKey.isNotBlank()) {
                                            text.metadataKeySaved
                                        } else {
                                            text.metadataKeyUsingBundled
                                        },
                                    color =
                                        if (appState.metadataApiKey.isNotBlank()) {
                                            BuroColors.Success
                                        } else {
                                            BuroColors.TextSubtle
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    item(key = "clock") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        SettingsSection(text.settingsText.clockLabel, text.settingsText.clockHint) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                SettingsPill(
                                    label = text.settingsText.clock24h,
                                    selected = appState.uses24HourClock,
                                    onClick = { appState.changeClockFormat(true) },
                                )
                                SettingsPill(
                                    label = text.settingsText.clock12h,
                                    selected = !appState.uses24HourClock,
                                    onClick = { appState.changeClockFormat(false) },
                                )
                            }
                        }
                    }

                    item(key = "subtitles") {
                        SettingsSection(text.settingsText.subtitlesLabel, text.settingsText.subtitlesHint) {
                            Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                    SubtitleSize.entries.forEach { option ->
                                        SettingsPill(
                                            label = option.label,
                                            selected = option == appState.subtitleStyle.size,
                                            onClick = {
                                                appState.changeSubtitleStyle(
                                                    appState.subtitleStyle.copy(size = option),
                                                )
                                            },
                                        )
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                    SubtitleColour.entries.forEach { option ->
                                        SettingsPill(
                                            label = option.label,
                                            selected = option == appState.subtitleStyle.textColour,
                                            onClick = {
                                                appState.changeSubtitleStyle(
                                                    appState.subtitleStyle.copy(textColour = option),
                                                )
                                            },
                                        )
                                    }
                                }
                                SettingsPill(
                                    label = text.settingsText.subtitlesBackground,
                                    selected = appState.subtitleStyle.background,
                                    onClick = {
                                        appState.changeSubtitleStyle(
                                            appState.subtitleStyle.copy(
                                                background = !appState.subtitleStyle.background,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    item(key = "parental") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        ParentalControlPanel(appState)
                    }

                    // The categories themselves, each with a switch to hide it and — once a PIN
                    // exists — one to lock it. Without this list the two features had nowhere to be
                    // used from: the filtering worked and nothing could reach it.
                    // Collapsed by default. A provider carries hundreds of categories, and listing
                    // them all inline buried every other setting under a wall of rows — the section
                    // says how many there are and opens on request.
                    // Deliberately the unfiltered list, not `xtreamCategories`: that one already has
                    // the hidden ones removed, so hiding a category also removed it from the switch
                    // that hid it, and there was then no way to bring it back.
                    val categories = appState.allCategoriesForSettings
                    if (categories.isNotEmpty()) {
                        item(key = "categories-header") {
                            HorizontalDivider(color = BuroColors.BorderSoft)
                            Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { categoriesExpanded = !categoriesExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = text.settingsText.categoriesLabel,
                                            color = BuroColors.Text,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            // The count answers "is it worth opening?" before it is.
                                            text = "${categories.size} · ${text.settingsText.categoriesHint}",
                                            color = BuroColors.TextSubtle,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Text(
                                        text = if (categoriesExpanded) "⌃" else "⌄",
                                        color = BuroColors.TextMuted,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                        if (categoriesExpanded) {
                            items(categories, key = { category -> "cat-${category.providerId}" }) { category ->
                                CategoryRow(
                                    name = category.name,
                                    hidden = category.providerId in appState.hiddenCategoryIds,
                                    locked = appState.parentalLock.lockedCategoryIds.contains(category.providerId),
                                    canLock = appState.hasParentalPin,
                                    hideLabel = text.settingsText.categoryHide,
                                    lockLabel = text.settingsText.categoryLock,
                                    onHiddenChange = { hide -> appState.setCategoryHidden(category.providerId, hide) },
                                    onLockedChange = { lock -> appState.setCategoryLocked(category.providerId, lock) },
                                )
                            }
                        }
                    }

                    // Maintenance, last: the update button, the version and the session controls,
                    // all moved here from the dropdown menu. They are the least-used settings and
                    // the most consequential, which is exactly the order they belong in.
                    item(key = "maintenance") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                            SettingsActionRow(
                                label = if (updateBusy) "…" else text.checkUpdate,
                                enabled = !updateBusy,
                                onClick = onUpdate,
                            )
                            if (sessionActive) {
                                SettingsActionRow(
                                    label = if (catalogRefreshing) "…" else text.refreshCatalog,
                                    enabled = !catalogRefreshing,
                                    onClick = onRefreshCatalog,
                                )
                                SettingsActionRow(
                                    label = text.endSession,
                                    tint = BuroColors.Error,
                                    onClick = {
                                        onEndSession()
                                        onDismiss()
                                    },
                                )
                            }
                            Text(
                                text = "IPTV BURO v$DESKTOP_VERSION",
                                color = BuroColors.TextSubtle,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = BuroSpacing.Xs),
                            )
                        }
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    style =
                        LocalScrollbarStyle.current.copy(
                            unhoverColor = BuroColors.BorderSoft,
                            hoverColor = BuroColors.Primary,
                        ),
                )
            }
        }
    }
}

/**
 * One category, with what can be done to it.
 *
 * Hiding and locking are separate switches because they are separate ideas: hiding is the user
 * tidying their own rail, locking is protecting it from someone else. Locking is offered only once
 * a PIN exists — a lock with no way to open it would shut the user out of their own catalogue.
 */
@Composable
private fun CategoryRow(
    name: String,
    hidden: Boolean,
    locked: Boolean,
    canLock: Boolean,
    hideLabel: String,
    lockLabel: String,
    onHiddenChange: (Boolean) -> Unit,
    onLockedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
    ) {
        Text(
            text = name,
            color = if (hidden) BuroColors.TextSubtle else BuroColors.Text,
            // Struck through when hidden. The list now shows hidden categories too — it has to, or
            // they could not be restored — so each row must say at a glance which state it is in.
            textDecoration = if (hidden) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SettingsPill(label = hideLabel, selected = hidden, onClick = { onHiddenChange(!hidden) })
        if (canLock) {
            SettingsPill(label = lockLabel, selected = locked, onClick = { onLockedChange(!locked) })
        }
    }
}

/**
 * One action in the settings list: a full-width row that does something when pressed.
 *
 * Distinct from [SettingsPill], which selects a value. These change state elsewhere — start an
 * update, end a session — so they read as buttons rather than as choices.
 */
@Composable
private fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = BuroColors.Text,
) {
    BuroInteractiveRow(
        onClick = { if (enabled) onClick() },
        selected = false,
        shape = BuroRadius.Small,
        contentDescription = label,
    ) { state ->
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(vertical = BuroSpacing.Xs),
            color = if (!enabled) BuroColors.TextSubtle else if (state.active) BuroColors.Primary else tint,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A titled block, with the line that says what the setting governs. */
@Composable
private fun SettingsSection(
    label: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
        Text(
            text = label,
            color = BuroColors.Text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = hint,
            color = BuroColors.TextSubtle,
            style = MaterialTheme.typography.labelSmall,
        )
        content()
    }
}

/** One selectable value, as a pill. */
@Composable
private fun SettingsPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier =
            Modifier
                .clip(BuroRadius.Pill)
                .background(if (selected) BuroColors.SurfaceHover else BuroColors.SurfaceRaised)
                .clickable(onClick = onClick)
                .padding(horizontal = BuroSpacing.Sm, vertical = 6.dp),
        color = if (selected) BuroColors.Primary else BuroColors.TextMuted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/**
 * A country's name for the region picker.
 *
 * Written out rather than taken from `Locale.getDisplayCountry`, which answers in the JVM's own
 * locale and would put German country names in front of a Portuguese-speaking user.
 */
/** Each language written in itself, so a user who cannot read the current one still finds theirs. */
private fun languageDisplayName(language: DesktopLanguage): String =
    when (language) {
        DesktopLanguage.PORTUGUESE_BRAZIL -> "Português (Brasil)"
        DesktopLanguage.ENGLISH -> "English"
        DesktopLanguage.GERMAN -> "Deutsch"
        DesktopLanguage.ITALIAN -> "Italiano"
    }

private fun regionDisplayName(code: String): String =
    when (code) {
        "BR" -> "Brasil"
        "PT" -> "Portugal"
        "US" -> "Estados Unidos"
        "DE" -> "Alemanha"
        "IT" -> "Itália"
        else -> code
    }
