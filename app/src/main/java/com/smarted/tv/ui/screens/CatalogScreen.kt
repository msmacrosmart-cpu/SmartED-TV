package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.ui.components.CategorySidebar
import com.smarted.tv.ui.components.ChannelCard
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.EdChip
import com.smarted.tv.ui.components.FilterPill
import com.smarted.tv.ui.components.PosterCard
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.ScreenTitle
import com.smarted.tv.ui.components.ShimmerBox
import com.smarted.tv.ui.state.CatalogViewModel
import com.smarted.tv.ui.state.SortMode
import com.smarted.tv.ui.theme.Ed

/**
 * Tela de catálogo (Canais / Filmes / Séries).
 *
 * Layout idêntico à referência:
 *   [categorias] [filtros + busca] [grade de pôsteres]
 * Em Canais, a grade vira uma lista numerada com logo + programa atual.
 */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    kind: MediaKind,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    onOpen: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    firstFocusRequester: FocusRequester,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = Ed.palette
    val categoryState = rememberLazyListState()

    LaunchedEffect(kind) { viewModel.setKind(kind) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(top = 18.dp),
    ) {
        // ---- coluna de categorias -------------------------------------------
        CategorySidebar(
            categories = state.categories,
            selected = state.selectedCategory,
            onSelect = {
                viewModel.selectCategory(it)
                categoryState.animateScrollToItem(0)
            },
            modifier = Modifier.fillMaxHeight(),
            title = if (kind == MediaKind.LIVE) "CATEGORIAS" else "CATEGORIAS",
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 14.dp, end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(
                    title = title,
                    subtitle = "${state.total} $subtitle",
                    modifier = Modifier.weight(1f),
                )
            }

            if (kind != MediaKind.LIVE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterPill(
                        label = state.sort.label,
                        onClick = {
                            val modes = SortMode.entries
                            viewModel.setSort(modes[(modes.indexOf(state.sort) + 1) % modes.size])
                        },
                        active = state.sort != SortMode.DEFAULT,
                    )
                    FilterPill(
                        label = "Ocultar assistidos",
                        onClick = { viewModel.toggleHideWatched() },
                        active = state.hideWatched,
                    )
                    SearchField(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = searchPlaceholder,
                        modifier = Modifier.width(360.dp),
                    )
                }
            } else {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = searchPlaceholder,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }

            when {
                state.loading && state.items.isEmpty() -> GridSkeleton()
                state.items.isEmpty() -> EmptyState(
                    title = "Nada por aqui ainda",
                    message = "Nenhum item nesta categoria. Sincronize o Brazuca Play em Ajustes " +
                        "ou escolha outra categoria.",
                )
                kind == MediaKind.LIVE -> LiveList(
                    items = state.items,
                    epgNow = state.epgNow,
                    epgProgress = state.epgProgress,
                    firstFocusRequester = firstFocusRequester,
                    onOpen = onPlay,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpen(item) },
                            modifier = Modifier.fillMaxWidth(),
                            width = null,
                        )
                    }
                }
            }
        }
    }
}

/** Lista de canais (logotipo + nome estilo guia, como na referência). */
@Composable
private fun LiveList(
    items: List<MediaItem>,
    epgNow: Map<String, String>,
    epgProgress: Map<String, Float>,
    firstFocusRequester: FocusRequester,
    onOpen: (MediaItem) -> Unit,
) {
    val palette = Ed.palette
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, channel ->
            ChannelListRow(
                channel = channel,
                index = index + 1,
                nowPlaying = epgNow[channel.id],
                progress = epgProgress[channel.id],
                focusRequester = if (index == 0) firstFocusRequester else null,
                onClick = { onOpen(channel) },
            )
        }
    }
}

@Composable
private fun ChannelListRow(
    channel: MediaItem,
    index: Int,
    nowPlaying: String?,
    progress: Float?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val palette = Ed.palette
    val type = Ed.type
    val interaction = com.smarted.tv.ui.components.edInteraction(
        onClick = onClick,
        focusRequester = focusRequester,
    )
    val faded = if (interaction.isFocused) palette.surface3 else palette.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(faded, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .then(interaction.modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.Text(
            text = "%02d".format(index),
            style = type.caption,
            color = palette.fg3,
            modifier = Modifier.width(26.dp),
        )
        Box(
            Modifier
                .width(84.dp)
                .height(46.dp)
                .background(palette.surface2, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            com.smarted.tv.ui.components.Artwork(
                url = channel.poster,
                title = channel.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            androidx.compose.material3.Text(
                text = channel.title,
                style = type.rowTitle,
                color = palette.fg,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            com.smarted.tv.ui.components.ThinProgress(fraction = progress ?: 0f)
        }
        androidx.compose.material3.Text(
            text = nowPlaying ?: channel.group ?: "Ao vivo",
            style = type.caption,
            color = if (nowPlaying != null) palette.fg2 else palette.fg3,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(230.dp),
        )
    }
}

@Composable
private fun GridSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { ShimmerBox(Modifier.weight(1f).height(250.dp)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { ShimmerBox(Modifier.weight(1f).height(250.dp)) }
        }
    }
}

/** Grade usada na tela de canais quando se prefere o formato de cards. */
@Composable
fun ChannelsGrid(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(190.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items, key = { it.id }) { channel ->
            ChannelCard(item = channel, onClick = { onOpen(channel) })
        }
    }
}
