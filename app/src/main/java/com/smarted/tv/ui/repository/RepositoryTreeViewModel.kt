package com.smarted.tv.ui.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.Channel
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodItem
import com.smarted.tv.core.model.VodKind
import com.smarted.tv.core.util.normalized
import com.smarted.tv.data.OnePlayBootstrap
import com.smarted.tv.data.repo.OnePlayIndexDto
import com.smarted.tv.data.repo.OnePlayNodeDto
import com.smarted.tv.data.repo.OnePlayTreeClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Navegação por árvore do repositório (estilo Kodi):
 * [Repositório] → [Addons] → [subpastas reais do índice/banco] → [subpastas] → [título].
 *
 * O nível Addons NÃO cai mais nas categorias engessadas "TV / Filmes / Séries": ele
 * reflete a estrutura de pastas real do repositório (índice OnePlay, na ordem declarada)
 * somada às pastas das fontes importadas no banco — exploração livre como no Kodi.
 * As categorias canônicas só aparecem como último recurso (offline sem índice e sem banco).
 *
 * As pastas/títulos vêm do banco local (importação das fontes) e, em melhor esforço,
 * da árvore recursiva do índice OnePlay lida via [OnePlayTreeClient] (Retrofit +
 * kotlinx-serialization). Falha de rede/índice => `null` => apenas árvore local.
 *
 * Selecionar um título abre o diálogo de destino: Reproduzir ou Salvar em
 * Canais/Filmes/Séries (conforme a categoria do conteúdo).
 */
