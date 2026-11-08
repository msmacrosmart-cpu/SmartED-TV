package com.smarted.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smarted.tv.core.data.AppGraph
import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.asDuration
import com.smarted.tv.core.util.asRating
import com.smarted.tv.ui.common.vmFactory
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.theme.LocalDeviceMode
import com.smarted.tv.ui.theme.LocalDimens
import com.smarted.tv.ui.theme.Smoke

/**
 * Filmes e Séries (mesmo composable, VM com escopo próprio).
 *  - Largo (TV / tablet paisagem): Coluna 1 = busca de categorias + categorias + favoritos;
 *    Coluna 2 = grade infinita 2:3. O topo da coluna 2 mostra a BUSCA DE TÍTULOS e, ao
 *    selecionar um título, o campo dá lugar ao painel HERO.
 *  - Compacto (celular): chips de categoria no topo da própria grade.
 */
@Composable
fun VodScreen(kind: VodKind, openId: String?, onPlay: (PlaybackRequest) -> Unit) {
    val vm: VodViewModel = viewModel(key = kind.name, factory = vmFactory { VodViewModel(AppGraph.content, kind) })
    val ui by vm.ui.collectAsState()
    val d = LocalDimens.current
    val mode = LocalDeviceMode.current

    LaunchedEffect(openId, ui.loaded) { if (openId != null && ui.loaded) vm.open(openId) }
    BackHandler(enabled = ui.selected != null) { vm.select(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = mode == DeviceMode.TV || maxWidth >= 840.dp
        Row(
            Modifier.fillMaxSize().padding(horizontal = d.screenH, vertical = d.screenV),
            horizontalArrangement = Arrangement.spacedBy(d.gutter),
        ) {
            if (wide) {
                Column(Modifier.weight(0.24f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(d.gutter / 2)) {
                    GlassTextField(ui.filters.categoryQuery, vm::setCategoryQuery, "Buscar categoria")
                    CategoryList(
                        ui.categories.map {
                            CategoryEntry(it.id, it.name, when (it.id) {
                                CAT_FAVORITES -> Icons.Rounded.Favorite
                                CAT_ALL -> Icons.Rounded.Apps
                                else -> null
                            })
                        },
                        ui.filters.categoryId, vm::selectCategory, Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
            VodGrid(vm, ui, wide, onPlay, Modifier.weight(if (wide) 0.76f else 1f).fillMaxHeight())
        }
    }
}

@Composable
private fun VodGrid(
    vm: VodViewModel, ui: VodViewModel.Ui, wide: Boolean,
    onPlay: (PlaybackRequest) -> Unit, modifier: Modifier,
) {
    val d = LocalDimens.current
    val gridState = rememberLazyGridState()
    val hint = if (vm.kind == VodKind.MOVIE) "Buscar filmes" else "Buscar séries"
    val full: LazyGridItemSpanFn = { GridItemSpan(maxLineSpan) }

    LaunchedEffect(ui.selected?.id) { if (ui.selected != null) gridState.animateScrollToItem(0) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(d.posterMinWidth),
        state = gridState, modifier = modifier,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Topo: busca de títulos  ⇄  Hero (ao selecionar).
        item(key = "top", span = full) {
            Crossfade(targetState = ui.selected, label = "searchOrHero") { sel ->
                if (sel == null) {
                    GlassTextField(ui.filters.titleQuery, vm::setTitleQuery, hint)
                } else {
                    HeroPanel(
                        item = sel, isFavorite = sel.id in ui.favoriteIds, wide = wide,
                        onPlay = onPlay, onToggleFavorite = { vm.toggleFavorite(sel.id) },
                        onClose = { vm.select(null) },
                    )
                }
            }
        }
        if (!wide) {
            item(key = "cats", span = full) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(ui.categories.size) { i ->
                        val c = ui.categories[i]
                        GlassChip(c.name, ui.filters.categoryId == c.id, { vm.selectCategory(c.id) },
                            icon = if (c.id == CAT_FAVORITES) Icons.Rounded.Favorite else null)
                    }
                }
            }
        }
        item(key = "sort", span = full) { SortBar(ui.filters, vm::setSort, vm::toggleDirection) }

        if (ui.loaded && ui.items.isEmpty()) {
            item(key = "empty", span = full) {
                Text("Nenhum título encontrado", fontSize = d.textBody, color = Smoke.TextTertiary,
                    modifier = Modifier.padding(16.dp))
            }
        }
        items(ui.items, key = { it.id }) { item ->
            PosterCard(item, item.id in ui.favoriteIds, onClick = { vm.select(item) })
        }
    }
}

private typealias LazyGridItemSpanFn = androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan

/** [De A a Z] [Últimos adicionados] [Mais votados] [Crescente/Decrescente] */
@Composable
private fun SortBar(f: VodViewModel.Filters, onSort: (SortKey) -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortKey.entries.forEach { k -> GlassChip(k.label, f.sort == k, { onSort(k) }) }
        GlassChip(
            if (f.ascending) "Crescente" else "Decrescente", selected = false, onClick = onToggle,
            icon = if (f.ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
        )
    }
}

/** Painel Hero: sinopse, metadados e (séries) seletores de temporada e episódio. */
@Composable
private fun HeroPanel(
    item: VodItem, isFavorite: Boolean, wide: Boolean,
    onPlay: (PlaybackRequest) -> Unit, onToggleFavorite: () -> Unit, onClose: () -> Unit,
) {
    val d = LocalDimens.current
    val isTv = LocalDeviceMode.current == DeviceMode.TV
    var seasonIdx by remember(item.id) { mutableIntStateOf(0) }
    var episodeIdx by remember(item.id) { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { if (isTv) runCatching { playFocus.requestFocus() } }

    val season = item.seasons.getOrNull(seasonIdx)
    val episodes = season?.episodes.orEmpty()
    val meta = listOfNotNull(
        item.year?.toString(), item.genres.firstOrNull(), item.durationMin?.asDuration(),
        item.rating?.let { "★ ${it.asRating()}" },
    ).joinToString("  ·  ")

    val play = {
        if (item.kind == VodKind.SERIES) {
            val ep = episodes.getOrNull(episodeIdx)
            val eps = season?.episodes.orEmpty()
            val idx = eps.indexOfFirst { it.id == ep?.id }
            onPlay(
                PlaybackRequest(
                    "${item.title} · T${season?.number ?: 1}E${ep?.number ?: 1}", ep?.streamUrl,
                    item.headers, ep?.id ?: item.favKey, "series",
                    next = eps.getOrNull(idx + 1)?.let {
                        PlaybackRequest("${item.title} · T${season?.number ?: 1}E${it.number}", it.streamUrl, item.headers, it.id, "series")
                    },
                )
            )
        } else onPlay(PlaybackRequest(item.title, item.streamUrl, item.headers, item.favKey, "movie"))
    }

    Box(Modifier.fillMaxWidth().glass(fill = Smoke.GlassRaised)) {
        (item.backdropUrl ?: item.posterUrl)?.let {
            AsyncImage(it, null, Modifier.matchParentSize().alpha(0.30f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent))))

        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PosterImage(
                item.posterUrl, item.title,
                Modifier.width(if (wide) 104.dp else 84.dp).aspectRatio(POSTER_ASPECT).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, fontSize = d.textTitle, fontWeight = FontWeight.Bold,
                    color = Smoke.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = d.textCaption, color = Smoke.TextSecondary)
                Text(item.plot, fontSize = d.textBody, color = Smoke.TextSecondary,
                    maxLines = if (wide) 3 else 5, overflow = TextOverflow.Ellipsis)

                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassButton("Assistir", Icons.Rounded.PlayArrow, play, Modifier.focusRequester(playFocus))
                    GlassButton(
                        if (isFavorite) "Remover favorito" else "Favoritar",
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        onToggleFavorite,
                    )
                    GlassButton("Fechar", Icons.Rounded.Close, onClose)
                }

                if (item.kind == VodKind.SERIES && item.seasons.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        itemsIndexed(item.seasons, key = { _, s -> s.number }) { i, s ->
                            GlassChip("Temporada ${s.number}", seasonIdx == i, { seasonIdx = i; episodeIdx = 0 })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(episodes, key = { _, e -> e.id }) { i, ep ->
                            GlassClickable(
                                onClick = {
                                    episodeIdx = i
                                    onPlay(PlaybackRequest("${item.title} · T${season?.number}E${ep.number}", ep.streamUrl, item.headers, ep.id, "series"))
                                },
                                onFocused = { episodeIdx = i },
                                selected = episodeIdx == i,
                                modifier = Modifier.width(150.dp).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(10.dp),
                            ) {
                                Column(Modifier.align(Alignment.CenterStart)) {
                                    Text("E${ep.number}", fontSize = d.textCaption, fontWeight = FontWeight.Bold, color = Smoke.TextPrimary)
                                    Text(ep.title, fontSize = d.textCaption, color = Smoke.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
