package com.smarted.tv.data.import

import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.Programme
import com.smarted.tv.data.json.long
import com.smarted.tv.data.json.obj
import com.smarted.tv.data.json.str
import com.smarted.tv.data.json.strOrNull
import com.smarted.tv.data.json.unwrapList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Mapeamentos puros das respostas Xtream (sem rede) — usados pelo cliente e pelos testes JVM. */
object XtreamMapper {

    data class Episode(
        val season: Int, val episode: Int, val title: String, val plot: String,
        val thumb: String?, val durationMinutes: Int?, val streamUrl: String, val ext: String,
    )

    private fun seriesUrl(source: ListSource, episodeStreamId: String, ext: String): String {
        val base = source.url.trim().trimEnd('/')
        val e = java.net.URLEncoder
        return "$base/series/${e.encode(source.username, "UTF-8")}/${e.encode(source.password, "UTF-8")}/$episodeStreamId.${ext.ifBlank { "mp4" }}"
    }

    fun episodesFromSeriesInfo(source: ListSource, info: JsonObject): List<Episode> {
        val episodes = info.obj("episodes") ?: return emptyList()
        val result = ArrayList<Episode>()
        for ((seasonKey, seasonNode) in episodes) {
            val seasonNumber = seasonKey.toIntOrNull() ?: (result.size + 1)
            val bucket = seasonNode as? JsonArray ?: continue
            for ((index, el) in bucket.withIndex()) {
                val item = el as? JsonObject ?: continue
                val id = item.str("id")
                if (id.isBlank()) continue
                result += Episode(
                    season = seasonNumber,
                    episode = item.str("episode_num").toIntOrNull() ?: (index + 1),
                    title = item.str("title").ifBlank { "Episódio ${index + 1}" },
                    plot = item.obj("info")?.str("plot").orEmpty(),
                    thumb = item.strOrNull("thumb"),
                    durationMinutes = item.obj("info")?.str("duration_secs")?.toLongOrNull()?.let { (it / 60).toInt() },
                    streamUrl = seriesUrl(source, id, item.str("container_extension")),
                    ext = item.str("container_extension").ifBlank { "mp4" },
                )
            }
        }
        return result
    }

    fun seasonsFromSeriesInfo(info: JsonObject): List<Pair<Int, String>> {
        val seasons = (info.obj("seasons") as? JsonArray) ?: return emptyList()
        return seasons.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val number = obj.str("season_number").toIntOrNull() ?: return@mapNotNull null
            number to obj.str("name").ifBlank { "Temporada $number" }
        }.sortedBy { it.first }
    }

    fun shortEpgProgrammes(streamId: String, element: JsonElement): List<Programme> {
        val (array, _) = element.unwrapList()
        return array.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val start = item.long("start_timestamp").let { if (it in 1 until 1_000_000_000_000L) it * 1_000 else it }
            val stop = item.long("stop_timestamp").let { if (it in 1 until 1_000_000_000_000L) it * 1_000 else it }
            val title = item.str("title")
            if (start <= 0L || title.isBlank()) return@mapNotNull null
            Programme(
                epgChannel = "short:$streamId", startMs = start,
                stopMs = if (stop > start) stop else start + 30 * 60_000L,
                title = title, description = item.str("description"), category = "",
            )
        }
    }

    fun authenticateResult(element: JsonElement?): Boolean {
        val obj = element as? JsonObject ?: return false
        val userInfo = obj.obj("user_info") ?: return false
        val status = userInfo.str("status")
        val auth = when (val el = userInfo["auth"]) {
            is kotlinx.serialization.json.JsonPrimitive -> el.booleanOrNull ?: el.contentOrNullBool() ?: true
            null -> true
            else -> true
        }
        return auth && (status.isBlank() || status.equals("Active", ignoreCase = true))
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullBool(): Boolean? =
        content.lowercase().let { if (it == "true" || it == "1") true else if (it == "false" || it == "0") false else null }
}
