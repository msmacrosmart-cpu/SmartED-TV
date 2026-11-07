package com.smarted.tv.core.util

import java.text.Normalizer
import java.util.Locale

/**
 * Utilidades de texto puras (sem Android) — testáveis em JVM.
 * Centraliza normalização usada em busca, casamento de títulos e EPG.
 */
object TextUtils {

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Remove acentos preservando o comprimento visual apropriado. */
    fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS.replace(normalized, "")
    }

    /** Normaliza para comparação: minúsculas, sem acento, sem pontuação. */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val stripped = stripAccents(input).lowercase(Locale.ROOT)
        val sb = StringBuilder(stripped.length)
        var lastSpace = true
        for (ch in stripped) {
            val keep = ch.isLetterOrDigit()
            if (keep) {
                sb.append(ch)
                lastSpace = false
            } else if (!lastSpace) {
                sb.append(' ')
                lastSpace = true
            }
        }
        return sb.toString().trim()
    }

    /** Remove ruído típico de nomes IPTV: "[1080p]", "(H265)", "FHD", "24/7". */
    private val NOISE = listOf(
        Regex("\\[[^\\]]*]"),
        Regex("\\([^)]*\\)"),
        Regex(
            "\\b(fhd|uhd|hd|sd|4k|8k|hevc|h265|h264|avc|aac|1080p|720p|480p|1080i|720i|" +
                "multi|dual|original|legendado|dublado|24/?7|ao vivo)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex("\\s{2,}"),
    )

    fun cleanTitle(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var out: String = input
        for (r in NOISE) out = out.replace(r, " ")
        return out.trim().trim('-', '.', '|', ':', ' ').trim()
    }

    /** Base para casar títulos entre addons/EPG/TMDB. */
    fun titleKey(input: String?): String = normalize(cleanTitle(input))

    /**
     * Similaridade simples baseada em tokens (Jaccard) — sem dependências.
     * Retorna 0..1.
     */
    fun similarity(a: String?, b: String?): Float {
        val ta = titleKey(a).split(' ').filter { it.isNotBlank() && it.length > 1 }.toSet()
        val tb = titleKey(b).split(' ').filter { it.isNotBlank() && it.length > 1 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        if (ta == tb) return 1f
        val inter = ta.intersect(tb).size.toFloat()
        val union = ta.union(tb).size.toFloat()
        return if (union == 0f) 0f else inter / union
    }

    /** Mesmo título? Tolerante a sufixos de qualidade e ordem de palavras. */
    fun sameTitle(a: String?, b: String?): Boolean {
        val ka = titleKey(a)
        val kb = titleKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        return similarity(a, b) >= 0.82f
    }

    fun initials(input: String?, max: Int = 2): String {
        if (input.isNullOrBlank()) return "?"
        val parts = titleKey(input).split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return "?"
        return parts.take(max).joinToString("") { it.first().uppercase(Locale.ROOT) }
    }

    /** Extrai ID do YouTube de várias formas de URL. */
    fun youtubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("(?:youtube\\.com/watch\\?.*v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/v/|youtube-nocookie\\.com/embed/)([A-Za-z0-9_-]{6,})"),
            Regex("^([A-Za-z0-9_-]{11})$"),
        )
        for (p in patterns) {
            val m = p.find(url)
            if (m != null) return m.groupValues[1].take(11)
        }
        return null
    }

    /** URL de thumbnail do YouTube (sem chamadas de API). */
    fun youtubeThumb(id: String?): String? =
        if (id.isNullOrBlank()) null else "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    /** "1h 42min" a partir de segundos. */
    fun formatDuration(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}min"
            h > 0 -> "${h}h"
            m > 0 -> "${m}min"
            else -> "${seconds}s"
        }
    }

    fun formatMinutes(minutes: Int?): String {
        if (minutes == null || minutes <= 0) return ""
        return formatDuration(minutes.toLong() * 60L)
    }

    /** Relógio de player: 1:02:03 ou 02:03. */
    fun formatClock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Hora "HH:mm" de um epoch millis, no fuso local. */
    fun formatTime(epochMs: Long, timeZoneOffsetMs: Int = 0): String {
        val totalMin = ((epochMs + timeZoneOffsetMs) / 60000L).toInt()
        val h = ((totalMin / 60) % 24 + 24) % 24
        val m = ((totalMin % 60) + 60) % 60
        return "%02d:%02d".format(h, m)
    }

    /** Trunca preservando palavras. */
    fun ellipsize(input: String?, max: Int): String {
        if (input.isNullOrBlank()) return ""
        if (input.length <= max) return input
        val cut = input.take(max).substringBeforeLast(' ', input.take(max))
        return cut.trimEnd().trimEnd(',', ';', '.') + "…"
    }

    /** Converte HTML/entidades em texto simples (para sinopses de addons). */
    fun stripHtml(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s = input
        s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</p>"), "\n\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    /** Texto de classificação indicativa a partir de rótulos comuns. */
    fun ageRating(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().uppercase(Locale.ROOT)
        return when {
            cleaned in setOf("L", "LIVRE", "0", "G", "ALL") -> "L"
            cleaned.startsWith("12") -> "12"
            cleaned.startsWith("14") -> "14"
            cleaned.startsWith("16") -> "16"
            cleaned.startsWith("18") -> "18"
            cleaned.startsWith("10") -> "10"
            else -> null
        }
    }

    /** Slug estável para chaves. */
    fun slug(input: String): String =
        normalize(input).replace(' ', '-').ifBlank { "item" }
}
