package com.smarted.tv.data

import com.smarted.tv.core.epg.EpgTimeline
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.ChannelRow
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.LiveChannel
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.NowNext
import com.smarted.tv.core.model.Page
import com.smarted.tv.core.model.Programme
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.data.db.IptvStore
import com.smarted.tv.data.import.XtreamClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Leitura de catálogo para as telas: tudo indexado no SQLite local, com fallback de
 * short-EPG do painel Xtream quando o XMLTV não cobre um canal (nunca deixa vazio sem motivo).
 */
class CatalogRepository(private val store: IptvStore) {

    private data class ShortEpgCache(val programmes: List<Programme>, val fetchedAtMs: Long)
    private val shortCache = ConcurrentHashMap<String, ShortEpgCache>()

    fun channelGroups(sourceId: Long?): List<Pair<String, Int>> = store.channelGroups(sourceId)

    fun channelPage(query: BrowseQuery, sourceId: Long?, page: Int, nowMs: Long, windowHours: Int): Page<ChannelRow> {
        val offset = page.coerceAtLeast(0) * Page.SIZE
        val favoritesOnly = query.isFavorites
        val category = if (query.isAll || query.isFavorites) null else query.category
        val items = store.channels(sourceId, category, query.search.ifBlank { null }, Page.SIZE, offset, favoritesOnly)
        val total = store.countChannels(sourceId, category, query.search.ifBlank { null }, favoritesOnly)
        val epgKeys = store.epgKeysFor(items.map { it.id })
        val timeline = store.timelineFor(epgKeys.values.toList(), nowMs, nowMs + windowHours * 60L * 60_000L)
        val rows = EpgTimeline.rows(
            channels = items,
            epgKeys = epgKeys,
            timeline = timeline,
            favoriteKeys = store.favoriteKeys("channel"),
            nowMs = nowMs,
        )
        return Page(rows, total, offset + items.size < total)
    }

    /** Combina timeline do banco (XMLTV) com cache short-EPG quando o canal não tem vínculo. */
    fun rowWithShortEpg(row: ChannelRow, fallback: List<Programme>?, nowMs: Long): ChannelRow {
        if (row.now != null || fallback.isNullOrEmpty()) return row
        val timeline = fallback.sortedBy { it.startMs }
        val now = EpgTimeline.nowNext(timeline, nowMs)
        return row.copy(now = now.now, next = now.next, progressPercent = now.progressPercent)
    }

    fun timelineFor(channel: LiveChannel, nowMs: Long, windowHours: Int): List<Programme> {
        val epgKey = store.epgKeysFor(listOf(channel.id))[channel.id] ?: return emptyList()
        return store.timelineFor(listOf(epgKey), nowMs, nowMs + windowHours * 60L * 60_000L)[epgKey].orEmpty()
    }

    fun upcomingFor(channel: LiveChannel, nowMs: Long, windowHours: Int, limit: Int): List<Programme> =
        EpgTimeline.upcoming(timelineFor(channel, nowMs, windowHours), nowMs, limit)

    /** Fornece short-EPG para canais visíveis sem XMLTV (painéis Xtream). */
    suspend fun shortEpgFallback(source: ListSource?, channels: List<LiveChannel>, nowMs: Long): Map<Long, List<Programme>> {
        if (source?.kind != SourceKind.XTREAM) return emptyMap()
        val client = XtreamClient(source)
        val result = HashMap<Long, List<Programme>>()
        for (channel in channels.take(60)) {
            val streamId = extractXtreamStreamId(channel.streamUrl) ?: continue
            val cached = shortCache[streamId]
            if (cached != null && nowMs - cached.fetchedAtMs < SHORT_TTL_MS) {
                if (cached.programmes.isNotEmpty()) result[channel.id] = cached.programmes
                continue
            }
            val programmes = runCatching { client.shortEpg(streamId) }.getOrDefault(emptyList())
            shortCache[streamId] = ShortEpgCache(programmes, nowMs)
            if (programmes.isNotEmpty()) result[channel.id] = programmes
        }
        return result
    }

