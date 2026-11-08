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
 * [Repositório] → [Addons] → [TV / Filmes / Séries] → [subpastas] → [título].
 *
 * As pastas/títulos vêm do banco local (importação das fontes) e, em melhor esforço,
 * da árvore recursiva do índice OnePlay lida via [OnePlayTreeClient] (Retrofit +
 * kotlinx-serialization). Falha de rede/índice => `null` => apenas árvore local.
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

        RepoTreeNode.Addons -> defaultCategories() + remoteExtraCategories()

        is RepoTreeNode.Category -> {
            val base = foldersOf(node.tab)
            val seen = base.map { it.name.normalized() }.toMutableSet()
            val remoteChildren = remoteCategoryNode(node.tab, node.name)?.children.orEmpty()
            val extras = remoteChildren.mapNotNull { child ->
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

    /** Caminho obrigatório da UI: Addons → TV / Filmes / Séries (nunca depende da rede). */
    private fun defaultCategories() = listOf(
        RepoTreeNode.Category(Tab.CHANNELS, "TV"),
        RepoTreeNode.Category(Tab.MOVIES, "Filmes"),
        RepoTreeNode.Category(Tab.SERIES, "Séries"),
    )

    /** Categorias extras que o índice remoto trouxer (as 3 canônicas sempre vêm primeiro). */
    private fun remoteExtraCategories(): List<RepoTreeNode> {
        val remote = remoteRoot?.children ?: return emptyList()
        val baseTabs = defaultCategories().map { it.tab }.toSet()
        return remote.mapNotNull { child ->
            val tab = tabOf(child) ?: return@mapNotNull null
            val name = child.name ?: return@mapNotNull null
            RepoTreeNode.Category(tab, name)
        }.filter { cat -> cat.tab !in baseTabs }
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

    private fun foldersOf(tab: Tab): List<RepoTreeNode> {
        val names = when (tab) {
            Tab.CHANNELS -> channels.value
                .filter { it.categoryId.isNotBlank() }
                .distinctBy { it.categoryId }
                .sortedBy { it.categoryId }
            Tab.MOVIES -> movies.value
                .filter { it.categoryId.isNotBlank() }
                .distinctBy { it.categoryId }
                .sortedBy { it.categoryId }
            Tab.SERIES -> series.value
                .filter { it.categoryId.isNotBlank() }
                .distinctBy { it.categoryId }
                .sortedBy { it.categoryId }
        }
        return names.map { RepoTreeNode.Folder(it, tab) }
    }

    private fun itemsOf(folder: RepoTreeNode.Folder): List<RepoTreeNode> = when (folder.tab) {
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

    fun select(node: RepoTreeNode.Item, onPlay: (PlaybackRequest) -> Unit) {
        when (node.mediaKind) {
            "series" -> playSeries(node, onPlay)
            else -> onPlay(toRequest(node))
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
