package com.smarted.tv.ui.state

import com.smarted.tv.core.data.db.CacheDao
import com.smarted.tv.core.data.db.FavoriteDao
import com.smarted.tv.core.data.db.FavoriteEntity
import com.smarted.tv.core.data.db.HiddenCategoryEntity
import com.smarted.tv.core.data.db.CategoryDao
import com.smarted.tv.core.data.db.ProgressDao
import com.smarted.tv.core.data.db.SearchDao
import com.smarted.tv.core.data.db.SearchHistoryEntity
import com.smarted.tv.core.data.db.WatchProgressEntity
import com.smarted.tv.core.model.ItemRef
import com.smarted.tv.core.model.MediaItem
import com.smarted.tv.core.model.MediaKind
import com.smarted.tv.core.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Biblioteca do usuário: favoritos, progresso, categorias ocultas, histórico
 * de busca e cache de catálogo.
 */
class LibraryRepository(
    private val favorites: FavoriteDao,
    private val progress: ProgressDao,
    private val categories: CategoryDao,
    private val search: SearchDao,
    private val cache: CacheDao,
) {

    fun observeFavorites(): Flow<List<MediaItem>> =
        favorites.observeAll().map { list -> list.map { it.toItem() } }

    fun observeRecent(limit: Int = 30): Flow<List<WatchProgress>> =
        progress.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeHiddenCategories(): Flow<Set<String>> =
        categories.observeAll().map { list -> list.map { "${it.addonId}|${it.kind}|${it.name}" }.toSet() }

    fun observeSearchHistory(): Flow<List<String>> =
        search.observeRecent().map { list -> list.map { it.query } }

    suspend fun isFavorite(ref: ItemRef): Boolean = withContext(Dispatchers.IO) {
        favorites.isFavorite(ref.key)
    }

    fun observeIsFavorite(ref: ItemRef): Flow<Boolean> = favorites.observeIsFavorite(ref.key)

    suspend fun toggleFavorite(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val key = item.ref.key
        if (favorites.isFavorite(key)) {
            favorites.delete(key)
            false
        } else {
            favorites.upsert(
                FavoriteEntity(
                    refKey = key,
                    addonId = item.addonId,
                    kind = item.kind.name,
                    itemId = item.id,
                    title = item.title,
                    subtitle = item.subtitle,
                    poster = item.poster,
                    streamUrl = item.streamUrl,
                ),
            )
            true
        }
    }

    suspend fun recentProgress(limit: Int = 30): List<WatchProgress> = withContext(Dispatchers.IO) {
        progress.recent(limit).map { it.toDomain() }
    }

    suspend fun progressFor(ref: ItemRef): WatchProgress? = withContext(Dispatchers.IO) {
        progress.byKey(ref.key)?.toDomain()
    }

    suspend fun saveProgress(
        item: MediaItem,
        positionMs: Long,
        durationMs: Long,
        season: Int? = null,
        episode: Int? = null,
        showId: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (positionMs < 5_000L && !item.kind.let { it == MediaKind.EPISODE }) return@withContext
        progress.upsert(
            WatchProgressEntity(
                refKey = item.ref.key,
                addonId = item.addonId,
                kind = item.kind.name,
                itemId = item.id,
                title = item.title,
                subtitle = item.subtitle,
                poster = item.poster,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                season = season,
                episode = episode,
                showId = showId,
                streamUrl = item.streamUrl,
            ),
        )
    }

    suspend fun removeProgress(ref: ItemRef) = withContext(Dispatchers.IO) {
        progress.delete(ref.key)
    }

    suspend fun hideCategory(addonId: String, kind: MediaKind, name: String) = withContext(Dispatchers.IO) {
        categories.hide(HiddenCategoryEntity(addonId, kind.name, name))
    }

    suspend fun unhideCategory(addonId: String, kind: MediaKind, name: String) = withContext(Dispatchers.IO) {
        categories.unhide(addonId, kind.name, name)
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length >= 2) search.record(SearchHistoryEntity(q))
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) { search.clear() }

    suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.IO) { cache.sizeBytes() ?: 0L }

    suspend fun clearCache() = withContext(Dispatchers.IO) { cache.clear() }

    private fun FavoriteEntity.toItem() = MediaItem(
        id = itemId,
        addonId = addonId,
        kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.MOVIE),
        title = title,
        subtitle = subtitle,
        poster = poster,
        streamUrl = streamUrl,
    )

    private fun WatchProgressEntity.toDomain() = WatchProgress(
        ref = refKey,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
        title = title,
        poster = poster,
        kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.MOVIE),
        season = season,
        episode = episode,
        showId = showId,
        addonId = addonId,
        itemId = itemId,
    )

    /** Constrói um [MediaItem] a partir do progresso persistido. */
    fun WatchProgress.toItem(): MediaItem = MediaItem(
        id = itemId.ifBlank { ref },
        addonId = addonId,
        kind = kind,
        title = title,
        subtitle = subtitleText(),
        poster = poster,
        durationSec = durationMs / 1000L,
        number = episode,
    )

    fun WatchProgress.subtitleText(): String? = when {
        season != null && episode != null -> "T${season}E$episode"
        else -> null
    }

    fun WatchProgress.progressItemTitle(): String = title
}
