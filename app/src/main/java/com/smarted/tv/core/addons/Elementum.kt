package com.smarted.tv.core.addons

import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.ContentClassifier
import com.smarted.tv.core.util.TextUtils

/**
 * Addon **Elementum** — torrentes dentro do app.
 *
 * O Elementum está no repositório Vikings (`plugin.video.elementum`). É um
 * addon Python, então não roda no Android; quem faz o trabalho é o **daemon**
 * (`elementumd`), um binário nativo que abre HTTP em `127.0.0.1:65220`.
 *
 * O daemon fala **Kodi**: as respostas de navegação são uma *view* com
 * `items[]`, cada item com `label`, `path` e `is_playable` — exatamente o que o
 * plugin Python consome. O app reaproveita esse contrato:
 *
 * | Uso | Endpoint |
 * | --- | --- |
 * | está no ar? | `GET /status` |
 * | menu inicial | `GET /?list=1` |
 * | filmes populares | `GET /movies/popular?list=1` |
 * | séries populares | `GET /shows/popular?list=1` |
 * | buscar | `GET /search?list=1&q=<termo>` |
 * | reproduzir | `GET /play?uri=<magnet>` |
 *
 * O `list=1` é o que faz o daemon **devolver a lista** de torrentes em vez de
 * abrir o diálogo de seleção do Kodi (ver `wantsLinksList` no daemon). Cada
 * item já vem com `path` apontando para `/play?uri=…`, então a reprodução é
 * montada a partir desse `path`.
 *
 * Tudo aqui é **puro** (sem OkHttp/Android): `tools/verify-core.sh` exercita a
 * detecção do addon, a leitura da view, o `play` e o descarte de magnet. Quem
 * faz as chamadas é `core/net/ElementumClient.kt`.
 *
 * Regra de acervo: Elementum é VOD — filme ou série, **nunca canal ao vivo**.
 */
object Elementum {

    /** Identificador do addon no repositório Vikings. */
    const val ADDON_ID = "plugin.video.elementum"

    /** Nome exibido quando o addon é criado localmente. */
    const val ADDON_NAME = "Elementum"

    /** Porta padrão do daemon local. */
    const val DEFAULT_PORT = 65220

    /** O daemon escuta só em loopback — o app é o cliente. */
    const val HOST = "127.0.0.1"

    /** Caminhos do menu do daemon usados pela tela. */
    const val PATH_MOVIES = "/movies/popular"
    const val PATH_SHOWS = "/shows/popular"

    /** Aceita variações do id/nome que o repositório publica. */
    private val ID_HINTS = listOf("elementum", "plugin.video.elementum", "script.elementum")
    private val NAME_HINTS = listOf("elementum")

    // ---------------------------------------------------------------------
    // detecção
    // ---------------------------------------------------------------------

    /** O addon instalado é o Elementum? */
    fun isElementum(id: String?, name: String? = null): Boolean {
        val idText = TextUtils.normalize(id.orEmpty())
        val nameText = TextUtils.normalize(name.orEmpty())
        if (ID_HINTS.any { idText == TextUtils.normalize(it) || idText.contains(TextUtils.normalize(it)) }) return true
        return NAME_HINTS.any { nameText.contains(it) }
    }

    // ---------------------------------------------------------------------
    // endpoints
    // ---------------------------------------------------------------------

    fun baseUrl(port: Int = DEFAULT_PORT): String = "http://$HOST:${if (port in 1..65535) port else DEFAULT_PORT}"

    fun statusUrl(port: Int = DEFAULT_PORT): String = "${baseUrl(port)}/status"

    /**
     * Busca por termo.
     *
     * `list=1` devolve a lista de torrentes no corpo em vez de pedir ao Kodi
     * que abra o seletor. O texto vai escapado porque vem do usuário.
     */
    fun searchUrl(query: String, port: Int = DEFAULT_PORT): String =
        "${baseUrl(port)}/search?list=1&q=${encodeQuery(query)}"

    /** Navegação de um caminho do menu (`/movies/`, `/movies/popular`, `/`…). */
    fun browseUrl(path: String, port: Int = DEFAULT_PORT): String {
        val clean = if (path.startsWith("/")) path else "/$path"
        val separator = if (clean.contains('?')) "&" else "?"
        return "${baseUrl(port)}$clean${separator}list=1"
    }

    /**
     * Link de reprodução entregue ao player.
     *
     * O daemon resolve o magnet, baixa o pedaço necessário e responde com um
     * `302` para `/files/…`, servindo o vídeo por HTTP. `uri` é o magnet
     * (ou o `path` cru do item, quando ele já está no formato do daemon).
     */
    fun playUrl(item: Item, port: Int = DEFAULT_PORT): String {
        val uri = item.torrent.ifBlank { return "" }
        return "${baseUrl(port)}/play?uri=${encodeQuery(uri)}"
    }

