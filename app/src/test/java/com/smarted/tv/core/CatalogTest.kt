package com.smarted.tv.core

import com.smarted.tv.core.catalog.*
import com.smarted.tv.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    private val addon = testAddon(Scope.MOVIES)
    @Test fun paginatesByRawLengthNotDeduplicatedLength() = runTest {
        val api = FakeApi().apply { pages = { _, extra ->
            when (extra["skip"]?.toInt() ?: 0) { 0 -> listOf(testItem(addon, "a"), testItem(addon, "b")); 2 -> listOf(testItem(addon, "b"), testItem(addon, "c")); else -> emptyList() }
        } }
        val pager = CatalogPager(api)
        val first = pager.load(listOf(addon), Scope.MOVIES, CatalogQuery(), null, emptySet())
        assertEquals(2, first.items.size)
        val second = pager.load(listOf(addon), Scope.MOVIES, CatalogQuery(), first.nextCursor, first.items.map { it.key }.toSet())
        assertEquals(listOf("c"), second.items.map { it.id })
        assertEquals(4L, second.nextCursor!!.sources[0].skip)
        val end = pager.load(listOf(addon), Scope.MOVIES, CatalogQuery(), second.nextCursor, setOf("a", "b", "c").map { "${addon.url}|movie|$it" }.toSet())
        assertNull(end.nextCursor)
        assertEquals(listOf(null, "2", "4"), api.requests.map { it.second["skip"] })
    }
    @Test fun nonPagedCatalogIsRequestedExactlyOnce() = runTest {
        val unpaged = testAddon(Scope.MOVIES, catalogs = listOf(CatalogSpec("movie", "top")))
        val api = FakeApi().apply { pages = { _, _ -> listOf(testItem(unpaged, "a")) } }
        val page = CatalogPager(api).load(listOf(unpaged), Scope.MOVIES, CatalogQuery(), null, emptySet())
        assertNull(page.nextCursor) // null is the end-of-catalog signal; a new null-cursor load is a fresh query.
        assertEquals(1, api.requests.size)
    }
    @Test fun brokenRepeatedPaginationTerminates() = runTest {
        val api = FakeApi().apply { pages = { _, _ -> listOf(testItem(addon, "a")) } }
        val page = CatalogPager(api).load(listOf(addon), Scope.MOVIES, CatalogQuery(), PageCursor(listOf(SourceProgress(1, false, "${addon.url}|movie|a"))), emptySet())
        assertNull(page.nextCursor)
        assertTrue(page.warnings.single().contains("repetida"))
    }
    @Test fun scopeIsolationAndRequiredSearch() = runTest {
        val movies = testAddon(Scope.MOVIES)
        val series = testAddon(Scope.SERIES)
        val special = testAddon(Scope.MOVIES, "https://search.example/manifest.json", listOf(CatalogSpec("movie", "search", extra = listOf(ExtraSpec("search", true)))))
        val api = FakeApi().apply { pages = { catalog, _ -> listOf(MediaItem("https://$catalog.example/manifest.json", catalog.id, catalog.type, Scope.fromWire(catalog.type)!!, catalog.id)) } }
        val page = CatalogPager(api).load(listOf(movies, series, special), Scope.MOVIES, CatalogQuery(), null, emptySet())
        assertEquals(listOf("top"), api.requests.map { it.first })
        assertEquals(setOf(Scope.MOVIES), page.items.map { it.scope }.toSet())
        CatalogPager(api).load(listOf(special), Scope.MOVIES, CatalogQuery(search = "ação"), null, emptySet())
        assertEquals("ação", api.requests.last().second["search"])
    }
    @Test fun filtersAccentAndCaseInsensitive() = runTest {
        val api = FakeApi().apply { pages = { _, _ -> listOf(testItem(addon, "1", "Ação TOTAL", listOf("Ação")), testItem(addon, "2", "Comédia", listOf("Comédia"))) } }
        val page = CatalogPager(api).load(listOf(addon), Scope.MOVIES, CatalogQuery(search = "acao", category = "AÇÃO"), null, emptySet())
        assertEquals(listOf("1"), page.items.map { it.id })
    }
    @Test fun sortIsStableAndExplicit() {
        val items = listOf(testItem(addon, "b", "Água").copy(rating = 9.0, releaseInfo = "2020"), testItem(addon, "a", "Amor").copy(rating = 7.0, releaseInfo = "2024"))
        assertEquals(listOf("b", "a"), sortItems(items, Sort.AZ, Direction.ASC).map { it.id })
        assertEquals(listOf("b", "a"), sortItems(items, Sort.RATING, Direction.DESC).map { it.id })
        assertEquals(listOf("a", "b"), sortItems(items, Sort.YEAR, Direction.DESC).map { it.id })
        assertEquals(listOf("a", "b"), sortItems(items, Sort.AZ, Direction.DESC).map { it.id })
    }
    @Test fun aggregatesMultipleCatalogsWithoutMixingOffsets() = runTest {
        val multi = testAddon(Scope.MOVIES, catalogs = listOf(CatalogSpec("movie", "a"), CatalogSpec("movie", "b")))
        val api = FakeApi().apply { pages = { catalog, _ -> listOf(testItem(multi, catalog.id)) } }
        val page = CatalogPager(api).load(listOf(multi), Scope.MOVIES, CatalogQuery(), null, emptySet())
        assertEquals(listOf("a", "b"), page.items.map { it.id })
        assertNull(page.nextCursor)
    }
}
