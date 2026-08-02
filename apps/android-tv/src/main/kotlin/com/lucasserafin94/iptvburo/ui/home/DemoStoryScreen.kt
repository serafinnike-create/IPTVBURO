package com.lucasserafin94.iptvburo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.Blue
import com.lucasserafin94.iptvburo.ui.theme.Ink
import com.lucasserafin94.iptvburo.ui.theme.InkSoft
import com.lucasserafin94.iptvburo.ui.theme.Muted
import com.lucasserafin94.iptvburo.ui.theme.Surface
import com.lucasserafin94.iptvburo.ui.theme.Teal
import com.lucasserafin94.iptvburo.ui.theme.White

@Composable
fun DemoStoryScreen(
    itemId: String,
    onImportSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val section = DemoHomeCatalog.section()
    val item = section.findItem(itemId)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Ink, InkSoft, Ink),
                ),
            ),
    ) {
        val phonePortrait = maxWidth < 600.dp && maxHeight >= maxWidth
        val compact = maxWidth < 850.dp || maxHeight < 620.dp
        val horizontalPadding =
            when {
                phonePortrait -> 16.dp
                compact -> 24.dp
                else -> 48.dp
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = if (compact) 24.dp else 36.dp,
                end = horizontalPadding,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 30.dp),
        ) {
            item(key = "story:header") {
                StoryHeader(
                    title = stringResource(R.string.buro_story_header),
                    onBack = onBack,
                )
            }

            if (item == null || !item.isDemonstration) {
                item(key = "story:missing") {
                    MissingStory(
                        compact = compact,
                        onImportSource = onImportSource,
                    )
                }
            } else {
                item(key = item.id) {
                    if (compact) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            StoryArtwork(
                                item = item,
                                compact = true,
                            )
                            StoryCopy(
                                item = item,
                                compact = true,
                                onImportSource = onImportSource,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(38.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            StoryArtwork(
                                item = item,
                                compact = false,
                            )
                            StoryCopy(
                                item = item,
                                compact = false,
                                onImportSource = onImportSource,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryHeader(
    title: String,
    onBack: () -> Unit,
) {
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FocusSurface(
            onClick = onBack,
            modifier = Modifier
                .size(52.dp)
                .focusRequester(backRequester),
            backgroundColor = Surface,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.buro_home_back),
                    tint = Teal,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.buro_story_header_demo),
                color = Muted,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoryArtwork(
    item: HomeItem,
    compact: Boolean,
) {
    val width: Dp
    val ratio: Float
    when (item.cardFormat) {
        HomeCardFormat.POSTER -> {
            width = if (compact) 180.dp else 252.dp
            ratio = 2f / 3f
        }

        HomeCardFormat.LANDSCAPE -> {
            width = if (compact) 310.dp else 430.dp
            ratio = 16f / 9f
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        BuroStaticArtwork(
            item = item,
            modifier = Modifier
                .width(minOf(width, maxWidth))
                .aspectRatio(ratio),
        )
    }
}

@Composable
private fun StoryCopy(
    item: HomeItem,
    compact: Boolean,
    onImportSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StoryDemoBadge(item.badge)
        Spacer(Modifier.height(14.dp))
        Text(
            text = item.title,
            color = White,
            fontSize = if (compact) 34.sp else 46.sp,
            lineHeight = if (compact) 38.sp else 50.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = item.subtitle,
            color = Teal,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.metadata,
            color = Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        item.progress?.let { progress ->
            Spacer(Modifier.height(14.dp))
            BuroHomeProgress(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth(if (compact) 0.82f else 0.62f)
                    .height(6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = item.synopsis,
            color = White.copy(alpha = 0.88f),
            fontSize = 17.sp,
            lineHeight = 25.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        StorySafetyNote()
        Spacer(Modifier.height(24.dp))
        FocusSurface(
            onClick = onImportSource,
            modifier = Modifier
                .fillMaxWidth(if (compact) 1f else 0.62f)
                .height(54.dp),
            backgroundColor = Teal,
            focusedBackgroundColor = Teal,
            selectedBackgroundColor = Teal,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.buro_story_add_source),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StorySafetyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Blue.copy(alpha = 0.13f))
            .border(
                width = 1.dp,
                color = Blue.copy(alpha = 0.3f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(R.string.buro_story_no_playback),
            color = Muted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun StoryDemoBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Teal.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = Teal.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = Teal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun MissingStory(
    compact: Boolean,
    onImportSource: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Surface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(if (compact) 30.dp else 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.buro_story_missing_title),
            color = White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.buro_story_missing_body),
            color = Muted,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        FocusSurface(
            onClick = onImportSource,
            modifier = Modifier.height(52.dp),
            backgroundColor = Teal,
            focusedBackgroundColor = Teal,
            selectedBackgroundColor = Teal,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Ink,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(R.string.buro_story_add_source),
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
