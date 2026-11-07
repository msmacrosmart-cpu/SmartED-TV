package com.smarted.tv.core.addons

import com.smarted.tv.core.model.CatalogPage
import com.smarted.tv.core.model.EpgProgram
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.PlayInfo

/**
 * Arquitetura modular de Addons (estilo Kodi).
 *
 * Um addon é qualquer fonte de conteúdo capaz de listar, detalhar, buscar,
 * resolver reprodução e fornecer EPG. O app traz o **Brazuca Play** embutido
 * e pode instalar outros addons a partir de repositórios remotos.
 */
interface Addon {

    /** Identidade e capacidades declaradas do addon. */
    val manifest: AddonManifest

    /** Abre uma seção e devolve a página de catálogo correspondente. */
    suspend fun browse(request: BrowseRequest): CatalogPage

    /** Busca textual no addon. */
    suspend fun search(query: String, page: Int = 1): List<MediaItem>

    /**
     * Enriquece um item com metadados completos (sinopse, elenco, temporadas,
     * trailer). O addon pode delegar ao TMDB via [com.smarted.tv.core.tmdb.TmdbClient].
     */
    suspend fun details(item: MediaItem): MediaItem

    /** Resolve a URL reproduzível de um item. `null` quando indisponível. */
    suspend fun resolve(item: MediaItem): PlayInfo?

    /** Programação (EPG). Retorna vazio quando o addon não fornece guia. */
    suspend fun epg(channel: MediaItem, startMs: Long, endMs: Long): List<EpgProgram> = emptyList()

    /** Canais cujo id/tvg-id devem ser consultados no guia. */
    suspend fun epgChannelIds(): List<String> = emptyList()

    /** Chamado quando o addon é instalado ou o repositório muda. */
    suspend fun refresh() {}

    /** Cachorros de saúde: usado pelo indexador de manutenção de links. */
    suspend fun healthCheck(): AddonHealth = AddonHealth.unknown()
}

/** Requisição de navegação padronizada. */
data class BrowseRequest(
    val kind: MediaKind,
    val section: Section = Section.ROOT,
    val category: String? = null,
    val page: Int = 1,
    val filter: String? = null,
    val item: MediaItem? = null,
) {
    enum class Section { ROOT, CATEGORIES, ITEMS, HOME, EPG, SEASON, EPISODES, GENRES, SEARCH }
}

/** Estado de saúde de um addon/fonte. */
data class AddonHealth(
    val ok: Boolean,
    val latencyMs: Long = 0,
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val message: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun unknown() = AddonHealth(ok = false, message = "não verificado")
        fun good(latency: Long, channels: Int = 0, movies: Int = 0, series: Int = 0) =
            AddonHealth(true, latency, channels, movies, series, null)
        fun bad(message: String) = AddonHealth(false, message = message)
    }
}

/**
 * Manifesto do addon — espelha o `addon.xml` do Kodi e adiciona campos
 * próprios do SmartED TV (fontes públicas, EPG padrão, trailer).
 */
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val summary: String = "",
    val description: String = "",
    val author: String = "",
    val icon: String? = null,
    val fanart: String? = null,
    val website: String? = null,
    val repoUrl: String? = null,
    val types: Set<MediaKind> = setOf(MediaKind.LIVE, MediaKind.MOVIE, MediaKind.SERIES),
    val providesEpg: Boolean = false,
    val builtin: Boolean = false,
    val requiresAuth: Boolean = false,
    /** Fontes públicas usadas pelo indexador interno. */
    val publicSources: List<String> = emptyList(),
    /** URLs de EPG (XMLTV) candidatas, em ordem de preferência. */
    val epgUrls: List<String> = emptyList(),
    /** Se true, o addon possui trilha nativa própria; senão usa o TMDB global. */
    val usesTmdb: Boolean = true,
    val changelog: String? = null,
) {
    val displayVersion: String get() = if (version.startsWith("v")) version else "v$version"
}

/** Exceção de domínio para falhas de addon (nunca vaza stack para a UI). */
class AddonException(message: String, cause: Throwable? = null) : Exception(message, cause)
