package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.ui.components.Artwork
import com.smarted.tv.ui.components.CastCard
import com.smarted.tv.ui.components.DividerLine
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.EdSecondaryButton
import com.smarted.tv.ui.components.PrimaryActionButton
import com.smarted.tv.ui.components.RatingPill
import com.smarted.tv.ui.components.QualityBadge
import com.smarted.tv.ui.components.SectionHeader
import com.smarted.tv.ui.components.ShimmerBox
import com.smarted.tv.ui.components.ThinProgress
import com.smarted.tv.ui.components.WideCard
import com.smarted.tv.ui.components.edBorder
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.DetailsViewModel
import com.smarted.tv.ui.theme.Ed
import androidx.compose.runtime.remember

/**
 * Ficha do item: arte de fundo, logo/título, nota (TMDB), elenco em faixa
 * horizontal contínua, trailer no YouTube (sem anúncios) e temporadas.
 */
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    item: MediaItem,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(item.ref.key) { viewModel.load(item) }
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
    }

    val current = state.item ?: item
    val details = current.details
    val playInteraction = edInteraction(onClick = { viewModel.play() }, focusRequester = playFocus)

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        // fundo com a arte e degradê
        Artwork(
            url = current.backdrop ?: current.poster,
            title = current.title,
            modifier = Modifier.fillMaxWidth().height(520.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.verticalGradient(
                        0f to palette.bg.copy(alpha = 0.55f),
                        0.55f to palette.bg.copy(alpha = 0.92f),
                        1f to palette.bg,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 170.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("header") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        Modifier
                            .width(190.dp)
                            .height(282.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surface2),
                    ) {
                        Artwork(current.poster, current.title, Modifier.fillMaxSize())
                    }
                    Column(
                        Modifier.padding(start = 26.dp).weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (details?.isSeries == true) "SÉRIE" else "FILME",
                            style = type.eyebrow,
                            color = palette.accent,
                        )
                        Text(
                            text = current.title,
                            style = type.display.copy(fontSize = type.display.fontSize * 1.16f),
                            color = palette.fg,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        details?.tagline?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = "\u201C$it\u201D",
                                style = type.bodySmall,
                                color = palette.fg3,
                                maxLines = 2,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            (details?.rating ?: current.rating)?.takeIf { it > 0 }?.let { RatingPill(it) }
                            current.year?.let {
                                Text(it.toString(), style = type.bodySmall, color = palette.fg2)
                            }
                            TextUtils.formatMinutes(details?.runtimeMin).takeIf { it.isNotBlank() }?.let {
                                Text(it, style = type.bodySmall, color = palette.fg2)
                            }
                            current.quality?.let { QualityBadge(it) }
                            details?.status?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = type.caption, color = palette.fg3)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryActionButton(
                                label = if (state.progress != null) "Continuar" else "Assistir",
                                icon = EdIcons.play,
                                interaction = playInteraction,
                            )
                            if (!details?.trailerYtId.isNullOrBlank() || !current.trailer.isNullOrBlank()) {
                                val trailerInteraction = edInteraction(onClick = { viewModel.playTrailer() })
                                TrailerButton(interaction = trailerInteraction)
                            }
                            val favInteraction = edInteraction(onClick = { viewModel.toggleFavorite() })
                            FavoriteButton(fav = state.isFavorite, interaction = favInteraction)
                        }
                        if (state.progress != null && state.progress!!.fraction > 0.02f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(0.6f),
                            ) {
                                ThinProgress(state.progress!!.fraction, Modifier.weight(1f))
                                Text(
                                    text = "  ${TextUtils.formatClock(state.progress!!.positionMs)}",
                                    style = type.caption,
                                    color = palette.fg3,
                                )
                            }
                        }
                    }
                }
            }

            details?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                item("genres") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.take(6).forEach { genre ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(palette.surface)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(genre, style = type.caption, color = palette.fg2)
                            }
                        }
                    }
                }
            }

            val plot = TextUtils.stripHtml(details?.plot ?: current.plot)
            if (plot.isNotBlank()) {
                item("plot") {
                    Text(
                        text = plot,
                        style = type.body,
                        color = palette.fg2,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            details?.cast?.takeIf { it.isNotEmpty() }?.let { cast ->
                item("cast-title") {
                    SectionHeader("Elenco", trailing = "${cast.size} pessoas")
                }
                item("cast") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(cast, key = { it.name }) { member ->
                            CastCard(name = member.name, character = member.character, photo = member.photo)
                        }
                    }
                }
            }

            if (details?.isSeries == true && state.seasons.isNotEmpty()) {
                item("seasons") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.seasons.forEach { season ->
                            EdChip(
                                label = "Temporada $season",
                                selected = season == state.selectedSeason,
                                onClick = { viewModel.selectSeason(season) },
                            )
                        }
                    }
                }
                item("episodes") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.episodes, key = { it.id }) { episode ->
                            WideCard(
                                item = episode,
                                onClick = { viewModel.playEpisode(episode) },
                                overline = episode.subtitle,
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item("loading") { ShimmerBox(Modifier.fillMaxWidth(0.5f).height(20.dp)) }
            }

            (details?.director ?: details?.network)?.takeIf { it.isNotBlank() }?.let { extra ->
                item("extra") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DividerLine()
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            details?.director?.let {
                                Text("Direção: $it", style = type.caption, color = palette.fg3)
                            }
                            details?.network?.let {
                                Text("Emissora: $it", style = type.caption, color = palette.fg3)
                            }
                            details?.country?.let {
                                Text("País: $it", style = type.caption, color = palette.fg3)
                            }
                            if (extra.isBlank()) Unit
                        }
                    }
                }
            }

            item("footer") {
                EdSecondaryButton(label = "Voltar", onClick = onBack, icon = EdIcons.back)
            }
        }
    }
}

@Composable
private fun TrailerButton(interaction: com.smarted.tv.ui.components.EdInteraction) {
    val palette = Ed.palette
    val type = Ed.type
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(shape)
            .background(if (interaction.isFocused) palette.surface3 else palette.surface, shape)
            .then(
                if (interaction.isFocused) {
                    Modifier.androidxBorder(palette.accent, shape, 3.dp)
                } else {
                    Modifier.androidxBorder(palette.line, shape, 1.dp)
                },
            )
            .then(interaction.modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EdIcons.play, contentDescription = null, tint = palette.fg2, modifier = Modifier.size(16.dp))
        Text("Trailer", style = type.bodySmall.copy(fontWeight = FontWeight.Medium), color = palette.fg)
    }
}

@Composable
private fun FavoriteButton(fav: Boolean, interaction: com.smarted.tv.ui.components.EdInteraction) {
    val palette = Ed.palette
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(shape)
            .background(if (interaction.isFocused) palette.surface3 else palette.surface, shape)
            .then(
                if (interaction.isFocused) Modifier.androidxBorder(palette.accent, shape, 3.dp)
                else Modifier.androidxBorder(palette.line, shape, 1.dp),
            )
            .then(interaction.modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (fav) EdIcons.favorite else EdIcons.favoriteBorder,
            contentDescription = "Favorito",
            tint = if (fav) palette.accent else palette.fg2,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun Modifier.androidxBorder(
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    width: androidx.compose.ui.unit.Dp,
): Modifier = this.edBorder(color, shape, width)