    private fun extractXtreamStreamId(url: String): String? {
        val marker = "/live/"
        val index = url.indexOf(marker)
        if (index < 0) return null
        return url.substring(index + marker.length).trimEnd('/').substringAfterLast('/').substringBefore('.').takeIf(String::isNotBlank)
    }

    fun nowNextFor(channel: LiveChannel, nowMs: Long): NowNext {
        val epgKey = store.epgKeysFor(listOf(channel.id))[channel.id] ?: return NowNext(null, null, 0)
        val timeline = store.timelineFor(listOf(epgKey), nowMs, nowMs + 48L * 60L * 60_000L)[epgKey].orEmpty()
        return EpgTimeline.nowNext(timeline.sortedBy { it.startMs }, nowMs)
    }

    fun toggleChannelFavorite(channel: LiveChannel): Boolean =
        store.toggleFavorite("channel", channel.favKey, channel.name)

    fun toggleVodFavorite(entry: VodEntry): Boolean =
        store.toggleFavorite(if (entry.kind == Tab.SERIES) "series" else "movie", entry.favKey, entry.name)

    fun favoriteKeys(kind: String): Set<String> = store.favoriteKeys(kind)

    fun favoriteChannels(): List<LiveChannel> = store.favoriteChannels()

    fun vodCategories(kind: Tab, search: String?): List<Pair<String, Int>> = store.vodCategories(kind, search)

    fun vodPage(kind: Tab, query: BrowseQuery, page: Int): Page<VodEntry> {
        val offset = page.coerceAtLeast(0) * Page.SIZE
        val favoritesOnly = query.isFavorites
        val category = if (query.isAll || query.isFavorites) null else query.category
        val items = store.vodPage(
            kind = kind, category = category, search = query.search.ifBlank { null },
            sort = query.sort, direction = query.direction, limit = Page.SIZE, offset = offset, favoritesOnly = favoritesOnly,
        )
        val total = store.countVod(kind, category, query.search.ifBlank { null }, favoritesOnly)
        return Page(items, total, offset + items.size < total)
    }

    fun vodById(id: Long): VodEntry? = store.vodById(id)
    fun vodByFavKey(favKey: String): VodEntry? = store.vodByFavKey(favKey)
    fun channelById(id: Long): LiveChannel? = store.channelById(id)
    fun channelsByIds(ids: List<Long>): List<LiveChannel> = store.channelsByIds(ids)
    fun episodes(vodId: Long): List<Episode> = store.episodesFor(vodId)
    fun sourceOf(sourceId: Long): ListSource? = store.sourceById(sourceId)

    fun saveProgress(favKey: String, title: String, position: Long, duration: Long, kind: String, url: String, isLive: Boolean) {
        if (favKey.isBlank()) return
        store.saveProgress(IptvStore.ProgressRecord(favKey, title, position, duration, System.currentTimeMillis(), kind, url, isLive))
    }

    fun progressFor(favKey: String): com.smarted.tv.core.model.PlaybackProgress? = store.progressFor(favKey)?.let {
        com.smarted.tv.core.model.PlaybackProgress(it.positionMs, it.durationMs, it.updatedMs)
    }

    fun continueWatching(): List<IptvStore.ProgressRecord> = store.recentProgress()

    fun recentlyAdded(kind: Tab, limit: Int): List<VodEntry> =
        store.vodPage(kind, null, null, com.smarted.tv.core.model.SortMode.ADDED, com.smarted.tv.core.model.SortDirection.DESC, limit, 0, false)

    fun topRated(kind: Tab, limit: Int): List<VodEntry> =
        store.vodPage(kind, null, null, com.smarted.tv.core.model.SortMode.RATING, com.smarted.tv.core.model.SortDirection.DESC, limit, 0, false)

    fun counts(): Map<Tab, Int> = mapOf(
        Tab.CHANNELS to store.countChannels(null, null, null, false),
        Tab.MOVIES to store.countVod(Tab.MOVIES, null, null, false),
        Tab.SERIES to store.countVod(Tab.SERIES, null, null, false),
    )

    fun programCount(): Int = store.programmeCount()

    fun wipeCaches() {
        store.wipeCatalogs()
        shortCache.clear()
    }

    companion object {
        private const val SHORT_TTL_MS = 3 * 60_000L
    }
}
