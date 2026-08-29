package com.lucasserafin94.iptvburo.desktop.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.lucasserafin94.iptvburo.desktop.ui.BuroColors
import com.lucasserafin94.iptvburo.desktop.ui.BuroInteractiveRow
import com.lucasserafin94.iptvburo.desktop.ui.BuroRadius
import com.lucasserafin94.iptvburo.desktop.ui.BuroRemoteArtwork
import com.lucasserafin94.iptvburo.desktop.ui.BuroSpacing
import com.lucasserafin94.iptvburo.desktop.ui.DesktopStrings
import com.lucasserafin94.iptvburo.desktop.ui.editorialTitle
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewSurface
import com.lucasserafin94.iptvburo.desktop.playback.MultiviewTile
import com.lucasserafin94.iptvburo.domain.model.EpgEntry
import com.lucasserafin94.iptvburo.domain.model.LiveGuide
import com.lucasserafin94.iptvburo.xtream.XtreamCatalogItem
import kotlinx.coroutines.delay

/**
 * The live guide: channels on one side, what is on them on the other.
 *
 * The catalogue answers "what channels are there". This answers "what is on", which is the question
 * somebody actually has when they sit down — and it is why every satellite box and every IPTV app
 * has this screen. A grid of station logos is not a substitute.
 *
 * The focused channel's artwork sits beside its schedule with a Watch button under it. The picture
 * is deliberately still: a preview that started a second stream every time the focus moved would
 * open and abandon a connection per row, and providers commonly cap how many a subscription may
 * hold at once — a viewer moving down a list would be locked out of their own account.
 */
