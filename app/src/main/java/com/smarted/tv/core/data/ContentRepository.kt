package com.smarted.tv.core.data

import com.smarted.tv.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato único entre a UI e a fonte de dados. A implementação real é
 * [IptvContentRepository] (banco SQLite + importadores M3U/Xtream/Repositório);
 * as telas nunca tocam no [com.smarted.tv.data] diretamente.
 */
interface ContentRepository {
    /** Ids com prefixo (`c:`/`m:`/`s:`) compartilhados por canais e VOD. */
    val favoriteIds: StateFlow<Set<String>>
    fun toggleFavorite(id: String)

    suspend fun channelCategories(): List<Category>
    suspend fun channels(): List<Channel>

    suspend fun vodCategories(kind: VodKind): List<Category>
    suspend fun vod(kind: VodKind): List<VodItem>

    /** Grade "a seguir" da pré-visualização de Canais (carregada no foco, não na lista). */
    suspend fun channelUpcoming(channelId: String, nowMs: Long, limit: Int): List<EpgProgram> = emptyList()

    /** Temporadas de uma série (carregadas sob demanda quando o HeroPanel abre). */
    suspend fun loadSeasons(vodId: String): List<Season> = emptyList()

    /**
     * Raiz da árvore de repositórios recursiva (estilo Kodi) — níveis
     * `root` → `repository` → `addon` → `category` → `folder` → `item` de [RepositoryNode].
     */
    suspend fun repositoryRoot(): RepositoryNode = RepositoryNode(
        id = "root",
        name = "Repositório",
        kind = RepositoryNode.Kind.ROOT,
    )

    /**
     * Filhos dinâmicos de qualquer nó da árvore: cada nível (repositórios,
     * addons, seções do addon, pastas) carrega sua lista de `children` sob
     * demanda — índice JSON do repositório + banco local, sem abas estáticas.
     */
    suspend fun repositoryChildren(node: RepositoryNode): List<RepositoryNode> = emptyList()

    /**
     * Como o clique em um ADDON deve ser tratado:
     *  - "menu"    — o índice declara menu JSON (o app segue a navegação real);
     *  - "xtream"  — capacidade REAL detectada (leitura estática do entrypoint/
     *                metadados): executar via cliente Xtream NATIVO (abas Canais/
     *                Filmes/Séries) — sem rodar Python do Kodi;
     *  - "details" — sem menu e sem capacidade mapeável: só metadados honestos
     *                (NUNCA explorador de arquivos de código).
     */
    suspend fun addonExecution(node: RepositoryNode): String = when {
        node.addonCapability == "xtream" -> "xtream"
        else -> "menu"
    }

    /** Conecta o add-on a um painel Xtream (nativo) e sincroniza as categorias. */
    suspend fun connectAddonXtream(name: String, url: String, user: String, pass: String): String =
        error("Execução de add-on não suportada nesta fonte.")

    /** Nome de uma fonte Xtream já conectada (atalho "abrir categorias já importadas"). */
    suspend fun existingXtreamName(): String? = null

    /** Incrementa a cada escrita/sincronização — os ViewModels recarregam. */
    val revision: StateFlow<Int> get() = NEVER
    val busy: StateFlow<Boolean> get() = NEVERB

    companion object {
        private val NEVER = kotlinx.coroutines.flow.MutableStateFlow(0)
        private val NEVERB = kotlinx.coroutines.flow.MutableStateFlow(false)
    }
}
