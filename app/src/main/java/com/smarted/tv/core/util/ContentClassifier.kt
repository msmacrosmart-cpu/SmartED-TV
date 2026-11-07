package com.smarted.tv.core.util

import com.smarted.tv.core.model.MediaKind

/**
 * Separação rigorosa entre **ao vivo**, **filme** e **série**.
 *
 * Por que existe: as listas públicas de IPTV são, quase sempre, listas de
 * *canais*. Quando o usuário apontava uma dessas listas para o campo "Filmes",
 * a aba de filmes enchia de canais ao vivo (Globo, SBT, ESPN…) — exatamente o
 * defeito que este classificador corrige.
 *
 * A decisão usa o que é verificável no próprio dado, na ordem:
 *
 *  1. **Formato do stream** — `.mp4/.mkv/.avi…` é arquivo estático (VOD);
 *     `.m3u8/.mpd/rtmp/udp` é transmissão (ao vivo).
 *  2. **Caminho da URL** — `/movie/`, `/vod/`, `/series/` (padrão Xtream e
 *     painéis de VOD) identificam catálogo mesmo quando o arquivo é `.ts`.
 *  3. **Marcadores de episódio** — `S01E02`, `1x02`, `T1E2`, `Episódio 3`,
 *     `Temporada 1` só existem em série.
 *
 * Nada aqui depende do `group-title` declarado pela lista: é comum um canal
 * estar no grupo "Cinema" só porque exibe filmes 24h — e ele continua sendo um
 * canal ao vivo.
 */
object ContentClassifier {