    /** Extrai um parâmetro de uma URL do daemon, já decodificado. */
    fun parameter(url: String, name: String): String {
        val marker = "$name="
        val queryAt = url.indexOf('?')
        if (queryAt >= 0) {
            for (part in url.substring(queryAt + 1).split('&')) {
                if (part.startsWith(marker)) return decodeQuery(part.substring(marker.length))
            }
            return ""
        }
        // path pode vir sozinho, ex.: "magnet:?xt=…"
        val at = url.indexOf(marker)
        return if (at >= 0) decodeQuery(url.substring(at + marker.length).substringBefore('&')) else ""
    }

    // ---------------------------------------------------------------------
    // modelo
    // ---------------------------------------------------------------------

    /** Um nó devolvido pelo daemon: menu (pasta) ou torrente (reproduzível). */
    data class Node(
        val label: String,
        val path: String = "",
        val playable: Boolean = false,
        val label2: String = "",
        val icon: String = "",
        val tagline: String = "",
        val code: String = "",
    )

    /** Um resultado de busca/navegação já resolvido para o player. */
    data class Item(
        val title: String,
        val torrent: String = "",
        val sizeBytes: Long = 0L,
        val seeds: Int = 0,
        val peers: Int = 0,
        val quality: String = "",
        val provider: String = "",
    ) {
        /** Filme ou série — sempre VOD. */
        val kind: MediaKind
            get() = if (ContentClassifier.hasEpisodeMarker(title) || looksLikeSeries(title)) {
                MediaKind.SERIES
            } else {
                MediaKind.MOVIE
            }

        /** "1080p · 1,4 GB · 320 seeds" — a linha de apoio do card. */
        val metaLine: String
            get() = buildList {
                if (quality.isNotBlank()) add(quality)
                if (sizeBytes > 0) add(humanSize(sizeBytes))
                if (seeds > 0) add("$seeds seeds")
                if (provider.isNotBlank()) add(provider)
            }.joinToString(" · ")
    }

    /** Estado do daemon: ligado/desligado e, quando ligado, a versão. */
    data class Status(
        val online: Boolean,
        val port: Int = DEFAULT_PORT,
        val version: String = "",
        val message: String = "",
    ) {
        val label: String get() = if (online) "Elementum ligado" else "Elementum desligado"
    }

    /** Temporada/episódio, "completa", "temporadas" — indício de série. */
    private fun looksLikeSeries(title: String): Boolean {
        val t = TextUtils.normalize(title)
        return SERIES_WORDS.any { t.contains(it) }
    }

    private val SERIES_WORDS = listOf(
        "temporada", "season", "serie", "series", "completa", "completo",
        "colecao", "episodios", "novela", "anime", "dorama",
    )

    // ---------------------------------------------------------------------
    // leitura das respostas
    // ---------------------------------------------------------------------

    /**
     * Lê o `/status`.
     *
     * Qualquer resposta HTTP já indica que o serviço está no ar; a versão é
     * aproveitada quando aparece no corpo (`version`/`elementum_version`).
     */
    fun parseStatus(json: String?, port: Int = DEFAULT_PORT): Status {
        if (json.isNullOrBlank()) {
            return Status(online = false, port = port, message = "Sem resposta do Elementum")
        }
        val version = field(json, "version", "elementum_version", "daemon_version").orEmpty()
        return Status(online = true, port = port, version = version, message = "")
    }

    /**
     * Lê uma *view* do daemon (`{"content_type":"…","items":[…]}`).
     *
     * Aceita também um array cru de objetos, que é o formato de versões
     * anteriores do daemon.
     */
    fun parseView(json: String?): List<Node> {
        if (json.isNullOrBlank()) return emptyList()
        return jsonObjects(json).mapNotNull { obj ->
            val label = field(obj, "label", "title"); 
            val path = field(obj, "path", "url", "link").orEmpty()
            if (label.isNullOrBlank() && path.isBlank()) return@mapNotNull null
            Node(
                label = stripKodiTags(label.orEmpty()).trim(),
                path = path.trim(),
                playable = rawValue(obj, "is_playable")?.trim() == "true",
                label2 = stripKodiTags(field(obj, "label2").orEmpty()).trim(),
                icon = field(obj, "icon", "thumbnail").orEmpty(),
                tagline = stripKodiTags(field(obj, "tagline").orEmpty()),
                code = field(obj, "code").orEmpty(),
            )
        }
    }

