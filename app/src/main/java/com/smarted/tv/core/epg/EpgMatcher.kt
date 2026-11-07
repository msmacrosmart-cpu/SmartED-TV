package com.smarted.tv.core.epg

import com.smarted.tv.core.util.TextUtils
import com.smarted.tv.core.util.XmltvParser

/**
 * Cruzamento inteligente entre canais da lista M3U e a grade XMLTV.
 *
 * Por que existe: as listas públicas quase nunca trazem `tvg-id` correto — ora
 * vem vazio, ora vem com o id do provedor (`globo.br@SD`), ora com o nome do
 * canal escrito de outra forma. Sem isso, o guia aparecia vazio para a maioria
 * dos canais mesmo com o XMLTV carregado.
 *
 * A estratégia é em camadas, da evidência mais forte para a mais fraca:
 *
 *  1. `tvg-id` exato no índice do XMLTV.
 *  2. `tvg-id` sem sufixo de qualidade/país (`globo.br@SD` → `globo`).
 *  3. Nome normalizado exato — remove HD/FHD/SD/4K, `|`, `[tags]`, `(tags)`.
 *  4. Similaridade de texto (tokens + prefixo + contenção), acima do limiar.
 *
 * O passo 4 é o "matching automático por nome": compara o nome limpo do canal
 * com o `display-name` de cada canal do XMLTV e escolhe o melhor score. Quando
 * dois candidatos empatam ou o score fica abaixo de [DEFAULT_THRESHOLD], nada é
 * escolhido — melhor não ter grade do que mostrar a programação de outro canal.
 *
 * Tudo aqui é função pura sobre texto, sem Android, e é coberto por testes em
 * `tools/verify-core.sh`.
 */
object EpgMatcher {

    /** Score mínimo para aceitar um cruzamento apenas por nome. */
    const val DEFAULT_THRESHOLD = 0.74f

    /** Acima disso o par é considerado praticamente certo. */
    const val STRONG_THRESHOLD = 0.92f

    /**
     * Tokens de ruído removidos do nome antes da comparação.
     *
     * Inclui qualidade (HD/FHD/4K), codecs, sufixos de feed/backup e o prefixo
     * "TV"/"Canal", que costuma existir de um lado só ("TV Cultura" ↔ "Cultura").
     */
    private val NOISE_TOKENS = setOf(
        // qualidade e codecs
        "hd", "fhd", "uhd", "sd", "hq", "hdr", "4k", "8k", "2k",
        "hevc", "h265", "h264", "avc", "aac", "ac3", "mp2", "mp4", "mkv",
        "1080", "1080p", "1080i", "720", "720p", "480", "480p", "360", "240", "2160",
        // feeds, espelhos e variações
        "raw", "backup", "bkp", "alt", "alternativo", "alternativa", "opc", "opcao",
        "feed", "multi", "multi2", "secundario", "principal", "fonte", "espelho",
        // sufixos comerciais que não mudam a emissora
        "24h", "24", "7", "aovivo", "vivo", "live", "online",
        // prefixos genéricos
        "tv", "canal", "channel", "ch", "rede", "network",
    )

    /** Ruído removido também do nome de exibição (não inclui "TV"/"Canal"). */
    private val DISPLAY_NOISE = setOf(
        "hd", "fhd", "uhd", "sd", "hq", "hdr", "4k", "8k", "2k",
        "hevc", "h265", "h264", "avc", "1080p", "1080i", "720p", "480p",
        "1080", "720", "480", "2160", "raw", "backup", "bkp", "alt", "alternativo",
        "alternativa", "multi", "multi2", "24h",
    )

    /** Qualificadores que distinguem canais diferentes da mesma marca. */
    private val QUALIFIERS = setOf(
        "news", "esporte", "esportes", "sports", "sport", "brasil", "brazil",
        "premium", "play", "max", "plus", "kids", "infantil", "jovem", "jovens",
        "radio", "music", "musica", "documentario", "documentary", "series",
        "novelas", "cinema", "action", "comedy", "internacional", "global",
    )

    private val BRACKETS = listOf(
        Regex("\\[[^\\]]*]"),
        Regex("\\([^)]*\\)"),
        Regex("\\{[^}]*}"),
        Regex("<[^>]*>"),
    )

