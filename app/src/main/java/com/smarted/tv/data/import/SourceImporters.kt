package com.smarted.tv.data.import

import com.smarted.tv.core.epg.EpgMatcher
import com.smarted.tv.core.model.ListSource
import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.model.Tab
import com.smarted.tv.core.parse.M3uParser
import com.smarted.tv.core.parse.XmltvParser
import com.smarted.tv.core.repo.RepoIndex
import com.smarted.tv.core.sources.StreamRules
import com.smarted.tv.core.text.TextNormalizer
import com.smarted.tv.data.db.IptvStore
import com.smarted.tv.data.json.double
import com.smarted.tv.data.json.long
import com.smarted.tv.data.json.obj
import com.smarted.tv.data.json.str
import com.smarted.tv.data.json.strOrNull
import com.smarted.tv.data.json.unwrapList
import com.smarted.tv.data.net.Http
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Resultado de uma importação para exibição no status da fonte. */
data class ImportOutcome(
    val channels: Int,
    val movies: Int,
    val series: Int,
    val epgChannels: Int,
    val epgProgrammes: Int,
    val epgUnmatched: Int,
    val warnings: List<String>,
) {
    val summary: String
        get() = buildString {
            append("$channels canal(is)")
            if (movies > 0) append(" · $movies filme(s)")
            if (series > 0) append(" · $series série(s)")
            if (epgProgrammes > 0) append(" · EPG com $epgProgrammes programa(s)")
            if (epgUnmatched > 0) append(" · $epgUnmatched sem EPG")
            if (warnings.isNotEmpty()) append(" · ${warnings.size} aviso(s)")
        }
}

/**
 * Orquestra M3U / Xtream / repositório → banco local. Rede e disco no dispatcher do chamador;
 * classificação e matching vêm do núcleo puro (testado em JVM).
 */
class SourceImporters(private val store: IptvStore) {

    suspend fun import(source: ListSource, epgWindowHours: Int, refreshEpg: Boolean = true): ImportOutcome = when (source.kind) {
        SourceKind.M3U -> importM3u(source, epgWindowHours, refreshEpg)
        SourceKind.XTREAM -> importXtream(source, epgWindowHours, refreshEpg)
        SourceKind.REPOSITORY -> error("Repositórios são sincronizados por SourceManager.importRepository().")
    }

    // ------------------------------------------------------------------ M3U

    suspend fun importM3u(source: ListSource, epgWindowHours: Int, refreshEpg: Boolean): ImportOutcome {
        val text = Http.getText(source.url, mapOf("Accept" to "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain"))
        if (!Http.looksLikePlaylist(source.url, text)) {
            error("O conteúdo baixado não parece uma playlist M3U. Verifique o link.")
        }
        val parsed = M3uParser.parse(text)
        val warnings = parsed.warnings.toMutableList()
        if (parsed.channels.isEmpty()) error("A playlist não contém canais válidos.")

        val live = ArrayList<IptvStore.ImportedChannel>()
        val movies = ArrayList<IptvStore.ImportedVod>()
        val series = ArrayList<IptvStore.ImportedVod>()
        val usedKeys = HashSet<String>()
        for (entry in parsed.channels) {
            val prefix = if (classifyGroup(entry.groupName, entry.streamUrl) == null) "ch" else "vod"
            val favKey = uniqueFavKey(source.id, prefix, entry.epgKey, usedKeys)
            when (classifyGroup(entry.groupName, entry.streamUrl)) {
                null, Tab.CHANNELS -> live += IptvStore.ImportedChannel(
                    favKey = favKey, number = entry.number, name = entry.name, group = entry.groupName,
                    logo = entry.logoUrl, url = entry.streamUrl, headers = entry.headers, epgKey = entry.epgKey,
                )
                Tab.MOVIES -> movies += vodFromPlaylistEntry(favKey, entry.epgKey, entry.name, entry.groupName, entry.logoUrl, entry.streamUrl)
                Tab.SERIES -> series += vodFromPlaylistEntry(favKey, entry.epgKey, entry.name, entry.groupName, entry.logoUrl, entry.streamUrl)
            }
        }
        store.replaceChannels(source.id, live)
        store.replaceVod(source.id, Tab.MOVIES, movies)
        store.replaceVod(source.id, Tab.SERIES, series)

        var epgChannels = 0
        var epgProgrammes = 0
        var epgUnmatched = live.size
        if (refreshEpg && source.epgUrl.isNotBlank()) {
            val outcome = importEpgFromUrl(source.epgUrl, epgWindowHours)
            epgChannels = outcome.epgChannels
            epgProgrammes = outcome.epgProgrammes
            epgUnmatched = outcome.epgUnmatched
            warnings += outcome.warnings
        }
        return ImportOutcome(live.size, movies.size, series.size, epgChannels, epgProgrammes, epgUnmatched, warnings)
    }

