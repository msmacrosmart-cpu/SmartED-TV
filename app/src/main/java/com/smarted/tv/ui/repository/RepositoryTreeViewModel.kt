package com.smarted.tv.ui.repository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.PlaybackRequest
import com.smarted.tv.core.model.RepositoryNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Navegação por árvore recursiva de repositórios (estilo Kodi) sobre [RepositoryNode]:
 * [Repositório] → [Addons] → pastas/sub-repositórios/ferramentas/add-ons → [item].
 *
 * Cada nó aberto carrega seus `children` DINAMICAMENTE via
 * [ContentRepository.repositoryChildren] (índice OnePlay + banco local) — a UI
 * nunca cai em abas estáticas; o destino do conteúdo (Canais/Filmes/Séries) só
 * aparece no diálogo ao selecionar um item final.
 */
class RepositoryTreeViewModel(
    private val repo: ContentRepository,
) : ViewModel() {

    data class Ui(
        val loaded: Boolean = false,
        val path: List<RepositoryNode> = emptyList(),
        val children: List<RepositoryNode> = emptyList(),
    )

    /** Raiz sintética inicial (evita `path.last()` vazio antes do primeiro load). */
    private val defaultRoot = RepositoryNode(
        id = "root",
        name = "Repositório",
        kind = RepositoryNode.Kind.ROOT,
    )

    private val _ui = MutableStateFlow(Ui(path = listOf(defaultRoot)))
    val ui = _ui.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    init {
        viewModelScope.launch {
            val root = runCatching { repo.repositoryRoot() }.getOrElse {
                _message.value = "Falha ao carregar o repositório: ${it.message ?: it.javaClass.simpleName}"
                RepositoryNode(id = "root", name = "Repositório", kind = RepositoryNode.Kind.ROOT)
            }
            _ui.value = Ui(loaded = true, path = listOf(root), children = loadChildren(root))
        }
        viewModelScope.launch {
            repo.revision.collect {
                val current = _ui.value.path.lastOrNull() ?: return@collect
                val kids = loadChildren(current)
                _ui.update { ui -> ui.copy(children = kids) }
            }
        }
    }

    /** Filhos dinâmicos do nó (sempre sob demanda); falha vira toast + nível vazio. */
    private suspend fun loadChildren(node: RepositoryNode): List<RepositoryNode> =
        runCatching { repo.repositoryChildren(node) }.getOrElse {
            _message.value = "Falha ao carregar o repositório: ${it.message ?: it.javaClass.simpleName}"
            emptyList()
        }

    fun navigateInto(node: RepositoryNode) {
        _ui.update { it.copy(path = it.path + node, children = emptyList(), loaded = false) }
        viewModelScope.launch {
            val kids = loadChildren(node)
            _ui.update { it.copy(loaded = true, children = kids) }
        }
    }

    fun goBack() {
        val path = _ui.value.path
        if (path.size <= 1) return
        val parent = path[path.lastIndex - 1]
        _ui.update { it.copy(path = path.dropLast(1), children = emptyList(), loaded = false) }
        viewModelScope.launch {
            val kids = loadChildren(parent)
            _ui.update { it.copy(loaded = true, children = kids) }
        }
    }

    // ── Seleção de conteúdo: destino (reproduzir) ou salvamento na seção da categoria ──

    private val _pending = MutableStateFlow<RepositoryNode?>(null)
    val pending = _pending.asStateFlow()

    /** Abre o diálogo de destino para o item escolhido na árvore. */
    fun select(node: RepositoryNode) {
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
    private fun favoriteId(node: RepositoryNode): String {
        val prefix = when (node.mediaKind) {
            "channel" -> "c:"
            "series" -> "s:"
            else -> "m:"
        }
        return prefix + node.favKey.ifBlank { node.id }
    }

    /** Seção de destino do conteúdo conforme a categoria. */
    fun sectionOf(node: RepositoryNode): String = when (node.mediaKind) {
        "channel" -> "Canais"
        "series" -> "Séries"
        else -> "Filmes"
    }

    fun isSaved(node: RepositoryNode): Boolean = favoriteId(node) in repo.favoriteIds.value

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
                "Removido de $section: ${node.name}"
            } else {
                "Salvo em $section: ${node.name}"
            }
        }.onFailure {
            _message.value = "Falha ao salvar em $section: ${it.message ?: it.javaClass.simpleName}"
        }
    }

    private fun playSeries(node: RepositoryNode, onPlay: (PlaybackRequest) -> Unit) {
        if (!node.url.isNullOrBlank()) {
            onPlay(toRequest(node))
            return
        }
        viewModelScope.launch {
            runCatching {
                val seasons = repo.loadSeasons(node.id)
                val season = seasons.firstOrNull() ?: error("Sem temporadas para ${node.name}")
                val ep = season.episodes.firstOrNull() ?: error("Sem episódios para ${node.name}")
                onPlay(
                    PlaybackRequest(
                        "${node.name} · T${season.number}E${ep.number}",
                        ep.streamUrl ?: "",
                        node.headers,
                        ep.id,
                        "series",
                    ),
                )
            }.onFailure {
                _message.value = it.message ?: "Não foi possível reproduzir ${node.name}"
            }
        }
    }

    private fun toRequest(node: RepositoryNode) = PlaybackRequest(
        node.name,
        node.url ?: "",
        node.headers,
        node.favKey,
        node.mediaKind ?: "movie",
        isLive = node.mediaKind == "channel",
    )
}
