package com.smarted.tv.core.repo

import com.smarted.tv.core.model.SourceKind

/**
 * Formato de "repositório" no estilo OnePlay: um JSON indexando várias fontes (M3U/Xtream)
 * com URLs de playlist, EPG e política de auto-atualização. Importar um repositório cria
 * uma fonte filha por entrada; a atualização do índice re-sincroniza as filhas (add/remove/update).
 *
 * ```json
 * {
 *   "name": "Minha Lista",
 *   "sources": [
 *     {"name": "Canais BR", "kind": "m3u", "url": "https://…/lista.m3u", "epg": "https://…/epg.xml", "update_minutes": 720},
 *     {"name": "Painel", "kind": "xtream", "url": "http://host:8080", "username": "u", "password": "p"}
 *   ]
 * }
 * ```
 */
data class RepoIndex(
    val name: String,
    val sources: List<RepoSource>,
)

data class RepoSource(
    val name: String,
    val kind: SourceKind,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val autoUpdateMinutes: Int = 720,
)

/** Diferença entre o índice atual e a importação anterior (identidade = url+kind). */
data class RepoDiff(
    val added: List<RepoSource>,
    val updated: List<RepoSource>,
    val removed: List<String>, // chaves de fonte removidas
) {
    val isEmpty: Boolean get() = added.isEmpty() && updated.isEmpty() && removed.isEmpty()

    companion object {
        fun keyOf(source: RepoSource): String = "${source.kind.name}|${source.url.trim().trimEnd('/')}"
        fun keyOfNameUrl(name: String, url: String, kind: SourceKind): String = "${kind.name}|${url.trim().trimEnd('/')}"
    }
}

fun computeRepoDiff(index: RepoIndex, existing: Map<String, Triple<String, String, Int>>): RepoDiff {
    val added = ArrayList<RepoSource>()
    val updated = ArrayList<RepoSource>()
    val seen = HashSet<String>()
    for (source in index.sources) {
        val key = RepoDiff.keyOf(source)
        if (key in seen) continue // dedupe dentro do próprio índice
        seen += key
        val previous = existing[key]
        when {
            previous == null -> added += source
            previous.first != source.name || previous.second != source.epgUrl || previous.third != source.autoUpdateMinutes -> updated += source
        }
    }
    val removed = existing.keys.filter { it !in seen }
    return RepoDiff(added, updated, removed)
}
