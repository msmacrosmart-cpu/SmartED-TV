package com.smarted.tv.core.data

import com.smarted.tv.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementa [ContentRepository] sobre o backend real do SmartED-TV
 * (IptvStore + CatalogRepository + SourceManager). As telas nunca sabem que
 * por baixo há SQLite, Xtream, M3U ou repositório OnePlay.
 *
 * Ids expostos à UI: `"c:" + favKey` (canal), `"m:"` (filme), `"s:"` (série).
 */
class IptvContentRepository(
    private val graph: com.smarted.tv.AppGraph,
    scope: CoroutineScope,
) : ContentRepository {

    private val io = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val store get() = graph.store
    private val catalog get() = graph.catalog

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    override val favoriteIds: StateFlow<Set<String>> = _favorites.asStateFlow()

    override val revision: StateFlow<Int> = graph.sources.revision

    private val _busy = MutableStateFlow(false)
    override val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        scope.launch {
            runCatching { refreshFavorites() }
            // Sincronizações (manuais ou do agendador) atualizam favoritos e derrubam caches.
            launch { graph.sources.busy.collect { _busy.value = it.isBusy } }
            graph.sources.revision.collect { runCatching { withContext(Dispatchers.IO) { refreshFavorites() } } }
        }
    }

    private fun refreshFavorites() {
        val chans = store.favoriteKeys("channel").map { "$CHAN$it" }
        val movies = store.favoriteKeys("movie").map { "$MOVIE$it" }
        val series = store.favoriteKeys("series").map { "$SERIES$it" }
        _favorites.value = (chans + movies + series).toSet()
    }

    override fun toggleFavorite(id: String) {
        val scopeTag = id.take(2)
        val key = id.removePrefix(scopeTag)
        scopeTagFavorite(scopeTag, key)
    }

    private fun scopeTagFavorite(scopeTag: String, key: String) {
        io.launch {
            when (scopeTag) {
                CHAN -> {
                    val ch = store.channelByFavKey(key)
                    if (ch != null) runCatching { catalog.toggleChannelFavorite(ch) }
                    else store.toggleFavorite("channel", key, "")
                }
                MOVIE, SERIES -> {
                    val entry = store.vodByFavKey(key)
                    if (entry != null) runCatching { catalog.toggleVodFavorite(entry) }
                    else store.toggleFavorite(if (scopeTag == SERIES) "series" else "movie", key, "")
                }
            }
            runCatching { refreshFavorites() }
        }
    }

    // ---------------------------------------------------------------- Canais

    override suspend fun channelCategories(): List<Category> = withContext(Dispatchers.IO) {
        store.channelGroups(null)
            .map { Category(id = it.first, name = it.first) }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Lista COMPLETA de canais com "agora/próximo" embutido — a tela filtra/busca em
     * memória. O EPG é resolvido em duas consultas agregadas (nunca por canal).
     */
    override suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val chans = store.channels(null, null, null, MAX_CHANNELS, 0, false)
        val keys = store.allEpgKeys()
        val distinct = keys.values.distinct()
        val timelines = buildMap {
            distinct.chunked(500).forEach { part ->
                putAll(store.timelineFor(part, now, now + EPG_HORIZON_MS))
            }
        }
        chans.map { ch ->
            val timeline = keys[ch.id]?.let { timelines[it] }.orEmpty()
            val current = timeline.firstOrNull { it.startMs <= now && it.stopMs > now }
            val next = timeline.firstOrNull { it.startMs > now }
            Channel(
                id = CHAN + ch.favKey,
                number = ch.number ?: 0,
                name = ch.name,
                logoUrl = ch.logoUrl,
                categoryId = ch.groupName,
                streamUrl = ch.streamUrl,
                epg = listOfNotNull(current, next).map {
                    EpgProgram(it.title, it.startMs, it.stopMs, it.description.ifBlank { null })
                },
                headers = ch.headers,
                favKey = ch.favKey,
            )
        }
    }

    override suspend fun channelUpcoming(channelId: String, nowMs: Long, limit: Int): List<EpgProgram> =
        withContext(Dispatchers.IO) {
            val ch = store.channelByFavKey(channelId.removePrefix(CHAN)) ?: return@withContext emptyList()
            catalog.upcomingFor(ch, nowMs, 48, limit).map {
                EpgProgram(it.title, it.startMs, it.stopMs, it.description.ifBlank { null })
            }
        }

    // ------------------------------------------------------------------- VOD

    override suspend fun vodCategories(kind: VodKind): List<Category> = withContext(Dispatchers.IO) {
        store.vodCategories(kind.toTab(), null)
            .map { Category(id = it.first, name = it.first) }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun vod(kind: VodKind): List<VodItem> = withContext(Dispatchers.IO) {
        store.vodPage(kind.toTab(), null, null, SortMode.NAME, SortDirection.ASC, MAX_VOD, 0, false)
            .map { it.toUiItem(kind) }
    }

    /** Temporadas de uma série: usa o cache do banco; busca do painel/M3U se vazio. */
    override suspend fun loadSeasons(vodId: String): List<Season> = withContext(Dispatchers.IO) {
        val key = vodId.removePrefix(SERIES)
        val entry = store.vodByFavKey(key) ?: return@withContext emptyList()
        var episodes = store.episodesFor(entry.id)
        if (episodes.isEmpty()) {
            val source = store.sourceById(entry.sourceId)
            if (source != null && entry.externalKey.isNotBlank()) {
                runCatching { graph.importers.loadSeriesEpisodes(entry.id, entry.externalKey, source) }
                episodes = store.episodesFor(entry.id)
            }
        }
        episodes.groupBy { it.season }.toSortedMap()
            .map { (seasonNo, list) ->
                Season(seasonNo, list.sortedBy { it.episode }.map { it.toUiEpisode(key) })
            }
    }

    // --------------------------------------------------------------- helpers

    private fun VodEntry.toUiItem(kind: VodKind) = VodItem(
        id = (if (kind == VodKind.SERIES) SERIES else MOVIE) + favKey,
        kind = kind,
        title = name,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        categoryId = category,
        year = year,
        rating = rating,
        addedAtMs = addedMs,
        durationMin = runtimeMinutes,
        genres = genre.split(',').map { it.trim() }.filter { it.isNotBlank() },
        plot = plot,
        streamUrl = streamUrl,
        headers = emptyMap(),
        favKey = favKey,
    )

    private fun StoredEpisode.toUiEpisode(seriesKey: String) = Episode(
        id = "e$seriesKey#$id",
        number = episode,
        title = title,
        plot = plot,
        durationMin = runtimeMinutes,
        streamUrl = streamUrl,
    )

    private fun VodKind.toTab(): Tab = if (this == VodKind.SERIES) Tab.SERIES else Tab.MOVIES

    private companion object {
        const val CHAN = "c:"
        const val MOVIE = "m:"
        const val SERIES = "s:"
        const val MAX_CHANNELS = 20_000
        const val MAX_VOD = 40_000
        const val EPG_HORIZON_MS = 12L * 60L * 60_000L
    }
}