    private fun vodFromPlaylistEntry(favKey: String, extKey: String, name: String, group: String, logo: String?, url: String) =
        IptvStore.ImportedVod(
            favKey = favKey, extKey = extKey, name = name, category = group,
            year = null, rating = null, addedMs = System.currentTimeMillis(), poster = logo, backdrop = null,
            plot = "", url = url, runtimeMinutes = null, cast = emptyList(), genre = "", country = "",
            language = "", imdbId = "", seasons = 0,
        )

    /** Classificação VOD por grupo/extensão — mesma regra usada nos testes JVM. */
    fun classifyGroup(group: String, url: String): Tab? {
        val tokens = TextNormalizer.normalize(group).split(' ').filter(String::isNotEmpty).toSet()
        return when {
            tokens.any { it in setOf("filme", "filmes", "movie", "movies", "vod", "cinema", "lancamento", "lancamentos", "series 2026") } -> Tab.MOVIES
            tokens.any { it in setOf("serie", "series", "novela", "novelas", "dorama", "doramas", "anime", "animes", "desenho", "desenhos") } -> Tab.SERIES
            url.substringBefore('?').let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".avi") } &&
                tokens.any { it in setOf("vod", "filme", "filmes", "movie", "movies") } -> Tab.MOVIES
            else -> null
        }
    }

    // --------------------------------------------------------------- Xtream

    suspend fun importXtream(source: ListSource, epgWindowHours: Int, refreshEpg: Boolean): ImportOutcome {
        val base = XtreamClient(source)
        if (!base.authenticate()) error("Autenticação no painel falhou. Verifique usuário/senha e o endereço.")

        val warnings = ArrayList<String>()
        val liveCategories = base.categoryMap("get_live_categories")
        val liveStreams = base.get("get_live_streams")
        val usedKeys = HashSet<String>()
        val live = ArrayList<IptvStore.ImportedChannel>()
        var index = 0
        for (item in liveStreams) {
            index++
            val name = item.str("name").ifBlank { "Canal ${index}" }
            val streamId = item.str("stream_id")
            if (streamId.isBlank()) continue
            val tvgId = item.strOrNull("epg_channel_id")
            val epgKey = tvgId?.let(TextNormalizer::normalize) ?: TextNormalizer.normalize(name)
            live += IptvStore.ImportedChannel(
                favKey = uniqueFavKey(source.id, "ch", epgKey, usedKeys), number = item.str("num").toIntOrNull(),
                name = name, group = item.str("category_id").let { liveCategories[it] ?: "Sem categoria" },
                logo = item.strOrNull("icon"), url = base.liveUrl(streamId), headers = emptyMap(), epgKey = epgKey,
            )
        }
        if (live.isEmpty()) warnings += "O painel não retornou canais ao vivo."
        store.replaceChannels(source.id, live)

        val movies = importXtreamVod(source, base, Tab.MOVIES)
        val seriesCount = importXtreamVod(source, base, Tab.SERIES)

        var epgChannels = 0
        var epgProgrammes = 0
        var epgUnmatched = live.size
        if (refreshEpg) {
            val epgUrl = source.epgUrl.ifBlank { base.xmltvUrl() }
            if (epgUrl.isNotBlank()) {
                val outcome = importEpgFromUrl(epgUrl, epgWindowHours)
                epgChannels = outcome.epgChannels
                epgProgrammes = outcome.epgProgrammes
                epgUnmatched = outcome.epgUnmatched
                warnings += outcome.warnings
            } else {
                warnings += "Painel sem EPG XMLTV; ative get_short_epg por canal nas Configurações."
            }
        }
        return ImportOutcome(live.size, movies, seriesCount, epgChannels, epgProgrammes, epgUnmatched, warnings)
    }

    private suspend fun importXtreamVod(source: ListSource, base: XtreamClient, kind: Tab): Int {
        val action = if (kind == Tab.MOVIES) "get_vod_streams" else "get_series"
        val categories = base.categoryMap(if (kind == Tab.MOVIES) "get_vod_categories" else "get_series_categories")
        val items = base.get(action)
        val usedKeys = HashSet<String>()
        val entries = ArrayList<IptvStore.ImportedVod>()
        for (item in items) {
            val name = item.str("name").trim()
            if (name.isBlank()) continue
            val idField = if (kind == Tab.MOVIES) "stream_id" else "series_id"
            val id = item.str(idField)
            if (id.isBlank()) continue
            val added = item.long("added").let { if (it > 1_000_000_000_000L || it < 1_000_000_000L) it else it * 1_000L }
            val year = item.strOrNull("year")?.toIntOrNull()
                ?: Regex("""\b(19|20)\d{2}\b""").find(item.str("releasedate"))?.value?.toIntOrNull()
                ?: Regex("""\((\d{4})\)""").find(name)?.groupValues?.get(1)?.toIntOrNull()
            val info = item.obj("info")
            entries += IptvStore.ImportedVod(
                favKey = uniqueFavKey(source.id, "vod", TextNormalizer.normalize(name), usedKeys),
                extKey = id, name = StreamRules.cleanVodTitle(name, kind),
                category = item.str("category_id").let { categories[it] }
                    .orEmpty().ifBlank { item.str("genre").ifBlank { "Sem categoria" } },
                year = year,
                rating = item.double("rating") ?: item.double("rating_5based"),
                addedMs = if (added > 0) added else System.currentTimeMillis(),
                poster = item.strOrNull("cover"),
                backdrop = item.strOrNull("cover_big"),
                plot = item.str("plot").ifBlank { info?.str("plot").orEmpty() },
                url = if (kind == Tab.MOVIES) base.movieUrl(id, item.str("container_extension").ifBlank { "mp4" }) else "",
                runtimeMinutes = item.str("duration").toLongOrNull()?.let { (it / 60).toInt() },
                cast = item.str("actors").ifBlank { info?.str("actors").orEmpty() }.split(',', '|').map(String::trim).filter(String::isNotBlank).take(12),
                genre = item.str("genre").ifBlank { info?.str("genre").orEmpty() },
                country = item.str("country").ifBlank { info?.str("country").orEmpty() },
                language = item.str("language").ifBlank { info?.str("language").orEmpty() },
                imdbId = item.str("imdb_id").ifBlank { info?.str("imdbId").orEmpty() },
                seasons = 0,
            )
        }
        store.replaceVod(source.id, kind, entries)
        return entries.size
    }

    /** Carrega temporadas/episódios de uma série Xtream sob demanda (aba Detalhes). */
    suspend fun loadSeriesEpisodes(vodId: Long, seriesExtKey: String, source: ListSource): Int {
        val base = XtreamClient(source)
        val info = base.seriesInfo(seriesExtKey) ?: return 0
        val episodes = base.episodesFromSeriesInfo(info)
        store.replaceEpisodes(
            vodId,
            episodes.map {
                IptvStore.ImportedEpisode(
                    season = it.season, episode = it.episode, title = it.title, plot = it.plot,
                    thumb = it.thumb, url = it.streamUrl, durationMinutes = it.durationMinutes, ext = it.ext,
                )
            },
        )
        return episodes.size
    }

    // ------------------------------------------------------------ EPG sync

    suspend fun importEpg(source: ListSource, epgWindowHours: Int): ImportOutcome {
        if (source.epgUrl.isBlank()) return ImportOutcome(0, 0, 0, 0, 0, 0, listOf("Sem URL de EPG configurada."))
        return importEpgFromUrl(source.epgUrl, epgWindowHours)
    }

    private suspend fun importEpgFromUrl(epgUrl: String, epgWindowHours: Int): ImportOutcome {
        val nowMs = System.currentTimeMillis()
        val startMs = nowMs - 2 * 60 * 60 * 1000L
        val endMs = nowMs + epgWindowHours.coerceIn(3, 72) * 60L * 60_000L
        val parsed = fetchXmltv(epgUrl, startMs, endMs)
        val refs = parsed.channels.values.toList()
        val plan = EpgMatcher.buildPlans(store.epgTargets(), refs, store.currentEpgLinks())
        store.replaceProgrammes(parsed.programmes, parsed.channels.entries.associate { (k, v) -> k to v.displayNames })
        store.applyEpgLinks(plan.links.map { IptvStore.EpgLinkByFavKey(it.channelKey, it.epgChannel, it.confidence) })
        store.trimProgrammesBefore(nowMs - 3 * 60 * 60 * 1000L)
        return ImportOutcome(0, 0, 0, refs.size, parsed.programmes.size, plan.unmatched.size, parsed.warnings)
    }

    private suspend fun fetchXmltv(url: String, startMs: Long, endMs: Long): XmltvParser.Result = withContext(Dispatchers.IO) {
        val request = Http.buildRequest(url, mapOf("Accept" to "application/xml, text/xml, */*"))
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} ao baixar o EPG")
            val body = response.body ?: error("EPG vazio")
            val gzipped = response.header("Content-Encoding", "").orEmpty().contains("gzip")
            val stream = if (gzipped) GZIPInputStream(body.byteStream()) else body.byteStream()
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                XmltvParser.parse(reader, startMs, endMs)
            }
        }
    }

    // ------------------------------------------------------- repositórios

    suspend fun decodeRepoIndex(url: String): RepoIndex = RepoJson.fromJson(Http.getJson(url), url)

    internal fun uniqueFavKey(sourceId: Long, prefix: String, baseKey: String, used: HashSet<String>): String =
        com.smarted.tv.core.sources.FavKeys.unique(sourceId, prefix, baseKey, used)
}

