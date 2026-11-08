package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.PlaybackTarget
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VodUi(
    val query: BrowseQuery = BrowseQuery(sort = SortMode.ADDED, direction = SortDirection.DESC),
    val categories: List<Pair<String, Int>> = emptyList(),
    val entries: List<VodEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val loading: Boolean = false,
    val selected: VodEntry? = null,
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int = 1,
    val detailBusy: Boolean = false,
    val favoriteKeys: Set<String> = emptySet(),
    val message: String = "",
    val categorySearch: String = "",
)

class VodViewModel(private val graph: AppGraph, val tab: Tab) : ViewModel() {

    private val _ui = MutableStateFlow(VodUi())
    val ui = _ui.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val data = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cats = graph.catalog.vodCategories(tab, _ui.value.categorySearch.ifBlank { null })
                val page = graph.catalog.vodPage(tab, _ui.value.query, _ui.value.page)
                val favs = graph.catalog.favoriteKeys(if (tab == Tab.SERIES) "series" else "movie")
                Triple(cats, page, favs)
            } }.getOrNull() ?: run { _ui.update { it.copy(loading = false, message = "Falha ao ler o catálogo local.") }; return@launch }
            _ui.update { it.copy(categories = data.first, entries = data.second.items, total = data.second.total, loading = false, favoriteKeys = data.third) }
        }
    }

    fun setQuery(transform: (BrowseQuery) -> BrowseQuery) {
        _ui.update { it.copy(query = transform(it.query)) }
        reload()
    }

    fun setCategorySearch(value: String) {
        _ui.update { it.copy(categorySearch = value) }
        viewModelScope.launch {
            val cats = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.catalog.vodCategories(tab, value.ifBlank { null }) } }.getOrNull() ?: return@launch
            _ui.update { it.copy(categories = cats) }
        }
    }

    fun loadMore() {
        val current = _ui.value
        if (current.loading || current.entries.size >= current.total) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, page = it.page + 1) }
            val page = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                graph.catalog.vodPage(tab, current.query, _ui.value.page)
            } }.getOrNull() ?: run { _ui.update { it.copy(loading = false) }; return@launch }
            _ui.update { it.copy(entries = it.entries + page.items, loading = false) }
        }
    }

    /** Seleciona um título: carrega detalhes (episódios de séries via painel) e troca busca por hero. */
    fun select(entry: VodEntry) {
        _ui.update { it.copy(selected = entry, episodes = emptyList(), detailBusy = entry.kind == Tab.SERIES) }
        if (entry.kind != Tab.SERIES) return
        viewModelScope.launch {
            val result = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val source = graph.store.sourceById(entry.sourceId) ?: return@withContext Triple(0, emptyList<Episode>(), 0)
                val loaded = runCatching { graph.importers.loadSeriesEpisodes(entry.id, entry.externalKey, source) }.getOrDefault(0)
                val episodes = graph.catalog.episodes(entry.id)
                Triple(loaded, episodes, episodes.map { it.season }.distinct().sorted().firstOrNull() ?: 1)
            } }.getOrNull() ?: run { _ui.update { it.copy(detailBusy = false) }; return@launch }
            _ui.update { it.copy(detailBusy = false, episodes = result.second, selectedSeason = if (it.selectedSeason > 0) result.third else it.selectedSeason) }
        }
    }

    fun clearSelection() { _ui.update { it.copy(selected = null, episodes = emptyList()) } }

    fun setSeason(season: Int) { _ui.update { it.copy(selectedSeason = season) } }

    fun toggleFavorite(entry: VodEntry) {
        viewModelScope.launch {
            val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.catalog.toggleVodFavorite(entry) }
            _ui.update {
                it.copy(
                    favoriteKeys = if (added) it.favoriteKeys + entry.favKey else it.favoriteKeys - entry.favKey,
                    message = if (added) "${entry.name} nos favoritos." else "${entry.name} removido dos favoritos.",
                )
            }
            if (_ui.value.query.isFavorites) reload()
        }
    }

    fun playbackTarget(entry: VodEntry): PlaybackTarget? =
        entry.streamUrl.takeIf(String::isNotBlank)?.let { PlaybackTarget(url = it, title = entry.name, isLive = false) }

    fun playbackTarget(episode: Episode): PlaybackTarget = PlaybackTarget(url = episode.streamUrl, title = episode.title, isLive = false)

    fun favoriteKind(): String = if (tab == Tab.SERIES) "series" else "movie"
}
