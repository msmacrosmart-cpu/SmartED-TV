package com.smarted.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smarted.tv.core.catalog.sortItems
import com.smarted.tv.core.catalog.foldText
import com.smarted.tv.core.model.*
import com.smarted.tv.ui.components.*
import com.smarted.tv.ui.state.CatalogViewModel
import com.smarted.tv.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CatalogScreen(vm: CatalogViewModel, revision: Int, onHome: () -> Unit, onSettings: () -> Unit, onPlay: (MediaItem, Video?) -> Unit) {
    val ui by vm.ui.collectAsState()
    val data by vm.data.collectAsState()
    LaunchedEffect(revision) { vm.start(); if (revision > 0) vm.retry() }
    BackHandler(enabled = ui.selected != null) { vm.closeDetails() }
    val categoriesFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val categoryList = remember(ui.items, ui.query.category, data.addons) { vm.categories() }
    Row(Modifier.fillMaxSize().testTag("catalog-${vm.scope}"), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.width(if (LocalCompact.current) 190.dp else 228.dp).fillMaxHeight().focusGroup().focusRestorer(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackAction(onHome)
            Text(if (vm.scope == Scope.MOVIES) "Filmes" else "Séries", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            SearchField(ui.categorySearch, vm::categorySearch, "Pesquisar categorias", Modifier.fillMaxWidth(), "category-search")
            val categories = listOf("Todos", "Favoritos") + categoryList.filter { foldText(it).contains(foldText(ui.categorySearch)) }
            LazyColumn(Modifier.weight(1f).focusGroup().focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories, key = { it }) { category ->
                    Choice(if (category == "Favoritos") "Favoritos (${data.favorites.count { it.scope == vm.scope }})" else category,
                        selected = ui.query.category == category, onClick = { vm.category(category) },
                        modifier = Modifier.fillMaxWidth().then(if (category == categories.first()) Modifier.focusRequester(categoriesFocus) else Modifier)
                            .focusProperties { right = gridFocus }.testTag("category-$category"))
                }
            }
            Text("${ui.items.size} títulos carregados", style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val selected = ui.selected
            if (selected != null) {
                DetailHero(vm, selected, ui.detailLoading, ui.season, ui.episode, data.favorites.any { it.key == selected.key }, onPlay,
                    Modifier.fillMaxWidth().weight(if (vm.scope == Scope.SERIES) 0.95f else 1f))
                if (vm.scope == Scope.SERIES) {
                    Column(Modifier.weight(1.05f).focusGroup().focusRestorer(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected.seasons.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            selected.seasons.take(8).forEach { season -> Choice("Temporada $season", ui.season == season, { vm.season(season) }, Modifier.testTag("season-$season")) }
                        }
                        val episodes = selected.videos.filter { it.season == ui.season }.sortedWith(compareBy({ it.episode ?: Int.MAX_VALUE }, { it.id }))
                        if (episodes.isEmpty()) Notice("Este addon ainda não informou episódios. Atualize o catálogo ou escolha outra fonte.")
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(episodes, key = { it.id }) { episode ->
                                Choice("${episode.episode?.let { "$it. " } ?: ""}${episode.title.ifBlank { episode.id }}", ui.episode?.id == episode.id,
                                    { vm.episode(episode) }, Modifier.fillMaxWidth().focusProperties { left = categoriesFocus }.testTag("episode-${episode.id}"))
                            }
                        }
                    }
                }
            } else {
                SearchField(ui.query.search, vm::search, "Pesquisar em ${if (vm.scope == Scope.MOVIES) "Filmes" else "Séries"}", Modifier.fillMaxWidth(), "content-search")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Choice("De A a Z", ui.query.sort == Sort.AZ, { vm.sort(Sort.AZ) })
                    Choice("Últimos adicionados", ui.query.sort == Sort.ADDED, { vm.sort(Sort.ADDED) })
                    Choice("Mais votados", ui.query.sort == Sort.RATING, { vm.sort(Sort.RATING) })
                    Choice("Ano", ui.query.sort == Sort.YEAR, { vm.sort(Sort.YEAR) })
                    Choice(if (ui.query.direction == Direction.ASC) "Crescente ↑" else "Decrescente ↓", false, vm::direction)
                }
                Notice(if (ui.query.sort == Sort.RATING) "Ordenação dos títulos carregados por nota IMDb; o protocolo não fornece número de votos."
                    else if (ui.query.sort == Sort.ADDED) "Ordenação dos títulos carregados: data de lançamento ou ordem do addon; addons sem data não informam quando foram adicionados."
                    else "Ordenação dos títulos carregados. Role para baixo para descobrir mais.")
                CatalogGrid(vm, ui.items, ui.query, ui.loading, ui.loadingMore, ui.hasMore, ui.error, gridFocus, categoriesFocus, onSettings)
            }
            ui.warnings.forEach { Notice(it) }
        }
    }
}