    /** Extensões de arquivo de vídeo estático (VOD de verdade). */
    val STATIC_VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "wmv", "flv", "mpg", "mpeg",
        "ogv", "ogg", "3gp", "divx", "vob", "m2ts", "mts",
    )

    /** Extensões típicas de transmissão contínua. */
    private val STREAM_EXTENSIONS: Set<String> = setOf("m3u8", "m3u", "mpd")

    /** Esquemas que só existem em transmissão ao vivo. */
    private val LIVE_SCHEMES = listOf("rtmp://", "rtmps://", "udp://", "rtp://", "srt://", "rtsp://")

    /** Caminhos que indicam biblioteca de VOD (Xtream, painéis, Plex-like). */
    private val VOD_PATHS = Regex(
        "(?i)/(movie|movies|vod|filmes?|film|cinema|store|video|videos|series|serie|shows?|tvshows?)/",
    )

    /** Marcas de episódio/temporada no título. */
    private val SEASON_EPISODE = Regex(
        "(?i)(?:\\bs(\\d{1,2})\\s*[ex](\\d{1,3})\\b|\\bt(\\d{1,2})\\s*e(\\d{1,3})\\b|" +
            "\\b(\\d{1,2})x(\\d{1,3})\\b|temporada\\s*(\\d{1,2})|season\\s*(\\d{1,2})|" +
            "\\bepis[oó]dio\\s*(\\d{1,3})\\b|\\bep\\.?\\s*(\\d{1,3})\\b)",
    )

    /** Extensão do arquivo, ignorando query string e fragmento. */
    fun extensionOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val clean = url.substringBefore('#').substringBefore('?')
        val lastSegment = clean.substringAfterLast('/')
        if (!lastSegment.contains('.')) return null
        val ext = lastSegment.substringAfterLast('.').lowercase()
        return ext.ifBlank { null }
    }

    /** É um arquivo de vídeo estático (MP4, MKV, AVI…)? */
    fun isStaticFile(url: String?): Boolean = extensionOf(url) in STATIC_VIDEO_EXTENSIONS

    /** É uma transmissão contínua (HLS/DASH/RTMP/UDP)? */
    fun isStream(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (LIVE_SCHEMES.any { lower.startsWith(it) }) return true
        return extensionOf(url) in STREAM_EXTENSIONS
    }

    /** URL está dentro de uma biblioteca de VOD (`/movie/`, `/series/`…). */
    fun hasVodPath(url: String?): Boolean = !url.isNullOrBlank() && VOD_PATHS.containsMatchIn(url)

    /** O título tem estrutura de episódio (`S01E02`, `1x02`, `Temporada 2`)?. */
    fun hasEpisodeMarker(title: String?): Boolean = !title.isNullOrBlank() && SEASON_EPISODE.containsMatchIn(title)

    /**
     * Motivo legível da classificação — usado em diagnóstico/Ajustes, para o
     * usuário entender por que um item entrou (ou saiu) de uma aba.
     */
    fun explain(kind: MediaKind, title: String?, url: String?): String = when (kind) {
        MediaKind.LIVE -> {
            if (acceptsLive(url)) "transmissão ao vivo" else "arquivo de VOD"
        }
        MediaKind.MOVIE -> {
            when {
                isStaticFile(url) -> "arquivo estático"
                hasVodPath(url) -> "caminho de VOD"
                isStream(url) -> "transmissão contínua (não é filme)"
                else -> "sem evidência de VOD"
            }
        }
        MediaKind.SERIES -> {
            when {
                hasEpisodeMarker(title) -> "episódio identificado"
                hasVodPath(url) -> "caminho de série"
                else -> "sem estrutura de temporadas"
            }
        }
        else -> "sem classificação"
    }

    /**
     * Canais: aceita tudo que **não** seja claramente um arquivo de VOD.
     * Um canal 24h com `.mp4` em loop continua sendo canal; um item vindo de
     * `/movie/` com `.mkv` não é.
     */
    fun acceptsLive(url: String?): Boolean {
        if (isStaticFile(url) && hasVodPath(url)) return false
        return true
    }

    /**
     * Filmes: só VOD real.
     *
     * Rejeita explicitamente `.m3u8`/`.mpd`/RTMP sem caminho de VOD — são os
     * canais ao vivo que apareciam na aba de filmes.
     */
    fun acceptsMovie(title: String?, url: String?): Boolean {
        if (hasEpisodeMarker(title)) return false
        if (isStaticFile(url)) return true
        if (hasVodPath(url)) return true
        if (isStream(url)) return false
        // URL sem extensão (ex.: painel que serve via `/play/<id>`): aceita
        // apenas se o caminho tiver cara de biblioteca.
        return false
    }

    /**
     * Séries: só conteúdo estruturado por temporada/episódio.
     *
     * A estrutura pode vir do título (`S01E02`) ou do caminho (`/series/…`);
     * sem nenhuma das duas o item é descartado, porque não há como montar
     * temporadas e episódios na tela de detalhes.
     */
    fun acceptsSeries(title: String?, url: String?): Boolean {
        if (hasEpisodeMarker(title)) return true
        return hasVodPath(url) && (url?.contains("series", true) == true ||
            url?.contains("serie", true) == true ||
            url?.contains("show", true) == true)
    }

    /**
     * Classifica um item bruto da lista. Devolve `null` quando o item não se
     * encaixa no tipo pedido — nesse caso ele é descartado daquela aba.
     */
    fun classifyFor(
        expected: MediaKind,
        title: String?,
        url: String?,
    ): MediaKind? = when (expected) {
        MediaKind.LIVE -> if (acceptsLive(url)) MediaKind.LIVE else null
        MediaKind.MOVIE -> if (acceptsMovie(title, url)) MediaKind.MOVIE else null
        MediaKind.SERIES -> if (acceptsSeries(title, url)) MediaKind.SERIES else null
        else -> null
    }

    /** Remove prefixos de catálogo do título de um filme: "FILMES | Nome" → "Nome". */
    fun movieTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withoutPrefix = raw.trim()
            .replace(Regex("(?i)^(filmes?|movies?|vod|cinema|lancamentos?)\\s*[:|\\-]\\s*"), "")
        return TextUtils.cleanTitle(withoutPrefix).ifBlank { TextUtils.cleanTitle(raw) }
    }

    /** Ano entre parênteses/colchetes no título, se houver. */
    fun yearOf(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("(?:19|20)\\d{2}").findAll(raw).lastOrNull() ?: return null
        val year = match.value.toIntOrNull() ?: return null
        return if (year in 1900..2100) year else null
    }
}
