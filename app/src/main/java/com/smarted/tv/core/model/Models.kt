package com.smarted.tv.core.model

/** Destinos de conteúdo do app. A aba decide o escopo; nada é inferido silenciosamente. */
enum class Tab { CHANNELS, MOVIES, SERIES }

/** Tipos de fonte suportados pelo repositório local (estilo OnePlay). */
enum class SourceKind { M3U, XTREAM, REPOSITORY }

/**
 * Uma fonte de lista configurada pelo usuário.
 * M3U: playlist http/asset com canais (e VOD opcional por grupos).
 * XTREAM: painel Xtream Codes (live + vod + series + EPG nativos).
 * REPOSITORY: índice JSON OnePlay-style que expande em fontes filhos.
 */
data class ListSource(
    val id: Long,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val enabled: Boolean = true,
    /** Minutos entre auto-atualizações; 0 desliga. */
    val autoUpdateMinutes: Int = 720,
    val lastRefreshMs: Long = 0,
    val status: String = "",
    val error: String = "",
) {
    val isAutoUpdateDue: Boolean
        get() = enabled && autoUpdateMinutes > 0 &&
            (System.currentTimeMillis() - lastRefreshMs) >= autoUpdateMinutes * 60_000L
}

fun ListSource.autoUpdateDueAt(nowMs: Long): Boolean =
    enabled && autoUpdateMinutes > 0 && (nowMs - lastRefreshMs) >= autoUpdateMinutes * 60_000L

/** Canal ao vivo extraído de M3U/Xtream. */
data class LiveChannel(
    val id: Long,
    val sourceId: Long,
    /** Chave derivada e estável p/ favoritos/progresso (sobrevive a re-import). */
    val favKey: String = "",
    val number: Int?,
    val name: String,
    val groupName: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val headers: Map<String, String> = emptyMap(),
    /** Chave usada para vincular ao EPG (tvg-id normalizado ou nome normalizado). */
    val epgKey: String = "",
)

/** Programa de EPG (XMLTV). */
data class Programme(
    val epgChannel: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String = "",
    val category: String = "",
)

/** Canal do XMLTV: id → nomes exibidos (para matching). */
data class EpgChannelRef(val id: String, val displayNames: List<String>, val logoUrl: String? = null)

/** Título VOD (filmes/séries) vindo de Xtream, grupos M3U ou catálogo local. */
data class VodEntry(
    val id: Long,
    val sourceId: Long,
    val favKey: String,
    val kind: Tab,
    val externalKey: String,
    val name: String,
    val category: String,
    val year: Int?,
    val rating: Double?,
    val addedMs: Long,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String,
    val streamUrl: String,
    val runtimeMinutes: Int?,
    val cast: List<String> = emptyList(),
    val genre: String = "",
    val countryCode: String = "",
    val language: String = "",
    val imdbId: String = "",
)

/** Episódio de série (Xtream get_series_info ou M3U agrupado). */
data class Episode(
    val id: Long,
    val vodId: Long,
    val season: Int,
    val episode: Int,
    val title: String,
    val plot: String = "",
    val thumbUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val streamUrl: String,
    val containerExtension: String = "mp4",
)

/** Alvo de reprodução resolvido (URL direta + headers + legendas). */
data class PlaybackTarget(
    val url: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = false,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

data class SubtitleTrack(val url: String, val language: String, val label: String = "")

/** Par agora/próximo calculado para um canal (linha do tempo do canal). */
data class NowNext(val now: Programme?, val next: Programme?, val progressPercent: Int)

/** Resultado do pareamento EPG de uma fonte. */
data class EpgMatchPlan(
    val links: List<EpgLink>,
    val unmatchedChannels: Int,
    val totalProgrammes: Int,
)

data class EpgLink(
    val channelKey: String,
    val epgChannel: String,
    /** 100 = tvg-id exato, 70-99 nome normalizado, 40-69 fuzzy, 0 sem vínculo. */
    val confidence: Int,
    val manual: Boolean = false,
)

/** Preferências do usuário (persistidas em DataStore). */
data class UserPreferences(
    val audioLanguage: String = "pt",
    val subtitleLanguage: String = "pt",
    val subtitlesEnabled: Boolean = true,
    val subtitleSizePercent: Int = 100,
    val resumePlayback: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
    val seekBackSeconds: Int = 10,
    val seekForwardSeconds: Int = 30,
    /** Exibir player de pré-visualização na coluna 3 da aba Canais. */
    val previewPlayerEnabled: Boolean = true,
    /** Janela do EPG carregada (horas a partir de agora). */
    val epgWindowHours: Int = 24,
    /** Elementum daemon habilitado para VOD/torrents. */
    val elementumEnabled: Boolean = false,
    val elementumHost: String = "127.0.0.1",
    val elementumPort: Int = 65064,
    /** Intervalo global de auto-atualização quando a fonte não define (minutos). */
    val autoUpdateMinutes: Int = 720,
) {
    init {
        require(subtitleSizePercent in 75..150) { "Tamanho de legenda inválido." }
        require(seekBackSeconds in 5..60 && seekForwardSeconds in 5..120) { "Intervalo de salto inválido." }
        require(epgWindowHours in 3..72) { "Janela de EPG inválida." }
        require(elementumPort in 1..65535) { "Porta do Elementum inválida." }
        require(autoUpdateMinutes == 0 || autoUpdateMinutes in 15..10080) { "Intervalo de atualização inválido." }
    }

    val elementumBaseUrl: String get() = "http://$elementumHost:$elementumPort"
}

/** Ordenações expostas na UI (VOD e canais). */
enum class SortMode { NAME, ADDED, RATING, YEAR }
enum class SortDirection { ASC, DESC }

data class BrowseQuery(
    val search: String = "",
    val category: String = ALL,
    val sort: SortMode = SortMode.ADDED,
    val direction: SortDirection = SortDirection.DESC,
) {
    companion object { const val ALL = "Todos"; const val FAVORITES = "★ Favoritos" }
    val isFavorites: Boolean get() = category == FAVORITES
    val isAll: Boolean get() = category == ALL
}

/** Linha da lista de canais (coluna 2). */
data class ChannelRow(
    val channel: LiveChannel,
    val now: Programme?,
    val next: Programme?,
    val progressPercent: Int,
    val isFavorite: Boolean,
)

/** Progresso local de reprodução (retomar filme/série). */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

/** Página de resultados com cursor de offset (paginação simples e estável). */
data class Page<T>(val items: List<T>, val total: Int, val hasMore: Boolean) {
    companion object {
        const val SIZE = 80
    }
}
