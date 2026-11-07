package com.smarted.tv.core.sources

import com.smarted.tv.core.model.Tab

/**
 * Regras de roteamento de stream compartilhadas entre importadores e testes.
 * Mantido no núcleo puro para que a UI nunca adote URL que o player não pode abrir.
 */
object StreamRules {

    val LIVE_SUFFIXES = listOf(".m3u8", ".ts", ".mpg", ".mpeg", ".mp4", ".mjpeg", "/live", "videoid=")
    val VIDEO_SUFFIXES = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".mpd", ".ts", ".m3u8", ".webm")

    fun isProbablyPlayable(url: String): Boolean {
        val clean = url.substringBefore('#').lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://") ||
            clean.startsWith("udp://") || clean.startsWith("rtp://") ||
            clean.startsWith("rtmp://") || clean.startsWith("rtmps://") || clean.startsWith("rtsp://")
    }

    fun looksLikeHls(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)

    fun looksLikeDash(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".mpd", ignoreCase = true)

    fun mimeFor(url: String): String? = when {
        looksLikeHls(url) -> "application/vnd.apple.mpegurl"
        looksLikeDash(url) -> "application/dash+xml"
        else -> null
    }

    /** Extensão do contêiner para Xtream montar URLs de filme/episódio. */
    fun extensionFromName(name: String, fallback: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "ts", "m2ts", "flv", "mpg", "mpeg" -> ext
            else -> fallback
        }
    }

    /** Nome de exibição a partir de um arquivo de playlist VOD do Xtream (sem extensões ruidosas). */
    private val QUALITY_BRACKET = Regex("""(?i)\s*[\[(](?:4k|uhd|fhd|hd|sd|1080p|720p|480p|h265|hevc|x264|x265|h264)[\])]""")

    fun cleanVodTitle(raw: String, kind: Tab): String {
        var name = raw.trim()
        if (kind == Tab.SERIES) return name
        name = name.replace(Regex("""(?i)\s*[\[(](19|20)\d{2}[\])]"""), "").trim()
        name = name.replace(QUALITY_BRACKET, "").trim()
        name = name.replace(Regex("""(?i)\s*\b(4k|fhd|hd|sd|uhd|1080p|720p|480p)\b\s*$"""), "").trim()
        return name.ifBlank { raw.trim() }
    }
}
