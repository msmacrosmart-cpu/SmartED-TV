package com.smarted.tv.core.model

/**
 * Nó recursivo da árvore de repositórios (estilo Kodi).
 *
 * O índice JSON (OnePlay) e o banco local são materializados nestes objetos; a UI
 * navega dinamicamente entrando em cada nó e pedindo seus `children` ao
 * [com.smarted.tv.core.data.ContentRepository] — sem redirecionar para abas estáticas.
 *
 * kinds: `root` → `addon` → (`category`)? → `folder`* → `item`.
 * Cada pasta carrega sua lista de children (subpastas, add-ons ou conteúdos) sob demanda.
 *
 * @param id chave estável (LazyColumn + identidade do nó).
 * @param tab seção dona do conteúdo (category/folder/item): define o destino Canais/Filmes/Séries.
 * @param pathKey caminho da pasta dentro da aba ("Pai/Filho") — âncora para achar o nó no índice.
 * @param mediaKind itens: "channel" | "movie" | "series" (categoria do conteúdo).
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
    val children: List<RepositoryNode> = emptyList(),
) {
    enum class Kind { ROOT, CATEGORY, FOLDER, ADDON, ITEM }

    val isItem: Boolean get() = kind == Kind.ITEM
}
