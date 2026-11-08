package com.smarted.tv.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.AppGraph
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.ChannelRow
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.LiveChannel
import com.smarted.tv.core.model.PlaybackTarget
import com.smarted.tv.core.model.Programme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelsUi(
    val query: BrowseQuery = BrowseQuery(),
    val groups: List<Pair<String, Int>> = emptyList(),
    val rows: List<ChannelRow> = emptyList(),
    val totalRows: Int = 0,
    val page: Int = 0,
    val loading: Boolean = false,
    val selected: LiveChannel? = null,
    val selectedNow: Programme? = null,
    val upcoming: List<Programme> = emptyList(),
    val previewTarget: PlaybackTarget? = null,
    val message: String = "",
    val sourceFilter: ListSource? = null,
    val categorySearch: String = "",
)

class ChannelsViewModel(private val graph: AppGraph) : ViewModel() {

    private val _ui = MutableStateFlow(ChannelsUi())
    val ui = _ui.asStateFlow()

    init { reload(firstPageOnly = false) }

    private fun fail(message: String?) = _ui.update { it.copy(loading = false, message = message ?: "Falha ao ler canais.") }

    /** Recarga categorias + página visível; chamado na abertura e após cada escrita. */
    fun reload(firstPageOnly: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val data = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val allGroups = graph.catalog.channelGroups(_ui.value.sourceFilter?.id)
                val groups = allGroups.filter { it.first.isBlank() || com.smarted.tv.core.text.TextNormalizer.searchMatches(_ui.value.categorySearch, it.first) }
                val page = graph.catalog.channelPage(
                    query = _ui.value.query, sourceId = _ui.value.sourceFilter?.id, page = if (firstPageOnly) _ui.value.page else 0,
                    nowMs = now, windowHours = graph.currentPreferences.epgWindowHours,
                )
                // Painéis Xtream sem XMLTV usam get_short_epg para o canal selecionado nunca ficar cego.
                val unlinked = page.items.map { it.channel }.filter { graph.store.epgKeysFor(listOf(it.id)).isEmpty() }
                val xtreamSource = unlinked.map { it.sourceId }.distinct().mapNotNull { graph.store.sourceById(it) }
                    .firstOrNull { it.kind == com.smarted.tv.core.model.SourceKind.XTREAM }
                val fallback = if (xtreamSource != null) {
                    graph.catalog.shortEpgFallback(xtreamSource, unlinked.filter { it.sourceId == xtreamSource.id }, now)
                } else emptyMap()
                val rows = page.items.map { row -> graph.catalog.rowWithShortEpg(row, fallback[row.channel.id], now) }
                Triple(groups, page.copy(items = rows), page.hasMore)
            } }.onFailure { fail(it.message) }.getOrNull() ?: run { _ui.update { it.copy(loading = false) }; return@launch }
            _ui.update {
                it.copy(
                    groups = data.first, rows = data.second.items, totalRows = data.second.total,
                    loading = false, page = if (firstPageOnly) it.page else 0,
                )
            }
            refreshSelected()
        }
    }

    fun setQuery(transform: (BrowseQuery) -> BrowseQuery) {
        _ui.update { it.copy(query = transform(it.query)) }
        reload(firstPageOnly = false)
    }

    fun setCategorySearch(text: String) { _ui.update { it.copy(categorySearch = text) } }

    fun loadMore() {
        if (_ui.value.loading) return
        val current = _ui.value
        if (current.rows.size >= current.totalRows) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, page = it.page + 1) }
            val page = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                graph.catalog.channelPage(current.query, current.sourceFilter?.id, _ui.value.page, System.currentTimeMillis(), graph.currentPreferences.epgWindowHours)
            } }.getOrNull() ?: run { _ui.update { it.copy(loading = false) }; return@launch }
            _ui.update { it.copy(rows = it.rows + page.items, loading = false) }
        }
    }

    fun select(channel: LiveChannel) {
        _ui.update { it.copy(selected = channel) }
        refreshSelected()
        if (graph.currentPreferences.previewPlayerEnabled) {
            _ui.update {
                it.copy(
                    previewTarget = PlaybackTarget(
                        url = channel.streamUrl, title = channel.name, headers = channel.headers, isLive = true,
                    )
                )
            }
        }
    }

    private fun refreshSelected() {
        val channel = _ui.value.selected ?: return
        viewModelScope.launch {
            val data = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val nowNext = graph.catalog.nowNextFor(channel, System.currentTimeMillis())
                val upcoming = graph.catalog.upcomingFor(channel, System.currentTimeMillis(), graph.currentPreferences.epgWindowHours, limit = 12)
                nowNext to upcoming
            } }.getOrNull() ?: return@launch
            _ui.update { it.copy(selectedNow = data.first.now, upcoming = data.second) }
        }
    }

    fun toggleFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                graph.catalog.toggleChannelFavorite(channel)
            }
            _ui.update {
                it.copy(
                    message = if (added) "${channel.name} nos favoritos." else "${channel.name} removido dos favoritos.",
                    rows = it.rows.map { row -> if (row.channel.id == channel.id) row.copy(isFavorite = added) else row },
                )
            }
        }
    }

    fun playbackTarget(channel: LiveChannel): PlaybackTarget = PlaybackTarget(
        url = channel.streamUrl, title = channel.name, headers = channel.headers, isLive = true,
    )
}
