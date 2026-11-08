package com.smarted.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.smarted.tv.ui.components.EmptyState
import com.smarted.tv.ui.components.FilterPill
import com.smarted.tv.ui.components.keepFocusInView
import com.smarted.tv.ui.components.PosterCell
import com.smarted.tv.ui.components.RemoteImage
import com.smarted.tv.ui.components.SearchField
import com.smarted.tv.ui.components.SectionHead
import com.smarted.tv.ui.components.TvAction
import com.smarted.tv.ui.rememberVodModel
import com.smarted.tv.ui.state.AppViewModel
import com.smarted.tv.ui.state.VodViewModel
import com.smarted.tv.ui.theme.LocalCompact
import com.smarted.tv.ui.theme.TvColors
import com.smarted.tv.ui.theme.dpx
import com.smarted.tv.ui.theme.spx
import com.smarted.tv.ui.theme.whiteFocus

/**
 * Filmes/Séries — 2 colunas no padrão do app de referência:
 * 1) Categorias de VOD + Favoritos + campo de busca de categorias no topo ·
 * 2) grade infinita estilo Smarters Pro (pôster 2:3, colunas ~150dp, filtros de ordenação).
 * Abrir um título substitui a grade pelo painel Hero (sinopse, metadados, temporadas/episódios).
 * Busca com escopo: aqui só Filmes OU só Séries, nunca canais.
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
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(revision) { if (revision > 0) vm.reload() }

    Row(
        Modifier.fillMaxSize().padding(horizontal = dpx(24), vertical = dpx(6)),
        horizontalArrangement = Arrangement.spacedBy(dpx(14)),
    ) {
        // ——— Coluna 1: categorias + favoritos + busca de categorias ———
        Column(
            Modifier.fillMaxHeight().weight(0.26f).clip(RoundedCornerShape(dpx(26)))
                .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
                .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(26)))
                .padding(dpx(18)),
            verticalArrangement = Arrangement.spacedBy(dpx(12)),
        ) {
            SectionHead("Categorias · ${if (tab == Tab.MOVIES) "Filmes" else "Séries"}")
            SearchField(
                value = ui.categorySearch,
                onChange = vm::setCategorySearch,
                hint = "Pesquisar categorias…",
                modifier = Modifier.fillMaxWidth(),
                tag = "vod-category-search",
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(8))) {
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
                items(ui.categories, key = { "cat-${it.first}" }) { (name, count) ->
                    CategoryChip(name, count, ui.query.category == name, { vm.setQuery { q -> q.copy(category = name) } })
                }
                if (ui.categories.isEmpty()) {
                    item { Text("Nenhuma categoria ainda — importe uma fonte com VOD (Xtream) ou grupos FILMES/SÉRIES numa M3U.", color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(dpx(8))) }
                }
            }
        }

        // ——— Coluna 2: grade infinita OU hero ———
        Box(Modifier.fillMaxHeight().weight(0.74f)) {
            val selected = ui.selected
            if (selected == null) {
                Column(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(dpx(26)))
                        .background(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF))))
                        .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(26)))
                        .padding(dpx(18)),
                    verticalArrangement = Arrangement.spacedBy(dpx(12)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dpx(12))) {
                        Text(scopeLabel(ui.query), color = Color.White, fontSize = spx(28), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%,d".format(ui.total).replace(',', '.') + " título(s)", color = TvColors.Muted, fontSize = spx(16))
                        Spacer(Modifier.weight(1f))
                        SearchField(
                            value = ui.query.search,
                            onChange = { text -> vm.setQuery { it.copy(search = text) } },
                            hint = if (tab == Tab.MOVIES) "Buscar filmes…" else "Buscar séries…",
                            modifier = Modifier.width(dpx(430)),
                            tag = "vod-content-search",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(dpx(10)), verticalAlignment = Alignment.CenterVertically) {
                        FilterPill("De A a Z", ui.query.sort == SortMode.NAME, "vod-sort-name") { vm.setQuery { it.copy(sort = SortMode.NAME) } }
                        FilterPill("Últimos adicionados", ui.query.sort == SortMode.ADDED, "vod-sort-added") { vm.setQuery { it.copy(sort = SortMode.ADDED) } }
                        FilterPill("Mais votados", ui.query.sort == SortMode.RATING, "vod-sort-rating") { vm.setQuery { it.copy(sort = SortMode.RATING) } }
                        FilterPill("Ano", ui.query.sort == SortMode.YEAR, "vod-sort-year") { vm.setQuery { it.copy(sort = SortMode.YEAR) } }
                        Spacer(Modifier.weight(1f))
                        FilterPill(
                            if (ui.query.direction == SortDirection.DESC) "↓ Decrescente" else "↑ Crescente",
                            false, "vod-direction",
                        ) { vm.setQuery { it.copy(direction = if (it.direction == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC) } }
                    }
                    if (ui.query.sort == SortMode.RATING) {
                        Text("“Mais votados” usa a nota informada pela fonte (Xtream/rating); ordene por outro campo quando a nota estiver ausente.", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("vod-rating-hint"))
                    }
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
                        // Grade Smarters Pro: colunas adaptativas ~150dp, pôster 2:3, infinito de cima p/ baixo.
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("vod-grid"),
                            horizontalArrangement = Arrangement.spacedBy(dpx(14)),
                            verticalArrangement = Arrangement.spacedBy(dpx(16)),
                            contentPadding = PaddingValues(bottom = dpx(12)),
                        ) {
                            itemsIndexed(ui.entries, key = { _, e -> "v-${e.id}" }) { _, entry ->
                                PosterCell(entry = entry, onClick = { vm.select(entry) })
                            }
                        }
                        if (ui.loading) Text("Carregando…", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = dpx(6)))
                    }
                }
            } else {
                DetailsHero(
                    entry = selected,
                    ui = ui,
                    vm = vm,
                    onBack = { vm.clearSelection(); keyboard?.hide() },
                    onPlay = { playVod(selected, vm, onPlay) },
                    onPlayEpisode = { episode ->
                        val seasonEps = ui.episodes.filter { it.season == ui.selectedSeason }
                        onPlay(
                            PlayRequest(
                                vm.playbackTarget(episode),
                                "${selected.name} · ${episodeTitle(episode)}",
                                "${selected.favKey}:${episode.id}",
                                "vod",
                                seasonEps.map { QueuedItem(selected.name + " · " + episodeTitle(it), vm.playbackTarget(it), "${selected.favKey}:${it.id}") },
                                seasonEps.indexOfFirst { e -> e.id == episode.id },
                            )
                        )
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

/** Painel Hero — substitui a grade: backdrop, pôster, sinopse, metadados e temporadas/episódios. */
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
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(dpx(26))).background(Color(0xFF07080A)).testTag("vod-hero-${entry.id}")) {
        Box(Modifier.fillMaxWidth().weight(0.44f)) {
            RemoteImage(entry.backdropUrl ?: entry.posterUrl, entry.name, Modifier.matchParentSize(), ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x33040506), Color(0xE6040506), Color(0xFF040506)))))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(dpx(26)),
                horizontalArrangement = Arrangement.spacedBy(dpx(24)),
            ) {
                Box(
                    Modifier.width(dpx(220)).aspectRatio(2f / 3f).clip(RoundedCornerShape(dpx(16)))
                        .border(1.dp, TvColors.Outline, RoundedCornerShape(dpx(16))),
                ) {
                    RemoteImage(entry.posterUrl, entry.name, Modifier.matchParentSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(8))) {
                    Text(entry.name, color = Color.White, fontSize = spx(34), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(dpx(10)), verticalAlignment = Alignment.CenterVertically) {
                        entry.year?.let { MetaChip(it.toString()) }
                        if (entry.rating != null && entry.rating > 0) MetaChip("★ %.1f".format(entry.rating))
                        entry.runtimeMinutes?.takeIf { it > 0 }?.let { MetaChip("${it} min") }
                        if (entry.genre.isNotBlank()) MetaChip(entry.genre)
                        if (entry.category.isNotBlank()) MetaChip(entry.category)
                    }
                    if (entry.plot.isNotBlank()) {
                        Text(entry.plot, color = TvColors.Muted, fontSize = spx(17), maxLines = if (LocalCompact.current) 3 else 5, overflow = TextOverflow.Ellipsis)
                    }
                    if (entry.cast.isNotEmpty()) {
                        Text("Elenco: ${entry.cast.take(6).joinToString(", ")}", color = TvColors.Muted, fontSize = spx(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(dpx(14)), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dpx(8))) {
                        if (entry.kind == Tab.MOVIES) {
                            TvAction(if (playable) "▶ Assistir" else "Sem stream direto", onPlay, primary = true, enabled = playable, modifier = Modifier.testTag("hero-play"))
                        } else {
                            Text("Escolha um episódio abaixo ▶", color = TvColors.Muted, fontSize = spx(16))
                        }
                        TvAction(if (entry.favKey in ui.favoriteKeys) "★ Favoritado" else "☆ Favoritar", { vm.toggleFavorite(entry) }, compact = true, modifier = Modifier.testTag("hero-fav"))
                        TvAction("Voltar à grade", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, compact = true, modifier = Modifier.testTag("hero-back"))
                        if (!playable && entry.kind == Tab.MOVIES) {
                            Text("Fonte direta indisponível nesta entrada — configure um provedor com streams.", color = Color(0xFFFFC9C9), fontSize = spx(14), maxLines = 2)
                        }
                    }
                }
            }
        }
        if (entry.kind == Tab.SERIES) {
            SeriesEpisodeList(ui, vm, seasons, onPlayEpisode, ui.detailBusy, Modifier.weight(0.56f))
        }
    }
}

