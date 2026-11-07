package com.smarted.tv.data.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Leitura defensiva de JSON de provedores (Xtream/Elementum variam tipos por servidor).
 * Strings viram números e vice-versa sem derrubar a importação.
 */

fun JsonObject.str(key: String, fallback: String = ""): String = when (val el = this[key]) {
    null, JsonNull -> fallback
    is JsonPrimitive -> el.contentOrNull ?: fallback
    else -> fallback
}

fun JsonObject.strOrNull(key: String): String? = when (val el = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> el.contentOrNull?.takeIf(String::isNotBlank)
    else -> null
}

fun JsonObject.long(key: String, fallback: Long = 0): Long = when (val el = this[key]) {
    null, JsonNull -> fallback
    is JsonPrimitive -> el.contentOrNull?.trim()?.toDoubleOrNull()?.toLong() ?: el.rawLong ?: fallback
    else -> fallback
}

fun JsonObject.int(key: String, fallback: Int = 0): Int = long(key, fallback.toLong()).toInt()

fun JsonObject.double(key: String): Double? = when (val el = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> el.contentOrNull?.trim()?.toDoubleOrNull() ?: el.rawDouble
    else -> null
}

fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean = when (val el = this[key]) {
    null, JsonNull -> fallback
    is JsonPrimitive -> el.rawBool ?: (el.contentOrNull?.trim()?.lowercase() in setOf("true", "1", "yes", "on"))
    else -> fallback
}

fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)

fun JsonElement.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

/**
 * Xtream pode responder um OBJETO (erro) onde esperava LISTA. Normaliza:
 * lista vazia quando o objeto é de erro; lista de um item quando é coleção nomeada.
 * Retorna também a mensagem de erro detectada, para o status da fonte.
 */
fun JsonElement.unwrapList(errorKeys: List<String> = listOf("errors", "message", "status", "info")): Pair<JsonArray, String?> {
    if (this is JsonArray) return this to null
    if (this is JsonObject) {
        // lista nomeada: {"epg_listings": [...]} / {"items": [...]} / {"movies": [...]}
        for (key in listOf("items", "results", "movies", "series", "list", "epg_listings", "seasons", "episodes", "categories", "streams")) {
            val arr = this[key]
            if (arr is JsonArray) {
                val nestedError = errorKeys.firstNotNullOfOrNull { k ->
                    val el = this[k]
                    if (el is JsonPrimitive) el.contentOrNull?.takeIf(String::isNotBlank) else null
                }
                return arr to nestedError
            }
        }
        // objeto de erro?
        val userAuth = obj("user_info")?.strOrNull("auth")
        if (userAuth != null && userAuth != "1") return JsonArray(emptyList()) to "Credenciais rejeitadas pelo painel."
        val message = errorKeys.firstNotNullOfOrNull { k ->
            when (val el = this[k]) {
                is JsonPrimitive -> el.contentOrNull?.takeIf(String::isNotBlank)
                is JsonArray -> (el.firstOrNull() as? JsonPrimitive)?.contentOrNull
                else -> null
            }
        }
        if (isEmpty()) return JsonArray(emptyList()) to null
        return JsonArray(listOf(this)) to message
    }
    return JsonArray(emptyList()) to "Resposta inesperada do provedor."
}

private val JsonPrimitive.rawDouble: Double? get() = if (isString) null else content.toDoubleOrNull()
private val JsonPrimitive.rawLong: Long? get() = if (isString) null else content.toLongOrNull()
private val JsonPrimitive.rawBool: Boolean? get() = if (isString) null else contentOrNull?.toBooleanStrictOrNull()
