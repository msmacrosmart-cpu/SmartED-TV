package com.smarted.tv.addons.brazuca

import com.smarted.tv.addons.brazuca.BrazucaPlayAddon.Companion.ID
import com.smarted.tv.core.data.SourceRepository
import com.smarted.tv.core.model.CatalogRow
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.core.model.StreamSource
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Catálogo do Brazuca Play.
 *
 * Responsável por baixar/parsear todas as fontes ativas (M3U), deduplicar,
 * derivar categorias, casar qualidade e montar as linhas da home.
 *
 * Estratégia de performance (essencial em TV box):
 *  - download em paralelo com limite de concorrência;
 *  - catálogo em memória (snapshot imutável) com TTL;
 *  - persistência em disco (Room cache) para abrir instantâneo no próximo start;
 *  - deduplicação por `titleKey + qualidade`.
 */
class BrazucaCatalog(
    private val sources: SourceRepository,
    private val http: Http,
    private val cache: com.smarted.tv.core.data.db.CacheDao,
) : com.smarted.tv.ui.state.CatalogGateway {

    data class Snapshot(
        override val live: List<MediaItem> = emptyList(),
        override val movies: List<MediaItem> = emptyList(),
        override val series: List<MediaItem> = emptyList(),
        val loadedAt: Long = 0L,
        override val degraded: Boolean = false,
        override val sourceErrors: List<String> = emptyList(),
    ) : com.smarted.tv.ui.state.CatalogSnapshot {
        override val total: Int get() = live.size + movies.size + series.size
        override val isEmpty: Boolean get() = total == 0

        fun rows(): List<CatalogRow> = buildList {
            if (live.isNotEmpty()) {
                val byGroup = live.groupBy { it.group ?: "Sem categoria" }
                byGroup.entries.sortedByDescending { it.value.size }.take(6).forEach { (group, items) ->
                    add(CatalogRow(group, items.take(30), MediaKind.LIVE))
                }
            }
            if (movies.isNotEmpty()) add(CatalogRow("Filmes", movies.take(30), MediaKind.MOVIE))
            if (series.isNotEmpty()) add(CatalogRow("Séries", series.take(30), MediaKind.SERIES))
        }
    }

    private val mutex = Mutex()
    private val altIndex = ConcurrentHashMap<String, List<String>>()

    private val _snapshot = MutableStateFlow(Snapshot())
    override val snapshotFlow: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _loading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _status = MutableStateFlow("Inicializando catálogo…")
    override val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var sourceUrlsSignature: String = ""

    private val ttlMs = 20 * 60 * 1000L

    fun sourceUrls(): List<String> = sourceUrlsSignature.split('|').filter { it.isNotBlank() }

    /** Snapshot em memória; recarrega quando expirado ou forçado. */
    override suspend fun snapshot(force: Boolean = false): Snapshot {
        val current = _snapshot.value
        val fresh = !force && !current.isEmpty && System.currentTimeMillis() - loadedAt < ttlMs
        if (fresh) return current
        return mutex.withLock {
            val again = _snapshot.value
            if (!force && !again.isEmpty && System.currentTimeMillis() - loadedAt < ttlMs) {
                return@withLock again
            }
            load()
        }
    }

    /** Hidrata do cache em disco na inicialização (abertura instantânea). */
    override suspend fun hydrate(): Snapshot = withContext(Dispatchers.IO) {
        if (!_snapshot.value.isEmpty) return@withContext _snapshot.value
        val entry = cache.get(CACHE_KEY) ?: return@withContext _snapshot.value
        val parsed = runCatching { decode(entry.payload) }.getOrNull() ?: return@withContext _snapshot.value
        loadedAt = entry.updatedAt
        _snapshot.value = parsed
        _status.value = "Catálogo em cache · ${parsed.total} itens"
        parsed
    }

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) {
        _loading.value = true
        _status.value = "Sincronizando fontes…"
        try {
            val enabled = sources.enabled()
            sourceUrlsSignature = enabled.joinToString("|") { it.url }
            if (enabled.isEmpty()) {
                _status.value = "Nenhuma fonte habilitada"
                return@withContext _snapshot.value
            }

            val errors = java.util.Collections.synchronizedList(ArrayList<String>())
            val results = coroutineScope {
                enabled
                    .sortedBy { it.priority }
                    .map { source ->
                        async {
                            try {
                                fetchSource(source)
                            } catch (e: Exception) {
                                errors.add("${source.name}: ${e.message ?: "falha"}")
                                emptyList<MediaItem>()
                            }
                        }
                    }
                    .awaitAll()
            }

            val flat = results.flatten()
            val live = dedupe(flat.filter { it.kind == MediaKind.LIVE })
            val movies = dedupe(flat.filter { it.kind == MediaKind.MOVIE })
            val series = dedupe(flat.filter { it.kind == MediaKind.SERIES })

            val snapshot = Snapshot(
                live = live.sortedWith(compareBy({ it.group ?: "~" }, { TextUtils.titleKey(it.title) })),
                movies = movies.sortedBy { TextUtils.titleKey(it.title) },
                series = series.sortedBy { TextUtils.titleKey(it.title) },
                loadedAt = System.currentTimeMillis(),
                degraded = errors.isNotEmpty(),
                sourceErrors = errors.toList(),
            )
            loadedAt = snapshot.loadedAt
            _snapshot.value = snapshot
            _status.value = buildString {
                append("${snapshot.live.size} canais · ${snapshot.movies.size} filmes · ${snapshot.series.size} séries")
                if (errors.isNotEmpty()) append(" · ${errors.size} fonte(s) com erro")
            }
            persist(snapshot)
            snapshot
        } finally {
            _loading.value = false
        }
    }

    private suspend fun fetchSource(source: PublicSource): List<MediaItem> {
        if (source.format == com.smarted.tv.core.model.SourceFormat.XMLTV) return emptyList()
        val body = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            http.getText(source.url, cacheable = true, timeoutSeconds = 45)
        } ?: throw IllegalStateException("timeout")
        val parsed = M3uParser.parse(body, addonId = ID, defaultKind = source.kind)
        val items = parsed.items.map { item ->
            val quality = item.quality ?: M3uParser.inferQuality(item.title)
            item.copy(
                addonId = ID,
                group = item.group ?: source.group ?: source.name,
                quality = quality,
                poster = item.poster?.takeIf { it.startsWith("http") },
                logo = item.poster?.takeIf { it.startsWith("http") },
            )
        }
        // guarda alternativas (mirrors) por chave de mídia
        items.groupBy { mediaKey(it) }.forEach { (key, list) ->
            val urls = list.mapNotNull { it.streamUrl }.distinct()
            if (urls.size > 1) altIndex[key] = urls
        }
        return items
    }

    private fun dedupe(items: List<MediaItem>): List<MediaItem> {
        val seen = HashMap<String, MediaItem>(items.size)
        for (item in items) {
            val key = mediaKey(item)
            val existing = seen[key]
            if (existing == null) {
                seen[key] = item
            } else {
                // prefere o item com arte/qualidade melhor
                val better = when {
                    existing.poster == null && item.poster != null -> item
                    existing.quality == null && item.quality != null -> item
                    else -> existing
                }
                seen[key] = better
                val urls = (altIndex[key] ?: emptyList()) + listOfNotNull(existing.streamUrl, item.streamUrl)
                altIndex[key] = urls.distinct()
            }
        }
        return seen.values.toList()
    }

    /** Chave canônica usada para deduplicação e para buscar mirrors. */
    fun mediaKey(item: MediaItem): String {
        val title = TextUtils.titleKey(item.title.ifBlank { item.subtitle ?: "" })
        val qual = item.quality ?: ""
        return "${item.kind.name}|$title|$qual"
    }

    fun alternatives(item: MediaItem): List<StreamSource> {
        val urls = altIndex[mediaKey(item)] ?: listOfNotNull(item.streamUrl)
        return alternativesFor(item, urls)
    }

    fun resolveStream(item: MediaItem): String? {
        item.streamUrl?.let { return it }
        return altIndex[mediaKey(item)]?.firstOrNull()
    }

    /** Remove item do catálogo em memória (após verificação de link morto). */
    suspend fun dropItem(refKey: String) {
        val current = _snapshot.value
        fun List<MediaItem>.filtered() = filterNot { it.ref.key == refKey }
        _snapshot.value = current.copy(
            live = current.live.filtered(),
            movies = current.movies.filtered(),
            series = current.series.filtered(),
        )
    }

    override fun invalidate() {
        loadedAt = 0L
        altIndex.clear()
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        cache.purgeAddon(ID)
        altIndex.clear()
        _snapshot.value = Snapshot()
        loadedAt = 0L
    }

    // ---------------------------------------------------------- persistência

    private suspend fun persist(snapshot: Snapshot) {
        runCatching {
            cache.put(
                com.smarted.tv.core.data.db.CatalogCacheEntity(
                    key = CACHE_KEY,
                    addonId = ID,
                    kind = "SNAPSHOT",
                    payload = encode(snapshot),
                    updatedAt = snapshot.loadedAt,
                    ttlMs = 24 * 60 * 60 * 1000L,
                ),
            )
        }
    }

    private fun encode(snapshot: Snapshot): String = buildString {
        append("{\"loadedAt\":").append(snapshot.loadedAt)
        append(",\"degraded\":").append(snapshot.degraded)
        append(",\"live\":[")
        appendItems(snapshot.live, "LIVE")
        append("],\"movies\":[")
        appendItems(snapshot.movies, "MOVIE")
        append("],\"series\":[")
        appendItems(snapshot.series, "SERIES")
        append("]}")
    }

    private fun StringBuilder.appendItems(items: List<MediaItem>, kind: String) {
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('{')
            append("\"k\":\"").append(kind).append("\",")
            append("\"id\":\"").append(esc(item.id)).append("\",")
            append("\"t\":\"").append(esc(item.title)).append("\",")
            append("\"s\":\"").append(esc(item.subtitle ?: "")).append("\",")
            append("\"p\":\"").append(esc(item.poster ?: "")).append("\",")
            append("\"g\":\"").append(esc(item.group ?: "")).append("\",")
            append("\"u\":\"").append(esc(item.streamUrl ?: "")).append("\",")
            append("\"q\":\"").append(esc(item.quality ?: "")).append("\",")
            append("\"tv\":\"").append(esc(item.tvgId ?: "")).append("\",")
            append("\"r\":").append(item.rating ?: 0.0)
            append('}')
        }
    }

    private fun decode(raw: String): Snapshot {
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return Snapshot()

        fun arr(key: String, kind: MediaKind): List<MediaItem> {
            val a = root[key] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return a.mapNotNull { el ->
                (el as? kotlinx.serialization.json.JsonObject)?.let { itemFrom(it, kind) }
            }
        }
        return Snapshot(
            live = arr("live", MediaKind.LIVE),
            movies = arr("movies", MediaKind.MOVIE),
            series = arr("series", MediaKind.SERIES),
            loadedAt = (root["loadedAt"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toLongOrNull() ?: 0L,
        )
    }

    private fun itemFrom(o: kotlinx.serialization.json.JsonObject, kind: MediaKind): MediaItem? {
        fun s(key: String): String? =
            (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
        val id = s("id") ?: return null
        val title = s("t") ?: return null
        return MediaItem(
            id = id,
            addonId = ID,
            kind = kind,
            title = title,
            subtitle = s("s"),
            poster = s("p"),
            group = s("g"),
            streamUrl = s("u"),
            quality = s("q"),
            tvgId = s("tv"),
            rating = (o["r"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull(),
        )
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    companion object {
        private const val CACHE_KEY = "brazuca.snapshot.v2"
        private const val FETCH_TIMEOUT_MS = 60_000L
    }
}