/** Cliente Xtream Codes mínimo (player_api.php). */
class XtreamClient(private val source: ListSource) {

    private val base: String = source.url.trim().trimEnd('/')

    private fun api(action: String, extra: Map<String, String> = emptyMap()): String {
        val params = StringBuilder("username=${encode(source.username)}&password=${encode(source.password)}&action=$action")
        extra.forEach { (k, v) -> params.append('&').append(encode(k)).append('=').append(encode(v)) }
        return "$base/player_api.php?$params"
    }

    fun liveUrl(streamId: String): String = "$base/live/${encode(source.username)}/${encode(source.password)}/$streamId.m3u8"
    fun movieUrl(streamId: String, ext: String): String = "$base/movie/${encode(source.username)}/${encode(source.password)}/$streamId.${ext.ifBlank { "mp4" }}"
    fun seriesUrl(episodeStreamId: String, ext: String): String = "$base/series/${encode(source.username)}/${encode(source.password)}/$episodeStreamId.${ext.ifBlank { "mp4" }}"
    fun xmltvUrl(): String = "$base/xmltv.php?username=${encode(source.username)}&password=${encode(source.password)}"
    fun shortEpgUrl(streamId: String): String = api("get_short_epg", mapOf("stream_id" to streamId, "limit" to "6"))

