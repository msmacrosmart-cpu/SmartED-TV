package com.smarted.tv.data.repo

import com.smarted.tv.core.model.RepositoryNode
import com.smarted.tv.core.model.Tab
import com.smarted.tv.data.net.Http
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Índice JSON do repositório OnePlay (fantasma) — DTOs recursivos.
 *
 * O campo `tree` descreve a árvore recursiva de diretórios que o módulo
 * Repository/Tree navega — materializada em `RepositoryNode`
 * (root → addon/category → pastas → itens) por [toRepositoryNode].
 * Ausência de `tree` (índice antigo) ou falha de rede/parse => `null`,
 * e a navegação segue com a árvore local do banco (modo offline à prova de falha).
 */
@Serializable
data class OnePlayIndexDto(
    val name: String? = null,
    /** Add-ons declarados pelo repositório (tipos universais Kodi: video, programs, radio, ...). */
    val addons: List<OnePlayAddonDto>? = null,
    /** Árvore legada (índices antigos sem `addons`): vira um único add-on. */
    val tree: OnePlayNodeDto? = null,
)

/** Um add-on do repositório: nome + tipo livre + seu menu recursivo (`tree`). */
@Serializable
data class OnePlayAddonDto(
    val name: String? = null,
    /** Tipo universal: "video" | "programs" | "radio" | "live" | ... (livre, exatamente como no JSON). */
    val type: String? = null,
    val tree: OnePlayNodeDto? = null,
)

@Serializable
data class OnePlayNodeDto(
    val name: String? = null,
    /** "root" | "category" | "folder" | "leaf" — intenção do nó (opcional). */
    val kind: String? = null,
    /** "channels" | "movies" | "series" — apenas em nós de categoria. */
    val tab: String? = null,
    /** URL direta de reprodução (nó folha). */
    val url: String? = null,
    val id: String? = null,
    /** Filhos recursivos (subpastas/conteúdo). */
    val children: List<OnePlayNodeDto>? = null,
)

/** Serviço Retrofit do índice OnePlay (interface pública exigida pelo Proxy dinâmico). */
interface OnePlayTreeApi {
    /** [indexUrl] é absoluto (@Url anula o baseUrl configurado). */
    @GET
    suspend fun index(@Url indexUrl: String): String
}

/**
 * Cliente HTTP/JSON nativo ultra leve (Retrofit + kotlinx-serialization sobre o
 * OkHttp já presente no app) para a árvore do repositório OnePlay "fantasma".
 * Sem engines externas pesadas. Toda falha (rede, timeout, JSON inválido) vira `null`.
 */
object OnePlayTreeClient {

    private const val RAW_BASE = "https://raw.githubusercontent.com/"

    private val api: OnePlayTreeApi by lazy {
        Retrofit.Builder()
            .baseUrl(RAW_BASE)
            .client(Http.client)
            .build()
            .create(OnePlayTreeApi::class.java)
    }

    /** Melhor esforço: `null` em qualquer falha (a UI cai para a árvore local). */
    suspend fun load(indexUrl: String): OnePlayIndexDto? = runCatching {
        val text = api.index(indexUrl)
        Http.json.decodeFromString(OnePlayIndexDto.serializer(), text)
    }.getOrNull()
}

// ── Índice JSON → navegação recursiva em RepositoryNode (root/addon/category/folder/item) ──

/**
 * Materializa o índice recursivo como esqueleto de [RepositoryNode] (filhos já
 * mapeados). O [com.smarted.tv.core.data.IptvContentRepository] usa este esqueleto
 * para servir `repositoryChildren` dinamicamente, mesclando o banco local.
 * `null` quando o índice não tem `tree` (índice antigo/falha de parse).
 */
fun OnePlayIndexDto.toRepositoryNode(): RepositoryNode? {
    val tree = tree ?: return null
    return tree.toRepositoryNode(parentTab = null, parentPath = "", isRoot = true)
}

