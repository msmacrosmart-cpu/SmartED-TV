package com.smarted.tv.core.sources

/**
 * Chaves de favorito/progresso derivadas (fonte + nome normalizado), não rowids:
 * sobrevivem a re-imports e a trocas de URL do canal. Sufixo `#n` resolve colisões.
 */
object FavKeys {

    fun unique(sourceId: Long, prefix: String, baseKey: String, used: HashSet<String>): String {
        var candidate = "$prefix:$sourceId:$baseKey"
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$prefix:$sourceId:$baseKey#$suffix"
            suffix++
        }
        return candidate
    }
}
