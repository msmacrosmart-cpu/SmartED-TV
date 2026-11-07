package com.smarted.tv.ui.state

import android.content.Context
import com.smarted.tv.core.addons.AddonManager
import com.smarted.tv.core.data.Settings
import com.smarted.tv.core.data.SourceRepository
import com.smarted.tv.core.data.db.SmartEdDatabase
import com.smarted.tv.core.epg.EpgService
import com.smarted.tv.core.indexer.Indexer
import com.smarted.tv.core.model.CatalogPage
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.PlayInfo
import com.smarted.tv.core.tmdb.TmdbClient
import kotlinx.coroutines.flow.StateFlow

/** Loja de configurações com estado observável. */
interface SettingsStoreLike {
    val state: StateFlow<Settings>
    val settings: Settings
    fun update(block: (Settings) -> Settings)
    fun setRepository(url: String, name: String)
    /** Troca a URL de um dos três repositórios de conteúdo (canais/filmes/séries). */
    fun setContentRepository(kind: com.smarted.tv.core.model.MediaKind, url: String)
    /** Troca a lista pública de um tipo (modos Normal e SmartED). */
    fun setPublicSource(kind: com.smarted.tv.core.model.MediaKind, url: String)
    /** Define o modo de conteúdo (Normal / SmartED / Repo) de uma aba. */
    fun setContentMode(kind: com.smarted.tv.core.model.MediaKind, mode: com.smarted.tv.core.model.ContentMode)
    fun reload()
}

/** Resumo do catálogo do addon nativo. */
interface CatalogSnapshot {
    val live: List<MediaItem>
    val movies: List<MediaItem>
    val series: List<MediaItem>
    val total: Int
    val isEmpty: Boolean
    val degraded: Boolean
    val sourceErrors: List<String>
}

/** Operações de catálogo usadas pelas telas. */
interface CatalogGateway {
    val snapshotFlow: StateFlow<out CatalogSnapshot>
    val loading: StateFlow<Boolean>
    val status: StateFlow<String>

    suspend fun snapshot(force: Boolean = false): CatalogSnapshot
    suspend fun hydrate(): CatalogSnapshot
    fun invalidate()
    suspend fun clearCache()
}

/** Operações do addon usadas pelas telas (browse/search/details/resolve). */
interface AddonGateway {
    suspend fun search(query: String, page: Int): List<MediaItem>
    suspend fun details(item: MediaItem): MediaItem
    suspend fun resolve(item: MediaItem): PlayInfo?
    suspend fun browsePage(kind: com.smarted.tv.core.model.MediaKind): CatalogPage?
}

/**
 * Dependências de alto nível expostas às telas.
 * `AppGraph` implementa esta interface — o resto da UI só depende dela.
 */
interface AppServices {
    val appContext: Context
    val settings: SettingsStoreLike
    val library: LibraryRepository
    val catalog: CatalogGateway
    val addon: AddonGateway
    val epg: EpgService
    val indexer: Indexer
    val addons: AddonManager
    val sources: SourceRepository
    val tmdb: TmdbClient
    val trailerFinder: com.smarted.tv.core.trailer.TrailerFinder
    val db: SmartEdDatabase

    var playerRequest: MediaItem?
    var trailerRequest: String?

    fun applyTmdbKey(key: String)
}
