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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.lucasserafin94.iptvburo.ui.ChannelUi
import com.lucasserafin94.iptvburo.ui.components.FocusSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroCanvas
import com.lucasserafin94.iptvburo.ui.theme.BuroSurface
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary

@Composable
internal fun PersonFilmographyScreen(
    personName: String,
    movies: List<ChannelUi>,
    onOpenMovie: (ChannelUi) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BuroCanvas).padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusSurface(
                onClick = onBack,
                modifier = Modifier.size(52.dp),
                backgroundColor = BuroSurface,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = BuroTextPrimary)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(personName, color = BuroTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Filmografia confirmada enquanto você navega nesta fonte",
                    color = BuroTextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (movies.isEmpty()) {
            Text(
                "A fonte informou o nome, mas não forneceu um identificador de pessoa ou uma filmografia completa.",
                color = BuroTextSecondary,
                fontSize = 17.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(movies, key = ChannelUi::id) { movie ->
                    FocusSurface(
                        onClick = { onOpenMovie(movie) },
                        modifier = Modifier.fillMaxWidth().height(116.dp),
                        backgroundColor = BuroSurface,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (movie.logoUrl != null) {
                                AsyncImage(
                                    model = movie.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.width(62.dp).height(92.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    Modifier.size(62.dp).clip(CircleShape).background(BuroAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(movie.name.take(1), color = BuroAccent, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    movie.name,
                                    color = BuroTextPrimary,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(movie.year?.toString(), movie.rating?.let { "★ ${"%.1f".format(it)}" }).joinToString(" • "),
                                    color = BuroTextSecondary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
