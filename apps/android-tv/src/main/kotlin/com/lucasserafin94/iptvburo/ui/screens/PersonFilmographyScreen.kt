package com.lucasserafin94.iptvburo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.PersonCreditUi
import com.lucasserafin94.iptvburo.ui.PersonDetailsUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

/**
 * Android counterpart of the Windows person page.
 *
 * The provider's local matches stay actionable, while the optional TMDb key adds the person's
 * portrait, biography and broader credits. A missing key never makes the cast name a dead end.
 */
@Composable
internal fun PersonFilmographyScreen(
    personName: String,
    movies: List<ChannelUi>,
    details: PersonDetailsUi?,
    onOpenMovie: (ChannelUi) -> Unit,
    /** Opens a filmography entry on the "where to watch" page. */
    onOpenCredit: (PersonCreditUi) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BuroCanvas).padding(horizontal = 22.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("person-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusSurface(
                    onClick = onBack,
                    modifier = Modifier.size(52.dp),
                    backgroundColor = BuroSurface,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.common_back),
                            tint = BuroTextPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                PersonPortrait(name = details?.name ?: personName, url = details?.photoUrl)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        details?.name ?: personName,
                        color = BuroTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val facts = listOfNotNull(details?.birthday, details?.placeOfBirth)
                    if (facts.isNotEmpty()) {
                        Text(
                            facts.joinToString(" • "),
                            color = BuroTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (details?.isLoading == true) {
                        Text(
                            stringResource(R.string.person_loading),
                            color = BuroAccent,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        details?.biography?.takeIf(String::isNotBlank)?.let { biography ->
            item("person-biography") {
                Text(
                    biography,
                    color = BuroTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }

        if (!details.isMetadataAvailable()) {
            item("person-metadata-hint") {
                Text(
                    stringResource(R.string.person_metadata_hint),
                    color = BuroTextSecondary,
                    fontSize = 14.sp,
                )
            }
        }

        if (movies.isNotEmpty()) {
            item("person-local-title") { SectionTitle(stringResource(R.string.person_in_your_library)) }
            items(movies, key = { movie -> "local:${movie.id}" }) { movie ->
                LocalMovieRow(movie = movie, onOpen = { onOpenMovie(movie) })
            }
        }

        val credits = details?.credits.orEmpty()
        if (credits.isNotEmpty()) {
            item("person-credits-title") { SectionTitle(stringResource(R.string.person_filmography)) }
            items(
                items = credits,
                key = { credit -> "credit:${credit.title}:${credit.year}:${credit.character}" },
            ) { credit ->
                CreditRow(
                    credit = credit,
                    onOpen = credit.externalId?.let { { onOpenCredit(credit) } },
                )
            }
        } else if (details?.isLoading != true && movies.isEmpty()) {
            item("person-empty") {
                Text(
                    stringResource(R.string.person_no_filmography),
                    color = BuroTextSecondary,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

private fun PersonDetailsUi?.isMetadataAvailable(): Boolean =
    this?.metadataConfigured == true || this?.isLoading == true

@Composable
private fun PersonPortrait(name: String, url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(82.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier.size(82.dp).clip(CircleShape).background(BuroAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                color = BuroAccent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = BuroTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun LocalMovieRow(movie: ChannelUi, onOpen: () -> Unit) {
    FocusSurface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().height(108.dp),
        backgroundColor = BuroSurface,
    ) {
        MediaRow(
            title = movie.name,
            subtitle = listOfNotNull(movie.year?.toString(), movie.rating?.let { "★ ${"%.1f".format(it)}" }).joinToString(" • "),
            artworkUrl = movie.logoUrl,
        )
    }
}

@Composable
private fun CreditRow(
    credit: PersonCreditUi,
    onOpen: (() -> Unit)?,
) {
    // Openable only when TMDb gave an id to look up. The rows were previously inert: tapping a
    // film in an actor's filmography did nothing at all, with no sign that it was not a button.
    if (onOpen == null) {
        Box(
            modifier = Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(14.dp)).background(BuroSurface),
        ) {
            MediaRow(
                title = credit.title,
                subtitle = listOfNotNull(credit.year?.toString(), credit.character).joinToString(" • "),
                artworkUrl = credit.posterUrl,
            )
        }
        return
    }
    FocusSurface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().height(108.dp),
        backgroundColor = BuroSurface,
    ) {
        MediaRow(
            title = credit.title,
            subtitle = listOfNotNull(credit.year?.toString(), credit.character).joinToString(" • "),
            artworkUrl = credit.posterUrl,
        )
    }
}

@Composable
private fun MediaRow(title: String, subtitle: String, artworkUrl: String?) {
    Row(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier.width(58.dp).height(88.dp).clip(RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.width(58.dp).height(88.dp).clip(RoundedCornerShape(9.dp))
                    .background(BuroAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.take(1), color = BuroAccent, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = BuroTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = BuroTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
