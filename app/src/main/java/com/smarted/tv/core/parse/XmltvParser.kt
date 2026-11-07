package com.smarted.tv.core.parse

import com.smarted.tv.core.model.EpgChannelRef
import com.smarted.tv.core.model.Programme
import com.smarted.tv.core.time.XmlTime
import java.io.BufferedReader
import java.io.Reader

/**
 * Leitor XMLTV em streaming (sem DOM): mantém a pilha de elementos, captura
 * `channel` (id/display-name/icon) e `programme` (start/stop/channel + título/descrição/categoria).
 * Filtra pela janela [windowStartMs, windowEndMs] para nunca carregar dias inteiros em memória.
 * Tolerante: entidades, CDATA, aspas simples, aninhamento imperfeito.
 */
object XmltvParser {

    data class Result(
        val channels: Map<String, EpgChannelRef>,
        val programmes: List<Programme>,
        val warnings: List<String>,
        val truncated: Boolean,
    )

    fun parse(source: Reader, windowStartMs: Long? = null, windowEndMs: Long? = null, maxProgrammes: Int = 250_000): Result {
        val reader = if (source is BufferedReader) source else source.buffered()
        val channels = LinkedHashMap<String, MutableList<String>>()
        val channelLogos = HashMap<String, String>()
        val programmes = ArrayList<Programme>()
        val warnings = ArrayList<String>()
        val stack = ArrayDeque<String>()
        val lexer = Lexer(reader)
        var current: MutableProgramme? = null
        var currentChannelId: String? = null
        var truncated = false

        while (true) {
            when (val token = lexer.next() ?: break) {
                is Start -> {
                    stack.addLast(token.name)
                    when {
                        token.name == "programme" && !token.selfClosed -> current = MutableProgramme(token.attrs)
                        token.name == "channel" && !token.selfClosed -> {
                            val id = token.attrs["id"]?.let(::decodeXmlEntities)?.trim()?.takeIf(String::isNotBlank)
                            if (id != null) {
                                channels.getOrPut(id) { mutableListOf() }
                                currentChannelId = id
                            }
                        }
                        token.name == "icon" -> {
                            val id = currentChannelId ?: stack.getOrNull(stack.size - 2)
                            val src = token.attrs["src"]?.let(::decodeXmlEntities)
                            if (id != null && !src.isNullOrBlank()) channelLogos[id] = src
                        }
                    }
                }
                is End -> {
                    if (token.name == "programme") {
                        current?.let { prog ->
                            val start = prog.startMs
                            val channel = prog.channel
                            val title = prog.titleText()
                            if (start != null && channel != null && title.isNotBlank()) {
                                val stop = prog.stopMs?.takeIf { it > start } ?: start
                                if ((windowStartMs == null || stop >= windowStartMs) && (windowEndMs == null || start <= windowEndMs)) {
                                    if (programmes.size < maxProgrammes) {
                                        programmes += Programme(
                                            epgChannel = channel,
                                            startMs = start,
                                            stopMs = stop,
                                            title = title,
                                            description = prog.descText(),
                                            category = prog.categoryText(),
                                        )
                                    } else {
                                        truncated = true
                                    }
                                }
                            }
                        }
                        current = null
                    }
                    if (token.name == "channel") currentChannelId = null
                    while (stack.isNotEmpty() && stack.removeLast() != token.name) { /* tolera aninhamento imperfeito */ }
                }
                is Text -> {
                    if (stack.isEmpty()) continue
                    when (stack.last()) {
                        "title" -> current?.appendTitle(token.value)
                        "desc", "long-desc" -> current?.appendDesc(token.value)
                        "category" -> current?.appendCategory(token.value)
                        "display-name" -> currentChannelId?.let { channels.getOrPut(it) { mutableListOf() } += token.value.trim() }
                        else -> Unit
                    }
                }
            }
        }

        val refs = channels.entries.associate { (id, names) ->
            val cleaned = names.map(String::trim).filter(String::isNotBlank).distinct()
            id to EpgChannelRef(id, cleaned, channelLogos[id])
        }
        if (programmes.isEmpty() && refs.isEmpty()) warnings += "O XMLTV não contém <programme>/<channel> reconhecíveis."
        return Result(refs, programmes, warnings, truncated)
    }

    private class MutableProgramme(attrs: Map<String, String>) {
        val channel: String? = attrs["channel"]?.let(::decodeXmlEntities)?.trim()?.takeIf(String::isNotBlank)
        val startMs: Long? = attrs["start"]?.let(::decodeXmlEntities)?.let(XmlTime::parseXmltv)
        val stopMs: Long? = attrs["stop"]?.let(::decodeXmlEntities)?.let(XmlTime::parseXmltv)
        private val titles = ArrayList<String>()
        private val descs = ArrayList<String>()
        private val categories = ArrayList<String>()
        fun appendTitle(value: String) { if (value.isNotBlank()) titles += value.trim() }
        fun appendDesc(value: String) { if (value.isNotBlank()) descs += value.trim() }
        fun appendCategory(value: String) { if (value.isNotBlank()) categories += value.trim() }
        fun titleText(): String = titles.joinToString(" ")
        fun descText(): String = descs.joinToString(" ").trim()
        fun categoryText(): String = categories.joinToString(", ").trim()
    }

