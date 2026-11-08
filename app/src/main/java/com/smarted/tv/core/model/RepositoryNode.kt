package com.smarted.tv.core.model

/**
 * Nó recursivo da árvore de repositórios (estilo Kodi), com os 4 níveis do
 * explorador: repositório → addon → seção/menu → conteúdo.
 *
 * O índice JSON (OnePlay) e o banco local são materializados nestes objetos; a UI
 * navega dinamicamente entrando em cada nó e pedindo seus `children` ao
 * [com.smarted.tv.core.data.ContentRepository] — sem redirecionar para abas estáticas.
 *
 * kinds: `root` (nível 0) → `repository` (lista de repositórios) → `addon`
 * (tipos universais Kodi: video, programs, radio, live, ... — [addonType] livre
 * vindo do JSON, nunca fixo em código) → `category`/`folder` (o fluxo EXATO de
 * diretórios que o JSON do addon retornar) → `item`.
 *
 * @param id chave estável (LazyColumn + identidade do nó).
 * @param tab seção dona do conteúdo (category/folder/item): define o destino Canais/Filmes/Séries.
 * @param pathKey caminho/categoria crua da pasta ("Pai/Filho" ou groupId) — âncora para o banco e o índice.
 * @param mediaKind itens: "channel" | "movie" | "series" (categoria do conteúdo).
 * @param addonType tipo universal do add-on ("video" | "programs" | "radio" | ...) — dado, não código.
 * @param repoUrl índice JSON do repositório dono do nó (lookups recursivos no índice).
 * @param addonId id do add-on dono do menu (para achar a árvore certa no JSON).
 * @param children filhos dinâmicos, preenchidos por `repositoryChildren`.
 */
data class RepositoryNode(
    val id: String,
    val name: String,
    val kind: Kind,
    val tab: Tab? = null,
    val pathKey: String = "",
    val mediaKind: String? = null,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val favKey: String = "",
    val addonType: String? = null,
    /** Id do nó ADDON dono deste menu (âncora para servir os filhos recursivos do JSON). */
    val addonId: String = "",
    val repoUrl: String = "",
    val children: List<RepositoryNode> = emptyList(),
) {
    enum class Kind { ROOT, REPOSITORY, CATEGORY, FOLDER, ADDON, ITEM }

    val isItem: Boolean get() = kind == Kind.ITEM
}
