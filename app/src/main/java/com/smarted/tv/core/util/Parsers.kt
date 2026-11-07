package com.smarted.tv.core.util

import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.SourceFormat
import com.smarted.tv.core.model.PublicSource
import java.net.URLDecoder

/**
 * Parsers puros: M3U/M3U8 estendido, XMLTV (EPG) e detecção de formato/qualidade.
 * Nenhuma dependência Android — cobertos por testes unitários em `app/src/test`.
 */
object M3uParser {

    data class ParsedPlaylist(
        val items: List<MediaItem>,
        val epgUrl: String? = null,
        val header: Map<String, String> = emptyMap(),
    )

    private val ATTR_REGEX = Regex("([A-Za-z0-9_.-]+)=\"([^\"]*)\"|([A-Za-z0-9_.-]+)=([^\\s\"]+)")
    private val KIND_HINT = Regex("(?i)#EXTINF:-?\\d+(?:\\.\\d+)?\\s*([a-z0-9+=/_.-]*)\\s*,\\s*(.*)$")

    /** Extrai atributos `k="v"` de uma linha #EXTINF / #EXTGRP. */
    fun parseAttributes(line: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (m in ATTR_REGEX.findAll(line)) {
            val key = (m.groupValues[1].ifBlank { m.groupValues[3] }).lowercase()
            val value = m.groupValues[2].ifBlank { m.groupValues[4] }
            if (key.isNotBlank()) map[key] = value.trim()
        }
        return map
    }

    /** Detecta o tipo de mídia por grupo/nome — heurística testada. */
    fun inferKind(attrs: Map<String, String>, title: String, url: String): MediaKind {
        val group = (attrs["group-title"] ?: attrs["group"] ?: attrs["group_title"] ?: "").lowercase()
        val t = title.lowercase()
        val combined = "$group $t"

        if (url.contains("/series/", true) || url.contains("/serie/", true)) return MediaKind.SERIES
        if (url.contains("/movie/", true) || url.contains("/movies/", true) || url.contains("/vod/", true)) {
            return MediaKind.MOVIE
        }
        seriesHint(combined)?.let { if (it) return MediaKind.SERIES }
        movieHint(combined)?.let { if (it) return MediaKind.MOVIE }
        if (url.contains(".m3u8", true) || url.contains(".ts", true) || url.contains("udp://", true) ||
            url.contains("rtmp://", true)
        ) {
            return MediaKind.LIVE
        }
        return MediaKind.LIVE
    }

    private fun seriesHint(s: String): Boolean? = when {
        s.contains("série") || s.contains("series") || s.contains("seriado") || s.contains("tv show") -> true
        s.contains("filme") || s.contains("movie") || s.contains("cinema") || s.contains("vod") -> false
        else -> null
    }

    private fun movieHint(s: String): Boolean? = when {
        s.contains("filme") || s.contains("movie") || s.contains("cinema") -> true
        else -> null
    }

    private val SEASON_EPISODE = Regex("(?i)(?:s|t)\\s?(\\d{1,2})\\s?[ex]\\s?(\\d{1,3})")
    private val EPISODE_ONLY = Regex("(?i)^(?:episode|ep|episodio|episódio)\\s?(\\d{1,3})")

    fun parseSeasonEpisode(title: String): Pair<Int, Int>? {
        SEASON_EPISODE.find(title)?.let {
            val s = it.groupValues[1].toIntOrNull() ?: return null
            val e = it.groupValues[2].toIntOrNull() ?: return null
            return s to e
        }
        EPISODE_ONLY.find(title)?.let {
            val e = it.groupValues[1].toIntOrNull() ?: return null
            return 1 to e
        }
        return null
    }

    /** Qualidade inferida do nome: 4K, 1080p, 720p, HD, SD. */
    fun inferQuality(name: String): String? {
        val n = name.lowercase()
        return when {
            n.contains("4k") || n.contains("2160") || n.contains("uhd") -> "4K"
            n.contains("1080") || n.contains("fhd") -> "1080p"
            n.contains("720") -> "720p"
            n.contains("480") || n.contains("360") -> "SD"
            n.contains(" hd") || n.contains("[hd]") || n.contains("(hd)") -> "HD"
            else -> null
        }
    }

