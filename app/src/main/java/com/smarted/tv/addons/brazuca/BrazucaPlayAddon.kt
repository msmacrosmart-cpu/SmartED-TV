package com.smarted.tv.addons.brazuca

import com.smarted.tv.core.addons.Addon
import com.smarted.tv.core.addons.AddonHealth
import com.smarted.tv.core.addons.AddonManifest
import com.smarted.tv.core.addons.BrowseRequest
import com.smarted.tv.core.model.CatalogPage
import com.smarted.tv.core.model.CatalogRow
import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PlayInfo
import com.smarted.tv.core.model.StreamSource
import com.smarted.tv.core.tmdb.TmdbClient
import com.smarted.tv.core.util.M3uParser
import com.smarted.tv.core.util.TextUtils

/**
 * **Brazuca Play** — addon nativo, carregado por padrão em toda inicialização.
 *
 * Capacidades:
 *  - Canais ao vivo, filmes e séries a partir das fontes públicas do indexador
 *    + fontes declaradas pelo repositório ativo (`https://vkodi.net/repo`).
 *  - Categorias derivadas de `group-title` com contagem (igual à referência).
 *  - EPG por tvg-id com fallback para XMLTV externo (ver [EpgService]).
 *  - Ficha completa via TMDB (nota, elenco, temporadas) e trailer do YouTube
 *    reproduzido pelo player nativo sem anúncios.
 *
 * O addon é *stateless*: o estado de catálogo vem do [BrazucaCatalog].
 */
