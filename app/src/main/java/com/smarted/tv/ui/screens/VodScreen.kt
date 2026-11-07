package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.ui.components.CategoryChip
import com.smarted.tv.ui.components.Choice
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.SectionLabel
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.components.VodPosterCard
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.state.VodViewModel
import com.smarted.tv.ui.rememberVodModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.smokedGlass
import com.smarted.tv.ui.theme.whiteFocus

/**
 * Filmes/Séries — 2 colunas (painel de referência "Categorias + grade"):
 * 1) busca de categorias + Favoritos + categorias · 2) grade infinita com ordenação
 * (A-Z, últimos adicionados, mais votados, ano, crescente/decrescente).
 * Selecionar um título troca a busca pelo banner hero (sinopse, metadados,
 * temporadas/episódios nas séries).
 */
@Composable
fun VodScreen(
    tab: Tab,
    app: AppViewModel,
    revision: Int,
    onPlay: (PlayRequest) -> Unit,
) {
    val vm = rememberVodModel(app.graph, tab)
    val ui by vm.ui.collectAsState()
    val compact = LocalCompact.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(revision) { if (revision > 0) vm.reload() }

    Row(Modifier.fillMaxSize().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
        // Coluna 1 — categorias
        Column(
            Modifier.fillMaxHeight().weight(0.28f).smokedGlass(18.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Categorias de ${if (tab == Tab.MOVIES) "filmes" else "séries"}")
            SearchField(
                value = ui.categorySearch,
                onChange = vm::setCategorySearch,
                hint = if (compact) "Pesquisar categor…" else "Pesquisar categorias…",
                modifier = Modifier.fillMaxWidth(),
                tag = "vod-category-search",
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    CategoryChip(
                        BrowseQuery.FAVORITES, ui.favoriteKeys.size.takeIf { it > 0 }, ui.query.isFavorites,
                        { vm.setQuery { q -> q.copy(category = if (q.isFavorites) BrowseQuery.ALL else BrowseQuery.FAVORITES) } },
                        icon = Icons.Default.Star,
                    )
                }
                item {
                    CategoryChip(BrowseQuery.ALL, ui.total.takeIf { ui.query.isAll && ui.query.search.isBlank() && !ui.query.isFavorites }, ui.query.isAll, { vm.setQuery { q -> q.copy(category = BrowseQuery.ALL) } })
                }
                items(ui.categories, key = { "cat-$it" }) { (name, count) ->
                    CategoryChip(name, count, ui.query.category == name, { vm.setQuery { q -> q.copy(category = name) } })
                }
                if (ui.categories.isEmpty()) {
                    item { Text("Nenhuma categoria ainda — importe uma fonte com VOD (Xtream) ou grupos FILMES/SÉRIES numa M3U.", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp)) }
                }
            }
        }

        // Coluna 2 — grade ou hero de detalhes
        Box(Modifier.fillMaxHeight().weight(0.72f)) {
            val selected = ui.selected
            if (selected == null) {
                Column(
                    Modifier.fillMaxSize().smokedGlass(18.dp).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SearchField(
                            value = ui.query.search,
                            onChange = { text -> vm.setQuery { it.copy(search = text) } },
                            hint = if (tab == Tab.MOVIES) "Buscar filmes…" else "Buscar séries…",
                            modifier = Modifier.weight(1f),
                            tag = "vod-content-search",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(scopeLabel(ui.query), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${ui.total} título(s)", style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
                        Spacer(Modifier.weight(1f))
                        Choice("De A a Z", ui.query.sort == SortMode.NAME, { vm.setQuery { it.copy(sort = SortMode.NAME) } })
                        Choice("Últimos adicionados", ui.query.sort == SortMode.ADDED, { vm.setQuery { it.copy(sort = SortMode.ADDED) } })
                        Choice("Mais votados", ui.query.sort == SortMode.RATING, { vm.setQuery { it.copy(sort = SortMode.RATING) } })
                        Choice("Ano", ui.query.sort == SortMode.YEAR, { vm.setQuery { it.copy(sort = SortMode.YEAR) } })
                        TvAction(
                            (if (ui.query.direction == SortDirection.DESC) "↓ Decrescente" else "↑ Crescente"),
                            { vm.setQuery { it.copy(direction = if (it.direction == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC) } },
                            compact = true, modifier = Modifier.testTag("vod-direction"),
                        )
                    }
                    NoticeRatingHint(ui.query.sort)
                    if (ui.entries.isEmpty() && !ui.loading) {
                        EmptyState(
                            if (ui.total == 0 && ui.categories.isEmpty()) "Catálogo de ${if (tab == Tab.MOVIES) "filmes" else "séries"} vazio" else "Nada para este filtro",
                            "Importe uma fonte com VOD nas Configurações ou limpe a busca.",
                            Modifier.fillMaxWidth(),
                        )
                    } else {
                        val gridState = rememberLazyGridState()
                        LaunchedEffect(gridState, ui.entries.size, ui.total) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                                .collect { last -> if (ui.entries.isNotEmpty() && last >= ui.entries.size - 14) vm.loadMore() }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (compact) 4 else 5),
                            state = gridState,
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("vod-grid"),
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            itemsIndexed(ui.entries, key = { _, e -> "v-${e.id}" }) { _, entry ->
                                VodPosterCard(entry = entry, onClick = { vm.select(entry) }, onFocus = {})
                            }
                        }
                        if (ui.loading) Text("Carregando…", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            } else {
                DetailsHero(
                    entry = selected,
                    ui = ui,
                    vm = vm,
                    onBack = { vm.clearSelection(); keyboard?.hide() },
                    onPlay = { playVod(selected, vm, it) },
                    onPlayEpisode = { episode ->
                        onPlay(PlayRequest(vm.playbackTarget(episode), "${selected.name} · ${episodeTitle(episode)}", "${selected.favKey}:${episode.id}", "vod"))
                    },
                )
            }
        }
    }
}

private fun playVod(entry: VodEntry, vm: VodViewModel, after: (PlayRequest) -> Unit) {
    val target = entry.streamUrl.takeIf(StreamRules::isProbablyPlayable) ?: return
    after(PlayRequest(vm.playbackTarget(entry) ?: return, entry.name, entry.favKey, "vod"))
}

private fun scopeLabel(q: BrowseQuery): String = when {
    q.isFavorites -> "Favoritos"
    q.isAll -> "Todos"
    else -> q.category
}

private fun episodeTitle(episode: Episode): String =
    "T${episode.season} E${episode.episode}${if (episode.title.isNotBlank()) " · ${episode.title}" else ""}"

@Composable
private fun NoticeRatingHint(sort: SortMode) {
    if (sort == SortMode.RATING) {
        Text("“Mais votados” usa a nota informada pela fonte (Xtream/rating); ordene por outro campo quando a nota estiver ausente.", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("vod-rating-hint"))
    }
}

/** Banner hero: substitui a busca; sinopse + metadados + play + (séries) temporadas/episódios. */

@Composable
private fun DetailsHero(
    entry: VodEntry,
    ui: com.smarted.tv.ui.state.VodUi,
    vm: VodViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
) {
    val playable = entry.streamUrl.isNotBlank() && StreamRules.isProbablyPlayable(entry.streamUrl)
    val seasons = if (ui.episodes.isEmpty()) emptyList() else ui.episodes.map { it.season }.distinct().sorted()
    Box(Modifier.fillMaxSize().testTag("vod-hero-${entry.id}")) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.42f)) {
                RemoteImage(entry.backdropUrl ?: entry.posterUrl, entry.name, Modifier.matchParentSize(), ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x00050608), Color(0xF2050608), Color(0xFF050608)))))
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.width(if (LocalCompact.current) 96.dp else 124.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) {
                        RemoteImage(entry.posterUrl, null, Modifier.matchParentSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            entry.year?.let { MetaChip(it.toString()) }
                            entry.rating?.takeIf { it > 0 }?.let { MetaChip("Nota %.1f".format(it)) }
                            if (entry.category.isNotBlank()) MetaChip(entry.category)
                            entry.runtimeMinutes?.takeIf { it > 0 }?.let { MetaChip("${it} min") }
                            if (entry.kind == Tab.SERIES && seasons.isNotEmpty()) MetaChip("${seasons.size} temporada(s)")
                        }
                        if (entry.plot.isNotBlank()) {
                            Text(entry.plot, color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = if (LocalCompact.current) 3 else 4, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (playable) {
                                TvAction(if (entry.kind == Tab.SERIES) "Assistir 1º episódio" else "Assistir", onPlay, primary = true, icon = Icons.Default.PlayArrow, modifier = Modifier.testTag("hero-play"))
                            }
                            HeartToggleFav(entry.favKey in ui.favoriteKeys) { vm.toggleFavorite(entry) }
                            TvAction("Voltar à grade", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, compact = true, modifier = Modifier.testTag("hero-back"))
                            if (!playable && entry.kind != Tab.SERIES) {
                                Text("Fonte direta indisponível nesta entrada — configure um provedor com streams.", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                            }
                        }
                    }
                }
            }
            if (entry.kind == Tab.SERIES) {
                SeriesEpisodeList(ui, vm, seasons, onPlayEpisode, ui.detailBusy)
            }
        }
    }
}

