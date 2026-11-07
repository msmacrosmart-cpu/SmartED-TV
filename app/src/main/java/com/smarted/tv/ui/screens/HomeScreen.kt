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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.WatchProgress
import com.smarted.tv.ui.components.CardSize
import com.smarted.tv.ui.components.ChannelCard
import com.smarted.tv.ui.components.EdPill
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.HeroBanner
import com.smarted.tv.ui.components.PosterCard
import com.smarted.tv.ui.components.RailSpacer
import com.smarted.tv.ui.components.SectionHeader
import com.smarted.tv.ui.components.ShimmerBox
import com.smarted.tv.ui.components.WideCard
import com.smarted.tv.ui.components.edFocusRing
import com.smarted.tv.ui.components.edInteraction
import com.smarted.tv.ui.state.HomeUiState
import com.smarted.tv.ui.state.HomeViewModel
import com.smarted.tv.ui.theme.Ed

/**
 * Home — réplica da experiência da referência:
 *  hero de destaque no topo, "Continuar assistindo", favoritos e faixas de
 *  canais/filmes/séries em rolagem horizontal com foco em D-pad.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlay: (MediaItem) -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onOpenChannel: (MediaItem) -> Unit,
    onOpenDestination: (String) -> Unit,
    heroFocusRequester: FocusRequester,
    onChannelsLoaded: (List<MediaItem>) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val type = Ed.type

    LaunchedEffect(Unit) { viewModel.load() }

    // Entrega a lista ao root para o zap do player (canal anterior/próximo).
    LaunchedEffect(state.channelRows) {
        val all = state.channelRows.flatMap { it.second }
        if (all.isNotEmpty()) onChannelsLoaded(all)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item(key = "hero") {
            Box(Modifier.padding(horizontal = 32.dp)) {
                val hero = state.hero
                if (hero != null) {
                    HeroBanner(
                        item = hero,
                        eyebrow = state.heroEyebrow,
                        onPlay = { onPlay(hero) },
                        onDetails = { onOpenDetails(hero) },
                        playFocusRequester = heroFocusRequester,
                        progress = state.heroProgress,
                        backdrops = state.heroBackdrops,
                    )
                } else {
                    ShimmerBox(Modifier.fillMaxWidth().height(300.dp))
                }
            }
        }

        if (state.loading && state.hero == null) {
            items(2) { ShimmerRow() }
        }

        // ===== Ordem de produto (de cima para baixo) =====
        // 1. Top 10 filmes mais votados
        // 2. Top 10 séries mais votadas
        // 3. Canais favoritos
        // 4. Todos os filmes
        // "Continuar assistindo" entra depois do Top 10 (é estado do usuário,
        // não curadoria) e "Ao vivo" fica como último bloco de catálogo.

        if (state.topMovies.isNotEmpty()) {
            item(key = "top-movies") {
                Rail(
                    title = stringResource(R.string.home_top_movies),
                    trailing = stringResource(R.string.home_top_movies_hint),
                    onSeeAll = { onOpenDestination("movies") },
                ) {
                    itemsIndexed(state.topMovies, key = { _, it -> "top-mv-${it.id}" }) { index, item ->
                        TopRankCard(
                            rank = index + 1,
                            item = item,
                            onClick = { onOpenDetails(item) },
                        )
                    }
                }
            }
        }

        if (state.topSeries.isNotEmpty()) {
            item(key = "top-series") {
                Rail(
                    title = stringResource(R.string.home_top_series),
                    trailing = stringResource(R.string.home_top_series_hint),
                    onSeeAll = { onOpenDestination("series") },
                ) {
                    itemsIndexed(state.topSeries, key = { _, it -> "top-sr-${it.id}" }) { index, item ->
                        TopRankCard(
                            rank = index + 1,
                            item = item,
                            onClick = { onOpenDetails(item) },
                        )
                    }
                }
            }
        }

        if (state.favoriteChannels.isNotEmpty()) {
            item(key = "fav-channels") {
                Rail(
                    title = stringResource(R.string.home_favorite_channels),
                    trailing = "${state.favoriteChannels.size} ${if (state.favoriteChannels.size == 1) "canal" else "canais"}",
                    onSeeAll = { onOpenDestination("favorites") },
                ) {
                    itemsIndexed(state.favoriteChannels, key = { i, it -> "favch-$i-${it.ref.key}" }) { index, channel ->
                        ChannelCard(
                            item = channel,
                            onClick = { onOpenChannel(channel) },
                            nowPlaying = channel.subtitle,
                            number = index + 1,
                        )
                    }
                }
            }
        }

        if (state.movies.isNotEmpty()) {
            item(key = "movies") {
                Rail(
                    title = stringResource(R.string.home_all_movies),
                    trailing = "${state.movieCount} títulos",
                    onSeeAll = { onOpenDestination("movies") },
                ) {
                    items(state.movies, key = { "mv-${it.id}" }) { item ->
                        PosterCard(item = item, onClick = { onOpenDetails(item) })
                    }
                }
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Rail(
                    title = stringResource(R.string.home_continue),
                    trailing = "${state.continueWatching.size} ${if (state.continueWatching.size == 1) "item" else "itens"}",
                    onSeeAll = { onOpenDestination("continue") },
                ) {
                    items(state.continueWatching, key = { "cw-${it.ref}" }) { progress ->
                        WideCard(
                            item = progressToItem(progress),
                            onClick = { onPlay(progressToItem(progress)) },
                            progress = progress.fraction,
                            overline = progress.subtitleText(),
                        )
                    }
                }
            }
        }

        if (state.channelRows.isNotEmpty()) {
            item(key = "channels-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = stringResource(R.string.home_live_channels),
                        trailing = "${state.liveCount} canais",
                        modifier = Modifier.weight(1f),
                    )
                    SeeAllPill { onOpenDestination("live") }
                }
            }
            itemsIndexed(state.channelRows, key = { _, row -> "ch-${row.first}" }) { _, row ->
                Rail(title = row.first, onSeeAll = { onOpenDestination("live") }) {
                    itemsIndexed(row.second, key = { i, item -> "ch-${row.first}-$i-${item.id}" }) { index, channel ->
                        ChannelCard(
                            item = channel,
                            onClick = { onOpenChannel(channel) },
                            nowPlaying = channel.subtitle,
                            number = index + 1,
                        )
                    }
                }
            }
        }

        if (state.series.isNotEmpty()) {
            item(key = "series") {
                Rail(
                    title = stringResource(R.string.home_all_series),
                    trailing = "${state.seriesCount} títulos",
                    onSeeAll = { onOpenDestination("series") },
                ) {
                    items(state.series, key = { "sr-${it.id}" }) { item ->
                        PosterCard(item = item, onClick = { onOpenDetails(item) })
                    }
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item(key = "favorites") {
                Rail(
                    title = stringResource(R.string.home_favorites),
                    trailing = "${state.favorites.size} ${if (state.favorites.size == 1) "item" else "itens"}",
                    onSeeAll = { onOpenDestination("favorites") },
                ) {
                    items(state.favorites, key = { "fav-${it.ref.key}" }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetails(item) },
                            width = CardSize.posterWidth,
                        )
                    }
                }
            }
        }

        if (!state.loading && state.hero == null) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    message = "Verifique sua conexão ou adicione fontes em Ajustes → Fontes públicas. " +
                        "Os repositórios configurados sincronizam automaticamente na abertura.",
                )
            }
        }

        item(key = "footer") {
            Column(Modifier.padding(horizontal = 32.dp)) {
                RailSpacer()
                Text(
                    text = state.status.ifBlank { stringResource(R.string.home_ready) },
                    style = type.caption,
                    color = palette.fg3,
                )
                state.error?.let {
                    Text(text = "Aviso: $it", style = type.caption, color = palette.warn)
                }
            }
        }
    }
}

/** Faixa horizontal com título e ação "ver tudo". */
@Composable
private fun Rail(
    title: String,
    trailing: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = title, trailing = trailing, modifier = Modifier.weight(1f))
            onSeeAll?.let { SeeAllPill(it) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SeeAllPill(onClick: () -> Unit) {
    EdPill(label = "Ver tudo", onClick = onClick)
}

@Composable
private fun ShimmerRow() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShimmerBox(Modifier.padding(horizontal = 32.dp).height(22.dp).fillMaxWidth(0.22f))
        Row(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(6) {
                ShimmerBox(Modifier.height(250.dp).weight(1f))
            }
        }
    }
}

