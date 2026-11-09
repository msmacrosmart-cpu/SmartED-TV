package com.smarted.tv.core.data

import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.normalized
import com.smarted.tv.core.util.toCategoryName
import com.smarted.tv.data.OnePlayBootstrap
import com.smarted.tv.data.net.Http
import com.smarted.tv.data.repo.OnePlayIndexDto
import com.smarted.tv.data.repo.OnePlayTreeClient
import com.smarted.tv.data.repo.toRepositoryMenu
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
    // Nível 2: ADDONS do repositório — lidos do JSON (tipos universais Kodi:
    //          video, programs, radio, ... — nada fixo em código).
    // Nível 3: menu EXATO do add-on conforme o JSON (subpastas/listas dinâmicas).
    // Nível 4: pastas → itens (diálogo: Reproduzir / Salvar em Canais|Filmes|Séries).

    /** Índice JSON do repositório dono ([repoUrl]) — cache de melhor esforço. */
    private var indexAttemptedFor: String? = null
    private var cachedIndex: OnePlayIndexDto? = null
    private var indexFailedAt: Long = 0L

    private suspend fun indexFor(repoUrl: String?): OnePlayIndexDto? {
        val url = repoUrl?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL
        // Suporte em cache; FALHA não é definitiva: tenta de novo após 15 s —
        // um timeout transitório não pode deixar a árvore presa no fallback local.
        if (indexAttemptedFor == url && cachedIndex != null) return cachedIndex
        if (indexAttemptedFor == url && indexFailedAt != 0L &&
            System.currentTimeMillis() - indexFailedAt < 15_000L
        ) {
            return null
        }
        indexAttemptedFor = url
        cachedIndex = if (url.startsWith("file://")) {
            // Índice instalado pelo navegador de ZIPs (arquivo local) OU uma
            // PASTA local: varredura recursiva da hierarquia real (KodiScanner).
            runCatching {
                val path = url.removePrefix("file://")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val f = java.io.File(path)
                    if (f.isDirectory) {
                        val scan = com.smarted.tv.data.repo.KodiScanner.scan(
                            com.smarted.tv.data.repo.KodiScanner.FileVfs,
                            path,
                            f.name.ifBlank { "Pasta" },
                        )
                        if (scan.entryCount == 0) null
                        else com.smarted.tv.data.repo.KodiScanner.buildIndex(scan)
                    } else {
                        Http.json.decodeFromString(OnePlayIndexDto.serializer(), f.readText())
                    }?.let { com.smarted.tv.data.repo.RepoPackages.migrateLegacyIndex(it, url) }
                }
            }.getOrNull()
        } else {
            runCatching {
                withTimeoutOrNull(10_000L) { OnePlayTreeClient.load(url) }
            }.getOrNull()?.let { com.smarted.tv.data.repo.RepoPackages.migrateLegacyIndex(it, url) }
        }
        indexFailedAt = if (cachedIndex == null) System.currentTimeMillis() else 0L
        return cachedIndex
    }

    /**
     * Raiz do MENU do add-on dono do nó ([RepositoryNode.addonId]) — do JSON
     * `addons[i].tree` (formato atual) ou do `tree` legado. É a partir dela que
     * cada pasta pede seus filhos reais, em qualquer profundidade.
     */
    private suspend fun menuRootFor(node: RepositoryNode): RepositoryNode? {
        val index = indexFor(node.repoUrl) ?: return null
        val anchor = node.addonId
        val tree = when {
            // Âncora de arquivos: estrutura extraída do repositório (File Manager).
            anchor.startsWith("files") -> index.files
            // Âncora de add-on: menu do add-on declarado no JSON.
            anchor.startsWith("addon|") -> anchor.substringAfter("addon|", "")
                .substringBefore("|").toIntOrNull()
                ?.let { index.addons?.getOrNull(it) }?.tree
            else -> null
        } ?: index.tree
        return tree?.toRepositoryMenu()
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
                // O phantom OnePlay sempre usa o índice ATUAL deste branch.
                url = if (s.name == OnePlayBootstrap.PHANTOM_NAME) OnePlayBootstrap.INDEX_URL else s.url,
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
                RepositoryNode.Kind.REPOSITORY -> repositorySections(node)
                RepositoryNode.Kind.ADDON -> addonChildren(node)
                RepositoryNode.Kind.CATEGORY ->
                    if (node.id.startsWith("adongroup|")) addonsOfGroup(node) else categoryChildren(node)
                RepositoryNode.Kind.FOLDER -> folderChildren(node)
                RepositoryNode.Kind.ITEM -> emptyList()
            }
        }

    /**
     * Nível 2 do repositório: SEPARAÇÃO por tipos oficiais Kodi — "Add-ons de
     * Vídeo", "Add-ons de Programas/Ferramentas", "PVR", ... (grupos dinâmicos
     * a partir dos tipos declarados no JSON) + as pastas de ARQUIVOS do
     * repositório (sub-repositórios e ferramentas — File Manager/instalação ZIP).
     */
    private suspend fun repositorySections(repo: RepositoryNode): List<RepositoryNode> {
        val addons = repositoryAddons(repo)
        val groups = addons.groupBy { (it.addonType ?: "other").lowercase() }
        val groupNodes = groups.entries.sortedBy { addonGroupOrder(it.key) }.map { (type, members) ->
            RepositoryNode(
                id = "adongroup|$type|${repo.id}",
                name = "${addonGroupLabel(type)} (${members.size})",
                kind = RepositoryNode.Kind.CATEGORY,
                repoUrl = repo.url?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL,
            )
        }
        return groupNodes + repositoryFileFolders(repo)
    }

    /** Ordem oficial dos grupos de add-ons (spec §5 — idêntico ao Kodi). */
    private fun addonGroupOrder(type: String): Int = when (type) {
        "video" -> 0
        "programs", "program", "tools", "script" -> 1
        "music", "audio", "radio" -> 2
        "skins", "skin", "interface" -> 3
        "services", "service" -> 4
        "metadata" -> 5
        "subtitles" -> 6
        "repository", "repositories" -> 7
        "pvr" -> 8
        "pictures" -> 9
        else -> 10
    }

    /** Rótulo oficial do grupo (spec §5); tipo desconhecido exibe o próprio tipo. */
    private fun addonGroupLabel(type: String): String = when (type) {
        "video" -> "Add-ons de Vídeo"
        "programs", "program", "tools", "script" -> "Add-ons de Programas/Ferramentas"
        "music", "audio", "radio" -> "Add-ons de Áudio/Rádio"
        "skins", "skin", "interface" -> "Add-ons de Interface"
        "services", "service" -> "Add-ons de Serviços"
        "metadata" -> "Add-ons de Metadados"
        "subtitles" -> "Add-ons de Legendas"
        "repository", "repositories" -> "Add-ons de Repositório"
        "pvr" -> "PVR"
        "pictures" -> "Add-ons de Imagens"
        "other" -> "Outros"
        else -> type.replaceFirstChar { it.uppercaseChar() }
    }

    /** Filhos do grupo de tipo: apenas os add-ons JSON daquele tipo (ordem do JSON). */
    private suspend fun addonsOfGroup(group: RepositoryNode): List<RepositoryNode> {
        val type = group.id.substringAfter("adongroup|", "").substringBefore("|")
        val repoId = group.id.substringAfterLast("|")
        val repo = RepositoryNode(
            id = repoId,
            name = repoId,
            kind = RepositoryNode.Kind.REPOSITORY,
            url = group.repoUrl,
        )
        return repositoryAddons(repo).filter { (it.addonType ?: "other").lowercase() == type }
    }

    /**
     * Estrutura de ARQUIVOS do repositório (JSON `files`): sub-repositórios e
     * ferramentas navegáveis — o "Gestor de Arquivos" lendo o conteúdo extraído.
     */
    private suspend fun repositoryFileFolders(repo: RepositoryNode): List<RepositoryNode> {
        val url = repo.url?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL
        val files = indexFor(url)?.files ?: return emptyList()
        return files.children.orEmpty().mapNotNull { child ->
            val name = child.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!child.url.isNullOrBlank()) return@mapNotNull null
            RepositoryNode(
                id = "folder|FILES|${child.id ?: name}",
                name = name,
                kind = RepositoryNode.Kind.FOLDER,
                pathKey = name,
                repoUrl = url,
                addonId = "files|${repo.id}",
            )
        }
    }

    /**
     * Nível 2: add-ons declarados NO JSON do repositório (tipos universais Kodi —
     * video, programs, radio, ...). Sem `addons` no JSON, cai no `tree` legado
     * (um único add-on); sem índice, um add-on com o conteúdo real do banco.
     */
    private suspend fun repositoryAddons(repo: RepositoryNode): List<RepositoryNode> {
        val url = repo.url?.takeIf { it.isNotBlank() } ?: OnePlayBootstrap.INDEX_URL
        val index = indexFor(url)
        val declared = index?.addons.orEmpty()
        if (declared.isNotEmpty()) {
            return declared.mapIndexed { idx, addon ->
                val meta = listOf(
                    addon.author?.takeIf { it.isNotBlank() },
                    addon.version?.takeIf { it.isNotBlank() }?.let { "v$it" },
                ).filterNotNull().joinToString(" - ").ifBlank { null }
                RepositoryNode(
                    id = "addon|$idx|${repo.id}",
                    name = addon.name?.takeIf { it.isNotBlank() } ?: "${repo.name} Add-on",
                    kind = RepositoryNode.Kind.ADDON,
                    addonType = addon.type,
                    addonMeta = meta,
                    addonDescription = addon.description?.takeIf { it.isNotBlank() },
                    addonCapability = addon.capability
                        ?: addon.description?.takeIf { d ->
                            com.smarted.tv.data.repo.RepoPackages.isXtreamHint(d)
                        }?.let { "xtream" },
                    addonEntrypoint = addon.entrypoint,
                    repoUrl = url,
                )
            }
        }
        // Índice legado (só `tree`): um único add-on com aquele menu.
        if (index?.tree != null) {
            return listOf(
                RepositoryNode(
                    id = "addon|0|${repo.id}",
                    name = index.name?.takeIf { it.isNotBlank() } ?: repo.name,
                    kind = RepositoryNode.Kind.ADDON,
                    repoUrl = url,
                ),
            )
        }
        // Offline sem índice: add-on único com as pastas REAIS do banco (nada simulado).
        return listOf(
            RepositoryNode(
                id = "addon|0|${repo.id}",
                name = repo.name,
                kind = RepositoryNode.Kind.ADDON,
                repoUrl = url,
            ),
        )
    }

    /** Execução do add-on: menu JSON quando existe; senão capacidade real. */
    override suspend fun addonExecution(node: RepositoryNode): String {
        if (node.addonCapability == "xtream") return "xtream"
        val idx = node.id.substringAfter("addon|", "").substringBefore("|").toIntOrNull()
        val index = indexFor(node.repoUrl)
        val addon = idx?.let { index?.addons?.getOrNull(it) }
        if (addon?.tree != null) return "menu"
        if (addon?.capability == "xtream") return "xtream"
        val text = listOfNotNull(addon?.description, addon?.name).joinToString(" ")
        return if (com.smarted.tv.data.repo.RepoPackages.isXtreamHint(text)) "xtream" else "details"
    }

    /** Conecta o add-on ao painel Xtream NATIVO (probe de credenciais real). */
    override suspend fun connectAddonXtream(name: String, url: String, user: String, pass: String): String {
        val source = withContext(Dispatchers.IO) {
            graph.sources.addXtream(
                name.ifBlank { "Add-on" },
                url.trim(),
                user.trim(),
                pass.trim(),
                "",
            )
        }
        // Mapeamento p/ as abas nativas: sincroniza NA HORA (o insert sozinho só
        // esperaria o auto-update) — ao vivo→Canais, VOD→Filmes, séries→Séries,
        // com URL de reprodução pronta para o player interno.
        val summary = try {
            graph.sources.refresh(source, includeEpg = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Add-on conectado (${source.name}), mas a sincronização falhou: " +
                (e.message ?: "erro de rede") + ". Tente atualizar a fonte."
        }
        return "Add-on conectado (${source.name}) — $summary"
    }

    override suspend fun existingXtreamName(): String? = withContext(Dispatchers.IO) {
        runCatching {
            store.listSources().firstOrNull {
                it.kind == com.smarted.tv.core.model.SourceKind.XTREAM && it.enabled
            }?.name
        }.getOrNull()
    }

    /**
     * Nível 3: o menu do add-on é EXATAMENTE o fluxo de diretórios que o JSON
     * retornar (subpastas, listas, itens) — nenhuma pasta é criada no código.
     * Sem índice (offline), expõe as pastas reais importadas no banco.
     */
    private suspend fun addonChildren(node: RepositoryNode): List<RepositoryNode> {
        val idx = node.id.substringAfter("addon|", "").substringBefore("|").toIntOrNull() ?: 0
        val index = indexFor(node.repoUrl)
        val menuRoot = index?.addons?.getOrNull(idx)?.tree ?: index?.tree
        if (menuRoot == null) {
            return Tab.entries.flatMap { localFolders(it) }
        }
        return menuRoot.toRepositoryMenu().children.map { it.withAnchor(node.repoUrl, node.id) }
    }

    /** Propaga repositório + add-on dono para a subárvore mapeada (lookups recursivos). */
    private fun RepositoryNode.withAnchor(repoUrl: String, addonId: String): RepositoryNode = copy(
        repoUrl = repoUrl,
        addonId = addonId,
        children = children.map { it.withAnchor(repoUrl, addonId) },
    )

    /** Seções: pastas locais da aba + categorias/filhos do índice do repositório. */
    private suspend fun categoryChildren(node: RepositoryNode): List<RepositoryNode> {
        val menu = menuRootFor(node)
        val tab = node.tab
        if (tab == null) {
            // Categoria do índice sem aba canônica: serve os filhos do índice direto.
            val skeletonCat = menu?.children?.firstOrNull {
                it.kind == RepositoryNode.Kind.CATEGORY && it.name.normalized() == node.name.normalized()
            } ?: return emptyList()
            return skeletonCat.children.map { child ->
                if (child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank()) {
                    child.copy(repoUrl = node.repoUrl, addonId = node.addonId)
                } else {
                    child.copy(repoUrl = node.repoUrl, addonId = node.addonId)
                }
            }
        }
        val out = LinkedHashMap<String, RepositoryNode>()
        for (folder in localFolders(tab)) {
            out.getOrPut(folder.id) { folder.copy(repoUrl = node.repoUrl, addonId = node.addonId) }
        }
        val remoteCat = menu?.children?.firstOrNull {
            it.kind == RepositoryNode.Kind.CATEGORY && it.tab == tab &&
                it.name.normalized() == node.name.normalized()
        } ?: menu?.children?.firstOrNull { it.kind == RepositoryNode.Kind.CATEGORY && it.tab == tab }
        for (child in remoteCat?.children.orEmpty()) {
            when {
                child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank() ->
                    out.getOrPut(child.id) { child.copy(repoUrl = node.repoUrl) }
                child.kind == RepositoryNode.Kind.ITEM ->
                    out.getOrPut(child.id) { child.copy(repoUrl = node.repoUrl, addonId = node.addonId) }
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
        val tab = node.tab
        val subFolders = mutableListOf<RepositoryNode>()
        val remoteItems = mutableListOf<RepositoryNode>()
        val remote = menuFolder(node)
        for (child in remote?.children.orEmpty()) {
            when {
                child.kind == RepositoryNode.Kind.FOLDER && child.url.isNullOrBlank() -> {
                    val nested = child.copy(
                        id = "folder|${tab?.name ?: "?"}|${node.pathKey}/${child.name}",
                        pathKey = "${node.pathKey}/${child.name}",
                        repoUrl = node.repoUrl,
                        addonId = node.addonId,
                    )
                    subFolders.add(nested)
                }
                child.kind == RepositoryNode.Kind.ITEM -> remoteItems.add(child.copy(addonId = node.addonId, repoUrl = node.repoUrl))
                else -> Unit
            }
        }
        val dbItems = if (tab != null) localItems(tab, node.pathKey.ifBlank { node.name }) else emptyList()
        val titles = dbItems.map { it.name.normalized() }.toMutableSet()
        val extraItems = remoteItems.filter { titles.add(it.name.normalized()) }
        return subFolders + dbItems + extraItems
    }

    /**
     * Nó da pasta DENTRO do menu do add-on: desce pathKey ("Pai/Filho") a partir
     * da categoria (quando tem tab) ou da própria raiz do menu (pastas de topo
     * sem tab, ex.: "VIP", "Rádios Nacionais") — servindo os filhos REAIS do JSON.
     */
    private suspend fun menuFolder(node: RepositoryNode): RepositoryNode? {
        val menu = menuRootFor(node) ?: return null
        var cur = if (node.tab != null) {
            menu.children.firstOrNull { it.kind == RepositoryNode.Kind.CATEGORY && it.tab == node.tab }
                ?: menu.children.firstOrNull { it.tab == node.tab }
                ?: menu
        } else {
            menu
        }
        for (segment in node.pathKey.split("/")) {
            if (segment.isBlank()) continue
            cur = cur.children.firstOrNull { it.name.normalized() == segment.normalized() } ?: return null
        }
        return cur
    }

    /**
     * Pastas locais de uma aba (categoryIds distintos). O `name` exibido passa por
     * [toCategoryName] ("Animation;Kids" → "Animation · Kids"); `pathKey` mantém o
     * valor crua para casar com os itens do banco.
     */
    private suspend fun localFolders(tab: Tab): List<RepositoryNode> {
        val cats = when (tab) {
            Tab.CHANNELS -> channels().map { it.categoryId }
            Tab.MOVIES -> vod(VodKind.MOVIE).map { it.categoryId }
            Tab.SERIES -> vod(VodKind.SERIES).map { it.categoryId }
        }.filter { it.isNotBlank() }
        val counts = cats.groupingBy { it }.eachCount()
        return counts.keys.sorted().map { raw ->
            RepositoryNode(
                id = "folder|${tab.name}|$raw",
                // Ex.: "Canais | Abertos (119)" — contagem real de itens do grupo.
                name = "${raw.toCategoryName()} (${counts[raw] ?: 0})",
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
        const val CHAN = "c:"
        const val MOVIE = "m:"
        const val SERIES = "s:"
        const val MAX_CHANNELS = 20_000
        const val MAX_VOD = 40_000
        const val EPG_HORIZON_MS = 12L * 60L * 60_000L
    }
}
