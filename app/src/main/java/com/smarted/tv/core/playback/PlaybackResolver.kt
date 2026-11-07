package com.smarted.tv.core.playback

import com.smarted.tv.core.model.*
import com.smarted.tv.core.protocol.AddonException
import com.smarted.tv.core.protocol.StremioApi
import kotlinx.coroutines.CancellationException

data class ResolvedPlayback(val streams: List<MediaStream>, val warnings: List<String>)

class PlaybackResolver(private val api: StremioApi) {
    suspend fun resolve(item: MediaItem, video: Video?, addons: List<InstalledAddon>): ResolvedPlayback {
        val id = item.playbackId(video)
        val inline = if (item.scope == Scope.CHANNELS) item.streams else video?.streams?.ifEmpty { item.streams } ?: item.streams
        val streams = inline.toMutableList()
        val warnings = mutableListOf<String>()
        val providers = addons.filter { it.scope == item.scope && it.manifest.resource("stream", item.type, id) }
        val origin = providers.firstOrNull { it.url == item.addonUrl }
        val ordered = listOfNotNull(origin) + providers.filterNot { it.url == item.addonUrl }
        // If the origin omits resources but has catalogs, try its stream endpoint once (common in the wild).
        val tryOrigin = origin == null && addons.any { it.url == item.addonUrl && it.manifest.resources.isEmpty() }
        val urls = ordered.map { it.url } + if (tryOrigin) listOf(item.addonUrl) else emptyList()
        for (url in urls.distinct()) {
            try { streams += api.streams(item.copy(addonUrl = url), id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { warnings += "Fonte ${url.substringAfter("://").substringBefore('/')}: ${e.message ?: "indisponível"}" }
        }
        val distinct = streams.distinctBy { it.url ?: it.externalUrl ?: it.infoHash ?: it.ytId ?: it.label }
        if (distinct.isEmpty()) throw AddonException("Nenhuma fonte de vídeo foi fornecida para este título. ${warnings.firstOrNull().orEmpty()}")
        if (distinct.none { it.direct }) {
            val message = when {
                distinct.any { it.externalUrl != null || it.ytId != null } -> "Este título exige um aplicativo ou navegador externo. Use \"Abrir no navegador\" para continuar no provedor."
                distinct.any { it.infoHash != null } -> "Este addon fornece apenas torrent/P2P. O SmartED não executa plugins nem mecanismos P2P; escolha um addon com streams HTTP diretos."
                else -> "Nenhum stream HTTP direto compatível foi fornecido."
            }
            throw AddonException(message)
        }
        return ResolvedPlayback(distinct, warnings)
    }
}
