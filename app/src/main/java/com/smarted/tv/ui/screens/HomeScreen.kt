package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.R
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.WatchProgress
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.EdIcons
import com.smarted.tv.ui.components.FeatureCard
import com.smarted.tv.ui.components.GenrePill
import com.smarted.tv.ui.components.LiveChannelCard
import com.smarted.tv.ui.components.ShelfHeader
import com.smarted.tv.ui.components.ShelfMetrics
import com.smarted.tv.ui.components.ShimmerBox
import com.smarted.tv.ui.components.StageHero
import com.smarted.tv.ui.state.HomeUiState
import com.smarted.tv.ui.state.HomeViewModel
import com.smarted.tv.ui.theme.Ed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home cinematográfica.
 *
 * Estrutura (de cima para baixo):
 *
 *  1. **Palco** — backdrop full-bleed que passa por baixo da doca lateral, com
 *     título, classificação, ano, duração, gêneros, sinopse e as ações
 *     "Assistir" / "Detalhes" em branco puro.
 *  2. **Em destaque para você** — cada trilha horizontal é um mural de cartões
 *     16:10 com o título e os metadados dentro da própria arte.
 *  3. Trilhas: Top 10 em alta, Séries em alta, Continuar assistindo,
 *     Lançamentos, Séries para maratonar, Canais favoritos e Gêneros.
 *
 * O conteúdo começa em [ShelfMetrics] + doca, nunca atrás dos ícones — o palco,
 * esse sim, sangra até a borda esquerda.
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

    // Entrega a lista completa de canais ao root (zap do player).
    LaunchedEffect(state.liveChannels) {
        if (state.liveChannels.isNotEmpty()) onChannelsLoaded(state.liveChannels)
    }

    val contentStart: Dp = Ed.dimen.dockInset + 20.dp
    val refreshed = remember { formattedNow() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        val stageHeight = (maxHeight * 0.68f)
            .coerceIn(Ed.dimen.stageMin, Ed.dimen.stageMax)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---------------------------------------------------------- palco
            item(key = "stage") {
                val hero = state.hero
                if (hero != null) {
                    StageHero(
                        item = hero,
                        eyebrow = eyebrowFor(hero, state),
                        onPlay = { onPlay(hero) },
                        onDetails = { onOpenDetails(hero) },
                        modifier = Modifier.height(stageHeight),
                        playFocusRequester = heroFocusRequester,
                        progress = state.heroProgress,
                        backdrops = state.heroBackdrops,
                        insetStart = Ed.dimen.dockInset,
                        refreshedLabel = stringResource(R.string.home_refreshed, refreshed),
                    )
                } else {
                    StageSkeleton(height = stageHeight, insetStart = Ed.dimen.dockInset)
                }
            }

            // ------------------------------------------- cabeçalho da vitrine
            item(key = "featured-head") {
                ShelfHeader(
                    title = stringResource(R.string.home_featured_for_you),
                    trailing = null,
                    modifier = Modifier.padding(start = contentStart, end = 40.dp),
                    big = true,
                )
            }

            // ------------------------------------------------- trilhas
            if (state.topMovies.isNotEmpty()) {
                item(key = "top-movies") {
                    Shelf(
                        title = stringResource(R.string.home_top_trending),
                        hint = stringResource(R.string.home_top_movies_hint),
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("movies") },
                    ) {
                        itemsIndexed(state.topMovies, key = { _, it -> "top-mv-${it.id}" }) { index, item ->
                            FeatureCard(
                                item = item,
                                onClick = { onOpenDetails(item) },
                                rank = index + 1,
                            )
                        }
                    }
                }
            }

            if (state.topSeries.isNotEmpty()) {
                item(key = "top-series") {
                    Shelf(
                        title = stringResource(R.string.home_trending_series),
                        hint = stringResource(R.string.home_top_series_hint),
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("series") },
                    ) {
                        itemsIndexed(state.topSeries, key = { _, it -> "top-sr-${it.id}" }) { index, item ->
                            FeatureCard(
                                item = item,
                                onClick = { onOpenDetails(item) },
                                rank = index + 1,
                            )
                        }
                    }
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    Shelf(
                        title = stringResource(R.string.home_continue),
                        hint = "${state.continueWatching.size}",
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("continue") },
                    ) {
                        items(state.continueWatching, key = { "cw-${it.ref}" }) { progress ->
                            val item = progressToItem(progress)
                            FeatureCard(
                                item = item,
                                onClick = { onPlay(item) },
                                progress = progress.fraction,
                                overline = progress.subtitleText(),
                            )
                        }
                    }
                }
            }

            if (state.movies.isNotEmpty()) {
                item(key = "movies") {
                    Shelf(
                        title = stringResource(R.string.home_new_movies),
                        hint = "${state.movieCount}",
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("movies") },
                    ) {
                        items(state.movies, key = { "mv-${it.id}" }) { item ->
                            FeatureCard(item = item, onClick = { onOpenDetails(item) })
                        }
                    }
                }
            }

            if (state.series.isNotEmpty()) {
                item(key = "series") {
                    Shelf(
                        title = stringResource(R.string.home_more_series),
                        hint = "${state.seriesCount}",
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("series") },
                    ) {
                        items(state.series, key = { "sr-${it.id}" }) { item ->
                            FeatureCard(item = item, onClick = { onOpenDetails(item) })
                        }
                    }
                }
            }

            val favoriteTitles = state.favorites.filter { it.kind != MediaKind.LIVE }
            if (favoriteTitles.isNotEmpty()) {
                item(key = "favorites") {
                    Shelf(
                        title = stringResource(R.string.home_favorites),
                        hint = "${favoriteTitles.size}",
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("favorites") },
                    ) {
                        items(favoriteTitles, key = { "fav-${it.ref.key}" }) { item ->
                            FeatureCard(item = item, onClick = { onOpenDetails(item) })
                        }
                    }
                }
            }

            if (state.favoriteChannels.isNotEmpty()) {
                item(key = "channels") {
                    Shelf(
                        title = stringResource(R.string.home_favorite_channels),
                        hint = "${state.favoriteChannels.size}",
                        contentStart = contentStart,
                        onSeeAll = { onOpenDestination("favorites") },
                    ) {
                        items(state.favoriteChannels, key = { "ch-${it.ref.key}" }) { channel ->
                            LiveChannelCard(
                                item = channel,
                                onClick = { onOpenChannel(channel) },
                            )
                        }
                    }
                }
            }

            val genres = collectGenres(state)
            if (genres.isNotEmpty()) {
                item(key = "genres") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShelfHeader(
                            title = stringResource(R.string.home_genres),
                            modifier = Modifier.padding(start = contentStart, end = 40.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(start = contentStart, end = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(genres, key = { "genre-$it" }) { genre ->
                                GenrePill(
                                    label = genre,
                                    icon = EdIcons.grid,
                                    onClick = { onOpenDestination("movies") },
                                )
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------- estados vazios
            val hasContent = state.liveChannels.isNotEmpty() ||
                state.movies.isNotEmpty() ||
                state.series.isNotEmpty() ||
                state.favoriteChannels.isNotEmpty()
            if (!state.loading && !hasContent) {
                item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        modifier = Modifier.padding(start = contentStart),
                    )
                }
            }

            item(key = "footer") {
                val hint = stringResource(R.string.home_dpad_hint)
                Column(
                    modifier = Modifier.padding(start = contentStart, end = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.status.ifBlank { hint },
                        style = type.caption,
                        color = palette.fg3,
                    )
                    state.error?.let {
                        Text(text = it, style = type.caption, color = palette.warn)
                    }
                }
            }
        }
    }
}

