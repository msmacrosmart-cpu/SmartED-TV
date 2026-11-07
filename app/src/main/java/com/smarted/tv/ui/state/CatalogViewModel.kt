package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.AppGraph
import com.smarted.tv.core.catalog.PageCursor
import com.smarted.tv.core.catalog.foldText
import com.smarted.tv.core.catalog.sortItems
import com.smarted.tv.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class CatalogUi(
    val query: CatalogQuery = CatalogQuery(),
    val categorySearch: String = "",
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String = "",
    val warnings: List<String> = emptyList(),
    val hasMore: Boolean = false,
    val selected: MediaItem? = null,
    val detailLoading: Boolean = false,
    val season: Int? = null,
    val episode: Video? = null,
)

class CatalogViewModel(val scope: Scope, val graph: AppGraph) : ViewModel() {
    private val _ui = MutableStateFlow(CatalogUi())
    val ui = _ui.asStateFlow()
    val data get() = graph.repository.data
    private var cursor: PageCursor? = null
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var searchJob: Job? = null
    private var generation = 0
    private val known = mutableSetOf<String>()
    private val guideSeen = mutableSetOf<String>()
    private val guideGate = Semaphore(3)
    private var started = false

    fun start() {
        if (started) return
        started = true
        reset()
        viewModelScope.launch {
            data.collect {
                if (_ui.value.query.category == "Favoritos") publishFavorites()
            }
        }
    }

