package com.smarted.tv.core

import com.smarted.tv.core.catalog.ChannelGuide
import com.smarted.tv.core.catalog.programWindow
import com.smarted.tv.core.model.*
import com.smarted.tv.core.playback.PlaybackResolver
import com.smarted.tv.core.protocol.AddonException
import com.smarted.tv.core.protocol.AddonUrls
import com.smarted.tv.core.protocol.StremioClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class HttpAndPlaybackTest {
    @Test fun realHttpRoundTripAndEncodedExtras() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("""{"id":"x","name":"X","version":"1","catalogs":[]}"""))
            server.enqueue(MockResponse().setBody("""{"metas":[{"id":"a/b","type":"movie","name":"A & B"}]}"""))
            StremioClient().use { client ->
                val url = server.url("/addon/manifest.json").toString()
                assertEquals("x", client.manifest(url).id)
            }
            assertEquals("/addon/manifest.json", server.takeRequest().path)
            // A separate server/client avoids consuming catalog response as a manifest.
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"metas":[{"id":"a/b","type":"movie","name":"A & B"}]}"""))
            StremioClient().use { client ->
                val addon = testAddon(Scope.MOVIES, server.url("/manifest.json").toString())
                val items = client.catalog(addon, addon.manifest.catalogs.first(), mapOf("search" to "ação & aventura", "skip" to "100"))
                assertEquals("items=" + items + " url=" + AddonUrls.resource(addon.url, "catalog", addon.manifest.catalogs.first().type, addon.manifest.catalogs.first().id, mapOf("search" to "ação & aventura", "skip" to "100")), "A & B", items.single().name)
                assertEquals("/catalog/movie/top/search=a%C3%A7%C3%A3o%20%26%20aventura&skip=100.json", server.takeRequest().path)
            }
        }
    }
    @Test fun serverErrorsAreNotMisreportedAsJsonErrors() = runTest {
        MockWebServer().use { server ->
            listOf(404 to "não encontrado", 401 to "Acesso negado", 429 to "limitou", 503 to "indisponível").forEach { (code, message) ->
                server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))
                val error = assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { StremioClient().manifest(server.url("/manifest.json").toString()) } }
                assertTrue(error.message!!.contains(message))
                assertFalse(error.message!!.contains("JSON inválido"))
            }
        }
    }
    @Test fun httpHtmlAndOversizedResponsesAreActionable() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("<html>Not found</html>"))
            server.enqueue(MockResponse().setBody(" " + "x".repeat(8 * 1024 * 1024 + 1)))
            StremioClient().use { client ->
                assertTrue(assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { client.manifest(server.url("/manifest.json").toString()) } }.message!!.contains("HTML"))
                assertTrue(assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { client.manifest(server.url("/manifest.json").toString()) } }.message!!.contains("muito grande"))
            }
        }
    }
    @Test fun epgUsesExactHalfOpenIntervalsAndChronology() {
        val videos = listOf(Video("next", "Next", startTime = "2026-11-07T11:00:00Z", endTime = "2026-11-07T12:00:00Z"),
            Video("now", "Now", startTime = "2026-11-07T10:00:00Z", endTime = "2026-11-07T11:00:00Z"), Video("bad", endTime = "invalid"))
        val guide = programWindow(videos, java.time.Instant.parse("2026-11-07T10:30:00Z"))
        assertEquals("now", guide.current!!.id); assertEquals(listOf("next"), guide.upcoming.map { it.id })
        assertEquals("next", programWindow(videos, java.time.Instant.parse("2026-11-07T11:00:00Z")).current!!.id)
        assertNull(programWindow(videos, java.time.Instant.parse("2026-11-07T12:00:00Z")).current)
    }
    @Test fun nativeGuideRequestsDateAndPreservesChannelId() = runTest {
        val dateAddon = testAddon(Scope.CHANNELS, catalogs = listOf(CatalogSpec("tv", "guide", extra = listOf(ExtraSpec("date"))))).let { it.copy(manifest = it.manifest.copy(epgProvider = true)) }
        val channel = testItem(dateAddon, "ch1")
        val api = FakeApi().apply { pages = { _, _ -> listOf(channel.copy(videos = listOf(Video("p1", "News", startTime = "2026-11-07T10:00:00Z", endTime = "2026-11-07T11:00:00Z")))) } }
        val result = ChannelGuide(api).refresh(channel, listOf(dateAddon), java.time.Instant.parse("2026-11-07T10:00:00Z"))
        assertEquals(listOf("2026-11-07", "2026-11-08"), api.requests.map { it.second["date"] })
        assertEquals("ch1", result.id); assertEquals(1, result.videos.size)
    }
    @Test fun streamsUseEpisodeIdAndAggregateSameScopeProviders() = runTest {
        val addon = testAddon(Scope.SERIES); val other = testAddon(Scope.MOVIES)
        val item = testItem(addon, "show").copy(videos = listOf(Video("show:2:3", "Episode", season = 2, episode = 3)))
        val api = FakeApi()
        PlaybackResolver(api).resolve(item, item.videos.first(), listOf(addon, other))
        assertEquals(listOf("stream:show:2:3"), api.requests.map { it.first })
    }
    @Test fun inlineStreamsWorkWithoutNetwork() = runTest {
        val addon = testAddon(Scope.MOVIES); val item = testItem(addon, "m").copy(streams = listOf(MediaStream("https://video/file.mp4")))
        val api = FakeApi(); val result = PlaybackResolver(api).resolve(item, null, listOf(addon))
        assertTrue(api.requests.isEmpty()); assertTrue(result.streams.single().direct)
    }
    @Test fun unsupportedP2pIsExplicitNotSilentlyPlayed() = runTest {
        val addon = testAddon(Scope.MOVIES); val api = FakeApi().apply { streams = listOf(MediaStream(infoHash = "abc")) }
        val error = assertThrows(AddonException::class.java) { kotlinx.coroutines.runBlocking { PlaybackResolver(api).resolve(testItem(addon, "m"), null, listOf(addon)) } }
        assertTrue(error.message!!.contains("P2P"))
    }
}