@Composable
fun LiveGuideScreen(
    channels: List<XtreamCatalogItem>,
    focusedChannelId: String?,
    scheduleFor: (String) -> List<EpgEntry>?,
    isLoading: (String) -> Boolean,
    onFocusChannel: (String) -> Unit,
    onWatch: (XtreamCatalogItem) -> Unit,
    /**
     * The stream to preview, or null when the channel cannot be played.
     *
     * Built by the caller rather than here, so the preview asks for a stream the same way every
     * other screen does — and so this composable never touches a credentialed address.
     */
    previewRequestFor: (XtreamCatalogItem) -> MultiviewTile?,
    strings: DesktopStrings,
    /**
     * Seconds since the epoch, so "now" can be tested rather than read from the clock.
     *
     * Ticks once a minute below, which is what moves the progress bar and rolls the schedule on
     * when a programme ends.
     */
    nowEpochSeconds: Long,
) {
    val text = strings
    val labels = text.shareStrings.screens
    val focused = channels.firstOrNull { it.providerId == focusedChannelId } ?: channels.firstOrNull()

    // The first channel is focused on arrival rather than leaving the schedule column empty until
    // something is clicked — an empty half-screen reads as a guide that failed to load.
    LaunchedEffect(channels.firstOrNull()?.providerId) {
        val first = focusedChannelId ?: channels.firstOrNull()?.providerId
        if (first != null) onFocusChannel(first)
    }

    val keyboard = remember { FocusRequester() }
    // The list takes the keyboard as soon as the guide opens, so the arrows work without a click
    // first — on a guide the arrows are how somebody moves, and asking for a click to enable them
    // is a step nobody expects.
    LaunchedEffect(Unit) { runCatching { keyboard.requestFocus() } }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(BuroSpacing.Md)
                .focusRequester(keyboard)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val index = channels.indexOfFirst { it.providerId == focused?.providerId }
                    if (index < 0) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            channels.getOrNull(index + 1)?.let { onFocusChannel(it.providerId) }
                            true
                        }
                        Key.DirectionUp -> {
                            channels.getOrNull(index - 1)?.let { onFocusChannel(it.providerId) }
                            true
                        }
                        // A page at a time, because four hundred channels is a long way with one
                        // arrow and the guide is the screen somebody scans rather than reads.
                        Key.PageDown -> {
                            channels.getOrNull((index + 10).coerceAtMost(channels.lastIndex))
                                ?.let { onFocusChannel(it.providerId) }
                            true
                        }
                        Key.PageUp -> {
                            channels.getOrNull((index - 10).coerceAtLeast(0))
                                ?.let { onFocusChannel(it.providerId) }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            focused?.let(onWatch)
                            true
                        }
                        else -> false
                    }
                },
    ) {
        // -------------------------------------------------------------------------------------
        // The channels
        // -------------------------------------------------------------------------------------
        Column(modifier = Modifier.width(340.dp).fillMaxHeight()) {
            Text(
                text = labels.guideTitle,
                color = BuroColors.Text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(BuroSpacing.Sm))
            val listState = rememberLazyListState()
            // The list follows the focus, or the arrows move it off the bottom of the screen and
            // the viewer is driving something they cannot see.
            LaunchedEffect(focused?.providerId) {
                val index = channels.indexOfFirst { it.providerId == focused?.providerId }
                if (index >= 0) {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val first = visible.firstOrNull()?.index ?: 0
                    val last = visible.lastOrNull()?.index ?: 0
                    if (index < first || index > last) {
                        runCatching { listState.animateScrollToItem(index) }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxHeight()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Keyed on the position as well as the id.
                //
                // A provider files one stream under two categories and sends it twice, and with two
                // subscriptions merged the same number can arrive from both. Compose throws on a
                // duplicate key and closes the window — which is how the catalogue grid crashed
                // before this rule existed.
                itemsIndexed(
                    channels,
                    key = { index, channel -> "$index:${channel.providerId}" },
                ) { _, channel ->
                    val isFocused = channel.providerId == focused?.providerId
                    ChannelRow(
                        channel = channel,
                        selected = isFocused,
                        // What is on now, so the list itself answers the question rather than
                        // making the viewer land on each row to find out.
                        onNow =
                            scheduleFor(channel.providerId)
                                ?.let { entries -> LiveGuide.upcoming(entries, nowEpochSeconds).firstOrNull() },
                        loading = isLoading(channel.providerId),
                        onClick = { onFocusChannel(channel.providerId) },
                    )
                }
            }
            // Visible, like every other long surface: four hundred channels scroll, and with
            // nothing on screen saying so the rows past the fold look like they do not exist.
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                style =
                    LocalScrollbarStyle.current.copy(
                        thickness = 10.dp,
                        unhoverColor = BuroColors.BorderSoft,
                        hoverColor = BuroColors.Primary,
                    ),
            )
            }
        }

        Spacer(Modifier.width(BuroSpacing.Md))

        // -------------------------------------------------------------------------------------
        // What is on the focused channel
        // -------------------------------------------------------------------------------------
        if (focused == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(labels.epgEmpty, color = BuroColors.TextSubtle)
            }
            return@Row
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .width(460.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BuroColors.SurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    // The channel plays here once the focus settles.
                    //
                    // Settles, not lands: the focus follows the pointer and the arrow keys, so a
                    // sweep down the list would otherwise open and abandon a stream per row. Many
                    // subscriptions allow only one connection at a time, and a viewer scanning
                    // their own guide would lock themselves out of their own account.
                    val tile = previewRequestFor(focused)
                    var previewing by remember(focused.providerId) { mutableStateOf(false) }
                    LaunchedEffect(focused.providerId) {
                        previewing = false
                        delay(PREVIEW_SETTLE_MILLIS)
                        previewing = true
                    }

                    if (previewing && tile != null) {
                        GuidePreview(tile = tile, strings = strings)
                    } else {
                        BuroRemoteArtwork(
                        artworkUrl = focused.artworkUrl,
                        contentDescription = focused.name,
                        modifier = Modifier.fillMaxSize().padding(BuroSpacing.Sm),
                        contentScale = ContentScale.Fit,
                    ) {
                        // A channel with no logo shows its initials rather than an empty box, which
                        // reads as artwork that failed rather than one the provider never sent.
                        Text(
                            text = focused.name.take(2).uppercase(),
                            color = BuroColors.TextSubtle,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        }
                    }
                }
                Spacer(Modifier.width(BuroSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = focused.name.editorialTitle(),
                        color = BuroColors.Text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(BuroSpacing.Sm))
                    BuroInteractiveRow(
                        onClick = { onWatch(focused) },
                        selected = false,
                        shape = BuroRadius.Small,
                        modifier = Modifier.width(200.dp),
                        contentDescription = labels.guideWatch,
                    ) {
                        Text(
                            // The arrows say "make this bigger": the channel is already playing in
                            // the panel beside it, so this is expanding rather than starting.
                            text = "⛶  ${labels.guideWatch}",
                            color = BuroColors.Primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(BuroSpacing.Md))

            val schedule = scheduleFor(focused.providerId)
            when {
                schedule == null ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = BuroColors.Primary,
                            modifier = Modifier.width(20.dp).height(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(labels.epgLoading, color = BuroColors.TextMuted)
                    }

                else -> {
                    val upcoming = LiveGuide.upcoming(schedule, nowEpochSeconds)
                    if (upcoming.isEmpty()) {
                        Text(labels.guideNoSchedule, color = BuroColors.TextSubtle)
                    } else {
                        val scheduleState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = scheduleState,
                                modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(BuroSpacing.Xs),
                            ) {
                                itemsIndexed(upcoming) { _, program ->
                                    ProgramRow(
                                        program = program,
                                        nowEpochSeconds = nowEpochSeconds,
                                        nowLabel = labels.guideNow,
                                    )
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(scheduleState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                style =
                                    LocalScrollbarStyle.current.copy(
                                        thickness = 10.dp,
                                        unhoverColor = BuroColors.BorderSoft,
                                        hoverColor = BuroColors.Primary,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How long the focus must hold still before a stream is opened.
 *
 * The focus follows the pointer and the arrow keys, so a sweep down the list would open and abandon
 * a connection per row. Many subscriptions allow only one at a time, and a viewer scanning their own
 * guide would lock themselves out of their own account. Long enough to survive a sweep, short enough
 * that stopping on a channel feels like it starts at once.
 */
private const val PREVIEW_SETTLE_MILLIS = 900L

/**
 * One channel playing inside the guide.
 *
 * Reuses the multiview surface rather than a second embedding of VLC: an embedded heavyweight canvas
 * has to be handled exactly one way on Windows, and that way is already written and proven there —
 * see MultiviewSurface for what goes wrong otherwise.
 */
@Composable
private fun GuidePreview(
    tile: MultiviewTile,
    strings: DesktopStrings,
) {
    val surface = remember { MultiviewSurface() }
    DisposableEffect(surface) { onDispose { surface.dispose() } }
    // Read in composable scope: the effect below is not one. Same reason recorded in MultiviewOverlay.
    val previewText = strings.shareStrings.screens
    LaunchedEffect(tile.providerId) {
        surface.sync(tiles = listOf(tile), onTileClicked = {}, text = previewText)
    }
    SwingPanel(
        background = MultiviewSurface.SEAM_COLOUR,
        factory = { surface.component() },
        modifier = Modifier.fillMaxSize(),
    )
}

/** One channel in the list, with what is on it now under the name. */
@Composable
private fun ChannelRow(
    channel: XtreamCatalogItem,
    selected: Boolean,
    onNow: EpgEntry?,
    loading: Boolean,
    onClick: () -> Unit,
) {
    // Selected by a click or by the arrows, never by the pointer merely crossing the row.
    //
    // Following the pointer was tried and is worse in the hand: the mouse passes over rows on its
    // way to somewhere else, and each pass changed the channel and started a stream. The viewer
    // ends up fighting the screen to reach the one they wanted. Reported after using it.
    BuroInteractiveRow(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        shape = BuroRadius.Small,
        contentDescription = channel.name,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                // The same cleaning the catalogue does: a viewer reading a channel list does not
                // need "[FHD]" on every row to know their subscription carries HD.
                text = channel.name.editorialTitle(),
                color = if (selected) BuroColors.Primary else BuroColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Nothing rather than a placeholder while the schedule is being fetched: a line saying
            // "loading" on forty rows at once is noise, and the row is readable without it.
            if (onNow != null) {
                Text(
                    text = onNow.title,
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (loading) {
                Text(
                    text = "…",
                    color = BuroColors.TextSubtle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** One programme: when it starts, what it is, and how far through it the viewer is. */
@Composable
private fun ProgramRow(
    program: EpgEntry,
    nowEpochSeconds: Long,
    nowLabel: String,
) {
    val onNow = LiveGuide.isOnNow(program, nowEpochSeconds)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(BuroRadius.Small)
                .background(if (onNow) BuroColors.SurfaceRaised else BuroColors.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clockOf(program.startEpochSeconds),
                color = if (onNow) BuroColors.Primary else BuroColors.TextSubtle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp),
            )
            Text(
                text = program.title,
                color = BuroColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onNow) {
                Text(
                    text = nowLabel.uppercase(),
                    color = BuroColors.Primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // The bar only where the times allow it to mean something. A programme the provider sent
        // without a clock gets no bar rather than one sitting at zero, which would claim it just
        // began — see LiveGuide.progressOf.
        LiveGuide.progressOf(program, nowEpochSeconds)?.takeIf { onNow }?.let { fraction ->
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                color = BuroColors.Primary,
                trackColor = BuroColors.Border,
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        program.description?.takeIf { onNow && it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = BuroColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A start time as HH:MM in the machine's own zone, or a dash when the provider sent none.
 *
 * A dash rather than a made-up time: a guide inventing a clock is worse than one admitting the
 * provider did not say.
 */
private fun clockOf(startEpochSeconds: Long?): String {
    val seconds = startEpochSeconds ?: return "—"
    val instant = java.time.Instant.ofEpochSecond(seconds)
    val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(local.hour, local.minute)
}

/**
 * The clock the guide reads, ticking once a minute.
 *
 * A guide whose "now" was fixed at the moment the screen opened would keep a finished programme at
 * the top and a progress bar frozen where it started. Once a minute is as often as any of it
 * changes, and far cheaper than a per-frame clock.
 */
@Composable
fun rememberGuideClock(): Long {
    var seconds by remember { mutableStateOf(System.currentTimeMillis() / 1_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            seconds = System.currentTimeMillis() / 1_000L
        }
    }
    return seconds
}