    fun search(value: String) {
        _ui.update { it.copy(query = it.query.copy(search = value), selected = null) }
        detailJob?.cancel()
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); reset() }
    }
    fun categorySearch(value: String) { _ui.update { it.copy(categorySearch = value) } }
    fun category(value: String) {
        _ui.update { it.copy(query = it.query.copy(category = value), selected = null) }
        detailJob?.cancel()
        reset()
    }
    fun sort(sort: Sort) {
        val direction = when (sort) { Sort.AZ -> Direction.ASC; else -> Direction.DESC }
        _ui.update { it.copy(query = it.query.copy(sort = sort, direction = direction)) }
    }
    fun direction() {
        _ui.update { it.copy(query = it.query.copy(direction = if (it.query.direction == Direction.ASC) Direction.DESC else Direction.ASC)) }
    }
    fun retry() = reset()

    private fun reset() {
        loadJob?.cancel()
        generation++
        cursor = null
        known.clear()
        _ui.update { it.copy(items = emptyList(), loading = true, loadingMore = false, error = "", warnings = emptyList(), hasMore = false) }
        if (_ui.value.query.category == "Favoritos") { publishFavorites(); return }
        load(generation)
    }

    fun loadMore() {
        val state = _ui.value
        if (state.loading || state.loadingMore || !state.hasMore || state.query.category == "Favoritos") return
        _ui.update { it.copy(loadingMore = true) }
        load(generation)
    }

    private fun load(token: Int) {
        loadJob = viewModelScope.launch {
            try {
                // An empty filtered remote page is not the end of a catalog. Bound each batch in the pager.
                var page = graph.pager.load(data.value.addons, scope, _ui.value.query, cursor, known)
                var attempts = 0
                while (token == generation && page.items.isEmpty() && page.nextCursor != null && page.warnings.isEmpty() && attempts++ < 4) {
                    cursor = page.nextCursor
                    page = graph.pager.load(data.value.addons, scope, _ui.value.query, cursor, known)
                }
                if (token != generation) return@launch
                page.items.forEach { known += it.key }
                cursor = page.nextCursor
                _ui.update {
                    it.copy(items = (it.items + page.items).distinctBy { item -> item.key },
                        loading = false, loadingMore = false, hasMore = page.nextCursor != null,
                        warnings = (it.warnings + page.warnings).distinct().take(6))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) _ui.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "Falha ao carregar o catálogo.") }
            }
        }
    }

    private fun publishFavorites() {
        val favorites = data.value.favorites.filter { it.scope == scope &&
            (_ui.value.query.search.isBlank() || foldText(it.name).contains(foldText(_ui.value.query.search))) &&
            (_ui.value.query.category == "Favoritos" || _ui.value.query.category == "Todos" || it.genres.any { g -> foldText(g) == foldText(_ui.value.query.category) }) }
        _ui.update { it.copy(items = favorites, loading = false, loadingMore = false, hasMore = false) }
    }

    fun categories(): List<String> {
        val genres = data.value.addons.filter { it.scope == scope }.flatMap { it.manifest.catalogsFor(scope).flatMap { c -> c.genres } }
        return (genres + _ui.value.items.flatMap { it.genres } + data.value.addons.filter { it.scope == scope }.flatMap { it.manifest.catalogsFor(scope).map { c -> c.displayName } }).distinct().sortedBy { foldText(it) }
    }

    fun select(item: MediaItem) {
        detailJob?.cancel()
        _ui.update { it.copy(selected = item, detailLoading = scope != Scope.CHANNELS, season = null, episode = null) }
        detailJob = viewModelScope.launch {
            try {
                val addon = data.value.addons.firstOrNull { it.url == item.addonUrl }
                val detailed = if (scope == Scope.CHANNELS) graph.guide.refresh(item, data.value.addons)
                    else if (addon?.manifest?.resource("meta", item.type, item.id) != false) graph.api.meta(item) else item
                val season = detailed.seasons.firstOrNull { it > 0 } ?: detailed.seasons.firstOrNull()
                val episode = detailed.videos.filter { it.season == season }.minWithOrNull(compareBy({ it.episode ?: Int.MAX_VALUE }, { it.id }))
                _ui.update { state ->
                    if (state.selected?.key != item.key) state else {
                        state.copy(selected = detailed, detailLoading = false, season = season, episode = episode,
                            items = state.items.map { if (it.key == detailed.key) detailed else it })
                    }
                }
                if (scope == Scope.CHANNELS) updateGuide(detailed)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _ui.update { if (it.selected?.key != item.key) it else it.copy(detailLoading = false, warnings = (it.warnings + "Detalhes indisponíveis: ${e.message ?: "tente novamente"}").take(6)) }
            }
        }
    }

    fun closeDetails() { detailJob?.cancel(); _ui.update { it.copy(selected = null, detailLoading = false) } }
    fun season(value: Int) {
        val item = _ui.value.selected ?: return
        _ui.update { it.copy(season = value, episode = item.videos.filter { v -> v.season == value }.minWithOrNull(compareBy({ v -> v.episode ?: Int.MAX_VALUE }, { v -> v.id }))) }
    }
    fun episode(video: Video) { _ui.update { it.copy(episode = video) } }
    fun toggleFavorite(item: MediaItem) { viewModelScope.launch { graph.repository.favorite(item) } }

    fun ensureGuide(items: List<MediaItem>) {
        if (scope != Scope.CHANNELS) return
        items.forEach { item ->
            if (!guideSeen.add(item.key)) return@forEach
            viewModelScope.launch {
                guideGate.withPermit {
                    try { updateGuide(graph.guide.refresh(item, data.value.addons)) }
                    catch (e: CancellationException) { guideSeen.remove(item.key); throw e }
                    catch (_: Exception) { guideSeen.remove(item.key) }
                }
            }
        }
    }
    fun refreshGuide(item: MediaItem) {
        viewModelScope.launch {
            try { updateGuide(graph.guide.refresh(item, data.value.addons)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _ui.update { it.copy(warnings = (it.warnings + "EPG indisponível: ${e.message ?: "tente novamente"}").take(6)) } }
        }
    }
    private fun updateGuide(item: MediaItem) {
        _ui.update { state -> state.copy(items = state.items.map { if (it.key == item.key) item else it },
            selected = state.selected?.let { if (it.key == item.key) item else it }) }
    }
    override fun onCleared() { loadJob?.cancel(); detailJob?.cancel(); searchJob?.cancel() }
}
