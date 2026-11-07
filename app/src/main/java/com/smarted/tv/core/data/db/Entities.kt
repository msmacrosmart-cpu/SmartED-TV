package com.smarted.tv.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "repositories")
data class RepositoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val addedAt: Long = 0L,
    val lastSyncAt: Long = 0L,
    val addonCount: Int = 0,
    val status: String = "UNKNOWN",
    val message: String? = null,
    val icon: String? = null,
)

@Entity(
    tableName = "installed_addons",
    indices = [Index("repositoryId")],
)
data class InstalledAddonEntity(
    @PrimaryKey val id: String,
    val repositoryId: String,
    val name: String,
    val version: String,
    val summary: String,
    val summaryLong: String = "",
    val author: String = "",
    val icon: String? = null,
    val enabled: Boolean = true,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val kind: String = "REMOTE",
    val types: String = "LIVE,MOVIE,SERIES",
    val sortOrder: Int = 0,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val refKey: String,
    val addonId: String,
    val kind: String,
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val poster: String? = null,
    val streamUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0,
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val refKey: String,
    val addonId: String,
    val kind: String,
    val itemId: String,
    val title: String,
    val poster: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val season: Int? = null,
    val episode: Int? = null,
    val showId: String? = null,
    val subtitle: String? = null,
    val streamUrl: String? = null,
)

@Entity(tableName = "catalog_cache", indices = [Index("addonId", "kind")])
data class CatalogCacheEntity(
    @PrimaryKey val key: String,
    val addonId: String,
    val kind: String,
    val payload: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val ttlMs: Long = 30 * 60 * 1000L,
)

@Entity(tableName = "public_sources")
data class PublicSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val kind: String = "LIVE",
    val format: String = "M3U",
    val group: String? = null,
    val logo: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val lastCheckedMs: Long = 0L,
    val healthy: Boolean = true,
    val lastError: String? = null,
    val discoveredFrom: String? = null,
)

@Entity(tableName = "hidden_categories", primaryKeys = ["addonId", "kind", "name"])
data class HiddenCategoryEntity(
    val addonId: String,
    val kind: String,
    val name: String,
)

@Entity(tableName = "epg_sources")
data class EpgSourceEntity(
    @PrimaryKey val url: String,
    val label: String,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val lastFetchedMs: Long = 0L,
    val channelCount: Int = 0,
    val programCount: Int = 0,
    val status: String = "UNKNOWN",
    val message: String? = null,
    val fromAddonId: String? = null,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val usedAt: Long = System.currentTimeMillis(),
)