class RepositoryTreeViewModel(
    private val repo: ContentRepository,
    private val loadTree: suspend () -> OnePlayIndexDto? = {
        OnePlayTreeClient.load(OnePlayBootstrap.INDEX_URL)
    },
) : ViewModel() {

    sealed class RepoTreeNode {
        abstract fun key(): String
        abstract fun label(): String

        object Root : RepoTreeNode() {
            override fun key() = "root"
            override fun label() = "Repositório"
        }

        object Addons : RepoTreeNode() {
            override fun key() = "addons"
            override fun label() = "Addons"
        }

        data class Category(val tab: Tab, val name: String) : RepoTreeNode() {
            override fun key() = "cat|${tab.name}|$name"
            override fun label() = name
        }

        /**
         * [pathKey] é o caminho da pasta dentro da categoria (ex.: "Pai/Filho") —
         * mantém a chave única no LazyColumn mesmo com pastas homônimas em níveis distintos.
         */
        data class Folder(val name: String, val tab: Tab, val pathKey: String = name) : RepoTreeNode() {
            override fun key() = "folder|${tab.name}|$pathKey"
            override fun label() = name
        }

        data class Item(
            val mediaKind: String, // "channel" | "movie" | "series"
            val id: String,
            val title: String,
            val streamUrl: String?,
            val headers: Map<String, String> = emptyMap(),
            val favKey: String = "",
        ) : RepoTreeNode() {
            override fun key() = "$mediaKind|$id"
            override fun label() = title
        }
    }

    data class Ui(
        val loaded: Boolean = false,
        val path: List<RepoTreeNode> = listOf(RepoTreeNode.Root),
        val children: List<RepoTreeNode> = emptyList(),
    )

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private val channels = MutableStateFlow<List<Channel>>(emptyList())
    private val movies = MutableStateFlow<List<VodItem>>(emptyList())
    private val series = MutableStateFlow<List<VodItem>>(emptyList())

    /** Árvore remota do índice OnePlay (null = offline/índice antigo => só local). */
    private var remoteRoot: OnePlayNodeDto? = null

    init {
        reload()
        viewModelScope.launch { repo.revision.collect { reload() } }
    }

    private fun reload() = viewModelScope.launch {
        runCatching {
            channels.value = repo.channels()
            movies.value = repo.vod(VodKind.MOVIE)
            series.value = repo.vod(VodKind.SERIES)
        }.onFailure {
            _message.value = "Falha ao carregar o repositório: ${it.message ?: it.javaClass.simpleName}"
        }
        _ui.update { it.copy(loaded = true, children = childrenOf(it.path.last())) }
        if (remoteRoot == null) {
            // Melhor esforço: a árvore OnePlay enriquece a navegação sem bloquear o offline.
            runCatching { withTimeoutOrNull(10_000L) { loadTree() }?.tree }
                .getOrNull()
                ?.let { node ->
                    remoteRoot = node
                    _ui.update { it.copy(children = childrenOf(it.path.last())) }
                }
        }
    }

    private fun childrenOf(node: RepoTreeNode): List<RepoTreeNode> = when (node) {
        RepoTreeNode.Root -> listOf(RepoTreeNode.Addons)

        RepoTreeNode.Addons -> addonChildren()

        is RepoTreeNode.Category -> {
            val base = foldersOf(node.tab)
            val seen = base.map { it.name.normalized() }.toMutableSet()
            val remoteChildren = remoteCategoryNode(node.tab, node.name)?.children.orEmpty()
            val extras: List<RepoTreeNode> = remoteChildren.mapNotNull { child ->
                val name = child.name
                when {
                    isFolderNode(child) && name != null -> {
                        if (seen.add(name.normalized())) RepoTreeNode.Folder(name, node.tab) else null
                    }
                    else -> toItem(child, fallbackKind(node.tab))
                }
            }
            base + extras
        }

        is RepoTreeNode.Folder -> {
            val dbItems = itemsOf(node)
            val remoteNode = remoteFolderNode(node.tab, node.pathKey)
            if (remoteNode == null) {
                dbItems
            } else {
                val subFolders = mutableListOf<RepoTreeNode>()
                val extraItems = mutableListOf<RepoTreeNode>()
                val titles = dbItems.map { it.title.normalized() }.toMutableSet()
                for (child in remoteNode.children.orEmpty()) {
                    val name = child.name
                    if (isFolderNode(child) && name != null) {
                        subFolders.add(RepoTreeNode.Folder(name, node.tab, "${node.pathKey}/$name"))
                    } else {
                        val item = toItem(child, fallbackKind(node.tab)) ?: continue
                        if (titles.add(item.title.normalized())) extraItems.add(item)
                    }
                }
                subFolders + dbItems + extraItems
            }
        }

        is RepoTreeNode.Item -> emptyList()
    }

    /** Último recurso (offline sem índice e sem banco): as 3 categorias canônicas. */
    private fun defaultCategories() = listOf(
        RepoTreeNode.Category(Tab.CHANNELS, "TV"),
        RepoTreeNode.Category(Tab.MOVIES, "Filmes"),
        RepoTreeNode.Category(Tab.SERIES, "Séries"),
    )

    /**
     * Addons estilo Kodi: cai DIRETO na estrutura real de pastas — as subpastas declaradas
     * no índice OnePlay (na ordem em que o repositório as declara) + as pastas das fontes
     * importadas (banco local) em todas as abas, deduplicadas por (aba, nome). Itens soltos
     * do índice também aparecem. Só sem índice E sem banco volta para [defaultCategories].
     */
    private fun addonChildren(): List<RepoTreeNode> {
        val out = LinkedHashMap<String, RepoTreeNode>()
        // 1) Subpastas do índice real (ex.: "Filmes · Aventura"), na ordem do repositório.
        for (cat in remoteRoot?.children.orEmpty()) {
            val tab = tabOf(cat) ?: continue
            for (child in cat.children.orEmpty()) {
                val name = child.name
                when {
                    name != null && child.url.isNullOrBlank() && isFolderNode(child) ->
                        out.getOrPut(RepoTreeNode.Folder(name, tab).key()) { RepoTreeNode.Folder(name, tab) }
                    else ->
                        toItem(child, fallbackKind(tab))?.let { out.getOrPut(it.key()) { it } }
                }
            }
        }
        // 2) Itens diretos na raiz do índice (sem categoria).
        for (child in remoteRoot?.children.orEmpty()) {
            if (child.url.isNullOrBlank()) continue
            val kind = child.kind?.takeIf { it == "channel" || it == "movie" || it == "series" }
                ?: tabOf(child)?.let { fallbackKind(it) } ?: continue
            toItem(child, kind)?.let { out.getOrPut(it.key()) { it } }
        }
        // 3) Pastas das fontes importadas no banco (grupos de canais, filmes, séries).
        for (tab in Tab.entries) {
            for (folder in foldersOf(tab)) out.getOrPut(folder.key()) { folder }
        }
        // Nunca um beco sem saída: sem índice e sem banco, categorias canônicas.
        return out.values.toList().ifEmpty { defaultCategories() }
    }

    /** Nó de categoria no índice remoto: por nome e, na dúvida, por aba. */
    private fun remoteCategoryNode(tab: Tab, name: String): OnePlayNodeDto? {
        val remote = remoteRoot?.children ?: return null
        return remote.firstOrNull { tabOf(it) == tab && (it.name ?: "").normalized() == name.normalized() }
            ?: remote.firstOrNull { tabOf(it) == tab }
    }

    /** Pasta no índice remoto, caminhando [pathKey] ("Pai/Filho") a partir da categoria. */
    private fun remoteFolderNode(tab: Tab, pathKey: String): OnePlayNodeDto? {
        var node = remoteRoot?.children?.firstOrNull { tabOf(it) == tab } ?: return null
        for (segment in pathKey.split("/")) {
            node = node.children?.firstOrNull { (it.name ?: "").normalized() == segment.normalized() }
                ?: return null
        }
        return node
    }

    private fun tabOf(node: OnePlayNodeDto): Tab? {
        when (node.tab?.lowercase()) {
            "channels" -> return Tab.CHANNELS
            "movies" -> return Tab.MOVIES
            "series" -> return Tab.SERIES
        }
        return when ((node.name ?: "").normalized()) {
            "tv" -> Tab.CHANNELS
            "filmes" -> Tab.MOVIES
            "series" -> Tab.SERIES
            else -> null
        }
    }

    private fun isFolderNode(node: OnePlayNodeDto): Boolean =
        node.kind == "folder" || node.children?.isNotEmpty() == true

    private fun fallbackKind(tab: Tab) = when (tab) {
        Tab.CHANNELS -> "channel"
        Tab.MOVIES -> "movie"
        Tab.SERIES -> "series"
    }

    /** Nó folha remoto => Item reproduzível (título + URL direta). */
    private fun toItem(node: OnePlayNodeDto, fallbackKind: String): RepoTreeNode.Item? {
        val url = node.url ?: return null
        if (url.isBlank()) return null
        val title = node.name ?: return null
        val kind = node.kind?.takeIf { it == "channel" || it == "movie" || it == "series" } ?: fallbackKind
        return RepoTreeNode.Item(kind, node.id ?: url, title, url)
    }

    private fun foldersOf(tab: Tab): List<RepoTreeNode.Folder> {
        val names: List<String> = when (tab) {
            Tab.CHANNELS -> channels.value
                .filter { it.categoryId.isNotBlank() }
                .map { it.categoryId }
            Tab.MOVIES -> movies.value
                .filter { it.categoryId.isNotBlank() }
                .map { it.categoryId }
            Tab.SERIES -> series.value
                .filter { it.categoryId.isNotBlank() }
                .map { it.categoryId }
        }.distinct().sorted()
        return names.map { name -> RepoTreeNode.Folder(name, tab) }
    }

    private fun itemsOf(folder: RepoTreeNode.Folder): List<RepoTreeNode.Item> = when (folder.tab) {
        Tab.CHANNELS -> channels.value
            .filter { it.categoryId == folder.name }
            .sortedBy { it.name }
            .map { RepoTreeNode.Item("channel", it.id, it.name, it.streamUrl, it.headers, it.favKey) }
        Tab.MOVIES -> movies.value
            .filter { it.categoryId == folder.name }
            .sortedBy { it.title }
            .map { RepoTreeNode.Item("movie", it.id, it.title, it.streamUrl, it.headers, it.favKey) }
        Tab.SERIES -> series.value
            .filter { it.categoryId == folder.name }
            .sortedBy { it.title }
            .map { RepoTreeNode.Item("series", it.id, it.title, it.streamUrl, it.headers, it.favKey) }
    }

    fun navigateInto(node: RepoTreeNode) {
        _ui.update { it.copy(path = it.path + node) }
        _ui.update { it.copy(children = childrenOf(node)) }
    }

    fun goBack() {
        val path = _ui.value.path
        if (path.size > 1) {
            val parent = path.dropLast(1).last()
            _ui.update { it.copy(path = path.dropLast(1), children = childrenOf(parent)) }
        }
    }

    // ── Seleção de conteúdo: destino (reproduzir) ou salvamento na seção da categoria ──

    private val _pending = MutableStateFlow<RepoTreeNode.Item?>(null)
    val pending = _pending.asStateFlow()

    /** Abre o diálogo de destino para o item escolhido na árvore. */
    fun select(node: RepoTreeNode.Item) {
        _pending.value = node
    }

    fun dismissSelection() {
        _pending.value = null
    }

    /** Reproduz o item escolhido (séries carregam temporadas/episódios sob demanda). */
    fun confirmPlay(onPlay: (PlaybackRequest) -> Unit) {
        val node = _pending.value ?: return
        _pending.value = null
        when (node.mediaKind) {
            "series" -> playSeries(node, onPlay)
            else -> onPlay(toRequest(node))
        }
    }

    /** Prefixo de id favorito por categoria: channel→c:, movie→m:, series→s:. */
    private fun favoriteId(node: RepoTreeNode.Item): String {
        val prefix = when (node.mediaKind) {
            "channel" -> "c:"
            "series" -> "s:"
            else -> "m:"
        }
        return prefix + node.favKey.ifBlank { node.id }
    }

    /** Seção de destino do conteúdo conforme a categoria. */
    fun sectionOf(node: RepoTreeNode.Item): String = when (node.mediaKind) {
        "channel" -> "Canais"
        "series" -> "Séries"
        else -> "Filmes"
    }

    fun isSaved(node: RepoTreeNode.Item): Boolean = favoriteId(node) in repo.favoriteIds.value

    /**
     * Salva o item na seção da sua categoria (Canais/Filmes/Séries); se já estava
     * salvo, remove. O resultado vai para [message] (toast na UI).
     */
    fun saveSelected() {
        val node = _pending.value ?: return
        _pending.value = null
        val id = favoriteId(node)
        val section = sectionOf(node)
        val wasSaved = isSaved(node)
        runCatching { repo.toggleFavorite(id) }.onSuccess {
            _message.value = if (wasSaved) {
                "Removido de $section: ${node.title}"
            } else {
                "Salvo em $section: ${node.title}"
            }
        }.onFailure {
            _message.value = "Falha ao salvar em $section: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun playSeries(node: RepoTreeNode.Item, onPlay: (PlaybackRequest) -> Unit) {
        if (!node.streamUrl.isNullOrBlank()) {
            onPlay(toRequest(node))
            return
        }
        viewModelScope.launch {
            runCatching {
                val seasons = repo.loadSeasons(node.id)
                val season = seasons.firstOrNull() ?: error("Sem temporadas para ${node.title}")
                val ep = season.episodes.firstOrNull() ?: error("Sem episódios para ${node.title}")
                onPlay(
                    PlaybackRequest(
                        "${node.title} · T${season.number}E${ep.number}",
                        ep.streamUrl ?: "",
                        node.headers,
                        ep.id,
                        "series",
                    ),
                )
            }.onFailure {
                _message.value = it.message ?: "Não foi possível reproduzir ${node.title}"
            }
        }
    }

    private fun toRequest(node: RepoTreeNode.Item) = PlaybackRequest(
        node.title,
        node.streamUrl ?: "",
        node.headers,
        node.favKey,
        node.mediaKind,
        isLive = node.mediaKind == "channel",
    )
}
