package com.smarted.tv.core.addons

import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.ContentClassifier
import com.smarted.tv.core.util.TextUtils

/**
 * Addon **Elementum** — torrentes dentro do app.
 *
 * O Elementum é um addon do repositório Vikings (`plugin.video.elementum`).
 * Como todo addon de repositório, ele é um plugin Python e não executa no
 * Android. A diferença é que o Elementum tem um **serviço local**: o daemon
 * (app Elementum para Android / `elementumd`) abre um servidor HTTP em
 * `127.0.0.1:65220` que busca torrentes e entrega o vídeo por streaming.
 *
 * O SmartED TV fala direto com esse daemon:
 *
 * | Uso | Endpoint |
 * | --- | --- |
 * | está no ar? | `GET /status` |
 * | buscar | `GET /search?q=<termo>` |
 * | abrir o vídeo | `GET /play?torrent=<magnet>` (o daemon serve o arquivo) |
 *
 * Tudo aqui é **puro** (sem OkHttp/Android): os testes de
 * `tools/verify-core.sh` exercitam a detecção do addon, a leitura do JSON do
 * daemon e a montagem do link de reprodução. Quem faz as chamadas é
 * `core/net/ElementumClient.kt`.
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

    /** Busca por termo; o texto vai escapado porque o termo vem do usuário. */
    fun searchUrl(query: String, port: Int = DEFAULT_PORT): String =
        "${baseUrl(port)}/search?q=${encodeQuery(query)}"

    /**
     * Link de reprodução entregue ao player.
     *
     * O daemon resolve o magnet e serve o arquivo por HTTP; o player só precisa
     * tocar essa URL. `title` ajuda o daemon a escolher o arquivo dentro do
     * torrent (pacotes com vários episódios).
     */
    fun playUrl(item: Item, port: Int = DEFAULT_PORT): String {
        val torrent = item.torrent.ifBlank { return "" }
        val title = if (item.title.isBlank()) "" else "&title=${encodeQuery(item.title)}"
        return "${baseUrl(port)}/play?torrent=${encodeQuery(torrent)}$title"
    }

    // ---------------------------------------------------------------------
    // modelo
    // ---------------------------------------------------------------------

    /** Um resultado de busca do daemon. */
    data class Item(
        val title: String,
        val torrent: String = "",
        val sizeBytes: Long = 0L,
        val seeds: Int = 0,
        val peers: Int = 0,
        val quality: String = "",
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
    // leitura do JSON do daemon
    // ---------------------------------------------------------------------

    /**
     * Lê o `/status`.
     *
     * O daemon responde com um objeto simples (`{"status":"ok","version":"…"}`)
     * ou, em versões mais novas, com um objeto aninhado. Qualquer resposta HTTP
     * já indica que o serviço está no ar — por isso a leitura é tolerante.
     */
    fun parseStatus(json: String?, port: Int = DEFAULT_PORT): Status {
        if (json.isNullOrBlank()) {
            return Status(online = false, port = port, message = "Sem resposta do Elementum")
        }
        val version = field(json, "version", "elementum_version", "daemon_version").orEmpty()
        return Status(online = true, port = port, version = version, message = "")
    }

    /**
     * Lê a lista de resultados.
     *
     * O daemon devolve um array de objetos (`[{"title":…,"torrent":…}]`) ou um
     * objeto com a lista dentro (`{"items":[…]}`). Cada objeto é lido campo a
     * campo, aceitando os nomes que as versões do Elementum usam.
     */
    fun parseItems(json: String?): List<Item> {
        if (json.isNullOrBlank()) return emptyList()
        return jsonObjects(json).mapNotNull { obj ->
            val title = field(obj, "title", "name", "label", "torrent_name").orEmpty().trim()
            val torrent = field(obj, "torrent", "magnet", "magnet_uri", "url", "link").orEmpty().trim()
            if (title.isBlank() && torrent.isBlank()) return@mapNotNull null
            Item(
                title = title.ifBlank { torrentName(torrent) },
                torrent = torrent,
                sizeBytes = numberField(obj, "size", "size_bytes", "total_size", "length"),
                seeds = numberField(obj, "seeds", "seeders", "seed").toInt(),
                peers = numberField(obj, "peers", "leechers").toInt(),
                quality = qualityOf(title),
            )
        }
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
        val rounded = (value * 10).toLong() / 10.0
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
     * O `streamUrl` é o link do daemon: assim o item entra na pasta **Filmes**
     * ou **Séries** já reproduzível, e a barreira do [AddonBrowser] continua
     * valendo (um magnet não é canal ao vivo).
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

    /** Magnet do torrent a partir do info hash (quando o daemon só devolve o hash). */
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

    /** Objetos `{…}` de primeiro nível (ou os de dentro de um array). */
    private fun jsonObjects(json: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (i in json.indices) {
            val c = json[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                c == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return out
    }

    /** Primeiro valor de texto entre os campos pedidos. */
    private fun field(json: String, vararg names: String): String? {
        for (name in names) {
            val value = rawValue(json, name) ?: continue
            return value.trim().trim('"')
        }
        return null
    }

    /** Primeiro valor numérico entre os campos pedidos. */
    private fun numberField(json: String, vararg names: String): Long {
        for (name in names) {
            val value = rawValue(json, name) ?: continue
            val number = value.trim().trim('"').filter { it.isDigit() }
            number.toLongOrNull()?.let { return it }
        }
        return 0L
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

    /** Nome do torrent a partir do magnet (`dn=`). */
    private fun torrentName(torrent: String): String {
        val marker = torrent.indexOf("dn=")
        if (marker < 0) return ""
        val rest = torrent.substring(marker + 3)
        return rest.substringBefore('&').replace('+', ' ').trim()
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

    private const val HEX = "0123456789ABCDEF"
}
