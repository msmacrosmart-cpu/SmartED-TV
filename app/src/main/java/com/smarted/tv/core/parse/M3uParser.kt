package com.smarted.tv.core.parse

import com.smarted.tv.core.text.TextNormalizer

/**
 * Parser de playlists M3U/M3U8 (RFC-8216 estendida com tags IPTV).
 * Suporta: #EXTINF (duração, nome, atributos tvg-*, group-title, channel-id, tvg-shift,
 * tvg-order/kodi-numbers), #EXTGRP, #EXTVLCOPT/#EXTHTTP (headers) e #EXTM3U URL-tvg.
 * Erros por linha não derrubam a lista: o canal inválido é ignorado e registrado em `warnings`.
 */
object M3uParser {

    data class Result(
        val channels: List<ParsedChannel>,
        val warnings: List<String>,
    )

    data class ParsedChannel(
        val name: String,
        val groupName: String,
        val logoUrl: String?,
        val tvgId: String?,
        val streamUrl: String,
        val number: Int?,
        val headers: Map<String, String>,
    ) {
        val epgKey: String get() = tvgId?.takeIf(String::isNotBlank)?.let { TextNormalizer.normalize(it) }
            ?: TextNormalizer.normalize(name)
    }

    private val EXTINF = "#EXTINF"
    private val QUOTED_ATTR = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(content: String): Result {
        val channels = ArrayList<ParsedChannel>()
        val warnings = ArrayList<String>()
        var pending: PendingInf? = null
        var pendingGroup: String? = null
        var pendingHeaders: MutableMap<String, String>? = null
        var pendingNumber: Int? = null

        val lines = content.split('\n', '\r')
        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line == "#EXTM3U" || line.startsWith("#EXTM3U ") -> Unit
                line.startsWith(EXTINF) -> {
                    pending = parseExtInf(line)
                    pendingGroup = null
                    pendingHeaders = null
                    pendingNumber = null
                }
                line.startsWith("#EXTGRP") -> pendingGroup = line.substringAfter(':', "").trim().ifEmpty { null }
                line.startsWith("#EXTVLCOPT:") -> {
                    val kvs = parseOption(line.removePrefix("#EXTVLCOPT:"))
                    if (kvs.isNotEmpty()) pendingHeaders = (pendingHeaders ?: mutableMapOf()).apply { putAll(kvs) }
                }
                line.startsWith("#EXTHTTP:") -> {
                    // formato "#EXTHTTP: {"User-Agent": "..."}" — somente pares simples; JSON malformado vira aviso.
                    val json = line.removePrefix("#EXTHTTP:").trim()
                    val parsed = parseSimpleJsonStringMap(json)
                    if (parsed.isEmpty()) warnings += "Linha EXTHTTP ignorada (JSON não suportado)."
                    else pendingHeaders = (pendingHeaders ?: mutableMapOf()).apply { putAll(parsed) }
                }
                line.startsWith("#KODIPROP") || line.startsWith("#EXTIMG") || line.startsWith("#EXTBIN") -> Unit
                line.startsWith("#") -> Unit
                else -> {
                    val url = line
                    if (!isHttpStream(url)) {
                        warnings += "URL ignorada (esquema não suportado): ${url.take(60)}"
                        pending = null
                        continue
                    }
                    val inf = pending
                    val name = inf?.title?.takeIf(String::isNotBlank) ?: url.substringAfterLast('/').substringBeforeLast('.')
                    channels += ParsedChannel(
                        name = name.trim(),
                        groupName = (inf?.group?.takeIf(String::isNotBlank) ?: pendingGroup ?: "Sem categoria").trim(),
                        logoUrl = inf?.logo?.takeIf(String::isNotBlank),
                        tvgId = inf?.tvgId?.takeIf(String::isNotBlank),
                        streamUrl = url,
                        number = inf?.number ?: pendingNumber,
                        headers = pendingHeaders?.toMap().orEmpty(),
                    )
                    pending = null
                    pendingGroup = null
                    pendingHeaders = null
                    pendingNumber = null
                }
            }
        }
        if (pending != null) warnings += "EXTINF sem URL correspondente no fim da lista."
        return Result(channels, warnings)
    }

    private data class PendingInf(
        val title: String,
        val group: String?,
        val logo: String?,
        val tvgId: String?,
        val number: Int?,
    )

    private fun parseExtInf(line: String): PendingInf? {
        val body = line.substringAfter(':', "")
        val attrs = LinkedHashMap<String, String>()
        QUOTED_ATTR.findAll(body).forEach { attrs[it.groupValues[1].lowercase()] = decode(it.groupValues[2]) }
        // nome = depois da última vírgula fora das aspas
        val title = extractTitle(body)
        val number = attrs["tvg-chno"]?.toIntOrNull()
            ?: attrs["tvg-order"]?.toIntOrNull()
            ?: attrs["channel-id"]?.toIntOrNull()
        return PendingInf(
            title = title,
            group = attrs["group-title"],
            logo = attrs["tvg-logo"],
            tvgId = attrs["tvg-id"] ?: attrs["radio-frequency"],
            number = number,
        )
    }

    /** Título após a última vírgula que não esteja dentro de aspas. */
    private fun extractTitle(body: String): String {
        var inQuotes = false
        var lastComma = -1
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) lastComma = i
            }
        }
        return if (lastComma >= 0) body.substring(lastComma + 1) else body
    }

    private fun parseOption(text: String): List<Pair<String, String>> {
        val trimmed = text.trim()
        val eq = trimmed.indexOf('=')
        if (eq <= 0) return emptyList()
        val key = trimmed.substring(0, eq).trim().lowercase()
        val value = trimmed.substring(eq + 1).trim()
        if (key.isBlank() || value.isBlank()) return emptyList()
        return when (key) {
            "http-user-agent" -> listOf("User-Agent" to value)
            "http-referrer" -> listOf("Referer" to value)
            "http-headerfields" -> value.split(',', ';').mapNotNull { part ->
                val sep = part.indexOf(':').takeIf { it > 0 } ?: part.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                part.substring(0, sep).trim().takeIf(String::isNotBlank)?.let { it to part.substring(sep + 1).trim() }
            }
            else -> emptyList()
        }
    }

    private fun parseSimpleJsonStringMap(json: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val matcher = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
        matcher.findAll(json).forEach { result[it.groupValues[1]] = decode(it.groupValues[2]) }
        return result
    }

    private fun isHttpStream(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true) ||
            url.startsWith("udp://", true) || url.startsWith("rtp://", true) ||
            url.startsWith("rtmp://", true) || url.startsWith("rtmps://", true) ||
            url.startsWith("rtsp://", true)

    private fun decode(value: String): String = value
        .replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
