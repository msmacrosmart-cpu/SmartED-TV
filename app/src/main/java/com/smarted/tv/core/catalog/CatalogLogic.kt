package com.smarted.tv.core.catalog

import com.smarted.tv.core.model.*
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

fun foldText(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").trim()

data class SourceProgress(val skip: Long = 0, val done: Boolean = false, val fingerprint: String = "")
data class PageCursor(val sources: List<SourceProgress>, val index: Int = 0) {
    val complete: Boolean get() = index >= sources.size || sources.all { it.done }
}

/** Raw response lengths drive skip; deduplication must never change a server offset. */
class CatalogPager(private val api: com.smarted.tv.core.protocol.StremioApi) {
    suspend fun load(addons: List<InstalledAddon>, scope: Scope, query: CatalogQuery, cursor: PageCursor?, known: Set<String>): CatalogPage {
        val sources = addons.filter { it.scope == scope }.flatMap { addon ->
            addon.manifest.catalogsFor(scope).map { addon to it }
        }
        if (sources.isEmpty()) return CatalogPage(emptyList(), null)
        val progress = cursor?.sources?.toMutableList() ?: MutableList(sources.size) { SourceProgress() }
        var index = cursor?.index ?: 0
        val unique = linkedMapOf<String, MediaItem>()
        val warnings = mutableListOf<String>()
        var requests = 0
        while (index < sources.size && unique.size < 48 && requests < 8) {
            val (addon, catalog) = sources[index]
            val state = progress[index]
            if (state.done) { index++; continue }
            val extra = linkedMapOf<String, String>()
            if (query.search.isNotBlank() && catalog.searchable) extra["search"] = query.search.trim()
            val genre = catalog.extra("genre")
            if (query.category !in setOf("Todos", "Favoritos") && genre != null && query.category in genre.options) extra["genre"] = query.category
            val date = catalog.extra("date")
            if (date?.required == true) extra["date"] = java.time.LocalDate.now().toString()
            val missing = catalog.extra.filter { it.required && it.name !in extra }
            if ((catalog.searchOnly && query.search.isBlank()) || missing.isNotEmpty()) {
                if (missing.isNotEmpty()) warnings += "${addon.manifest.name} / ${catalog.displayName}: exige ${missing.joinToString { it.name }}; catálogo não solicitado com parâmetros incompletos."
                progress[index] = state.copy(done = true)
                index++
                continue
            }
            if (state.skip > 0) extra["skip"] = state.skip.toString()
            requests++
            try {
                val raw = api.catalog(addon, catalog, extra)
                val fingerprint = raw.joinToString("|") { it.key }
                val repeated = state.skip > 0 && raw.isNotEmpty() && fingerprint == state.fingerprint
                if (repeated) warnings += "${addon.manifest.name}: paginação repetida interrompida para evitar um loop."
                raw.filter { matches(it, query) }.forEach { if (it.key !in known) unique.putIfAbsent(it.key, it) }
                progress[index] = SourceProgress(state.skip + raw.size,
                    raw.isEmpty() || !catalog.paged || repeated, fingerprint)
                index++
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                warnings += "${addon.manifest.name} / ${catalog.displayName}: ${e.message ?: "falha ao carregar"}"
                progress[index] = state.copy(done = true)
                index++
            }
        }
        // One response per catalog per batch: a small page must not silently prefetch
        // the next page and break the UI's visible infinite-scroll boundary.
        val start = cursor?.index ?: 0
        val remaining = progress.drop(start).indexOfFirst { !it.done }
        val nextIndex = if (remaining < 0) sources.size else start + remaining
        val next = PageCursor(progress.toList(), nextIndex).takeUnless { it.complete }
        return CatalogPage(unique.values.toList(), next, warnings.distinct())
    }

    private fun matches(item: MediaItem, query: CatalogQuery): Boolean {
        return item.scope == Scope.fromWire(item.type) &&
            (query.search.isBlank() || foldText(item.name).contains(foldText(query.search))) &&
            (query.category in setOf("Todos", "Favoritos") || item.genres.any { foldText(it) == foldText(query.category) } || item.catalogName == query.category)
    }
}

/** Sorts the loaded window. The protocol has no universal server-side sort or vote-count field. */
fun sortItems(items: List<MediaItem>, sort: Sort, direction: Direction): List<MediaItem> {
    val comparator: Comparator<MediaItem> = when (sort) {
        Sort.AZ -> compareBy<MediaItem> { foldText(it.name) }.thenBy { it.key }
        Sort.ADDED -> compareBy<MediaItem> { it.addedAt ?: releaseMillis(it.released) ?: 0L }.thenBy { it.catalogPosition }
        Sort.RATING -> compareBy<MediaItem> { it.rating ?: -1.0 }.thenBy { foldText(it.name) }
        Sort.YEAR -> compareBy<MediaItem> { it.year ?: Int.MIN_VALUE }.thenBy { foldText(it.name) }
    }
    return items.sortedWith(if (direction == Direction.DESC) comparator.reversed() else comparator)
}

private fun releaseMillis(value: String?): Long? = value?.let {
    runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
}

data class ProgramWindow(val current: Video?, val upcoming: List<Video>)
fun programWindow(videos: List<Video>, now: Instant): ProgramWindow {
    fun instant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val sorted = videos.mapNotNull { video ->
        val start = instant(video.startTime)
        val end = instant(video.endTime)
        if (start != null && end != null && end > start) Triple(video, start, end) else null
    }.sortedBy { it.second }
    return ProgramWindow(sorted.firstOrNull { (_, start, end) -> !now.isBefore(start) && now.isBefore(end) }?.first,
        sorted.filter { it.second > now }.map { it.first })
}
