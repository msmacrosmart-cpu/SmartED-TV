package com.smarted.tv.core.protocol

import java.net.URLEncoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AddonUrls {
    fun normalize(input: String): String {
        val value = input.trim().removePrefix("\uFEFF")
        if (value.isEmpty()) throw AddonException("Informe a URL do manifesto.")
        if (value.length > 4096) throw AddonException("URL muito longa (máximo de 4.096 caracteres).")
        val http = if (value.startsWith("stremio://", true)) "https://${value.substring(10)}" else value
        val parsed = http.toHttpUrlOrNull() ?: throw AddonException("URL inválida. Use https://servidor/manifest.json ou http://servidor/manifest.json.")
        if (parsed.scheme !in setOf("http", "https") || parsed.host.isEmpty()) {
            throw AddonException("Somente manifestos HTTP/JSON são aceitos.")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw AddonException("Não inclua usuário e senha na URL. Use um addon com token no caminho.")
        }
        val builder = parsed.newBuilder().fragment(null)
        if (!parsed.pathSegments.lastOrNull().equals("manifest.json", true)) builder.addPathSegment("manifest.json")
        return builder.build().toString()
    }

    fun resource(manifestUrl: String, resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val base = normalize(manifestUrl).toHttpUrlOrNull()!!
        val builder = base.newBuilder()
        val lastIndex = base.pathSize - 1
        builder.removePathSegment(lastIndex)
        builder.addPathSegment(resource).addPathSegment(type)
        if (extra.isNotEmpty()) {
            // Extras replace the id segment for catalog requests: /catalog/type/id/key=value.json.
            val args = extra.entries.sortedBy { it.key }.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
            builder.addPathSegment(id).addEncodedPathSegment("$args.json")
        } else {
            builder.addPathSegment("$id.json")
        }
        return builder.build().toString()
    }

    fun display(url: String): String = url.toHttpUrlOrNull()?.let { "${it.host}${it.encodedPath}" } ?: url
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%7E", "~")
}
