package com.smarted.tv.core.epg

import com.smarted.tv.core.model.EpgChannelRef
import com.smarted.tv.core.model.EpgLink
import com.smarted.tv.core.text.TextNormalizer

/**
 * Pareamento EPG inteligente: nunca depende só de `tvg-id`.
 * Ordem: tvg-id exato → id exato → nome normalizado exato → sufixo "canal X" → fuzzy por tokens.
 * O vínculo escolhido é persistido pelo chamador; recálculos futuros só reparam os quebrados,
 * mantendo a programação sincronizada mesmo quando a lista ou o XMLTV trocam de identidade.
 */
object EpgMatcher {

    data class Plan(val links: List<EpgLink>, val matched: Int, val unmatched: List<String>)

    private val QUALITY_TOKENS = setOf(
        "hd", "sd", "fhd", "fhd", "uhd", "4k", "8k", "hq", "lq", "hevc",
        "backup", "alt", "qualita", "qualidade", "full", "plus", "hdtv", "sdtv",
    )
    private val NOISE_PREFIXES = listOf(
        "canal ", "tv ", "canale ", "el canal", "la 1", "la 2",
    )

    fun plan(
        channelKey: String,
        channelName: String,
        epgChannels: List<EpgChannelRef>,
        previous: EpgLink? = null,
    ): EpgLink? {
        // tvg-id já normalizado no importador: casa com o id do XMLTV ou com seu nome normalizado.
        val byId = epgChannels.firstOrNull {
            normalizeKey(it.id) == channelKey || it.displayNames.any { n -> normalizeKey(n) == channelKey }
        }
        if (byId != null) return EpgLink(channelKey, byId.id, 100)

        val target = scoreTarget(channelName)
        var best: Pair<EpgChannelRef, Int>? = null
        for (ref in epgChannels) {
            for (display in ref.displayNames.ifEmpty { listOf(ref.id) }) {
                val s = scoreChannel(display, target)
                if (best == null || s > best!!.second) best = ref to s
            }
        }
        val (candidate, score) = best ?: return previous?.takeIf { prev -> epgChannels.any { ref -> ref.id == prev.epgChannel } }?.let { it.copy(confidence = 40) }
        if (score >= MIN_AUTO) return EpgLink(channelKey, candidate.id, score)
        // Mantém vínculo anterior apenas se o EPG ainda conhece o canal (auto-cura).
        return previous?.takeIf { prev -> epgChannels.any { ref -> ref.id == prev.epgChannel } }?.let {
            it.copy(confidence = 40)
        }
    }

    const val MIN_AUTO = 40

    /** Remove qualificadores/prefeixos e devolve tokens minúsculos estáveis. */
    private fun scoreTarget(name: String): List<String> = tokenize(name, dropQuality = true)

    private fun scoreChannel(displayName: String, target: List<String>): Int {
        if (target.isEmpty()) return 0
        val source = tokenize(displayName, dropQuality = true)
        if (source.isEmpty()) return 0
        if (source == target) return 96
        val targetSet = target.toSet()
        val sourceSet = source.toSet()
        val overlap = targetSet.count { it in sourceSet }
        val extraInSource = sourceSet.count { it !in targetSet }
        val coverage = overlap * 100 / maxOf(targetSet.size, sourceSet.size)
        // um canal com tokens extras (ex.: "globo sp manha") pode casar com "globo" (lista) quando cobre tudo
        val coversTarget = overlap == targetSet.size
        val covered = overlap * 100 / targetSet.size
        return when {
            coversTarget && extraInSource <= 1 -> maxOf(coverage, 90)
            coversTarget -> 80
            covered >= 70 && extraInSource <= 2 -> 60
            covered >= 50 && overlap >= 2 -> 45
            else -> coverage / 2
        }
    }

    private fun tokenize(name: String, dropQuality: Boolean): List<String> {
        var normalized = TextNormalizer.normalize(name)
        for (prefix in NOISE_PREFIXES) {
            if (normalized.startsWith(prefix)) { normalized = normalized.removePrefix(prefix); break }
        }
        val tokens = normalized.split(' ').filter(String::isNotEmpty).toMutableList()
        if (dropQuality && tokens.size > 1) {
            tokens.removeAll { it in QUALITY_TOKENS }
        }
        return tokens.filter(String::isNotEmpty)
    }

    fun buildPlans(
        channels: List<Triple<String, String, String>>, // favKey, epgKey, name
        epgChannels: List<EpgChannelRef>,
        previousLinks: Map<String, EpgLink>,
    ): Plan {
        val links = ArrayList<EpgLink>(channels.size)
        val unmatched = ArrayList<String>()
        for ((key, epgKey, name) in channels) {
            val link = plan(epgKey.ifBlank { TextNormalizer.normalize(name) }, name, epgChannels, previousLinks[key])
            if (link != null) links += link.copy(channelKey = key) else unmatched += key
        }
        return Plan(links, links.size, unmatched)
    }

    fun normalizeKey(value: String): String = TextNormalizer.normalize(value)
}
