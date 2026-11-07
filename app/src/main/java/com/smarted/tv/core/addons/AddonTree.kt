package com.smarted.tv.core.addons

import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.util.TextUtils

/**
 * Navegação de um serviço de mídia **como no Kodi**: uma árvore de pastas,
 * várias camadas, com o usuário descendo até o item que quer.
 *
 * Cada resposta do serviço é uma lista de nós ([Node]) com:
 *  - `label` — o nome que aparece na tela ("Lançamentos", "Ação", "Aventura");
 *  - `path` — para onde o nó leva;
 *  - `playable` — `true` quando já é o vídeo (torrente, arquivo).
 *
 * O `path` é **relativo ao serviço**, no dialeto do Kodi:
 * `plugin://plugin.video.elementum/movies/genre/acao`. Por isso o caminho é
 * normalizado para `/movies/genre/acao` antes de ser pedido ao daemon. Também
 * aceita URL absoluta (`http://…`) e o `/play?uri=…` que já é reproduzível.
 *
 * Tudo aqui é puro — `tools/verify-core.sh` exercita a árvore, a navegação e o
 * que pode ser salvo na lista do usuário.
 */
object AddonTree {

    /** Prefixo dos caminhos de plugin do Kodi. */
    private const val PLUGIN_PREFIX = "plugin://"

    /** Ícone/cor por tipo de pasta, para a tela escolher o desenho. */
    enum class FolderStyle { ROOT, MOVIE, SERIES, GENRE, SEARCH, OTHER }

    /**
     * Um nó da árvore.
     *
     * [Node.playable] vem do serviço; [Node.kind] só é preenchido quando o nó já
     * é conteúdo (VOD), nunca em pasta.
     */
    data class Node(
        val label: String,
        val path: String = "",
        val playable: Boolean = false,
        val label2: String = "",
        val icon: String = "",
        val tagline: String = "",
        val code: String = "",
        /** Descrição vinda do `info.plot`. */
        val plot: String = "",
    ) {
        /** É o vídeo em si (torrente/arquivo) e não uma pasta. */
        val isItem: Boolean get() = playable

        /** É uma pasta navegável. */
        val isFolder: Boolean get() = !playable && path.isNotBlank()

        /** Caminho absoluto dentro do serviço, `plugin://` removido. */
        val route: String get() = routeOf(path)

        /** Nome para a tela: rótulo limpo de marcação do Kodi. */
        val title: String get() = Elementum.stripKodiTags(label).trim()
    }

    /**
     * Um nível da árvore: onde estamos e o que tem aqui.
     *
     * [trail] é a trilha de navegação (o caminho percorrido) — é o que a tela
     * usa para "voltar uma pasta" sem depender de estado escondido.
     */
    data class Level(
        val route: String,
        val title: String,
        val nodes: List<Node>,
        val trail: List<String> = emptyList(),
    ) {
        val folders: List<Node> get() = nodes.filter { it.isFolder }
        val items: List<Node> get() = nodes.filter { it.isItem }

        /** Pode salvar o que está aqui na lista do usuário. */
        val canSave: Boolean get() = items.isNotEmpty()

        /** Nível anterior (null na raiz). */
        val parent: String? get() = trail.dropLast(1).lastOrNull()
    }

    // ---------------------------------------------------------------------
    // caminhos
    // ---------------------------------------------------------------------

    /**
     * Converte o `path` de um nó no caminho que o serviço entende.
     *
     * `plugin://plugin.video.elementum/movies/genre/acao` → `/movies/genre/acao`.
     * URL absoluta de mídia é devolvida como está. Magnet não é navegável e
     * resulta em vazio.
     */
    fun routeOf(path: String): String {
        val clean = path.trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("magnet:")) return ""
        if (clean.startsWith(PLUGIN_PREFIX)) {
            // plugin://<id do addon><caminho>[?query]
            val rest = clean.removePrefix(PLUGIN_PREFIX)
            val slash = rest.indexOf('/')
            val route = if (slash < 0) "/" else rest.substring(slash)
            return if (route.isEmpty()) "/" else route
        }
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    /** URL absoluta de reprodução (o daemon resolve e serve o arquivo)? */
    fun isPlayRoute(route: String): Boolean {
        val clean = route.lowercase()
        return clean.startsWith("http") && clean.contains("/play")
    }

    /**
     * Rota de reprodução **dentro do serviço**: `/play?uri=…` (ou `/download`).
     *
     * É o que o daemon devolve no `path` de cada torrente. A tela e o player
     * precisam dela como URL absoluta, e é [playUrl] que faz essa montagem.
     */
    fun isServicePlayRoute(route: String): Boolean {
        val clean = route.lowercase()
        val play = clean.startsWith("/play") || clean.startsWith("/download")
        return play && clean.contains("uri=")
    }

