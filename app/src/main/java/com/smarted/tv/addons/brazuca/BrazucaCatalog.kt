package com.smarted.tv.addons.brazuca

import com.smarted.tv.addons.brazuca.BrazucaPlayAddon.Companion.ID
import com.smarted.tv.core.data.SourceRepository
import com.smarted.tv.core.model.CatalogRow
import com.smarted.tv.core.model.DemoVod
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.SourceTargeting
import com.smarted.tv.core.model.PublicSource
import com.smarted.tv.core.model.StreamSource
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.ContentClassifier
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.SeriesGrouper
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
    private val settings: com.smarted.tv.core.data.SettingsStore? = null,
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
    override suspend fun snapshot(force: Boolean): Snapshot {
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
            // Cada aba escolhe o seu modo: Normal (lista pública), SmartED
            // (pública + repositórios) ou Repo (só repositórios).
            val enabled = configuredSources()
                .distinctBy { "${it.url}|${it.targets.joinToString(",")}" }
            sourceUrlsSignature = enabled.joinToString("|") { "${it.kind}:${it.url}" }
            if (enabled.isEmpty()) {
                _status.value = "Nenhuma fonte habilitada"
                val empty = applyDemoFallback(Snapshot())
                _snapshot.value = empty
                return@withContext empty
            }

            val errors = java.util.Collections.synchronizedList(ArrayList<String>())
            val results = coroutineScope {
                enabled
                    .sortedBy { it.priority }
                    .map { source ->
                        async {
                            try {
                                source to fetchSource(source)
                            } catch (e: Exception) {
                                errors.add("${source.name}: ${e.message ?: "falha"}")
                                source to emptyList<MediaItem>()
                            }
                        }
                    }
                    .awaitAll()
            }

            val brOnly = settings?.settings?.portugueseOnly ?: true
            var totalRaw = 0
            var totalAccepted = 0

            // Filtro em duas etapas por aba:
            //  1. tipo de conteúdo (ao vivo × filme × série) — o classificador
            //     recusa canal ao vivo na aba de filmes e vice-versa;
            //  2. idioma (PT-BR), quando `portugueseOnly` está ligado.
            fun accept(fetched: List<Pair<PublicSource, List<MediaItem>>>): List<MediaItem> = buildList {
                for ((source, items) in fetched) {
                    // Uma fonte pode servir a mais de uma aba; cada destino
                    // reclassifica os itens com o seu próprio contrato.
                    for (kind in source.targets) {
                        for (raw in items) {
                            totalRaw++
                            val typed = applyKind(kind, raw) ?: continue
                            if (brOnly && !isPortuguese(typed)) continue
                            totalAccepted++
                            add(typed)
                        }
                    }
                }
            }

            val typed = accept(results)
            filterStats = FilterStats(
                total = totalRaw,
                accepted = totalAccepted,
                rejected = totalRaw - totalAccepted,
            )

            val live = dedupe(typed.filter { it.kind == MediaKind.LIVE })
            val movies = dedupe(typed.filter { it.kind == MediaKind.MOVIE })
            // Séries são agrupadas em temporadas/episódios: a lista traz cada
            // episódio numa linha e a aba precisa mostrar a série.
            val series = SeriesGrouper.group(typed.filter { it.kind == MediaKind.SERIES }, ID)

            val snapshot = applyDemoFallback(
                Snapshot(
                    live = live.sortedWith(compareBy({ it.group ?: "~" }, { TextUtils.titleKey(it.title) })),
                    movies = movies.sortedBy { TextUtils.titleKey(it.title) },
                    series = series.sortedBy { TextUtils.titleKey(it.title) },
                    loadedAt = System.currentTimeMillis(),
                    degraded = errors.isNotEmpty(),
                    sourceErrors = errors.toList(),
                ),
            )
            loadedAt = snapshot.loadedAt
            _snapshot.value = snapshot
            _status.value = buildString {
                append("${snapshot.live.size} canais · ${snapshot.movies.size} filmes · ${snapshot.series.size} séries")
                val episodeCount = snapshot.series.sumOf { it.number ?: 0 }
                if (episodeCount > 0) append(" ($episodeCount episódios)")
                if (filterStats.rejected > 0) {
                    append(" · ${filterStats.rejected} item(ns) fora do tipo/idioma")
                }
                if (errors.isNotEmpty()) append(" · ${errors.size} fonte(s) com erro")
            }
            persist(snapshot)
            snapshot
        } finally {
            _loading.value = false
        }
    }


    /** Contadores do filtro de idioma, para diagnóstico na UI/Ajustes. */
    data class FilterStats(val total: Int = 0, val accepted: Int = 0, val rejected: Int = 0)

    /** Cache curto do corpo baixado, para não repetir download da mesma lista. */
    private val bodyCache = ConcurrentHashMap<String, String>()

    @Volatile
    var filterStats: FilterStats = FilterStats()
        private set

    /** Abas de conteúdo do app (a "Biblioteca" não tem acervo próprio). */
    private val KINDS = listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES)

    /**
     * Fontes do catálogo — **somente repositórios e addons**.
     *
     * A arquitetura mudou: listas M3U avulsas saíram de cena. Cada aba é
     * alimentada pelo addon embutido do app (que carrega o catálogo que
     * acompanha os assets) e pelos repositórios cujas pastas o usuário
     * conectou no navegador. Addons Kodi são Python e não executam no Android:
     * o repositório entrega a **estrutura** (TV ao Vivo, Filmes, Séries) e o
     * addon embutido entrega os streams.
     */
    private suspend fun configuredSources(): List<PublicSource> {
        val bundled = sources.enabled()
            .filter { it.targets.isNotEmpty() }
            // Fontes cadastradas como lista avulsa (M3U/XTREAM) não entram mais.
            .filterNot { it.discoveredFrom.equals("user", ignoreCase = true) }
            .sortedBy { it.priority }
        return bundled.distinctBy { "${it.url}|${SourceTargeting.encode(it.targets)}" }
    }

    private fun kindLabel(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "Canais"
        MediaKind.MOVIE -> "Filmes"
        MediaKind.SERIES -> "Séries"
        else -> "Conteúdo"
    }

    /**
     * Aplica o tipo da aba ao item bruto e o submete ao [ContentClassifier].
     *
     * Devolve `null` quando o item não pertence àquela aba — é aqui que um
     * canal ao vivo deixa de aparecer em Filmes.
     */
    private fun applyKind(kind: MediaKind, raw: MediaItem): MediaItem? {
        val url = raw.streamUrl
        if (ContentClassifier.classifyFor(kind, raw.title, url) == null) return null
        return when (kind) {
            MediaKind.MOVIE -> raw.copy(
                kind = MediaKind.MOVIE,
                title = ContentClassifier.movieTitle(raw.title).ifBlank { raw.title },
                year = raw.year ?: ContentClassifier.yearOf(raw.title),
            )
            MediaKind.SERIES -> raw.copy(kind = MediaKind.SERIES)
            else -> raw.copy(kind = kind)
        }
    }

    /**
     * Rede de segurança: sem nada vindo dos repositórios, a aba Filmes ficaria
     * vazia. Entram então os filmes de domínio público do [DemoVod] — sempre com
     * arquivo estático, nunca canal ao vivo.
     */
    private fun applyDemoFallback(snapshot: Snapshot): Snapshot {
        if (snapshot.movies.isNotEmpty() || snapshot.series.isNotEmpty()) return snapshot
        if (DemoVod.movies.isEmpty()) return snapshot
        return snapshot.copy(movies = DemoVod.movies.sortedBy { TextUtils.titleKey(it.title) })
    }

    private fun isPortuguese(item: MediaItem): Boolean =
        com.smarted.tv.core.util.LanguageFilter.isPortuguese(
            title = item.title,
            group = item.group,
            language = item.language,
            country = item.country,
        )

    private suspend fun fetchSource(source: PublicSource): List<MediaItem> {
        if (source.format == com.smarted.tv.core.model.SourceFormat.XMLTV) return emptyList()
        // Os três repositórios podem apontar para a mesma URL (ex.: só há uma
        // lista PT disponível); o corpo é baixado uma vez e reaproveitado.
        val body = bodyCache[source.url] ?: run {
            val downloaded = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                http.getText(source.url, cacheable = true, timeoutSeconds = 45)
            } ?: throw IllegalStateException("timeout")
            bodyCache[source.url] = downloaded
            downloaded
        }
        val parsed = M3uParser.parse(body, addonId = ID, defaultKind = source.kind)
        // Toda fonte é "escopada" pelo campo em que foi cadastrada: a lista de
        // Filmes só alimenta Filmes. O tipo final ainda passa pelo
        // ContentClassifier em `applyKind`, que descarta o que não é VOD.
        val items = parsed.items.map { item ->
            val quality = item.quality ?: M3uParser.inferQuality(item.title)
            item.copy(
                addonId = ID,
                kind = source.kind,
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
            // Séries levam a estrutura de temporadas junto: sem isso a aba
            // abriria pelo cache sem episódios até a próxima sincronização.
            val seasons = item.details?.seasons.orEmpty()
            if (seasons.isNotEmpty()) {
                append(",\"eps\":[")
                var first = true
                for (season in seasons) {
                    for (ep in season.episodes) {
                        if (!first) append(',')
                        first = false
                        append('[').append(season.number).append(',').append(ep.number).append(',')
                        append('"').append(esc(ep.title)).append("\",\"")
                        append(esc(ep.streamUrl ?: "")).append("\",\"")
                        append(esc(ep.still ?: "")).append("\"]")
                    }
                }
                append(']')
            }
            append('}')
        }
    }

    /** Reconstrói a ficha de série (temporadas/episódios) a partir do cache. */
    private fun detailsFromCache(
        episodes: List<Triple<Int, Int, List<String>>>,
        poster: String?,
        rating: Double?,
    ): com.smarted.tv.core.model.ItemDetails? {
        if (episodes.isEmpty()) return null
        val seasons = episodes
            .groupBy { it.first }
            .toSortedMap()
            .map { (number, eps) ->
                com.smarted.tv.core.model.Season(
                    number = number,
                    title = "Temporada $number",
                    poster = eps.firstNotNullOfOrNull { it.third.getOrNull(2)?.takeIf { p -> p.isNotBlank() } },
                    // Ordem gravada no cache: [título, url do stream, still].
                    episodes = eps.sortedBy { it.second }.map { (season, epNumber, data) ->
                        val url = data.getOrNull(1)?.takeIf { it.isNotBlank() }
                        com.smarted.tv.core.model.Episode(
                            id = url ?: "ep-$season-$epNumber",
                            title = data.getOrNull(0).orEmpty().ifBlank { "Episódio $epNumber" },
                            season = season,
                            number = epNumber,
                            streamUrl = url,
                            still = data.getOrNull(2)?.takeIf { it.isNotBlank() },
                            addonId = ID,
                        )
                    },
                )
            }
        return com.smarted.tv.core.model.ItemDetails(
            poster = poster,
            rating = rating,
            seasons = seasons,
            isSeries = true,
            source = "m3u",
        )
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
            series = arr("series", MediaKind.SERIES).map { item ->
                val eps = episodesOf(root, item.id)
                if (eps.isEmpty()) item
                else item.copy(
                    details = detailsFromCache(eps, item.poster, item.rating),
                    number = eps.size,
                )
            },
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

    /** Lê o array `eps` de uma série no JSON do cache. */
    private fun episodesOf(
        root: kotlinx.serialization.json.JsonObject,
        itemId: String,
    ): List<Triple<Int, Int, List<String>>> {
        val array = root["series"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val target = array.filterIsInstance<kotlinx.serialization.json.JsonObject>()
            .firstOrNull { o ->
                (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == itemId
            } ?: return emptyList()
        val eps = target["eps"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return eps.mapNotNull { el ->
            val row = el as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val season = (row.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                ?: return@mapNotNull null
            val number = (row.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
                ?: return@mapNotNull null
            val data = row.drop(2).mapNotNull {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }
            Triple(season, number, data)
        }
    }

    private fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    companion object {
        // v3: separação estrita por tipo + séries agrupadas em temporadas.
        private const val CACHE_KEY = "brazuca.snapshot.v3"
        private const val FETCH_TIMEOUT_MS = 60_000L
    }
}
