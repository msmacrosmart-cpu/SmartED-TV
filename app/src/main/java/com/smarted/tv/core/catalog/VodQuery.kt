package com.smarted.tv.core.catalog

import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.VodEntry
import com.smarted.tv.core.text.TextNormalizer

/**
 * Ordenação/filtro compartilhados por tela e por testes. O banco replica estas chaves para
 * paginação (SQL: norm_name, added_ms, rating, year), então o resultado é idêntico com 30 mil itens.
 */
object VodQuery {

    fun sortKey(entry: VodEntry, mode: SortMode): Comparable<*> = when (mode) {
        SortMode.NAME -> TextNormalizer.normalize(entry.name).ifBlank { entry.name.lowercase() }
        SortMode.ADDED -> entry.addedMs
        SortMode.RATING -> entry.rating ?: -1.0
        SortMode.YEAR -> entry.year ?: 0
    }

    fun applies(entry: VodEntry, query: BrowseQuery, favorites: Set<Long>, entryId: (VodEntry) -> Long): Boolean {
        if (!query.isFavorites && query.category != BrowseQuery.ALL &&
            TextNormalizer.categoryKey(entry.category) != TextNormalizer.categoryKey(query.category)
        ) return false
        if (query.isFavorites && entryId(entry) !in favorites) return false
        if (query.search.isNotBlank() && !TextNormalizer.searchMatches(query.search, entry.name)) return false
        return true
    }

    @Suppress("UNCHECKED_CAST")
    fun compare(a: VodEntry, b: VodEntry, mode: SortMode, direction: SortDirection): Int {
        val keyA = sortKey(a, mode)
        val keyB = sortKey(b, mode)
        val base = (keyA as Comparable<Any>).compareTo(keyB as Any)
        if (base != 0) return if (direction == SortDirection.DESC) -base else base
        // desempate canônico por nome normalizado (estável, independente de ordem)
        return TextNormalizer.normalize(a.name).compareTo(TextNormalizer.normalize(b.name))
    }

    fun order(entries: List<VodEntry>, query: BrowseQuery, favorites: Set<Long> = emptySet()): List<VodEntry> {
        val filtered = entries.filter { applies(it, query, favorites) { e -> e.id } }
        val sorted = filtered.sortedWith { a, b -> compare(a, b, query.sort, query.direction) }
        return sorted
    }

    /** Categorias com contagem, ordenadas por volume e nome (coluna 1). */
    data class CategoryCount(val name: String, val count: Int)

    fun categories(entries: List<VodEntry>, search: String = ""): List<CategoryCount> {
        val grouped = entries.groupingBy { it.category.ifBlank { "Sem categoria" } }.eachCount()
        return grouped.entries
            .map { (name, count) -> CategoryCount(name, count) }
            .filter { search.isBlank() || TextNormalizer.searchMatches(search, it.name) }
            .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { TextNormalizer.normalize(it.name) })
    }
}
