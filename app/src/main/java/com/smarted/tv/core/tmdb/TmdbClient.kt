package com.smarted.tv.core.tmdb

import com.smarted.tv.core.model.CastMember
import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.ItemDetails
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.Season
import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente TMDB com:
 *  - busca por título/ano (para casar com IPTV),
 *  - ficha completa (`pt-BR` por padrão), elenco em ordem de crédito,
 *  - temporadas/episódios de séries,
 *  - trailers oficiais do YouTube (reprodução sem anúncios via player nativo),
 *  - cache em memória com TTL,
 *  - fallback para `imdb_id` → `find`.
 *
 * JSON é interpretado com [JsonObject] (sem data classes rígidas) para
 * sobreviver a mudanças de schema da API.
 */
class TmdbClient(
    private val http: Http,
    apiKey: String,
    private val language: String = "pt-BR",
) {

    @Volatile
    private var key: String = apiKey.trim()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val memCache = ConcurrentHashMap<String, CacheEntry>()
    private val mutex = Mutex()

    val isConfigured: Boolean get() = key.isNotBlank()

    fun updateKey(newKey: String) {
        key = newKey.trim()
        memCache.clear()
    }

    private data class CacheEntry(val at: Long, val payload: String)

    private val TTL_MS = 6 * 60 * 60 * 1000L

    // ------------------------------------------------------------------ HTTP

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        if (!isConfigured) return null
        val query = buildString {
            append("api_key=").append(key)
            append("&language=").append(language)
            params.forEach { (k, v) -> append('&').append(k).append('=').append(enc(v)) }
        }
        val url = "$API$path?$query"
        memCache[url]?.let { if (System.currentTimeMillis() - it.at < TTL_MS) return parse(it.payload) }
        return try {
            val body = http.getText(url, cacheable = false, timeoutSeconds = 20)
            memCache[url] = CacheEntry(System.currentTimeMillis(), body)
            parse(body)
        } catch (_: Exception) {
            memCache[url]?.let { return parse(it.payload) }
            null
        }
    }

    private fun parse(raw: String): JsonObject? = try {
        json.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun enc(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8")

    // --------------------------------------------------------------- Busca

    /** Busca combinada (filme + série) e escolhe o melhor casamento. */
    suspend fun match(
        title: String,
        year: Int? = null,
        isSeries: Boolean = false,
        imdbId: String? = null,
    ): TmdbMatch? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val cleanTitle = if (title.isBlank()) return@withContext null else TextUtils.cleanTitle(title)
        if (cleanTitle.length < 2) return@withContext null

        if (!imdbId.isNullOrBlank()) {
            find(imdbId)?.let { if (it.score >= 0.5f) return@withContext it }
        }

        val query = enc(cleanTitle)
        val movie = searchRaw("/search/movie", query, year)
        val tv = searchRaw("/search/tv", query, year)
        val candidates = (movie + tv).map { it to score(it, cleanTitle, year, isSeries) }
            .filter { it.second > 0.35f }
            .sortedByDescending { it.second }
        candidates.firstOrNull()?.let { return@withContext it.first }
        null
    }

    private suspend fun searchRaw(path: String, encodedQuery: String, year: Int?): List<TmdbMatch> {
        val params = mutableMapOf<String, String>()
        if (year != null) {
            if (path.contains("/tv")) params["first_air_date_year"] = year.toString()
            else params["year"] = year.toString()
        }
        // Query precisa ser codificada uma única vez, então montamos a URL manualmente.
        val obj = getWithRawQuery(path, "query=$encodedQuery", params) ?: return emptyList()
        val results = obj["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val isTv = path.contains("/tv")
            TmdbMatch(
                tmdbId = id,
                title = o.str(if (isTv) "name" else "title") ?: o.str("original_title") ?: "",
                originalTitle = o.str(if (isTv) "original_name" else "original_title"),
                year = year(o.str(if (isTv) "first_air_date" else "release_date")),
                poster = image(o.str("poster_path"), "w500"),
                backdrop = image(o.str("backdrop_path"), "w1280"),
                overview = o.str("overview"),
                rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull,
                votes = o["vote_count"]?.jsonPrimitive?.intOrNull,
                isSeries = isTv,
                genreIds = (o["genre_ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList(),
            )
        }
    }

    private suspend fun getWithRawQuery(path: String, rawQuery: String, params: Map<String, String>): JsonObject? {
        if (!isConfigured) return null
        val extra = params.entries.joinToString("") { "&${it.key}=${enc(it.value)}" }
        val url = "$API$path?api_key=$key&language=$language&$rawQuery$extra"
        memCache[url]?.let { if (System.currentTimeMillis() - it.at < TTL_MS) return parse(it.payload) }
        return try {
            val body = http.getText(url, cacheable = false, timeoutSeconds = 20)
            memCache[url] = CacheEntry(System.currentTimeMillis(), body)
            parse(body)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun find(imdbId: String): TmdbMatch? {
        val obj = get("/find/$imdbId", mapOf("external_source" to "imdb_id")) ?: return null
        val movie = obj["movie_results"]?.jsonArray?.firstOrNull()?.jsonObject
        if (movie != null) return matchFromFind(movie, isSeries = false)
        val tv = obj["tv_results"]?.jsonArray?.firstOrNull()?.jsonObject
        if (tv != null) return matchFromFind(tv, isSeries = true)
        return null
    }

    private fun matchFromFind(o: JsonObject, isSeries: Boolean): TmdbMatch {
        val id = o["id"]?.jsonPrimitive?.intOrNull ?: 0
        return TmdbMatch(
            tmdbId = id,
            title = o.str(if (isSeries) "name" else "title") ?: "",
            originalTitle = o.str(if (isSeries) "original_name" else "original_title"),
            year = year(o.str(if (isSeries) "first_air_date" else "release_date")),
            poster = image(o.str("poster_path"), "w500"),
            backdrop = image(o.str("backdrop_path"), "w1280"),
            overview = o.str("overview"),
            rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull,
            votes = o["vote_count"]?.jsonPrimitive?.intOrNull,
            isSeries = isSeries,
            genreIds = emptyList(),
        )
    }

    private fun score(candidate: TmdbMatch, title: String, year: Int?, wantSeries: Boolean): Float {
        var score = TextUtils.similarity(candidate.title, title) * 0.62f +
            TextUtils.similarity(candidate.originalTitle ?: candidate.title, title) * 0.13f
        if (candidate.year != null && year != null) {
            val diff = kotlin.math.abs(candidate.year - year)
            score += when {
                diff == 0 -> 0.25f
                diff == 1 -> 0.12f
                diff <= 2 -> 0.05f
                else -> -0.10f
            }
        } else if (year == null) {
            score += 0.05f
        }
        if (candidate.isSeries == wantSeries) score += 0.08f else score -= 0.06f
        if ((candidate.votes ?: 0) > 50) score += 0.03f
        return score.coerceIn(0f, 1f)
    }

    // -------------------------------------------------------------- Detalhe

    /** Ficha completa do filme. */
    suspend fun movieDetails(tmdbId: Int): ItemDetails? = withContext(Dispatchers.IO) {
        val o = get(
            "/movie/$tmdbId",
            mapOf("append_to_response" to "credits,videos,images,release_dates"),
        ) ?: return@withContext null
        ItemDetails(
            plot = o.str("overview"),
            tagline = o.str("tagline"),
            status = o.str("status"),
            release = o.str("release_date"),
            runtimeMin = o["runtime"]?.jsonPrimitive?.intOrNull,
            rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull,
            votes = o["vote_count"]?.jsonPrimitive?.intOrNull,
            cast = credits(o, "cast"),
            crew = credits(o, "crew").filter {
                it.role in setOf("Director", "Screenplay", "Writer", "Story")
            }.take(6),
            genres = genres(o),
            backdrop = image(o.str("backdrop_path"), "w1280"),
            poster = image(o.str("poster_path"), "w500"),
            trailerUrl = trailer(o),
            trailerYtId = trailerId(o),
            isSeries = false,
            source = "tmdb",
            director = credits(o, "crew").firstOrNull { it.role == "Director" }?.name,
            country = (o["production_countries"]?.jsonArray ?: JsonArray(emptyList()))
                .mapNotNull { it.jsonObject.str("name") }.take(1).firstOrNull(),
        )
    }

    /** Ficha completa da série com temporadas e episódios. */
    suspend fun seriesDetails(tmdbId: Int, loadEpisodes: Boolean = true): ItemDetails? =
        withContext(Dispatchers.IO) {
            val o = get(
                "/tv/$tmdbId",
                mapOf("append_to_response" to "credits,videos,images,external_ids"),
            ) ?: return@withContext null
            val seasons = ArrayList<Season>()
            val seasonNumbers = (o["seasons"]?.jsonArray ?: JsonArray(emptyList()))
                .mapNotNull { it.jsonObject["season_number"]?.jsonPrimitive?.intOrNull }
                .filter { it > 0 }
            for (num in seasonNumbers.take(30)) {
                val seasonObj = if (loadEpisodes) get("/tv/$tmdbId/season/$num") else null
                val summary = (o["seasons"]?.jsonArray ?: JsonArray(emptyList()))
                    .map { it.jsonObject }
                    .firstOrNull { it["season_number"]?.jsonPrimitive?.intOrNull == num }
                val episodes = (seasonObj?.get("episodes")?.jsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
                    val eo = el.jsonObject
                    val epNum = eo["episode_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val epTitle = eo.str("name") ?: "Episódio $epNum"
                    Episode(
                        id = "tmdb-$tmdbId-s${num}e$epNum",
                        title = epTitle,
                        season = num,
                        number = epNum,
                        plot = eo.str("overview"),
                        still = image(eo.str("still_path"), "w500"),
                        rating = eo["vote_average"]?.jsonPrimitive?.doubleOrNull,
                        durationSec = (eo["runtime"]?.jsonPrimitive?.intOrNull ?: 0).toLong() * 60L,
                        airDate = eo.str("air_date"),
                        addonId = "",
                    )
                }
                seasons.add(
                    Season(
                        number = num,
                        title = summary?.str("name") ?: "Temporada $num",
                        poster = image(summary?.str("poster_path"), "w500"),
                        plot = summary?.str("overview"),
                        episodes = episodes,
                    ),
                )
            }
            ItemDetails(
                plot = o.str("overview"),
                tagline = o.str("tagline"),
                status = o.str("status"),
                release = o.str("first_air_date"),
                runtimeMin = (o["episode_run_time"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull),
                rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull,
                votes = o["vote_count"]?.jsonPrimitive?.intOrNull,
                cast = credits(o, "cast"),
                crew = credits(o, "crew"),
                genres = genres(o),
                backdrop = image(o.str("backdrop_path"), "w1280"),
                poster = image(o.str("poster_path"), "w500"),
                trailerUrl = trailer(o),
                trailerYtId = trailerId(o),
                seasons = seasons,
                isSeries = true,
                source = "tmdb",
                network = (o["networks"]?.jsonArray ?: JsonArray(emptyList()))
                    .mapNotNull { it.jsonObject.str("name") }.take(2).joinToString(", ").ifBlank { null },
            )
        }

    /** Season avulsa (usado quando a tela pede sob demanda). */
    suspend fun season(tmdbId: Int, seasonNumber: Int): Season? = withContext(Dispatchers.IO) {
        val o = get("/tv/$tmdbId/season/$seasonNumber") ?: return@withContext null
        val episodes = (o["episodes"]?.jsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val eo = el.jsonObject
            val epNum = eo["episode_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            Episode(
                id = "tmdb-$tmdbId-s${seasonNumber}e$epNum",
                title = eo.str("name") ?: "Episódio $epNum",
                season = seasonNumber,
                number = epNum,
                plot = eo.str("overview"),
                still = image(eo.str("still_path"), "w500"),
                rating = eo["vote_average"]?.jsonPrimitive?.doubleOrNull,
                durationSec = (eo["runtime"]?.jsonPrimitive?.intOrNull ?: 0).toLong() * 60L,
                airDate = eo.str("air_date"),
            )
        }
        Season(
            number = seasonNumber,
            title = o.str("name") ?: "Temporada $seasonNumber",
            poster = image(o.str("poster_path"), "w500"),
            plot = o.str("overview"),
            episodes = episodes,
        )
    }

    /** Enriquecimento rápido para um item de catálogo. */
    suspend fun enrich(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        mutex.withLock {
            val match = match(
                title = item.title,
                year = item.year,
                isSeries = item.kind == MediaKind.SERIES,
                imdbId = item.imdbId,
            ) ?: return@withContext item
            val details = if (match.isSeries) {
                seriesDetails(match.tmdbId, loadEpisodes = false)
            } else {
                movieDetails(match.tmdbId)
            } ?: return@withContext item
            item.copy(
                title = item.title,
                poster = item.poster ?: details.poster,
                backdrop = item.backdrop ?: details.backdrop,
                rating = item.rating ?: details.rating,
                year = item.year ?: yearOf(details.release),
                plot = item.plot ?: details.plot,
                genres = if (item.genres.isEmpty()) details.genres else item.genres,
                trailer = item.trailer ?: details.trailerYtId,
                tmdbId = match.tmdbId,
                details = details,
            )
        }
    }

    // ------------------------------------------------------------- Trending

    suspend fun trending(kind: MediaKind = MediaKind.MOVIE, window: String = "week"): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val path = when (kind) {
                MediaKind.MOVIE -> "/trending/movie/$window"
                MediaKind.SERIES -> "/trending/tv/$window"
                else -> "/trending/all/$window"
            }
            val obj = get(path) ?: return@withContext emptyList()
            (obj["results"]?.jsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val isTv = o.str("media_type") == "tv" || o.containsKey("first_air_date")
                com.smarted.tv.core.model.MediaItem(
                    id = "tmdb-$id",
                    addonId = "tmdb",
                    kind = if (isTv) MediaKind.SERIES else MediaKind.MOVIE,
                    title = o.str(if (isTv) "name" else "title") ?: return@mapNotNull null,
                    poster = image(o.str("poster_path"), "w500"),
                    backdrop = image(o.str("backdrop_path"), "w780"),
                    plot = o.str("overview"),
                    rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull,
                    year = year(o.str(if (isTv) "first_air_date" else "release_date")),
                    tmdbId = id,
                )
            }
        }

    // -------------------------------------------------------------- helpers

    private fun credits(o: JsonObject, key: String): List<CastMember> {
        val credits = o["credits"]?.jsonObject ?: return emptyList()
        val arr = credits[key]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val c = el.jsonObject
            val name = c.str("name") ?: return@mapNotNull null
            CastMember(
                name = name,
                character = c.str("character") ?: c.str("job"),
                photo = image(c.str("profile_path"), "w185"),
                order = c["order"]?.jsonPrimitive?.intOrNull ?: 999,
                role = c.str("job") ?: c.str("character"),
            )
        }.sortedBy { it.order }.take(24)
    }

    private fun genres(o: JsonObject): List<String> =
        (o["genres"]?.jsonArray ?: JsonArray(emptyList()))
            .mapNotNull { it.jsonObject.str("name") }
            .filter { it.isNotBlank() }

    private fun trailerId(o: JsonObject): String? {
        val videos = o["videos"]?.jsonObject?.get("results")?.jsonArray ?: return null
        val list = videos.map { it.jsonObject }
        val official = list.filter { it.str("site") == "YouTube" }
        val preferred = official.firstOrNull {
            it.str("type") == "Trailer" && it["official"]?.jsonPrimitive?.booleanOrNull == true
        } ?: official.firstOrNull { it.str("type") == "Trailer" }
        ?: official.firstOrNull { it.str("type") in setOf("Teaser", "Clip") }
        return preferred?.str("key")
    }

    private fun trailer(o: JsonObject): String? =
        trailerId(o)?.let { "https://www.youtube.com/watch?v=$it" }

    private fun image(path: String?, size: String): String? =
        if (path.isNullOrBlank()) null else "$IMAGE/$size$path"

    private fun year(date: String?): Int? =
        date?.take(4)?.toIntOrNull()

    private fun yearOf(date: String?): Int? = year(date)

    private fun JsonObject.str(key: String): String? = runCatching {
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull()

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        const val IMAGE = "https://image.tmdb.org/t/p"
        const val YOUTUBE_WEB = "https://www.youtube.com/watch?v="
    }
}

data class TmdbMatch(
    val tmdbId: Int,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val isSeries: Boolean = false,
    val genreIds: List<Int> = emptyList(),
    val score: Float = 1f,
)

/** Mapeamento de ids de gênero do TMDB (pt-BR). */
object TmdbGenres {
    val movie = mapOf(
        28 to "Ação", 12 to "Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime",
        99 to "Documentário", 18 to "Drama", 10751 to "Família", 14 to "Fantasia",
        36 to "História", 27 to "Terror", 10402 to "Música", 9648 to "Mistério",
        10749 to "Romance", 878 to "Ficção científica", 10770 to "Cinema TV",
        53 to "Suspense", 10752 to "Guerra", 37 to "Faroeste",
    )
    val tv = mapOf(
        10759 to "Ação & Aventura", 16 to "Animação", 35 to "Comédia", 80 to "Crime",
        99 to "Documentário", 18 to "Drama", 10751 to "Família", 10762 to "Kids",
        9648 to "Mistério", 10763 to "Notícias", 10764 to "Reality", 10765 to "Sci-Fi & Fantasia",
        10766 to "Novela", 10767 to "Talk Show", 10768 to "Guerra & Política", 37 to "Faroeste",
    )

    fun names(ids: List<Int>, isSeries: Boolean, limit: Int = 3): List<String> {
        val map = if (isSeries) tv else movie
        return ids.mapNotNull { map[it] }.take(limit)
    }
}
