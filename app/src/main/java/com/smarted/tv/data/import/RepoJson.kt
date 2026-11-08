package com.smarted.tv.data.import

import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.repo.RepoIndex
import com.smarted.tv.core.repo.RepoSource
import com.smarted.tv.data.json.long
import com.smarted.tv.data.json.str
import com.smarted.tv.data.json.strOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Decodifica o índice JSON de um repositório de fontes (estilo OnePlay). */
object RepoJson {

    fun fromJson(element: JsonElement, repoUrl: String): RepoIndex {
        val root = element as? JsonObject ?: error("O repositório deve ser um JSON objeto na raiz.")
        val name = root.str("name").ifBlank { repoUrl.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Repositório" } }
        val sourcesArray = root["sources"] as? JsonArray ?: error("Falta a lista \"sources\" no JSON do repositório.")
        val items = sourcesArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val url = obj.str("url").ifBlank { obj.str("playlist").ifBlank { obj.str("m3u") } }
            if (url.isBlank()) return@mapNotNull null
            val kindName = obj.str("kind").ifBlank { obj.str("type") }.lowercase()
            val hasCredentials = obj.strOrNull("username") != null && obj.strOrNull("password") != null
            val kind = if (kindName == "xtream" || hasCredentials) SourceKind.XTREAM else SourceKind.M3U
            RepoSource(
                name = obj.str("name").ifBlank { url.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Fonte" } },
                kind = kind, url = url,
                username = obj.str("username"), password = obj.str("password"),
                epgUrl = obj.str("epg").ifBlank { obj.str("epg_url") },
                autoUpdateMinutes = obj.long("update_minutes", 720L).coerceIn(0, 10_080).toInt(),
            )
        }
        if (items.isEmpty()) error("Nenhuma fonte válida no JSON (itens precisam de \"url\").")
        return RepoIndex(name, items)
    }
}