    suspend fun authenticate(): Boolean {
        val element = runCatching { Http.getJson("$base/player_api.php?username=${encode(source.username)}&password=${encode(source.password)}") }.getOrNull()
        return XtreamMapper.authenticateResult(element)
    }

    suspend fun get(action: String, extra: Map<String, String> = emptyMap()): List<JsonObject> {
        val element = Http.getJson(api(action, extra))
        val (array, message) = element.unwrapList()
        if (array.isEmpty() && message != null) error("Xtream [$action]: $message")
        return array.mapNotNull { it as? JsonObject }
    }

    suspend fun categoryMap(action: String): Map<String, String> =
        get(action).associate { item -> item.str("category_id") to item.str("category_name") }.filterKeys(String::isNotBlank)

    /** Detalhamento sob demanda (plot/ano) — tolerante a painéis que não implementam. */
    suspend fun vodInfo(streamId: String): JsonObject? =
        runCatching { Http.getJson(api("get_vod_info", mapOf("vod_id" to streamId))) as? JsonObject }.getOrNull()

    suspend fun seriesInfo(seriesId: String): JsonObject? =
        runCatching { Http.getJson(api("get_series_info", mapOf("series_id" to seriesId))) as? JsonObject }.getOrNull()

    /** EPG curto (now/next) direto do painel, para quando não há XMLTV. */
    suspend fun shortEpg(streamId: String): List<com.smarted.tv.core.model.Programme> {
        val element = runCatching { Http.getJson(shortEpgUrl(streamId)) }.getOrNull() ?: return emptyList()
        return XtreamMapper.shortEpgProgrammes(streamId, element)
    }

    fun episodesFromSeriesInfo(info: JsonObject): List<XtreamEpisode> =
        XtreamMapper.episodesFromSeriesInfo(source, info).map { XtreamEpisode(it.season, it.episode, it.title, it.plot, it.thumb, it.durationMinutes, it.streamUrl, it.ext) }

    fun seasonsFromSeriesInfo(info: JsonObject): List<Pair<Int, String>> = XtreamMapper.seasonsFromSeriesInfo(info)

    data class XtreamEpisode(
        val season: Int, val episode: Int, val title: String, val plot: String,
        val thumb: String?, val durationMinutes: Int?, val streamUrl: String, val ext: String,
    )

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
