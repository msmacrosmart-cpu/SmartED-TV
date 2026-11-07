package com.smarted.tv.core.addons

import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.ContentClassifier
import com.smarted.tv.core.util.TextUtils

/**
 * Item dentro de uma pasta de repositório — o equivalente a uma entrada do
 * navegador do Kodi (um addon de conteúdo, com pôster e metadados).
 *
 * [streamUrl] só é preenchido quando o addon declara um link de mídia direto
 * (MP4/MKV para filmes e séries, HLS/TS para canais). Addons escritos em Python
 * não são executáveis no Android: eles aparecem no navegador como metadados e
 * quem entrega os streams é o addon embutido do app.
 */
data class BrowserEntry(
    val id: String,
    val title: String,
    val summary: String,
    val kind: MediaKind,
    val author: String = "",
    val version: String = "",
    val poster: String? = null,
    val fanart: String? = null,
    val streamUrl: String? = null,
) {
    /** Só é reproduzível se o link respeitar o contrato da pasta. */
    val playable: Boolean get() = AddonBrowser.accepts(kind, streamUrl)

    /** "Autor · v1.2.3" — a linha de metadados dos cards. */
    val metaLine: String
        get() = buildList {
            if (author.isNotBlank()) add(author)
            if (version.isNotBlank() && version != "—") add("v$version")
        }.joinToString(" · ")
}

/** Uma das pastas principais do repositório (menu do Kodi). */
data class RepositoryFolder(
    val kind: MediaKind,
    val title: String,
    val description: String,
    val entries: List<BrowserEntry>,
) {
    val count: Int get() = entries.size
    val playableCount: Int get() = entries.count { it.playable }
}

/**
 * Estrutura de navegação de um repositório no molde do Kodi.
 *
 * Um repositório Kodi é um diretório com `addons.xml`; o usuário entra no
 * addon de vídeo e navega por TV ao Vivo, Filmes e Séries. Como os addons são
 * Python, o SmartED TV lê o índice, classifica cada addon pelo nome/resumo e
 * monta essas três pastas — que são as mesmas três abas do app.
 *
 * Tudo aqui é puro: os testes de `tools/verify-core.sh` exercitam a
 * classificação e a barreira de formato sem Android.
 */
object AddonBrowser {

    /** Ordem fixa das pastas/abas. */
    val KINDS: List<MediaKind> = listOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES)

    private val LIVE_WORDS = listOf(
        "live", "ao vivo", "aovivo", "canal", "canais", "channel", "channels",
        "epg", "guia", "linear", "aberto",
    )
    private val MOVIE_WORDS = listOf(
        "filme", "filmes", "movie", "movies", "cinema", "vod", "lancamento",
    )
    private val SERIES_WORDS = listOf(
        "serie", "series", "tv show", "tvshow", "temporada", "novela", "dorama",
        "anime", "desenho", "episodio",
    )

    fun titleFor(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "TV ao Vivo"
        MediaKind.MOVIE -> "Filmes"
        MediaKind.SERIES -> "Séries"
        else -> "Conteúdo"
    }

    fun descriptionFor(kind: MediaKind): String = when (kind) {
        MediaKind.LIVE -> "Canais ao vivo, com EPG"
        MediaKind.MOVIE -> "Filmes sob demanda (MP4, MKV)"
        MediaKind.SERIES -> "Séries por temporada e episódio"
        else -> "Conteúdo do repositório"
    }

    /**
     * Em qual(is) pasta(s) um addon entra.
     *
     * A classificação usa id + nome + resumo normalizados (sem acento), igual ao
     * cruzamento de EPG. Sem palavra-chave reconhecida o addon é tratado como
     * multi-conteúdo e aparece nas três pastas — é o comportamento do Kodi para
     * addons genéricos.
     */
    fun classify(id: String, name: String, summary: String): Set<MediaKind> {
        val text = TextUtils.normalize("$id $name $summary")
        val out = LinkedHashSet<MediaKind>()
        if (LIVE_WORDS.any { text.contains(it) }) out.add(MediaKind.LIVE)
        if (MOVIE_WORDS.any { text.contains(it) }) out.add(MediaKind.MOVIE)
        if (SERIES_WORDS.any { text.contains(it) }) out.add(MediaKind.SERIES)
        if (out.isEmpty()) out.addAll(KINDS)
        return out
    }

    /**
     * Barreira de formato por pasta: Canais aceitam stream ao vivo; Filmes e
     * Séries aceitam **somente arquivo de vídeo estático** (MP4, MKV…), nunca
     * link de canal. É o mesmo contrato do [ContentClassifier] aplicado um nível
     * acima, no navegador do repositório.
     */
    fun accepts(kind: MediaKind, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.trim()
        if (!clean.startsWith("http")) return false
        return when (kind) {
            MediaKind.LIVE -> ContentClassifier.isStream(clean)
            // Filmes/Séries aceitam arquivo estático **ou** o link de um
            // daemon local que já resolveu o arquivo (Elementum serve o vídeo
            // em `127.0.0.1:65220/play`). O magnet cru continua recusado.
            MediaKind.MOVIE, MediaKind.SERIES ->
                ContentClassifier.isStaticFile(clean) || isLocalPlayerLink(clean)
            else -> false
        }
    }

    /**
     * Link de reprodução de um serviço local de mídia (Elementum e afins).
     *
     * Só loopback conta: é o daemon que entrega o arquivo, não um site externo
     * disfarçado de `.mp4`. Assim a separação Canais × Filmes continua de pé.
     */
    fun isLocalPlayerLink(url: String): Boolean {
        val clean = url.trim().lowercase()
        val loopback = clean.startsWith("http://127.0.0.1:") || clean.startsWith("http://localhost:")
        return loopback && clean.contains("/play")
    }

    /** Primeiro link de mídia direto entre os candidatos (ou null). */
    fun mediaUrl(candidates: List<String?>): String? = candidates
        .mapNotNull { it?.trim() }
        .firstOrNull { accepts(MediaKind.LIVE, it) || accepts(MediaKind.MOVIE, it) }

    /** Monta as três pastas, sempre nesta ordem, com os itens já filtrados. */
    fun folders(entries: List<BrowserEntry>): List<RepositoryFolder> = KINDS.map { kind ->
        RepositoryFolder(
            kind = kind,
            title = titleFor(kind),
            description = descriptionFor(kind),
            entries = entries
                .filter { it.kind == kind }
                .sortedBy { TextUtils.normalize(it.title) },
        )
    }

    /** Resumo para a tela: "TV ao Vivo 12 · Filmes 40 · Séries 18". */
    fun summary(folders: List<RepositoryFolder>): String =
        folders.joinToString(" · ") { "${it.title} ${it.count}" }
}
