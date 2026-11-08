package com.smarted.tv.core.data

import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.normalized
import com.smarted.tv.core.util.toCategoryName
import com.smarted.tv.data.OnePlayBootstrap
import com.smarted.tv.data.repo.OnePlayTreeClient
import com.smarted.tv.data.repo.toRepositoryNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    // ---------------------------------- Árvore recursiva de repositórios (Kodi)
    //
    // Nível 1: REPOSITÓRIOS — fontes cadastradas kind=REPOSITORY (nunca a pasta "Addons").
    // Nível 2: ADDONS do repositório — "OnePlay Vídeo Addon", "OnePlay Live TV".
    // Nível 3: menu/seções do addon — Canais ao Vivo, Filmes, Séries, Lançamentos.
    // Nível 4: pastas → itens (diálogo: Reproduzir / Salvar em Canais|Filmes|Séries).

    /** Esqueleto do índice (JSON → RepositoryNode) do repositório dono ([repoUrl]). */
    private var indexAttemptedFor: String? = null
    private var cachedSkeleton: RepositoryNode? = null

    private suspend fun skeletonFor(repoUrl: String?): RepositoryNode? {
        val url = repoUrl?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL
        if (indexAttemptedFor == url) return cachedSkeleton
        indexAttemptedFor = url
        cachedSkeleton = runCatching {
            withTimeoutOrNull(10_000L) { OnePlayTreeClient.load(url) }?.toRepositoryNode()
        }.getOrNull()
        return cachedSkeleton
    }

    /** Nível 1: a raiz lista os REPOSITÓRIOS cadastrados (nunca a pasta "Addons"). */
    override suspend fun repositoryRoot(): RepositoryNode = withContext(Dispatchers.IO) {
        RepositoryNode(
            id = "root",
            name = "Repositório",
            kind = RepositoryNode.Kind.ROOT,
            children = repositorySources(),
        )
    }

    private fun repositorySources(): List<RepositoryNode> {
        val sources = runCatching { store.listSources() }.getOrDefault(emptyList())
            .filter { it.kind == SourceKind.REPOSITORY }
        val nodes = sources.map { s ->
            RepositoryNode(
                id = "repo|${s.id}",
                name = s.name,
                kind = RepositoryNode.Kind.REPOSITORY,
                url = s.url,
            )
        }
        // Nunca um beco sem saída: sem repositório cadastrado, expõe o OnePlay padrão.
        return nodes.ifEmpty {
            listOf(
                RepositoryNode(
                    id = "repo|0",
                    name = OnePlayBootstrap.PHANTOM_NAME,
                    kind = RepositoryNode.Kind.REPOSITORY,
                    url = OnePlayBootstrap.INDEX_URL,
                ),
            )
        }
    }

    /** Filhos dinâmicos por nível do explorador Kodi — sem redirecionar para abas estáticas. */
    override suspend fun repositoryChildren(node: RepositoryNode): List<RepositoryNode> =
        withContext(Dispatchers.IO) {
            when (node.kind) {
                RepositoryNode.Kind.ROOT -> repositorySources()
                RepositoryNode.Kind.REPOSITORY -> repositoryAddons(node)
                RepositoryNode.Kind.ADDON -> addonChildren(node)
                RepositoryNode.Kind.CATEGORY ->
                    if (node.id == RELEASES_ID) releasesChildren() else categoryChildren(node)
                RepositoryNode.Kind.FOLDER -> folderChildren(node)
                RepositoryNode.Kind.ITEM -> emptyList()
            }
        }

    /** Nível 2: addons do repositório — vídeo + live (ex.: OnePlay Vídeo Addon, OnePlay Live TV). */
    private fun repositoryAddons(repo: RepositoryNode): List<RepositoryNode> {
        val repoUrl = repo.url?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL
        return listOf(
            RepositoryNode(
                id = "addon|video|${repo.id}",
                name = "${repo.name} Vídeo Addon",
                kind = RepositoryNode.Kind.ADDON,
                repoUrl = repoUrl,
            ),
            RepositoryNode(
                id = "addon|live|${repo.id}",
                name = "${repo.name} Live TV",
                kind = RepositoryNode.Kind.ADDON,
                repoUrl = repoUrl,
            ),
        )
    }

    /** Nível 3: menu nativo do addon (seções) — estilo Kodi. */
    private suspend fun addonChildren(node: RepositoryNode): List<RepositoryNode> {
        // Live TV: direto nos grupos de canais (pastas) do addon de canais ao vivo.
        if (node.id.contains("|live|")) {
            return categoryChildren(sectionNode("Canais ao Vivo", Tab.CHANNELS, node.repoUrl))
        }
        // Vídeo: seções canônicas do addon + categorias extras declaradas no índice.
        val sections = mutableListOf(
            sectionNode("Canais ao Vivo", Tab.CHANNELS, node.repoUrl),
            sectionNode("Filmes", Tab.MOVIES, node.repoUrl),
            sectionNode("Séries", Tab.SERIES, node.repoUrl),
            RepositoryNode(
                id = RELEASES_ID,
                name = "Lançamentos",
                kind = RepositoryNode.Kind.CATEGORY,
                repoUrl = node.repoUrl,
            ),
        )
        val canonical = setOf("tv", "filmes", "series", "séries", "canais ao vivo")
        for (cat in skeletonFor(node.repoUrl)?.children.orEmpty()) {
            if (cat.kind != RepositoryNode.Kind.CATEGORY || cat.name.normalized() in canonical) continue
            sections += cat.copy(repoUrl = node.repoUrl)
        }
        return sections
    }

    private fun sectionNode(name: String, tab: Tab, repoUrl: String) = RepositoryNode(
        id = "cat|${tab.name}|$name",
        name = name,
        kind = RepositoryNode.Kind.CATEGORY,
        tab = tab,
        repoUrl = repoUrl,
    )

    /** Nível 4 da seção Lançamentos: VOD ordenado por data de inclusão (recentes primeiro). */
    private suspend fun releasesChildren(): List<RepositoryNode> =
        (vod(VodKind.MOVIE) + vod(VodKind.SERIES))
            .sortedByDescending { it.addedAtMs }
            .take(40)
            .map { v ->
                val isSeries = v.kind == VodKind.SERIES
                RepositoryNode(
                    id = (if (isSeries) "series" else "movie") + "|" + v.id,
                    name = v.title,
                    kind = RepositoryNode.Kind.ITEM,
                    tab = if (isSeries) Tab.SERIES else Tab.MOVIES,
                    mediaKind = if (isSeries) "series" else "movie",
                    url = v.streamUrl,
                    headers = v.headers,
                    favKey = v.favKey,
                )
            }

    /** Seções: pastas locais da aba + categorias/filhos do índice do repositório. */
    private suspend fun categoryChildren(node: RepositoryNode): List<RepositoryNode> {
        val skeleton = skeletonFor(node.repoUrl)
        val tab = node.tab
        if (tab == null) {
            // Categoria do índice sem aba canônica: serve os filhos do índice direto.
            val skeletonCat = skeleton?.children?.firstOrNull {
                it.kind == RepositoryNode.Kind.CATEGORY && it.name.normalized() == node.name.normalized()
            } ?: return emptyList()
            return skeletonCat.children.map { child ->
                if (child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank()) {
                    child.copy(repoUrl = node.repoUrl)
                } else {
                    child
                }
            }
        }
        val out = LinkedHashMap<String, RepositoryNode>()
        for (folder in localFolders(tab)) out.getOrPut(folder.id) { folder }
        val remoteCat = skeleton?.children?.firstOrNull {
            it.kind == RepositoryNode.Kind.CATEGORY && it.tab == tab &&
                it.name.normalized() == node.name.normalized()
        } ?: skeleton?.children?.firstOrNull { it.kind == RepositoryNode.Kind.CATEGORY && it.tab == tab }
        for (child in remoteCat?.children.orEmpty()) {
            when {
                child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank() ->
                    out.getOrPut(child.id) { child.copy(repoUrl = node.repoUrl) }
                child.kind == RepositoryNode.Kind.ITEM ->
                    out.getOrPut(child.id) { child }
                else -> Unit
            }
        }
        return out.values.toList()
    }

    /**
     * Filhos de uma pasta: subpastas/itens do índice no caminho "Pai/Filho"
     * + itens do banco pertencentes à pasta — carregados sob demanda.
     */
    private suspend fun folderChildren(node: RepositoryNode): List<RepositoryNode> {
        val tab = node.tab ?: return emptyList()
        val subFolders = mutableListOf<RepositoryNode>()
        val remoteItems = mutableListOf<RepositoryNode>()
        val remote = remoteFolder(tab, node.pathKey, node.repoUrl)
        for (child in remote?.children.orEmpty()) {
            when {
                child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank() -> {
                    val nested = child.copy(
                        id = "folder|${tab.name}|${node.pathKey}/${child.name}",
                        pathKey = "${node.pathKey}/${child.name}",
                        repoUrl = node.repoUrl,
                    )
                    subFolders.add(nested)
                }
                child.kind == RepositoryNode.Kind.ITEM -> remoteItems.add(child)
                else -> Unit
            }
        }
        val dbItems = localItems(tab, node.pathKey.ifBlank { node.name })
        val titles = dbItems.map { it.name.normalized() }.toMutableSet()
        val extraItems = remoteItems.filter { titles.add(it.name.normalized()) }
        return subFolders + dbItems + extraItems
    }

    /** Nó de pasta no índice remoto, caminhando pathKey ("Pai/Filho") a partir da categoria. */
    private suspend fun remoteFolder(tab: Tab, pathKey: String, repoUrl: String): RepositoryNode? {
        val skeleton = skeletonFor(repoUrl) ?: return null
        var node = skeleton.children.firstOrNull { it.kind == RepositoryNode.Kind.CATEGORY && it.tab == tab }
            ?: skeleton.children.firstOrNull { it.tab == tab }
            ?: return null
        for (segment in pathKey.split("/")) {
            node = node.children.firstOrNull { it.name.normalized() == segment.normalized() } ?: return null
        }
        return node
    }

    /**
     * Pastas locais de uma aba (categoryIds distintos). O `name` exibido passa por
     * [toCategoryName] ("Animation;Kids" → "Animation · Kids"); `pathKey` mantém o
     * valor crua para casar com os itens do banco.
     */
    private suspend fun localFolders(tab: Tab): List<RepositoryNode> {
        val names = when (tab) {
            Tab.CHANNELS -> channels().map { it.categoryId }
            Tab.MOVIES -> vod(VodKind.MOVIE).map { it.categoryId }
            Tab.SERIES -> vod(VodKind.SERIES).map { it.categoryId }
        }.filter { it.isNotBlank() }.distinct().sorted()
        return names.map { raw ->
            RepositoryNode(
                id = "folder|${tab.name}|$raw",
                name = raw.toCategoryName(),
                kind = RepositoryNode.Kind.FOLDER,
                tab = tab,
                pathKey = raw,
            )
        }
    }

    /** Itens do banco dentro de uma pasta (pela categoria crua em [RepositoryNode.pathKey]). */
    private suspend fun localItems(tab: Tab, folderKey: String): List<RepositoryNode> = when (tab) {
        Tab.CHANNELS -> channels().filter { it.categoryId == folderKey }.sortedBy { it.name }.map {
            RepositoryNode(
                id = "channel|${it.id}", name = it.name, kind = RepositoryNode.Kind.ITEM,
                tab = tab, pathKey = folderKey, mediaKind = "channel",
                url = it.streamUrl, headers = it.headers, favKey = it.favKey,
            )
        }
        Tab.MOVIES -> vod(VodKind.MOVIE).filter { it.categoryId == folderKey }.sortedBy { it.title }.map {
            RepositoryNode(
                id = "movie|${it.id}", name = it.title, kind = RepositoryNode.Kind.ITEM,
                tab = tab, pathKey = folderKey, mediaKind = "movie",
                url = it.streamUrl, headers = it.headers, favKey = it.favKey,
            )
        }
        Tab.SERIES -> vod(VodKind.SERIES).filter { it.categoryId == folderKey }.sortedBy { it.title }.map {
            RepositoryNode(
                id = "series|${it.id}", name = it.title, kind = RepositoryNode.Kind.ITEM,
                tab = tab, pathKey = folderKey, mediaKind = "series",
                url = it.streamUrl, headers = it.headers, favKey = it.favKey,
            )
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
        const val RELEASES_ID = "cat|RELEASES|Lançamentos"
        const val CHAN = "c:"
        const val MOVIE = "m:"
        const val SERIES = "s:"
        const val MAX_CHANNELS = 20_000
        const val MAX_VOD = 40_000
        const val EPG_HORIZON_MS = 12L * 60L * 60_000L
    }
}
