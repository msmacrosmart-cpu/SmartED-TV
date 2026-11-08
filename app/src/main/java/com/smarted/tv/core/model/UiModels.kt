package com.smarted.tv.core.model

import androidx.compose.runtime.Immutable

/**
 * Modelos da camada de UI (layout SmartED "Black Glass"). O adaptador
 * [com.smarted.tv.core.data.IptvContentRepository] projeta as entidades do banco
 * (LiveChannel/VodEntry/StoredEpisode…) nestes tipos. IDs de favorito são
 * prefixados (`c:` canais, `m:` filmes, `s:` séries) para compartilhar um único set.
 */

const val CAT_FAVORITES = "__fav__"
const val CAT_ALL = "__all__"

enum class DeviceMode { PHONE, TV }

@Immutable
data class Category(val id: String, val name: String)

@Immutable
data class EpgProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String? = null,
) {
    fun isLive(now: Long) = now in startMs until endMs
    fun progress(now: Long): Float =
        ((now - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
}

@Immutable
data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val streamUrl: String?,
    val epg: List<EpgProgram>,
    /** Cabeçalhos exigidos pela fonte (token de sessão etc.). */
    val headers: Map<String, String> = emptyMap(),
    /** Chave estável no banco (favoritos/EPG sobrevivem a re-import). */
    val favKey: String = "",
) {
    fun current(now: Long): EpgProgram? = epg.firstOrNull { it.isLive(now) }
    fun upcoming(now: Long): List<EpgProgram> = epg.filter { it.endMs > now }
}

enum class VodKind { MOVIE, SERIES }

@Immutable
data class Episode(
    val id: String,
    val number: Int,
    val title: String,
    val plot: String,
    val durationMin: Int?,
    val streamUrl: String?,
)

@Immutable
data class Season(val number: Int, val episodes: List<Episode>)

@Immutable
data class VodItem(
    val id: String,
    val kind: VodKind,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val categoryId: String,
    val year: Int?,
    val rating: Double?,
    val addedAtMs: Long,
    val durationMin: Int?,
    val genres: List<String>,
    val plot: String,
    val streamUrl: String?,
    val seasons: List<Season> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val favKey: String = "",
)

/** Pedido de reprodução enviado pelas telas ao player em tela cheia. */
data class PlaybackRequest(
    val title: String,
    val url: String?,
    val headers: Map<String, String> = emptyMap(),
    /** Chave de progresso/favorito ("" = sem retomada). */
    val favKey: String = "",
    /** "channel" | "movie" | "series" — separa favoritos e histórico. */
    val kind: String = "movie",
    val isLive: Boolean = false,
    /** Série: próximo episódio na fila (autoavanço do player). */
    val next: PlaybackRequest? = null,
)

enum class SourceType { XTREAM, M3U_URL, M3U_FILE, LOCAL_REPO, REPOSITORY }

/** Fonte listada em Configurações; espelho bidirecional de [ListSource]. */
data class PlaylistSource(
    val id: String,
    val name: String,
    val type: SourceType,
    /** Xtream: servidor (http://host:porta). M3U_URL/REPOSITORY: URL. M3U_FILE/LOCAL_REPO: content:// URI. */
    val url: String,
    val username: String? = null,
    val password: String? = null,
    /** URL XMLTV opcional (M3U/Repositório). */
    val epgUrl: String? = null,
    /** Estado da última sincronização (vindos do banco). */
    val status: String = "",
    val error: String = "",
    val lastRefreshMs: Long = 0,
    val enabled: Boolean = true,
    val autoUpdateMinutes: Int = 720,
)
