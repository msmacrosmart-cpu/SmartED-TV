package com.smarted.tv.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories ORDER BY isDefault DESC, addedAt ASC")
    fun observeAll(): Flow<List<RepositoryEntity>>

    @Query("SELECT * FROM repositories ORDER BY isDefault DESC, addedAt ASC")
    suspend fun all(): List<RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE id = :id")
    suspend fun byId(id: String): RepositoryEntity?

    @Query("SELECT * FROM repositories WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): RepositoryEntity?

    @Upsert
    suspend fun upsert(repo: RepositoryEntity)

    @Query("DELETE FROM repositories WHERE id = :id AND isDefault = 0")
    suspend fun delete(id: String)

    @Query("UPDATE repositories SET lastSyncAt = :at, status = :status, message = :message, addonCount = :count WHERE id = :id")
    suspend fun markSynced(id: String, at: Long, status: String, message: String?, count: Int)

    @Query("SELECT COUNT(*) FROM repositories")
    suspend fun count(): Int
}

@Dao
interface AddonDao {
    @Query("SELECT * FROM installed_addons ORDER BY sortOrder ASC, installedAt ASC")
    fun observeAll(): Flow<List<InstalledAddonEntity>>

    @Query("SELECT * FROM installed_addons ORDER BY sortOrder ASC, installedAt ASC")
    suspend fun all(): List<InstalledAddonEntity>

    @Query("SELECT * FROM installed_addons WHERE repositoryId = :repoId")
    suspend fun byRepository(repoId: String): List<InstalledAddonEntity>

    @Query("SELECT * FROM installed_addons WHERE id = :id")
    suspend fun byId(id: String): InstalledAddonEntity?

    @Upsert
    suspend fun upsert(addon: InstalledAddonEntity)

    @Upsert
    suspend fun upsertAll(addons: List<InstalledAddonEntity>)

    @Query("UPDATE installed_addons SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM installed_addons WHERE id = :id AND kind != 'BUILTIN'")
    suspend fun delete(id: String)

    @Query("DELETE FROM installed_addons WHERE repositoryId = :repoId AND kind = 'REMOTE'")
    suspend fun deleteRemoteByRepository(repoId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position ASC, addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position ASC, addedAt DESC")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE refKey = :refKey)")
    fun observeIsFavorite(refKey: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE refKey = :refKey)")
    suspend fun isFavorite(refKey: String): Boolean

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE refKey = :refKey")
    suspend fun delete(refKey: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 40): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 40): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE refKey = :refKey")
    suspend fun byKey(refKey: String): WatchProgressEntity?

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE refKey = :refKey")
    suspend fun delete(refKey: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clear()
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM catalog_cache WHERE key = :key")
    suspend fun get(key: String): CatalogCacheEntity?

    @Upsert
    suspend fun put(entity: CatalogCacheEntity)

    @Query("DELETE FROM catalog_cache WHERE updatedAt < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("DELETE FROM catalog_cache WHERE addonId = :addonId")
    suspend fun purgeAddon(addonId: String)

    @Query("DELETE FROM catalog_cache")
    suspend fun clear()

    @Query("SELECT SUM(LENGTH(payload)) FROM catalog_cache")
    suspend fun sizeBytes(): Long?
}

@Dao
interface PublicSourceDao {
    @Query("SELECT * FROM public_sources ORDER BY priority ASC, name ASC")
    fun observeAll(): Flow<List<PublicSourceEntity>>

    @Query("SELECT * FROM public_sources ORDER BY priority ASC, name ASC")
    suspend fun all(): List<PublicSourceEntity>

    @Query("SELECT * FROM public_sources WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun enabled(): List<PublicSourceEntity>

    @Upsert
    suspend fun upsert(source: PublicSourceEntity)

    @Upsert
    suspend fun upsertAll(sources: List<PublicSourceEntity>)

    @Query("UPDATE public_sources SET healthy = :healthy, lastCheckedMs = :at, lastError = :error WHERE id = :id")
    suspend fun markHealth(id: String, healthy: Boolean, at: Long, error: String?)

    @Query("DELETE FROM public_sources WHERE id = :id AND discoveredFrom IS NULL")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM public_sources")
    suspend fun count(): Int
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_sources ORDER BY priority ASC")
    fun observeSources(): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_sources WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun enabledSources(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_sources WHERE url = :url")
    suspend fun source(url: String): EpgSourceEntity?

    @Upsert
    suspend fun upsertSource(source: EpgSourceEntity)

    @Query("DELETE FROM epg_sources WHERE url = :url AND fromAddonId IS NULL")
    suspend fun deleteSource(url: String)

    @Query("UPDATE epg_sources SET lastFetchedMs = :at, channelCount = :channels, programCount = :programs, status = :status, message = :message WHERE url = :url")
    suspend fun markFetched(url: String, at: Long, channels: Int, programs: Int, status: String, message: String?)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM hidden_categories")
    fun observeAll(): Flow<List<HiddenCategoryEntity>>

    @Query("SELECT * FROM hidden_categories WHERE addonId = :addonId AND kind = :kind")
    suspend fun forAddon(addonId: String, kind: String): List<HiddenCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(entity: HiddenCategoryEntity)

    @Query("DELETE FROM hidden_categories WHERE addonId = :addonId AND kind = :kind AND name = :name")
    suspend fun unhide(addonId: String, kind: String, name: String)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT 12")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun record(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)
}
