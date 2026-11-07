package com.smarted.tv.core

import com.smarted.tv.core.catalog.VodQuery
import com.smarted.tv.core.epg.EpgMatcher
import com.smarted.tv.core.epg.EpgTimeline
import com.smarted.tv.core.model.BrowseQuery
import com.smarted.tv.core.model.EpgChannelRef
import com.smarted.tv.core.model.EpgLink
import com.smarted.tv.core.model.LiveChannel
import com.smarted.tv.core.model.Programme
import com.smarted.tv.core.model.SortDirection
import com.smarted.tv.core.model.SortMode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.model.VodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EpgMatcherTest {
    private val refs = listOf(
        EpgChannelRef("globo.sp", listOf("Globo SP", "TV Globo São Paulo")),
        EpgChannelRef("telecine-pipoca", listOf("Telecine Pipoca HD")),
        EpgChannelRef("band-4k", listOf("Band 4K")),
        EpgChannelRef("hbo", listOf("HBO", "HBO Brasil")),
    )

    @Test fun `tvg id normalizes and matches`() {
        val link = EpgMatcher.plan("globo sp", "Qualquer Nome", refs)
        assertEquals("globo.sp", link?.epgChannel)
        assertEquals(100, link?.confidence)
    }

    @Test fun `quality suffixes do not break name match`() {
        assertNotNull(EpgMatcher.plan("", "TELECINE PIPOCA FHD 1080p", refs))
        assertEquals("telecine-pipoca", EpgMatcher.plan("", "Telecine Pipoca HD", refs)?.epgChannel)
    }

    @Test fun `no match returns null instead of inventing`() {
        assertNull(EpgMatcher.plan("", "Canal Aleatório 999xyz", refs))
    }

    @Test fun `self-healing keeps valid previous link`() {
        val kept = EpgMatcher.plan("", "Band", refs, previous = EpgLink("band", "band-4k", 96))
        assertEquals("band-4k", kept?.epgChannel)
        val orphan = EpgMatcher.plan("", "Band", emptyList(), previous = EpgLink("band", "band-4k", 96))
        assertNull(orphan)
    }

    @Test fun `build plans uses previous links map keyed by fav key`() {
        val channels = listOf(
            Triple("ch:1:globo", "globo sp", "Globo"),
            Triple("ch:1:hbo", "", "HBO Brasil"),
            Triple("ch:1:desconhecido", "", "Canal Sem EPG Real"),
        )
        val plan = EpgMatcher.buildPlans(channels, refs, mapOf("ch:1:hbo" to EpgLink("ch:1:hbo", "hbo", 90)))
        assertEquals(listOf("globo.sp", "hbo"), plan.links.map { it.epgChannel })
        assertEquals(listOf("ch:1:desconhecido"), plan.unmatched)
    }
}

class EpgTimelineTest {
    private val now = 1_794_065_400_000L

    private fun programme(offsetMin: Long, durationMin: Long, title: String) =
        Programme("c", now + offsetMin * 60_000L, now + (offsetMin + durationMin) * 60_000L, title)

    @Test fun `now next and progress`() {
        val list = listOf(programme(-10, 60, "Atual"), programme(50, 40, "Depois"), programme(90, 30, "Mais tarde"))
        val result = EpgTimeline.nowNext(list, now)
        assertEquals("Atual", result.now?.title)
        assertEquals("Depois", result.next?.title)
        assertEquals(16, result.progressPercent)
    }

    @Test fun `gap between programmes picks next starting one`() {
        val list = listOf(programme(-120, 30, "Passado"), programme(30, 60, "Futuro"))
        val result = EpgTimeline.nowNext(list, now)
        assertNull(result.now)
        assertEquals("Futuro", result.next?.title)
    }

    @Test fun `grid starts at current and respects limit`() {
        val list = (0 until 10).map { programme(-30L + it * 60L, 60, "P$it") }
        val grid = EpgTimeline.upcoming(list, now, limit = 4)
        assertEquals(listOf("P0", "P1", "P2", "P3"), grid.map { it.title })
    }

    @Test fun `rows join channels with timeline and favorites`() {
        val channel = LiveChannel(1, 1, "ch:1:x", 10, "Canal X", "Grupo", null, "http://a/b", "x", emptyMap())
        val rows = EpgTimeline.rows(
            listOf(channel), mapOf(1L to "c"), mapOf("c" to listOf(programme(-5, 60, "Show"))), setOf("ch:1:x"), now,
        )
        assertEquals("Show", rows.single().now?.title)
        assertEquals(true, rows.single().isFavorite)
    }
}

class VodQueryTest {
    private fun entry(name: String, cat: String, added: Long, rating: Double?, year: Int?) = VodEntry(
        id = name.hashCode().toLong(), sourceId = 1, favKey = "vod:1:${name.lowercase()}", kind = Tab.MOVIES,
        externalKey = name, name = name, category = cat, year = year, rating = rating, addedMs = added,
        posterUrl = null, backdropUrl = null, plot = "", streamUrl = "http://x/$name.mp4", runtimeMinutes = null,
    )

    private val entries = listOf(
        entry("Zebra o Filme", "Lançamentos", 3, 8.0, 2024),
        entry("a Abelha", "Clássicos", 1, 9.0, 1999),
        entry("Meio Termo", "Clássicos", 2, 5.0, 2005),
    )

    @Test fun `az ignores case and accents`() {
        val result = VodQuery.order(entries, BrowseQuery(sort = SortMode.NAME, direction = SortDirection.ASC))
        assertEquals(listOf("a Abelha", "Meio Termo", "Zebra o Filme"), result.map { it.name })
    }

    @Test fun `direction toggles ordering`() {
        val desc = VodQuery.order(entries, BrowseQuery(sort = SortMode.ADDED, direction = SortDirection.DESC))
        assertEquals("Zebra o Filme", desc.first().name)
        val asc = VodQuery.order(entries, BrowseQuery(sort = SortMode.ADDED, direction = SortDirection.ASC))
        assertEquals("a Abelha", asc.first().name)
    }

    @Test fun `rating missing sorts last then name tiebreak`() {
        val withNull = entries + entry("Sem Nota", "Clássicos", 4, null, null)
        val top = VodQuery.order(withNull, BrowseQuery(sort = SortMode.RATING, direction = SortDirection.DESC))
        assertEquals("a Abelha", top.first().name)
        assertEquals("Sem Nota", top.last().name)
    }

    @Test fun `category and search filters`() {
        assertEquals(2, VodQuery.order(entries, BrowseQuery(category = "Clássicos")).size)
        assertEquals(1, VodQuery.order(entries, BrowseQuery(search = "zebra filme")).size)
    }

    @Test fun `categories counted and filtered`() {
        val cats = VodQuery.categories(entries, "lanc")
        assertEquals(1, cats.size)
        assertEquals("Lançamentos", cats.single().name)
        assertEquals(2, VodQuery.categories(entries).first().count)
    }
}
