package com.smarted.tv.core.util

import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind

/**
 * Formatação dos metadados exibidos nos cartões e no palco da Home.
 *
 * Tudo aqui é puro (sem Android) e determinístico, para que os cartões mostrem
 * sempre a mesma linha de metadados: `2013 · 16 · 2h 30min`. A classificação
 * indicativa vem da fonte quando existe ([MediaItem.certification]) e, quando a
 * fonte não informa, é inferida a partir do gênero/grupo — nunca inventamos um
 * selo para item sem nenhuma pista de gênero.
 */
object MediaMeta {

    private val FAMILY_WORDS = listOf(
        "infantil", "familia", "família", "animacao", "animação", "desenho",
        "kids", "aventura", "aventuras",
    )
    private val TEEN_WORDS = listOf(
        "acao", "ação", "ficcao", "ficção", "fantasia", "super-herói", "super-heroi",
        "misterio", "mistério", "esporte",
    )
    private val ADULT_WORDS = listOf(
        "terror", "horror", "guerra", "crime", "suspense", "thriller", "policial",
        "erotico", "erótico", "drama",
    )

    /** Duração curta: `2h 30min`, `48min`, `2h`. */
    fun runtimeLabel(seconds: Long?): String? {
        if (seconds == null || seconds <= 0L) return null
        val minutes = (seconds / 60L).toInt()
        if (minutes <= 0) return null
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours <= 0 -> "${rest}min"
            rest == 0 -> "${hours}h"
            else -> "${hours}h ${rest}min"
        }
    }

    /** Duração a partir de minutos (fichas do TMDB). */
    fun runtimeLabel(minutes: Int?): String? =
        if (minutes == null || minutes <= 0) null else runtimeLabel(minutes * 60L)

    /**
     * Classificação indicativa do item: a da fonte ou uma inferência pelo
     * gênero. Devolve `null` quando não há nenhuma pista — a UI esconde o selo.
     */
    fun ageRating(item: MediaItem): String? {
        val declared = item.certification?.trim().orEmpty()
        if (declared.isNotEmpty()) return declared.uppercase()
        if (item.kind == MediaKind.LIVE) return null
        val haystack = buildString {
            append(item.group.orEmpty())
            append(' ')
            append(item.genres.joinToString(" "))
            append(' ')
            append(item.details?.genres?.joinToString(" ").orEmpty())
        }.lowercase()
        if (haystack.isBlank()) return null
        return when {
            ADULT_WORDS.any { haystack.contains(it) } -> "16"
            TEEN_WORDS.any { haystack.contains(it) } -> "12"
            FAMILY_WORDS.any { haystack.contains(it) } -> "L"
            else -> null
        }
    }

    /** Sinopse limpa (HTML fora) para o palco e as fichas. */
    fun synopsis(item: MediaItem, limit: Int = 320): String {
        val raw = item.details?.plot ?: item.plot ?: ""
        val clean = TextUtils.stripHtml(raw).replace("\n", " ").trim()
        return if (clean.length > limit) TextUtils.ellipsize(clean, limit) else clean
    }

    /** Gêneros legíveis: usa `details.genres` e cai para `genres`. */
    fun genres(item: MediaItem, max: Int = 3): List<String> {
        val source = if (item.details?.genres?.isNotEmpty() == true) {
            item.details.genres
        } else {
            item.genres
        }
        return source
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(max)
    }

    /** Linha compacta dos cartões: `2013 · 16 · 2h 30min`. */
    fun shortMeta(item: MediaItem, withYear: Boolean = true): String {
        val parts = ArrayList<String>(4)
        if (withYear) item.year?.takeIf { it > 1900 }?.let { parts.add(it.toString()) }
        ageRating(item)?.let { parts.add(it) }
        runtimeLabel(item.durationSec ?: item.details?.runtimeMin?.let { it * 60L })?.let { parts.add(it) }
        if (parts.isEmpty()) {
            genres(item, 2).firstOrNull()?.let { parts.add(it) }
        }
        return parts.joinToString(" · ")
    }

    /** Linha longa do palco: `2013 · 16 · 2h 30min · Ação, Suspense`. */
    fun fullMeta(item: MediaItem): String {
        val parts = ArrayList<String>(5)
        item.year?.takeIf { it > 1900 }?.let { parts.add(it.toString()) }
        ageRating(item)?.let { parts.add(it) }
        runtimeLabel(item.durationSec ?: item.details?.runtimeMin?.let { it * 60L })?.let { parts.add(it) }
        val genres = genres(item, 3)
        if (genres.isNotEmpty()) parts.add(genres.joinToString(", "))
        return parts.joinToString(" · ")
    }
}
