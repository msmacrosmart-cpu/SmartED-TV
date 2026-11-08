package com.smarted.tv.core.parse

import com.smarted.tv.core.model.SourceKind
import com.smarted.tv.core.repo.RepoIndex
import com.smarted.tv.core.repo.RepoSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser de índice de repositório estilo OnePlay / Kodi.
 *
 * Aceita:
 *   - URL raw do índice (index.json) com {"name":..., "sources":[...]}
 *   - Estrutura de diretórios do OnePlay com nó "addons" e diretórios raiz
 *     (plugins/addons).
 *
 * Trata:
 *   - Compressão HTTP (GZIP) — já tratado em Http.decodeBytes.
 *   - BOM / caracteres nulos (UTF-8 com BOM) — limpeza do buffer antes de decodificar.
 *   - Respostas HTML de bloqueio — já rejeitado em Http.readJsonText.
 *
 * O corpo bruto já deve chegar limpo (sem BOM, sem GZIP, sem HTML) pois o
 * Http.readJsonText + decodeBytes cuidam disso antes de chegar aqui.
 */
object RepoJsonParser {

    fun fromJson(element: JsonElement, repoUrl: String): RepoIndex {
        val root = element as? JsonObject ?: error("O repositório deve ser um JSON objeto na raiz.")

        val name = root.strOrNull("name")
            ?.takeIf { it.isNotBlank() }
            ?.let { it }
            ?: repoUrl.substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Repositório"

        // Tenta "sources" primeiro (formato OnePlay padrão), depois "addons" (estilo Kodi).
        val sourcesArray = when {
            root["sources"] is JsonArray -> root["sources"] as JsonArray
            root["addons"] is JsonArray -> root["addons"] as JsonArray
            else -> error("Falta a lista \"sources\" (ou \"addons\") no JSON do repositório.")
        }

        val items = sourcesArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null

            // Aceita múltiplos nomes de campo para a URL da fonte.
            val url = obj.strOrNull("url")
                ?.takeIf { it.isNotBlank() }
                ?: obj.strOrNull("playlist")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("m3u")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("source")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("file")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("path")
                    ?.takeIf { it.isNotBlank() }
            if (url.isBlank()) return@mapNotNull null

            val kindName = obj.strOrNull("kind")
                ?.takeIf { it.isNotBlank() }
                ?: obj.strOrNull("type")
                ?.takeIf { it.isNotBlank() }
                ?: ""
            val hasCredentials = obj.strOrNull("username") != null && obj.strOrNull("password") != null
            val kind = if (kindName.lowercase() == "xtream" || kindName.lowercase() == "xstream" || hasCredentials) {
                SourceKind.XTREAM
            } else {
                SourceKind.M3U
            }

            val updateMin = obj.longOrNull("update_minutes")
                ?: obj.longOrNull("auto_update_minutes")
                ?: 720L

            RepoSource(
                name = obj.strOrNull("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: obj.strOrNull("title")
                    ?.takeIf { it.isNotBlank() }
                    ?: url.substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Fonte",
                kind = kind,
                url = url,
                username = obj.strOrNull("username") ?: "",
                password = obj.strOrNull("password") ?: "",
                epgUrl = obj.strOrNull("epg")
                    ?: obj.strOrNull("epg_url")
                    ?: obj.strOrNull("epgUrl")
                    ?: "",
                autoUpdateMinutes = updateMin.coerceIn(0, 10_080).toInt(),
            )
        }

        if (items.isEmpty()) error("Nenhuma fonte válida no JSON (itens precisam de \"url\").")
        return RepoIndex(name, items)
    }

    /** Limpa BOM (\\uFEFF) e caracteres nulos de um texto antes de decodificar JSON. */
    fun cleanJsonText(raw: String): String {
        var cleaned = raw.trim()
        // Remove BOM se presente
        if (cleaned.startsWith("\uFEFF")) cleaned = cleaned.substring(1)
        // Remove caracteres nulos (\u0000) que podem quebrar o parser
        cleaned = cleaned.replace("\u0000", "")
        return cleaned
    }

    private fun JsonObject.strOrNull(key: String): String? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.content.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.content.toLongOrNull()
            else -> null
        }
    }
