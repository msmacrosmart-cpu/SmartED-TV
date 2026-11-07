package com.smarted.tv.core.catalog

import com.smarted.tv.core.model.InstalledAddon
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.Scope
import com.smarted.tv.core.protocol.StremioApi
import java.time.Instant
import java.time.ZoneOffset

/** Native Stremio EPG: catalog/tv/id/date=YYYY-MM-DD.json -> metasDetailed[].videos. */
class ChannelGuide(private val api: StremioApi) {
    suspend fun refresh(item: MediaItem, addons: List<InstalledAddon>, now: Instant = Instant.now()): MediaItem {
        require(item.scope == Scope.CHANNELS)
        val addon = addons.firstOrNull { it.url == item.addonUrl }
        val guideCatalog = addon?.manifest?.catalogsFor(Scope.CHANNELS)?.firstOrNull { it.extra("date") != null }
        var detailed: MediaItem? = null
        if (addon != null && addon.manifest.epgProvider && guideCatalog != null) {
            val days = listOf(now.atZone(ZoneOffset.UTC).toLocalDate(), now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1))
            val videos = days.flatMap { day ->
                try { api.catalog(addon, guideCatalog, mapOf("date" to day.toString())) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }.filter { it.id == item.id }.flatMap { it.videos }
            }
            if (videos.isNotEmpty()) detailed = item.copy(videos = videos.distinctBy { it.id })
        }
        if (detailed == null && (addon == null || addon.manifest.resource("meta", item.type, item.id))) {
            detailed = try { api.meta(item) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        }
        return detailed ?: item
    }
}