    private val SEPARATORS = Regex("[|/\\\\•·–—_,;:+*\"'`´]+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Nome de exibição "limpo": remove tags, separadores e ruído, preservando o
     * texto que o usuário reconhece. Usado na UI (ex.: mostrar "Globo" em vez de
     * "Globo HD | Backup").
     */
    fun displayName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var out: String = raw
        for (r in BRACKETS) out = out.replace(r, " ")
        out = out.replace(SEPARATORS, " ")
        out = TextUtils.cleanTitle(out)
        // Tira só o ruído de qualidade/feed ("HD", "FHD", "Backup"), mantendo
        // o nome que o usuário reconhece — inclusive prefixos como "TV" e "Canal".
        val words = out.split(' ').filter { word ->
            word.isNotBlank() && TextUtils.stripAccents(word.lowercase()) !in DISPLAY_NOISE
        }
        val rebuilt = words.joinToString(" ")
        return rebuilt.ifBlank { out }.replace(Regex("\\s{2,}"), " ").trim()
    }

    /**
     * Chave de comparação: minúscula, sem acento, sem ruído de qualidade e com
     * tokens ordenados de forma estável (a ordem não deve influenciar o match).
     */
    fun normalize(raw: String?): String = tokensOf(raw).joinToString(" ")

    /** Tokens significativos de um nome de canal (vazio se só houver ruído). */
    fun tokensOf(raw: String?): List<String> {
        val cleaned = displayName(raw)
        if (cleaned.isBlank()) return emptyList()
        val flat = TextUtils.stripAccents(cleaned.lowercase())
        val candidates = flat.split(NON_WORD).filter { it.isNotBlank() }
        val kept = ArrayList<String>(candidates.size)
        for (token in candidates) {
            // "24h" já colapsou em "24" + "h" pelo split; ambos saem por NOISE.
            if (token in NOISE_TOKENS) continue
            // letras soltas ("a", "e") só atrapalham a comparação
            if (token.length == 1 && !token[0].isDigit()) continue
            kept.add(token)
        }
        // Se tudo era ruído (ex.: canal chamado "TV HD"), mantém o original.
        return if (kept.isEmpty()) candidates else kept
    }

    /** Dígitos do nome — distinguem "ESPN" de "ESPN 2", "SporTV" de "SporTV 2". */
    private fun digitsOf(raw: String?): String =
        tokensOf(raw).filter { tok -> tok.all { it.isDigit() } }.joinToString(",")

    /** Qualificadores presentes (news, esportes, brasil…). */
    private fun qualifiersOf(raw: String?): Set<String> =
        tokensOf(raw).filter { it in QUALIFIERS }.toSet()