    /**
     * URL que o player recebe para um nó.
     *
     * - `/play?uri=…` (rota do serviço) → `http://127.0.0.1:65220/play?uri=…`;
     * - `http://…` → devolvida como está;
     * - magnet → vazio (o player não toca magnet: quem resolve é o daemon);
     * - pasta → vazio.
     */
    fun playUrl(node: Node, baseUrl: String = Elementum.baseUrl()): String {
        val route = node.route
        if (route.isEmpty()) return ""
        if (route.startsWith("http")) return route
        if (isServicePlayRoute(route)) return baseUrl.trimEnd('/') + route
        return ""
    }

    /**
     * Depois de entrar numa pasta, a resposta pode ser uma única pasta (Kodi faz
     * isso para "abrir direto") — este helper diz se há só um caminho a seguir.
     */
    fun onlyFolder(nodes: List<Node>): Node? {
        val folders = nodes.filter { it.isFolder }
        return if (folders.size == 1 && nodes.none { it.isItem }) folders.first() else null
    }

    /** Título de exibição de uma pasta: limpo e capitalizado. */
    fun titleOf(node: Node): String {
        val title = node.title
        if (title.isBlank()) return "Pasta"
        return if (title.first().isLowerCase()) title.replaceFirstChar { it.uppercase() } else title
    }

    // ---------------------------------------------------------------------
    // o que a tela mostra
    // ---------------------------------------------------------------------

    /**
     * Caminho com a trilha montada a partir da rota atual.
     *
     * Serve para voltar: `parentOf("/movies/genre/acao")` = `/movies/genre`.
     */
    fun parentOf(route: String): String? {
        if (route.isEmpty() || route == "/") return null
        if (route.startsWith("http")) return null
        val clean = route.substringBefore('?').trimEnd('/')
        val lastSlash = clean.lastIndexOf('/')
        // `/movies` é o primeiro nível: acima dele está a raiz do addon.
        if (lastSlash <= 0) return null
        return clean.substring(0, lastSlash)
    }

    /** Estilo da pasta, para a tela dar ícone/cor sem inventar regra. */
    fun styleOf(node: Node): FolderStyle {
        val text = TextUtils.normalize("${node.title} ${node.route}")
        return when {
            text.contains("trakt") || text.contains("tmdb") -> FolderStyle.OTHER
            text.contains("busca") || text.contains("buscar") || text.contains("search") -> FolderStyle.SEARCH
            // Gênero vem antes de filme/série: `/movies/genre/acao` é uma pasta
            // de gênero mesmo carregando "movies" no caminho.
            text.contains("genero") || text.contains("genre") ||
                text.contains("acao") || text.contains("aventura") || text.contains("comedia") ||
                text.contains("drama") || text.contains("terror") -> FolderStyle.GENRE
            text.contains("serie") || text.contains("show") ||
                text.contains("temporada") || text.contains("season") -> FolderStyle.SERIES
            text.contains("filme") || text.contains("movie") || text.contains("cinema") -> FolderStyle.MOVIE
            else -> FolderStyle.OTHER
        }
    }

    // ---------------------------------------------------------------------
    // lista do usuário
    // ---------------------------------------------------------------------

    /**
     * Onde o usuário salvou: a aba do app onde o item vai aparecer.
     *
     * No momento de salvar ele escolhe **Canais**, **Filmes** ou **Séries** —
     * a mesma separação de acervo do resto do app. A escolha é a autoridade:
     * o item aparece na aba escolhida, mesmo que o título pareça outra coisa.
     */
    enum class Shelf(val kind: MediaKind, val label: String, val description: String) {
        CHANNELS(MediaKind.LIVE, "Canais", "Playlist de canais ao vivo"),
        MOVIES(MediaKind.MOVIE, "Filmes", "Sua lista de filmes"),
        SERIES(MediaKind.SERIES, "Séries", "Sua lista de séries"),
        ;

        companion object {
            /** Ordem fixa na tela: Canais, Filmes, Séries. */
            val ALL: List<Shelf> = listOf(CHANNELS, MOVIES, SERIES)

            /** Aba a partir do `kind` gravado (texto do banco/modelo). */
            fun of(kind: MediaKind?): Shelf = when (kind) {
                MediaKind.LIVE -> CHANNELS
                MediaKind.SERIES -> SERIES
                MediaKind.MOVIE -> MOVIES
                else -> MOVIES
            }

            /** Aba a partir do texto gravado (aceita sinônimos e nulo). */
            fun parse(raw: String?): Shelf = of(
                com.smarted.tv.core.util.SourceTargeting.parse(raw).firstOrNull(),
            )

            /** Aba sugerida para o item, sem escolha do usuário. */
            fun suggest(node: Node): Shelf = of(kindOfStatic(node))
        }
    }

    /** `kindOf` estático para uso dentro do enum (sem instância). */
    private fun kindOfStatic(node: Node): MediaKind = kindOf(node)