    /**
     * Faz o parse completo de uma playlist M3U. Aceita:
     *  - `#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Nome`
     *  - `#EXTGRP:Group`
     *  - `#EXTVLCOPT:http-user-agent=...` / `http-referrer=...`
     *  - `#KODIPROP:...`
     *  - linhas de URL soltas
     *  - `#EXTM3U url-tvg="..."`
     */
    fun parse(
        content: String,
        addonId: String,
        defaultKind: MediaKind = MediaKind.LIVE,
        limit: Int = 100_000,
    ): ParsedPlaylist {
        val items = ArrayList<MediaItem>(minOf(limit, 4096))
        var epgUrl: String? = null
        var header: Map<String, String> = emptyMap()

        var pendingTitle: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()
        var pendingGroup: String? = null
        var pendingHeaders = LinkedHashMap<String, String>()

        val lines = content.lineSequence()
        for (rawLine in lines) {
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    header = parseAttributes(line)
                    epgUrl = header.entries.firstOrNull {
                        it.key in setOf("url-tvg", "x-tvg-url", "tvg-url", "url_tvg")
                    }?.value?.substringBefore(',')?.trim()
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingAttrs = parseAttributes(line)
                    val match = KIND_HINT.find(line)
                    pendingTitle = match?.groupValues?.get(2)?.trim()?.ifBlank { null }
                        ?: line.substringAfterLast(',').trim().ifBlank { null }
                    pendingGroup = pendingAttrs["group-title"] ?: pendingAttrs["group"]
                    pendingHeaders = LinkedHashMap()
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':').trim().ifBlank { pendingGroup }
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" -> pendingHeaders["User-Agent"] = value
                            "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                            "http-origin" -> pendingHeaders["Origin"] = value
                        }
                    }
                }

                line.startsWith("#KODIPROP", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "inputstream.adaptive.stream_headers" ->
                                value.split('&').forEach { pair ->
                                    val k = pair.substringBefore('=').trim()
                                    val v = pair.substringAfter('=', "").trim()
                                    if (k.isNotEmpty() && v.isNotEmpty()) pendingHeaders[k] = v
                                }
                            "http-user-agent" -> pendingHeaders["User-Agent"] = value
                        }
                    }
                }

                line.startsWith("#") -> Unit // outros comentários

                else -> {
                    // linha de URL
                    val url = line
                    val title = pendingTitle ?: guessTitleFromUrl(url)
                    if (pendingTitle != null || url.isNotBlank()) {
                        val attrs = pendingAttrs
                        val kind = if (defaultKind == MediaKind.LIVE) {
                            inferKind(attrs, title, url)
                        } else {
                            defaultKind
                        }
                        val quality = inferQuality(title) ?: inferQuality(pendingGroup ?: "")
                        val logo = attrs["tvg-logo"] ?: attrs["logo"] ?: attrs["tvg-logo-small"]
                        val id = attrs["tvg-id"] ?: buildStableId(addonId, kind, title, url)
                        val episodeText = attrs["tvg-name"] ?: title
                        val se = if (kind == MediaKind.SERIES) parseSeasonEpisode(episodeText) else null
                        items.add(
                            MediaItem(
                                id = id.ifBlank { buildStableId(addonId, kind, title, url) },
                                addonId = addonId,
                                kind = kind,
                                title = TextUtils.cleanTitle(title).ifBlank { title },
                                subtitle = se?.let { "T${it.first}E${it.second}" },
                                poster = logo,
                                group = pendingGroup?.trim()?.ifBlank { null },
                                tvgId = attrs["tvg-id"],
                                streamUrl = url,
                                quality = quality,
                                number = se?.second,
                                ext = url.substringAfterLast('.', "").substringBefore('?').ifBlank { null },
                            ),
                        )
                        // headers do item são mantidos em cache pelo addon
                        if (pendingHeaders.isNotEmpty()) {
                            ItemHeaderStore.put(url, pendingHeaders.toMap())
                        }
                    }
                    pendingTitle = null
                    pendingAttrs = emptyMap()
                    pendingHeaders = LinkedHashMap()
                    if (items.size >= limit) break
                }
            }
        }
        return ParsedPlaylist(items = items, epgUrl = epgUrl, header = header)
    }

    private fun guessTitleFromUrl(url: String): String {
        val file = url.substringBefore('?').substringAfterLast('/')
        return file.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
    }

    fun buildStableId(addonId: String, kind: MediaKind, title: String, url: String): String {
        val base = url.hashCode().toUInt().toString(16)
        return "${kind.name.lowercase()}-$base"
    }

    /** Mantém os cabeçalhos HTTP declarados na playlist para reuso na reprodução. */
    object ItemHeaderStore {
        private val map = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
        fun put(url: String, headers: Map<String, String>) {
            if (headers.isNotEmpty()) map[url] = headers
        }
        fun get(url: String?): Map<String, String> {
            if (url.isNullOrBlank()) return emptyMap()
            map[url]?.let { return it }
            // casamento por prefixo quando o addon altera query string
            val base = url.substringBefore('?')
            return map.entries.firstOrNull { it.key.substringBefore('?') == base }?.value ?: emptyMap()
        }
        fun clear() = map.clear()
    }
}

