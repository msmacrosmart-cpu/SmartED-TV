package com.smarted.tv.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RepositoryEntity::class,
        InstalledAddonEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
        CatalogCacheEntity::class,
        PublicSourceEntity::class,
        HiddenCategoryEntity::class,
        EpgSourceEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SmartEdDatabase : RoomDatabase() {

    abstract fun repositoryDao(): RepositoryDao
    abstract fun addonDao(): AddonDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun progressDao(): ProgressDao
    abstract fun cacheDao(): CacheDao
    abstract fun publicSourceDao(): PublicSourceDao
    abstract fun epgDao(): EpgDao
    abstract fun categoryDao(): CategoryDao
    abstract fun searchDao(): SearchDao

    companion object {
        private const val DB_NAME = "smarted-tv.db"

        @Volatile
        private var instance: SmartEdDatabase? = null

        fun get(context: Context): SmartEdDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): SmartEdDatabase =
            Room.databaseBuilder(context, SmartEdDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