/** Trilha horizontal com título pequeno e ação discreta. */
@Composable
private fun Shelf(
    title: String,
    contentStart: Dp,
    hint: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfHeader(
            title = title,
            trailing = hint,
            actionLabel = if (onSeeAll != null) stringResource(R.string.action_see_all) else null,
            onAction = onSeeAll,
            modifier = Modifier.padding(start = contentStart, end = 40.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(start = contentStart, end = 40.dp, top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(ShelfMetrics.gap),
            content = content,
        )
    }
}

/** Esqueleto do palco enquanto o catálogo chega. */
@Composable
private fun StageSkeleton(height: Dp, insetStart: Dp) {
    val palette = Ed.palette
    Box(Modifier.fillMaxSize().height(height).background(palette.bgDeep)) {
        Column(
            modifier = Modifier.padding(start = insetStart + 30.dp, bottom = 40.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
        ) {
            ShimmerBox(Modifier.height(12.dp).fillMaxSize())
        }
    }
}

/** Eyebrow do palco de acordo com o tipo do destaque. */
@Composable
private fun eyebrowFor(hero: MediaItem, state: HomeUiState): String = when (hero.kind) {
    MediaKind.MOVIE -> stringResource(R.string.home_stage_movie)
    MediaKind.SERIES -> stringResource(R.string.home_stage_series)
    MediaKind.LIVE -> stringResource(R.string.home_stage_live)
    else -> state.heroEyebrow
}

/** Gêneros mais frequentes do acervo (máximo 14, ordem alfabética). */
private fun collectGenres(state: HomeUiState): List<String> {
    val counter = LinkedHashMap<String, Int>()
    (state.movies + state.series).forEach { item ->
        item.genres.forEach { raw ->
            val genre = raw.trim()
            if (genre.isNotEmpty() && genre.length <= 22) {
                counter[genre] = (counter[genre] ?: 0) + 1
            }
        }
    }
    return counter.entries
        .sortedByDescending { it.value }
        .take(14)
        .map { it.key }
        .sorted()
}

/** Converte progresso persistido em item exibível nos cartões. */
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

/** "2:13" — usado no selo de atualização do palco. */
private fun formattedNow(): String =
    runCatching { SimpleDateFormat("H:mm", Locale.forLanguageTag("pt-BR")).format(Date()) }
        .getOrDefault("")
