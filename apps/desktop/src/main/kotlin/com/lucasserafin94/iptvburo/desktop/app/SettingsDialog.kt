package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import com.lucasserafin94.iptvburo.desktop.DesktopAppState
import com.lucasserafin94.iptvburo.metadata.TmdbStreamingCatalogue
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleColour
import com.lucasserafin94.iptvburo.desktop.update.DESKTOP_VERSION
import com.lucasserafin94.iptvburo.desktop.playback.SubtitleSize
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.xtream.XtreamContentType
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.arrowScrollableList
import com.lucasserafin94.iptvburo.desktop.ui.edgeScrollableVertically
import com.lucasserafin94.iptvburo.desktop.ui.SettingsTabStrings
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
    /** Opens the step-by-step guide, for a customer who has never registered a TMDb key. */
    onOpenTmdbGuide: () -> Unit = {},
    /** Opens the page where OMDb issues its key. */
    onOpenOmdbSite: () -> Unit = {},
    /** And the guide for it, which exists mainly to explain the activation email. */
    onOpenOmdbGuide: () -> Unit = {},
) {
    val text = strings
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(SettingsTab.GENERAL) }
    val listState = rememberLazyListState()
    var categoriesExpanded by remember { mutableStateOf(false) }

    // Back to the top when the group changes. One state is reused across tabs — a keyed
    // rememberLazyListState would be recreated mid-composition and lose the scrollbar's adapter —
    // so the position is reset explicitly instead.
    LaunchedEffect(tab) { listState.scrollToItem(0) }

    // Measured when the dialog opens, not only after a fill.
    //
    // The figure comes from walking the cache directory, and the ordinary way it grows is by
    // drawing posters — which happens all the time and nowhere near this screen. Without this the
    // panel reported whatever the last fill left behind, so somebody with megabytes on disk was
    // shown "Em uso: 0 B" and had no reason to believe anything was being kept.
    LaunchedEffect(Unit) { appState.refreshCacheUsage() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // No scrim, and no indication either.
                //
                // A plain `clickable` carries Material's default ripple, and on a full-screen click
                // target that paints a grey wash over the entire app — the dimming this comment
                // claimed to have removed. It was never a scrim; it was the hover state of a
                // dismiss area the size of the window.
                //
                // The click target itself stays, so pressing outside the panel still closes it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
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
                    // The canvas colour, not Surface.
                    //
                    // Surface is nine levels lighter than the background, which is right for a card
                    // sitting on the canvas and wrong for a panel covering it: against a screen full
                    // of bright posters the lighter grey read as washed out, as though the panel
                    // were dimmed. Matching the canvas makes it read as part of the app.
                    .background(BuroColors.Canvas)
                    // The border does the separating now, and has to: with the panel the same
                    // colour as what is behind it, the edge is the only thing that says where one
                    // ends and the other begins.
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
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SettingsTabColumn(
                    selected = tab,
                    onSelect = { chosen -> tab = chosen },
                    text = text.shareStrings.settingsTabs,
                )
                Spacer(Modifier.width(BuroSpacing.Md))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    LazyColumn(
                        // Keyed on the tab so switching starts at the top of the new group rather
                        // than at whatever offset the previous one was scrolled to.
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
                    if (tab == SettingsTab.GENERAL) item(key = "language") {
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

                    if (tab == SettingsTab.GENERAL) item(key = "region") {
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
                    if (tab == SettingsTab.DATA) item(key = "metadata-key") {
                        SettingsSection(text.metadataKeyLabel, text.metadataKeyUses) {
                            Column {
                                // FlowRow, not Row: the hint is a sentence and the guide link is a
                                // second one beside it, and a Row hands the first child all the
                                // width it asks for. The hint took two lines of the panel and left
                                // the link a few pixels, which it then filled by breaking "como
                                // obter?" one letter per line down the edge of the scrollbar.
                                //
                                // Wrapping puts the link on its own line when the pair does not fit
                                // instead, which is also what the region and language pills above
                                // do with the same width.
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                                ) {
                                    Text(
                                        text = text.metadataKeyHint,
                                        color = BuroColors.Primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clickable(onClick = onOpenTmdbSettings),
                                    )
                                    // The way in for somebody who has never done this.
                                    //
                                    // The link beside it goes straight to the API settings page,
                                    // which cannot be reached without an account and cannot be
                                    // used without knowing that "Developer" is the right answer.
                                    // Anybody who does not already have a key stops there.
                                    Text(
                                        text = text.tmdbGuide.tmdbGuideButton,
                                        color = BuroColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        textDecoration = TextDecoration.Underline,
                                        // Never break this one across lines. It is two words, and
                                        // squeezed into a narrow column it is what produced the
                                        // vertical letter stack this layout was fixing.
                                        softWrap = false,
                                        modifier = Modifier.clickable(onClick = onOpenTmdbGuide),
                                    )
                                }
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

                    // The critics' scores, under the TMDb key because it is the same kind of
                    // setting: a key the user supplies that widens what the app can say about a
                    // title. Separate from TMDb's because it is a different service's account.
                    if (tab == SettingsTab.DATA) item(key = "critic-key") {
                        val ratings = text.shareStrings.ratings
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        SettingsSection(ratings.criticKeyLabel, ratings.criticKeyHint) {
                            Column {
                                // The same pair the TMDb key above offers: the site itself, and a
                                // guide for somebody who has never registered a key there.
                                //
                                // The hint named omdbapi.com and stopped, which assumes the reader
                                // knows the free tier is a radio button and — the step people
                                // actually miss — that the key arrives by email behind an activation
                                // link. Pasting the key without opening that link yields no scores
                                // and no explanation.
                                //
                                // FlowRow rather than Row: see SettingsLabelWrapUiTest. A Row hands
                                // the first label all the width and breaks the second one letter per
                                // line, which is exactly what happened to the TMDb pair.
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(BuroSpacing.Sm),
                                    verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                                ) {
                                    Text(
                                        text = "omdbapi.com",
                                        color = BuroColors.Primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        softWrap = false,
                                        modifier = Modifier.clickable(onClick = onOpenOmdbSite),
                                    )
                                    Text(
                                        text = ratings.criticGuideButton,
                                        color = BuroColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        textDecoration = TextDecoration.Underline,
                                        softWrap = false,
                                        modifier = Modifier.clickable(onClick = onOpenOmdbGuide),
                                    )
                                }
                                Spacer(Modifier.height(BuroSpacing.Xs))
                                OutlinedTextField(
                                    value = appState.criticScoresApiKey,
                                    onValueChange = appState::updateCriticScoresApiKey,
                                    singleLine = true,
                                    placeholder = {
                                        Text(ratings.criticKeyPlaceholder, color = BuroColors.TextSubtle)
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
                                    // Same reasoning as the TMDb field above: the key applies as it
                                    // is typed, so silence would read as the paste not having taken.
                                    text =
                                        if (appState.criticScoresApiKey.isNotBlank()) {
                                            ratings.criticKeySaved
                                        } else {
                                            ratings.criticKeyAbsent
                                        },
                                    color =
                                        if (appState.criticScoresApiKey.isNotBlank()) {
                                            BuroColors.Success
                                        } else {
                                            BuroColors.TextSubtle
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    if (tab == SettingsTab.GENERAL) item(key = "clock") {
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

                    // The cache belongs with the keys: both are about what the app fetches and what
                    // it keeps. It used to sit inside the subtitles item, along with the cast
                    // receiver — three unrelated settings under one key, which is a good part of
                    // why this screen was hard to read.
                    if (tab == SettingsTab.DATA) item(key = "cache") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        // The same panel the first run offers, so the explanation cannot drift
                        // apart from the setting it explains.
                        SettingsSection(
                            label = text.shareStrings.cache.title,
                            // The panel carries its own explanation, so the section adds only the
                            // heading — repeating it here would say the same thing twice.
                            hint = "",
                        ) {
                            CacheChoicePanel(
                                budget = appState.cacheBudget,
                                onChoose = appState::chooseCacheBudget,
                                estimate = null,
                                progress = appState.cacheProgress,
                                bytesUsed = appState.cacheBytesUsed,
                                onStartFill = appState::startCacheFill,
                                onPauseFill = appState::pauseCacheFill,
                                onResumeFill = appState::resumeCacheFill,
                                onCancelFill = appState::cancelCacheFill,
                                onRefresh = appState::refreshCacheFill,
                                onClear = appState::clearArtworkCache,
                                showTitle = false,
                            )
                        }
                    }

                    // Receiving a title from a phone on the same network.
                    //
                    // Off until switched on, and the code is only on screen while it is listening.
                    // This is the one feature that opens a socket, so it is something the user
                    // turns on rather than something they discover has been running — and the code
                    // is what keeps a shared network safe.
                    if (tab == SettingsTab.GENERAL) item(key = "receiver") {
                        HorizontalDivider(color = BuroColors.BorderSoft)
                        SettingsSection(
                            label = text.shareStrings.receiver.title,
                            hint = text.shareStrings.receiver.hint,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs)) {
                                // Switches rather than pills. A pill reading "Ligado — desligar"
                                // makes the reader work out whether the words are the state or the
                                // action, and it says both at once; a switch's position *is* the
                                // state, and there is nothing to decode.
                                SettingsSwitch(
                                    label = text.shareStrings.receiver.receiveNow,
                                    checked = appState.castPairingCode != null,
                                    onCheckedChange = { appState.toggleCastReceiver() },
                                )
                                // Separate from the switch above: that one closes the socket for
                                // this session, this one decides whether it opens again tomorrow.
                                SettingsSwitch(
                                    label = text.shareStrings.receiver.autoStart,
                                    checked = appState.castReceiverAutoStart,
                                    onCheckedChange = appState::changeCastReceiverAutoStart,
                                )
                                appState.castPairingCode?.let { code ->
                                    Text(
                                        text = "${text.shareStrings.receiver.codeLabel}: $code",
                                        color = BuroColors.Primary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = text.shareStrings.receiver.codeExplanation,
                                        color = BuroColors.TextSubtle,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    // The way to revoke: every phone that knew the old code has to
                                    // be told again, which is exactly what revoking means.
                                    SettingsPill(
                                        label = text.shareStrings.receiver.regenerate,
                                        selected = false,
                                        onClick = appState::regenerateCastPairingCode,
                                    )
                                }
                            }
                        }

                    }

                    if (tab == SettingsTab.SUBTITLES) item(key = "subtitles") {
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

                    // One card per film, or every copy the provider carries.
                    //
                    // Added because the grid was reported as showing duplicate films: a list holds
                    // the same title three or four times over, one per quality or dubbing, and the
                    // catalogue listed all of them. Collapsing is the default; this is for whoever
                    // wants the raw list back.
                    if (tab == SettingsTab.CONTENT) item(key = "duplicates") {
                        SettingsSection(
                            label = text.shareStrings.serviceCatalogue.duplicatesLabel,
                            hint = text.shareStrings.serviceCatalogue.duplicatesHint,
                        ) {
                            SettingsSwitch(
                                label = text.shareStrings.serviceCatalogue.duplicatesToggle,
                                checked = appState.collapsesDuplicateTitles,
                                onCheckedChange = { value ->
                                    scope.launch { appState.changeCollapsesDuplicateTitles(value) }
                                },
                            )
                        }
                    }

                    if (tab == SettingsTab.CONTENT) item(key = "parental") {
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
                    // All three sections, not only whichever the catalogue happens to be showing.
                    //
                    // Reading the open section meant a user in Filmes could not reach a series
                    // category at all: the switch for it was not on the screen, with nothing to say
                    // why. Grouped by section so a list of several hundred rows stays navigable.
                    val grouped =
                        listOf(
                            XtreamContentType.LIVE to text.live,
                            XtreamContentType.MOVIE to text.movies,
                            XtreamContentType.SERIES to text.series,
                        ).map { (type, label) -> Triple(type, label, appState.categoriesForSettings(type)) }
                            .filter { (_, _, list) -> list.isNotEmpty() }

                    val categories = grouped.flatMap { (_, _, list) -> list }
                    val hiddenByType = grouped.associate { (type, _, _) ->
                        type to appState.hiddenCategoryIdsForSettings(type)
                    }
                    val lockedByType = grouped.associate { (type, _, _) ->
                        type to appState.lockedCategoryIdsForSettings(type)
                    }
                    if (categories.isNotEmpty()) {
                        if (tab == SettingsTab.CONTENT) item(key = "categories-header") {
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
                            grouped.forEach { (type, label, list) ->
                                // A heading per section. Without one, several hundred rows from
                                // three sections read as one undifferentiated list, and a category
                                // named the same in films and series is indistinguishable.
                                item(key = "cat-head-$type") {
                                    Text(
                                        text = "$label · ${list.size}",
                                        color = BuroColors.Primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = BuroSpacing.Sm),
                                    )
                                }
                                items(list, key = { category -> "cat-$type-${category.providerId}" }) { category ->
                                    CategoryRow(
                                        name = category.name,
                                        hidden = category.providerId in hiddenByType[type].orEmpty(),
                                        locked = category.providerId in lockedByType[type].orEmpty(),
                                        canLock = appState.hasParentalPin,
                                        hideLabel = text.settingsText.categoryHide,
                                        lockLabel = text.settingsText.categoryLock,
                                        onHiddenChange = { hide ->
                                            appState.setCategoryHidden(category.providerId, hide, type)
                                        },
                                        onLockedChange = { lock ->
                                            appState.setCategoryLocked(category.providerId, lock, type)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Maintenance, last: the update button, the version and the session controls,
                    // all moved here from the dropdown menu. They are the least-used settings and
                    // the most consequential, which is exactly the order they belong in.
                    if (tab == SettingsTab.MAINTENANCE) item(key = "maintenance") {
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

/**
 * Which group of settings is on screen.
 *
 * The screen had grown to eleven sections in one scrolling column, all with the same weight, so
 * finding one meant reading past the ten that were not wanted — and each new setting made every
 * other one harder to find. Five groups of two or three, named for where somebody would look.
 */
private enum class SettingsTab {
    GENERAL,
    CONTENT,
    SUBTITLES,
    DATA,
    MAINTENANCE,
}

/**
 * The groups, down the left.
 *
 * A column rather than a row of tabs across the top: the names are words rather than icons, five of
 * them do not fit across a 520 dp panel without truncating, and a vertical list leaves the settings
 * themselves the full height of the dialog.
 */
@Composable
private fun SettingsTabColumn(
    selected: SettingsTab,
    onSelect: (SettingsTab) -> Unit,
    text: SettingsTabStrings,
) {
    val labels =
        listOf(
            SettingsTab.GENERAL to text.general,
            SettingsTab.CONTENT to text.content,
            SettingsTab.SUBTITLES to text.subtitles,
            SettingsTab.DATA to text.data,
            SettingsTab.MAINTENANCE to text.maintenance,
        )

    Column(
        modifier = Modifier.width(TAB_COLUMN_WIDTH).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
    ) {
        labels.forEach { (value, label) ->
            val active = value == selected
            BuroInteractiveRow(
                onClick = { onSelect(value) },
                selected = active,
                shape = BuroRadius.Small,
                contentDescription = label,
                modifier = Modifier.fillMaxWidth(),
            ) { state ->
                Text(
                    text = label,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BuroSpacing.Sm, vertical = BuroSpacing.Sm),
                    color =
                        when {
                            active -> BuroColors.Primary
                            state.active -> BuroColors.Text
                            else -> BuroColors.TextMuted
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Sized for the longest label in any of the five languages.
 *
 * That is Spanish "Mantenimiento" at thirteen characters, followed by Italian "Manutenzione" and
 * "Sottotitoli". Measured against the widest rather than the Portuguese the app is usually read in,
 * because a truncated navigation label is how somebody fails to find a whole group of settings.
 */
private val TAB_COLUMN_WIDTH = 152.dp

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
        // A pill is a single value — a language, a region, a size — and it is never right to break
        // one across lines. Squeezed into whatever width was left over, "16 GB" wrapped into "1 / 6
        // / G / B" down the edge of the panel, which is how this rule got written.
        //
        // Enforced here rather than at each of the dozen call sites, so a pill added later cannot
        // reintroduce it.
        softWrap = false,
        maxLines = 1,
    )
}

/**
 * A setting that is simply on or off.
 *
 * A switch rather than a pill, because a pill has to *say* which state it is in and ends up saying
 * both — "Ligado — desligar" leaves the reader working out whether the words describe the state or
 * the button. A switch's position is the state, and the label only has to name the thing.
 */
@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // The whole row toggles, not just the switch: a 40dp target at the far edge of a
                // dialog is a poor thing to ask anyone to hit.
                .clip(BuroRadius.Small)
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (checked) BuroColors.Text else BuroColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = BuroColors.OnPrimary,
                    checkedTrackColor = BuroColors.Primary,
                ),
        )
    }
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
        DesktopLanguage.SPANISH -> "Español"
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