    /**
     * Converte a view em resultados reproduzíveis.
     *
     * Só entram itens com `path` de reprodução: nós de menu (`is_playable`
     * falso) são ignorados — o app já oferece os destinos que interessam
     * (filmes, séries e busca).
     */
    fun parseItems(json: String?): List<Item> =
        parseView(json)
            .filter { it.playable }
            .mapNotNull { node ->
                val magnet = playTarget(node.path)
                if (magnet.isBlank()) return@mapNotNull null
                val meta = parseTagline(node.tagline)
                val title = node.label.ifBlank { parameter(node.path, "query") }.trim()
                if (title.isBlank()) return@mapNotNull null
                Item(
                    title = title,
                    torrent = magnet,
                    sizeBytes = meta.sizeBytes,
                    seeds = meta.seeds,
                    peers = meta.peers,
                    quality = meta.quality.ifBlank { qualityOf(node.label) },
                    provider = node.label2,
                )
            }

    /** Magnet/URI de um `path` do daemon (`/play?uri=…`, `/download?uri=…`). */
    fun playTarget(path: String): String {
        if (path.isBlank()) return ""
        val uri = parameter(path, "uri")
        if (uri.isNotBlank()) return uri
        // Alguns caminhos vêm com o magnet inteiro, sem parâmetro.
        if (path.startsWith("magnet:")) return path.trim()
        return ""
    }

    /** Dados de apoio do rótulo: `"320 / 45 - 1080p - 1.4 GB"`. */
    data class TagMeta(val seeds: Int = 0, val peers: Int = 0, val sizeBytes: Long = 0L, val quality: String = "")

    fun parseTagline(tagline: String?): TagMeta {
        val text = tagline.orEmpty()
        if (text.isBlank()) return TagMeta()
        val parts = text.split('-').map { it.trim() }
        var seeds = 0
        var peers = 0
        var size = 0L
        var quality = ""
        for (part in parts) {
            if (part.contains('/') && seeds == 0) {
                val numbers = part.split('/').mapNotNull { it.trim().toIntOrNull() }
                if (numbers.size >= 2) {
                    seeds = numbers[0]
                    peers = numbers[1]
                    continue
                }
            }
            val bytes = parseSize(part)
            if (bytes > 0) {
                size = bytes
                continue
            }
            val q = qualityOf(part)
            if (q.isNotBlank()) quality = q
        }
        return TagMeta(seeds = seeds, peers = peers, sizeBytes = size, quality = quality)
    }

