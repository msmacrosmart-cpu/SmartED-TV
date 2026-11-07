package com.smarted.tv.core.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Banco local do app.
 *
 * Regra de ouro: **toda** mudança de esquema (coluna nova, tabela nova, tipo
 * alterado) exige subir `version` E escrever a migração correspondente.
 *
 * O Room guarda a assinatura do esquema em `room_master_table`. Se a assinatura
 * gravada no arquivo não bate com a das entidades, ele lança
 * `IllegalStateException: Room cannot verify the data integrity` — e isso
 * acontece na **abertura do app**, porque o [AppGraph] abre o banco ao subir.
 * Foi exatamente o que ocorreu ao adicionar `repositories.kinds`.
 *
 * `tools/check-db-schema.py` quebra o build se o esquema mudar sem versão nova,
 * se faltar a migração daquele passo ou se o SQL não reproduzir o esquema
 * esperado (ele roda o ALTER num SQLite de verdade antes de aprovar).
 */
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
        SavedItemEntity::class,
    ],
    version = 4,
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
    abstract fun savedItemDao(): SavedItemDao

    companion object {
        private const val DB_NAME = "smarted-tv.db"
        private const val TAG = "SmartEdDatabase"

        /**
         * v1 → v2: `repositories.kinds`, as abas (Canais/Filmes/Séries) que o
         * usuário marcou ao cadastrar o repositório. Vazio = todas as abas.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `repositories` ADD COLUMN `kinds` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 → v3: `saved_items`, a lista do usuário montada navegando a árvore
         * dos addons (pastas como "Lançamentos", "Ação", "Aventura").
         */
        /**
         * v2 → v3: `saved_items`, a lista do usuário montada navegando a árvore
         * dos addons (pastas como "Lançamentos", "Ação", "Aventura").
         *
         * O SQL não declara `DEFAULT`: o Room compara os valores padrão do
         * arquivo com os das entidades, e a entidade não declara nenhum (os
         * valores iniciais são do Kotlin, não do esquema). Criar com `DEFAULT`
         * aqui faz o app recriar o banco na abertura.
         */
        /**
         * v2 -> v3: `saved_items`, a lista do usuario montada navegando a arvore
         * dos addons (pastas como "Lancamentos", "Acao", "Aventura").
         *
         * O SQL nao declara DEFAULT: o Room compara os valores padrao do arquivo
         * com os das entidades, e a entidade nao declara nenhum (os valores
         * iniciais sao do Kotlin). Criar com DEFAULT faz o app recriar o banco.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `playUrl` TEXT NOT NULL, `folder` TEXT NOT NULL, `folderRoute` TEXT NOT NULL, `kind` TEXT NOT NULL, `meta` TEXT NOT NULL, `poster` TEXT, `addonId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_playUrl` ON `saved_items` (`playUrl`)")
            }
        }

        /**
         * v3 -> v4: `saved_items.shelf`, a aba (Canais/Filmes/Series) escolhida
         * pelo usuario ao salvar.
         *
         * ADD COLUMN NOT NULL exigiria DEFAULT, que voltaria a divergir do
         * esquema da entidade; por isso a tabela e recriada com o esquema final
         * e os dados sao copiados.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `saved_items_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `playUrl` TEXT NOT NULL, `folder` TEXT NOT NULL, `folderRoute` TEXT NOT NULL, `kind` TEXT NOT NULL, `shelf` TEXT NOT NULL, `meta` TEXT NOT NULL, `poster` TEXT, `addonId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `saved_items_new` (`id`, `title`, `playUrl`, `folder`, `folderRoute`, `kind`, `shelf`, `meta`, `poster`, `addonId`, `addedAt`, `position`) SELECT `id`, `title`, `playUrl`, `folder`, `folderRoute`, `kind`, 'MOVIE', `meta`, `poster`, `addonId`, `addedAt`, `position` FROM `saved_items`")
                db.execSQL("DROP TABLE `saved_items`")
                db.execSQL("ALTER TABLE `saved_items_new` RENAME TO `saved_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_playUrl` ON `saved_items` (`playUrl`)")
            }
        }

        @Volatile
        private var instance: SmartEdDatabase? = null

        fun get(context: Context): SmartEdDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        /**
         * Abre o banco já na inicialização.
         *
         * O Room abre o arquivo de forma preguiçosa, na primeira consulta — e
         * uma falha no meio de uma tela derruba o app sem chance de recuperação.
         * Aqui forçamos a abertura dentro do `get`: se o arquivo antigo for
         * incompatível por qualquer motivo (migração faltando, esquema de uma
         * versão intermediária, arquivo corrompido), recriamos o banco em vez de
         * deixar o app morrer na abertura. Perder o cache local é melhor do que
         * um crash loop que impede o usuário de abrir o app.
         */
        private fun build(context: Context): SmartEdDatabase {
            val db = openDatabase(context)
            return try {
                db.openHelper.writableDatabase
                db
            } catch (t: Throwable) {
                Log.w(TAG, "Banco local incompatível; recriando.", t)
                runCatching { db.close() }
                runCatching { context.deleteDatabase(DB_NAME) }
                openDatabase(context)
            }
        }

        private fun openDatabase(context: Context): SmartEdDatabase =
            Room.databaseBuilder(context, SmartEdDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
