package com.smarted.tv.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.normalized
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Escopo estrito: este ViewModel só conhece CANAIS. A busca nunca toca em VOD. */
class ChannelsViewModel(private val repo: ContentRepository) : ViewModel() {

    data class Ui(
        val loaded: Boolean = false,
        val categories: List<Category> = emptyList(),
        val selectedId: String = CAT_ALL,
        val query: String = "",
        val channels: List<Channel> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
    )

    private val allChannels = MutableStateFlow<List<Channel>?>(null)
    private val providerCats = MutableStateFlow<List<Category>>(emptyList())
    private val selectedId = MutableStateFlow(CAT_ALL)
    private val query = MutableStateFlow("")
    private val _focusedId = MutableStateFlow<String?>(null)
    val focusedId = _focusedId.asStateFlow()

    private val _upcoming = MutableStateFlow<List<EpgProgram>>(emptyList())
    val upcoming = _upcoming.asStateFlow()

    val ui: StateFlow<Ui> = combine(allChannels, providerCats, selectedId, query, repo.favoriteIds) { all, cats, sel, q, favs ->
        val list = all.orEmpty()
        val shown = when {
            // Com texto na busca: procura em TODOS os canais (ignora a categoria).
            q.isNotBlank() -> list.filter { it.name.normalized().contains(q.normalized()) }
            sel == CAT_ALL -> list
            sel == CAT_FAVORITES -> list.filter { it.id in favs }
            else -> list.filter { it.categoryId == sel }
        }
        Ui(
            loaded = all != null,
            categories = listOf(Category(CAT_FAVORITES, "Favoritos"), Category(CAT_ALL, "Todos os canais")) + cats,
            selectedId = sel, query = q, channels = shown, favoriteIds = favs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    init {
        reload()
        viewModelScope.launch { repo.revision.collect { reload() } }
    }

    private fun reload() = viewModelScope.launch {
        runCatching {
            providerCats.value = repo.channelCategories()
            allChannels.value = repo.channels()
        }
    }

    fun selectCategory(id: String) { selectedId.value = id; query.value = "" }
    fun setQuery(q: String) { query.value = q }
    fun focus(id: String) {
        _focusedId.value = id
        viewModelScope.launch {
            runCatching { _upcoming.value = repo.channelUpcoming(id, System.currentTimeMillis(), 12) }
        }
    }
    fun toggleFavorite(id: String) = repo.toggleFavorite(id)
}
