package com.smarted.tv.core.util

import com.smarted.tv.core.model.Episode
import com.smarted.tv.core.model.ItemDetails
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.Season

/**
 * Agrupa episódios soltos em **séries com temporadas**.
 *
 * As listas M3U de VOD trazem cada episódio como uma linha independente:
 *
 * ```
 * #EXTINF:-1,The Boys S01E01 - A Lei de Homelander
 * #EXTINF:-1,The Boys S01E02 - Cherry
 * ```
 *
 * A aba Séries precisa mostrar **uma série** com temporadas e episódios (é o
 * contrato da tela de detalhes: `details.seasons`). Este agrupador faz essa
 * transformação sem depender de metadados externos:
 *
 *  1. extrai o título da série removendo `S01E02`, `1x02`, `T1E2`, "Episódio 3";
 *  2. usa temporada 1 quando o episódio não informa temporada;
 *  3. ordena temporadas e episódios e deduplica por (temporada, episódio);
 *  4. escolhe como capa a primeira arte disponível entre os episódios.
 *
 * Função pura, sem Android — testada em `tools/verify-core.sh`.
 */
object SeriesGrouper {

    /** Título da série a partir do título do episódio. */
    fun showTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var out: String = raw
        // "The Boys S01E01 - A Lei" → corta tudo a partir do marcador.
        out = Regex(
            "(?i)\\s*[-–—:|]?\\s*(?:\\bs\\d{1,2}\\s*[ex]\\d{1,3}\\b|\\bt\\d{1,2}\\s*e\\d{1,3}\\b|" +
                "\\b\\d{1,2}x\\d{1,3}\\b|\\btemporada\\s*\\d{1,2}\\b|\\bseason\\s*\\d{1,2}\\b|" +
                "\\bepis[oó]dio\\s*\\d{1,3}\\b|\\bep\\.?\\s*\\d{1,3}\\b).*$",
        ).replace(out, " ")
        out = ContentClassifier.movieTitle(out)
        return out.trim().trim('-', '–', '—', ':', '|', '.').trim()
    }

    /** Título do episódio sem o nome da série e sem o marcador de temporada. */
    fun episodeTitle(raw: String?, show: String): String {
        if (raw.isNullOrBlank()) return ""
        var out: String = raw
        if (show.isNotBlank()) {
            val idx = out.indexOf(show, ignoreCase = true)
            if (idx == 0) out = out.substring(show.length)
        }
        out = out.replace(
            Regex("(?i)\\b(?:s\\d{1,2}\\s*[ex]\\d{1,3}|t\\d{1,2}\\s*e\\d{1,3}|\\d{1,2}x\\d{1,3})\\b"),
            " ",
        )
        out = TextUtils.cleanTitle(out)
        return out.ifBlank { TextUtils.cleanTitle(raw) }
    }

    /** Chave estável da série (usada em favoritos e progresso). */
    fun showId(show: String): String = "show-" + TextUtils.slug(show)

    /**
     * Agrupa os itens em séries. Itens sem marcador de episódio são ignorados
     * (não há como montar temporadas) — quem chama já filtrou por
     * [ContentClassifier.acceptsSeries].
     */
    fun group(episodes: List<MediaItem>, addonId: String = ""): List<MediaItem> {
        if (episodes.isEmpty()) return emptyList()

        val buckets = LinkedHashMap<String, Pair<String, MutableList<MediaItem>>>()

        for (item in episodes) {
            val se = M3uParser.parseSeasonEpisode(item.title) ?: continue
            val show = showTitle(item.title).ifBlank { item.title }
            if (show.isBlank()) continue
            val key = TextUtils.titleKey(show)
            if (key.isBlank()) continue
            val number = se.second.coerceAtLeast(1)
            val season = se.first.coerceAtLeast(1)
            val bucket = buckets.getOrPut(key) { show to ArrayList() }
            bucket.second.add(item.copy(number = number, subtitle = "T${season}E$number"))
        }

        val shows = ArrayList<MediaItem>(buckets.size)
        for (bucket in buckets.values) {
            val show = bucket.first
            val items = bucket.second
            if (items.isEmpty()) continue

            val seasons = ArrayList<Season>()
            val bySeason = items.groupBy { item ->
                M3uParser.parseSeasonEpisode(item.title)?.first?.coerceAtLeast(1) ?: 1
            }
            for (seasonNumber in bySeason.keys.sorted()) {
                val seasonItems = bySeason.getValue(seasonNumber)
                    .sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { TextUtils.titleKey(it.title) }))
                val seen = HashSet<Int>()
                val eps = ArrayList<Episode>(seasonItems.size)
                for (item in seasonItems) {
                    val number = item.number ?: (eps.size + 1)
                    if (!seen.add(number)) continue
                    eps.add(
                        Episode(
                            id = item.id,
                            title = episodeTitle(item.title, show).ifBlank { "Episódio $number" },
                            season = seasonNumber,
                            number = number,
                            plot = item.plot,
                            still = item.poster,
                            rating = item.rating,
                            durationSec = item.durationSec,
                            streamUrl = item.streamUrl,
                            showTitle = show,
                            showId = showId(show),
                            addonId = item.addonId.ifBlank { addonId },
                        ),
                    )
                }
                if (eps.isEmpty()) continue
                seasons.add(
                    Season(
                        number = seasonNumber,
                        title = "Temporada $seasonNumber",
                        poster = eps.firstNotNullOfOrNull { it.still },
                        episodes = eps,
                    ),
                )
            }
            if (seasons.isEmpty()) continue

            val totalEpisodes = seasons.sumOf { it.episodes.size }
            val poster = seasons.asSequence().mapNotNull { it.poster }.firstOrNull()
                ?: items.firstNotNullOfOrNull { it.poster }
            val year = ContentClassifier.yearOf(items.first().title)
            val rating = items.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average()

            shows.add(
                MediaItem(
                    id = showId(show),
                    addonId = addonId.ifBlank { items.first().addonId },
                    kind = MediaKind.SERIES,
                    title = show,
                    subtitle = "${seasons.size} ${if (seasons.size == 1) "temporada" else "temporadas"}",
                    poster = poster,
                    backdrop = items.firstNotNullOfOrNull { it.backdrop },
                    year = year,
                    rating = rating,
                    group = items.firstNotNullOfOrNull { it.group },
                    number = totalEpisodes,
                    language = items.firstNotNullOfOrNull { it.language },
                    country = items.firstNotNullOfOrNull { it.country },
                    quality = items.mapNotNull { it.quality }.maxByOrNull { qualityRank(it) },
                    details = ItemDetails(
                        poster = poster,
                        backdrop = items.firstNotNullOfOrNull { it.backdrop },
                        rating = rating,
                        seasons = seasons,
                        isSeries = true,
                        source = "m3u",
                    ),
                ),
            )
        }
        // Ordem estável: mais episódios primeiro, depois alfabética — a mesma
        // régua usada no ranking da Home.
        return shows.sortedWith(
            compareByDescending<MediaItem> { it.number ?: 0 }.thenBy { TextUtils.titleKey(it.title) },
        )
    }

    private fun qualityRank(quality: String): Int = when (quality.uppercase()) {
        "4K" -> 4
        "1080P", "FHD" -> 3
        "720P", "HD" -> 2
        "SD" -> 1
        else -> 0
    }
}
