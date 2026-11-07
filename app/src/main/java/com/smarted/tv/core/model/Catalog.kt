package com.smarted.tv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tipo de conteúdo suportado pelo media center. */
@Serializable
enum class MediaKind {
    LIVE, MOVIE, SERIES, EPISODE, UNKNOWN;

    companion object {
        fun fromRoute(raw: String?): MediaKind = when (raw?.lowercase()) {
            "live", "tv", "channels", "channel" -> LIVE
            "movies", "movie", "vod", "films", "filmes" -> MOVIE
            "series", "serie", "shows", "shows_tv" -> SERIES
            "episode", "episodes", "episodios" -> EPISODE
            else -> UNKNOWN
        }
    }
}

/** Referência estável a um item de catálogo (chave de cache/favoritos/progresso). */
@Serializable
data class ItemRef(
    val addonId: String,
    val kind: MediaKind,
    val id: String,
) {
    val key: String get() = "$addonId|${kind.name}|$id"
}

/** Item genérico de catálogo: canal, filme ou série. */
@Serializable
data class MediaItem(
    val id: String,
    val addonId: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val plot: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val logo: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val durationSec: Long? = null,
    val group: String? = null,
    val number: Int? = null,
    val tvgId: String? = null,
    val streamUrl: String? = null,
    val trailer: String? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    /** Metadados ricos carregados sob demanda (ficha do item). */
    val details: ItemDetails? = null,
    /** Pista de qualidade detectada no nome, ex.: "4K", "1080p", "HD". */
    val quality: String? = null,
    val ext: String? = null,
) {
    val ref: ItemRef get() = ItemRef(addonId, kind, id)
}

/** Ficha estendida exibida na tela de detalhes. */
@Serializable
data class ItemDetails(
    val plot: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val release: String? = null,
    val runtimeMin: Int? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val cast: List<CastMember> = emptyList(),
    val crew: List<CastMember> = emptyList(),
    val genres: List<String> = emptyList(),
    val backdrop: String? = null,
    val poster: String? = null,
    val trailerUrl: String? = null,
    val trailerYtId: String? = null,
    val seasons: List<Season> = emptyList(),
    val isSeries: Boolean = false,
    val source: String = "addon",
    val logos: List<String> = emptyList(),
    val country: String? = null,
    val director: String? = null,
    val network: String? = null,
)

@Serializable
data class CastMember(
    val name: String,
    val character: String? = null,
    val photo: String? = null,
    val order: Int = 0,
    val role: String? = null,
)

@Serializable
data class Season(
    val number: Int,
    val title: String? = null,
    val poster: String? = null,
    val plot: String? = null,
    val episodes: List<Episode> = emptyList(),
)

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val season: Int = 1,
    val number: Int = 1,
    val plot: String? = null,
    val still: String? = null,
    val rating: Double? = null,
    val durationSec: Long? = null,
    val airDate: String? = null,
    val streamUrl: String? = null,
    val showTitle: String? = null,
    val showId: String? = null,
    val addonId: String = "",
    val kind: MediaKind = MediaKind.EPISODE,
) {
    fun toMediaItem(): MediaItem = MediaItem(
        id = id,
        addonId = addonId,
        kind = MediaKind.EPISODE,
        title = title,
        subtitle = "T${season}E${number}",
        plot = plot,
        poster = still,
        rating = rating,
        durationSec = durationSec,
        number = number,
        streamUrl = streamUrl,
    )
}

/** Resultado de resolução de reprodução. */
@Serializable
data class PlayInfo(
    val url: String,
    val title: String = "",
    val subtitle: String? = null,
    val poster: String? = null,
    val kind: MediaKind = MediaKind.MOVIE,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val isLive: Boolean = false,
    val durationSec: Long? = null,
    val startPositionMs: Long = 0L,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<String> = emptyList(),
    val sources: List<StreamSource> = emptyList(),
    val selectedSourceIndex: Int = 0,
    /** Próximo episódio para "continuar assistindo". */
    val nextEpisode: Episode? = null,
    val ref: ItemRef? = null,
)

@Serializable
data class SubtitleTrack(
    val url: String,
    val language: String = "und",
    val label: String? = null,
    val mimeType: String = "application/x-subrip",
)

@Serializable
data class StreamSource(
    val url: String,
    val label: String = "",
    val quality: String? = null,
    val bitrate: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    val priority: Int = 0,
)

/** Programa de EPG (guia de programação). */
@Serializable
data class EpgProgram(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val episodeNum: String? = null,
    val rating: String? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    fun progressAt(now: Long): Float =
        if (now <= startMs) 0f
        else if (now >= endMs) 1f
        else ((now - startMs).toFloat() / durationMs.coerceAtLeast(1L).toFloat())
}

/** Uma faixa (categoria) de catálogo exibida como linha horizontal. */
@Serializable
data class CatalogRow(
    val title: String,
    val items: List<MediaItem> = emptyList(),
    val kind: MediaKind = MediaKind.MOVIE,
    val showProgress: Boolean = false,
)

@Serializable
data class CatalogPage(
    val title: String = "",
    val rows: List<CatalogRow> = emptyList(),
    val items: List<MediaItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val total: Int? = null,
    val nextPage: Int? = null,
    val kind: MediaKind = MediaKind.UNKNOWN,
)

/** Fonte externa de mídia (playlist pública, mirror, addon). */
@Serializable
data class PublicSource(
    val id: String,
    val name: String,
    val url: String,
    val kind: MediaKind = MediaKind.LIVE,
    val format: SourceFormat = SourceFormat.M3U,
    val group: String? = null,
    val logo: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val lastCheckedMs: Long = 0L,
    val healthy: Boolean = true,
    @SerialName("lastError") val lastError: String? = null,
)

@Serializable
enum class SourceFormat { M3U, XTREAM, PLUGIN, XMLTV }

/** Item de resultado de busca unificada. */
data class SearchResult(
    val item: MediaItem,
    val score: Int = 0,
)

/** Progresso de reprodução persistido. */
@Serializable
data class WatchProgress(
    val ref: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String = "",
    val poster: String? = null,
    val kind: MediaKind = MediaKind.MOVIE,
    val season: Int? = null,
    val episode: Int? = null,
    val showId: String? = null,
    val addonId: String = "",
    val itemId: String = "",
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val isFinished: Boolean get() = fraction >= 0.94f
}
