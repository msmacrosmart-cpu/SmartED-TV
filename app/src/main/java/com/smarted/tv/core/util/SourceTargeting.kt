package com.smarted.tv.core.util

import com.smarted.tv.core.model.MediaKind

/**
 * Destinos (abas) de uma fonte de conteúdo.
 *
 * Por que existe: uma lista M3U pode conter **só canais**, **só filmes** ou um
 * acervo misto de um painel Xtream. Em vez de adivinhar pelo `group-title`
 * (que erra muito), o usuário marca explicitamente onde a fonte se aplica:
 *
 * ```
 * [x] Usar para Canais (Live TV)
 * [ ] Usar para Filmes (VOD)
 * [x] Usar para Séries
 * ```
 *
 * A marcação é a intenção do usuário; o [ContentClassifier] continua sendo a
 * segunda barreira, item a item (um canal ao vivo marcado em "Filmes" é
 * descartado de qualquer forma).
 *
 * A serialização usa texto separado por vírgula porque cabe no campo `kind`
 * já existente no banco — sem migração e sem perder compatibilidade com os
 * catálogos JSON antigos (`"kind": "LIVE"` continua valendo).
 */
object SourceTargeting {

    /** Todas as abas de conteúdo, na ordem de exibição. */
    val ALL: List<MediaKind> = listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES)

    /** Marcas textuais aceitas além do nome do enum. */
    private val ALIASES: Map<String, MediaKind> = mapOf(
        "LIVE" to MediaKind.LIVE,
        "TV" to MediaKind.LIVE,
        "CANAL" to MediaKind.LIVE,
        "CANAIS" to MediaKind.LIVE,
        "CHANNEL" to MediaKind.LIVE,
        "CHANNELS" to MediaKind.LIVE,
        "MOVIE" to MediaKind.MOVIE,
        "MOVIES" to MediaKind.MOVIE,
        "FILME" to MediaKind.MOVIE,
        "FILMES" to MediaKind.MOVIE,
        "VOD" to MediaKind.MOVIE,
        "SERIES" to MediaKind.SERIES,
        "SERIE" to MediaKind.SERIES,
        "SÉRIES" to MediaKind.SERIES,
        "SHOW" to MediaKind.SERIES,
        "SHOWS" to MediaKind.SERIES,
    )

    /**
     * Lê a marcação gravada em texto: `"LIVE,MOVIE"`, `"live;series"` ou
     * `"Filmes"`. Tokens desconhecidos são ignorados e a ordem de [ALL] é
     * preservada para a saída ser estável.
     */
    fun parse(raw: String?): List<MediaKind> {
        if (raw.isNullOrBlank()) return emptyList()
        val found = HashSet<MediaKind>()
        raw.split(',', ';', '|', ' ', '/')
            .map { TextUtils.stripAccents(it.trim()).uppercase() }
            .filter { it.isNotBlank() }
            .forEach { token -> ALIASES[token]?.let { found.add(it) } }
        return ALL.filter { it in found }
    }

    /** Grava a marcação em texto (ordem estável, sem duplicatas). */
    fun encode(kinds: Collection<MediaKind>): String =
        ALL.filter { it in kinds }.joinToString(",") { it.name }

    /** A fonte deve alimentar esta aba? */
    fun routesTo(targets: Collection<MediaKind>, kind: MediaKind): Boolean = kind in targets

    /**
     * Destinos efetivos de uma fonte: a marcação explícita quando existe;
     * caso contrário, o tipo único legado (`kind`).
     */
    fun effective(explicit: Collection<MediaKind>, fallback: MediaKind): List<MediaKind> =
        if (explicit.isNotEmpty()) ALL.filter { it in explicit } else listOf(fallback)

    /** Rótulo curto em português para a marcação (ex.: "Canais + Filmes"). */
    fun describe(targets: Collection<MediaKind>): String {
        val ordered = ALL.filter { it in targets }
        return when {
            ordered.isEmpty() -> "Nenhuma aba"
            ordered.size == ALL.size -> "Todas as abas"
            else -> ordered.joinToString(" + ") { label(it) }
        }
    }

    private fun label(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "Canais"
        MediaKind.MOVIE -> "Filmes"
        MediaKind.SERIES -> "Séries"
        else -> "Outros"
    }
}