@Composable
private fun SeriesEpisodeList(
    ui: com.smarted.tv.ui.state.VodUi,
    vm: VodViewModel,
    seasons: List<Int>,
    onPlayEpisode: (Episode) -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = dpx(26), vertical = dpx(14)), verticalArrangement = Arrangement.spacedBy(dpx(10))) {
        SectionHead("Temporadas e episódios")
        if (seasons.isEmpty()) {
            Text(
                if (busy) "Carregando episódios do painel…" else "Este título ainda não tem episódios sincronizados (painel sem get_series_info ou fonte sem lista de episódios).",
                color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(dpx(10))) {
                seasons.forEach { season ->
                    FilterPill("T$season", ui.selectedSeason == season, "season-$season") { vm.setSeason(season) }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(8)), contentPadding = PaddingValues(bottom = dpx(10))) {
                items(ui.episodes.filter { it.season == ui.selectedSeason }, key = { "e-${it.id}" }) { episode ->
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
            .keepFocusInView()
            .onFocusChanged { focused = it.isFocused }
            .whiteFocus(focused, radius = dpx(18))
            .padding(dpx(3))
            .clip(RoundedCornerShape(dpx(14)))
            .background(if (focused) Color(0x26FFFFFF) else Color(0x0DFFFFFF))
            .clickable(interactionSource = interaction, indication = null, onClick = { onPlay(episode) })
            .padding(dpx(12)),
        horizontalArrangement = Arrangement.spacedBy(dpx(14)), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${episode.episode}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = spx(22), modifier = Modifier.width(dpx(48)))
        Box(Modifier.width(dpx(150)).height(dpx(84)).clip(RoundedCornerShape(dpx(9))).background(Color(0xFF101114))) {
            RemoteImage(episode.thumbUrl, null, Modifier.matchParentSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dpx(3))) {
            Text(episode.title.ifBlank { "Episódio ${episode.episode}" }, color = Color.White, fontSize = spx(19), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (episode.plot.isNotBlank()) {
                Text(episode.plot, color = TvColors.Muted, fontSize = spx(15), maxLines = if (LocalCompact.current) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
        episode.runtimeMinutes?.takeIf { it > 0 }?.let { Text("${it} min", color = TvColors.Muted, fontSize = spx(15)) }
        Icon(Icons.Default.PlayArrow, null, Modifier.size(dpx(22)), tint = Color.White)
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(Modifier.background(Color(0x24FFFFFF), RoundedCornerShape(dpx(10))).padding(horizontal = dpx(12), vertical = dpx(4))) {
        Text(text, color = Color.White, fontSize = spx(15), maxLines = 1)
    }
}