    /** "1.4 GB" / "700,5 MB" → bytes. */
    fun parseSize(text: String): Long {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return 0L
        val match = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([KMGT]i?B)""", RegexOption.IGNORE_CASE).find(cleaned)
            ?: return 0L
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].uppercase().first()
        val factor = when (unit) {
            'K' -> 1024L
            'M' -> 1024L * 1024
            'G' -> 1024L * 1024 * 1024
            'T' -> 1024L * 1024 * 1024 * 1024
            else -> 1L
        }
        return (value * factor).toLong()
    }

    /** Resolução declarada no título ("1080p", "720p", "4K"). */
    fun qualityOf(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("2160p") || t.contains("4k") || t.contains("uhd") -> "4K"
            t.contains("1080p") || t.contains("fullhd") || t.contains("full hd") -> "1080p"
            t.contains("720p") || t.contains("hd") -> "720p"
            t.contains("480p") || t.contains("sd") -> "480p"
            else -> ""
        }
    }

    /** Tamanho legível em pt-BR ("1,4 GB"). */
    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        // Arredonda (e não trunca): 1.400.000.000 B deve virar "1,4 GB", não "1,3 GB".
        val rounded = kotlin.math.round(value * 10) / 10.0
        val text = if (rounded >= 100 || rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().replace('.', ',')
        }
        return "$text ${units[index]}"
    }

    /**
     * Converte resultados em itens de pasta.
     *
     * O `streamUrl` é o `/play` do daemon: assim o item entra em **Filmes** ou
     * **Séries** já reproduzível, e a barreira do [AddonBrowser] continua
     * valendo (magnet cru não é URL de player; `/play` de loopback é).
     */
    fun toEntries(items: List<Item>, port: Int = DEFAULT_PORT): List<BrowserEntry> =
        items.mapIndexed { index, item ->
            BrowserEntry(
                id = "$ADDON_ID:${item.torrent.hashCode()}:$index",
                title = item.title.ifBlank { "Torrente" },
                summary = item.metaLine,
                kind = item.kind,
                author = ADDON_NAME,
                streamUrl = playUrl(item, port).ifBlank { null },
            )
        }

    /** Magnet do torrent a partir do info hash (quando só o hash é conhecido). */
    fun magnet(infoHash: String, name: String = ""): String {
        val hash = infoHash.trim()
        if (hash.isBlank()) return ""
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
        ).joinToString("") { "&tr=${encodeQuery(it)}" }
        return "magnet:?xt=urn:btih:$hash&dn=${encodeQuery(name)}$trackers"
    }

    // ---------------------------------------------------------------------
    // JSON mínimo (o núcleo do app não depende de biblioteca de JSON nos testes)
    // ---------------------------------------------------------------------

    /**
     * Objetos de item de uma resposta do daemon.
     *
     * Aceita as duas formas: um array na raiz (`[{…},{…}]`) ou a *view* do
     * Kodi (`{"content_type":"…","items":[{…}]}`). Cada item pode ter objetos
     * aninhados (`info`, `art`) — eles ficam dentro do objeto devolvido, e a
     * leitura de campo continua achando as chaves de lá.
     */
    private fun jsonObjects(json: String): List<String> {
        val text = json.trim()
        if (text.isEmpty()) return emptyList()
        return when (text.first()) {
            '[' -> objectsInArray(text, 0)
            '{' -> {
                val itemsAt = indexOfKey(text, "items")
                if (itemsAt < 0) listOf(text) else objectsInArray(text, itemsAt)
            }
            else -> emptyList()
        }
    }

    /** Objetos `{…}` de primeiro nível a partir do array que abre em [arrayHint]. */
    private fun objectsInArray(json: String, arrayHint: Int): List<String> {
        val arrayStart = json.indexOf('[', arrayHint)
        if (arrayStart < 0) return emptyList()
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (i in arrayStart until json.length) {
            val c = json[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> {
                    if (depth == 1) start = i
                    depth++
                }
                c == '}' -> {
                    depth--
                    if (depth == 1 && start >= 0) {
                        out.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
                c == '[' -> depth++
                c == ']' -> {
                    depth--
                    if (depth == 0) break
                }
            }
        }
        return out
    }

    /** Posição da chave `"nome"` (seguida de `:`), ou -1. */
    private fun indexOfKey(json: String, name: String): Int {
        val needle = "\"$name\""
        var from = 0
        while (true) {
            val at = json.indexOf(needle, from)
            if (at < 0) return -1
            var i = at + needle.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i < json.length && json[i] == ':') return at
            from = at + needle.length
        }
    }

    /** Primeiro valor de texto entre os campos pedidos. */
    private fun field(json: String, vararg names: String): String? {
        for (name in names) {
            val value = rawValue(json, name) ?: continue
            return value.trim().trim('"')
        }
        return null
    }

    /** Valor cru do campo (string entre aspas, número ou booleano). */
    private fun rawValue(json: String, name: String): String? {
        val needle = "\"$name\""
        var from = 0
        while (true) {
            val keyAt = json.indexOf(needle, from)
            if (keyAt < 0) return null
            var i = keyAt + needle.length
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] != ':') {
                from = keyAt + needle.length
                continue
            }
            i++
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length) return null
            if (json[i] == '"') {
                val end = closingQuote(json, i)
                return if (end > i) json.substring(i, end + 1) else null
            }
            var end = i
            while (end < json.length && json[end] !in ",}] \n\r\t") end++
            return json.substring(i, end)
        }
    }

    private fun closingQuote(json: String, openAt: Int): Int {
        var i = openAt + 1
        while (i < json.length) {
            when {
                json[i] == '\\' -> i += 2
                json[i] == '"' -> return i
                else -> i++
            }
        }
        return -1
    }

    /** Remove marcação do Kodi (`[B]`, `[COLOR x]`, `[I]`…) dos rótulos. */
    fun stripKodiTags(text: String): String {
        if (text.indexOf('[') < 0) return text
        val builder = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '[') {
                val end = text.indexOf(']', i)
                if (end > i) {
                    i = end + 1
                    continue
                }
            }
            builder.append(text[i])
            i++
        }
        return builder.toString()
    }

    /** Percent-encoding para query string (UTF-8, sem depender do Android). */
    fun encodeQuery(text: String): String {
        val builder = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (c.isLetterOrDigit() && b < 0x80 || c in "-_.~") {
                builder.append(c)
            } else {
                builder.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return builder.toString()
    }

    /** Percent-decoding (`+` conta como espaço, igual ao `url.Query` do Go). */
    fun decodeQuery(text: String): String {
        if (text.indexOf('%') < 0 && text.indexOf('+') < 0) return text
        val bytes = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '%' && i + 2 < text.length -> {
                    val value = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (value != null) {
                        bytes.add(value.toByte())
                        i += 3
                    } else {
                        bytes.add(c.code.toByte())
                        i++
                    }
                }
                c == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                else -> {
                    bytes.add(c.code.toByte())
                    i++
                }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private const val HEX = "0123456789ABCDEF"
}
