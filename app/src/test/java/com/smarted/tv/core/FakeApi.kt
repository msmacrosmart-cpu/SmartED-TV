package com.smarted.tv.core

import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.StremioApi

class FakeApi : StremioApi {
    var manifestResult = Manifest("test", "Test", "1.0", resources = listOf(ResourceSpec("catalog"), ResourceSpec("meta"), ResourceSpec("stream")), catalogs = Scope.entries.map { CatalogSpec(it.wireTypes.first(), it.name) })
    val requests = mutableListOf<Pair<String, Map<String, String>>>()
    var pages: (CatalogSpec, Map<String, String>) -> List<MediaItem> = { _, _ -> emptyList() }
    var streams: List<MediaStream> = listOf(MediaStream("https://media.example/video.mp4"))
    override suspend fun manifest(url: String) = manifestResult
    override suspend fun catalog(addon: InstalledAddon, catalog: CatalogSpec, extra: Map<String, String>): List<MediaItem> {
        requests += catalog.id to extra
        return pages(catalog, extra)
    }
    override suspend fun meta(item: MediaItem) = item
    override suspend fun streams(item: MediaItem, videoId: String): List<MediaStream> { requests += "stream:$videoId" to emptyMap(); return streams }
}

fun testAddon(scope: Scope, url: String = "https://${scope.name.lowercase()}.example/manifest.json", catalogs: List<CatalogSpec> = listOf(CatalogSpec(scope.wireTypes.first(), "top", extra = listOf(ExtraSpec("skip"), ExtraSpec("search"))))) =
    InstalledAddon(url, scope, Manifest("test-${scope.name}", "Test", "1.0", resources = listOf(ResourceSpec("catalog"), ResourceSpec("meta"), ResourceSpec("stream")), catalogs = catalogs), 1L)
fun testItem(addon: InstalledAddon, id: String, name: String = id, genres: List<String> = emptyList()) =
    MediaItem(addon.url, id, addon.scope.wireTypes.first(), addon.scope, name, genres = genres)