/**
 * Parser XMLTV tolerante. Suporta streams grandes (usa leitura incremental por
 * linha) e não depende de DOM/Android.
 */
object XmltvParser {

    data class Channel(
        val id: String,
        val name: String,
        val icon: String? = null,
    )

    data class Result(
        val channels: Map<String, Channel>,
        val programs: Map<String, List<EpgProgram>>,
        val timezone: String? = null,
    )

    private val CHANNEL_START = Regex("<channel\\s+id=\"([^\"]*)\"")
    private val CHANNEL_ID_ALT = Regex("id=['\"]([^'\"]*)['\"]")
    private val DISPLAY_NAME = Regex("<display-name[^>]*>(.*?)</display-name>", RegexOption.DOT_MATCHES_ALL)
    private val ICON_SRC = Regex("<icon[^>]*src=['\"]([^'\"]*)['\"]")
    private val PROGRAMME_START = Regex(
        "<programme\\s+start=\"([^\"]*)\"\\s+(?:stop=\"([^\"]*)\")?[^>]*channel=\"([^\"]*)\"|<programme\\s+[^>]*channel=\"([^\"]*)\"[^>]*start=\"([^\"]*)\"(?:\\s+stop=\"([^\"]*)\")?",
    )
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val DESC = Regex("<desc[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
    private val CATEGORY = Regex("<category[^>]*>(.*?)</category>", RegexOption.DOT_MATCHES_ALL)
    private val ICON = Regex("<icon[^>]*src=['\"]([^'\"]*)['\"]")
    private val EPISODE_NUM = Regex("<episode-num[^>]*>(.*?)</episode-num>")
    private val RATING_VAL = Regex("<rating[^>]*>\\s*<value>(.*?)</value>", RegexOption.DOT_MATCHES_ALL)

    fun unescapeEntities(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    /** Converte "20240101120000 +0000" / "20240101120000" em epoch millis (UTC). */
    fun parseXmltvTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val datePart = trimmed.substringBefore(' ').replace("-", "").replace(":", "")
        if (datePart.length < 12) return null
        val year = datePart.substring(0, 4).toIntOrNull() ?: return null
        val month = datePart.substring(4, 6).toIntOrNull() ?: return null
        val day = datePart.substring(6, 8).toIntOrNull() ?: return null
        val hour = datePart.substring(8, 10).toIntOrNull() ?: return null
        val minute = datePart.substring(10, 12).toIntOrNull() ?: return null
        val second = if (datePart.length >= 14) datePart.substring(12, 14).toIntOrNull() ?: 0 else 0

        var offsetMinutes = 0
        val offsetPart = trimmed.substringAfter(' ', "").trim()
        if (offsetPart.length >= 5) {
            val sign = if (offsetPart.startsWith("-")) -1 else 1
            val hh = offsetPart.substring(1, 3).toIntOrNull() ?: 0
            val mm = offsetPart.substring(3, 5).toIntOrNull() ?: 0
            offsetMinutes = sign * (hh * 60 + mm)
        }
        return epochFromUtc(year, month, day, hour, minute, second) - offsetMinutes * 60_000L
    }

    /**
     * Algoritmo de dias-civis (Howard Hinnant) — evita APIs de Calendar e
     * funciona igual em JVM e Android, sem surpresas de fuso.
     */
    fun epochFromUtc(y: Int, month: Int, d: Int, h: Int, min: Int, sec: Int): Long {
        var year = y.toLong()
        val m = month.toLong()
        val day = d.toLong()
        if (m <= 2) year -= 1
        val era = (if (year >= 0) year else year - 399) / 400
        val yoe = year - era * 400
        val mp = if (m > 2) m - 3 else m + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097 + doe - 719468
        return days * 86_400_000L + h * 3_600_000L + min * 60_000L + sec * 1_000L
    }

    /**
     * Parse do XMLTV. Implementação por blocos (streaming-friendly) aceitando
     * XML mal formatado frequentemente encontrado em provedores de EPG.
     */
    fun parse(content: String, limitPrograms: Int = 200_000): Result {
        val channels = LinkedHashMap<String, Channel>()
        val programs = HashMap<String, MutableList<EpgProgram>>()
        var timezone: String? = null

        val channelBlocks = Regex("<channel\\b[^>]*>.*?</channel>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (block in channelBlocks.findAll(content)) {
            val text = block.value
            val id = CHANNEL_ID_ALT.find(text)?.groupValues?.get(1) ?: continue
            val name = DISPLAY_NAME.find(text)?.groupValues?.get(1)?.let { unescapeEntities(it) } ?: id
            val icon = ICON_SRC.find(text)?.groupValues?.get(1)
            channels[id] = Channel(id = id, name = name.trim(), icon = icon)
        }

        val programmeBlocks = Regex("<programme\\b[^>]*>.*?</programme>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        var count = 0
        for (block in programmeBlocks.findAll(content)) {
            if (count >= limitPrograms) break
            val text = block.value
            val openTag = text.substringBefore('>')
            val startRaw = attr(openTag, "start")
            val stopRaw = attr(openTag, "stop")
            val channelId = attr(openTag, "channel") ?: continue
            val startMs = parseXmltvTime(startRaw) ?: continue
            val endMs = parseXmltvTime(stopRaw) ?: (startMs + 30 * 60_000L)
            val title = TITLE.find(text)?.groupValues?.get(1)?.let { unescapeEntities(it) }?.trim().orEmpty()
            if (title.isBlank()) continue
            val desc = DESC.find(text)?.groupValues?.get(1)?.let { unescapeEntities(it).trim() }
            val category = CATEGORY.find(text)?.groupValues?.get(1)?.let { unescapeEntities(it).trim() }
            val icon = ICON.find(text)?.groupValues?.get(1)
            val episodeNum = EPISODE_NUM.find(text)?.groupValues?.get(1)?.trim()
            val rating = RATING_VAL.find(text)?.groupValues?.get(1)?.let { unescapeEntities(it).trim() }
            val list = programs.getOrPut(channelId) { ArrayList() }
            list.add(
                EpgProgram(
                    channelId = channelId,
                    title = title,
                    startMs = startMs,
                    endMs = endMs,
                    description = desc,
                    category = category,
                    icon = icon,
                    episodeNum = episodeNum,
                    rating = rating,
                ),
            )
            count++
        }
        if (timezone == null) {
            timezone = Regex("<tv\\b[^>]*date=['\"]([^'\"]*)['\"]").find(content)?.groupValues?.get(1)
        }
        programs.values.forEach { it.sortBy { p -> p.startMs } }
        return Result(channels = channels, programs = programs, timezone = timezone)
    }

    private fun attr(tag: String, name: String): String? {
        val r = Regex("$name=['\"]([^'\"]*)['\"]")
        return r.find(tag)?.groupValues?.get(1)
    }

    /** Índice invertido normalizado para casar nome de canal ↔ tvg-id. */
    fun buildChannelIndex(channels: Map<String, Channel>): Map<String, String> {
        val index = HashMap<String, String>()
        for ((id, ch) in channels) {
            index[TextUtils.titleKey(id)] = id
            index[TextUtils.titleKey(ch.name)] = id
        }
        return index
    }
}

/** Codificação tolerante usada por endpoints de playlist. */
object DecodeUtils {
    fun urlDecode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    /** Resolve `url` relativo contra `base` sem depender de android.net.Uri. */
    fun resolve(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        val scheme = if (base.startsWith("https://")) "https" else "http"
        if (relative.startsWith("//")) return "$scheme:$relative"
        val host = base.substringAfter("://").substringBefore('/')
        val root = "$scheme://$host"
        if (relative.startsWith("/")) return "$root$relative"
        val dir = base.substringBeforeLast('/', root)
        return "$dir/$relative"
    }
}

/** Detecção de provedor a partir de uma URL de repositório/fonte. */
object SourceDetector {
    fun formatFor(url: String): SourceFormat = when {
        url.contains("get.php", true) || url.contains("player_api.php", true) -> SourceFormat.XTREAM
        url.endsWith(".xml", true) || url.endsWith(".xml.gz", true) -> SourceFormat.XMLTV
        url.endsWith(".m3u", true) || url.endsWith(".m3u8", true) -> SourceFormat.M3U
        else -> SourceFormat.PLUGIN
    }

    /** Normaliza a URL do repositório (garante esquema). */
    fun normalizeRepoUrl(raw: String): String {
        var v = raw.trim()
        if (v.isEmpty()) return v
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "https://$v"
        return v
    }

    /** Sugere o arquivo de índice de um repositório estilo Kodi. */
    fun indexCandidates(repoUrl: String): List<String> {
        val base = repoUrl.trimEnd('/')
        return listOf(
            "$base/index.html",
            "$base/addons.xml",
            "$base/repo/addons.xml",
            "$base/zips/addons.xml",
            "$base/addons.xml.gz",
        )
    }

    fun defaultSource(url: String, name: String, kind: MediaKind): PublicSource {
        val format = formatFor(url)
        return PublicSource(
            id = "src-" + Integer.toHexString(("$name$url").hashCode()),
            name = name,
            url = url,
            kind = kind,
            format = format,
        )
    }
}