    /**
     * Similaridade entre dois nomes de canal, de 0 a 1.
     *
     * Combina três sinais complementares:
     *  - **contenção**: o conjunto menor está contido no maior ("Globo" ⊂ "Globo SP");
     *  - **prefixo**: quanto do início do nome casa ("Ban" de "Band" não basta);
     *  - **Jaccard**: sobreposição geral dos tokens.
     *
     * Penalidades evitam os erros clássicos de casamento automático:
     *  - dígitos diferentes ("ESPN 2" × "ESPN");
     *  - qualificador de um lado só ("Band" × "BandNews");
     *  - marcas que só compartilham uma palavra genérica.
     */
    fun score(a: String?, b: String?): Float {
        val ta = tokensOf(a)
        val tb = tokensOf(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f

        val na = ta.joinToString(" ")
        val nb = tb.joinToString(" ")
        if (na == nb) return 1f

        // Compara também sem espaços: "redeglobo" × "rede globo".
        if (na.replace(" ", "") == nb.replace(" ", "")) return 0.98f

        val setA = ta.toSet()
        val setB = tb.toSet()
        val inter = setA.intersect(setB)
        if (inter.isEmpty()) return 0f

        val smaller = minOf(setA.size, setB.size)
        val union = setA.union(setB).size
        val containment = inter.size.toFloat() / smaller.toFloat()
        val jaccard = inter.size.toFloat() / union.toFloat()

        val commonChars = commonPrefixLength(na, nb)
        val prefix = commonChars.toFloat() / maxOf(na.length, nb.length).toFloat()

        var score = 0.55f * containment + 0.30f * prefix + 0.15f * jaccard

        // Dígitos diferentes quase sempre são canais diferentes (ESPN × ESPN 2).
        if (digitsOf(a) != digitsOf(b)) score -= 0.18f

        // Qualificador exclusivo: Band × BandNews, Globo × Globo News.
        val qa = qualifiersOf(a)
        val qb = qualifiersOf(b)
        val onlyOne = (qa - qb) + (qb - qa)
        if (onlyOne.isNotEmpty()) score -= 0.12f * onlyOne.size

        // Compartilhar apenas uma palavra muito curta/genérica é sinal fraco.
        if (inter.all { it.length <= 2 }) score -= 0.25f

        return score.coerceIn(0f, 1f)
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    /** Candidato do XMLTV com o score calculado. */
    data class Match(
        val id: String,
        val name: String,
        val score: Float,
    )

    /**
     * Ranqueia todos os canais do XMLTV contra o nome do canal da lista.
     * Ordenado do melhor para o pior; nunca devolve score 0.
     */
    fun rank(
        channelName: String?,
        channels: Map<String, XmltvParser.Channel>,
        friendlyName: ((XmltvParser.Channel) -> String)? = null,
    ): List<Match> {
        if (channelName.isNullOrBlank() || channels.isEmpty()) return emptyList()
        val scored = ArrayList<Match>(channels.size)
        for ((id, ch) in channels) {
            val name = friendlyName?.invoke(ch) ?: ch.name
            // O id do XMLTV também é comparado: muitos guias usam "globo.br"
            // como id e "Globo" como display-name.
            val byName = score(channelName, name)
            val byId = score(channelName, id.replace('.', ' ').replace('-', ' '))
            val best = maxOf(byName, byId * 0.96f)
            if (best > 0f) scored.add(Match(id = id, name = name, score = best))
        }
        return scored.sortedWith(
            compareByDescending<Match> { it.score }.thenBy { it.id },
        )
    }

    /**
     * Melhor cruzamento para um canal, considerando também o `tvg-id` declarado.
     *
     * Devolve `null` quando nada passa do limiar — o guia fica "sem informação",
     * que é o comportamento correto quando o casamento é duvidoso.
     */
    fun bestMatch(
        channelName: String?,
        tvgId: String?,
        channels: Map<String, XmltvParser.Channel>,
        threshold: Float = DEFAULT_THRESHOLD,
    ): Match? {
        if (channels.isEmpty()) return null

        // 1) tvg-id bate exatamente com um canal do guia — testando as
        // variações comuns: "globo", "globo.br", "globo.br@SD", "globo-hd".
        tvgId?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            val variants = listOf(
                raw,
                raw.substringBefore('@'),
                raw.substringBefore('.'),
                raw.substringBefore('@').substringBefore('.'),
                raw.replace('.', ' ').replace('@', ' ').trim(),
            ).filter { it.isNotBlank() }
            for (variant in variants) {
                channels[variant]?.let { return Match(it.id, it.name, 1f) }
                // o id do guia pode ter o ponto que o tvg-id não tem
                channels.values.firstOrNull {
                    it.id.equals(variant, ignoreCase = true)
                }?.let { return Match(it.id, it.name, 1f) }
            }
        }

        // 2) tvg-id sem sufixo, comparado ao índice normalizado
        val idKey = tvgId?.let { normalize(it.substringBefore('@').replace('.', ' ')) }.orEmpty()
        if (idKey.isNotBlank()) {
            val exact = channels.values.firstOrNull { normalize(it.id.replace('.', ' ')) == idKey }
            if (exact != null) return Match(exact.id, exact.name, 0.97f)
        }

        // 3) nome normalizado idêntico
        val nameKey = normalize(channelName)
        if (nameKey.isNotBlank()) {
            val exact = channels.values.firstOrNull { normalize(it.name) == nameKey }
            if (exact != null) return Match(exact.id, exact.name, 0.95f)
        }

        // 4) similaridade — só aceita se houver um vencedor claro
        val ranked = rank(channelName, channels)
        val best = ranked.firstOrNull() ?: return null
        if (best.score < threshold) return null
        val runnerUp = ranked.getOrNull(1)
        if (runnerUp != null && best.score - runnerUp.score < 0.02f && best.score < STRONG_THRESHOLD) {
            return null // empate técnico: não arrisca a grade de outro canal
        }
        return best
    }

    /**
     * Índice de nomes normalizados → id do XMLTV. Serve de atalho para o
     * casamento exato antes de partir para a similaridade (evita varrer todos
     * os canais a cada consulta numa TV box).
     */
    fun buildIndex(channels: Map<String, XmltvParser.Channel>): Map<String, String> {
        val index = HashMap<String, String>(channels.size * 2)
        for ((id, ch) in channels) {
            val byName = normalize(ch.name)
            if (byName.isNotBlank()) index.putIfAbsent(byName, id)
            val byId = normalize(ch.id.replace('.', ' '))
            if (byId.isNotBlank()) index.putIfAbsent(byId, id)
        }
        return index
    }
}
