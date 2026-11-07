package com.smarted.tv.core.util

/**
 * Filtro de idioma para separar o acervo em português do resto.
 *
 * Por que existe: as listas públicas (iptv-org e afins) misturam dezenas de
 * idiomas. O requisito do produto é que as abas Canais, Filmes e Séries
 * mostrem **somente** conteúdo em PT-BR — e cada repositório é dedicado a um
 * tipo de mídia.
 *
 * Estratégia em cascata (a primeira evidência forte decide):
 *  1. `tvg-language` / `tvg-country` do M3U — dado explícito, mais confiável.
 *  2. Nome do grupo (`group-title`) — "Brasil", "Nacional", "Dublado"…
 *  3. Título — marcadores explícitos (DUBLADO, LEGENDADO, PT-BR, (BR)).
 *  4. Dicionário de palavras funcionais do português no título (O, A, DE, DO…).
 *
 * Tudo é feito sobre texto já normalizado (sem acentos, minúsculo) por
 * [TextUtils.stripAccents], para "SÉRIES" e "series" caírem no mesmo teste.
 */
object LanguageFilter {

    /** Idiomas aceitos como português. */
    private val PT_LANGUAGE_TAGS = setOf("pt", "por", "pt-br", "pt_br", "ptbr", "português", "portugues", "brazil", "br")

    /** Códigos de país que consideramos catálogo brasileiro. */
    private val BR_COUNTRY_TAGS = setOf("br", "brazil", "brasil")

    /** Palavras de grupo que indicam conteúdo nacional/lusófono. */
    private val PT_GROUP_HINTS = listOf(
        "brasil", "brazil", "br ", "nacional", "portug", "pt-br", "ptbr",
        "dublado", "legendado", "brasileir",
    )

    /** Marcadores explícitos no título. */
    private val PT_TITLE_TAGS = listOf(
        "[br]", "(br)", "pt-br", "ptbr", "dublado", "dublada", "legendado",
        "legendada", "nacional", "brasil",
    )

    /**
     * Idiomas que, se aparecerem no título/grupo, excluem o item mesmo que
     * outras pistas sugiram português (ex.: "Movie EN/FR").
     */
    private val NON_PT_TAGS = listOf(
        "[en]", "[es]", "[fr]", "[de]", "[it]", "[ru]", "[ar]", "[tr]", "[hi]", "[ja]", "[ko]", "[zh]",
        "english", "espanol", "español", "francais", "deutsch", "italiano", "russkiy",
        "sub eng", "sub espa", "ingles ", "castellano", "latino",
    )

    /** Palavras funcionais do português — sinal estatístico de título em PT. */
    private val PT_STOPWORDS = setOf(
        "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das",
        "no", "na", "nos", "nas", "em", "com", "para", "por", "que", "e",
        "ao", "aos", "sua", "seu", "suas", "seus", "ele", "ela", "voce",
        "nao", "mais", "como", "mundo", "vida", "amor", "guerra", "cidade",
        "familia", "homem", "mulher", "noite", "dia", "tempo", "casa",
    )

    /** Palavras funcionais de espanhol — usadas para desempatar pt vs es. */
    private val ES_STOPWORDS = setOf(
        "el", "los", "las", "un", "una", "del", "al", "es", "y", "con",
        "por", "para", "que", "su", "sus", "muy", "pero", "esta", "este",
    )

    data class Verdict(
        val isPortuguese: Boolean,
        val confidence: Float,
        val reason: String,
    ) {
        companion object {
            val Unknown = Verdict(false, 0f, "sem evidência")
            val Yes = Verdict(true, 1f, "português")
        }
    }

    /**
     * Decide se um item pertence ao acervo em português.
     *
     * @param title título do item
     * @param group categoria (`group-title`)
     * @param language `tvg-language` explícito, se houver
     * @param country `tvg-country`, se houver
     */
    fun verdict(
        title: String,
        group: String? = null,
        language: String? = null,
        country: String? = null,
    ): Verdict {
        val t = TextUtils.stripAccents(title.lowercase())
        val g = TextUtils.stripAccents((group ?: "").lowercase())
        val lang = TextUtils.stripAccents((language ?: "").lowercase()).trim()
        val ctry = TextUtils.stripAccents((country ?: "").lowercase()).trim()

        // 1) metadados explícitos
        if (lang.isNotBlank()) {
            if (lang in PT_LANGUAGE_TAGS || lang.startsWith("pt")) {
                return Verdict(true, 1f, "tvg-language=$lang")
            }
            // idioma explícito diferente de português: exclui, mesmo com título ambíguo
            if (lang.length <= 6) return Verdict(false, 0.95f, "tvg-language=$lang")
        }
        if (ctry.isNotBlank()) {
            if (ctry in BR_COUNTRY_TAGS) return Verdict(true, 0.98f, "tvg-country=$ctry")
            if (ctry.length == 2 && ctry !in BR_COUNTRY_TAGS) return Verdict(false, 0.9f, "tvg-country=$ctry")
        }

        // 2) exclusão explícita no título/grupo
        val haystack = "$t $g"
        NON_PT_TAGS.firstOrNull { haystack.contains(it) }?.let { tag ->
            // "legendado" em PT não deve ser confundido com "sub eng"
            if (tag == "sub eng" && g.contains("legendado")) {
                // segue a análise
            } else {
                return Verdict(false, 0.85f, "marcador estrangeiro '$tag'")
            }
        }

        // 3) marcador explícito de português
        PT_TITLE_TAGS.firstOrNull { t.contains(it) }?.let { tag ->
            return Verdict(true, 0.9f, "marcador '$tag'")
        }
        PT_GROUP_HINTS.firstOrNull { g.contains(it.trim()) }?.let { hint ->
            return Verdict(true, 0.8f, "grupo '$hint'")
        }

        // 4) estatística de palavras funcionais
        val words = t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return Verdict.Unknown
        val ptHits = words.count { it in PT_STOPWORDS }
        val esHits = words.count { it in ES_STOPWORDS }
        val signWords = listOf("ção", "ções", "não", "são", "guer", "céu", "país")
        val signHits = signWords.count { t.contains(it) }

        // títulos curtos ("O Auto") são ruidosos: exige mais de uma evidência
        val evidence = ptHits + signHits
        val need = if (words.size <= 2) 2 else 1
        if (evidence >= need && evidence > esHits) {
            return Verdict(true, (0.5f + evidence * 0.1f).coerceAtMost(0.85f), "$evidence palavra(s) PT")
        }
        if (esHits > 0 && esHits >= ptHits) {
            return Verdict(false, 0.6f, "marcadores de espanhol")
        }
        return Verdict.Unknown
    }

    /** Atalho booleano: aceita português com confiança mínima. */
    fun isPortuguese(
        title: String,
        group: String? = null,
        language: String? = null,
        country: String? = null,
        minConfidence: Float = 0.5f,
    ): Boolean = verdict(title, group, language, country).let { it.isPortuguese && it.confidence >= minConfidence }
}
