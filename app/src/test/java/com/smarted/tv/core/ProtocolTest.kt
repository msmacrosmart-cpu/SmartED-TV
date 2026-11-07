package com.smarted.tv.core

import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.*
import org.junit.Assert.*
import org.junit.Test

class ProtocolTest {
    private val valid = """{"id":"example","name":"Example","version":"1.0.0","types":["movie","series","tv"],"resources":["catalog",{"name":"stream","types":["movie","series","tv"],"idPrefixes":["ex:"]}],"catalogs":[{"id":"films","type":"movie","name":"Films","extraSupported":["skip","search"],"extraRequired":[],"genres":["Ação"],"pageSize":2},{"id":"shows","type":"series","extra":[{"name":"genre","options":["Drama"],"isRequired":false}]},{"id":"channels","type":"tv","extra":[{"name":"date"}]}],"behaviorHints":{"epgProvider":true}}"""

    @Test fun manifestSupportsBothResourceAndExtraRepresentations() {
        val manifest = ProtocolJson.manifest(valid)
        assertEquals(3, manifest.catalogs.size)
        assertTrue(manifest.catalogs[0].paged)
        assertTrue(manifest.catalogs[0].searchable)
        assertEquals(2, manifest.catalogs[0].pageSize)
        assertTrue(manifest.resource("stream", "series", "ex:1"))
        assertFalse(manifest.resource("stream", "series", "other:1"))
        assertTrue(manifest.epgProvider)
        assertEquals(1, manifest.catalogsFor(Scope.MOVIES).size)
    }
    @Test fun acceptsBomAndUnknownFields() { assertEquals("example", ProtocolJson.manifest("\uFEFF  $valid").id) }
    @Test fun strictJsonRejectsLenientSyntax() {
        listOf("", "[]", "null", "<html></html>", "{id:'x'}", "{\"id\":}", "$valid trailing").forEach {
            assertThrows(AddonException::class.java) { ProtocolJson.manifest(it) }
        }
    }
    @Test fun requiredManifestFieldsAreValidated() {
        assertThrows(AddonException::class.java) { ProtocolJson.manifest("""{"id":"x","name":"X"}""") }
        assertThrows(AddonException::class.java) { ProtocolJson.manifest("""{"id":"x","name":"","version":"1"}""") }
    }
    @Test fun scalarProtocolVariantsDoNotCrash() {
        val raw = """{"metas":[{"id":"m1","type":"movie","name":"Film","imdbRating":8.5,"releaseInfo":2024,"genres":"Ação, Drama","runtime":120,"description":null},{"id":"s1","type":"series","name":"Show","links":[{"category":"genre","name":"Suspense"}]}, {"id":false},"bad",{"id":"x","type":"movie"}]}"""
        val items = ProtocolJson.metas(raw).mapNotNull { ProtocolJson.item(it, "https://a/manifest.json", Scope.MOVIES, "movie") }
        assertEquals(1, items.size)
        assertEquals(8.5, items[0].rating!!, 0.0)
        assertEquals(listOf("Ação", "Drama"), items[0].genres)
        assertEquals(2024, items[0].year)
        assertEquals("120", items[0].runtime)
    }
    @Test fun nativeEpgAndSeriesVideosPreserveTheirIds() {
        val raw = """{"meta":{"id":"show","type":"series","name":"Show","videos":[{"id":"show:2:3","title":"Ep","season":2,"episode":3,"released":"2024-01-01T00:00:00Z","streams":[{"url":"https://video/mp4","name":"HD","behaviorHints":{"proxyHeaders":{"request":{"Authorization":"Bearer test"}}},"subtitles":[{"id":"pt","url":"https://sub/pt.srt","lang":"pt-BR"}]}]}]}}"""
        val meta = ProtocolJson.item(ProtocolJson.meta(raw), "https://a/manifest.json", Scope.SERIES, "series")!!
        assertEquals("show:2:3", meta.playbackId(meta.videos.first()))
        assertEquals("Bearer test", meta.videos.first().streams.first().headers["Authorization"])
        assertEquals("pt-BR", meta.videos.first().streams.first().subtitles.first().language)
    }
    @Test fun livePlaybackAlwaysUsesChannelIdentity() {
        val item = MediaItem("https://a/manifest.json", "channel1", "tv", Scope.CHANNELS, "News", defaultVideoId = "programme2", videos = listOf(Video("programme2", "News at ten")))
        assertEquals("channel1", item.playbackId(item.videos.first()))
    }
    @Test fun manifestScopeIsExplicitAndValidated() {
        val manifest = ProtocolJson.manifest(valid)
        ManifestValidator.validate(manifest, Scope.CHANNELS)
        val moviesOnly = manifest.copy(catalogs = manifest.catalogsFor(Scope.MOVIES), resources = emptyList())
        assertThrows(AddonException::class.java) { ManifestValidator.validate(moviesOnly, Scope.SERIES) }
        val streamOnly = moviesOnly.copy(catalogs = emptyList(), resources = listOf(ResourceSpec("stream", listOf("movie"))))
        ManifestValidator.validate(streamOnly, Scope.MOVIES)
        assertThrows(AddonException::class.java) { ManifestValidator.validate(streamOnly, Scope.SERIES) }
    }
    @Test fun configurationRequiredIsActionable() {
        val manifest = ProtocolJson.manifest(valid).copy(configurationRequired = true)
        val error = assertThrows(AddonException::class.java) { ManifestValidator.validate(manifest, Scope.MOVIES) }
        assertTrue(error.message!!.contains("configurado"))
    }
    @Test fun invalidCatalogAndResourceShapesAreRejected() {
        assertThrows(AddonException::class.java) { ProtocolJson.metas("""{"metas":{}}""") }
        assertThrows(AddonException::class.java) { ProtocolJson.meta("""{"meta":[]}""") }
        assertThrows(AddonException::class.java) { ProtocolJson.streams("""{"streams":null}""") }
        assertEquals(emptyList<MediaStream>(), ProtocolJson.streams("""{"streams":[null,42,{}, {"url":"https://x/video"}]}""").let { it.filter(MediaStream::direct) }.drop(1))
    }
    @Test fun detailedGuideResponseIsAccepted() { assertEquals(1, ProtocolJson.metas("""{"metasDetailed":[{"id":"tv1","type":"tv","name":"TV"}]}""").size) }
    @Test fun extraRequiredMergesWithFullDefinitions() {
        val m = ProtocolJson.manifest(valid.replace("\"extraSupported\":[\"skip\",\"search\"]", "\"extraSupported\":[\"skip\",\"search\"],\"extra\":[{\"name\":\"search\",\"isRequired\":true}]"))
        assertTrue(m.catalogs.first().searchOnly)
    }
}