@Composable
private fun ColumnScope.CatalogGrid(vm: CatalogViewModel, items: List<MediaItem>, query: CatalogQuery, loading: Boolean, loadingMore: Boolean, hasMore: Boolean, error: String,
                        gridFocus: FocusRequester, categoriesFocus: FocusRequester, onSettings: () -> Unit) {
    val state = rememberLazyGridState()
    LaunchedEffect(state, hasMore, items.size) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect {
            if (it >= items.size - 8) vm.loadMore()
        }
    }
    val sorted = remember(items, query.sort, query.direction) { sortItems(items, query.sort, query.direction) }
    if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
    else if (sorted.isEmpty() && !hasMore) EmptyState(if (error.isNotBlank()) "Não foi possível carregar" else "Nenhum título por aqui",
        error.ifBlank { "Adicione um addon compatível, selecione outra categoria ou refine sua busca. Nenhum conteúdo de outro escopo será misturado." },
        Modifier.fillMaxWidth().focusRequester(gridFocus), { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TvAction("Tentar novamente", vm::retry); TvAction("Configurações", onSettings) } })
    else LazyVerticalGrid(columns = GridCells.Adaptive(if (LocalCompact.current) 132.dp else 155.dp), modifier = Modifier.weight(1f).focusGroup().focusRestorer().testTag("content-grid"),
        state = state, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
        items(sorted, key = { it.key }) { item -> MediaCard(item, { vm.select(item) }, Modifier.fillMaxWidth().focusProperties { left = categoriesFocus }
            .then(if (item.key == sorted.first().key) Modifier.focusRequester(gridFocus) else Modifier)) }
        if (hasMore || loadingMore || error.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loadingMore) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                TvAction(if (error.isNotBlank()) "Tentar novamente" else if (loadingMore) "Carregando…" else "Carregar mais", vm::loadMore, enabled = !loadingMore)
                if (error.isNotBlank()) Text(error, color = TvColors.Muted)
            }
        }
    }
}

@Composable
private fun DetailHero(vm: CatalogViewModel, item: MediaItem, loading: Boolean, season: Int?, episode: Video?, favorite: Boolean,
                       onPlay: (MediaItem, Video?) -> Unit, modifier: Modifier) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.key) { playFocus.requestFocus() }
    Box(modifier.smokedGlass().testTag("detail-hero")) {
        RemoteImage(item.background ?: item.poster, null, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF5090A0C), Color(0x99090A0C), Color(0x33090A0C)))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(if (item.scope == Scope.MOVIES) "Filme / ${item.catalogName}" else "Série / ${item.catalogName}")
                TvAction("Fechar detalhes", vm::closeDetails, compact = true)
            }
            Text(item.name, color = Color.White, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.year?.toString(), item.runtime.takeIf(String::isNotBlank), item.rating?.let { "IMDb $it" }, item.language.takeIf(String::isNotBlank)).joinToString("  ·  "), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(item.genres.joinToString(" · "), color = TvColors.Muted, style = MaterialTheme.typography.labelMedium)
            Text(item.description.ifBlank { "O addon não forneceu uma sinopse para este título." }, color = TvColors.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = if (item.scope == Scope.SERIES) 3 else 6, overflow = TextOverflow.Ellipsis)
            if (item.cast.isNotEmpty()) Text("Elenco: ${item.cast.take(5).joinToString()}", color = TvColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (episode != null) Text("T${season ?: episode.season ?: 1} E${episode.episode ?: 1} · ${episode.title}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvAction(if (loading) "Carregando detalhes…" else "Assistir", { onPlay(item, episode) }, icon = Icons.Default.PlayArrow, primary = true, enabled = !loading, focusRequester = playFocus)
                TvAction(if (favorite) "Favoritado" else "Favoritar", { vm.toggleFavorite(item) }, icon = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder)
            }
        }
    }
}
