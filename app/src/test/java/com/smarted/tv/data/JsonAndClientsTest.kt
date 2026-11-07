package com.smarted.tv.data

import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.data.elementum.ElementumClient
import com.smarted.tv.data.import.RepoJson
import com.smarted.tv.data.import.XtreamMapper
import com.smarted.tv.data.net.Http
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RepoJsonTest {
    private val json = Http.json

    @Test fun `parses playlist and xtream entries with defaults`() {
        val element = json.parseToJsonElement(
            """{"name":"Pacote BR","sources":[
               {"name":"Canais","kind":"m3u","url":"https://a/lista.m3u","epg":"https://a/epg.xml","update_minutes":60},
               {"playlist":"https://b/vod.m3u"},
               {"name":"Painel","url":"http://host:8080","username":"u","password":"p"}
            ]}"""
        )
        val index = RepoJson.fromJson(element, "http://x/repo.json")
        assertEquals("Pacote BR", index.name)
        assertEquals(3, index.sources.size)
        assertEquals(SourceKind.M3U, index.sources[0].kind)
        assertEquals(60, index.sources[0].autoUpdateMinutes)
        assertEquals("https://b/vod.m3u", index.sources[1].url)
        assertEquals(720, index.sources[1].autoUpdateMinutes)
        assertEquals(SourceKind.XTREAM, index.sources[2].kind)
    }

    @Test fun `rejects non-object root and empty sources`() {
        runCatching { RepoJson.fromJson(json.parseToJsonElement("[]"), "http://x") }.onFailure { assertTrue(it is IllegalStateException) }
        runCatching { RepoJson.fromJson(json.parseToJsonElement("""{"sources":[]}"""), "http://x") }.onFailure { assertTrue(it.message!!.contains("Nenhuma fonte")) }
    }
}

class XtreamMapperTest {
    private val json = Http.json
    private val source = ListSource(id = 1, name = "P", kind = SourceKind.XTREAM, url = "http://host:80", username = "user", password = "pass")

    @Test fun `auth detection`() {
        assertTrue(XtreamMapper.authenticateResult(json.parseToJsonElement("""{"user_info":{"auth":1,"status":"Active"},"server_info":{}}""")))
        assertFalse(XtreamMapper.authenticateResult(json.parseToJsonElement("""{"user_info":{"auth":0,"status":"Expired"}}""")))
        assertFalse(XtreamMapper.authenticateResult(json.parseToJsonElement("""{"errors":["bad"]}""")))
    }

    @Test fun `episodes grouped by season with proper urls`() {
        val raw = """{"seasons":[{"season_number":"2","name":"T2"},{"season_number":"1"}],
               "episodes":{"1":[{"id":"55","episode_num":"2","title":"Ep B","container_extension":"mkv","info":{"plot":"b","duration_secs":"1200"}}],
                            "2":[{"id":"77","episode_num":"1","title":"Ep C"}]}}"""
        val info = json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        val episodes = XtreamMapper.episodesFromSeriesInfo(source, info)
        assertEquals(2, episodes.size)
        val first = episodes.first()
        assertEquals(1, first.season)
        assertEquals(2, first.episode)
        assertEquals("http://host:80/series/user/pass/55.mkv", first.streamUrl)
        assertEquals(20, first.durationMinutes)
        val seasons = XtreamMapper.seasonsFromSeriesInfo(info)
        assertEquals(listOf(1 to "Temporada 1", 2 to "T2"), seasons)
    }

    @Test fun `short epg accepts epoch seconds and titles`() {
        val response = json.parseToJsonElement("""{"epg_listings":[{"title":"Jornal","start_timestamp":"1794065400","stop_timestamp":"1794069000","description":"noticias"}]}""")
        val programmes = XtreamMapper.shortEpgProgrammes("123", response)
        assertEquals(1, programmes.size)
        assertEquals("Jornal", programmes.single().title)
        assertEquals("short:123", programmes.single().epgChannel)
        assertEquals(1_794_065_400_000L, programmes.single().startMs)
    }
}

class ElementumClientTest {
    private val server = MockWebServer()
    private lateinit var client: ElementumClient

    @Before fun setUp() {
        client = ElementumClient("http://${server.hostName}:${server.port}")
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun `status online from torrents list`() = kotlinx.coroutines.runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                "/torrents/list" -> MockResponse().setResponseCode(200).setBody("[]")
                "/movies/search" -> MockResponse().setResponseCode(200)
                    .setBody("""{"total_entries":1,"movies":[{"title":"Duna","year":"2021","tmdb_id":"438631","image":"http://p","overview":"areia","imdb_id":"tt11504102"}]}""")
                "/movie/438631/links" -> MockResponse().setResponseCode(200)
                    .setBody("""[{"title":"Duna 2021 1080p","torrentUrl":"magnet:?xt=urn:btih:ABC","downloadUrl":"http://127.0.0.1:65064/proxy/file.mkv","seeder":"12","size":"4.2"}]""")
                "/reload" -> MockResponse().setResponseCode(200).setBody("reloaded")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val status = client.status()
        assertTrue(status.online)
        val movies = client.searchMovies("duna")
        assertEquals("Duna", movies.single().title)
        assertEquals("438631", movies.single().tmdbId)
        val links = client.movieLinks("438631")
        val link = links.single()
        assertEquals("magnet:?xt=urn:btih:ABC", link.magnetUrl)
        assertEquals("http://127.0.0.1:65064/proxy/file.mkv", link.directUrl)
        assertTrue(client.triggerAutoUpdate())
        val first = server.takeRequest()
        assertTrue(first.path!!.startsWith("/torrents/list"))
    }

    @Test fun `status offline when daemon missing`() = kotlinx.coroutines.runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
        }
        val status = client.status()
        assertFalse(status.online)
        assertTrue(status.message.contains("indisponível"))
    }
}