    private sealed interface Token
    private class Start(val name: String, val attrs: Map<String, String>, val selfClosed: Boolean) : Token
    private class End(val name: String) : Token
    private class Text(val value: String) : Token

    /** Tokenizador incremental com pushback de 1 slot: texto antes de uma tag é entregue primeiro. */
    private class Lexer(private val reader: BufferedReader) {
        private var queued: Token? = null

        fun next(): Token? {
            (queued as Token?)?.let { queued = null; return it }
            val buf = StringBuilder(64)
            var c = reader.read()
            while (c != -1 && c.toChar() != '<') {
                buf.append(c.toChar())
                c = reader.read()
            }
            if (c == -1) return if (buf.isEmpty()) null else Text(decodeXmlEntities(buf.toString()))
            val tag = StringBuilder(128)
            var quoteChar = -1
            while (true) {
                c = reader.read()
                if (c == -1) break
                val ch = c.toChar()
                tag.append(ch)
                if (quoteChar != -1) { if (ch.code == quoteChar) quoteChar = -1; continue }
                if (ch == '"' || ch == '\'') { quoteChar = ch.code; continue }
                if (ch == '>') break
            }
            val tagToken = classify(tag.toString())
            if (buf.isNotEmpty()) {
                queued = tagToken
                return Text(decodeXmlEntities(buf.toString()))
            }
            return tagToken
        }

        private fun classify(raw: String): Token? = when {
            raw.startsWith("<![CDATA[") -> Text(decodeXmlEntities(raw.removePrefix("<![CDATA[").removeSuffix("]>")))
            raw.startsWith("<?") || raw.startsWith("<!DOCTYPE") || raw.startsWith("<!--") -> null
            else -> {
                val body = raw.removePrefix("<").removeSuffix(">").trim()
                val selfClosed = body.endsWith("/")
                val inner = body.removeSuffix("/").trim()
                when {
                    inner.startsWith("/") -> End(inner.substring(1).trim().substringBefore(' ').lowercase())
                    else -> {
                        val name = inner.substringBefore(' ').trim().lowercase()
                        if (name.isEmpty()) null else Start(name, parseXmlAttrs(inner.substringAfter(' ', "")), selfClosed)
                    }
                }
            }
        }
    }
}

private fun parseXmlAttrs(source: String): Map<String, String> {
    val attrs = LinkedHashMap<String, String>()
    var i = 0
    while (i < source.length) {
        while (i < source.length && source[i].isWhitespace()) i++
        val nameStart = i
        while (i < source.length && source[i] != '=') i++
        if (i >= source.length) break
        val name = source.substring(nameStart, i).trim().lowercase()
        i++ // '='
        while (i < source.length && source[i].isWhitespace()) i++
        if (i >= source.length) break
        val quote = source[i]
        val value: String
        if (quote == '"' || quote == '\'') {
            val end = source.indexOf(quote, i + 1).takeIf { it > 0 } ?: source.length
            value = source.substring(i + 1, end)
            i = if (end >= source.length) source.length else end + 1
        } else {
            val end = source.indexOf(' ', i).takeIf { it > 0 } ?: source.length
            value = source.substring(i, end)
            i = end
        }
        if (name.isNotEmpty()) attrs[name] = value
    }
    return attrs
}

fun decodeXmlEntities(value: String): String {
    if (!value.contains('&')) return value
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val ch = value[i]
        if (ch != '&') { sb.append(ch); i++; continue }
        val end = value.indexOf(';', i + 1)
        if (end < 0 || end - i > 12) { sb.append(ch); i++; continue }
        val entity = value.substring(i + 1, end)
        val decoded = decodeXmlEntity(entity)
        if (decoded == null) { sb.append(ch); i++; continue }
        sb.append(decoded)
        i = end + 1
    }
    return sb.toString()
}

private fun decodeXmlEntity(entity: String): String? = when {
    entity == "amp" -> "&"
    entity == "lt" -> "<"
    entity == "gt" -> ">"
    entity == "quot" -> "\""
    entity == "apos" -> "'"
    entity == "nbsp" -> " "
    entity.startsWith("#x") || entity.startsWith("#X") ->
        entity.drop(2).toIntOrNull(radix = 16)?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) }
    entity.startsWith("#") ->
        entity.drop(1).toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) }
    else -> null
}