/** Converte progresso persistido em item de catálogo exibível. */
private fun progressToItem(progress: WatchProgress): MediaItem = MediaItem(
    id = progress.itemId.ifBlank { progress.ref },
    addonId = progress.addonId,
    kind = progress.kind,
    title = progress.title,
    subtitle = progress.subtitleText(),
    poster = progress.poster,
    durationSec = progress.durationMs / 1000L,
    number = progress.episode,
)

private fun WatchProgress.subtitleText(): String? =
    if (season != null && episode != null) "T${season}E$episode" else null

/**
 * Cartão do "Top 10": número grande à esquerda, pôster à direita.
 * O número usa o contorno branco do design system e o preenchimento em vidro.
 */
@Composable
private fun TopRankCard(
    rank: Int,
    item: MediaItem,
    onClick: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = edInteraction(onClick = onClick)
    Row(
        modifier = Modifier
            .edFocusRing(interaction.isFocused, RoundedCornerShape(14.dp))
            .then(interaction.modifier)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = rank.toString(),
            style = type.display.copy(fontSize = 64.sp, fontWeight = FontWeight.Black),
            color = if (interaction.isFocused) palette.accent else palette.fg3,
            modifier = Modifier.padding(end = 6.dp),
        )
        PosterCard(
            item = item,
            onClick = onClick,
            width = CardSize.posterWidth,
        )
    }
}