@Composable
private fun HeartToggleFav(isFavorite: Boolean, onToggle: () -> Unit) {
    TvAction(if (isFavorite) "★ Favoritado" else "☆ Favoritar", onToggle, compact = true, modifier = Modifier.testTag("hero-fav"))
}

@Composable
private fun SeriesEpisodeList(
    ui: com.smarted.tv.ui.state.VodUi,
    vm: VodViewModel,
    seasons: List<Int>,
    onPlayEpisode: (Episode) -> Unit,
    busy: Boolean,
) {
    Column(Modifier.fillMaxWidth().weight(0.58f).smokedGlass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Temporadas e episódios")
        if (seasons.isEmpty()) {
            Text(
                if (busy) "Carregando episódios do painel…" else if (ui.episodes.isEmpty()) "Este título ainda não tem episódios sincronizados (painel sem get_series_info ou fonte sem lista de episódios)." else "",
                color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                seasons.forEach { season ->
                    Choice("T$season", ui.selectedSeason == season, { vm.setSeason(season) }, modifier = Modifier.testTag("season-$season"))
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.episodes.filter { it.season == ui.selectedSeason }, key = { "e-$it" }) { episode ->
                    EpisodeRow(episode, onPlayEpisode)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onPlay: (Episode) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().testTag("episode-${episode.id}")
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = 12.dp).padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0x26FFFFFF) else Color(0x0DFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = { onPlay(episode) })
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${episode.episode}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
        Box(Modifier.width(86.dp).height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF101114))) {
            RemoteImage(episode.thumbUrl, null, Modifier.matchParentSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(episode.title.ifBlank { "Episódio ${episode.episode}" }, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (episode.plot.isNotBlank()) {
                Text(episode.plot, color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = if (LocalCompact.current) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
        episode.durationMinutes?.takeIf { it > 0 }?.let { Text("${it} min", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium) }
        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = Color.White)
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(Modifier.background(Color(0x24FFFFFF), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
