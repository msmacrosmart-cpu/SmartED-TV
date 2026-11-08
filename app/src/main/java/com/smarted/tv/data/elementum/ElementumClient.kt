package com.smarted.tv.data.elementum

import com.smarted.tv.data.json.asArrayOrEmpty
import com.smarted.tv.data.json.long
import com.smarted.tv.data.json.obj
import com.smarted.tv.data.json.str
import com.smarted.tv.data.json.unwrapList
import com.smarted.tv.data.net.Http
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Integração com o daemon do Elementum (Kodi add-on) via HTTP local — rotas reais de
 * `api/routes.go` (elgatito/elementum): /torrents/list, /torrents/add, /movies/search,
 * /shows/search, /movie/{tmdbId}/links, /show/{id}/season/{s}/episode/{e}/links, /reload.
 * O app NÃO executa torrent nem código de plugin: apenas conversa com o daemon e
 * delega reprodução direta quando há URL HTTP; caso contrário abre no Kodi via intent.
 */
class ElementumClient(
    private val baseUrl: String = "http://127.0.0.1:65064",
) {

    data class DaemonStatus(
        val online: Boolean,
        val activeTorrents: Int,
        val version: String = "",
        val message: String = "",
    )

    data class TorrentInfo(
        val id: String,
        val name: String,
        val progressPercent: Int,
        val speedDownloadKbps: Long,
        val files: List<String>,
    )

    data class SearchResult(
        val tmdbId: String,
        val title: String,
        val year: String,
        val posterUrl: String,
        val overview: String,
        val imdbId: String,
    )

    data class TorrentLink(
        val label: String,
        val magnetUrl: String,
        val downloadUrl: String,
        val quality: String,
        val sizeGb: Double?,
        val seeders: Int,
        /** Stream direto quando o provedor expõe http(s) jogável. */
        val directUrl: String,
        val pluginUrl: String,
    )

    suspend fun status(): DaemonStatus {
        val torrents = runCatching { getJsonOrNull("$baseUrl/torrents/list") }
        val element = torrents.getOrNull()
        if (element == null) {
            return DaemonStatus(
                online = false, activeTorrents = 0,
                message = "Daemon indisponível em $baseUrl. Instale/ative o Elementum (Kodi) ou ajuste host/porta.",
            )
        }
        val (array, _) = element.unwrapList()
        return DaemonStatus(online = true, activeTorrents = array.size, version = element.strOrNullVersion(), message = "Elementum respondeu em $baseUrl")
    }

    suspend fun activeTorrents(): List<TorrentInfo> {
        val element = getJsonOrNull("$baseUrl/torrents/list") ?: return emptyList()
        val (array, _) = element.unwrapList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            TorrentInfo(
                id = obj.str("hash").ifBlank { obj.str("torrentID") },
                name = obj.str("name").ifBlank { obj.str("filename") },
                progressPercent = obj.long("progress").toInt().coerceIn(0, 100),
                speedDownloadKbps = obj.long("downloadSpeed"),
                files = obj["fileList"]?.asArrayOrEmpty()?.mapNotNull { it as? JsonObject }
                    ?.map { it.str("path") }.orEmpty(),
            )
        }
    }

    suspend fun reloadProviders(): Boolean =
        runCatching { Http.getText("$baseUrl/reload") }.isSuccess ||
            runCatching { Http.getText("$baseUrl/reload", mapOf("Accept" to "text/plain")) }.isSuccess

    /** "Atualização automática" dos catálogos do Elementum (repo auto-update do add-on). */
    suspend fun triggerAutoUpdate(): Boolean = reloadProviders()

    suspend fun searchMovies(query: String, page: Int = 1): List<SearchResult> =
        search("$baseUrl/movies/search", query, page, keys = listOf("movies", "items", "results"))

    suspend fun searchShows(query: String, page: Int = 1): List<SearchResult> =
        search("$baseUrl/shows/search", query, page, keys = listOf("shows", "items", "results"))

    private suspend fun search(endpoint: String, query: String, page: Int, keys: List<String>): List<SearchResult> {
        val url = "$endpoint?q=${encode(query)}&page=$page"
        val element = getJsonOrNull(url) ?: return emptyList()
        val array = when {
            element is JsonArray -> element
            element is JsonObject -> keys.firstNotNullOfOrNull { element[it] as? JsonArray } ?: element.unwrapList().first
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val title = obj.str("title").ifBlank { obj.str("name") }
            val id = obj.str("tmdb_id").ifBlank { obj.str("tmdbId") }.ifBlank { obj.str("id") }
            if (title.isBlank() && id.isBlank()) return@mapNotNull null
            SearchResult(
                tmdbId = id, title = title,
                year = obj.str("year").ifBlank { obj.str("releasedate").take(4) },
                posterUrl = obj.str("image").ifBlank { obj.str("poster") },
                overview = obj.str("overview"), imdbId = obj.str("imdb_id"),
            )
        }
    }

    suspend fun movieLinks(tmdbId: String): List<TorrentLink> =
        links("$baseUrl/movie/${encode(tmdbId)}/links")

    suspend fun episodeLinks(showId: String, season: Int, episode: Int): List<TorrentLink> =
        links("$baseUrl/show/${encode(showId)}/season/$season/episode/$episode/links")

    private suspend fun links(url: String): List<TorrentLink> {
        val element = getJsonOrNull(url) ?: return emptyList()
        val (array, _) = element.unwrapList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val label = obj.str("title").ifBlank { obj.str("label") }
            val info = obj.obj("info")
            val downloadUrl = obj.str("downloadUrl").ifBlank { obj.str("download_url") }.ifBlank { info?.str("downloadUrl").orEmpty() }
            val magnet = obj.str("torrentUrl").ifBlank { obj.str("torrent_url") }.ifBlank { obj.str("url") }
            val direct = listOf(downloadUrl, magnet, obj.str("url")).firstOrNull { it.startsWith("http", ignoreCase = true) && !it.contains("magnet:") }
                .orEmpty()
            if (label.isBlank() && magnet.isBlank() && downloadUrl.isBlank()) return@mapNotNull null
            TorrentLink(
                label = label.ifBlank { "Fonte" },
                magnetUrl = if (magnet.startsWith("magnet:")) magnet else "",
                downloadUrl = downloadUrl,
                quality = obj.str("quality").ifBlank { downloadUrl.substringBefore('&').substringAfter("quality=", "") },
                sizeGb = obj.str("size").toDoubleOrNull(),
                seeders = obj.long("seeder").toInt(),
                directUrl = if (looksPlayable(direct)) direct else "",
                pluginUrl = obj.str("path").ifBlank { obj.str("plugin") },
            )
        }
    }

    /** Envia um magnet ao daemon (pré-download gerenciado pelo Elementum). */
    suspend fun addMagnet(magnet: String): Boolean {
        val url = "$baseUrl/torrents/add?link=${encode(magnet)}"
        return runCatching { Http.getText(url) }.isSuccess
    }

    private fun looksPlayable(url: String): Boolean =
        url.startsWith("http", ignoreCase = true) &&
            !url.substringBefore('#').substringAfterLast('.', "").lowercase().let { it == "php" || it == "json" || it == "html" }

    private suspend fun getJsonOrNull(url: String): JsonElement? =
        runCatching { Http.getJson(url) }.getOrNull()

    private fun JsonElement.strOrNullVersion(): String = (this as? JsonObject)?.str("version").orEmpty()

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
