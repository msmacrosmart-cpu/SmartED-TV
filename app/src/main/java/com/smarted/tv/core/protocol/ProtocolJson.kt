package com.smarted.tv.core.protocol

import com.smarted.tv.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AddonException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Strict JSON syntax, tolerant protocol shapes. Never accept HTML, unquoted keys or arbitrary JS. */
object ProtocolJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun root(raw: String, context: String): JsonObject {
        val clean = raw.removePrefix("\uFEFF").trim()
        if (clean.isEmpty()) throw AddonException("$context: resposta vazia.")
        if (clean.startsWith('<')) throw AddonException("$context: o servidor retornou uma página HTML, não JSON. Confira a URL do manifest.json.")
        val element = try { json.parseToJsonElement(clean) } catch (e: Exception) {
            throw AddonException("$context: JSON inválido (${e.message?.take(160) ?: "erro de sintaxe"}).", e)
        }
        return element as? JsonObject ?: throw AddonException("$context: a raiz precisa ser um objeto JSON.")
    }

    fun manifest(raw: String): Manifest {
        val obj = root(raw, "Manifesto")
        fun required(key: String): String = obj.text(key)?.takeIf(String::isNotBlank)
            ?: throw AddonException("Manifesto sem \"$key\" válido. Use a URL completa de manifest.json.")
        val catalogs = obj["catalogs"].objects().mapIndexed { index, item ->
            val type = item.text("type") ?: throw AddonException("Catálogo ${index + 1}: campo \"type\" ausente ou inválido.")
            val id = item.text("id")?.takeIf(String::isNotBlank)
                ?: throw AddonException("Catálogo ${index + 1}: campo \"id\" ausente ou inválido.")
            val extras = (item["extra"].objects().mapNotNull { e ->
                e.text("name")?.let {
                    ExtraSpec(it, e.bool("isRequired"), e.strings("options"), e.integer("optionsLimit"))
                }
            } + item.strings("extraSupported").map { ExtraSpec(it) } +
                item.strings("extraRequired").map { ExtraSpec(it, true) })
                .distinctBy { it.name }
                .groupBy { it.name }.map { (name, values) ->
                    ExtraSpec(name, values.any { it.required }, values.flatMap { it.options }.distinct(), values.firstNotNullOfOrNull { it.optionsLimit })
                }
            CatalogSpec(type, id, item.text("name").orEmpty(), extras,
                (item["genres"].strings() + item["genres"].objects().mapNotNull { it.text("name") }).distinct(),
                (item.integer("pageSize") ?: 100).coerceIn(1, 1000))
        }
        if (catalogs.map { it.type to it.id }.distinct().size != catalogs.size) {
            throw AddonException("Manifesto contém catálogos duplicados (mesmo type/id).")
        }
        val hints = obj["behaviorHints"] as? JsonObject ?: JsonObject(emptyMap())
        return Manifest(required("id"), required("name"), required("version"),
            obj.text("description").orEmpty(), obj.text("logo"), obj.text("background"), obj.strings("types"),
            obj["resources"].objectsOrStrings().mapNotNull { resource ->
                when (resource) {
                    is JsonPrimitive -> resource.takeIf { it.isString }?.content?.let { ResourceSpec(it) }
                    is JsonObject -> resource.text("name")?.let {
                        ResourceSpec(it, resource.strings("types"),
                            if (resource["idPrefixes"] is JsonArray) resource.strings("idPrefixes") else null)
                    }
                    else -> null
                }
            }, catalogs, hints.bool("configurationRequired"), obj.text("configurationUrl"), hints.bool("epgProvider"))
    }

    fun metas(raw: String): List<JsonObject> {
        val root = root(raw, "Catálogo")
        val values = root["metas"] ?: root["metasDetailed"]
        if (values !is JsonArray) throw AddonException("Catálogo: \"metas\" ou \"metasDetailed\" precisa ser uma lista JSON.")
        return values.filterIsInstance<JsonObject>()
    }

    fun meta(raw: String): JsonObject {
        val root = root(raw, "Detalhes")
        val meta = root["meta"]
        if (meta !is JsonObject) throw AddonException("Detalhes: campo \"meta\" ausente ou inválido.")
        return meta
    }

    fun streams(raw: String): List<MediaStream> {
        val values = root(raw, "Streams")["streams"]
        if (values !is JsonArray) throw AddonException("Streams: campo \"streams\" precisa ser uma lista JSON.")
        return values.filterIsInstance<JsonObject>().map(::stream)
    }

    fun item(obj: JsonObject, addonUrl: String, scope: Scope, fallbackType: String): MediaItem? {
        val id = obj.text("id")?.takeIf(String::isNotBlank) ?: return null
        val name = obj.text("name")?.takeIf(String::isNotBlank) ?: return null
        val type = obj.text("type") ?: fallbackType
        // Explicit scope is a hard boundary, even if an addon returns mixed results.
        if (type !in scope.wireTypes) return null
        val hints = obj["behaviorHints"] as? JsonObject ?: JsonObject(emptyMap())
        val videos = obj["videos"].objects().mapNotNull { video ->
            val videoId = video.text("id") ?: return@mapNotNull null
            Video(videoId, video.text("title") ?: video.text("name").orEmpty(),
                video.integer("season") ?: video.integer("seasonNumber"),
                video.integer("episode") ?: video.integer("number"),
                video.text("released"), video.text("startTime") ?: video.text("start"),
                video.text("endTime") ?: video.text("end"), video.text("thumbnail"),
                video.text("description") ?: video.text("overview").orEmpty(),
                (video["streams"] as? JsonArray)?.filterIsInstance<JsonObject>()?.map(::stream).orEmpty())
        }
        return MediaItem(addonUrl, id, type, scope, name, obj.text("poster"), obj.text("background"),
            obj.text("logo"), obj.text("description") ?: obj.text("overview").orEmpty(),
            obj.text("releaseInfo").orEmpty(), obj.text("released"),
            obj.text("imdbRating")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..10.0 },
            obj.text("runtime").orEmpty(), obj.strings("genres").ifEmpty {
                obj["links"].objects().filter { it.text("category") == "genre" }.mapNotNull { it.text("name") }
            }, obj.strings("cast"), obj.strings("director"), obj.text("language").orEmpty(), obj.text("country").orEmpty(),
            videos, (obj["streams"] as? JsonArray)?.filterIsInstance<JsonObject>()?.map(::stream).orEmpty(),
            hints.text("defaultVideoId"), hints.bool("hasScheduledVideos") || scope == Scope.CHANNELS)
    }

    fun stream(obj: JsonObject): MediaStream {
        val hints = obj["behaviorHints"] as? JsonObject ?: JsonObject(emptyMap())
        val proxy = hints["proxyHeaders"] as? JsonObject ?: JsonObject(emptyMap())
        val headers = (proxy["request"] as? JsonObject ?: proxy).entries
            .filter { it.key !in setOf("request", "response") }
            .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it } }.toMap()
        return MediaStream(obj.text("url"), obj.text("externalUrl"), obj.text("infoHash"), obj.text("ytId"),
            obj.text("name").orEmpty(), obj.text("description") ?: obj.text("title").orEmpty(), headers,
            obj["subtitles"].objects().mapNotNull { s ->
                val url = s.text("url") ?: return@mapNotNull null
                Subtitle(s.text("id") ?: url, url, s.text("lang") ?: s.text("language").orEmpty())
            })
    }

    private fun JsonElement?.text(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)
        ?.takeIf { it !is JsonNull && (it.isString || it.content.toDoubleOrNull() != null || it.content in setOf("true", "false")) }?.content
    private fun JsonElement?.bool(key: String): Boolean = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.let {
        it.content.equals("true", true)
    } ?: false
    private fun JsonElement?.integer(key: String): Int? = (this as? JsonObject)?.get(key).text()?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= Int.MIN_VALUE.toDouble() && it <= Int.MAX_VALUE.toDouble() }?.toInt()
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)
        ?.takeIf { it !is JsonNull && (it.isString || it.content.toDoubleOrNull() != null || it.content in setOf("true", "false")) }?.content
    private fun JsonElement?.strings(key: String): List<String> = (this as? JsonObject)?.get(key).strings()
    private fun JsonElement?.strings(): List<String> = when (this) {
        is JsonArray -> mapNotNull { it.text() }.filter(String::isNotBlank)
        is JsonPrimitive -> text()?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        else -> emptyList()
    }
    private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
    private fun JsonElement?.objectsOrStrings(): List<JsonElement> = (this as? JsonArray)
        ?.filter { it is JsonObject || it is JsonPrimitive }.orEmpty()
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)
    ?.takeIf { it !is JsonNull && (it.isString || it.content.toDoubleOrNull() != null || it.content in setOf("true", "false")) }?.content