class BrazucaPlayAddon(
    private val catalog: BrazucaCatalog,
    private val tmdb: TmdbClient,
    private val epg: com.smarted.tv.core.epg.EpgService,
) : Addon, com.smarted.tv.ui.state.AddonGateway {

    override val manifest = AddonManifest(
        id = ID,
        name = "Brazuca Play",
        version = "2.4.0",
        summary = "Canais ao vivo, filmes e séries em português",
        description = "Addon oficial e padrão do SmartED TV. Agrega as fontes do " +
            "repositório ativo (padrão: vkodi.net/repo), indexa os links, " +
            "resolve EPG por tvg-id e enriquece o catálogo com TMDB.",
        author = "SmartED TV",
        icon = "https://vkodi.net/repo/icon.png",
        repoUrl = "https://vkodi.net/repo",
        types = setOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES),
        providesEpg = true,
        builtin = true,
        usesTmdb = true,
        epgUrls = listOf(
            "https://epg.best/brazil.xml",
            "https://iptv-org.github.io/epg/guides/br/tv.epg.xml",
        ),
        publicSources = catalog.sourceUrls(),
    )

    override suspend fun refresh() {
        catalog.invalidate()
    }

    override suspend fun browse(request: BrowseRequest): CatalogPage = when (request.kind) {
        MediaKind.LIVE -> browseLive(request)
        MediaKind.MOVIE -> browseVod(request, MediaKind.MOVIE)
        MediaKind.SERIES -> browseVod(request, MediaKind.SERIES)
        else -> browseHome()
    }

    private suspend fun browseHome(): CatalogPage {
        val snapshot = catalog.snapshot()
        return CatalogPage(
            title = "Início",
            kind = MediaKind.UNKNOWN,
            rows = buildList {
                if (snapshot.live.isNotEmpty()) {
                    add(CatalogRow("Canais ao vivo", snapshot.live.take(24), MediaKind.LIVE))
                }
                if (snapshot.movies.isNotEmpty()) {
                    add(CatalogRow("Filmes", snapshot.movies.take(24), MediaKind.MOVIE))
                }
                if (snapshot.series.isNotEmpty()) {
                    add(CatalogRow("Séries", snapshot.series.take(24), MediaKind.SERIES))
                }
            },
        )
    }

    /** Canais: lista plana quando não há categoria; senão agrupada. */
    private suspend fun browseLive(request: BrowseRequest): CatalogPage {
        val snapshot = catalog.snapshot()
        val all = snapshot.live
        if (request.category == null && request.section != BrowseRequest.Section.ITEMS) {
            val groups = all.groupBy { it.group ?: "Sem categoria" }
                .toList()
                .sortedWith(compareByDescending<Pair<String, List<MediaItem>>> { it.second.size }.thenBy { it.first })
            return CatalogPage(
                title = "Canais ao vivo",
                kind = MediaKind.LIVE,
                categories = groups.map { it.first },
                total = all.size,
                rows = groups.take(12).map { (group, items) ->
                    CatalogRow(group, items.take(40), MediaKind.LIVE)
                },
                items = all,
            )
        }
        val filtered = if (request.category == null) all
        else all.filter { (it.group ?: "Sem categoria").equals(request.category, ignoreCase = true) }
        return CatalogPage(
            title = request.category ?: "Canais ao vivo",
            kind = MediaKind.LIVE,
            categories = all.mapNotNull { it.group }.distinct().sorted(),
            items = filtered,
            total = filtered.size,
        )
    }

    /** Filmes e séries: categorias + itens (com paginação simples). */
    private suspend fun browseVod(request: BrowseRequest, kind: MediaKind): CatalogPage {
        val snapshot = catalog.snapshot()
        val all = if (kind == MediaKind.MOVIE) snapshot.movies else snapshot.series
        val filtered = when {
            request.category == null -> all
            else -> all.filter { (it.group ?: "Sem categoria").equals(request.category, ignoreCase = true) }
        }
        val pageSize = 240
        val page = request.page.coerceAtLeast(1)
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        return CatalogPage(
            title = if (kind == MediaKind.MOVIE) "Filmes" else "Séries",
            kind = kind,
            categories = all.mapNotNull { it.group }.distinct().sorted(),
            items = filtered.subList(from, to),
            total = filtered.size,
            nextPage = if (to < filtered.size) page + 1 else null,
        )
    }

    override suspend fun search(query: String, page: Int): List<MediaItem> {
        val q = TextUtils.titleKey(query)
        if (q.isBlank()) return emptyList()
        val snapshot = catalog.snapshot()
        val pool = snapshot.live + snapshot.movies + snapshot.series
        return pool.asSequence()
            .map { item -> item to scoreSearch(item, q) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<MediaItem, Int>> { it.second }
                    .thenByDescending { it.first.rating ?: 0.0 },
            )
            .take(120)
            .map { it.first }
            .toList()
    }

    private fun scoreSearch(item: MediaItem, query: String): Int {
        val key = TextUtils.titleKey(item.title)
        if (key.isBlank()) return 0
        var score = when {
            key == query -> 100
            key.startsWith(query) -> 80
            key.contains(query) -> 60
            else -> {
                val sim = TextUtils.similarity(item.title, query)
                if (sim >= 0.6f) (sim * 50).toInt() else 0
            }
        }
        if (item.kind == MediaKind.LIVE) score += 4
        if (item.poster != null) score += 2
        return score
    }

    override suspend fun details(item: MediaItem): MediaItem {
        if (item.details != null) return item
        val enriched = tmdb.enrich(item)
        if (enriched.details != null) return enriched
        // Sem TMDB: usa o que a fonte oferece.
        return item.copy(
            details = com.smarted.tv.core.model.ItemDetails(
                plot = item.plot,
                poster = item.poster,
                backdrop = item.backdrop,
                rating = item.rating,
                isSeries = item.kind == MediaKind.SERIES,
                source = "brazuca",
            ),
        )
    }

    override suspend fun resolve(item: MediaItem): PlayInfo? {
        val url = item.streamUrl ?: catalog.resolveStream(item) ?: return null
        val isLive = item.kind == MediaKind.LIVE
        val sources = catalog.alternatives(item)
        val headers = M3uParser.ItemHeaderStore.get(url)
        return PlayInfo(
            url = url,
            title = item.title,
            subtitle = item.subtitle ?: item.group,
            poster = item.poster,
            kind = item.kind,
            headers = headers,
            isLive = isLive,
            mimeType = mimeFor(url),
            sources = sources,
            selectedSourceIndex = 0,
            ref = item.ref,
            durationSec = item.durationSec,
        )
    }

    private fun mimeFor(url: String): String? = when {
        url.contains(".m3u8", true) -> "application/x-mpegURL"
        url.contains(".mpd", true) -> "application/dash+xml"
        url.contains(".mp4", true) -> "video/mp4"
        url.contains(".mkv", true) -> "video/x-matroska"
        else -> null
    }

    override suspend fun epg(channel: MediaItem, startMs: Long, endMs: Long): List<EpgProgram> =
        epg.programsFor(channel, startMs, endMs)

    override suspend fun epgChannelIds(): List<String> =
        catalog.snapshot().live.mapNotNull { it.tvgId }.distinct()

    override suspend fun healthCheck(): AddonHealth {
        val started = System.currentTimeMillis()
        val snap = try {
            catalog.snapshot(force = true)
        } catch (e: Exception) {
            return AddonHealth.bad(e.message ?: "catálogo indisponível")
        }
        val latency = System.currentTimeMillis() - started
        return if (snap.total == 0) {
            AddonHealth.bad("nenhum item indexado")
        } else {
            AddonHealth.good(latency, snap.live.size, snap.movies.size, snap.series.size)
        }
    }

    override suspend fun browsePage(kind: MediaKind) = browse(
        BrowseRequest(kind = kind, section = BrowseRequest.Section.ROOT),
    )

    companion object {
        const val ID = "brazuca.play"
        const val DEFAULT_REPO = "https://vkodi.net/repo"
        const val FALLBACK_EPG = "https://epg.best/brazil.xml"
    }
}

/** Converte alternativas de stream em [StreamSource] ordenado por prioridade. */
internal fun alternativesFor(item: MediaItem, urls: List<String>): List<StreamSource> =
    urls.distinct().mapIndexed { index, url ->
        StreamSource(
            url = url,
            label = if (index == 0) "Principal" else "Servidor ${index + 1}",
            quality = item.quality,
            isHls = url.contains(".m3u8", true),
            isDash = url.contains(".mpd", true),
            priority = index,
            headers = M3uParser.ItemHeaderStore.get(url),
        )
    }