    /**
     * Um item que o usuário salvou da árvore.
     *
     * Guarda o essencial para montar a playlist dele: título, o link que o
     * player toca (`playUrl`), a pasta de origem e o tipo (filme/série).
     */
    data class SavedItem(
        val id: String,
        val title: String,
        val playUrl: String,
        val folder: String = "",
        val folderRoute: String = "",
        val kind: MediaKind = MediaKind.MOVIE,
        /** Aba escolhida pelo usuário no momento de salvar. */
        val shelf: Shelf = Shelf.MOVIES,
        val meta: String = "",
        val poster: String? = null,
        val addedAt: Long = 0L,
        val position: Int = 0,
    )

    /**
     * Um nó pode ser salvo?
     *
     * Sim quando é conteúdo: reproduzível (`/play?uri=…`) ou arquivo estático
     * (MP4/MKV). Pasta não entra na lista — o usuário salva o que quer assistir.
     */
    fun canSave(node: Node): Boolean {
        if (!node.isItem) return false
        val route = node.route
        if (route.isEmpty()) return false
        if (isPlayRoute(route) || isServicePlayRoute(route)) return true
        return AddonBrowser.accepts(MediaKind.MOVIE, route) ||
            AddonBrowser.accepts(MediaKind.SERIES, route)
    }

    /**
     * Item de lista a partir de um nó já resolvido.
     *
     * [playUrl] é o link que o player recebe — o `/play?uri=…` do daemon ou o
     * arquivo direto. [folder] e [folderRoute] guardam de onde veio, para a tela
     * agrupar a lista por pasta ("Lançamentos", "Ação"…).
     */
    fun toSaved(
        node: Node,
        playUrl: String,
        folder: String = "",
        folderRoute: String = "",
        kind: MediaKind? = null,
        shelf: Shelf? = null,
        addedAt: Long = 0L,
        position: Int = 0,
    ): SavedItem {
        val target = playUrl.ifBlank { node.route }
        val meta = Elementum.parseTagline(node.tagline)
        return SavedItem(
            id = savedId(target, node.title),
            title = node.title.ifBlank { "Item" },
            playUrl = target,
            folder = folder.trim(),
            folderRoute = folderRoute.trim(),
            kind = kind ?: kindOf(node),
            // Sem escolha explícita, a aba vem do tipo detectado.
            shelf = shelf ?: Shelf.of(kind ?: kindOf(node)),
            meta = meta.let { m ->
                listOf(
                    m.quality,
                    if (m.sizeBytes > 0) Elementum.humanSize(m.sizeBytes) else "",
                    if (m.seeds > 0) "${m.seeds} seeds" else "",
                    node.label2,
                ).filter { it.isNotBlank() }.joinToString(" · ")
            },
            addedAt = addedAt,
            position = position,
        )
    }

    /** Tipo do nó: usa o título e o caminho para decidir entre filme e série. */
    fun kindOf(node: Node): MediaKind {
        val text = "${node.title} ${node.route}"
        return if (Elementum.Item(title = text).kind == MediaKind.SERIES) MediaKind.SERIES else MediaKind.MOVIE
    }

    /** Id estável de um item salvo (mesmo link não entra duas vezes). */
    fun savedId(playUrl: String, title: String): String {
        val seed = if (playUrl.isNotBlank()) playUrl else title
        return "saved:${seed.hashCode()}"
    }

    /**
     * Lista do usuário separada por aba escolhida (Canais / Filmes / Séries),
     * cada aba em ordem de inserção e, dentro dela, agrupada pela pasta de
     * origem ("Lançamentos", "Ação", "Aventura"…).
     */
    data class ShelfGroup(
        val shelf: Shelf,
        val folders: List<Pair<String, List<SavedItem>>>,
    ) {
        val count: Int get() = folders.sumOf { it.second.size }
        val isEmpty: Boolean get() = count == 0
    }

    fun groupByShelf(items: List<SavedItem>): List<ShelfGroup> = Shelf.ALL.map { shelf ->
        val mine = items.filter { it.shelf == shelf }.sortedBy { it.position }
        ShelfGroup(
            shelf = shelf,
            folders = mine
                .groupBy { it.folder.ifBlank { "Sem pasta" } }
                .map { (folder, list) -> folder to list },
        )
    }

    /** Agrupa uma aba da lista do usuário por pasta de origem. */
    fun groupSaved(items: List<SavedItem>): List<Pair<String, List<SavedItem>>> =
        items
            .sortedBy { it.position }
            .groupBy { it.folder.ifBlank { "Sem pasta" } }
            .map { (folder, list) -> folder to list }

    /** Resumo da lista: "12 itens · Filmes 8 · Séries 4". */
    fun savedSummary(items: List<SavedItem>): String {
        if (items.isEmpty()) return "Sua lista está vazia"
        val base = "${items.size} " + (if (items.size == 1) "item" else "itens")
        val parts = Shelf.ALL.mapNotNull { shelf ->
            val count = items.count { it.shelf == shelf }
            if (count == 0) null else "${shelf.label} $count"
        }
        return if (parts.isEmpty()) base else "$base · ${parts.joinToString(" · ")}"
    }
}
