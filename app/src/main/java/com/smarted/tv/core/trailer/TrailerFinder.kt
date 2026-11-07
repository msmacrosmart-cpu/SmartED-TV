package com.smarted.tv.core.trailer

import com.smarted.tv.core.net.Http
import com.smarted.tv.core.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Localizador de trailers (lógica herdada do "Addon Oficial").
 *
 * Ordem de resolução:
 *  1. `videoId` já conhecido (TMDB `videos`, YouTube).
 *  2. Busca por título no índice público do YouTube (`results?search_query=`),
 *     lendo `ytInitialData` da página — sem chave de API.
 *  3. Fallback: monta a URL de busca embutida do YouTube para o player nativo.
 *
 * O vídeo é sempre entregue ao [YouTubePlayer] do app (youtube-nocookie +
 * bloqueio de anúncios), portanto **não** passa pelo app do YouTube e não
 * exibe anúncios pre-roll.
 */
class TrailerFinder(private val http: Http) {

    private val cache = ConcurrentHashMap<String, String>()
    private val TTL_MS = 12 * 60 * 60 * 1000L
    private val cacheTimes = ConcurrentHashMap<String, Long>()

    data class Trailer(
        val videoId: String?,
        val title: String? = null,
        val source: String = "search",
        val searchFallbackUrl: String,
    ) {
        val hasVideo: Boolean get() = !videoId.isNullOrBlank()
    }

    /** Encontra o melhor trailer para um título/ano. */
    suspend fun find(title: String, year: Int? = null, language: String = "pt"): Trailer =
        withContext(Dispatchers.IO) {
            val clean = TextUtils.cleanTitle(title)
            val key = "${TextUtils.titleKey(clean)}|${year ?: ""}|$language"
            cache[key]?.let { cached ->
                return@withContext Trailer(cached, source = "cache", searchFallbackUrl = fallbackUrl(clean, year))
            }

            val query = buildList {
                add(clean)
                add("trailer")
                add("oficial")
                if (language.startsWith("pt")) add("legendado")
                year?.let { add(it.toString()) }
            }.joinToString(" ")

            val html = runCatching {
                http.getText(
                    "https://www.youtube.com/results?search_query=${encode(query)}&sp=EgIQAQ%253D%253D",
                    cacheable = false,
                    timeoutSeconds = 20,
                )
            }.getOrNull()

            val candidates = if (html != null) parseCandidates(html) else emptyList()
            val best = chooseBest(candidates, clean, year)
            if (best != null) {
                cache[key] = best
                cacheTimes[key] = System.currentTimeMillis()
                Trailer(best, title = clean, source = "search", searchFallbackUrl = fallbackUrl(clean, year))
            } else {
                Trailer(null, title = clean, source = "fallback", searchFallbackUrl = fallbackUrl(clean, year))
            }
        }

    fun invalidate() {
        cache.clear()
        cacheTimes.clear()
    }

    /**
     * Extrai `videoId` + título dos resultados. O YouTube embute JSON
     * (`ytInitialData`) com pares `videoRenderer` — leitura tolerante.
     */
    fun parseCandidates(html: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        val renderer = Regex("\"videoRenderer\":\\{")
        val idRegex = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
        val titleRegex = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"")
        val durationRegex = Regex("\"lengthText\":\\{\"accessibility\".*?\"simpleText\":\"(\\d{1,2}:\\d{2}(?::\\d{2})?)\"")

        for (match in renderer.findAll(html)) {
            val window = html.substring(match.range.first, minOf(html.length, match.range.first + 2600))
            val id = idRegex.find(window)?.groupValues?.get(1) ?: continue
            val title = titleRegex.find(window)?.groupValues?.get(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\u0026", "&")
                ?: ""
            val duration = durationRegex.find(window)?.groupValues?.get(1)
            out.add(Candidate(id = id, title = title, duration = duration))
            if (out.size >= 12) break
        }
        return out
    }

    /** Escolhe o candidato com melhor casamento de título e duração de trailer. */
    fun chooseBest(candidates: List<Candidate>, title: String, year: Int?): String? {
        if (candidates.isEmpty()) return null
        val scored = candidates.map { c ->
            var score = TextUtils.similarity(c.title, title) * 100.0
            val lower = c.title.lowercase()
            if (lower.contains("trailer")) score += 22
            if (lower.contains("oficial") || lower.contains("official")) score += 12
            if (lower.contains("teaser")) score -= 10
            if (lower.contains("react") || lower.contains("review") || lower.contains("gameplay")) score -= 60
            if (lower.contains("dublado")) score += 6
            year?.let { if (lower.contains(it.toString())) score += 10 }
            c.duration?.let { d ->
                val seconds = parseDuration(d)
                // trailers oficiais têm tipicamente 1–4 minutos
                if (seconds in 45..300) score += 10 else if (seconds > 1200) score -= 40
            }
            c to score
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second < 30) candidates.first().id else best.first.id
    }

    private fun parseDuration(text: String): Int {
        val parts = text.split(':').mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private fun fallbackUrl(title: String, year: Int?): String {
        val q = buildList {
            add(title)
            add("trailer oficial")
            year?.let { add(it.toString()) }
        }.joinToString(" ")
        return "https://www.youtube.com/embed?listType=search&list=${encode(q)}"
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    data class Candidate(val id: String, val title: String, val duration: String? = null)
}