/**
 * Mapeia a RAIZ do menu de um add-on (campo `tree` de [OnePlayAddonDto]) em nó
 * ROOT com os filhos recursivos já convertidos em [RepositoryNode] — o menu é
 * lido exatamente como o JSON declara (sem pastas fixas no app).
 */
fun OnePlayNodeDto.toRepositoryMenu(): RepositoryNode =
    toRepositoryNode(parentTab = null, parentPath = "", isRoot = true)

private fun OnePlayNodeDto.toRepositoryNode(
    parentTab: Tab?,
    parentPath: String,
    isRoot: Boolean,
): RepositoryNode {
    val tab = tabOfDto() ?: parentTab
    val streamUrl = url?.takeIf { it.isNotBlank() }
    val title = name ?: ""
    val dtoKind = kind?.lowercase()
    val nodeKind = when {
        streamUrl != null -> RepositoryNode.Kind.ITEM
        isRoot -> RepositoryNode.Kind.ROOT
        dtoKind == "addon" -> RepositoryNode.Kind.ADDON
        dtoKind == "category" -> RepositoryNode.Kind.CATEGORY
        // Índice sem `kind` explícito: nó de topo com aba e filhos = categoria.
        dtoKind == null && tab != null && parentPath.isEmpty() && childrenOrEmpty().isNotEmpty() ->
            RepositoryNode.Kind.CATEGORY
        else -> RepositoryNode.Kind.FOLDER
    }
    // Caminho só de pastas (a categoria reinicia o caminho): "Pai/Filho".
    val pathKey = when (nodeKind) {
        RepositoryNode.Kind.ROOT, RepositoryNode.Kind.CATEGORY -> ""
        else -> if (parentPath.isEmpty()) title else "$parentPath/$title"
    }
    val mediaKind = when {
        nodeKind != RepositoryNode.Kind.ITEM -> null
        kind == "channel" || kind == "movie" || kind == "series" -> kind
        tab == Tab.CHANNELS -> "channel"
        tab == Tab.SERIES -> "series"
        else -> "movie"
    }
    val nodeId = when (nodeKind) {
        RepositoryNode.Kind.ROOT -> "root"
        RepositoryNode.Kind.REPOSITORY -> "repo|$title"
        RepositoryNode.Kind.ADDON -> "addon|$title"
        RepositoryNode.Kind.CATEGORY -> "cat|${tab?.name ?: "?"}|$title"
        RepositoryNode.Kind.FOLDER -> "folder|${tab?.name ?: "?"}|$pathKey"
        RepositoryNode.Kind.ITEM -> "$mediaKind|${this.id ?: streamUrl}"
    }
    // Filhos: a categoria reinicia o caminho; pastas acumulam "Pai/Filho".
    val childPath = if (nodeKind == RepositoryNode.Kind.FOLDER) pathKey else ""
    return RepositoryNode(
        id = nodeId,
        name = title,
        kind = nodeKind,
        tab = tab,
        pathKey = pathKey,
        mediaKind = mediaKind,
        url = streamUrl,
        favKey = "",
        children = childrenOrEmpty().map { it.toRepositoryNode(tab, childPath, isRoot = false) },
    )
}

/** Nó de categoria no índice por aba (ou por nome canônico na dúvida). */
private fun OnePlayNodeDto.tabOfDto(): Tab? {
    when (tab?.lowercase()) {
        "channels" -> return Tab.CHANNELS
        "movies" -> return Tab.MOVIES
        "series" -> return Tab.SERIES
    }
    return when (name?.trim()?.lowercase()) {
        "tv" -> Tab.CHANNELS
        "filmes" -> Tab.MOVIES
        "séries", "series" -> Tab.SERIES
        else -> null
    }
}

private fun OnePlayNodeDto.childrenOrEmpty(): List<OnePlayNodeDto> = children.orEmpty()
