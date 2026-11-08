package com.smarted.tv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarted.tv.core.data.ContentRepository
import com.smarted.tv.core.model.*
import com.smarted.tv.core.util.normalized
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortKey(val label: String, val defaultAscending: Boolean) {
    AZ("De A a Z", true),
    RECENT("Últimos adicionados", false),
    TOP("Mais votados", false),
}

/** Escopo estrito: uma instância por [kind]; Filmes nunca enxergam Séries (e nunca Canais). */
class VodViewModel(private val repo: ContentRepository, val kind: VodKind) : ViewModel() {

    data class Filters(
        val categoryId: String = CAT_ALL,
        val categoryQuery: String = "",
        val titleQuery: String = "",
        val sort: SortKey = SortKey.RECENT,
        val ascending: Boolean = false,
    )

    data class Ui(
        val loaded: Boolean = false,
        val categories: List<Category> = emptyList(),
        val filters: Filters = Filters(),
        val items: List<VodItem> = emptyList(),
        val favoriteIds: Set<String> = emptySet(),
        val selected: VodItem? = null,
    )

    private val filters = MutableStateFlow(Filters())
    private val all = MutableStateFlow<List<VodItem>?>(null)
    private val cats = MutableStateFlow<List<Category>>(emptyList())
    private val selectedId = MutableStateFlow<String?>(null)

    val ui: StateFlow<Ui> = combine(filters, all, cats, repo.favoriteIds, selectedId) { f, items, c, favs, sel ->
        val list = items.orEmpty()
        val q = f.titleQuery.normalized()
        val filtered = list.asSequence()
            .filter {
                when (f.categoryId) {
                    CAT_ALL -> true
                    CAT_FAVORITES -> it.id in favs
                    else -> it.categoryId == f.categoryId
                }
            }
            .filter { q.isBlank() || it.title.normalized().contains(q) }
            .toList()

        val base: Comparator<VodItem> = when (f.sort) {
            SortKey.AZ -> compareBy<VodItem> { it.title.normalized() }
            SortKey.RECENT -> compareBy<VodItem> { it.addedAtMs }
            SortKey.TOP -> compareBy<VodItem> { it.rating ?: 0.0 }
        }
        val sorted = filtered.sortedWith(if (f.ascending) base else base.reversed())

        val cq = f.categoryQuery.normalized()
        val virtual = listOf(Category(CAT_FAVORITES, "Favoritos"), Category(CAT_ALL, "Todos"))
        val visibleCats = virtual + c.filter { cq.isBlank() || it.name.normalized().contains(cq) }

        Ui(
            loaded = items != null, categories = visibleCats, filters = f, items = sorted,
            favoriteIds = favs, selected = sel?.let { id -> list.firstOrNull { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    init {
        reload()
        viewModelScope.launch { repo.revision.collect { reload() } }
    }

    private fun reload() = viewModelScope.launch {
        runCatching {
            cats.value = repo.vodCategories(kind)
            all.value = repo.vod(kind)
            selectedId.value?.let { ensureSeasons(it) }
        }
    }

    /** Temporadas de série chegam sob demanda (o banco só tem as já vistas). */
    private fun ensureSeasons(id: String) {
        val current = all.value?.firstOrNull { it.id == id } ?: return
        if (current.kind != VodKind.SERIES || current.seasons.isNotEmpty()) return
        viewModelScope.launch {
            runCatching {
                val seasons = repo.loadSeasons(id)
                if (seasons.isNotEmpty()) {
                    all.update { list -> list?.map { if (it.id == id) it.copy(seasons = seasons) else it } }
                }
            }
        }
    }

    fun selectCategory(id: String) = filters.update { it.copy(categoryId = id) }
    fun setCategoryQuery(q: String) = filters.update { it.copy(categoryQuery = q) }
    fun setTitleQuery(q: String) = filters.update { it.copy(titleQuery = q) }
    fun setSort(k: SortKey) = filters.update { it.copy(sort = k, ascending = k.defaultAscending) }
    fun toggleDirection() = filters.update { it.copy(ascending = !it.ascending) }
    fun select(item: VodItem?) { selectedId.value = item?.id; item?.let { ensureSeasons(it.id) } }
    fun open(id: String) { selectedId.value = id; ensureSeasons(id) }
    fun toggleFavorite(id: String) = repo.toggleFavorite(id)
}
