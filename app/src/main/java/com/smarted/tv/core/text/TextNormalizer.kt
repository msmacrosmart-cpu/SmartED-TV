package com.smarted.tv.core.text

/**
 * Normalização compartilhada por busca, categorias e pareamento de EPG.
 * Sem dependências de Android/ICU para manter o núcleo testável em JVM puro.
 */
object TextNormalizer {

    private val ACCENT_MAP = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a',
        'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o', 'ø' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n',
    )

    /** minúsculas, sem acentos, sem pontuação, espaços colapsados. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input.lowercase()) {
            val mapped = ACCENT_MAP[ch] ?: ch
            when {
                mapped in 'a'..'z' || mapped in '0'..'9' -> { sb.append(mapped); lastWasSpace = false }
                !lastWasSpace -> { sb.append(' '); lastWasSpace = true }
            }
        }
        return sb.toString().trim()
    }

    /** true se [query] (normalizado) casar com [target] por contenção de tokens na ordem. */
    fun searchMatches(query: String, target: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        val t = normalize(target)
        if (t.contains(q)) return true
        // tolera termos fora de ordem: "viva gid" casa "globo viva a gid"
        val tokens = q.split(' ').filter(String::isNotEmpty)
        return tokens.isNotEmpty() && tokens.all { t.contains(it) }
    }

    /** chave canônica para comparações (usado em categorias e busca). */
    fun categoryKey(name: String): String {
        val n = normalize(name)
        return n.ifBlank { name.trim() }
    }
}
