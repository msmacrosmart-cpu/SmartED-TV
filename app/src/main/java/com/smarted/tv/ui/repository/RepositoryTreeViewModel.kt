package com.smarted.tv.ui.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.Channel
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.onFailure

class RepositoryTreeViewModel(private val repo: ContentRepository) : ViewModel() {

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

        data class Folder(val name: String, val tab: Tab) : RepoTreeNode() {
            override fun key() = "folder|${tab.name}|$name"
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

    init {
        reload()
        viewModelScope.launch { repo.revision.collect { reload() } }
    }

    private fun reload() = viewModelScope.launch {
        runCatching {
            channels.value = repo.channels()
            movies.value = repo.vod(VodKind.MOVIE)
            series.value = repo.vod(VodKind.SERIES)
            _ui.update { it.copy(loaded = true, children = childrenOf(it.path.last())) }
        }.onFailure {
            _message.value = "Falha ao carregar o repositório: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun childrenOf(node: RepoTreeNode): List<RepoTreeNode> = when (node) {
        RepoTreeNode.Root -> listOf(RepoTreeNode.Addons)
        RepoTreeNode.Addons -> listOf(
            RepoTreeNode.Category(Tab.CHANNELS, "TV"),
            RepoTreeNode.Category(Tab.MOVIES, "Filmes"),
            RepoTreeNode.Category(Tab.SERIES, "Séries"),
        )
        is RepoTreeNode.Category -> foldersOf(node.tab)
        is RepoTreeNode.Folder -> itemsOf(node)
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
        if (node.streamUrl.isNotBlank()) {
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
